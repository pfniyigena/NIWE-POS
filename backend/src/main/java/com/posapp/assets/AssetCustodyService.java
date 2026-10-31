package com.posapp.assets;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.posapp.assets.dto.AssetDtos.CustodyDto;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Who holds an asset: check-out and check-in with the date and condition, the full history, and a handover sheet to sign.
 * {@code FixedAsset.custodianName} / {@code assignedTo} always show the current holder.
 */
@Service
public class AssetCustodyService {

    private final AssetCustodyRepository repository;
    private final UserRepository userRepository;
    private final AssetHistoryService history;
    private final AuditLogService auditLogService;
    private final StoreSettingsRepository storeSettingsRepository;

    public AssetCustodyService(AssetCustodyRepository repository, UserRepository userRepository, AssetHistoryService history,
                               AuditLogService auditLogService, StoreSettingsRepository storeSettingsRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.history = history;
        this.auditLogService = auditLogService;
        this.storeSettingsRepository = storeSettingsRepository;
    }

    @Transactional
    public void deleteAllOf(Long assetId) {
        repository.deleteAll(repository.findByAssetIdOrderByCheckedOutOnDescIdDesc(assetId));
    }

    @Transactional(readOnly = true)
    public List<CustodyDto> historyOf(Long assetId) {
        return repository.findByAssetIdOrderByCheckedOutOnDescIdDesc(assetId).stream().map(CustodyDto::from).toList();
    }

