package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtFeeInvoiceDtos.*;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The invoice the agency gives an owner for its management fee. It is made when an owner statement is agreed (one per statement), carries the fee and the VAT on it for each property, and
 * is settled by the agency keeping the money out of the rent it collected, so nothing is paid on it. It is a document only: the statement already books the fee, the VAT and what is owed
 * to the owner, so the invoice never posts to the books a second time. Reopening the statement voids the invoice (its number is kept); agreeing it again makes a new one.
 */
@Service
public class PmFeeInvoiceService {

    private final PmFeeInvoiceRepository repository;
    private final PmFeeInvoiceLineRepository lineRepository;
    private final PmOwnerStatementRepository statementRepository;
    private final PmStatementLineRepository statementLineRepository;
    private final PmSettingsService settings;
    private final StoreSettingsRepository storeSettingsRepository;
    private final PmViews views;
    private final PmFeeInvoicePdf pdf;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    public PmFeeInvoiceService(PmFeeInvoiceRepository repository, PmFeeInvoiceLineRepository lineRepository, PmOwnerStatementRepository statementRepository,
                               PmStatementLineRepository statementLineRepository, PmSettingsService settings, StoreSettingsRepository storeSettingsRepository, PmViews views, PmFeeInvoicePdf pdf,
                               PropertyManagementAccess access, AuditLogService auditLogService) {
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.statementRepository = statementRepository;
        this.statementLineRepository = statementLineRepository;
        this.settings = settings;
        this.storeSettingsRepository = storeSettingsRepository;
        this.views = views;
        this.pdf = pdf;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private PmFeeInvoice find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Fee invoice not found: " + id));
    }

    // ------------------------------------------------------------------ making and voiding (inside the statement's own transaction)

    /** Makes the invoice for an agreed statement. Returns null when the statement has no management fee. */
    @Transactional
    public PmFeeInvoice issueFor(PmOwnerStatement st, String by) {
        if (st.getManagementFee() == null || st.getManagementFee().signum() <= 0) return null;
        if (repository.findByStatementIdAndStatus(st.getId(), PmFeeInvoiceStatus.ISSUED).isPresent()) throw ApiException.conflict("This statement already has an invoice for the management fee.");
        Snapshot s = views.snapshot();
        PmOwner owner = s.owners.get(st.getOwnerId());
        List<PmStatementLine> lines = statementLineRepository.findByStatementId(st.getId());
        Map<Long, BigDecimal> vatByProperty = new HashMap<>();
        for (PmStatementLine l : lines) if (l.getKind() == StatementLineKind.VAT && l.getPropertyId() != null) vatByProperty.merge(l.getPropertyId(), l.getAmount().negate(), BigDecimal::add);
        List<PmStatementLine> fees = lines.stream().filter(l -> l.getKind() == StatementLineKind.FEE).sorted(Comparator.comparing(PmStatementLine::getId)).toList();
        BigDecimal net = BigDecimal.ZERO, vat = BigDecimal.ZERO;
        for (PmStatementLine f : fees) {
            net = net.add(f.getAmount().negate());
            if (f.getPropertyId() != null) vat = vat.add(vatByProperty.getOrDefault(f.getPropertyId(), BigDecimal.ZERO));
        }
        if (net.compareTo(st.getManagementFee()) != 0 || vat.compareTo(st.getVatAmount()) != 0)
            throw ApiException.conflict("The fee lines of statement " + st.getCode() + " do not add up to its totals. Refresh the statement and try again.");
        StoreSettings store = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        PmFeeInvoice inv = new PmFeeInvoice();
        inv.setNumber(settings.nextCode("FI"));
        inv.setStatementId(st.getId());
        inv.setStatementCode(st.getCode());
        inv.setOwnerId(st.getOwnerId());
        inv.setIssueDate(LocalDate.now());
        inv.setPeriodFrom(st.getPeriodFrom());
        inv.setPeriodTo(st.getPeriodTo());
        inv.setNetAmount(net);
        inv.setVatPercent(vat.signum() > 0 ? st.getVatPercent() : null);
        inv.setVatAmount(vat);
        inv.setTotalAmount(net.add(vat));
        inv.setOwnerName(owner == null ? "Owner" : owner.getName());
        inv.setOwnerTaxNumber(owner == null ? null : owner.getTaxNumber());
        inv.setOwnerAddress(owner == null ? null : owner.getAddress());
        if (store != null) {
            inv.setSellerName(store.getBusinessName());
            inv.setSellerTaxId(store.getTaxId());
            inv.setSellerAddress(store.getAddress());
            inv.setSellerPhone(store.getPhone());
            inv.setSellerEmail(store.getEmail());
        }
        inv.setIssuedByName(by);
        repository.save(inv);
        int order = 0;
        for (PmStatementLine f : fees) {
            PmProperty p = f.getPropertyId() == null ? null : s.properties.get(f.getPropertyId());
            PmFeeInvoiceLine l = new PmFeeInvoiceLine();
            l.setInvoiceId(inv.getId());
            l.setSortOrder(order++);
            l.setPropertyId(f.getPropertyId());
            l.setDescription((p == null ? "" : p.getName() + " - ") + f.getDescription());
            l.setBaseAmount(f.getBaseAmount());
            l.setFeePercent(f.getFeePercent());
            l.setNetAmount(f.getAmount().negate());
            l.setVatAmount(f.getPropertyId() == null ? BigDecimal.ZERO : vatByProperty.getOrDefault(f.getPropertyId(), BigDecimal.ZERO));
            lineRepository.save(l);
        }
        auditLogService.record("PM_FEE_INVOICE_ISSUED", "Issued " + inv.getNumber() + " to " + inv.getOwnerName() + " for statement " + st.getCode() + ", total " + inv.getTotalAmount().toPlainString(), "PmFeeInvoice", inv.getId());
        return inv;
    }

