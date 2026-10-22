package com.posapp.reports;

import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.reports.dto.WorkOrderReportRows.*;
import com.posapp.timetracking.TimeTrackingService;
import com.posapp.workorder.WorkOrder;
import com.posapp.workorder.WorkOrderCost;
import com.posapp.workorder.WorkOrderCostRepository;
import com.posapp.workorder.WorkOrderLine;
import com.posapp.workorder.WorkOrderLineRepository;
import com.posapp.workorder.WorkOrderRepository;
import com.posapp.workorder.WorkOrderStatus;
import com.posapp.workorder.WorkOrderType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Work order reports: the job list, profitability of customer jobs, maintenance cost by asset, and overdue jobs. */
@Service
public class WorkOrderReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final WorkOrderRepository repository;
    private final WorkOrderLineRepository lineRepository;
    private final WorkOrderCostRepository costRepository;
    private final InvoiceRepository invoiceRepository;
    private final TimeTrackingService timeService;

    public WorkOrderReportService(WorkOrderRepository repository, WorkOrderLineRepository lineRepository, WorkOrderCostRepository costRepository,
                                  InvoiceRepository invoiceRepository, TimeTrackingService timeService) {
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.costRepository = costRepository;
        this.invoiceRepository = invoiceRepository;
        this.timeService = timeService;
    }

    private record Costs(BigDecimal parts, BigDecimal external) {
    }

    private Map<Long, Costs> costs(List<WorkOrder> list) {
        if (list.isEmpty()) return Map.of();
        List<Long> ids = list.stream().map(WorkOrder::getId).toList();
        Map<Long, BigDecimal> parts = lineRepository.findByWorkOrderIdIn(ids).stream().collect(Collectors.groupingBy(WorkOrderLine::getWorkOrderId,
                Collectors.reducing(ZERO, l -> l.getUnitCost().multiply(BigDecimal.valueOf(l.getQuantity())), BigDecimal::add)));
        Map<Long, BigDecimal> external = costRepository.findByWorkOrderIdIn(ids).stream().collect(Collectors.groupingBy(WorkOrderCost::getWorkOrderId,
                Collectors.reducing(ZERO, WorkOrderCost::getAmount, BigDecimal::add)));
        return list.stream().collect(Collectors.toMap(WorkOrder::getId, w -> new Costs(parts.getOrDefault(w.getId(), ZERO), external.getOrDefault(w.getId(), ZERO))));
    }

    private BigDecimal hours(WorkOrder w) {
        return w.getTimeProjectId() == null ? ZERO : timeService.hoursOf(w.getTimeProjectId());
    }

    private static LocalDate day(java.time.Instant i) {
        return i == null ? null : i.atZone(ZoneOffset.UTC).toLocalDate();
    }

    @Transactional(readOnly = true)
    public List<WorkOrderRow> workOrders(LocalDate from, LocalDate to) {
        List<WorkOrder> list = repository.findAllByOrderByCreatedAtDesc().stream().filter(w -> {
            LocalDate d = w.getRequestedDate() != null ? w.getRequestedDate() : day(w.getCreatedAt());
            return !d.isBefore(from) && !d.isAfter(to);
        }).toList();
        Map<Long, Costs> costs = costs(list);
        return list.stream().map(w -> new WorkOrderRow(w.getNumber(), w.getType().name(), w.getStatus().name(), w.getPriority().name(), w.getTitle(),
                w.getCustomerName(), w.getAssetName(), w.getAssignedToName(), w.getRequestedDate(), w.getDueDate(), hours(w), costs.get(w.getId()).parts(),
                costs.get(w.getId()).external(), w.getInvoiceNumber())).toList();
    }

    @Transactional(readOnly = true)
    public List<ProfitRow> profitability(LocalDate from, LocalDate to) {
        List<WorkOrder> list = repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(w -> w.getType() == WorkOrderType.CUSTOMER && w.getStatus() == WorkOrderStatus.COMPLETED && w.getCompletedAt() != null)
                .filter(w -> !day(w.getCompletedAt()).isBefore(from) && !day(w.getCompletedAt()).isAfter(to))
                .sorted(Comparator.comparing(WorkOrder::getCompletedAt)).toList();
        Map<Long, Costs> costs = costs(list);
        return list.stream().map(w -> {
            BigDecimal revenue = w.getInvoiceId() == null ? ZERO
                    : invoiceRepository.findById(w.getInvoiceId()).map(Invoice::getTotalAmount).orElse(ZERO);
            BigDecimal cost = costs.get(w.getId()).parts().add(costs.get(w.getId()).external());
            BigDecimal margin = revenue.subtract(cost);
            BigDecimal pct = revenue.signum() == 0 ? null : margin.multiply(HUNDRED).divide(revenue, 1, RoundingMode.HALF_UP);
            return new ProfitRow(w.getNumber(), w.getTitle(), w.getCustomerName(), day(w.getCompletedAt()), revenue, costs.get(w.getId()).parts(),
                    costs.get(w.getId()).external(), hours(w), margin, pct);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AssetCostRow> assetCosts(LocalDate from, LocalDate to) {
        List<WorkOrder> list = repository.findAll().stream()
                .filter(w -> w.getAssetId() != null && w.getStatus() != WorkOrderStatus.CANCELLED)
                .filter(w -> {
                    LocalDate d = w.getCompletedAt() != null ? day(w.getCompletedAt()) : (w.getRequestedDate() != null ? w.getRequestedDate() : day(w.getCreatedAt()));
                    return !d.isBefore(from) && !d.isAfter(to);
                }).toList();
        Map<Long, Costs> costs = costs(list);
        Map<Long, List<WorkOrder>> byAsset = list.stream().collect(Collectors.groupingBy(WorkOrder::getAssetId));
        return byAsset.values().stream().map(l -> {
            BigDecimal parts = l.stream().map(w -> costs.get(w.getId()).parts()).reduce(ZERO, BigDecimal::add);
            BigDecimal external = l.stream().map(w -> costs.get(w.getId()).external()).reduce(ZERO, BigDecimal::add);
            BigDecimal hours = l.stream().map(this::hours).reduce(ZERO, BigDecimal::add);
            LocalDate last = l.stream().map(w -> w.getCompletedAt() != null ? day(w.getCompletedAt()) : w.getRequestedDate()).filter(d -> d != null)
                    .max(Comparator.naturalOrder()).orElse(null);
            return new AssetCostRow(l.get(0).getAssetName(), l.size(), hours, parts, external, parts.add(external), last);
        }).sorted(Comparator.comparing(AssetCostRow::totalCost).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public List<OverdueRow> overdue() {
        LocalDate today = LocalDate.now();
        return repository.findAll().stream()
                .filter(w -> w.getDueDate() != null && w.getDueDate().isBefore(today)
                        && (w.getStatus() == WorkOrderStatus.OPEN || w.getStatus() == WorkOrderStatus.IN_PROGRESS || w.getStatus() == WorkOrderStatus.ON_HOLD))
                .sorted(Comparator.comparing(WorkOrder::getDueDate))
                .map(w -> new OverdueRow(w.getNumber(), w.getTitle(), w.getCustomerName(), w.getAssignedToName(), w.getPriority().name(), w.getStatus().name(),
                        w.getDueDate(), ChronoUnit.DAYS.between(w.getDueDate(), today))).toList();
    }
}
