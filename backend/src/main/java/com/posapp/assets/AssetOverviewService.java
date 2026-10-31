package com.posapp.assets;

import com.posapp.assets.dto.AssetDtos.AttentionItem;
import com.posapp.assets.dto.AssetDtos.NamedAmount;
import com.posapp.assets.dto.AssetDtos.OverviewDto;
import com.posapp.branch.BranchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The numbers on the Assets overview page: value by category and branch, and what needs attention. */
@Service
public class AssetOverviewService {

    private static final int LIMIT = 20;

    private final FixedAssetService assetService;
    private final AssetDepreciationService depreciationService;
    private final AssetSettingsService settingsService;
    private final AssetVerificationRepository verificationRepository;
    private final BranchService branchService;

    public AssetOverviewService(FixedAssetService assetService, AssetDepreciationService depreciationService, AssetSettingsService settingsService,
                                AssetVerificationRepository verificationRepository, BranchService branchService) {
        this.assetService = assetService;
        this.depreciationService = depreciationService;
        this.settingsService = settingsService;
        this.verificationRepository = verificationRepository;
        this.branchService = branchService;
    }

    @Transactional(readOnly = true)
    public OverviewDto overview(Long branchId) {
        AssetSettings settings = settingsService.entity();
        LocalDate today = LocalDate.now();
        Long hq = branchService.headquartersId();
        Map<Long, String> branchNames = new HashMap<>();
        branchService.listAll().forEach(b -> branchNames.put(b.id(), b.name()));
        long count = 0, fully = 0, disposed = 0, noCustodian = 0;
        BigDecimal cost = BigDecimal.ZERO, accumulated = BigDecimal.ZERO, nextCharge = BigDecimal.ZERO;
        LocalDate through = null;
        int behind = 0;
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        Map<String, long[]> cats = new LinkedHashMap<>();
        Map<String, BigDecimal[]> catAmounts = new LinkedHashMap<>();
        Map<String, long[]> brs = new LinkedHashMap<>();
        Map<String, BigDecimal[]> brAmounts = new LinkedHashMap<>();
        List<AttentionItem> soon = new ArrayList<>();
        List<AttentionItem> warranty = new ArrayList<>();
        List<AttentionItem> insurance = new ArrayList<>();
        for (FixedAsset a : assetService.visibleAssets(null, branchId)) {
            if (a.getStatus() == AssetStatus.DISPOSED) {
                disposed++;
                continue;
            }
            count++;
            if (a.getStatus() == AssetStatus.FULLY_DEPRECIATED) fully++;
            if (a.getCustodianName() == null) noCustodian++;
            cost = cost.add(a.getCost());
            accumulated = accumulated.add(a.getAccumulatedDepreciation());
            BigDecimal book = a.bookValue();
            String cat = a.getCategory().getName();
            cats.computeIfAbsent(cat, k -> new long[1])[0]++;
            catAmounts.computeIfAbsent(cat, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            catAmounts.get(cat)[0] = catAmounts.get(cat)[0].add(a.getCost());
            catAmounts.get(cat)[1] = catAmounts.get(cat)[1].add(book);
            String br = branchNames.getOrDefault(a.getBranchId() != null ? a.getBranchId() : hq, "Head office");
            brs.computeIfAbsent(br, k -> new long[1])[0]++;
            brAmounts.computeIfAbsent(br, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            brAmounts.get(br)[0] = brAmounts.get(br)[0].add(a.getCost());
            brAmounts.get(br)[1] = brAmounts.get(br)[1].add(book);
            if (a.getLastDepreciatedThrough() != null && (through == null || a.getLastDepreciatedThrough().isAfter(through))) through = a.getLastDepreciatedThrough();
            if (a.getStatus() == AssetStatus.ACTIVE) {
                List<BigDecimal> projection = depreciationService.project(a, 600);
                if (!projection.isEmpty()) {
                    nextCharge = nextCharge.add(projection.get(0));
                    if (projection.size() <= settings.getSoonMonths()) {
                        soon.add(new AttentionItem(a.getId(), a.getCode(), a.getName(), projection.size() + " month(s) left, book value " + book.setScale(2, java.math.RoundingMode.HALF_UP), null, projection.size()));
                    }
                }
                YearMonth next = AssetDepreciationService.nextMonth(a);
                if (!next.isAfter(lastMonth)) {
                    behind = Math.max(behind, (int) ChronoUnit.MONTHS.between(next, lastMonth) + 1);
                }
            }
            attention(warranty, a, a.getWarrantyExpiresOn(), "Warranty", settings.getAlertDays(), today);
            attention(insurance, a, a.getInsuranceExpiresOn(), "Insurance", settings.getAlertDays(), today);
        }
        soon.sort(Comparator.comparing(AttentionItem::days));
        warranty.sort(Comparator.comparing(AttentionItem::date));
        insurance.sort(Comparator.comparing(AttentionItem::date));
        return new OverviewDto(count, cost, accumulated, cost.subtract(accumulated), fully, disposed, nextCharge, through, behind, noCustodian,
                verificationRepository.countByStatus(AssetVerification.OPEN), settings.getAlertDays(), settings.getSoonMonths(),
                named(cats, catAmounts), named(brs, brAmounts), cap(soon), cap(warranty), cap(insurance));
    }

    private static void attention(List<AttentionItem> out, FixedAsset a, LocalDate expires, String what, int alertDays, LocalDate today) {
        String state = FixedAssetService.state(expires, alertDays, today);
        if (state == null || "OK".equals(state)) return;
        int days = (int) ChronoUnit.DAYS.between(today, expires);
        out.add(new AttentionItem(a.getId(), a.getCode(), a.getName(), what + (days < 0 ? " expired " + (-days) + " day(s) ago" : days == 0 ? " expires today" : " expires in " + days + " day(s)"), expires, days));
    }

    private static List<NamedAmount> named(Map<String, long[]> counts, Map<String, BigDecimal[]> amounts) {
        List<NamedAmount> out = new ArrayList<>();
        counts.forEach((k, v) -> out.add(new NamedAmount(k, v[0], amounts.get(k)[0], amounts.get(k)[1])));
        out.sort(Comparator.comparing(NamedAmount::bookValue).reversed());
        return out;
    }

    private static List<AttentionItem> cap(List<AttentionItem> list) {
        return list.size() > LIMIT ? list.subList(0, LIMIT) : list;
    }
}
