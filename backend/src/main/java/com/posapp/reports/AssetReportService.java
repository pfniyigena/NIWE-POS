package com.posapp.reports;

import com.posapp.assets.AssetAdjustment;
import com.posapp.assets.AssetAdjustmentRepository;
import com.posapp.assets.AssetDepreciationService;
import com.posapp.assets.AssetSettingsService;
import com.posapp.assets.AssetStatus;
import com.posapp.assets.AssetVerification;
import com.posapp.assets.AssetVerificationLine;
import com.posapp.assets.AssetVerificationLineRepository;
import com.posapp.assets.AssetVerificationRepository;
import com.posapp.assets.FixedAssetService;
import com.posapp.assets.AssetTransfer;
import com.posapp.assets.AssetTransferRepository;
import com.posapp.assets.DepreciationEntry;
import com.posapp.assets.DepreciationEntryRepository;
import com.posapp.assets.FixedAsset;
import com.posapp.assets.FixedAssetRepository;
import com.posapp.branch.BranchService;
import com.posapp.reports.dto.AssetReportRows.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * The Assets reports (Assets > Reports): register, depreciation schedule, disposals, transfers and totals per category. All
 * but the transfers can be limited to one branch (an asset with no branch belongs to head office).
 */
@Service
public class AssetReportService {

    private final FixedAssetRepository assetRepository;
    private final DepreciationEntryRepository entryRepository;
    private final AssetTransferRepository transferRepository;
    private final BranchService branchService;
    private final AssetAdjustmentRepository adjustmentRepository;
    private final AssetVerificationRepository verificationRepository;
    private final AssetVerificationLineRepository verificationLineRepository;
    private final AssetDepreciationService depreciationService;
    private final AssetSettingsService settingsService;
    private final FixedAssetService assetService;

    public AssetReportService(FixedAssetRepository assetRepository, DepreciationEntryRepository entryRepository,
                              AssetTransferRepository transferRepository, BranchService branchService,
                              AssetAdjustmentRepository adjustmentRepository, AssetVerificationRepository verificationRepository,
                              AssetVerificationLineRepository verificationLineRepository, AssetDepreciationService depreciationService,
                              AssetSettingsService settingsService, FixedAssetService assetService) {
        this.adjustmentRepository = adjustmentRepository;
        this.verificationRepository = verificationRepository;
        this.verificationLineRepository = verificationLineRepository;
        this.depreciationService = depreciationService;
        this.settingsService = settingsService;
        this.assetService = assetService;
        this.assetRepository = assetRepository;
        this.entryRepository = entryRepository;
        this.transferRepository = transferRepository;
        this.branchService = branchService;
    }

    private Map<Long, String> branchNames() {
        Map<Long, String> names = new HashMap<>();
        branchService.listAll().forEach(b -> names.put(b.id(), b.name()));
        return names;
    }

    private boolean matches(Long recordBranchId, Long filterBranchId, Long headquartersId) {
        return filterBranchId == null || ReportService.branchMatches(recordBranchId, filterBranchId, headquartersId);
    }

    private String nameOf(Map<Long, String> names, Long branchId, Long headquartersId) {
        return names.get(branchId != null ? branchId : headquartersId);
    }

    /** Every asset, as it stands today (disposed ones included, marked by status). */
    @Transactional(readOnly = true)
    public List<AssetRegisterRow> register(Long branchId) {
        return register(branchId, null);
    }

    /** The register with the same filters as the screen (status, category, search, location, holder, view). */
    @Transactional(readOnly = true)
    public List<AssetRegisterRow> register(Long branchId, FixedAssetService.Filter filter) {
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        com.posapp.assets.AssetSettings settings = settingsService.entity();
        List<AssetRegisterRow> rows = new ArrayList<>();
        for (FixedAsset a : assetRepository.findAllByOrderByCodeAsc()) {
            if (!matches(a.getBranchId(), branchId, hq)) continue;
            if (filter != null) {
                if (filter.status() != null && a.getStatus() != filter.status()) continue;
                if (!assetService.matches(a, filter, settings, LocalDate.now())) continue;
            }
            rows.add(new AssetRegisterRow(a.getCode(), a.getName(), a.getCategory().getName(), a.getStatus().name(), a.getPurchaseDate(),
                    a.getCost(), a.getAccumulatedDepreciation(), a.bookValue(), nameOf(names, a.getBranchId(), hq),
                    a.getLocation(), a.getAssignedTo(), a.getSerialNumber(), a.getWarrantyExpiresOn(), a.getInsuranceExpiresOn()));
        }
        return rows;
    }

