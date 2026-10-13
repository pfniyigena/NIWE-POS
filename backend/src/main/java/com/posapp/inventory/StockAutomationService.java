package com.posapp.inventory;

import com.posapp.catalog.CatalogService;
import com.posapp.catalog.dto.ProductDto;
import com.posapp.email.MailService;
import com.posapp.inventory.dto.PurchaseRequisitionDto;
import com.posapp.inventory.dto.ReorderRequisitionRequest;
import com.posapp.inventory.dto.ReorderSuggestionDto;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The two daily stock jobs, both switched on in Admin &gt; Inventory &gt; Settings:
 * <ul>
 *   <li><b>Auto reorder</b> — turns today's {@link ReorderService#suggestions()} into one DRAFT purchase
 *       requisition. It never submits or approves anything, and skips products that are already on an
 *       open requisition (draft, pending or approved) so an unreviewed draft is not duplicated tomorrow.</li>
 *   <li><b>Low-stock digest</b> — one email to the notifications address listing every product below its
 *       low-stock threshold or at/below its reorder level, plus the requisition just drafted (if any).
 *       Nothing is sent when nothing is low.</li>
 * </ul>
 * A cheap check runs every five minutes and starts the jobs once per store-local day, from the configured
 * time onward, so a server that was down at the exact minute still catches up later the same day.
 */
@Service
public class StockAutomationService {

    private static final Logger log = LoggerFactory.getLogger(StockAutomationService.class);

    private final StoreSettingsService storeSettingsService;
    private final ReorderService reorderService;
    private final PurchaseRequisitionService purchaseRequisitionService;
    private final CatalogService catalogService;
    private final MailService mailService;

    public StockAutomationService(StoreSettingsService storeSettingsService,
                                  ReorderService reorderService,
                                  PurchaseRequisitionService purchaseRequisitionService,
                                  CatalogService catalogService,
                                  MailService mailService) {
        this.storeSettingsService = storeSettingsService;
        this.reorderService = reorderService;
        this.purchaseRequisitionService = purchaseRequisitionService;
        this.catalogService = catalogService;
        this.mailService = mailService;
    }

