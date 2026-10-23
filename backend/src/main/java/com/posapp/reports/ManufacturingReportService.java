package com.posapp.reports;
import com.posapp.common.Qty;

import com.posapp.manufacturing.ManufacturingOrder;
import com.posapp.manufacturing.ManufacturingOrderConsumption;
import com.posapp.manufacturing.ManufacturingOrderRepository;
import com.posapp.manufacturing.ManufacturingOrderRun;
import com.posapp.manufacturing.ManufacturingOrderRunOperation;
import com.posapp.manufacturing.ManufacturingOrderRunRepository;
import com.posapp.manufacturing.ManufacturingOrderStatus;
import com.posapp.reports.dto.ManufacturingRows.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MF-5: the Manufacturing reports. Everything is read from what the production runs recorded, so later price or recipe changes never move a past figure. */
@Service
public class ManufacturingReportService {

    private final ManufacturingOrderRunRepository runRepository;
    private final ManufacturingOrderRepository orderRepository;

    public ManufacturingReportService(ManufacturingOrderRunRepository runRepository, ManufacturingOrderRepository orderRepository) {
        this.runRepository = runRepository;
        this.orderRepository = orderRepository;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal yieldOf(BigDecimal good, BigDecimal scrapped) {
        BigDecimal started = good.add(scrapped);
        return started.signum() > 0 ? good.multiply(BigDecimal.valueOf(100)).divide(started, 1, RoundingMode.HALF_UP) : null;
    }

    private static BigDecimal unit(BigDecimal total, BigDecimal credit, BigDecimal good) {
        return good.signum() > 0 ? total.subtract(credit).divide(good, 2, RoundingMode.HALF_UP) : null;
    }

    private List<ManufacturingOrderRun> runs(Instant from, Instant to, Long productId) {
        List<ManufacturingOrderRun> out = new ArrayList<>();
        for (ManufacturingOrderRun r : runRepository.findByCreatedAtBetweenOrderByCreatedAtAscIdAsc(from, to)) {
            if (productId != null && !productId.equals(r.getManufacturingOrder().getProduct().getId())) continue;
            out.add(r);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<RunRow> productionRuns(Instant from, Instant to, Long productId) {
        List<RunRow> rows = new ArrayList<>();
        for (ManufacturingOrderRun r : runs(from, to, productId)) {
            ManufacturingOrder mo = r.getManufacturingOrder();
            BigDecimal total = nz(r.getTotalCost());
            rows.add(new RunRow(r.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), mo.getOrderNumber(), mo.getProduct().getName(), r.getQuantityProduced(),
                    r.getQuantityScrapped(), yieldOf(r.getQuantityProduced(), r.getQuantityScrapped()), nz(r.getMaterialCost()), nz(r.getLabourCost()),
                    nz(r.getMachineCost()), nz(r.getOverheadCost()), nz(r.getByProductCredit()), total, unit(total, nz(r.getByProductCredit()), r.getQuantityProduced())));
        }
        return rows;
    }

    private static final class Acc {
        int runs; BigDecimal good = BigDecimal.ZERO, scrap = BigDecimal.ZERO;
        BigDecimal material = BigDecimal.ZERO, labour = BigDecimal.ZERO, machine = BigDecimal.ZERO, overhead = BigDecimal.ZERO, credit = BigDecimal.ZERO, total = BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public List<ProductCostRow> costPerProduct(Instant from, Instant to, Long productId) {
        Map<String, Acc> map = new LinkedHashMap<>();
        for (ManufacturingOrderRun r : runs(from, to, productId)) {
            Acc a = map.computeIfAbsent(r.getManufacturingOrder().getProduct().getName(), k -> new Acc());
            a.runs++;
            a.good = a.good.add(r.getQuantityProduced());
            a.scrap = a.scrap.add(r.getQuantityScrapped());
            a.material = a.material.add(nz(r.getMaterialCost()));
            a.labour = a.labour.add(nz(r.getLabourCost()));
            a.machine = a.machine.add(nz(r.getMachineCost()));
            a.overhead = a.overhead.add(nz(r.getOverheadCost()));
            a.credit = a.credit.add(nz(r.getByProductCredit()));
            a.total = a.total.add(nz(r.getTotalCost()));
        }
        List<ProductCostRow> rows = new ArrayList<>();
        map.forEach((name, a) -> rows.add(new ProductCostRow(name, a.runs, a.good, a.scrap, yieldOf(a.good, a.scrap), a.material, a.labour, a.machine, a.overhead, a.credit, a.total, unit(a.total, a.credit, a.good))));
        rows.sort(Comparator.comparing(ProductCostRow::productName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ComponentUseRow> componentUse(Instant from, Instant to, Long productId) {
        class C { int runs; BigDecimal planned = BigDecimal.ZERO, used = BigDecimal.ZERO; BigDecimal cost = BigDecimal.ZERO, varianceCost = BigDecimal.ZERO; }
        Map<String, C> map = new LinkedHashMap<>();
        for (ManufacturingOrderRun r : runs(from, to, productId)) {
            for (ManufacturingOrderConsumption c : r.getConsumptions()) {
                C a = map.computeIfAbsent(c.getComponentName(), k -> new C());
                BigDecimal planned = c.getPlannedQuantity() != null ? c.getPlannedQuantity() : c.getQuantityConsumed();
                BigDecimal unitCost = nz(c.getUnitCost());
                a.runs++;
                a.planned = a.planned.add(planned);
                a.used = a.used.add(c.getQuantityConsumed());
                a.cost = a.cost.add(unitCost.multiply(c.getQuantityConsumed()));
                a.varianceCost = a.varianceCost.add(unitCost.multiply(c.getQuantityConsumed().subtract(planned)));
            }
        }
        List<ComponentUseRow> rows = new ArrayList<>();
        map.forEach((name, a) -> rows.add(new ComponentUseRow(name, a.runs, a.planned, a.used, a.used.subtract(a.planned),
                a.planned.signum() > 0 ? a.used.subtract(a.planned).multiply(BigDecimal.valueOf(100)).divide(a.planned, 1, RoundingMode.HALF_UP) : null,
                a.cost.setScale(2, RoundingMode.HALF_UP), a.varianceCost.setScale(2, RoundingMode.HALF_UP))));
        rows.sort(Comparator.comparing(ComponentUseRow::componentName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<WorkstationRow> workstations(Instant from, Instant to, Long productId) {
        class W { int runs; BigDecimal planned = BigDecimal.ZERO, actual = BigDecimal.ZERO, machine = BigDecimal.ZERO, labour = BigDecimal.ZERO; }
        Map<String, W> map = new LinkedHashMap<>();
        for (ManufacturingOrderRun r : runs(from, to, productId)) {
            for (ManufacturingOrderRunOperation o : r.getOperations()) {
                W w = map.computeIfAbsent(o.getWorkstationName() != null ? o.getWorkstationName() : "No workstation (hand work)", k -> new W());
                w.runs++;
                w.planned = w.planned.add(nz(o.getPlannedMinutes()));
                w.actual = w.actual.add(nz(o.getActualMinutes()));
                w.machine = w.machine.add(nz(o.getMachineCost()));
                w.labour = w.labour.add(nz(o.getLabourCost()));
            }
        }
        List<WorkstationRow> rows = new ArrayList<>();
        map.forEach((name, w) -> rows.add(new WorkstationRow(name, w.runs, w.planned, w.actual, w.actual.subtract(w.planned), w.machine, w.labour, w.machine.add(w.labour))));
        rows.sort(Comparator.comparing(WorkstationRow::workstation, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ScrapRow> scrap(Instant from, Instant to, Long productId) {
        class S { int runs; BigDecimal units = BigDecimal.ZERO; BigDecimal cost = BigDecimal.ZERO; }
        Map<String, S> map = new LinkedHashMap<>();
        Map<String, String[]> names = new LinkedHashMap<>();
        for (ManufacturingOrderRun r : runs(from, to, productId)) {
            if (r.getQuantityScrapped().signum() <= 0) continue;
            String product = r.getManufacturingOrder().getProduct().getName();
            String reason = r.getScrapReason() != null ? r.getScrapReason() : "No reason given";
            String key = product + "\u0000" + reason;
            names.putIfAbsent(key, new String[]{product, reason});
            S s = map.computeIfAbsent(key, k -> new S());
            s.runs++;
            s.units = s.units.add(r.getQuantityScrapped());
            s.cost = s.cost.add(nz(r.getScrapCost()));
        }
        List<ScrapRow> rows = new ArrayList<>();
        map.forEach((k, s) -> rows.add(new ScrapRow(names.get(k)[0], names.get(k)[1], s.runs, s.units, s.cost)));
        rows.sort(Comparator.comparing(ScrapRow::scrapCost).reversed());
        return rows;
    }

    /** Orders created in the period: what was planned against what has been made and spent so far. */
    @Transactional(readOnly = true)
    public List<OrderPlanRow> orderPlan(Instant from, Instant to, Long productId) {
        List<OrderPlanRow> rows = new ArrayList<>();
        for (ManufacturingOrder mo : orderRepository.findByCreatedAtBetweenOrderByCreatedAtAscIdAsc(from, to)) {
            if (productId != null && !productId.equals(mo.getProduct().getId())) continue;
            if (mo.getStatus() == ManufacturingOrderStatus.CANCELLED) continue;
            BigDecimal planned = nz(mo.getPlannedMaterialCost()).add(nz(mo.getPlannedLabourCost())).add(nz(mo.getPlannedMachineCost())).add(nz(mo.getPlannedOverheadCost()));
            BigDecimal actual = BigDecimal.ZERO;
            for (ManufacturingOrderRun r : mo.getRuns()) actual = actual.add(nz(r.getTotalCost()));
            BigDecimal share = mo.getQuantityPlanned().signum() > 0
                    ? planned.multiply(mo.getQuantityProduced()).divide(mo.getQuantityPlanned(), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            rows.add(new OrderPlanRow(mo.getOrderNumber(), mo.getProduct().getName(), mo.getStatus().name(), mo.getScheduledDate(), mo.getQuantityPlanned(),
                    mo.getQuantityProduced(), Qty.max(BigDecimal.ZERO, mo.getQuantityPlanned().subtract(mo.getQuantityProduced())), planned, share, actual, actual.subtract(share)));
        }
        return rows;
    }
}