    /** Assets still owned, grouped by place ({@code byHolder = false}) or by who holds them. */
    @Transactional(readOnly = true)
    public List<AssetHolderRow> byHolder(Long branchId, boolean byHolder) {
        record Acc(int[] count, BigDecimal[] money) {
        }
        Long hq = branchService.headquartersId();
        Map<String, Acc> map = new LinkedHashMap<>();
        for (FixedAsset a : assetRepository.findAllByOrderByCodeAsc()) {
            if (a.getStatus() == AssetStatus.DISPOSED || !matches(a.getBranchId(), branchId, hq)) continue;
            String key = byHolder ? a.getCustodianName() : a.getLocation();
            if (key == null || key.isBlank()) key = byHolder ? "(nobody)" : "(no location)";
            Acc acc = map.computeIfAbsent(key, k -> new Acc(new int[1], new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}));
            acc.count()[0]++;
            acc.money()[0] = acc.money()[0].add(a.getCost());
            acc.money()[1] = acc.money()[1].add(a.getAccumulatedDepreciation());
        }
        List<AssetHolderRow> rows = new ArrayList<>();
        map.forEach((name, acc) -> rows.add(new AssetHolderRow(name, acc.count()[0], acc.money()[0], acc.money()[1], acc.money()[0].subtract(acc.money()[1]))));
        rows.sort(Comparator.comparing(AssetHolderRow::holder, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Warranty and insurance dates already passed or ending within {@code days} (default: the alert days of the settings), soonest first. */
    @Transactional(readOnly = true)
    public List<AssetExpiryRow> expiry(Long branchId, Integer days) {
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        int within = days != null ? Math.max(0, Math.min(days, 3650)) : settingsService.entity().getAlertDays();
        LocalDate today = LocalDate.now();
        List<AssetExpiryRow> rows = new ArrayList<>();
        for (FixedAsset a : assetRepository.findAllByOrderByCodeAsc()) {
            if (a.getStatus() == AssetStatus.DISPOSED || !matches(a.getBranchId(), branchId, hq)) continue;
            addExpiry(rows, a, "Warranty", a.getWarrantyExpiresOn(), null, within, today, names, hq);
            addExpiry(rows, a, "Insurance", a.getInsuranceExpiresOn(), a.getInsurancePolicy(), within, today, names, hq);
        }
        rows.sort(Comparator.comparing(AssetExpiryRow::expiresOn));
        return rows;
    }

    private void addExpiry(List<AssetExpiryRow> rows, FixedAsset a, String kind, LocalDate on, String detail, int within, LocalDate today, Map<Long, String> names, Long hq) {
        if (on == null || on.isAfter(today.plusDays(within))) return;
        long left = ChronoUnit.DAYS.between(today, on);
        rows.add(new AssetExpiryRow(a.getCode(), a.getName(), a.getCategory().getName(), nameOf(names, a.getBranchId(), hq), kind, on, left,
                left < 0 ? "Expired" : "Expiring", detail));
    }

    /** The next {@code months} months of depreciation (default 12, at most 120), assuming nothing is bought, sold or changed. */
    @Transactional(readOnly = true)
    public List<AssetForecastRow> forecast(Long branchId, Integer months) {
        int n = months != null ? Math.max(1, Math.min(months, 120)) : 12;
        Long hq = branchService.headquartersId();
        BigDecimal[] charge = new BigDecimal[n];
        int[] count = new int[n];
        java.util.Arrays.fill(charge, BigDecimal.ZERO);
        BigDecimal book = BigDecimal.ZERO;
        YearMonth first = YearMonth.now();
        for (FixedAsset a : assetRepository.findAllByOrderByCodeAsc()) {
            if (a.getStatus() == AssetStatus.DISPOSED || !matches(a.getBranchId(), branchId, hq)) continue;
            book = book.add(a.bookValue());
            if (a.getStatus() != AssetStatus.ACTIVE) continue;
            // months already due but not charged yet are counted in the first month, so the forecast starts from today's books
            YearMonth next = AssetDepreciationService.nextMonth(a);
            int lag = (int) Math.max(0, ChronoUnit.MONTHS.between(next, first));
            List<BigDecimal> projection = depreciationService.project(a, n + lag);
            for (int i = 0; i < projection.size(); i++) {
                int slot = Math.max(0, i - lag);
                if (i < lag) {
                    charge[0] = charge[0].add(projection.get(i));
                    continue;
                }
                if (slot >= n) break;
                charge[slot] = charge[slot].add(projection.get(i));
                count[slot]++;
            }
        }
        List<AssetForecastRow> rows = new ArrayList<>();
        BigDecimal running = book;
        for (int i = 0; i < n; i++) {
            running = running.subtract(charge[i]);
            rows.add(new AssetForecastRow(first.plusMonths(i).toString(), count[i], charge[i], running));
        }
        return rows;
    }

    /** Opening balance, additions, improvements, disposals, depreciation and closing balance per category, for the period. */
    @Transactional(readOnly = true)
    public List<AssetMovementRow> movement(LocalDate from, LocalDate to, Long branchId) {
        Long hq = branchService.headquartersId();
        LocalDate before = from.minusDays(1);
        Map<Long, List<AssetAdjustment>> improvements = new HashMap<>();
        Map<Long, List<AssetAdjustment>> impairments = new HashMap<>();
        for (AssetAdjustment adj : adjustmentRepository.findAllByOrderByAdjustmentDateAscIdAsc()) {
            if (AssetAdjustment.IMPROVEMENT.equals(adj.getKind())) improvements.computeIfAbsent(adj.getAssetId(), k -> new ArrayList<>()).add(adj);
            else if (AssetAdjustment.IMPAIRMENT.equals(adj.getKind())) impairments.computeIfAbsent(adj.getAssetId(), k -> new ArrayList<>()).add(adj);
        }
        Map<Long, List<DepreciationEntry>> entries = new HashMap<>();
        for (DepreciationEntry e : entryRepository.findAll()) {
            entries.computeIfAbsent(e.getAsset().getId(), k -> new ArrayList<>()).add(e);
        }
        Map<String, BigDecimal[]> byCategory = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FixedAsset a : assetRepository.findAllByOrderByCodeAsc()) {
            if (!matches(a.getBranchId(), branchId, hq)) continue;
            // [0] opening cost, [1] additions, [2] improvements, [3] disposals cost, [4] closing cost,
            // [5] opening accum, [6] depreciation, [7] disposals accum, [8] closing accum
            BigDecimal[] v = byCategory.computeIfAbsent(a.getCategory().getName(), k -> zeros(9));
            List<AssetAdjustment> imps = improvements.getOrDefault(a.getId(), List.of());
            BigDecimal improvementsTotal = imps.stream().map(AssetAdjustment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<DepreciationEntry> es = entries.getOrDefault(a.getId(), List.of());
            List<AssetAdjustment> imp2 = impairments.getOrDefault(a.getId(), List.of());
            boolean openedBefore = existsAt(a, before);
            boolean openAtEnd = existsAt(a, to);
            if (openedBefore) {
                v[0] = v[0].add(costAt(a, imps, before));
                v[5] = v[5].add(accumAt(a, es, imp2, before));
            }
            if (openAtEnd) {
                v[4] = v[4].add(costAt(a, imps, to));
                v[8] = v[8].add(accumAt(a, es, imp2, to));
            }
            if (!a.getPurchaseDate().isBefore(from) && !a.getPurchaseDate().isAfter(to)) {
                v[1] = v[1].add(a.getOriginalCost());
            }
            for (AssetAdjustment adj : imps) {
                if (!adj.getAdjustmentDate().isBefore(from) && !adj.getAdjustmentDate().isAfter(to) && !a.getPurchaseDate().isAfter(adj.getAdjustmentDate())) {
                    v[2] = v[2].add(adj.getAmount());
                }
            }
            if (a.getStatus() == AssetStatus.DISPOSED && a.getDisposalDate() != null && !a.getDisposalDate().isBefore(from) && !a.getDisposalDate().isAfter(to)) {
                v[3] = v[3].add(a.getCost());
                v[7] = v[7].add(a.getAccumulatedDepreciation());
            }
        }
        List<AssetMovementRow> rows = new ArrayList<>();
        byCategory.forEach((name, v) -> {
            // depreciation is whatever carries the opening accumulated depreciation to the closing one, so the report always adds up
            BigDecimal depreciation = v[8].subtract(v[5]).add(v[7]);
            rows.add(new AssetMovementRow(name, v[0], v[1], v[2], v[3], v[4], v[5], depreciation, v[7], v[8], v[4].subtract(v[8])));
        });
        return rows;
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        java.util.Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static boolean existsAt(FixedAsset a, LocalDate day) {
        if (a.getPurchaseDate().isAfter(day)) return false;
        return !(a.getStatus() == AssetStatus.DISPOSED && a.getDisposalDate() != null && !a.getDisposalDate().isAfter(day));
    }

    private static BigDecimal costAt(FixedAsset a, List<AssetAdjustment> improvements, LocalDate day) {
        BigDecimal cost = a.getOriginalCost();
        for (AssetAdjustment adj : improvements) {
            if (!adj.getAdjustmentDate().isAfter(day)) cost = cost.add(adj.getAmount());
        }
        return cost;
    }

    private static BigDecimal accumAt(FixedAsset a, List<DepreciationEntry> entries, List<AssetAdjustment> impairments, LocalDate day) {
        BigDecimal accumulated = a.getAccumulatedDepreciation();
        for (DepreciationEntry e : entries) {
            if (e.getPeriodEnd().isAfter(day)) accumulated = accumulated.subtract(e.getAmount());
        }
        for (AssetAdjustment adj : impairments) {
            if (adj.getAdjustmentDate().isAfter(day)) accumulated = accumulated.subtract(adj.getAmount());
        }
        return accumulated;
    }

    /** Every asset of one verification round (or of all rounds when {@code verificationId} is null). */
    @Transactional(readOnly = true)
    public List<AssetVerificationRow> verification(Long verificationId) {
        List<AssetVerificationRow> rows = new ArrayList<>();
        for (AssetVerification v : verificationRepository.findAllByOrderByIdDesc()) {
            if (verificationId != null && !verificationId.equals(v.getId())) continue;
            for (AssetVerificationLine l : verificationLineRepository.findByVerificationIdOrderByAssetCodeAsc(v.getId())) {
                rows.add(new AssetVerificationRow(v.getName(), v.getStatus(), v.getStartedOn(), l.getAssetCode(), l.getAssetName(), l.getExpectedLocation(),
                        l.getExpectedCustodian(), l.getResult(), l.getFoundLocation(), l.getCheckedByName(), l.getNote(), l.isWrittenOff() ? "Yes" : "No"));
            }
        }
        return rows;
    }

    /** One row per asset charge; the branch is the one the charge was made to at the time (it stays put after a transfer). */
    @Transactional(readOnly = true)
    public List<DepreciationScheduleRow> depreciationSchedule(LocalDate from, LocalDate to, Long branchId) {
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<DepreciationScheduleRow> rows = new ArrayList<>();
        for (DepreciationEntry e : entryRepository.findByPeriodEndBetweenOrderByPeriodEndAscIdAsc(from, to)) {
            if (!matches(e.getBranchId(), branchId, hq)) continue;
            FixedAsset a = e.getAsset();
            rows.add(new DepreciationScheduleRow(e.getPeriodEnd(), a.getCode(), a.getName(), a.getCategory().getName(),
                    nameOf(names, e.getBranchId(), hq), e.getAmount(), e.getAccumulatedAfter(), e.getBookValueAfter()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<AssetDisposalRow> disposals(LocalDate from, LocalDate to, Long branchId) {
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<AssetDisposalRow> rows = new ArrayList<>();
        for (FixedAsset a : assetRepository.findByStatusAndDisposalDateBetweenOrderByDisposalDateAsc(AssetStatus.DISPOSED, from, to)) {
            if (!matches(a.getBranchId(), branchId, hq)) continue;
            rows.add(new AssetDisposalRow(a.getDisposalDate(), a.getCode(), a.getName(), a.getCategory().getName(),
                    nameOf(names, a.getBranchId(), hq), a.getCost(), a.getAccumulatedDepreciation(), a.bookValue(),
                    a.getDisposalProceeds(), a.getDisposalGainLoss()));
        }
        return rows;
    }

    /** Moves in the period; with a branch chosen, moves into or out of that branch. */
    @Transactional(readOnly = true)
    public List<AssetTransferRow> transfers(LocalDate from, LocalDate to, Long branchId) {
        List<AssetTransferRow> rows = new ArrayList<>();
        for (AssetTransfer t : transferRepository.findByTransferDateBetweenOrderByTransferDateAscIdAsc(from, to)) {
            if (branchId != null && !branchId.equals(t.getFromBranchId()) && !branchId.equals(t.getToBranchId())) continue;
            FixedAsset a = t.getAsset();
            rows.add(new AssetTransferRow(t.getTransferDate(), a.getCode(), a.getName(), t.getFromBranchName(), t.getToBranchName(),
                    t.getFromLocation(), t.getToLocation(), t.getReason()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<AssetCategorySummaryRow> byCategory(Long branchId) {
        record Acc(int[] count, BigDecimal[] money) {
        }
        Long hq = branchService.headquartersId();
        Map<String, Acc> map = new LinkedHashMap<>();
        for (FixedAsset a : assetRepository.findAllByOrderByCodeAsc()) {
            if (a.getStatus() == AssetStatus.DISPOSED) continue;
            if (!matches(a.getBranchId(), branchId, hq)) continue;
            Acc acc = map.computeIfAbsent(a.getCategory().getName(), k -> new Acc(new int[1], new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}));
            acc.count()[0]++;
            acc.money()[0] = acc.money()[0].add(a.getCost());
            acc.money()[1] = acc.money()[1].add(a.getAccumulatedDepreciation());
        }
        List<AssetCategorySummaryRow> rows = new ArrayList<>();
        map.forEach((name, acc) -> rows.add(new AssetCategorySummaryRow(name, acc.count()[0], acc.money()[0], acc.money()[1],
                acc.money()[0].subtract(acc.money()[1]))));
        rows.sort(Comparator.comparing(AssetCategorySummaryRow::categoryName));
        return rows;
    }
}
