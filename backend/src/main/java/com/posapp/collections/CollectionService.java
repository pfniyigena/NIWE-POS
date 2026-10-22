package com.posapp.collections;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchService;
import com.posapp.collections.CollectionDtos.Aging;
import com.posapp.collections.CollectionDtos.CustomerRow;
import com.posapp.collections.CollectionDtos.CustomersPage;
import com.posapp.collections.CollectionDtos.ItemRow;
import com.posapp.collections.CollectionDtos.ItemsPage;
import com.posapp.collections.CollectionDtos.LogRow;
import com.posapp.collections.CollectionDtos.MailedStatement;
import com.posapp.collections.CollectionDtos.PaymentLine;
import com.posapp.collections.CollectionDtos.Printed;
import com.posapp.collections.CollectionDtos.RemindResult;
import com.posapp.collections.CollectionDtos.ReminderItem;
import com.posapp.collections.CollectionDtos.SettingsDto;
import com.posapp.collections.CollectionDtos.SettingsRequest;
import com.posapp.collections.CollectionDtos.StatementData;
import com.posapp.collections.CollectionDtos.StatementLine;
import com.posapp.collections.CollectionDtos.Summary;
import com.posapp.common.ApiException;
import com.posapp.common.ListPaging;
import com.posapp.credit.CustomerCreditService;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.invoice.Invoice;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceStatus;
import com.posapp.order.OrderRepository;
import com.posapp.order.OrderStatus;
import com.posapp.order.SaleOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collecting what customers owe: every unpaid invoice and every till sale on credit with its age, per customer or per item; a customer's statement (printed or
 * e-mailed); and reminder e-mails about overdue items, by hand or once a day when an admin switches that on. One reminder mail goes to a customer for everything that
 * is overdue, with their statement attached. An item with no due date is due after the customer's credit term, or the default term from the settings.
 *
 * <p>Amounts come from {@link CustomerCreditService} (the same figures the payment screens use), so what is listed here is exactly what can be paid there.
 */
