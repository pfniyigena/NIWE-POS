package com.posapp.subscription;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.DocumentPdfService;
import com.posapp.invoice.InvoiceService;
import com.posapp.invoice.SubscriptionInvoiceSpec;
import com.posapp.common.TaxMath;
import com.posapp.security.AppUserPrincipal;
import com.posapp.subscription.dto.SubscriptionEventDto;
import com.posapp.subscription.dto.SubscriptionRequests;
import com.posapp.subscription.dto.SubscriptionViews;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
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
import java.util.Comparator;
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
    private final SubscriptionEventRepository eventRepository;
    private final SubscriptionSettingsRepository settingsRepository;

    public SubscriptionService(SubscriptionRepository repository,
                                SubscriptionPlanRepository planRepository,
                                CustomerRepository customerRepository,
                                InvoiceService invoiceService,
                                AuditLogService auditLogService,
                                DocumentPdfService documentPdfService,
                                StoreSettingsService storeSettingsService,
                                TaxRateService taxRateService,
                                SubscriptionEventRepository eventRepository,
                                SubscriptionSettingsRepository settingsRepository) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.customerRepository = customerRepository;
        this.invoiceService = invoiceService;
        this.auditLogService = auditLogService;
        this.documentPdfService = documentPdfService;
        this.storeSettingsService = storeSettingsService;
        this.taxRateService = taxRateService;
        this.eventRepository = eventRepository;
        this.settingsRepository = settingsRepository;
    }

    /** Optional filters: customer, status, plan, free-text search (customer or plan name), and a "needs attention" view: trial, due-soon, cancel-scheduled, term-ending, paused-resuming. */
    @Transactional(readOnly = true)
    public List<SubscriptionDto> listAll(Long customerId, SubscriptionStatus status, Long planId, String search, String view) {
        List<Subscription> rows = customerId != null
                ? repository.findByCustomerIdOrderByIdDesc(customerId)
                : repository.findAllByOrderByIdDesc();
        String q = search == null ? "" : search.trim().toLowerCase();
        LocalDate today = LocalDate.now();
        return rows.stream()
                .filter(s -> status == null || s.getStatus() == status)
                .filter(s -> planId == null || (s.getPlan() != null && planId.equals(s.getPlan().getId())))
                .filter(s -> q.isEmpty() || (s.getCustomerName() != null && s.getCustomerName().toLowerCase().contains(q))
                        || (s.getPlan() != null && s.getPlan().getName() != null && s.getPlan().getName().toLowerCase().contains(q)))
                .filter(s -> matchesView(s, view, today))
                .map(SubscriptionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> listAll(Long customerId) {
        return listAll(customerId, null, null, null, null);
    }

    private static boolean matchesView(Subscription s, String view, LocalDate today) {
        if (view == null || view.isBlank() || "all".equals(view)) return true;
        boolean live = s.getStatus() == SubscriptionStatus.ACTIVE;
        return switch (view) {
            case "trial" -> live && s.getTrialEndDate() != null && s.getTrialEndDate().isAfter(today);
            case "due-soon" -> live && !s.getNextBillingDate().isAfter(today.plusDays(7));
            case "cancelling" -> live && s.isCancelAtPeriodEnd();
            case "term-ending" -> live && s.getTermCycles() != null && !s.isAutoRenew() && s.getTermCycles() - s.getCyclesBilled() <= 1;
            case "resuming" -> s.getStatus() == SubscriptionStatus.PAUSED && s.getResumeOn() != null;
            default -> true;
        };
    }

    // ------------------------------------------------------------------ helpers

    /** quantity x price, less the discount, before tax: what one cycle is worth to the business. */
    public static BigDecimal cycleNet(Subscription s) {
        BigDecimal gross = s.getPrice().multiply(BigDecimal.valueOf(Math.max(1, s.getQuantity())));
        BigDecimal discounted = TaxMath.applyDiscount(gross, s.getDiscountPercent());
        if (s.getTaxRatePercent() != null && Boolean.TRUE.equals(s.getTaxInclusive())) {
            return TaxMath.netOfTax(discounted, TaxMath.taxOnAmount(discounted, s.getTaxRatePercent(), true), true);
        }
        return discounted.setScale(2, RoundingMode.HALF_UP);
    }

    /** One cycle's amount normalised to a month. */
    public static BigDecimal monthly(BigDecimal cycle, BillingInterval interval) {
        if (cycle == null || interval == null) return BigDecimal.ZERO;
        BigDecimal factor = switch (interval) {
            case WEEKLY -> BigDecimal.valueOf(52).divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);
            case MONTHLY -> BigDecimal.ONE;
            case QUARTERLY -> BigDecimal.ONE.divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
            case YEARLY -> BigDecimal.ONE.divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP);
        };
        return cycle.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate today() {
        return LocalDate.now();
    }

    private String actorName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p && p.getUser() != null) {
            return p.getUser().getFullName();
        }
        return null;
    }

    private void event(Subscription s, SubscriptionEventKind kind, String detail) {
        SubscriptionEvent e = new SubscriptionEvent();
        e.setSubscription(s);
        e.setKind(kind);
        e.setDetail(detail != null && detail.length() > 2000 ? detail.substring(0, 2000) : detail);
        e.setCreatedByName(actorName());
        eventRepository.save(e);
    }

    private static String describe(Subscription s) {
        return s.getQuantity() + " x " + s.getPrice().stripTrailingZeros().toPlainString() + " " + s.getBillingInterval()
                + (s.getDiscountPercent() != null && s.getDiscountPercent().signum() > 0 ? " (" + s.getDiscountPercent().stripTrailingZeros().toPlainString() + "% off)" : "");
    }

    private static int intIn(Integer v, int lo, int hi, int dflt, String what) {
        if (v == null) return dflt;
        if (v < lo || v > hi) throw ApiException.badRequest(what + " must be between " + lo + " and " + hi + ".");
        return v;
    }

    private static BigDecimal money(BigDecimal v, String what) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw ApiException.badRequest(what + " cannot be negative.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private void assertEditable(Subscription s, String action) {
        if (s.getStatus() == SubscriptionStatus.CANCELLED || s.getStatus() == SubscriptionStatus.ENDED) {
            throw ApiException.badRequest("This subscription is " + s.getStatus() + " - it can no longer be " + action + ".");
        }
    }

    private void applyTax(Subscription s, Long taxRateId) {
        if (taxRateId != null) {
            TaxRate rate = taxRateService.getEntityOrThrow(taxRateId);
            s.setTaxCode(rate.getCode());
            s.setTaxRatePercent(rate.getRatePercent());
            s.setTaxInclusive(rate.isInclusive());
        } else {
            s.setTaxCode(null);
            s.setTaxRatePercent(null);
            s.setTaxInclusive(null);
        }
    }

    private static BigDecimal discount(BigDecimal d) {
        if (d == null) return BigDecimal.ZERO;
        if (d.signum() < 0 || d.compareTo(BigDecimal.valueOf(100)) > 0) throw ApiException.badRequest("Discount must be between 0 and 100%.");
        return d;
    }

    private static BigDecimal price(BigDecimal p) {
        if (p == null || p.signum() <= 0) throw ApiException.badRequest("Price must be greater than zero.");
        return p;
    }

    // ------------------------------------------------------------------ create / edit / change plan

    @Transactional
    public SubscriptionDto create(CreateSubscriptionRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> ApiException.notFound("Customer not found: " + request.getCustomerId()));
        SubscriptionPlan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> ApiException.notFound("Subscription plan not found: " + request.getPlanId()));
        if (!plan.isActive()) {
            throw ApiException.badRequest("'" + plan.getName() + "' is no longer offered for new subscriptions");
        }
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : today();

        Subscription subscription = new Subscription();
        subscription.setCustomerId(customer.getId());
        subscription.setCustomerName(customer.getName());
        subscription.setPlan(plan);
        // Price and interval are snapshotted (pre-filled from the plan on the form, editable per customer); editing the plan later never reaches back.
        subscription.setPrice(price(request.getPrice()));
        subscription.setBillingInterval(request.getBillingInterval());
        applyTax(subscription, request.getTaxRateId());
        subscription.setDiscountPercent(discount(request.getDiscountPercent()));
        subscription.setQuantity(intIn(request.getQuantity(), 1, 100000, 1, "Quantity"));
        subscription.setSetupFee(money(request.getSetupFee(), "Setup fee"));
        subscription.setTermCycles(request.getTermCycles() == null ? null : intIn(request.getTermCycles(), 1, 1200, 1, "Number of cycles"));
        subscription.setAutoRenew(!Boolean.FALSE.equals(request.getAutoRenew()));
        int trialDays = intIn(request.getTrialDays(), 0, 365, 0, "Trial days");
        subscription.setTrialDays(trialDays);
        // Billing starts when the free trial ends; "prepaid" bills on that day, "paid after service" one cycle later.
        LocalDate billingStart = startDate.plusDays(trialDays);
        subscription.setTrialEndDate(trialDays > 0 ? billingStart : null);
        boolean billInAdvance = !Boolean.FALSE.equals(request.getBillInAdvance());
        subscription.setBillInAdvance(billInAdvance);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(startDate);
        subscription.setBillingAnchorDay(billingStart.getDayOfMonth());
        subscription.setNextBillingDate(billInAdvance ? billingStart : subscription.getBillingInterval().addTo(billingStart, billingStart.getDayOfMonth()));
        subscription.setNotes(request.getNotes());
        Subscription saved = repository.save(subscription);

        auditLogService.record("SUBSCRIPTION_CREATED",
                "Subscribed " + customer.getName() + " to '" + plan.getName() + "' (" + describe(saved) + "), first billing " + saved.getNextBillingDate(),
                "Subscription", saved.getId());
        event(saved, SubscriptionEventKind.CREATED, "Subscribed to '" + plan.getName() + "' - " + describe(saved)
                + (trialDays > 0 ? ", " + trialDays + " free trial day(s)" : "")
                + (saved.getSetupFee().signum() > 0 ? ", setup fee " + saved.getSetupFee() : "")
                + (saved.getTermCycles() != null ? ", " + saved.getTermCycles() + " cycle(s) then " + (saved.isAutoRenew() ? "renews" : "ends") : "")
                + "; first billing " + saved.getNextBillingDate());
        return SubscriptionDto.from(saved);
    }

    /** Edits the terms. They apply from the next bill; nothing is charged or credited now. */
    @Transactional
    public SubscriptionDto update(Long id, SubscriptionRequests.UpdateRequest r) {
        Subscription s = getEntityOrThrow(id);
        assertEditable(s, "edited");
        String before = describe(s);
        if (r.price() != null) s.setPrice(price(r.price()));
        if (r.billingInterval() != null) s.setBillingInterval(r.billingInterval());
        if (r.quantity() != null) s.setQuantity(intIn(r.quantity(), 1, 100000, 1, "Quantity"));
        if (r.discountPercent() != null) s.setDiscountPercent(discount(r.discountPercent()));
        applyTax(s, r.taxRateId());
        if (r.setupFee() != null) {
            BigDecimal fee = money(r.setupFee(), "Setup fee");
            if (s.isSetupFeeBilled() && fee.compareTo(s.getSetupFee()) != 0) {
                throw ApiException.badRequest("The setup fee has already been billed - it can no longer be changed.");
            }
            s.setSetupFee(fee);
        }
        if (r.termCycles() != null && r.termCycles() == 0) {
            s.setTermCycles(null);
        } else if (r.termCycles() != null) {
            s.setTermCycles(intIn(r.termCycles(), 1, 1200, 1, "Number of cycles"));
        }
        if (r.autoRenew() != null) s.setAutoRenew(r.autoRenew());
        if (r.notes() != null) s.setNotes(clip(r.notes(), 4000));
        Subscription saved = repository.save(s);
        String after = describe(saved);
        auditLogService.record("SUBSCRIPTION_EDITED", "Edited the subscription of " + saved.getCustomerName() + ": " + before + " -> " + after, "Subscription", saved.getId());
        event(saved, SubscriptionEventKind.EDITED, before.equals(after) ? "Terms or notes edited" : before + " -> " + after + " (from the next bill)");
        return SubscriptionDto.from(saved);
    }

    /** Works out, without saving anything, what moving to the requested terms means. */
    @Transactional(readOnly = true)
    public SubscriptionViews.ChangeQuoteDto quoteChange(Long id, SubscriptionRequests.ChangePlanRequest r) {
        Subscription s = getEntityOrThrow(id);
        assertEditable(s, "changed");
        return computeQuote(s, r);
    }

    private SubscriptionViews.ChangeQuoteDto computeQuote(Subscription s, SubscriptionRequests.ChangePlanRequest r) {
        BigDecimal newPrice = price(r.price() != null ? r.price() : s.getPrice());
        BillingInterval newInterval = r.billingInterval() != null ? r.billingInterval() : s.getBillingInterval();
        int newQty = intIn(r.quantity(), 1, 100000, s.getQuantity(), "Quantity");
        BigDecimal newDiscount = discount(r.discountPercent() != null ? r.discountPercent() : s.getDiscountPercent());
        BigDecimal oldCycle = TaxMath.applyDiscount(s.getPrice().multiply(BigDecimal.valueOf(s.getQuantity())), s.getDiscountPercent());
        BigDecimal newCycle = TaxMath.applyDiscount(newPrice.multiply(BigDecimal.valueOf(newQty)), newDiscount);
        // Net values for the monthly comparison are shown in the same basis as the subscription's own cycleNet.
        BigDecimal monthlyBefore = monthly(cycleNet(s), s.getBillingInterval());
        Subscription probe = new Subscription();
        probe.setPrice(newPrice);
        probe.setQuantity(newQty);
        probe.setDiscountPercent(newDiscount);
        if (r.taxRateId() != null) {
            TaxRate rate = taxRateService.getEntityOrThrow(r.taxRateId());
            probe.setTaxRatePercent(rate.getRatePercent());
            probe.setTaxInclusive(rate.isInclusive());
        }
        BigDecimal monthlyAfter = monthly(cycleNet(probe), newInterval);

        boolean prepaid = !Boolean.FALSE.equals(s.getBillInAdvance());
        LocalDate today = today();
        boolean canProrate = Boolean.TRUE.equals(r.prorate()) && prepaid && s.getStatus() == SubscriptionStatus.ACTIVE
                && s.getLastBilledAt() != null && s.getCyclesBilled() > 0;
        if (!canProrate) {
            String why = !Boolean.TRUE.equals(r.prorate()) ? "The new terms apply from the next bill; nothing is charged or credited now."
                    : !prepaid ? "This subscription is billed after service, so the new terms simply apply from the next bill."
                    : "Nothing has been billed for the current period yet, so the new terms apply from the next bill.";
            return new SubscriptionViews.ChangeQuoteDto(false, 0, 0, oldCycle, newCycle, BigDecimal.ZERO, BigDecimal.ZERO, monthlyBefore, monthlyAfter, why);
        }
        LocalDate periodEnd = s.getNextBillingDate();
        LocalDate periodStart = s.getBillingInterval().subtractFrom(periodEnd, anchorOf(s));
        long periodDays = Math.max(1, ChronoUnit.DAYS.between(periodStart, periodEnd));
        long remaining = Math.min(periodDays, Math.max(0, ChronoUnit.DAYS.between(today, periodEnd)));
        if (remaining == 0) {
            return new SubscriptionViews.ChangeQuoteDto(false, 0, periodDays, oldCycle, newCycle, BigDecimal.ZERO, BigDecimal.ZERO, monthlyBefore, monthlyAfter,
                    "The period already billed has run out, so the new terms apply from the next bill.");
        }
        long newDays = Math.max(1, ChronoUnit.DAYS.between(periodEnd, newInterval.addTo(periodEnd, anchorOf(s))));
        BigDecimal oldDaily = oldCycle.divide(BigDecimal.valueOf(periodDays), 8, RoundingMode.HALF_UP);
        BigDecimal newDaily = newCycle.divide(BigDecimal.valueOf(newDays), 8, RoundingMode.HALF_UP);
        BigDecimal delta = newDaily.subtract(oldDaily).multiply(BigDecimal.valueOf(remaining)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal charge = delta.signum() > 0 ? delta : BigDecimal.ZERO;
        BigDecimal credit = delta.signum() < 0 ? delta.negate() : BigDecimal.ZERO;
        String msg = delta.signum() == 0 ? "The change costs the same for the rest of the period."
                : delta.signum() > 0 ? "An invoice for " + charge + " is issued now for the " + remaining + " day(s) left of the period already billed."
                : "A credit of " + credit + " is kept on the subscription and taken off the next invoice(s).";
        return new SubscriptionViews.ChangeQuoteDto(true, remaining, periodDays, oldCycle, newCycle, charge, credit, monthlyBefore, monthlyAfter, msg);
    }

    @Transactional
    public SubscriptionDto changePlan(Long id, SubscriptionRequests.ChangePlanRequest r) {
        Subscription s = getEntityOrThrow(id);
        assertEditable(s, "changed");
        SubscriptionViews.ChangeQuoteDto quote = computeQuote(s, r);
        String before = (s.getPlan() != null ? "'" + s.getPlan().getName() + "' " : "") + describe(s);
        if (r.planId() != null && (s.getPlan() == null || !r.planId().equals(s.getPlan().getId()))) {
            SubscriptionPlan plan = planRepository.findById(r.planId()).orElseThrow(() -> ApiException.notFound("Subscription plan not found: " + r.planId()));
            if (!plan.isActive()) throw ApiException.badRequest("'" + plan.getName() + "' is no longer offered.");
            s.setPlan(plan);
        }
        if (r.price() != null) s.setPrice(price(r.price()));
        if (r.billingInterval() != null) s.setBillingInterval(r.billingInterval());
        if (r.quantity() != null) s.setQuantity(intIn(r.quantity(), 1, 100000, 1, "Quantity"));
        if (r.discountPercent() != null) s.setDiscountPercent(discount(r.discountPercent()));
        applyTax(s, r.taxRateId());
        String invoiceNote = "";
        if (quote.chargeNow().signum() > 0) {
            String planName = s.getPlan() != null ? s.getPlan().getName() : "Subscription";
            InvoiceDto adj = invoiceService.generateForSubscription(new SubscriptionInvoiceSpec(
                    s.getCustomerId(), s.getCustomerName(), s.getId(), planName, null, 1,
                    s.getTaxCode(), s.getTaxRatePercent(), s.getTaxInclusive(), BigDecimal.ZERO, null, null,
                    List.of(new SubscriptionInvoiceSpec.Extra("Plan change adjustment - " + planName + " (" + quote.remainingDays() + " day(s) of the current period)", quote.chargeNow()))));
            invoiceNote = "; adjustment invoice " + adj.invoiceNumber() + " for " + quote.chargeNow() + " (before tax)";
        } else if (quote.creditAdded().signum() > 0) {
            s.setCreditBalance(s.getCreditBalance().add(quote.creditAdded()));
            invoiceNote = "; credit " + quote.creditAdded() + " (before tax) kept for the next invoice(s)";
        }
        Subscription saved = repository.save(s);
        String after = (saved.getPlan() != null ? "'" + saved.getPlan().getName() + "' " : "") + describe(saved);
        auditLogService.record("SUBSCRIPTION_PLAN_CHANGED", "Changed the subscription of " + saved.getCustomerName() + ": " + before + " -> " + after + invoiceNote, "Subscription", saved.getId());
        event(saved, SubscriptionEventKind.PLAN_CHANGED, before + " -> " + after + invoiceNote + (quote.prorated() ? "" : " (from the next bill)"));
        return SubscriptionDto.from(saved);
    }

    // ------------------------------------------------------------------ pause / resume / skip / cancel

    /** Reversible - see SubscriptionStatus's own javadoc. Only from ACTIVE. */
    @Transactional
    public SubscriptionDto pause(Long id) {
        return pause(id, null, null);
    }

    @Transactional
    public SubscriptionDto pause(Long id, String reason, LocalDate resumeOn) {
        Subscription subscription = getEntityOrThrow(id);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw ApiException.badRequest("Subscription is " + subscription.getStatus() + ", not ACTIVE - can't pause it");
        }
        if (resumeOn != null && (!resumeOn.isAfter(today()) || resumeOn.isAfter(today().plusDays(365)))) {
            throw ApiException.badRequest("Choose a resume date after today and within a year.");
        }
        subscription.setStatus(SubscriptionStatus.PAUSED);
        subscription.setPausedAt(Instant.now());
        subscription.setPauseReason(clip(reason, 250));
        subscription.setResumeOn(resumeOn);
        Subscription saved = repository.save(subscription);
        auditLogService.record("SUBSCRIPTION_PAUSED", "Paused subscription for " + saved.getCustomerName(), "Subscription", saved.getId());
        event(saved, SubscriptionEventKind.PAUSED, "Paused" + (saved.getPauseReason() != null ? ": " + saved.getPauseReason() : "")
                + (resumeOn != null ? " - resumes on " + resumeOn : ""));
        return SubscriptionDto.from(saved);
    }

    /**
     * Only from PAUSED. If the subscription sat paused long enough that {@code nextBillingDate}
     * is now in the past, it's bumped to today instead - resuming shouldn't instantly bill for
     * the entire paused period.
     */
    @Transactional
    public SubscriptionDto resume(Long id) {
        Subscription subscription = getEntityOrThrow(id);
        if (subscription.getStatus() != SubscriptionStatus.PAUSED) {
            throw ApiException.badRequest("Subscription is " + subscription.getStatus() + ", not PAUSED - can't resume it");
        }
        return SubscriptionDto.from(doResume(subscription, false));
    }

    private Subscription doResume(Subscription subscription, boolean automatic) {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setResumeOn(null);
        subscription.setPauseReason(null);
        subscription.setPausedAt(null);
        if (subscription.getNextBillingDate().isBefore(today())) {
            subscription.setNextBillingDate(today());
            subscription.setBillingAnchorDay(today().getDayOfMonth());
        }
        Subscription saved = repository.save(subscription);
        auditLogService.record("SUBSCRIPTION_RESUMED", (automatic ? "Resumed automatically the subscription for " : "Resumed subscription for ") + saved.getCustomerName()
                + " - next billing " + saved.getNextBillingDate(), "Subscription", saved.getId());
        event(saved, SubscriptionEventKind.RESUMED, (automatic ? "Resumed automatically on the planned date" : "Resumed") + " - next billing " + saved.getNextBillingDate());
        return saved;
    }

    /** Resumes every paused subscription whose resume date has come; returns their customers' names. */
    @Transactional
    public List<String> resumeDue(LocalDate today) {
        List<String> names = new ArrayList<>();
        for (Subscription s : repository.findByStatusAndResumeOnLessThanEqual(SubscriptionStatus.PAUSED, today)) {
            doResume(s, true);
            names.add(s.getCustomerName());
        }
        return names;
    }

    /** Skips the next billing without an invoice; the cycle after it follows as usual. */
    @Transactional
    public SubscriptionDto skipNext(Long id) {
        Subscription s = getEntityOrThrow(id);
        if (s.getStatus() != SubscriptionStatus.ACTIVE) {
            throw ApiException.badRequest("Subscription is " + s.getStatus() + ", not ACTIVE - can't skip a billing");
        }
        if (s.isCancelAtPeriodEnd()) {
            throw ApiException.badRequest("A cancellation is scheduled - undo it first or just let the subscription end.");
        }
        LocalDate skipped = s.getNextBillingDate();
        s.setNextBillingDate(s.getBillingInterval().addTo(skipped, anchorOf(s)));
        Subscription saved = repository.save(s);
        auditLogService.record("SUBSCRIPTION_SKIPPED", "Skipped the billing of " + skipped + " for " + saved.getCustomerName(), "Subscription", saved.getId());
        event(saved, SubscriptionEventKind.SKIPPED, "Skipped the billing due " + skipped + " - next billing " + saved.getNextBillingDate());
        return SubscriptionDto.from(saved);
    }

    /** Terminal - see SubscriptionStatus's own javadoc. From ACTIVE or PAUSED; refused if already CANCELLED or ENDED. */
    @Transactional
    public SubscriptionDto cancel(Long id, String reason) {
        return cancel(id, reason, false);
    }

    /** atPeriodEnd: stays ACTIVE until the paid period runs out (the next billing date), then cancels without billing again. */
    @Transactional
    public SubscriptionDto cancel(Long id, String reason, boolean atPeriodEnd) {
        Subscription subscription = getEntityOrThrow(id);
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw ApiException.badRequest("Subscription is already cancelled");
        }
        if (subscription.getStatus() == SubscriptionStatus.ENDED) {
            throw ApiException.badRequest("Subscription has already ended");
        }
        if (atPeriodEnd) {
            if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                throw ApiException.badRequest("Only an active subscription can be cancelled at the end of its period - resume it first or cancel it now.");
            }
            subscription.setCancelAtPeriodEnd(true);
            subscription.setCancelReason(clip(reason, 2000));
            Subscription saved = repository.save(subscription);
            boolean prepaid = !Boolean.FALSE.equals(saved.getBillInAdvance());
            auditLogService.record("SUBSCRIPTION_CANCEL_SCHEDULED", "Scheduled cancellation of the subscription for " + saved.getCustomerName() + " on " + saved.getNextBillingDate(), "Subscription", saved.getId());
            event(saved, SubscriptionEventKind.CANCEL_SCHEDULED, "Cancels on " + saved.getNextBillingDate() + (prepaid ? " (no further bill)" : " (after the last cycle is billed)")
                    + (saved.getCancelReason() != null ? ": " + saved.getCancelReason() : ""));
            return SubscriptionDto.from(saved);
        }
        return SubscriptionDto.from(doCancel(subscription, reason, "Cancelled"));
    }

    private Subscription doCancel(Subscription subscription, String reason, String label) {
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now());
        subscription.setCancelAtPeriodEnd(false);
        subscription.setResumeOn(null);
        if (reason != null) subscription.setCancelReason(clip(reason, 2000));
        Subscription saved = repository.save(subscription);
        auditLogService.record("SUBSCRIPTION_CANCELLED", label + " subscription for " + saved.getCustomerName(), "Subscription", saved.getId());
        event(saved, SubscriptionEventKind.CANCELLED, label + (saved.getCancelReason() != null ? ": " + saved.getCancelReason() : ""));
        return saved;
    }

    @Transactional
    public SubscriptionDto undoCancel(Long id) {
        Subscription s = getEntityOrThrow(id);
        if (!s.isCancelAtPeriodEnd() || s.getStatus() != SubscriptionStatus.ACTIVE) {
            throw ApiException.badRequest("No cancellation is scheduled for this subscription.");
        }
        s.setCancelAtPeriodEnd(false);
        s.setCancelReason(null);
        Subscription saved = repository.save(s);
        auditLogService.record("SUBSCRIPTION_CANCEL_UNDONE", "Withdrew the scheduled cancellation for " + saved.getCustomerName(), "Subscription", saved.getId());
        event(saved, SubscriptionEventKind.CANCEL_UNDONE, "Scheduled cancellation withdrawn");
        return SubscriptionDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionEventDto> events(Long id) {
        getEntityOrThrow(id);
        return eventRepository.findBySubscriptionIdOrderByIdDesc(id).stream().map(SubscriptionEventDto::from).toList();
    }

    /** This subscription's own billing history - every invoice it has ever generated, newest first. */
    @Transactional(readOnly = true)
    public List<InvoiceDto> billingHistory(Long id) {
        getEntityOrThrow(id);
        return invoiceService.listBySubscriptionId(id);
    }

    /**
     * Fourier: "Print also subscription" - an A4 PDF of this subscription's own current terms
     * plus its billing history, to open in a new tab and print, same shape as InvoiceService.pdf.
     */
    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        SubscriptionDto dto = getById(id);
        List<InvoiceDto> history = invoiceService.listBySubscriptionId(id);
        return documentPdfService.subscriptionPdf(dto, history, storeSettingsService.getSettingsEntity());
    }

    // ------------------------------------------------------------------ billing

    /** Bills this one subscription right now, regardless of whether nextBillingDate has actually arrived yet - same effect as the scheduled sweep picking it up, just on demand. */
    @Transactional
    public SubscriptionDto billNow(Long id) {
        Subscription subscription = getEntityOrThrow(id);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw ApiException.badRequest("Subscription is " + subscription.getStatus() + ", not ACTIVE - can't bill it");
        }
        if (subscription.isCancelAtPeriodEnd()) {
            throw ApiException.badRequest("A cancellation is scheduled - withdraw it first if you want to bill again.");
        }
        billOne(subscription);
        return SubscriptionDto.from(subscription);
    }

    /** Ids of every ACTIVE subscription whose next billing date has arrived (or passed) - the scheduled sweep's input. */
    @Transactional(readOnly = true)
    public List<Long> dueSubscriptionIds(LocalDate today) {
        return repository.findByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus.ACTIVE, today)
                .stream().map(Subscription::getId).toList();
    }

    @Transactional(readOnly = true)
    public boolean isDue(Long id, LocalDate today) {
        return repository.findById(id).map(s -> s.getStatus() == SubscriptionStatus.ACTIVE && !s.getNextBillingDate().isAfter(today)).orElse(false);
    }

    /** What billing one subscription produced. outcome is BILLED, or CANCELLED when a scheduled cancellation was carried out without a new invoice. */
    public record BillResult(Long subscriptionId, String customerName, BigDecimal price, String invoiceNumber, LocalDate nextBillingDate, String outcome) {
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
        boolean prepaid = !Boolean.FALSE.equals(subscription.getBillInAdvance());
        if (subscription.isCancelAtPeriodEnd() && prepaid) {
            // The period that was paid for has run out: cancel instead of billing another one.
            doCancel(subscription, null, "Cancelled at the end of the paid period");
            return new BillResult(subscription.getId(), subscription.getCustomerName(), subscription.getPrice(), null, subscription.getNextBillingDate(), "CANCELLED");
        }
        String invoiceNumber = billOne(subscription);
        boolean cancelled = subscription.getStatus() == SubscriptionStatus.CANCELLED;
        return new BillResult(subscription.getId(), subscription.getCustomerName(), subscription.getPrice(), invoiceNumber, subscription.getNextBillingDate(), cancelled ? "BILLED_AND_CANCELLED" : "BILLED");
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
            if (s.isCancelAtPeriodEnd() && !Boolean.FALSE.equals(s.getBillInAdvance())) continue;   // nothing will be billed
            String email = s.getCustomerId() == null ? null
                    : customerRepository.findById(s.getCustomerId()).map(Customer::getEmail).orElse(null);
            result.add(new ReminderInfo(s.getId(), s.getCustomerName(), email,
                    s.getPlan() != null ? s.getPlan().getName() : "Subscription",
                    TaxMath.applyDiscount(s.getPrice().multiply(BigDecimal.valueOf(s.getQuantity())), s.getDiscountPercent()), next));
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

    private LocalDate baseDate(InvoiceDto inv, ZoneId zone) {
        return inv.dueDate() != null ? inv.dueDate()
                : (inv.invoiceDate() != null ? inv.invoiceDate().atZone(zone).toLocalDate() : null);
    }

    /** ACTIVE subscriptions with an ISSUED invoice still unpaid more than {@code days} days after its due date (or invoice date when it has none). */
    @Transactional(readOnly = true)
    public List<OverdueInfo> overdueCandidates(LocalDate today, int days, ZoneId zone) {
        List<OverdueInfo> result = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (InvoiceDto inv : invoiceService.unpaidSubscriptionInvoices()) {
            Long sid = inv.subscriptionId();
            if (sid == null || seen.contains(sid)) continue;
            Subscription s = repository.findById(sid).orElse(null);
            if (s == null || s.getStatus() != SubscriptionStatus.ACTIVE) continue;
            LocalDate base = baseDate(inv, zone);
            if (base != null && base.plusDays(days).isBefore(today)) {
                result.add(new OverdueInfo(s.getId(), s.getCustomerName(), inv.invoiceNumber(), base, inv.amountOutstanding()));
                seen.add(sid);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ overdue reminders to the customer

    public record DunningInfo(Long subscriptionId, Long invoiceId, String customerName, String customerEmail, String invoiceNumber,
                              BigDecimal outstanding, long daysLate, int reminderNumber) {
    }

    /** Unpaid subscription invoices due a reminder today under the saved settings. */
    @Transactional(readOnly = true)
    public List<DunningInfo> dunningCandidates(LocalDate today, ZoneId zone) {
        SubscriptionSettings st = settingsEntity();
        List<DunningInfo> out = new ArrayList<>();
        if (!st.isDunningEnabled()) return out;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (InvoiceDto inv : invoiceService.unpaidSubscriptionInvoices()) {
            Long sid = inv.subscriptionId();
            if (sid == null || !seen.add(sid)) continue;
            Subscription s = repository.findById(sid).orElse(null);
            if (s == null) continue;
            LocalDate base = baseDate(inv, zone);
            if (base == null) continue;
            long late = ChronoUnit.DAYS.between(base, today);
            if (late < st.getDunningFirstDays()) continue;
            boolean same = inv.id().equals(s.getDunningInvoiceId());
            int sent = same ? s.getDunningCount() : 0;
            if (sent >= st.getDunningMax()) continue;
            if (same && s.getDunningLastOn() != null && s.getDunningLastOn().plusDays(st.getDunningRepeatDays()).isAfter(today)) continue;
            String email = inv.customerEmail();
            out.add(new DunningInfo(sid, inv.id(), s.getCustomerName(), email, inv.invoiceNumber(), inv.amountOutstanding(), late, sent + 1));
        }
        return out;
    }

    @Transactional
    public void markDunned(Long subscriptionId, Long invoiceId, LocalDate today, int number, String invoiceNumber) {
        Subscription s = getEntityOrThrow(subscriptionId);
        s.setDunningInvoiceId(invoiceId);
        s.setDunningCount(number);
        s.setDunningLastOn(today);
        repository.save(s);
        event(s, SubscriptionEventKind.REMINDER, "Overdue reminder " + number + " e-mailed for invoice " + invoiceNumber);
    }

    // ------------------------------------------------------------------ settings

    @Transactional
    public SubscriptionSettings settingsEntity() {
        return settingsRepository.findAll().stream().findFirst().orElseGet(() -> settingsRepository.save(new SubscriptionSettings()));
    }

    @Transactional
    public SubscriptionViews.SettingsDto settings() {
        return toDto(settingsEntity());
    }

    @Transactional
    public SubscriptionViews.SettingsDto updateSettings(SubscriptionRequests.SettingsRequest r) {
        SubscriptionSettings s = settingsEntity();
        if (r.dunningEnabled() != null) s.setDunningEnabled(r.dunningEnabled());
        if (r.dunningFirstDays() != null) s.setDunningFirstDays(intIn(r.dunningFirstDays(), 0, 90, 3, "Days before the first reminder"));
        if (r.dunningRepeatDays() != null) s.setDunningRepeatDays(intIn(r.dunningRepeatDays(), 1, 90, 7, "Days between reminders"));
        if (r.dunningMax() != null) s.setDunningMax(intIn(r.dunningMax(), 1, 10, 3, "Reminders per invoice"));
        if (r.catchUpMax() != null) s.setCatchUpMax(intIn(r.catchUpMax(), 1, 12, 1, "Missed cycles billed per run"));
        SubscriptionSettings saved = settingsRepository.save(s);
        auditLogService.record("SUBSCRIPTION_SETTINGS_UPDATED", "Updated subscription settings (overdue reminders " + (saved.isDunningEnabled() ? "on" : "off")
                + ", up to " + saved.getCatchUpMax() + " missed cycle(s) per run)", "SubscriptionSettings", saved.getId());
        return toDto(saved);
    }

    private static SubscriptionViews.SettingsDto toDto(SubscriptionSettings s) {
        return new SubscriptionViews.SettingsDto(s.isDunningEnabled(), s.getDunningFirstDays(), s.getDunningRepeatDays(), s.getDunningMax(), s.getCatchUpMax());
    }

    // ------------------------------------------------------------------ overview

    @Transactional(readOnly = true)
    public SubscriptionViews.SummaryDto summary() {
        LocalDate today = today();
        ZoneId zone = ZoneId.systemDefault();
        int active = 0, trial = 0, paused = 0, cancelled = 0, ended = 0, newLast30 = 0, cancelledLast30 = 0;
        int up7 = 0, up30 = 0, trialsEnding = 0, cancelling = 0, termsEnding = 0;
        BigDecimal mrr = BigDecimal.ZERO, up7Amt = BigDecimal.ZERO, up30Amt = BigDecimal.ZERO;
        List<SubscriptionViews.UpcomingDto> upcoming = new ArrayList<>();
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        for (Subscription s : repository.findAllByOrderByIdDesc()) {
            if (s.getStartDate() != null && !s.getStartDate().isBefore(today.minusDays(30))) newLast30++;
            switch (s.getStatus()) {
                case PAUSED -> paused++;
                case CANCELLED -> {
                    cancelled++;
                    if (s.getCancelledAt() != null && s.getCancelledAt().isAfter(cutoff)) cancelledLast30++;
                }
                case ENDED -> {
                    ended++;
                    if (s.getEndedAt() != null && s.getEndedAt().isAfter(cutoff)) cancelledLast30++;
                }
                case ACTIVE -> {
                    boolean onTrial = s.getTrialEndDate() != null && s.getTrialEndDate().isAfter(today);
                    if (onTrial) {
                        trial++;
                        if (!s.getTrialEndDate().isAfter(today.plusDays(7))) trialsEnding++;
                    } else {
                        active++;
                        mrr = mrr.add(monthly(cycleNet(s), s.getBillingInterval()));
                    }
                    if (s.isCancelAtPeriodEnd()) cancelling++;
                    if (s.getTermCycles() != null && !s.isAutoRenew() && s.getTermCycles() - s.getCyclesBilled() <= 1) termsEnding++;
                    boolean billsAgain = !(s.isCancelAtPeriodEnd() && !Boolean.FALSE.equals(s.getBillInAdvance()));
                    if (billsAgain && !s.getNextBillingDate().isAfter(today.plusDays(30))) {
                        BigDecimal amt = cycleNet(s);
                        up30++;
                        up30Amt = up30Amt.add(amt);
                        if (!s.getNextBillingDate().isAfter(today.plusDays(7))) {
                            up7++;
                            up7Amt = up7Amt.add(amt);
                        }
                        upcoming.add(new SubscriptionViews.UpcomingDto(s.getId(), s.getCustomerName(), s.getPlan() != null ? s.getPlan().getName() : null,
                                s.getNextBillingDate(), amt, onTrial));
                    }
                }
            }
        }
        upcoming.sort(Comparator.comparing(SubscriptionViews.UpcomingDto::date).thenComparing(SubscriptionViews.UpcomingDto::customerName, Comparator.nullsLast(Comparator.naturalOrder())));
        List<SubscriptionViews.UnpaidDto> unpaid = new ArrayList<>();
        BigDecimal unpaidAmt = BigDecimal.ZERO;
        java.util.Set<Long> unpaidSubs = new java.util.HashSet<>();
        for (InvoiceDto inv : invoiceService.unpaidSubscriptionInvoices()) {
            unpaidAmt = unpaidAmt.add(inv.amountOutstanding());
            unpaidSubs.add(inv.subscriptionId());
            LocalDate base = baseDate(inv, zone);
            long late = base == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(base, today));
            Subscription s = inv.subscriptionId() == null ? null : repository.findById(inv.subscriptionId()).orElse(null);
            unpaid.add(new SubscriptionViews.UnpaidDto(inv.subscriptionId(), inv.customerName(), inv.invoiceNumber(), inv.amountOutstanding(), late,
                    s != null && inv.id().equals(s.getDunningInvoiceId()) ? s.getDunningCount() : 0));
        }
        unpaid.sort(Comparator.comparingLong(SubscriptionViews.UnpaidDto::daysLate).reversed());
        int payingBase = active + paused;
        BigDecimal avg = active == 0 ? BigDecimal.ZERO : mrr.divide(BigDecimal.valueOf(active), 2, RoundingMode.HALF_UP);
        int denominator = payingBase + trial + cancelledLast30;
        BigDecimal churn = denominator == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(cancelledLast30).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
        return new SubscriptionViews.SummaryDto(active, trial, paused, cancelled, ended, mrr, mrr.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP), avg,
                newLast30, cancelledLast30, churn, up7, up7Amt, up30, up30Amt, trialsEnding, cancelling, termsEnding,
                unpaidSubs.size(), unpaidAmt, upcoming.size() > 12 ? new ArrayList<>(upcoming.subList(0, 12)) : upcoming,
                unpaid.size() > 12 ? new ArrayList<>(unpaid.subList(0, 12)) : unpaid);
    }

    // ------------------------------------------------------------------ the bill itself

    /**
     * Bills the cycle that is due. {@code dueDate} (the subscription's {@code nextBillingDate} going in) sits at one end of the cycle:
     * for a prepaid subscription it is the start of the cycle paid for upfront, for "paid after service" it is the end of the cycle just
     * rendered. The next bill's date is always {@code dueDate} advanced one interval. The invoice carries quantity x price (less the discount,
     * with tax), a setup fee on the first invoice, and any credit left over from a downgrade. A fixed term counts cycles billed and then renews
     * or ends the subscription; a scheduled cancellation on a paid-after-service subscription is carried out right after its last cycle is billed.
     */
    /** The billing day of month: the stored anchor, or (rows from before 1.22.1 the migration could not fill) the day of the next billing date. */
    static Integer anchorOf(Subscription s) {
        if (s.getBillingAnchorDay() != null) return s.getBillingAnchorDay();
        return s.getNextBillingDate() == null ? null : s.getNextBillingDate().getDayOfMonth();
    }

    private String billOne(Subscription subscription) {
        String planName = subscription.getPlan() != null ? subscription.getPlan().getName() : "Subscription";
        LocalDate dueDate = subscription.getNextBillingDate();
        boolean advance = !Boolean.FALSE.equals(subscription.getBillInAdvance());
        Integer anchor = anchorOf(subscription);
        LocalDate periodStart = advance ? dueDate : subscription.getBillingInterval().subtractFrom(dueDate, anchor);
        LocalDate periodEnd = advance ? subscription.getBillingInterval().addTo(dueDate, anchor) : dueDate;

        List<SubscriptionInvoiceSpec.Extra> extras = new ArrayList<>();
        BigDecimal base = TaxMath.applyDiscount(subscription.getPrice().multiply(BigDecimal.valueOf(Math.max(1, subscription.getQuantity()))), subscription.getDiscountPercent());
        boolean addSetup = !subscription.isSetupFeeBilled() && subscription.getSetupFee() != null && subscription.getSetupFee().signum() > 0;
        if (addSetup) {
            extras.add(new SubscriptionInvoiceSpec.Extra("Setup fee - " + planName, subscription.getSetupFee()));
            base = base.add(subscription.getSetupFee());
        }
        BigDecimal credit = BigDecimal.ZERO;
        if (subscription.getCreditBalance() != null && subscription.getCreditBalance().signum() > 0) {
            credit = subscription.getCreditBalance().min(base);
            if (credit.signum() > 0) extras.add(new SubscriptionInvoiceSpec.Extra("Credit from plan change", credit.negate()));
        }

        InvoiceDto invoice = invoiceService.generateForSubscription(new SubscriptionInvoiceSpec(
                subscription.getCustomerId(), subscription.getCustomerName(), subscription.getId(),
                planName, subscription.getPrice(), subscription.getQuantity(),
                subscription.getTaxCode(), subscription.getTaxRatePercent(), subscription.getTaxInclusive(),
                subscription.getDiscountPercent(), periodStart, periodEnd, extras));

        subscription.setLastBilledAt(Instant.now());
        subscription.setNextBillingDate(subscription.getBillingInterval().addTo(dueDate, anchor));
        subscription.setCyclesBilled(subscription.getCyclesBilled() + 1);
        if (addSetup) subscription.setSetupFeeBilled(true);
        if (credit.signum() > 0) subscription.setCreditBalance(subscription.getCreditBalance().subtract(credit));
        repository.save(subscription);

        // invoice.invoiceNumber() is already ISSUED and posted to Accounting by the time this entry is written.
        auditLogService.record("SUBSCRIPTION_BILLED",
                "Billed " + subscription.getCustomerName() + " " + subscription.getPrice() + " x " + subscription.getQuantity() + " for '" + planName
                        + "' (period " + periodStart + " to " + periodEnd + ") - issued invoice " + invoice.invoiceNumber()
                        + " (next billing " + subscription.getNextBillingDate() + ")",
                "Subscription", subscription.getId());
        event(subscription, SubscriptionEventKind.BILLED, "Invoice " + invoice.invoiceNumber() + " for " + invoice.totalAmount() + ", period " + periodStart + " to " + periodEnd
                + (addSetup ? ", includes the setup fee" : "") + (credit.signum() > 0 ? ", " + credit + " credit applied" : ""));

        // Fixed term: after the last cycle the subscription renews or ends.
        if (subscription.getTermCycles() != null && subscription.getCyclesBilled() >= subscription.getTermCycles()) {
            if (subscription.isAutoRenew()) {
                subscription.setCyclesBilled(0);
                subscription.setRenewals(subscription.getRenewals() + 1);
                repository.save(subscription);
                event(subscription, SubscriptionEventKind.RENEWED, "Term of " + subscription.getTermCycles() + " cycle(s) completed - renewed for another " + subscription.getTermCycles()
                        + " (renewal " + subscription.getRenewals() + ")");
            } else if (!subscription.isCancelAtPeriodEnd()) {
                subscription.setStatus(SubscriptionStatus.ENDED);
                subscription.setEndedAt(Instant.now());
                repository.save(subscription);
                auditLogService.record("SUBSCRIPTION_ENDED", "Subscription for " + subscription.getCustomerName() + " ended after " + subscription.getTermCycles() + " cycle(s)", "Subscription", subscription.getId());
                event(subscription, SubscriptionEventKind.ENDED, "Term of " + subscription.getTermCycles() + " cycle(s) completed - the subscription has ended");
            }
        }
        if (subscription.isCancelAtPeriodEnd() && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            // Paid-after-service: the scheduled cancellation takes effect now that the last cycle has been billed.
            doCancel(subscription, null, "Cancelled after the last cycle was billed");
        }
        return invoice.invoiceNumber();
    }

    @Transactional(readOnly = true)
    public SubscriptionDto getById(Long id) {
        return SubscriptionDto.from(getEntityOrThrow(id));
    }

    private Subscription getEntityOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subscription not found: " + id));
    }
}
