package com.posapp.assets;

import com.posapp.assets.dto.AssetDtos.VerificationDto;
import com.posapp.assets.dto.AssetDtos.VerificationLineDto;
import com.posapp.assets.dto.AssetRequests.DisposeRequest;
import com.posapp.assets.dto.AssetRequests.MarkRequest;
import com.posapp.assets.dto.AssetRequests.VerificationRequest;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.branch.BranchService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Physical verification rounds: pick the assets of a branch / category / location, walk round and mark each one found, missing
 * or damaged, then close the round. Missing assets can be written off afterwards (which follows the disposal approval rule).
 */
@Service
public class AssetVerificationService {

    private static final Set<String> RESULTS = Set.of(AssetVerificationLine.PENDING, AssetVerificationLine.FOUND, AssetVerificationLine.MISSING, AssetVerificationLine.DAMAGED);

    private final AssetVerificationRepository repository;
    private final AssetVerificationLineRepository lineRepository;
    private final FixedAssetRepository assetRepository;
    private final AssetCategoryRepository categoryRepository;
    private final FixedAssetService assetService;
    private final AssetHistoryService history;
    private final AuditLogService auditLogService;
    private final BranchService branchService;
    private final BranchAccessService branchAccessService;

    public AssetVerificationService(AssetVerificationRepository repository, AssetVerificationLineRepository lineRepository,
                                    FixedAssetRepository assetRepository, AssetCategoryRepository categoryRepository, FixedAssetService assetService,
                                    AssetHistoryService history, AuditLogService auditLogService, BranchService branchService,
                                    BranchAccessService branchAccessService) {
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.assetService = assetService;
        this.history = history;
        this.auditLogService = auditLogService;
        this.branchService = branchService;
        this.branchAccessService = branchAccessService;
    }

    @Transactional(readOnly = true)
    public List<VerificationDto> list() {
        Map<Long, String> branches = branchNames();
        Map<Long, String> categories = categoryNames();
        return repository.findAllByOrderByIdDesc().stream().filter(v -> branchAccessService.canAccess(v.getBranchId())).map(v -> toDto(v, branches, categories)).toList();
    }

    @Transactional(readOnly = true)
    public VerificationDto get(Long id) {
        return toDto(find(id), branchNames(), categoryNames());
    }

    @Transactional(readOnly = true)
    public List<VerificationLineDto> lines(Long id) {
        find(id);
        Map<Long, AssetStatus> status = new HashMap<>();
        assetRepository.findAll().forEach(a -> status.put(a.getId(), a.getStatus()));
        return lineRepository.findByVerificationIdOrderByAssetCodeAsc(id).stream().map(l -> toLineDto(l, status.get(l.getAssetId()))).toList();
    }

    @Transactional
    public VerificationDto start(VerificationRequest r) {
        String name = r.name() == null ? "" : r.name().trim();
        if (name.isEmpty()) throw ApiException.badRequest("Give the round a name.");
        Long hq = branchService.headquartersId();
        Long branch = r.branchId() != null ? branchService.resolveForDocument(r.branchId()) : null;
        if (branch != null) branchAccessService.assertCanAccess(branch);
        String location = r.location() == null || r.location().isBlank() ? null : r.location().trim();
        List<FixedAsset> scope = assetService.visibleAssets(null, branch).stream()
                .filter(a -> a.getStatus() != AssetStatus.DISPOSED)
                .filter(a -> r.categoryId() == null || r.categoryId().equals(a.getCategory().getId()))
                .filter(a -> location == null || (a.getLocation() != null && a.getLocation().toLowerCase(Locale.ROOT).contains(location.toLowerCase(Locale.ROOT))))
                .toList();
        if (scope.isEmpty()) throw ApiException.badRequest("No assets match that branch, category and location.");
        AssetVerification v = new AssetVerification();
        v.setName(name.length() > 255 ? name.substring(0, 255) : name);
        v.setBranchId(branch);
        v.setCategoryId(r.categoryId());
        v.setLocation(location);
        v.setStartedOn(LocalDate.now());
        v.setNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
        v.setCreatedByName(AssetHistoryService.actor().name());
        v = repository.save(v);
        for (FixedAsset a : scope) {
            AssetVerificationLine l = new AssetVerificationLine();
            l.setVerificationId(v.getId());
            l.setAssetId(a.getId());
            l.setAssetCode(a.getCode());
            l.setAssetName(a.getName());
            l.setExpectedLocation(a.getLocation());
            l.setExpectedCustodian(a.getCustodianName());
            l.setBookValue(a.bookValue());
            lineRepository.save(l);
        }
        auditLogService.record("ASSET_VERIFICATION_STARTED", "Started verification " + v.getName() + " (" + scope.size() + " assets)", "AssetVerification", v.getId());
        return toDto(v, branchNames(), categoryNames());
    }