    /** Hands the asset to someone (closing the current holder's spell first). The caller saves the asset. */
    @Transactional
    public void checkOut(FixedAsset asset, Long userId, String name, LocalDate date, String condition, String note) {
        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw ApiException.conflict("A disposed asset can't be handed to anyone.");
        }
        String holder = name != null && !name.isBlank() ? name.trim() : null;
        if (userId != null) {
            AppUser user = userRepository.findById(userId).orElseThrow(() -> ApiException.badRequest("That user was not found."));
            holder = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
        }
        if (holder == null) {
            throw ApiException.badRequest("Choose who gets the asset or type a name.");
        }
        LocalDate on = date != null ? date : LocalDate.now();
        if (on.isBefore(asset.getPurchaseDate())) {
            throw ApiException.badRequest("The date can't be before the purchase date.");
        }
        if (on.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("The date can't be in the future.");
        }
        repository.findFirstByAssetIdAndCheckedInOnIsNullOrderByIdDesc(asset.getId()).ifPresent(open -> {
            open.setCheckedInOn(on.isBefore(open.getCheckedOutOn()) ? open.getCheckedOutOn() : on);
            open.setInCondition(clip(condition, 255));
            repository.save(open);
        });
        AssetCustody c = new AssetCustody();
        c.setAssetId(asset.getId());
        c.setCustodianUserId(userId);
        c.setCustodianName(holder);
        c.setCheckedOutOn(on);
        c.setOutCondition(clip(condition, 255));
        c.setNote(clip(note, 1000));
        c.setCreatedByName(AssetHistoryService.actor().name());
        repository.save(c);
        asset.setCustodianUserId(userId);
        asset.setCustodianName(holder);
        asset.setAssignedTo(holder);
        asset.setCheckedOutOn(on);
        history.event(asset.getId(), "CHECK_OUT", "Handed to " + holder + " on " + on + (condition != null && !condition.isBlank() ? " (" + condition.trim() + ")" : ""));
        auditLogService.record("ASSET_CHECKED_OUT", "Asset " + asset.getCode() + " handed to " + holder, "FixedAsset", asset.getId());
    }

    /** The holder gives the asset back. The caller saves the asset. */
    @Transactional
    public void checkIn(FixedAsset asset, LocalDate date, String condition, String note) {
        AssetCustody open = repository.findFirstByAssetIdAndCheckedInOnIsNullOrderByIdDesc(asset.getId())
                .orElseThrow(() -> ApiException.badRequest("Nobody is holding this asset."));
        LocalDate on = date != null ? date : LocalDate.now();
        if (on.isBefore(open.getCheckedOutOn())) {
            throw ApiException.badRequest("The return date can't be before the hand-out date (" + open.getCheckedOutOn() + ").");
        }
        if (on.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("The date can't be in the future.");
        }
        open.setCheckedInOn(on);
        open.setInCondition(clip(condition, 255));
        if (note != null && !note.isBlank()) open.setNote(clip((open.getNote() == null ? "" : open.getNote() + " | ") + note.trim(), 1000));
        repository.save(open);
        history.event(asset.getId(), "CHECK_IN", "Returned by " + open.getCustodianName() + " on " + on + (condition != null && !condition.isBlank() ? " (" + condition.trim() + ")" : ""));
        auditLogService.record("ASSET_CHECKED_IN", "Asset " + asset.getCode() + " returned by " + open.getCustodianName(), "FixedAsset", asset.getId());
        asset.setCustodianUserId(null);
        asset.setCustodianName(null);
        asset.setAssignedTo(null);
        asset.setCheckedOutOn(null);
    }

    /** The "Assigned to" box of the edit form: a different name hands the asset over, an empty box takes it back. */
    @Transactional
    public void applyAssignment(FixedAsset asset, String assignedTo, LocalDate date) {
        String wanted = assignedTo == null || assignedTo.isBlank() ? null : assignedTo.trim();
        String current = asset.getCustodianName();
        if ((wanted == null && current == null) || (wanted != null && wanted.equals(current))) {
            return;
        }
        if (wanted == null) {
            checkIn(asset, date, null, null);
        } else {
            checkOut(asset, null, wanted, date, null, null);
        }
    }

    // ---- handover sheet ----

    @Transactional(readOnly = true)
    public byte[] handoverPdf(FixedAsset asset, String categoryName) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            StoreSettings s = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
            String business = s != null && s.getBusinessName() != null && !s.getBusinessName().isBlank() ? s.getBusinessName().trim() : "Business";
            doc.add(new Paragraph(business, font(true, 15)));
            Paragraph title = new Paragraph("Asset handover sheet", font(true, 18));
            title.setSpacingBefore(6);
            title.setSpacingAfter(10);
            doc.add(title);
            PdfPTable t = new PdfPTable(new float[]{1.2f, 3});
            t.setWidthPercentage(100);
            row(t, "Tag", asset.getCode());
            row(t, "Asset", asset.getName());
            row(t, "Category", categoryName);
            row(t, "Serial number", asset.getSerialNumber());
            row(t, "Location", asset.getLocation());
            row(t, "Purchased", String.valueOf(asset.getPurchaseDate()));
            row(t, "Cost", money(asset.getCost()));
            row(t, "Warranty until", asset.getWarrantyExpiresOn() != null ? asset.getWarrantyExpiresOn().toString() : null);
            doc.add(t);
            AssetCustody open = repository.findFirstByAssetIdAndCheckedInOnIsNullOrderByIdDesc(asset.getId()).orElse(null);
            Paragraph h = new Paragraph(open != null ? "Handed to" : "Currently not held by anyone", font(true, 12));
            h.setSpacingBefore(14);
            h.setSpacingAfter(6);
            doc.add(h);
            if (open != null) {
                PdfPTable c = new PdfPTable(new float[]{1.2f, 3});
                c.setWidthPercentage(100);
                row(c, "Name", open.getCustodianName());
                row(c, "Date", String.valueOf(open.getCheckedOutOn()));
                row(c, "Condition", open.getOutCondition());
                row(c, "Note", open.getNote());
                doc.add(c);
            }
            Paragraph terms = new Paragraph("The person named above has received this asset in the condition shown and agrees to look after it, "
                    + "use it for the business only, and return it when asked or when leaving.", font(false, 9.5f));
            terms.setSpacingBefore(16);
            doc.add(terms);
            PdfPTable sig = new PdfPTable(2);
            sig.setWidthPercentage(100);
            sig.setSpacingBefore(40);
            sig.addCell(sigCell("Received by (signature and date)"));
            sig.addCell(sigCell("Handed over by (signature and date)"));
            doc.add(sig);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw ApiException.badRequest("The handover sheet could not be made.");
        }
    }

    private static PdfPCell sigCell(String label) {
        PdfPCell c = new PdfPCell(new Phrase(label, font(false, 9)));
        c.setBorder(com.lowagie.text.Rectangle.TOP);
        c.setPaddingTop(6);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        return c;
    }

    private static void row(PdfPTable t, String label, String value) {
        PdfPCell a = new PdfPCell(new Phrase(label, font(true, 10)));
        a.setBorderColor(java.awt.Color.LIGHT_GRAY);
        a.setPadding(5);
        PdfPCell b = new PdfPCell(new Phrase(value != null && !value.isBlank() ? value : "-", font(false, 10)));
        b.setBorderColor(java.awt.Color.LIGHT_GRAY);
        b.setPadding(5);
        t.addCell(a);
        t.addCell(b);
    }

    private static Font font(boolean bold, float size) {
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size);
    }

    private static String money(java.math.BigDecimal v) {
        return v == null ? "" : new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v);
    }

    private static String clip(String v, int max) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
