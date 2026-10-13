package com.posapp.reports;

import com.posapp.assets.AssetStatus;
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

    public AssetReportService(FixedAssetRepository assetRepository, DepreciationEntryRepository entryRepository,
                              AssetTransferRepository transferRepository, BranchService branchService) {
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
        Map<Long, String> names = branchNames();
        Long hq = branchService.headquartersId();
        List<AssetRegisterRow> rows = new ArrayList<>();
        for (FixedAsset a : assetRepository.findAllByOrderByCodeAsc()) {
            if (!matches(a.getBranchId(), branchId, hq)) continue;
            rows.add(new AssetRegisterRow(a.getCode(), a.getName(), a.getCategory().getName(), a.getStatus().name(), a.getPurchaseDate(),
                    a.getCost(), a.getAccumulatedDepreciation(), a.bookValue(), nameOf(names, a.getBranchId(), hq),
                    a.getLocation(), a.getAssignedTo(), a.getSerialNumber()));
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