    @Transactional
    public VerificationLineDto mark(Long verificationId, Long lineId, MarkRequest r) {
        AssetVerification v = open(verificationId);
        AssetVerificationLine l = line(verificationId, lineId);
        String result = normalize(r.result());
        l.setResult(result);
        l.setFoundLocation(AssetVerificationLine.FOUND.equals(result) || AssetVerificationLine.DAMAGED.equals(result) ? clip(r.foundLocation(), 255) : null);
        l.setNote(clip(r.note(), 500));
        if (AssetVerificationLine.PENDING.equals(result)) {
            l.setCheckedAt(null);
            l.setCheckedByName(null);
        } else {
            l.setCheckedAt(Instant.now());
            l.setCheckedByName(AssetHistoryService.actor().name());
        }
        lineRepository.save(l);
        return toLineDto(l, assetRepository.findById(l.getAssetId()).map(FixedAsset::getStatus).orElse(null));
    }

    /** Marks every asset not checked yet with one result (usually "found" after walking round). */
    @Transactional
    public VerificationDto markRest(Long verificationId, String resultText) {
        AssetVerification v = open(verificationId);
        String result = normalize(resultText);
        if (AssetVerificationLine.PENDING.equals(result)) throw ApiException.badRequest("Choose found, missing or damaged.");
        for (AssetVerificationLine l : lineRepository.findByVerificationIdOrderByAssetCodeAsc(verificationId)) {
            if (!AssetVerificationLine.PENDING.equals(l.getResult())) continue;
            l.setResult(result);
            l.setCheckedAt(Instant.now());
            l.setCheckedByName(AssetHistoryService.actor().name());
            lineRepository.save(l);
        }
        return toDto(v, branchNames(), categoryNames());
    }

    @Transactional
    public VerificationDto close(Long verificationId, boolean applyLocations) {
        AssetVerification v = open(verificationId);
        long pending = lineRepository.countByVerificationIdAndResult(verificationId, AssetVerificationLine.PENDING);
        if (pending > 0) {
            throw ApiException.conflict(pending + " asset(s) have not been checked yet. Mark them (or use 'Mark the rest') before closing.");
        }
        int moved = 0;
        for (AssetVerificationLine l : lineRepository.findByVerificationIdOrderByAssetCodeAsc(verificationId)) {
            FixedAsset a = assetRepository.findById(l.getAssetId()).orElse(null);
            if (a == null) continue;
            history.event(a.getId(), "VERIFIED", v.getName() + ": " + l.getResult().toLowerCase(Locale.ROOT)
                    + (l.getFoundLocation() != null ? " at " + l.getFoundLocation() : "") + (l.getNote() != null ? " - " + l.getNote() : ""));
            if (applyLocations && l.getFoundLocation() != null && a.getStatus() != AssetStatus.DISPOSED
                    && !l.getFoundLocation().equals(a.getLocation())) {
                history.event(a.getId(), "LOCATION", "Location changed from " + (a.getLocation() == null ? "(none)" : a.getLocation()) + " to " + l.getFoundLocation() + " after verification");
                a.setLocation(l.getFoundLocation());
                assetRepository.save(a);
                moved++;
            }
        }
        v.setStatus(AssetVerification.CLOSED);
        v.setClosedOn(LocalDate.now());
        v.setClosedByName(AssetHistoryService.actor().name());
        repository.save(v);
        auditLogService.record("ASSET_VERIFICATION_CLOSED", "Closed verification " + v.getName() + (moved > 0 ? " (" + moved + " location(s) updated)" : ""), "AssetVerification", v.getId());
        return toDto(v, branchNames(), categoryNames());
    }

