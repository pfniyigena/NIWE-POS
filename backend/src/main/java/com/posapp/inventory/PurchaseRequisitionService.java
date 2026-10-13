package com.posapp.inventory;

import com.posapp.alerts.AlertMailService;
import com.posapp.audit.AuditLogService;
import com.posapp.catalog.CatalogService;
import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.inventory.dto.ConvertRequisitionRequest;
import com.posapp.inventory.dto.PurchaseOrderDto;
import com.posapp.inventory.dto.PurchaseOrderRequest;
import com.posapp.inventory.dto.PurchaseRequisitionDto;
import com.posapp.inventory.dto.PurchaseRequisitionRequest;
import com.posapp.security.AppUserPrincipal;
import com.posapp.user.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The internal "can we buy this" approval gate placed in front of {@link PurchaseOrder} (Fourier:
 * "Build Quotation, Invoice, and the purchase-requisition approval gate ... without touching
 * warehousing"). DRAFT -&gt; PENDING_APPROVAL -&gt; APPROVED/REJECTED, or DRAFT/PENDING_APPROVAL
 * -&gt; CANCELLED — see {@link PurchaseRequisitionStatus}'s javadoc. Only {@link
 * #convertToPurchaseOrder} ever creates a real {@link PurchaseOrder} — via {@code
 * PurchaseOrderService}'s own public {@code createDraft}, same as every other cross-boundary call
 * in this app, even though (unlike Quotation/Invoice) there's no actual module boundary to cross
 * here at all, since this class lives in the same package as the one it's calling into.
 * <p>
 * {@link #currentActorFullName} pulls the acting user off {@link SecurityContextHolder} the same
 * way {@code AuditLogService.currentActor} does, so {@code requestedByFullName}/{@code
 * approvedByFullName} are stamped without threading an extra parameter through every method here.
 */
@Service
public class PurchaseRequisitionService {

    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    private final CatalogService catalogService;
    private final AuditLogService auditLogService;
    private final PurchaseOrderService purchaseOrderService;
    private final AlertMailService alertMailService;

    public PurchaseRequisitionService(PurchaseRequisitionRepository purchaseRequisitionRepository,
                                       CatalogService catalogService,
                                       AuditLogService auditLogService,
                                       PurchaseOrderService purchaseOrderService,
                                       AlertMailService alertMailService) {
        this.purchaseRequisitionRepository = purchaseRequisitionRepository;
        this.catalogService = catalogService;
        this.auditLogService = auditLogService;
        this.purchaseOrderService = purchaseOrderService;
        this.alertMailService = alertMailService;
    }

    @Transactional(readOnly = true)
    public List<PurchaseRequisitionDto> listAll() {
        return purchaseRequisitionRepository.findAllByOrderByRequestDateDesc().stream().map(PurchaseRequisitionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseRequisitionDto getById(Long id) {
        return PurchaseRequisitionDto.from(getEntityOrThrow(id));
    }

    /** Ids of products already waiting on a requisition that is still open (draft, pending approval or approved but not yet converted). */
    @Transactional(readOnly = true)
    public java.util.Set<Long> openRequisitionProductIds() {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (PurchaseRequisition r : purchaseRequisitionRepository.findByStatusIn(java.util.List.of(
                PurchaseRequisitionStatus.DRAFT, PurchaseRequisitionStatus.PENDING_APPROVAL, PurchaseRequisitionStatus.APPROVED))) {
            for (PurchaseRequisitionLine line : r.getLines()) {
                if (line.getProduct() != null) ids.add(line.getProduct().getId());
            }
        }
        return ids;
    }

    @Transactional
    public PurchaseRequisitionDto createDraft(PurchaseRequisitionRequest request) {
        PurchaseRequisition r = new PurchaseRequisition();
        // Own "PR-" prefix — see PurchaseRequisition's class javadoc for why this never collides
        // with PurchaseOrder's "PO-", SalesOrder's "SO-" or Quotation's "QT-".
        r.setRequisitionNumber("PR-" + System.currentTimeMillis());
        r.setRequestedByFullName(currentActorFullName());
        applyRequest(r, request);
        PurchaseRequisition saved = purchaseRequisitionRepository.save(r);
        auditLogService.record("PURCHASE_REQUISITION_CREATED",
                "Created purchase requisition " + saved.getRequisitionNumber()
                        + " (" + saved.getLines().size() + " line" + (saved.getLines().size() == 1 ? "" : "s") + ")",
                "PurchaseRequisition", saved.getId());
        return PurchaseRequisitionDto.from(saved);
    }

    /** Only a DRAFT can still be edited — see PurchaseRequisitionStatus's javadoc. */
    @Transactional
    public PurchaseRequisitionDto updateDraft(Long id, PurchaseRequisitionRequest request) {
        PurchaseRequisition r = getEntityOrThrow(id);
        if (r.getStatus() != PurchaseRequisitionStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft requisition can be edited — " + r.getRequisitionNumber() + " is " + r.getStatus());
        }
        r.getLines().clear();
        applyRequest(r, request);
        PurchaseRequisition saved = purchaseRequisitionRepository.save(r);
        auditLogService.record("PURCHASE_REQUISITION_UPDATED", "Updated purchase requisition " + saved.getRequisitionNumber(), "PurchaseRequisition", saved.getId());
        return PurchaseRequisitionDto.from(saved);
    }

    /**
     * DRAFT -> PENDING_APPROVAL: locks the requisition to further line edits and puts it in front
     * of an admin to decide on. Fourier: "Wiring a handful of business events to that same
     * mailer" — this transition is the trigger for the "purchase requisition awaiting approval"
     * alert, the one moment it actually starts waiting on someone.
     */
    @Transactional
    public PurchaseRequisitionDto submit(Long id) {
        PurchaseRequisition r = getEntityOrThrow(id);
        if (r.getStatus() != PurchaseRequisitionStatus.DRAFT) {
            throw ApiException.badRequest("Only a draft requisition can be submitted — " + r.getRequisitionNumber() + " is " + r.getStatus());
        }
        r.setStatus(PurchaseRequisitionStatus.PENDING_APPROVAL);
        PurchaseRequisition saved = purchaseRequisitionRepository.save(r);
        auditLogService.record("PURCHASE_REQUISITION_SUBMITTED", "Submitted purchase requisition " + saved.getRequisitionNumber() + " for approval", "PurchaseRequisition", saved.getId());
        alertMailService.alertRequisitionAwaitingApproval(saved);
        return PurchaseRequisitionDto.from(saved);
    }

    /** PENDING_APPROVAL -> APPROVED — see {@link #convertToPurchaseOrder} for turning this into a real order. */
    @Transactional
    public PurchaseRequisitionDto approve(Long id) {
        PurchaseRequisition r = getEntityOrThrow(id);
        if (r.getStatus() != PurchaseRequisitionStatus.PENDING_APPROVAL) {
            throw ApiException.badRequest("Only a pending requisition can be approved — " + r.getRequisitionNumber() + " is " + r.getStatus());
        }
        r.setStatus(PurchaseRequisitionStatus.APPROVED);
        r.setApprovedAt(Instant.now());
        r.setApprovedByFullName(currentActorFullName());
        PurchaseRequisition saved = purchaseRequisitionRepository.save(r);
        auditLogService.record("PURCHASE_REQUISITION_APPROVED", "Approved purchase requisition " + saved.getRequisitionNumber(), "PurchaseRequisition", saved.getId());
        return PurchaseRequisitionDto.from(saved);
    }

    /** PENDING_APPROVAL -> REJECTED. */
    @Transactional
    public PurchaseRequisitionDto reject(Long id, String reason) {
        PurchaseRequisition r = getEntityOrThrow(id);
        if (r.getStatus() != PurchaseRequisitionStatus.PENDING_APPROVAL) {
            throw ApiException.badRequest("Only a pending requisition can be rejected — " + r.getRequisitionNumber() + " is " + r.getStatus());
        }
        r.setStatus(PurchaseRequisitionStatus.REJECTED);
        r.setRejectedAt(Instant.now());
        r.setRejectionReason(reason);
        PurchaseRequisition saved = purchaseRequisitionRepository.save(r);
        auditLogService.record("PURCHASE_REQUISITION_REJECTED", "Rejected purchase requisition " + saved.getRequisitionNumber(), "PurchaseRequisition", saved.getId());
        return PurchaseRequisitionDto.from(saved);
    }

    /** Allowed from DRAFT or PENDING_APPROVAL — withdrawing a request before it's been decided on, same leniency every other module's cancel() allows pre-commitment. */
    @Transactional
    public PurchaseRequisitionDto cancel(Long id) {
        PurchaseRequisition r = getEntityOrThrow(id);
        if (r.getStatus() != PurchaseRequisitionStatus.DRAFT && r.getStatus() != PurchaseRequisitionStatus.PENDING_APPROVAL) {
            throw ApiException.badRequest("Cannot cancel purchase requisition " + r.getRequisitionNumber() + " — it is " + r.getStatus());
        }
        r.setStatus(PurchaseRequisitionStatus.CANCELLED);
        PurchaseRequisition saved = purchaseRequisitionRepository.save(r);
        auditLogService.record("PURCHASE_REQUISITION_CANCELLED", "Cancelled purchase requisition " + saved.getRequisitionNumber(), "PurchaseRequisition", saved.getId());
        return PurchaseRequisitionDto.from(saved);
    }

    /**
     * APPROVED -&gt; CONVERTED: the one moment a supplier and pricing are attached at all (see
     * class javadoc) — builds a {@code PurchaseOrderRequest} from the given supplier/expected
     * date/per-line unit costs and this requisition's own lines, then creates it via {@code
     * PurchaseOrderService.createDraft}. Returns the new Purchase Order (not this requisition) so
     * the caller can navigate straight to it, same convention as {@code
     * QuotationService.convertToSalesOrder}.
     */
    @Transactional
    public PurchaseOrderDto convertToPurchaseOrder(Long id, ConvertRequisitionRequest request) {
        PurchaseRequisition r = getEntityOrThrow(id);
        if (r.getStatus() != PurchaseRequisitionStatus.APPROVED) {
            throw ApiException.badRequest("Only an approved requisition can be converted — " + r.getRequisitionNumber() + " is " + r.getStatus());
        }

        PurchaseOrderRequest poRequest = new PurchaseOrderRequest();
        poRequest.setSupplierId(request.getSupplierId());
        poRequest.setExpectedDate(request.getExpectedDate());
        poRequest.setNotes("Converted from requisition " + r.getRequisitionNumber() + (r.getNotes() != null && !r.getNotes().isBlank() ? " — " + r.getNotes() : ""));
        poRequest.setLines(r.getLines().stream().map(line -> {
            ConvertRequisitionRequest.LineEntry costEntry = request.getLines().stream()
                    .filter(e -> e.getLineId().equals(line.getId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("No unit cost supplied for line '" + line.getProductName() + "'"));
            PurchaseOrderRequest.LineEntry entry = new PurchaseOrderRequest.LineEntry();
            entry.setProductId(line.getProduct() != null ? line.getProduct().getId() : null);
            entry.setQuantityOrdered(line.getQuantityRequested());
            entry.setUnitCost(costEntry.getUnitCost());
            return entry;
        }).collect(Collectors.toList()));

        PurchaseOrderDto created = purchaseOrderService.createDraft(poRequest);

        r.setStatus(PurchaseRequisitionStatus.CONVERTED);
        r.setConvertedPurchaseOrderId(created.id());
        r.setConvertedPurchaseOrderNumber(created.orderNumber());
        PurchaseRequisition saved = purchaseRequisitionRepository.save(r);
        auditLogService.record("PURCHASE_REQUISITION_CONVERTED",
                "Converted purchase requisition " + saved.getRequisitionNumber() + " into purchase order " + created.orderNumber(),
                "PurchaseRequisition", saved.getId());
        return created;
    }

    PurchaseRequisition getEntityOrThrow(Long id) {
        return purchaseRequisitionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Purchase requisition not found: " + id));
    }

    private void applyRequest(PurchaseRequisition r, PurchaseRequisitionRequest request) {
        r.setNotes(request.getNotes());
        List<PurchaseRequisitionLine> lines = request.getLines().stream().map(entry -> {
            Product product = catalogService.getProductOrThrow(entry.getProductId());
            PurchaseRequisitionLine line = new PurchaseRequisitionLine();
            line.setRequisition(r);
            line.setProduct(product);
            line.setProductName(product.getName());
            line.setQuantityRequested(entry.getQuantityRequested());
            line.setNote(entry.getNote());
            return line;
        }).collect(Collectors.toList());
        r.getLines().addAll(lines);
    }

    /** Same SecurityContextHolder pattern as AuditLogService.currentActor — "system" (via null-safe caller handling) rather than failing when nothing is authenticated. */
    private String currentActorFullName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            return "system";
        }
        AppUser user = principal.getUser();
        return user != null ? user.getFullName() : "system";
    }
}
