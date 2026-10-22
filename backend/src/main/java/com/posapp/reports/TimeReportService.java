package com.posapp.reports;

import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.reports.dto.TimeReportRows.*;
import com.posapp.timetracking.TimeEntry;
import com.posapp.timetracking.TimeEntryRepository;
import com.posapp.timetracking.TimeEntryStatus;
import com.posapp.timetracking.TimeProject;
import com.posapp.timetracking.TimeProjectRepository;
import com.posapp.timetracking.TimeSettingsRepository;
import com.posapp.timetracking.TimeTask;
import com.posapp.timetracking.TimeTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** The time tracking reports: timesheets, project hours against budget, unbilled time (work in progress), utilisation and the entry list. */
@Service
public class TimeReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TimeEntryRepository entryRepository;
    private final TimeProjectRepository projectRepository;
    private final TimeTaskRepository taskRepository;
    private final CustomerRepository customerRepository;
    private final TimeSettingsRepository settingsRepository;

    public TimeReportService(TimeEntryRepository entryRepository, TimeProjectRepository projectRepository, TimeTaskRepository taskRepository,
                             CustomerRepository customerRepository, TimeSettingsRepository settingsRepository) {
        this.entryRepository = entryRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.customerRepository = customerRepository;
        this.settingsRepository = settingsRepository;
    }

    private static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal amount(TimeEntry e) {
        return e.getHourlyRate().multiply(BigDecimal.valueOf(e.getMinutes())).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    private static boolean unbilled(TimeEntry e) {
        return e.isBillable() && e.getStatus() == TimeEntryStatus.APPROVED && e.getInvoiceId() == null;
    }

    private Map<Long, String> customers() {
        return customerRepository.findAll().stream().collect(Collectors.toMap(Customer::getId, c -> c.getName() == null ? "" : c.getName(), (a, b) -> a));
    }

    private List<TimeEntry> between(LocalDate from, LocalDate to) {
        return entryRepository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(from, to);
    }

    @Transactional(readOnly = true)
    public List<TimesheetRow> timesheets(LocalDate from, LocalDate to) {
        Map<String, List<TimeEntry>> byPerson = between(from, to).stream().collect(Collectors.groupingBy(TimeEntry::getUserName, TreeMap::new, Collectors.toList()));
        return byPerson.entrySet().stream().map(en -> {
            List<TimeEntry> l = en.getValue();
            long all = l.stream().mapToLong(TimeEntry::getMinutes).sum();
            long bill = l.stream().filter(TimeEntry::isBillable).mapToLong(TimeEntry::getMinutes).sum();
            long pending = l.stream().filter(e -> e.getStatus() == TimeEntryStatus.DRAFT || e.getStatus() == TimeEntryStatus.SUBMITTED || e.getStatus() == TimeEntryStatus.REJECTED)
                    .mapToLong(TimeEntry::getMinutes).sum();
            long invoiced = l.stream().filter(e -> e.getStatus() == TimeEntryStatus.INVOICED).mapToLong(TimeEntry::getMinutes).sum();
            BigDecimal amount = l.stream().filter(e -> e.isBillable()).map(TimeReportService::amount).reduce(ZERO, BigDecimal::add);
            return new TimesheetRow(en.getKey(), hours(all), hours(bill), hours(all - bill), amount, hours(pending), hours(invoiced));
        }).toList();
    }

    /** Every project with its hours against the budget, over its whole life. */
    @Transactional(readOnly = true)
    public List<ProjectHoursRow> projectHours() {
        Map<Long, String> customers = customers();
        Map<Long, List<TimeEntry>> byProject = entryRepository.findAll().stream().collect(Collectors.groupingBy(TimeEntry::getProjectId));
        return projectRepository.findAllByOrderByNameAsc().stream().map(p -> {
            List<TimeEntry> l = byProject.getOrDefault(p.getId(), List.of());
            long all = l.stream().mapToLong(TimeEntry::getMinutes).sum();
            long bill = l.stream().filter(TimeEntry::isBillable).mapToLong(TimeEntry::getMinutes).sum();
            BigDecimal billed = l.stream().filter(e -> e.getStatus() == TimeEntryStatus.INVOICED).map(TimeReportService::amount).reduce(ZERO, BigDecimal::add);
            BigDecimal wip = l.stream().filter(TimeReportService::unbilled).map(TimeReportService::amount).reduce(ZERO, BigDecimal::add);
            BigDecimal budget = p.getBudgetHours();
            BigDecimal percent = budget == null ? null : hours(all).multiply(HUNDRED).divide(budget, 1, RoundingMode.HALF_UP);
            BigDecimal left = budget == null ? null : budget.subtract(hours(all));
            return new ProjectHoursRow(p.getName(), p.getCustomerId() == null ? null : customers.get(p.getCustomerId()), p.getStatus().name(), budget, hours(all),
                    hours(bill), percent, left, billed, wip);
        }).toList();
    }

    /** Approved billable time not yet on an invoice, per project. */
    @Transactional(readOnly = true)
    public List<UnbilledRow> unbilled() {
        Map<Long, String> customers = customers();
        Map<Long, TimeProject> projects = projectRepository.findAll().stream().collect(Collectors.toMap(TimeProject::getId, p -> p));
        Map<Long, List<TimeEntry>> byProject = entryRepository.findByStatus(TimeEntryStatus.APPROVED).stream().filter(TimeReportService::unbilled)
                .collect(Collectors.groupingBy(TimeEntry::getProjectId));
        return byProject.entrySet().stream().map(en -> {
            TimeProject p = projects.get(en.getKey());
            List<TimeEntry> l = en.getValue();
            return new UnbilledRow(p == null ? "?" : p.getName(), p == null || p.getCustomerId() == null ? null : customers.get(p.getCustomerId()), l.size(),
                    hours(l.stream().mapToLong(TimeEntry::getMinutes).sum()), l.stream().map(TimeReportService::amount).reduce(ZERO, BigDecimal::add),
                    l.stream().map(TimeEntry::getWorkDate).min(Comparator.naturalOrder()).orElse(null));
        }).sorted(Comparator.comparing(UnbilledRow::amount).reversed()).toList();
    }

    /** Hours logged against the working hours available (Monday to Friday, hours per day from the settings). */
    @Transactional(readOnly = true)
    public List<UtilisationRow> utilisation(LocalDate from, LocalDate to) {
        BigDecimal perDay = settingsRepository.findAll().stream().findFirst().map(s -> s.getDailyCapacityHours()).orElse(BigDecimal.valueOf(8));
        long weekdays = from.datesUntil(to.plusDays(1)).filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY).count();
        BigDecimal capacity = perDay.multiply(BigDecimal.valueOf(weekdays));
        Map<String, List<TimeEntry>> byPerson = between(from, to).stream().collect(Collectors.groupingBy(TimeEntry::getUserName, TreeMap::new, Collectors.toList()));
        return byPerson.entrySet().stream().map(en -> {
            long all = en.getValue().stream().mapToLong(TimeEntry::getMinutes).sum();
            long bill = en.getValue().stream().filter(TimeEntry::isBillable).mapToLong(TimeEntry::getMinutes).sum();
            BigDecimal logged = hours(all);
            BigDecimal billable = hours(bill);
            BigDecimal util = capacity.signum() == 0 ? ZERO : logged.multiply(HUNDRED).divide(capacity, 1, RoundingMode.HALF_UP);
            BigDecimal billPct = capacity.signum() == 0 ? ZERO : billable.multiply(HUNDRED).divide(capacity, 1, RoundingMode.HALF_UP);
            return new UtilisationRow(en.getKey(), capacity, logged, billable, util, billPct);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<EntryRow> entries(LocalDate from, LocalDate to) {
        Map<Long, String> customers = customers();
        Map<Long, TimeProject> projects = projectRepository.findAll().stream().collect(Collectors.toMap(TimeProject::getId, p -> p));
        Map<Long, String> tasks = taskRepository.findAll().stream().collect(Collectors.toMap(TimeTask::getId, TimeTask::getName));
        return between(from, to).stream().map(e -> {
            TimeProject p = projects.get(e.getProjectId());
            return new EntryRow(e.getWorkDate(), e.getUserName(), p == null ? "?" : p.getName(), p == null || p.getCustomerId() == null ? null : customers.get(p.getCustomerId()),
                    e.getTaskId() == null ? null : tasks.get(e.getTaskId()), e.getDescription(), hours(e.getMinutes()), e.isBillable() ? "Yes" : "No",
                    e.getHourlyRate(), amount(e), e.getStatus().name());
        }).toList();
    }
}
