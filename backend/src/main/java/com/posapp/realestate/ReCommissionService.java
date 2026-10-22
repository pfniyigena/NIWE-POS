package com.posapp.realestate;

import com.posapp.accounting.ExpenseService;
import com.posapp.accounting.ExpenseStatus;
import com.posapp.accounting.dto.ExpenseDto;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.RePrintService.Printed;
import com.posapp.realestate.dto.RealEstateCommissionDtos.MailedPayee;
import com.posapp.realestate.dto.RealEstateCommissionDtos.PayeeOption;
import com.posapp.realestate.dto.RealEstateCommissionDtos.PayeeStatement;
import com.posapp.realestate.dto.RealEstateCommissionDtos.PayoutBoard;
import com.posapp.realestate.dto.RealEstateCommissionDtos.PostDueResult;
import com.posapp.realestate.dto.RealEstateCommissionDtos.ShareLine;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Commission payouts. Each commission share on a deal is followed from "sale not complete" through "earned", "sent to Expenses", "approved" to "paid", using the expense
 * the share was posted as. From that: a board with totals per person, a statement (PDF, or by e-mail) for each person, and a button that posts every earned share at once.
 */
@Service
public class ReCommissionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final RealEstateAccess access;
    private final ReDealRepository dealRepository;
    private final ReDealCommissionRepository commissionRepository;
    private final RePropertyRepository propertyRepository;
    private final ExpenseService expenseService;
    private final ReDealService dealService;
    private final ReSettingsService settingsService;
    private final ModuleService moduleService;
    private final MailService mailService;
    private final UserRepository userRepository;
    private final RePrintFiles files;
    private final AuditLogService auditLogService;

    public ReCommissionService(RealEstateAccess access, ReDealRepository dealRepository, ReDealCommissionRepository commissionRepository, RePropertyRepository propertyRepository,
                               ExpenseService expenseService, ReDealService dealService, ReSettingsService settingsService, ModuleService moduleService, MailService mailService,
                               UserRepository userRepository, RePrintFiles files, AuditLogService auditLogService) {
        this.access = access;
        this.dealRepository = dealRepository;
        this.commissionRepository = commissionRepository;
        this.propertyRepository = propertyRepository;
        this.expenseService = expenseService;
        this.dealService = dealService;
        this.settingsService = settingsService;
        this.moduleService = moduleService;
        this.mailService = mailService;
        this.userRepository = userRepository;
        this.files = files;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ the board

    private static String keyOf(ReDealCommission c) {
        if (c.getPayeeKind() == PayeeKind.STAFF && c.getPayeeUserId() != null) return "u" + c.getPayeeUserId();
        return "x:" + (c.getPayeeName() == null ? "" : c.getPayeeName().trim().toLowerCase(Locale.ROOT));
    }

    private static BigDecimal add(BigDecimal a, BigDecimal b) {
        return (a == null ? ZERO : a).add(b == null ? ZERO : b);
    }

    private record Work(ReDealCommission c, ReDeal d, ReProperty p) {
    }

    /** The state of a share, and the date it was paid when it was. */
    private record Outcome(PayoutState state, LocalDate paidOn, Long expenseId) {
    }

    private Outcome outcome(ReDealCommission c, ReDeal d, Map<Long, ExpenseDto> expenses) {
        boolean completed = d.getStage() == DealStage.COMPLETED;
        if (c.getStatus() == CommissionStatus.POSTED && c.getExpenseId() != null) {
            ExpenseDto e = expenses.get(c.getExpenseId());
            if (e == null || e.status() == ExpenseStatus.REJECTED || e.status() == ExpenseStatus.VOIDED) return new Outcome(PayoutState.REJECTED, null, c.getExpenseId());
            if (e.status() == ExpenseStatus.APPROVED && (e.paid() || e.settledAt() != null)) {
                LocalDate on = e.settledAt() != null ? e.settledAt().atZone(ZoneId.systemDefault()).toLocalDate() : e.expenseDate();
                return new Outcome(PayoutState.PAID, on, c.getExpenseId());
            }
            if (e.status() == ExpenseStatus.APPROVED) return new Outcome(PayoutState.TO_PAY, null, c.getExpenseId());
            return new Outcome(PayoutState.AWAITING_APPROVAL, null, c.getExpenseId());
        }
        return new Outcome(completed ? PayoutState.TO_POST : PayoutState.PLANNED, null, null);
    }

    private Map<Long, ExpenseDto> expensesOf(List<ReDealCommission> shares) {
        Map<Long, ExpenseDto> out = new LinkedHashMap<>();
        for (ReDealCommission c : shares) {
            if (c.getExpenseId() == null || out.containsKey(c.getExpenseId())) continue;
            try {
                out.put(c.getExpenseId(), expenseService.getById(c.getExpenseId()));
            } catch (ApiException ex) {
                if (ex.getStatus() != HttpStatus.NOT_FOUND) throw ex;
            }
        }
        return out;
    }

    /** Every earned or planned share of the sales completed in the period (with no period: every deal that is not cancelled), grouped by the person it is for. */
    @Transactional(readOnly = true)
    public PayoutBoard board(LocalDate from, LocalDate to, String payee, boolean openOnly) {
        access.on();
        if (from != null && to != null && to.isBefore(from)) throw ApiException.badRequest("The end of the period is before its start.");
        Map<Long, ReProperty> props = propertyRepository.findAll().stream().collect(Collectors.toMap(ReProperty::getId, Function.identity()));
        Map<Long, ReDeal> deals = dealRepository.findAll().stream().filter(d -> d.getStage() != DealStage.CANCELLED).filter(d -> {
            if (from == null && to == null) return true;
            if (d.getStage() != DealStage.COMPLETED || d.getCompletedDate() == null) return false;
            return (from == null || !d.getCompletedDate().isBefore(from)) && (to == null || !d.getCompletedDate().isAfter(to));
        }).collect(Collectors.toMap(ReDeal::getId, Function.identity()));
        List<ReDealCommission> shares = deals.isEmpty() ? List.of() : commissionRepository.findByDealIdIn(deals.keySet()).stream().filter(c -> c.getStatus() != CommissionStatus.CANCELLED).toList();
        Map<Long, ExpenseDto> expenses = expensesOf(shares);

        Map<String, List<Work>> byKey = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (ReDealCommission c : shares) {
            String key = keyOf(c);
            names.putIfAbsent(key, c.getPayeeName());
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(new Work(c, deals.get(c.getDealId()), props.get(deals.get(c.getDealId()).getPropertyId())));
        }
        List<PayeeOption> options = names.entrySet().stream().map(e -> new PayeeOption(e.getKey(), e.getValue())).sorted(Comparator.comparing(o -> o.name().toLowerCase(Locale.ROOT))).toList();

        List<PayeeStatement> payees = new ArrayList<>();
        BigDecimal earned = ZERO, paid = ZERO, toPost = ZERO, planned = ZERO;
        long toPostCount = 0;
        for (Map.Entry<String, List<Work>> e : byKey.entrySet()) {
            if (payee != null && !payee.isBlank() && !payee.equals(e.getKey())) continue;
            PayeeStatement st = statementOf(e.getKey(), e.getValue(), expenses);
            if (openOnly && st.owed().signum() <= 0) continue;
            payees.add(st);
            earned = earned.add(st.earned());
            paid = paid.add(st.paid());
            toPost = toPost.add(st.toPost());
            planned = planned.add(st.planned());
            toPostCount += st.lines().stream().filter(l -> l.state() == PayoutState.TO_POST).count();
        }
        payees.sort(Comparator.comparing(PayeeStatement::owed).reversed().thenComparing(p -> p.payeeName().toLowerCase(Locale.ROOT)));
        ReSettings rs = settingsService.entity();
        return new PayoutBoard(from, to, moduleService.isEnabled(ModuleKey.EXPENSES), rs.getCommissionAccountId() != null, mailService.isConfigured(), earned, paid, earned.subtract(paid), toPost,
                planned, toPostCount, options, payees);
    }

    private PayeeStatement statementOf(String key, List<Work> works, Map<Long, ExpenseDto> expenses) {
        BigDecimal toPost = ZERO, awaiting = ZERO, toPay = ZERO, paid = ZERO, planned = ZERO, rejected = ZERO;
        List<ShareLine> lines = new ArrayList<>();
        ReDealCommission first = works.get(0).c();
        for (Work w : works) {
            BigDecimal amount = ReDealService.shareAmount(w.c(), ReDealService.commissionTotal(w.d()));
            Outcome o = outcome(w.c(), w.d(), expenses);
            switch (o.state()) {
                case PLANNED -> planned = planned.add(amount);
                case TO_POST -> toPost = toPost.add(amount);
                case AWAITING_APPROVAL -> awaiting = awaiting.add(amount);
                case TO_PAY -> toPay = toPay.add(amount);
                case PAID -> paid = paid.add(amount);
                case REJECTED -> rejected = rejected.add(amount);
            }
            BigDecimal price = w.d().getAgreedPrice() != null ? w.d().getAgreedPrice() : w.d().getOfferAmount();
            lines.add(new ShareLine(w.d().getId(), w.d().getCode(), w.p() == null ? null : w.p().getCode(), w.p() == null ? null : w.p().getTitle(), w.d().getCompletedDate(), price,
                    w.c().getId(), w.c().getRole(), amount, o.state(), o.expenseId(), o.paidOn()));
        }
        lines.sort(Comparator.comparing(ShareLine::completedDate, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(ShareLine::dealCode));
        BigDecimal earned = toPost.add(awaiting).add(toPay).add(paid);
        boolean email = first.getPayeeKind() == PayeeKind.STAFF && first.getPayeeUserId() != null
                && userRepository.findById(first.getPayeeUserId()).map(u -> u.getEmail() != null && !u.getEmail().isBlank()).orElse(false);
        return new PayeeStatement(key, first.getPayeeName(), first.getPayeeKind(), first.getPayeeUserId(), email, earned, paid, earned.subtract(paid), toPost, awaiting, toPay, planned, rejected, lines);
    }

    // ------------------------------------------------------------------ post everything that is earned

    /** Sends every earned share (of the people and period chosen) to Expenses, as the commission account of the Real Estate settings. One that cannot be posted does not stop the rest. */
    public PostDueResult postDue(Actor a, LocalDate from, LocalDate to, String payee) {
        access.on();
        if (!moduleService.isEnabled(ModuleKey.EXPENSES)) throw ApiException.badRequest("Switch on the Expenses module to post commission to the accounts.");
        if (settingsService.entity().getCommissionAccountId() == null) throw ApiException.badRequest("Choose the expense account for commission in the Real Estate settings first.");
        PayoutBoard b = board(from, to, payee, false);
        int posted = 0, failed = 0;
        BigDecimal amount = ZERO;
        List<String> problems = new ArrayList<>();
        for (PayeeStatement st : b.payees()) {
            for (ShareLine l : st.lines()) {
                if (l.state() != PayoutState.TO_POST) continue;
                try {
                    dealService.postCommission(a, l.dealId(), l.commissionId(), null);
                    posted++;
                    amount = amount.add(l.amount());
                } catch (ApiException ex) {
                    failed++;
                    problems.add(l.dealCode() + " (" + st.payeeName() + "): " + ex.getMessage());
                }
            }
        }
        String msg = posted == 0 && failed == 0 ? "Nothing was waiting to be posted." : "Posted " + posted + " share" + (posted == 1 ? "" : "s") + " to Expenses" + (failed > 0 ? "; " + failed + " could not be posted." : ".");
        if (posted > 0) auditLogService.record("RE_COMMISSION_POST_DUE", "Posted " + posted + " commission shares (" + amount + ") to Expenses", "ReDeal", null);
        return new PostDueResult(posted, failed, amount, problems, msg);
    }

    // ------------------------------------------------------------------ the statement

    private PayeeStatement one(LocalDate from, LocalDate to, String payee) {
        if (payee == null || payee.isBlank()) throw ApiException.badRequest("Choose whose statement to make.");
        PayoutBoard b = board(from, to, payee, false);
        if (b.payees().isEmpty()) throw ApiException.notFound("There is no commission for that person" + (from != null || to != null ? " in this period." : "."));
        return b.payees().get(0);
    }

    private static String periodText(LocalDate from, LocalDate to) {
        if (from == null && to == null) return "All sales";
        return "Sales completed " + (from != null ? "from " + RePrintFiles.DAY.format(from) + " " : "") + (to != null ? "to " + RePrintFiles.DAY.format(to) : "");
    }

    @Transactional(readOnly = true)
    public Printed statement(LocalDate from, LocalDate to, String payee) {
        access.on();
        PayeeStatement st = one(from, to, payee);
        byte[] pdf = files.commissionStatement(st, periodText(from, to).trim(), files.currency());
        String safe = st.payeeName().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        return new Printed(pdf, "commission-statement-" + (safe.isEmpty() ? "payee" : safe) + ".pdf");
    }

    /** E-mails the statement to a staff member (the address on their user record). */
    public MailedPayee emailStatement(Actor a, LocalDate from, LocalDate to, String payee) {
        access.on();
        if (!mailService.isConfigured()) throw ApiException.badRequest("E-mail is not set up yet. Set up SMTP in Settings > Email first.");
        PayeeStatement st = one(from, to, payee);
        if (st.payeeKind() != PayeeKind.STAFF || st.payeeUserId() == null) throw ApiException.badRequest(st.payeeName() + " is not on your staff list, so there is no e-mail address. Print the statement instead.");
        AppUser u = userRepository.findById(st.payeeUserId()).orElseThrow(() -> ApiException.notFound("That user no longer exists."));
        if (u.getEmail() == null || u.getEmail().isBlank()) throw ApiException.badRequest(st.payeeName() + " has no e-mail address on the user record. Add one under Users first.");
        Printed pdf = statement(from, to, payee);
        String business = files.businessName();
        ReSettings rs = settingsService.entity();
        StringBuilder body = new StringBuilder();
        body.append("<p>Dear ").append(HtmlUtils.htmlEscape(st.payeeName())).append(",</p>");
        body.append("<p>Please find attached your commission statement (").append(HtmlUtils.htmlEscape(periodText(from, to).trim())).append("). ");
        body.append("Earned: <b>").append(plain(st.earned())).append("</b>, paid: <b>").append(plain(st.paid())).append("</b>, still to be paid: <b>").append(plain(st.owed())).append("</b>.</p>");
        StringBuilder contact = new StringBuilder();
        for (String part : new String[]{rs.getWebsitePhone(), rs.getWebsiteEmail()}) if (part != null && !part.isBlank()) contact.append(contact.length() == 0 ? "" : "  |  ").append(part.trim());
        if (contact.length() > 0) body.append("<p>Questions? Contact us: ").append(HtmlUtils.htmlEscape(contact.toString())).append("</p>");
        body.append("<p>").append(HtmlUtils.htmlEscape(business)).append("</p>");
        mailService.send(u.getEmail(), "Your commission statement - " + business, body.toString(), List.of(new EmailAttachment(pdf.fileName(), "application/pdf", pdf.content())));
        auditLogService.record("RE_COMMISSION_STATEMENT", "E-mailed a commission statement to " + st.payeeName() + " (" + u.getEmail() + ")", "AppUser", u.getId());
        return new MailedPayee(u.getEmail(), "Statement sent to " + u.getEmail() + ".");
    }

    private static String plain(BigDecimal v) {
        return new java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v);
    }
}