@Service
public class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final List<String> SENT_KINDS = List.of("MANUAL", "AUTO");

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerCreditService creditService;
    private final CollectionSettingsRepository settingsRepository;
    private final CollectionReminderRepository reminderRepository;
    private final CollectionFiles files;
    private final MailService mailService;
    private final BranchService branchService;
    private final AuditLogService auditLogService;

    public CollectionService(CustomerRepository customerRepository, OrderRepository orderRepository, InvoiceRepository invoiceRepository, CustomerCreditService creditService,
                             CollectionSettingsRepository settingsRepository, CollectionReminderRepository reminderRepository, CollectionFiles files, MailService mailService,
                             BranchService branchService, AuditLogService auditLogService) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.creditService = creditService;
        this.settingsRepository = settingsRepository;
        this.reminderRepository = reminderRepository;
        this.files = files;
        this.mailService = mailService;
        this.branchService = branchService;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ settings

    @Transactional
    public CollectionSettings settingsEntity() {
        List<CollectionSettings> all = settingsRepository.findAll();
        if (!all.isEmpty()) return all.get(0);
        return settingsRepository.save(new CollectionSettings());
    }

    private SettingsDto toDto(CollectionSettings s) {
        return new SettingsDto(s.isAutoEnabled(), s.getFirstDays(), s.getRepeatDays(), s.getMaxReminders(), s.getDefaultTermDays(), mailService.isConfigured());
    }

    @Transactional
    public SettingsDto settings() {
        return toDto(settingsEntity());
    }

    private static int range(Integer v, int min, int max, String what) {
        if (v < min || v > max) throw ApiException.badRequest(what + " must be between " + min + " and " + max + ".");
        return v;
    }

    @Transactional
    public SettingsDto updateSettings(SettingsRequest r) {
        CollectionSettings s = settingsEntity();
        if (r.autoEnabled() != null) s.setAutoEnabled(r.autoEnabled());
        if (r.firstDays() != null) s.setFirstDays(range(r.firstDays(), 1, 365, "Days after the due date"));
        if (r.repeatDays() != null) s.setRepeatDays(range(r.repeatDays(), 1, 365, "Days between reminders"));
        if (r.maxReminders() != null) s.setMaxReminders(range(r.maxReminders(), 1, 20, "Number of reminders"));
        if (r.defaultTermDays() != null) s.setDefaultTermDays(range(r.defaultTermDays(), 0, 365, "Payment term"));
        settingsRepository.save(s);
        auditLogService.record("COLLECTION_SETTINGS_CHANGED", "Collections settings: automatic reminders " + (s.isAutoEnabled() ? "on" : "off") + ", first after " + s.getFirstDays() + " days, every "
                + s.getRepeatDays() + " days, at most " + s.getMaxReminders() + ", default term " + s.getDefaultTermDays() + " days", "CollectionSettings", s.getId());
        return toDto(s);
    }

    // ------------------------------------------------------------------ the open items

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    private static LocalDate local(Instant t, LocalDate fallback) {
        return t == null ? fallback : t.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    static String bucket(long days) {
        if (days <= 0) return "CURRENT";
        if (days <= 30) return "1-30";
        if (days <= 60) return "31-60";
        if (days <= 90) return "61-90";
        return "90+";
    }

    private ItemRow item(String kind, Long id, String number, Customer c, LocalDate date, LocalDate dueSet, BigDecimal total, BigDecimal outstanding, Long branchId, LocalDate today, int defTerm) {
        LocalDate due = dueSet;
        boolean assumed = false;
        if (due == null) {
            int term = c.getDefaultCreditTermDays() != null ? c.getDefaultCreditTermDays() : defTerm;
            due = date.plusDays(term);
            assumed = true;
        }
        BigDecimal paid = total.subtract(outstanding);
        if (paid.signum() < 0) paid = ZERO;
        long days = outstanding.signum() > 0 && due.isBefore(today) ? ChronoUnit.DAYS.between(due, today) : 0;
        return new ItemRow(kind, id, number, c.getId(), c.getName(), c.getEmail(), c.getPhone(), date, due, assumed, total, paid, outstanding, days, bucket(days), branchId);
    }

    /** Every unpaid till credit sale and issued invoice of the given customers (all customers when {@code only} is null). */
    private List<ItemRow> open(Long only, LocalDate today) {
        int defTerm = settingsEntity().getDefaultTermDays();
        List<Customer> customers = only != null ? customerRepository.findById(only).map(List::of).orElse(List.of()) : customerRepository.findAll();
        List<ItemRow> out = new ArrayList<>();
        for (Customer c : customers) {
            if (c.isWalkIn()) continue;
            for (SaleOrder o : orderRepository.findCreditOrdersForCustomer(c.getId(), OrderStatus.COMPLETED)) {
                BigDecimal owed = nz(creditService.outstandingForOrder(o));
                if (owed.signum() <= 0) continue;
                out.add(item("POS_SALE", o.getId(), o.getInvoiceNumber(), c, local(o.getCompletedAt(), today), o.getDueDate(), nz(o.getCreditAmount()), owed, o.getBranchId(), today, defTerm));
            }
            for (Invoice inv : invoiceRepository.findByCustomerIdOrderByInvoiceDateAsc(c.getId())) {
                if (inv.getStatus() != InvoiceStatus.ISSUED) continue;
                BigDecimal owed = nz(creditService.outstandingForInvoice(inv));
                if (owed.signum() <= 0) continue;
                out.add(item("INVOICE", inv.getId(), inv.getInvoiceNumber(), c, local(inv.getInvoiceDate(), today), inv.getDueDate(), nz(inv.getTotalAmount()), owed, inv.getBranchId(), today, defTerm));
            }
        }
        return out;
    }

    private static Aging aging(List<ItemRow> items) {
        BigDecimal cur = ZERO, a = ZERO, b = ZERO, c = ZERO, d = ZERO;
        for (ItemRow i : items) {
            switch (i.bucket()) {
                case "CURRENT" -> cur = cur.add(i.outstanding());
                case "1-30" -> a = a.add(i.outstanding());
                case "31-60" -> b = b.add(i.outstanding());
                case "61-90" -> c = c.add(i.outstanding());
                default -> d = d.add(i.outstanding());
            }
        }
        return new Aging(cur, a, b, c, d);
    }

    private static Summary summary(List<ItemRow> items) {
        BigDecimal total = items.stream().map(ItemRow::outstanding).reduce(ZERO, BigDecimal::add);
        List<ItemRow> late = items.stream().filter(i -> i.daysOverdue() > 0).toList();
        BigDecimal overdue = late.stream().map(ItemRow::outstanding).reduce(ZERO, BigDecimal::add);
        Set<Long> owing = items.stream().map(ItemRow::customerId).collect(Collectors.toSet());
        Set<Long> overdueCustomers = late.stream().map(ItemRow::customerId).collect(Collectors.toSet());
        return new Summary(total, overdue, late.size(), owing.size(), overdueCustomers.size(), aging(items));
    }

    private List<ItemRow> filtered(Long customerId, Long branchId, String q, LocalDate today) {
        Long hq = branchId == null ? null : branchService.headquartersId();
        return open(customerId, today).stream()
                .filter(i -> ListPaging.branch(i.branchId(), branchId, hq))
                .filter(i -> ListPaging.has(q, i.number(), i.customerName(), i.customerEmail(), i.customerPhone()))
                .toList();
    }

    private static final Comparator<ItemRow> WORST_FIRST = Comparator.comparingLong(ItemRow::daysOverdue).reversed().thenComparing(ItemRow::outstanding, Comparator.reverseOrder());

    /** The items as the screen shows them: rows of the chosen page; the totals cover every match of the customer / branch / search filters. */
    @Transactional
    public ItemsPage items(boolean canRemind, boolean overdueOnly, String bucket, Long customerId, Long branchId, String q, Integer page, Integer size) {
        List<ItemRow> all = filtered(customerId, branchId, q, LocalDate.now());
        List<ItemRow> rows = all.stream()
                .filter(i -> !overdueOnly || i.daysOverdue() > 0)
                .filter(i -> bucket == null || bucket.isBlank() || bucket.equalsIgnoreCase(i.bucket()))
                .sorted(WORST_FIRST).toList();
        List<ItemRow> shown = ListPaging.of(rows, page, size).getBody();
        return new ItemsPage(shown, rows.size(), summary(all), canRemind, mailService.isConfigured(), settingsEntity().getDefaultTermDays());
    }

    /** All matching items, not one page — for the exports. */
    @Transactional
    public List<ItemRow> allItems(boolean overdueOnly, String bucket, Long customerId, Long branchId, String q) {
        return filtered(customerId, branchId, q, LocalDate.now()).stream()
                .filter(i -> !overdueOnly || i.daysOverdue() > 0)
                .filter(i -> bucket == null || bucket.isBlank() || bucket.equalsIgnoreCase(i.bucket()))
                .sorted(WORST_FIRST).toList();
    }

    private Instant lastReminder(Long customerId) {
        return reminderRepository.findByCustomerIdAndKindInAndCreatedAtAfterOrderByCreatedAtDesc(customerId, SENT_KINDS, Instant.EPOCH).stream().map(CollectionReminder::getCreatedAt).findFirst().orElse(null);
    }

    /** Reminders mailed about the debt that is currently overdue: those sent since its oldest due date. */
    private List<CollectionReminder> remindersFor(Long customerId, LocalDate oldestDue) {
        Instant since = oldestDue == null ? Instant.EPOCH : oldestDue.atStartOfDay(ZoneId.systemDefault()).toInstant();
        return reminderRepository.findByCustomerIdAndKindInAndCreatedAtAfterOrderByCreatedAtDesc(customerId, SENT_KINDS, since);
    }

    private List<CustomerRow> customerRows(List<ItemRow> items) {
        Map<Long, List<ItemRow>> by = new LinkedHashMap<>();
        for (ItemRow i : items) by.computeIfAbsent(i.customerId(), k -> new ArrayList<>()).add(i);
        List<CustomerRow> out = new ArrayList<>();
        for (Map.Entry<Long, List<ItemRow>> e : by.entrySet()) {
            List<ItemRow> list = e.getValue();
            ItemRow f = list.get(0);
            List<ItemRow> late = list.stream().filter(i -> i.daysOverdue() > 0).toList();
            LocalDate oldestDue = late.stream().map(ItemRow::dueDate).min(LocalDate::compareTo).orElse(null);
            List<CollectionReminder> sent = remindersFor(e.getKey(), oldestDue);
            Customer c = customerRepository.findById(e.getKey()).orElse(null);
            out.add(new CustomerRow(e.getKey(), f.customerName(), f.customerEmail(), f.customerPhone(), list.size(), late.size(), list.stream().map(ItemRow::outstanding).reduce(ZERO, BigDecimal::add),
                    late.stream().map(ItemRow::outstanding).reduce(ZERO, BigDecimal::add), late.stream().mapToLong(ItemRow::daysOverdue).max().orElse(0), aging(list), sent.size(), lastReminder(e.getKey()),
                    c != null && c.isCreditEnabled()));
        }
        return out;
    }

    private static final Comparator<CustomerRow> OWES_MOST_LATE = Comparator.comparingLong(CustomerRow::oldestDaysOverdue).reversed().thenComparing(CustomerRow::outstanding, Comparator.reverseOrder());

    @Transactional
    public CustomersPage customers(boolean canRemind, boolean overdueOnly, Long branchId, String q, Integer page, Integer size) {
        List<ItemRow> all = filtered(null, branchId, q, LocalDate.now());
        List<CustomerRow> rows = customerRows(all).stream().filter(r -> !overdueOnly || r.overdueItems() > 0).sorted(OWES_MOST_LATE).toList();
        return new CustomersPage(ListPaging.of(rows, page, size).getBody(), rows.size(), summary(all), canRemind, mailService.isConfigured());
    }

    @Transactional
    public List<CustomerRow> allCustomers(boolean overdueOnly, Long branchId, String q) {
        return customerRows(filtered(null, branchId, q, LocalDate.now())).stream().filter(r -> !overdueOnly || r.overdueItems() > 0).sorted(OWES_MOST_LATE).toList();
    }

    // ------------------------------------------------------------------ the statement

    @Transactional
    public StatementData statementData(Long customerId) {
        Customer c = customerRepository.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer not found."));
        LocalDate today = LocalDate.now();
        List<ItemRow> items = open(customerId, today).stream().sorted(Comparator.comparing(ItemRow::date).thenComparing(i -> i.number() == null ? "" : i.number())).toList();
        List<StatementLine> lines = items.stream().map(i -> new StatementLine(i.date(), i.number(), "POS_SALE".equals(i.kind()) ? "Sale on credit" : "Invoice", i.total(), i.paid(), i.outstanding(), i.dueDate(),
                i.daysOverdue())).toList();
        LocalDate since = today.minusDays(90);
        List<PaymentLine> payments = new ArrayList<>();
        for (CustomerPaymentDto p : creditService.paymentHistory(customerId)) {
            LocalDate d = local(p.paidAt(), today);
            if (!d.isBefore(since)) payments.add(new PaymentLine(d, p.receiptNumber(), p.method(), p.amount()));
        }
        payments.sort(Comparator.comparing(PaymentLine::date).reversed());
        BigDecimal owed = items.stream().map(ItemRow::outstanding).reduce(ZERO, BigDecimal::add);
        BigDecimal late = items.stream().filter(i -> i.daysOverdue() > 0).map(ItemRow::outstanding).reduce(ZERO, BigDecimal::add);
        return new StatementData(c.getId(), c.getName(), c.getEmail(), c.getPhone(), today, lines, payments, owed, late, aging(items));
    }

    private static String slug(String s) {
        return s == null ? "customer" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    @Transactional
    public Printed statement(Long customerId) {
        StatementData d = statementData(customerId);
        return new Printed(files.statement(d), "statement-" + slug(d.customerName()) + ".pdf");
    }

    private void requireMail() {
        if (!mailService.isConfigured()) throw ApiException.badRequest("E-mail is not set up yet. Set up SMTP in Settings > Email first.");
    }

    private void contact(StringBuilder body) {
        String contact = files.contactLine();
        if (!contact.isEmpty()) body.append("<p>Questions? Contact us: ").append(HtmlUtils.htmlEscape(contact)).append("</p>");
        body.append("<p>").append(HtmlUtils.htmlEscape(files.businessName())).append("</p>");
    }

    private void logSent(Long customerId, String kind, String email, int items, BigDecimal amount, String by) {
        CollectionReminder r = new CollectionReminder();
        r.setCustomerId(customerId);
        r.setKind(kind);
        r.setToEmail(email);
        r.setItems(items);
        r.setAmount(amount);
        r.setSentBy(by);
        reminderRepository.save(r);
    }

    @Transactional
    public MailedStatement emailStatement(Long customerId, String by) {
        requireMail();
        StatementData d = statementData(customerId);
        if (d.email() == null || d.email().isBlank()) throw ApiException.badRequest("This customer has no e-mail address on the customer record.");
        byte[] pdf = files.statement(d);
        StringBuilder body = new StringBuilder("<p>Dear ").append(HtmlUtils.htmlEscape(d.customerName())).append(",</p>");
        body.append("<p>Please find attached your statement of account with ").append(HtmlUtils.htmlEscape(files.businessName())).append(" up to ").append(CollectionFiles.day(d.asOf())).append(".</p>");
        if (d.totalOutstanding().signum() > 0) body.append("<p>The amount still to pay is <b>").append(CollectionFiles.amount(files.currency(), d.totalOutstanding())).append("</b>.</p>");
        else body.append("<p>Nothing is owed on your account. Thank you.</p>");
        contact(body);
        String to = d.email().trim();
        mailService.send(to, "Your statement of account - " + files.businessName(), body.toString(), List.of(new EmailAttachment("statement-" + slug(d.customerName()) + ".pdf", "application/pdf", pdf)));
        logSent(customerId, "STATEMENT", to, d.lines().size(), d.totalOutstanding(), by);
        auditLogService.record("CUSTOMER_STATEMENT_SENT", "E-mailed the statement of " + d.customerName() + " to " + to, "Customer", customerId);
        return new MailedStatement(to, "Statement sent to " + to + ".");
    }

    // ------------------------------------------------------------------ reminders

    private String reminderHtml(String name, List<ItemRow> list) {
        String cur = files.currency();
        BigDecimal total = list.stream().map(ItemRow::outstanding).reduce(ZERO, BigDecimal::add);
        StringBuilder b = new StringBuilder("<p>Dear ").append(HtmlUtils.htmlEscape(name)).append(",</p>");
        b.append("<p>Our records show that the following amounts are overdue. If you have already paid, please ignore this reminder and accept our thanks.</p>");
        b.append("<table cellpadding=\"4\" style=\"border-collapse:collapse;\" border=\"1\"><tr><th>Number</th><th>Due</th><th>Days overdue</th><th align=\"right\">Still to pay</th></tr>");
        for (ItemRow i : list) {
            b.append("<tr><td>").append(HtmlUtils.htmlEscape(String.valueOf(i.number()))).append("</td><td>").append(i.dueDate()).append("</td><td>").append(i.daysOverdue())
                    .append("</td><td align=\"right\">").append(CollectionFiles.amount(cur, i.outstanding())).append("</td></tr>");
        }
        b.append("<tr><td colspan=\"3\"><b>Total</b></td><td align=\"right\"><b>").append(CollectionFiles.amount(cur, total)).append("</b></td></tr></table>");
        b.append("<p>Your statement is attached. Please arrange payment as soon as you can, or contact us if there is a problem with any of these.</p>");
        contact(b);
        return b.toString();
    }

    private RemindResult sendTo(Map<Long, List<ItemRow>> groups, String kind, String by) {
        int mails = 0, count = 0, skipped = 0;
        List<ReminderItem> out = new ArrayList<>();
        for (Map.Entry<Long, List<ItemRow>> e : groups.entrySet()) {
            List<ItemRow> list = e.getValue();
            ItemRow f = list.get(0);
            String name = f.customerName();
            String email = f.customerEmail() != null && !f.customerEmail().isBlank() ? f.customerEmail().trim() : null;
            BigDecimal owed = list.stream().map(ItemRow::outstanding).reduce(ZERO, BigDecimal::add);
            if (email == null) {
                skipped++;
                out.add(new ReminderItem(e.getKey(), name, null, list.size(), owed, "This customer has no e-mail address."));
                continue;
            }
            try {
                byte[] pdf = files.statement(statementData(e.getKey()));
                mailService.send(email, "Overdue payment reminder - " + files.businessName(), reminderHtml(name, list),
                        List.of(new EmailAttachment("statement-" + slug(name) + ".pdf", "application/pdf", pdf)));
            } catch (Exception ex) {
                log.warn("Could not send the payment reminder to {}: {}", name, ex.getMessage());
                skipped++;
                out.add(new ReminderItem(e.getKey(), name, email, list.size(), owed, "The e-mail could not be sent."));
                continue;
            }
            logSent(e.getKey(), kind, email, list.size(), owed, by);
            auditLogService.record("CUSTOMER_REMINDER_SENT", "Payment reminder for " + list.size() + " item(s) (" + CollectionFiles.money(owed) + ") sent to " + name + " at " + email, "Customer", e.getKey());
            mails++;
            count += list.size();
            out.add(new ReminderItem(e.getKey(), name, email, list.size(), owed, null));
        }
        return new RemindResult(mails, count, skipped, out);
    }

    private static Map<Long, List<ItemRow>> byCustomer(List<ItemRow> list) {
        Map<Long, List<ItemRow>> out = new LinkedHashMap<>();
        for (ItemRow i : list) out.computeIfAbsent(i.customerId(), k -> new ArrayList<>()).add(i);
        return out;
    }

    /** Reminds the chosen customers about everything overdue now, whatever the automatic settings say. */
    @Transactional
    public RemindResult remind(List<Long> customerIds, String by) {
        requireMail();
        if (customerIds == null || customerIds.isEmpty()) throw ApiException.badRequest("Choose the customers to remind.");
        Set<Long> want = Set.copyOf(customerIds);
        List<ItemRow> chosen = open(null, LocalDate.now()).stream().filter(i -> want.contains(i.customerId()) && i.daysOverdue() > 0).sorted(WORST_FIRST).toList();
        if (chosen.isEmpty()) throw ApiException.badRequest("None of those customers has anything overdue.");
        return sendTo(byCustomer(chosen), "MANUAL", by);
    }

    private Map<Long, List<ItemRow>> eligible(LocalDate today) {
        CollectionSettings s = settingsEntity();
        Instant cutoff = Instant.now().minus(s.getRepeatDays(), ChronoUnit.DAYS);
        Map<Long, List<ItemRow>> out = new LinkedHashMap<>();
        for (Map.Entry<Long, List<ItemRow>> e : byCustomer(open(null, today).stream().filter(i -> i.daysOverdue() > 0).sorted(WORST_FIRST).toList()).entrySet()) {
            List<ItemRow> list = e.getValue();
            long oldest = list.stream().mapToLong(ItemRow::daysOverdue).max().orElse(0);
            if (oldest < s.getFirstDays()) continue;
            LocalDate oldestDue = list.stream().map(ItemRow::dueDate).min(LocalDate::compareTo).orElse(null);
            List<CollectionReminder> sent = remindersFor(e.getKey(), oldestDue);
            if (sent.size() >= s.getMaxReminders()) continue;
            if (!sent.isEmpty() && sent.get(0).getCreatedAt().isAfter(cutoff)) continue;
            out.put(e.getKey(), list);
        }
        return out;
    }

    /** Who the automatic run would remind right now. */
    @Transactional
    public List<ReminderItem> preview() {
        List<ReminderItem> out = new ArrayList<>();
        for (Map.Entry<Long, List<ItemRow>> e : eligible(LocalDate.now()).entrySet()) {
            ItemRow f = e.getValue().get(0);
            String email = f.customerEmail() != null && !f.customerEmail().isBlank() ? f.customerEmail().trim() : null;
            out.add(new ReminderItem(e.getKey(), f.customerName(), email, e.getValue().size(), e.getValue().stream().map(ItemRow::outstanding).reduce(ZERO, BigDecimal::add),
                    email == null ? "This customer has no e-mail address." : null));
        }
        return out;
    }

    /** The "send now" button: the automatic run, started by hand. */
    @Transactional
    public RemindResult runNow(String by) {
        requireMail();
        return sendTo(eligible(LocalDate.now()), "AUTO", by);
    }

    @Scheduled(cron = "0 15 7 * * *")
    public void daily() {
        try {
            if (!settingsEntity().isAutoEnabled() || !mailService.isConfigured()) return;
            RemindResult r = runDaily();
            if (r.mails() > 0) log.info("Payment reminders: {} mails for {} items", r.mails(), r.items());
        } catch (Exception e) {
            log.warn("Payment reminders skipped: {}", e.getMessage());
        }
    }

    @Transactional
    public RemindResult runDaily() {
        return sendTo(eligible(LocalDate.now()), "AUTO", "automatic run");
    }

    // ------------------------------------------------------------------ the log

    @Transactional(readOnly = true)
    public List<LogRow> history(Long customerId) {
        List<CollectionReminder> rows = customerId != null ? reminderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId) : reminderRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, String> names = new java.util.HashMap<>();
        List<LogRow> out = new ArrayList<>();
        for (CollectionReminder r : rows) {
            String name = names.computeIfAbsent(r.getCustomerId(), id -> customerRepository.findById(id).map(Customer::getName).orElse(""));
            out.add(new LogRow(r.getId(), r.getCustomerId(), name, r.getKind(), r.getToEmail(), r.getItems(), r.getAmount(), r.getSentBy(), r.getCreatedAt()));
        }
        return out;
    }
}
