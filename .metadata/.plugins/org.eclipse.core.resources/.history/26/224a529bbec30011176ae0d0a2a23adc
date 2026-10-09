package com.posapp.branch;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.dto.BranchDto;
import com.posapp.branch.dto.BranchReceiptInfo;
import com.posapp.branch.dto.BranchRequest;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for {@link Branch} plus the helpers other modules use to tag documents. Deliberately
 * depends on nothing but its own repository and {@code AuditLogService} (in particular NOT on
 * {@code WarehouseService}): WarehouseService depends on this class, so keeping the dependency
 * one-directional avoids any circular-bean risk. Never hard-deletes — a branch already referenced
 * by past documents must keep existing, so retiring one is a deactivate.
 */
@Service
public class BranchService {

    private final BranchRepository repository;
    private final AuditLogService auditLogService;

    public BranchService(BranchRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<BranchDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream().map(BranchDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BranchDto> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(BranchDto::from).toList();
    }

    @Transactional
    public BranchDto create(BranchRequest request) {
        String code = request.code().trim();
        String name = request.name().trim();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("A branch with code '" + code + "' already exists");
        }
        if (repository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A branch named '" + name + "' already exists");
        }
        Branch branch = new Branch();
        branch.setCode(code);
        branch.setName(name);
        applyRequest(branch, request);
        Branch saved = repository.save(branch);
        auditLogService.record("BRANCH_CREATED", "Created branch " + saved.getCode() + " (" + saved.getName() + ")", "Branch", saved.getId());
        return BranchDto.from(saved);
    }

    @Transactional
    public BranchDto update(Long id, BranchRequest request) {
        Branch branch = getEntityOrThrow(id);
        String code = request.code().trim();
        String name = request.name().trim();
        if (!branch.getCode().equalsIgnoreCase(code) && repository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("A branch with code '" + code + "' already exists");
        }
        if (!branch.getName().equalsIgnoreCase(name) && repository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A branch named '" + name + "' already exists");
        }
        if (branch.isHeadquarters() && request.active() != null && !request.active()) {
            throw ApiException.conflict("Cannot deactivate '" + branch.getName() + "' — it is the head office");
        }
        branch.setCode(code);
        branch.setName(name);
        applyRequest(branch, request);
        Branch saved = repository.save(branch);
        auditLogService.record("BRANCH_UPDATED", "Updated branch " + saved.getCode() + " (" + saved.getName() + ")", "Branch", saved.getId());
        return BranchDto.from(saved);
    }

    @Transactional
    public BranchDto setActive(Long id, boolean active) {
        Branch branch = getEntityOrThrow(id);
        if (branch.isHeadquarters() && !active) {
            throw ApiException.conflict("Cannot deactivate '" + branch.getName() + "' — it is the head office");
        }
        branch.setActive(active);
        Branch saved = repository.save(branch);
        auditLogService.record(active ? "BRANCH_ACTIVATED" : "BRANCH_DEACTIVATED",
                (active ? "Activated" : "Deactivated") + " branch " + saved.getCode(), "Branch", saved.getId());
        return BranchDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Branch getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Branch not found: " + id));
    }

    /** The head-office branch. Assumes {@link #seedDefaultBranchIfNeeded} has run (DataSeeder calls it on every startup). */
    @Transactional(readOnly = true)
    public Branch getHeadquartersOrThrow() {
        return repository.findByHeadquartersTrue()
                .orElseThrow(() -> ApiException.notFound("No head-office branch is configured"));
    }

    /** Id of the head-office branch — what a null {@code branchId} on a legacy record means. */
    @Transactional(readOnly = true)
    public Long headquartersId() {
        return getHeadquartersOrThrow().getId();
    }

    /** The branch id to snapshot on a new document: the given branch's id, or head office when none. */
    @Transactional(readOnly = true)
    public Long idOrHeadquarters(Branch branch) {
        return branch != null ? branch.getId() : headquartersId();
    }