    @Transactional
    public void delete(Long verificationId) {
        AssetVerification v = open(verificationId);
        lineRepository.deleteAll(lineRepository.findByVerificationIdOrderByAssetCodeAsc(verificationId));
        repository.delete(v);
        auditLogService.record("ASSET_VERIFICATION_DELETED", "Deleted verification " + v.getName(), "AssetVerification", verificationId);
    }

    /** Writes a missing asset off (a disposal with no proceeds). Large values follow the disposal approval rule. */
    @Transactional
    public VerificationLineDto writeOff(Long verificationId, Long lineId) {
        AssetVerification v = find(verificationId);
        AssetVerificationLine l = line(verificationId, lineId);
        if (!AssetVerificationLine.MISSING.equals(l.getResult())) throw ApiException.badRequest("Only assets marked missing can be written off.");
        if (l.isWrittenOff()) throw ApiException.conflict("This asset has already been written off.");
        FixedAsset a = assetRepository.findById(l.getAssetId()).orElseThrow(() -> ApiException.notFound("Asset not found."));
        if (a.getStatus() == AssetStatus.DISPOSED) throw ApiException.conflict("This asset has already been disposed.");
        assetService.dispose(a.getId(), new DisposeRequest(LocalDate.now(), java.math.BigDecimal.ZERO, "Missing in verification " + v.getName()));
        l.setWrittenOff(true);
        lineRepository.save(l);
        return toLineDto(l, AssetStatus.DISPOSED);
    }

    // ---- helpers ----

    private AssetVerification find(Long id) {
        AssetVerification v = repository.findById(id).orElseThrow(() -> ApiException.notFound("Verification not found: " + id));
        branchAccessService.assertCanAccess(v.getBranchId());
        return v;
    }

    private AssetVerification open(Long id) {
        AssetVerification v = find(id);
        if (!AssetVerification.OPEN.equals(v.getStatus())) throw ApiException.conflict("This verification is closed.");
        return v;
    }

    private AssetVerificationLine line(Long verificationId, Long lineId) {
        return lineRepository.findById(lineId).filter(l -> l.getVerificationId().equals(verificationId))
                .orElseThrow(() -> ApiException.notFound("Line not found."));
    }

    private static String normalize(String result) {
        String r = result == null ? "" : result.trim().toUpperCase(Locale.ROOT);
        if (!RESULTS.contains(r)) throw ApiException.badRequest("The result must be FOUND, MISSING, DAMAGED or PENDING.");
        return r;
    }

    private static String clip(String v, int max) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private Map<Long, String> branchNames() {
        Map<Long, String> names = new HashMap<>();
        branchService.listAll().forEach(b -> names.put(b.id(), b.name()));
        return names;
    }

    private Map<Long, String> categoryNames() {
        Map<Long, String> names = new HashMap<>();
        categoryRepository.findAll().forEach(c -> names.put(c.getId(), c.getName()));
        return names;
    }

    private VerificationDto toDto(AssetVerification v, Map<Long, String> branches, Map<Long, String> categories) {
        long total = lineRepository.countByVerificationId(v.getId());
        return new VerificationDto(v.getId(), v.getName(), v.getStatus(), v.getBranchId(), v.getBranchId() != null ? branches.get(v.getBranchId()) : null,
                v.getCategoryId(), v.getCategoryId() != null ? categories.get(v.getCategoryId()) : null, v.getLocation(), v.getStartedOn(), v.getClosedOn(),
                v.getNote(), v.getCreatedByName(), v.getClosedByName(), total,
                lineRepository.countByVerificationIdAndResult(v.getId(), AssetVerificationLine.PENDING),
                lineRepository.countByVerificationIdAndResult(v.getId(), AssetVerificationLine.FOUND),
                lineRepository.countByVerificationIdAndResult(v.getId(), AssetVerificationLine.MISSING),
                lineRepository.countByVerificationIdAndResult(v.getId(), AssetVerificationLine.DAMAGED));
    }

    private static VerificationLineDto toLineDto(AssetVerificationLine l, AssetStatus assetStatus) {
        return new VerificationLineDto(l.getId(), l.getAssetId(), l.getAssetCode(), l.getAssetName(), l.getExpectedLocation(), l.getExpectedCustodian(),
                l.getBookValue(), l.getResult(), l.getFoundLocation(), l.getNote(), l.getCheckedAt(), l.getCheckedByName(), l.isWrittenOff(),
                assetStatus != null ? assetStatus.name() : null);
    }
}
