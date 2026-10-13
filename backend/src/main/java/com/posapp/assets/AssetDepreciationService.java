package com.posapp.assets;

import com.posapp.accounting.AccountingService;
import com.posapp.assets.dto.AssetDtos.DepreciationRunResult;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Monthly depreciation. Each asset is charged one full month for every month-end from its start month up to the
 * requested date; months already charged are never charged twice. All the charges of one month are posted to
 * Accounting as one entry (Depreciation Expense / Accumulated Depreciation).
 *
 * <p>Straight line: (cost - salvage) / life each month. Declining balance: book value x 2 / life each month, never taking
 * the book value below the salvage value. With "prorate first month" the first charge covers only the days from the start
 * date to month end. The last month always takes whatever is left so the asset ends exactly at its salvage value.
 */
@Service
public class AssetDepreciationService {

    private final FixedAssetRepository assetRepository;
    private final DepreciationEntryRepository entryRepository;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;
    private final ModuleService moduleService;

    public AssetDepreciationService(FixedAssetRepository assetRepository, DepreciationEntryRepository entryRepository,
                                    AccountingService accountingService, AuditLogService auditLogService,
                                    ModuleService moduleService) {
        this.assetRepository = assetRepository;
        this.entryRepository = entryRepository;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
        this.moduleService = moduleService;
    }

    /** Charges every active asset up to {@code upTo} (default: the end of last month) and posts the monthly entries. */
    @Transactional
    public DepreciationRunResult run(LocalDate upTo) {
        LocalDate limit = upTo != null ? upTo : endOfLastMonth();
        if (limit.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("Depreciation can only be run up to today.");
        }
        Map<MonthBranch, BigDecimal> perMonth = new LinkedHashMap<>();
        int entries = 0;
        for (FixedAsset asset : assetRepository.findByStatusOrderByCodeAsc(AssetStatus.ACTIVE)) {
            entries += depreciate(asset, limit, perMonth);
        }
        postMonths(perMonth);
        BigDecimal total = perMonth.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int months = (int) perMonth.keySet().stream().map(MonthBranch::month).distinct().count();
        if (entries > 0) {
            auditLogService.record("ASSET_DEPRECIATION_RUN", "Depreciation posted up to " + limit + ": " + entries + " charge(s), total " + total);
        }
        return new DepreciationRunResult(months, entries, total, limit);
    }

    /**
     * Charges one asset up to {@code limit} (used before a disposal) and posts the result. Returns the amount charged.
     */
    @Transactional
    public BigDecimal runFor(FixedAsset asset, LocalDate limit) {
        Map<MonthBranch, BigDecimal> perMonth = new LinkedHashMap<>();
        depreciate(asset, limit, perMonth);
        postMonths(perMonth);
        return perMonth.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Every day: catches up months that ended since the last run. Does nothing while the module is not activated. */
    @Scheduled(cron = "0 30 5 * * *")
    @Transactional
    public void scheduledRun() {
        if (!moduleService.isEnabled(ModuleKey.ASSETS)) {
            return;
        }
        run(endOfLastMonth());
    }

    private static LocalDate endOfLastMonth() {
        return YearMonth.now().minusMonths(1).atEndOfMonth();
    }

    /** Creates the missing monthly entries for one asset and adds the amounts to {@code perMonth}. Returns how many were created. */
    private int depreciate(FixedAsset asset, LocalDate limit, Map<MonthBranch, BigDecimal> perMonth) {
        if (asset.getStatus() != AssetStatus.ACTIVE) {
            return 0;
        }
        YearMonth month = asset.getLastDepreciatedThrough() == null
                ? YearMonth.from(asset.getDepreciationStartDate())
                : YearMonth.from(asset.getLastDepreciatedThrough()).plusMonths(1);
        int created = 0;
        BigDecimal depreciable = asset.getCost().subtract(asset.getSalvageValue());
        while (!month.atEndOfMonth().isAfter(limit)) {
            BigDecimal remaining = depreciable.subtract(asset.getAccumulatedDepreciation());
            if (remaining.compareTo(new BigDecimal("0.005")) < 0) {
                asset.setStatus(AssetStatus.FULLY_DEPRECIATED);
                break;
            }
            BigDecimal amount = monthlyAmount(asset, depreciable, remaining, month);
            asset.setAccumulatedDepreciation(asset.getAccumulatedDepreciation().add(amount));
            asset.setLastDepreciatedThrough(month.atEndOfMonth());

            DepreciationEntry entry = new DepreciationEntry();
            entry.setAsset(asset);
            entry.setPeriodEnd(month.atEndOfMonth());
            entry.setBranchId(asset.getBranchId());
            entry.setAmount(amount);
            entry.setAccumulatedAfter(asset.getAccumulatedDepreciation());
            entry.setBookValueAfter(asset.bookValue());
            entryRepository.save(entry);
            perMonth.merge(new MonthBranch(month, asset.getBranchId()), amount, BigDecimal::add);
            created++;

            if (depreciable.subtract(asset.getAccumulatedDepreciation()).compareTo(new BigDecimal("0.005")) < 0) {
                asset.setStatus(AssetStatus.FULLY_DEPRECIATED);
                break;
            }
            month = month.plusMonths(1);
        }
        assetRepository.save(asset);
        return created;
    }

    private BigDecimal monthlyAmount(FixedAsset asset, BigDecimal depreciable, BigDecimal remaining, YearMonth month) {
        int life = Math.max(1, asset.getUsefulLifeMonths());
        BigDecimal amount;
        if (asset.getMethod() == DepreciationMethod.DECLINING_BALANCE) {
            amount = asset.bookValue().multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP);
        } else {
            amount = depreciable.divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP);
        }
        if (asset.isProrateFirstMonth() && month.equals(YearMonth.from(asset.getDepreciationStartDate()))) {
            // only the days from the start date to the end of its month; the last month absorbs the difference
            int daysInMonth = month.lengthOfMonth();
            int daysUsed = daysInMonth - asset.getDepreciationStartDate().getDayOfMonth() + 1;
            amount = amount.multiply(BigDecimal.valueOf(daysUsed)).divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
        }
        if (amount.compareTo(remaining) > 0 || remaining.subtract(amount).compareTo(new BigDecimal("0.05")) < 0) {
            // last month (or a rounding crumb left over): take everything that is left
            amount = remaining;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** One month's charges of one branch (null = head office) — posted together as one journal entry. */
    private record MonthBranch(YearMonth month, Long branchId) {
    }

    private void postMonths(Map<MonthBranch, BigDecimal> perMonth) {
        List<MonthBranch> keys = new ArrayList<>(perMonth.keySet());
        keys.sort(java.util.Comparator.comparing(MonthBranch::month));
        for (MonthBranch key : keys) {
            BigDecimal amount = perMonth.get(key);
            if (amount.signum() > 0) {
                accountingService.postAssetDepreciation(key.month().getYear() * 100L + key.month().getMonthValue(),
                        "Depreciation for " + key.month(), amount, key.branchId());
            }
        }
    }
}
