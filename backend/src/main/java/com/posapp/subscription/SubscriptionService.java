package com.posapp.subscription;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.DocumentPdfService;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.dto.InvoiceDto;
import com.posapp.settings.StoreSettingsService;
import com.posapp.subscription.dto.CreateSubscriptionRequest;
import com.posapp.subscription.dto.SubscriptionDto;
import com.posapp.tax.TaxRate;
import com.posapp.tax.TaxRateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Fourier: "Subscriptions Module" — scoped via AskUserQuestion to customer subscriptions (not the
 * business's own recurring costs, which {@code com.posapp.recurringexpense.RecurringExpenseTemplate}
 * already covers), billed against a fixed, admin-defined {@link SubscriptionPlan} rather than the
 * product catalog, auto-generating a real {@code Invoice} on each billing date.
 *
 * <p>The daily scheduled sweep lives in {@link SubscriptionAutomationService} (it calls {@link #billDue}) — same shape as {@code
 * RecurringExpenseTemplateService.generateDueTemplates}: every still-ACTIVE subscription whose
 * {@code nextBillingDate} has arrived (or passed — e.g. the app was offline that day) gets billed,
 * each isolated in its own try/catch so one failure never stops the rest. {@link #billNow}
 * exposes the same single-subscription logic for an admin who doesn't want to wait for the next
 * sweep. Billing only ever advances one cycle per run — a subscription left unbilled for several
 * days advances one interval each time it's processed, so it naturally catches up over the
 * following days' runs rather than generating a burst of back-dated invoices in one go.
 *
 * <p>Each bill is a brand-new, already-ISSUED standalone {@code Invoice} (see {@code
 * InvoiceService#generateForSubscription}) — same "auto-generate an invoice, admin/customer still
 * records payment separately" choice made when scoping this module, not an auto-charge: this app
 * has no stored card/payment-gateway integration to actually auto-charge anything.
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository repository;
    private final SubscriptionPlanRepository planRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceService invoiceService;
    private final AuditLogService auditLogService;
    /** Fourier: "Print also subscription" — see {@link #pdf}. */
    private final DocumentPdfService documentPdfService;
    private final StoreSettingsService storeSettingsService;
    private final TaxRateService taxRateService;

    public SubscriptionService(SubscriptionRepository repository,
                                SubscriptionPlanRepository planRepository,
                                CustomerRepository customerRepository,
                                InvoiceService invoiceService,
                                AuditLogService auditLogService,
                                DocumentPdfService documentPdfService,
                                StoreSettingsService storeSettingsService,
                                TaxRateService taxRateService) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.customerRepository = customerRepository;
        this.invoiceService = invoiceService;
        this.auditLogService = auditLogService;
        this.documentPdfService = documentPdfService;
        this.storeSettingsService = storeSettingsService;
        this.taxRateService = taxRateService;
    }

    /** Fourier: "on the customer ... view button" convention every other module's own listAll follows — optional `customerId` narrows this to one customer's own subscriptions. */
    @Transactional(readOnly = true)
    public List<SubscriptionDto> listAll(Long customerId) {
        List<Subscription> rows = customerId != null
                ? repository.findByCustomerIdOrderByIdDesc(customerId)
                : repository.findAllByOrderByIdDesc();
        return rows.stream().map(SubscriptionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionDto getById(Long id) {
        return SubscriptionDto.from(getEntityOrThrow(id));
    }

    @Transactional
    public SubscriptionDto create(CreateSubscriptionRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + request.getCustomerId()));
        SubscriptionPlan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> ApiException.notFound("Subscription plan not found: " + request.getPlanId()));
        if (!plan.isActive()) {
            throw ApiException.badRequest("'" + plan.getName() + "' is no longer offered for new subscriptions");
        }
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();

        Subscription subscription = new Subscription();
        subscription.setCustomerId(customer.getId());
        subscription.setCustomerName(customer.getName());
        subscription.setPlan(plan);
        // Fourier: "What if the One Subscription can be assign to the customer with different
        // price and different Billing interval" — the caller supplies these explicitly (the "New
        // subscription" form pre-fills both from the plan but leaves them editable), rather than
        // this service always copying plan.getPrice()/getBillingInterval() verbatim; see
        // CreateSubscriptionRequest.price's own javadoc. Still a snapshot either way — editing the
        // plan later never reaches back into this subscription's own committed terms.
        subscription.setPrice(request.getPrice());
        subscription.setBillingInterval(request.getBillingInterval());
        // Fourier: "subscription plan should also have tax and discount on it" — same
        // pre-filled-from-the-plan-but-editable-per-customer treatment as price/billingInterval
        // above: the caller supplies the (possibly overridden) tax rate and discount explicitly,
        // and we snapshot the rate's own code/percent/inclusive onto this subscription now, same
        // code-not-foreign-key convention as InvoiceLine.taxCode/taxRatePercent/taxInclusive.
        if (request.getTaxRateId() != null) {
            TaxRate rate = taxRateService.getEntityOrThrow(request.getTaxRateId());
            subscription.setTaxCode(rate.getCode());
            subscription.setTaxRatePercent(rate.getRatePercent());
            subscription.setTaxInclusive(rate.isInclusive());
        } else {
            subscription.setTaxCode(null);
            subscription.setTaxRatePercent(null);
            subscription.setTaxInclusive(null);
        }
        subscription.setDiscountPercent(request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO);
        // Fourier: "may be some subscription will be paid after service other will be prepaid" —
        // chosen fresh for this one subscription (no plan-level default); null is treated as
        // prepaid, same behavior every subscription had before this field existed. This is the
        // only place billInAdvance is ever read — it only decides where the very first
        // nextBillingDate lands; every later cycle just advances by billingInterval regardless.
        boolean billInAdvance = !Boolean.FALSE.equals(request.getBillInAdvance());
        subscription.setBillInAdvance(billInAdvance);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(startDate);
        subscription.setNextBillingDate(billInAdvance ? startDate : subscription.getBillingInterval().addTo(startDate));
        subscription.setNotes(request.getNotes());
        Subscription saved = repository.save(subscription);

        auditLogService.record("SUBSCRIPTION_CREATED",
                "Subscribed " + customer.getName() + " to '" + plan.getName() + "' (" + saved.getPrice() + " / " + saved.getBillingInterval()
                        + "), first billing " + saved.getNextBillingDate(),
                "Subscription", saved.getId());
        return SubscriptionDto.from(saved);
    }

    /** Reversible — see SubscriptionStatus's own javadoc. Only from ACTIVE. */
    @Transactional
    public SubscriptionDto pause(Long id) {
        Subscription subscription = getEntityOrThrow(id);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw ApiException.badRequest("Subscription is " + subscription.getStatus() + ", not ACTIVE — can't pause it");
        }
        subscription.setStatus(SubscriptionStatus.PAUSED);
        Subscription saved = repository.save(subscription);
        auditLogService.record("SUBSCRIPTION_PAUSED", "Paused subscription for " + saved.getCustomerName(), "Subscription", saved.getId());
        return SubscriptionDto.from(saved);
    }

    /**
     * Only from PAUSED. If the subscription sat paused long enough that {@code nextBillingDate}
     * is now in the past, it's bumped to today instead — resuming shouldn't instantly bill for
     * the entire paused period.
     */
    @Transactional
    public SubscriptionDto resume(Long id) {
        Subscription subscription = getEntityOrThrow(id);
        if (subscription.getStatus() != SubscriptionStatus.PAUSED) {
            throw ApiException.badRequest("Subscription is " + subscription.getStatus() + ", not PAUSED — can't resume it");
        }
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        if (subscription.getNextBillingDate().isBefore(LocalDate.now())) {
            subscription.setNextBillingDate(LocalDate.now());
        }
        Subscription saved = repository.save(subscription);
        auditLogService.record("SUBSCRIPTION_RESUMED", "Resumed subscription for " + saved.getCustomerName()
                + " — next billing " + saved.getNextBillingDate(), "Subscription", saved.getId());
        return SubscriptionDto.from(saved);
    }

    /** Terminal — see SubscriptionStatus's own javadoc. From ACTIVE or PAUSED; refused if already CANCELLED. */
    @Transactional
    public SubscriptionDto cancel(Long id, String reason) {
        Subscription subscription = getEntityOrThrow(id);
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw ApiException.badRequest("Subscription is already cancelled");
        }
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now());
        subscription.setCancelReason(reason);
        Subscription saved = repository.save(subscription);
        auditLogService.record("SUBSCRIPTION_CANCELLED", "Cancelled subscription for " + saved.getCustomerName(), "Subscription", saved.getId());
        return SubscriptionDto.from(saved);
    }

    /** This subscription's own billing history — every invoice it has ever generated, newest first. */
    @Transactional(readOnly = true)
    public List<InvoiceDto> billingHistory(Long id) {
        getEntityOrThrow(id);
        return invoiceService.listBySubscriptionId(id);
    }

    /**
     * Fourier: "Print also subscription" — an A4 PDF of this subscription's own current terms
     * plus its billing history, to open in a new tab and print, same shape as InvoiceService.pdf.
     */
    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        SubscriptionDto dto = getById(id);
        List<InvoiceDto> history = invoiceService.listBySubscriptionId(id);
        return documentPdfService.subscriptionPdf(dto, history, storeSettingsService.getSettingsEntity());
    }

    /** Bills this one subscription right now, regardless of whether nextBillingDate has actually arrived yet — same effect as the scheduled sweep picking it up, just on demand. */
    @Transactional
    public SubscriptionDto billNow(Long id) {
        Subscription subscription = getEntityOrThrow(id);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw ApiException.badRequest("Subscription is " + subscription.getStatus() + ", not ACTIVE — can't bill it");
        }
        billOne(subscription);
        return SubscriptionDto.from(subscription);
    }

    /** Ids of every ACTIVE subscription whose next billing date has arrived (or passed) — the scheduled sweep's input. */
    @Transactional(readOnly = true)
    public List<Long> dueSubscriptionIds(LocalDate today) {
        return repository.findByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus.ACTIVE, today)
                .stream().map(Subscription::getId).toList();
    }

    /** What billing one subscription produced. */
    public record BillResult(Long subscriptionId, String customerName, BigDecimal price, String invoiceNumber, LocalDate nextBillingDate) {
    }

    /**
     * Bills one due subscription in its own transaction (the caller is not transactional), so one failure
     * never rolls back or poisons the others. Re-checks that it is still ACTIVE and due.
     */
    @Transactional
    public BillResult billDue(Long id, LocalDate today) {
        Subscription subscription = getEntityOrThrow(id);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE || subscription.getNextBillingDate().isAfter(today)) {
            throw ApiException.badRequest("Subscription is no longer due");
        }
        String invoiceNumber = billOne(subscription);
        return new BillResult(subscription.getId(), subscription.getCustomerName(), subscription.getPrice(), invoiceNumber, subscription.getNextBillingDate());
    }

    /** An upcoming bill that should be announced. */
    public record ReminderInfo(Long subscriptionId, String customerName, String customerEmail, String planName,
                               BigDecimal price, LocalDate nextBillingDate) {
    }

    /** ACTIVE subscriptions billing within the next {@code days} days (but not today) that have not been reminded for that billing date yet. */
    @Transactional(readOnly = true)
    public List<ReminderInfo> reminderCandidates(LocalDate today, int days) {
        List<ReminderInfo> result = new ArrayList<>();
        for (Subscription s : repository.findByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus.ACTIVE, today.plusDays(days))) {
            LocalDate next = s.getNextBillingDate();
            if (!next.isAfter(today) || next.equals(s.getReminderSentFor())) continue;
            String email = s.getCustomerId() == null ? null
                    : customerRepository.findById(s.getCustomerId()).map(Customer::getEmail).orElse(null);
            result.add(new ReminderInfo(s.getId(), s.getCustomerName(), email,
                    s.getPlan() != null ? s.getPlan().getName() : "Subscription", s.getPrice(), next));
        }
        return result;
    }

    @Transactional
    public void markReminderSent(Long id, LocalDate billingDate) {
        Subscription s = getEntityOrThrow(id);
        s.setReminderSentFor(billingDate);
        repository.save(s);
    }

    /** An ACTIVE subscription holding an issued invoice that has stayed unpaid too long. */
    public record OverdueInfo(Long subscriptionId, String customerName, String invoiceNumber, LocalDate invoiceDate, BigDecimal outstanding) {
    }

    /** ACTIVE subscriptions with an ISSUED invoice still unpaid more than {@code days} days after its due date (or invoice date when it has none). */
    @Transactional(readOnly = true)
    public List<OverdueInfo> overdueCandidates(LocalDate today, int days, ZoneId zone) {
        List<OverdueInfo> result = new ArrayList<>();
        for (Subscription s : repository.findAllByOrderByIdDesc()) {
            if (s.getStatus() != SubscriptionStatus.ACTIVE) continue;
            for (InvoiceDto inv : invoiceService.listBySubscriptionId(s.getId())) {
                if (!"ISSUED".equals(inv.status()) || inv.amountOutstanding() == null || inv.amountOutstanding().signum() <= 0) continue;
                LocalDate base = inv.dueDate() != null ? inv.dueDate()
                        : (inv.invoiceDate() != null ? inv.invoiceDate().atZone(zone).toLocalDate() : null);
                if (base != null && base.plusDays(days).isBefore(today)) {
                    result.add(new OverdueInfo(s.getId(), s.getCustomerName(), inv.invoiceNumber(), base, inv.amountOutstanding()));
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Fourier: "when I click to Bill now, it will create an invoice from the last end date. Now
     * it is still creating same invoice with same start and end date" — the bug was that no
     * billing-period concept existed at all, so every generated invoice only ever carried
     * {@code Instant.now()} (today, real clock time) with nothing distinguishing one cycle from
     * the next. Fixed by computing this bill's own period right here, before this subscription's
     * {@code nextBillingDate} ({@code dueDate} below) is touched.
     *
     * <p>Fourier: "may be some subscription will be paid after service other will be prepaid" —
     * {@code dueDate} (the subscription's {@code nextBillingDate} going in) always sits at one
     * end of the cycle being billed, but which end depends on {@link Subscription#getBillInAdvance()}:
     * for a prepaid subscription, {@code dueDate} is the *start* of the cycle this bill pays for
     * upfront, so {@code periodEnd} is {@code dueDate} advanced one interval forward; for a "paid
     * after service" subscription, {@code dueDate} is the *end* of the cycle that's just been
     * rendered (service already given, payment now due), so {@code periodStart} is {@code
     * dueDate} moved one interval *backward* instead ({@link BillingInterval#subtractFrom}).
     * Either way, the next bill's own due date is simply {@code dueDate} advanced one interval —
     * cycles are evenly spaced regardless of which end of each one its due date falls on, so this
     * line needs no branching at all; only the *period label* on the generated invoice does.
     */
    private String billOne(Subscription subscription) {
        String planName = subscription.getPlan() != null ? subscription.getPlan().getName() : "Subscription";
        LocalDate dueDate = subscription.getNextBillingDate();
        boolean advance = !Boolean.FALSE.equals(subscription.getBillInAdvance());
        LocalDate periodStart = advance ? dueDate : subscription.getBillingInterval().subtractFrom(dueDate);
        LocalDate periodEnd = advance ? subscription.getBillingInterval().addTo(dueDate) : dueDate;

        InvoiceDto invoice = invoiceService.generateForSubscription(
                subscription.getCustomerId(), subscription.getCustomerName(), subscription.getId(),
                planName, subscription.getPrice(),
                subscription.getTaxCode(), subscription.getTaxRatePercent(), subscription.getTaxInclusive(),
                subscription.getDiscountPercent(),
                periodStart, periodEnd);

        subscription.setLastBilledAt(Instant.now());
        subscription.setNextBillingDate(subscription.getBillingInterval().addTo(dueDate));
        repository.save(subscription);

        // Fourier: "instead of making invoice DRAFT make then ISSUED and hit accounting" —
        // invoice.invoiceNumber() below is already ISSUED and posted to Accounting by the time
        // this audit entry is written; see InvoiceService.generateForSubscription's own javadoc.
        auditLogService.record("SUBSCRIPTION_BILLED",
                "Billed " + subscription.getCustomerName() + " " + subscription.getPrice() + " for '" + planName
                        + "' (period " + periodStart + " to " + periodEnd + ") — issued invoice " + invoice.invoiceNumber()
                        + " (next billing " + subscription.getNextBillingDate() + ")",
                "Subscription", subscription.getId());
        return invoice.invoiceNumber();
    }

    private Subscription getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subscription not found: " + id));
    }
}