    /** The statement was reopened: the invoice in force is voided. */
    @Transactional
    public void voidFor(PmOwnerStatement st, String reason) {
        PmFeeInvoice inv = repository.findByStatementIdAndStatus(st.getId(), PmFeeInvoiceStatus.ISSUED).orElse(null);
        if (inv == null) return;
        inv.setStatus(PmFeeInvoiceStatus.VOID);
        inv.setVoidReason(reason);
        inv.setVoidedAt(Instant.now());
        repository.save(inv);
        auditLogService.record("PM_FEE_INVOICE_VOIDED", "Voided " + inv.getNumber() + ": " + reason, "PmFeeInvoice", inv.getId());
    }

    /** For an agreed or paid statement that has no invoice (agreed before invoices existed): makes it now. */
    @Transactional
    public FeeInvoiceDto issue(Actor a, Long statementId) {
        access.on();
        PmOwnerStatement st = statementRepository.findById(statementId).orElseThrow(() -> ApiException.notFound("Statement not found: " + statementId));
        if (st.getStatus() == StatementStatus.DRAFT) throw ApiException.conflict("Agree the statement first. An invoice is made when it is agreed.");
        if (repository.findByStatementIdAndStatus(statementId, PmFeeInvoiceStatus.ISSUED).isPresent()) throw ApiException.conflict("This statement already has an invoice.");
        if (st.getManagementFee().signum() <= 0) throw ApiException.conflict("This statement has no management fee, so there is nothing to invoice.");
        PmFeeInvoice inv = issueFor(st, PropertyManagementAccess.name(a.user()));
        return dto(a, views.snapshot(), inv);
    }

    // ------------------------------------------------------------------ reading

    private FeeInvoiceRow row(PmFeeInvoice i) {
        return new FeeInvoiceRow(i.getId(), i.getNumber(), i.getStatus(), i.getIssueDate(), i.getOwnerId(), i.getOwnerName(), i.getOwnerTaxNumber(), i.getStatementId(), i.getStatementCode(),
                i.getPeriodFrom(), i.getPeriodTo(), i.getNetAmount(), i.getVatPercent(), i.getVatAmount(), i.getTotalAmount());
    }