    /**
     * Validates an explicitly requested branch id for a document (null = head office) and returns
     * the id to store. Rejects unknown and deactivated branches.
     */
    @Transactional(readOnly = true)
    public Long resolveForDocument(Long requestedBranchId) {
        if (requestedBranchId == null) {
            return headquartersId();
        }
        Branch branch = getEntityOrThrow(requestedBranchId);
        if (!branch.isActive()) {
            throw ApiException.conflict("Branch '" + branch.getName() + "' is inactive");
        }
        return branch.getId();
    }

    /**
     * Applies the branch's document prefix (e.g. "KLA-") to a freshly generated document number
     * ("POS-1789..." becomes "KLA-POS-1789..."). A null branch means head office; a branch with no
     * prefix leaves the number unchanged.
     */
    @Transactional(readOnly = true)
    /**
     * The one branch a set of documents all belong to (a null entry counts as head office), or null when they
     * span more than one branch or the set is empty — used to attribute a payment that settles several documents.
     */
    public Long commonBranchId(java.util.Collection<Long> branchIds) {
        Long headquarters = headquartersId();
        java.util.Set<Long> distinct = new java.util.HashSet<>();
        for (Long id : branchIds) {
            distinct.add(id != null ? id : headquarters);
        }
        return distinct.size() == 1 ? distinct.iterator().next() : null;
    }

    public String prefixedNumber(Long branchId, String number) {
        Long id = branchId != null ? branchId : headquartersId();
        String prefix = repository.findById(id).map(Branch::getDocumentPrefix).orElse(null);
        return prefix == null || prefix.isBlank() ? number : prefix.trim() + number;
    }

    /** Receipt header details for a branch (null id = head office). */
    @Transactional(readOnly = true)
    public BranchReceiptInfo receiptInfo(Long branchId) {
        return BranchReceiptInfo.from(branchId != null ? getEntityOrThrow(branchId) : getHeadquartersOrThrow());
    }

    /** The branch's own taxpayer id when it has one, otherwise the business-wide {@code fallback}. */
    @Transactional(readOnly = true)
    public String effectiveTaxId(Long branchId, String fallback) {
        Long id = branchId != null ? branchId : headquartersId();
        String own = repository.findById(id).map(Branch::getTaxId).orElse(null);
        return own != null && !own.isBlank() ? own : fallback;
    }

    /** The branch's own fiscal-authority branch code when it has one, otherwise the terminal's configured {@code fallback}. */
    @Transactional(readOnly = true)
    public String effectiveEfrisBranchCode(Long branchId, String fallback) {
        Long id = branchId != null ? branchId : headquartersId();
        String own = repository.findById(id).map(Branch::getEfrisBranchCode).orElse(null);
        return own != null && !own.isBlank() ? own : fallback;
    }

    /** Idempotent: creates "Head Office" (code HO), flagged {@link Branch#headquarters}, the first time it runs. */
    @Transactional
    public void seedDefaultBranchIfNeeded() {
        if (repository.findByHeadquartersTrue().isPresent()) {
            return;
        }
        Branch branch = new Branch();
        branch.setCode(repository.existsByCodeIgnoreCase("HO") ? "HQ" : "HO");
        branch.setName(repository.existsByNameIgnoreCase("Head Office") ? "Head Office (default)" : "Head Office");
        branch.setActive(true);
        branch.setHeadquarters(true);
        Branch saved = repository.save(branch);
        auditLogService.record("BRANCH_CREATED", "Seeded default branch '" + saved.getName() + "'", "Branch", saved.getId());
    }

    private void applyRequest(Branch branch, BranchRequest request) {
        branch.setAddress(blankToNull(request.address()));
        branch.setPhone(blankToNull(request.phone()));
        branch.setEmail(blankToNull(request.email()));
        branch.setReceiptHeader(blankToNull(request.receiptHeader()));
        branch.setTaxId(blankToNull(request.taxId()));
        branch.setEfrisBranchCode(blankToNull(request.efrisBranchCode()));
        branch.setDocumentPrefix(blankToNull(request.documentPrefix()));
        if (request.active() != null) {
            branch.setActive(request.active());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