    /** What a run did; also what the "Run now" button shows. */
    public record RunResult(boolean requisitionsEnabled, String requisitionNumber, int requisitionLines,
                            boolean digestEnabled, boolean digestSent, int lowStockCount, int reorderCount, String message) {
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    public void tick() {
        try {
            StoreSettings settings = storeSettingsService.getSettingsEntity();
            if (!settings.isLowStockDigestEnabled() && !settings.isAutoRequisitionEnabled()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now(zoneOf(settings));
            LocalDate today = now.toLocalDate();
            if (today.equals(settings.getStockJobLastRun())) {
                return;
            }
            if (now.toLocalTime().isBefore(timeOf(settings))) {
                return;
            }
            // Mark first: a job that fails should not be retried every five minutes all day.
            storeSettingsService.markStockJobsRan(today);
            RunResult result = run(settings);
            log.info("Daily stock jobs: {}", result.message());
        } catch (Exception ex) {
            log.warn("Daily stock jobs failed: {}", ex.getMessage(), ex);
        }
    }

    /** Runs whichever jobs are switched on, right now, regardless of the schedule (the "Run now" button). */
    public RunResult runNow() {
        return run(storeSettingsService.getSettingsEntity());
    }

    private RunResult run(StoreSettings settings) {
        boolean wantRequisition = settings.isAutoRequisitionEnabled();
        boolean wantDigest = settings.isLowStockDigestEnabled();
        StringBuilder message = new StringBuilder();

        List<ReorderSuggestionDto> reorder = reorderService.suggestions();
        String requisitionNumber = null;
        int requisitionLines = 0;

        if (wantRequisition) {
            Set<Long> alreadyOpen = purchaseRequisitionService.openRequisitionProductIds();
            List<ReorderRequisitionRequest.Entry> entries = new ArrayList<>();
            for (ReorderSuggestionDto s : reorder) {
                if (alreadyOpen.contains(s.productId())) continue;
                ReorderRequisitionRequest.Entry e = new ReorderRequisitionRequest.Entry();
                e.setProductId(s.productId());
                e.setQuantity(Math.max(s.suggestedQuantity(), 1));
                entries.add(e);
            }
            if (entries.isEmpty()) {
                message.append("No new products to reorder. ");
            } else {
                ReorderRequisitionRequest request = new ReorderRequisitionRequest();
                request.setNotes("Created automatically by the daily reorder job");
                request.setLines(entries);
                PurchaseRequisitionDto created = reorderService.createRequisition(request);
                requisitionNumber = created.requisitionNumber();
                requisitionLines = entries.size();
                message.append("Drafted requisition ").append(requisitionNumber).append(" with ").append(requisitionLines).append(" line(s). ");
            }
        }

        List<ProductDto> lowStock = catalogService.listLowStockPrimary();
        boolean digestSent = false;
        if (wantDigest) {
            if (lowStock.isEmpty() && reorder.isEmpty()) {
                message.append("Nothing is low, so no digest was sent.");
            } else {
                digestSent = sendDigest(settings, lowStock, reorder, requisitionNumber, requisitionLines);
                message.append(digestSent ? "Digest emailed." : "Digest not sent (no notifications email or SMTP not configured).");
            }
        }
        if (!wantRequisition && !wantDigest) {
            message.append("Both daily stock jobs are switched off in Inventory settings.");
        }
        return new RunResult(wantRequisition, requisitionNumber, requisitionLines, wantDigest, digestSent,
                lowStock.size(), reorder.size(), message.toString().trim());
    }

    private boolean sendDigest(StoreSettings settings, List<ProductDto> lowStock, List<ReorderSuggestionDto> reorder,
                               String requisitionNumber, int requisitionLines) {
        String to = settings.getNotificationsEmail();
        if (to == null || to.isBlank() || !mailService.isConfigured()) {
            return false;
        }
        StringBuilder html = new StringBuilder("<p>Daily stock digest for ")
                .append(escape(settings.getBusinessName())).append(".</p>");
        if (requisitionNumber != null) {
            html.append("<p>A draft purchase requisition <b>").append(escape(requisitionNumber)).append("</b> with ")
                    .append(requisitionLines).append(" line(s) was created for review.</p>");
        }
        if (!lowStock.isEmpty()) {
            html.append("<h3>Below the low-stock threshold (").append(lowStock.size()).append(")</h3>")
                    .append(table(List.of("SKU", "Product", "On hand"), lowStock.stream()
                            .map(p -> List.of(nz(p.sku()), nz(p.name()), String.valueOf(p.stockQuantity()))).toList()));
        }
        if (!reorder.isEmpty()) {
            html.append("<h3>At or below reorder level (").append(reorder.size()).append(")</h3>")
                    .append(table(List.of("SKU", "Product", "Sellable", "On order", "Reorder level", "Suggested qty"), reorder.stream()
                            .map(r -> List.of(nz(r.sku()), nz(r.productName()), String.valueOf(r.onHand()), String.valueOf(r.onOrder()),
                                    String.valueOf(r.reorderLevel()), String.valueOf(r.suggestedQuantity()))).toList()));
        }
        try {
            mailService.send(to.trim(), "Daily stock digest: " + lowStock.size() + " low, " + reorder.size() + " to reorder", html.toString(), List.of());
            return true;
        } catch (Exception ex) {
            log.warn("Could not send the stock digest: {}", ex.getMessage());
            return false;
        }
    }

    private static String table(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("<table style=\"border-collapse:collapse\" border=\"1\" cellpadding=\"6\"><tr>");
        for (String h : headers) sb.append("<th align=\"left\">").append(escape(h)).append("</th>");
        sb.append("</tr>");
        for (List<String> row : rows) {
            sb.append("<tr>");
            for (String cell : row) sb.append("<td>").append(escape(cell)).append("</td>");
            sb.append("</tr>");
        }
        return sb.append("</table>").toString();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static ZoneId zoneOf(StoreSettings settings) {
        try {
            if (settings.getTimeZone() != null && !settings.getTimeZone().isBlank()) {
                return ZoneId.of(settings.getTimeZone().trim());
            }
        } catch (Exception ignored) {
            // fall through to the server's own zone
        }
        return ZoneId.systemDefault();
    }

    private static LocalTime timeOf(StoreSettings settings) {
        try {
            return LocalTime.parse(settings.getStockJobTime() == null ? "06:30" : settings.getStockJobTime().trim());
        } catch (Exception ex) {
            return LocalTime.of(6, 30);
        }
    }
}