    FeeInvoiceDto dto(Actor a, Snapshot s, PmFeeInvoice i) {
        List<FeeInvoiceLineDto> lines = lineRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(i.getId()).stream().map(l -> {
            PmProperty p = l.getPropertyId() == null ? null : s.properties.get(l.getPropertyId());
            return new FeeInvoiceLineDto(l.getId(), l.getPropertyId(), p == null ? null : p.getName(), l.getDescription(), l.getBaseAmount(), l.getFeePercent(), l.getNetAmount(), l.getVatAmount(),
                    l.getNetAmount().add(l.getVatAmount()));
        }).toList();
        boolean exists = i.getStatementId() != null && statementRepository.existsById(i.getStatementId());
        return new FeeInvoiceDto(row(i), i.getVatAmount().signum() > 0, i.getOwnerAddress(), i.getSellerName(), i.getSellerTaxId(), i.getSellerAddress(), i.getSellerPhone(), i.getSellerEmail(),
                i.getIssuedByName(), i.getVoidReason(), i.getVoidedAt(), lines, exists, a.manager());
    }

    @Transactional(readOnly = true)
    public FeeInvoiceDto get(Actor a, Long id) {
        access.on();
        return dto(a, views.snapshot(), find(id));
    }

    /** The invoice in force for a statement, else the latest voided one, else nothing. */
    @Transactional(readOnly = true)
    public FeeInvoiceDto forStatement(Actor a, Long statementId) {
        access.on();
        List<PmFeeInvoice> all = repository.findByStatementIdOrderByIdDesc(statementId);
        PmFeeInvoice pick = all.stream().filter(i -> i.getStatus() == PmFeeInvoiceStatus.ISSUED).findFirst().orElse(all.isEmpty() ? null : all.get(0));
        return pick == null ? null : dto(a, views.snapshot(), pick);
    }

    @Transactional(readOnly = true)
    public List<FeeInvoiceRow> list(Long ownerId, PmFeeInvoiceStatus status, LocalDate from, LocalDate to, String q) {
        access.on();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return repository.findAll().stream()
                .filter(i -> ownerId == null || ownerId.equals(i.getOwnerId()))
                .filter(i -> status == null || i.getStatus() == status)
                .filter(i -> from == null || !i.getIssueDate().isBefore(from))
                .filter(i -> to == null || !i.getIssueDate().isAfter(to))
                .filter(i -> needle.isEmpty() || i.getNumber().toLowerCase(Locale.ROOT).contains(needle) || i.getOwnerName().toLowerCase(Locale.ROOT).contains(needle)
                        || i.getStatementCode() != null && i.getStatementCode().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(PmFeeInvoice::getIssueDate).thenComparing(PmFeeInvoice::getId).reversed())
                .map(this::row).collect(Collectors.toCollection(ArrayList::new));
    }

    /** What the invoices in force among these rows add up to. */
    public static FeeInvoiceTotals totals(List<FeeInvoiceRow> rows) {
        List<FeeInvoiceRow> live = rows.stream().filter(r -> r.status() == PmFeeInvoiceStatus.ISSUED).toList();
        BigDecimal net = live.stream().map(FeeInvoiceRow::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = live.stream().map(FeeInvoiceRow::vatAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FeeInvoiceTotals(live.size(), net, vat, net.add(vat));
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        access.on();
        PmFeeInvoice i = find(id);
        FeeInvoiceDto d = dto(new Actor(null, false, true, false), views.snapshot(), i);
        StoreSettings store = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        return pdf.build(d, store);
    }

    /** The invoice in force for a statement as an e-mail attachment: its file name and bytes, or null when there is none. */
    @Transactional(readOnly = true)
    public Attachment attachmentFor(Long statementId) {
        PmFeeInvoice i = repository.findByStatementIdAndStatus(statementId, PmFeeInvoiceStatus.ISSUED).orElse(null);
        return i == null ? null : new Attachment(i.getNumber().toLowerCase(Locale.ROOT) + ".pdf", pdf(i.getId()));
    }

    public record Attachment(String fileName, byte[] bytes) {
    }

    public String filename(Long id) {
        return find(id).getNumber().toLowerCase(Locale.ROOT) + ".pdf";
    }
}
