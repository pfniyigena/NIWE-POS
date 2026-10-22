package com.posapp.hotel;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.paymentmethod.PaymentMethodService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * A group's master account. Member bills charged to the group at check-out, deposits the organiser pays in advance and payments against
 * what is owed. It depends only on the entry store and accounting, so the folio service can use it without a circular dependency.
 */
@Service
public class GroupLedger {

    private final GroupEntryRepository entryRepository;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;
    private final PaymentMethodService paymentMethodService;
    private final HotelClock clock;

    public GroupLedger(GroupEntryRepository entryRepository, AccountingService accountingService, AuditLogService auditLogService,
                       PaymentMethodService paymentMethodService, HotelClock clock) {
        this.entryRepository = entryRepository;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
        this.paymentMethodService = paymentMethodService;
        this.clock = clock;
    }

    /** Totals of a group's account. {@code owed} is what the group still has to pay; {@code held} is deposit not yet used. */
    public record Totals(BigDecimal billed, BigDecimal deposits, BigDecimal applied, BigDecimal paid, BigDecimal refunded,
                         BigDecimal owed, BigDecimal held) {
    }

    public Totals totals(Long groupId) {
        return totals(entryRepository.findByGroupIdOrderByIdAsc(groupId));
    }

    static Totals totals(List<GroupEntry> entries) {
        BigDecimal billed = sum(entries, GroupEntryKind.BILL);
        BigDecimal deposits = sum(entries, GroupEntryKind.DEPOSIT);
        BigDecimal applied = sum(entries, GroupEntryKind.APPLIED);
        BigDecimal paid = sum(entries, GroupEntryKind.PAYMENT);
        BigDecimal refunded = sum(entries, GroupEntryKind.REFUND);
        return new Totals(billed, deposits, applied, paid, refunded, billed.subtract(applied).subtract(paid), deposits.subtract(applied).subtract(refunded));
    }

    private static BigDecimal sum(List<GroupEntry> entries, GroupEntryKind kind) {
        return entries.stream().filter(e -> e.getKind() == kind).map(GroupEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** A member's bill moves to the group at check-out; any held deposit is set against it straight away. */
    @Transactional
    public GroupEntry billFromStay(Booking booking, int billNo, BigDecimal amount, LocalDate today) {
        HotelGroup group = booking.getGroup();
        if (group == null) throw ApiException.badRequest("This booking is not part of a group");
        GroupEntry e = new GroupEntry();
        e.setGroup(group);
        e.setKind(GroupEntryKind.BILL);
        e.setBooking(booking);
        e.setBillNo(billNo);
        e.setDescription("Stay " + booking.getBookingNumber() + " — " + booking.getGuest().getFullName() + (billNo > 1 ? " (bill " + billNo + ")" : ""));
        e.setAmount(money(amount));
        e.setEntryDate(today);
        GroupEntry saved = entryRepository.save(e);
        accountingService.postHotelGroupBill(saved.getId(), "Group " + group.getGroupCode() + " " + booking.getBookingNumber(), saved.getAmount());
        auditLogService.record("HOTEL_GROUP_BILLED", group.getGroupCode() + " " + saved.getAmount() + " from " + booking.getBookingNumber(), "HotelGroup", group.getId());
        BigDecimal held = totals(group.getId()).held();
        BigDecimal toApply = held.min(totals(group.getId()).owed());
        if (toApply.signum() > 0) applyDeposit(group, toApply, today);
        return saved;
    }

    private void applyDeposit(HotelGroup group, BigDecimal amount, LocalDate today) {
        GroupEntry a = new GroupEntry();
        a.setGroup(group);
        a.setKind(GroupEntryKind.APPLIED);
        a.setDescription("Deposit applied");
        a.setAmount(money(amount));
        a.setEntryDate(today);
        GroupEntry saved = entryRepository.save(a);
        accountingService.postHotelGroupApplied(saved.getId(), "Group deposit applied " + group.getGroupCode(), saved.getAmount());
    }

    /** The organiser pays a deposit, held until the group's bills arrive. */
    @Transactional
    public GroupEntry deposit(HotelGroup group, String method, BigDecimal amount, String reference, String note) {
        requireOpen(group);
        BigDecimal value = positive(amount);
        GroupEntry e = entry(group, GroupEntryKind.DEPOSIT, method, value, reference, note == null || note.isBlank() ? "Group deposit" : note.trim());
        accountingService.postHotelGroupDeposit(e.getId(), "Group deposit " + group.getGroupCode(), e.getAmount());
        auditLogService.record("HOTEL_GROUP_DEPOSIT", group.getGroupCode() + " " + e.getAmount(), "HotelGroup", group.getId());
        BigDecimal toApply = totals(group.getId()).held().min(totals(group.getId()).owed());
        if (toApply.signum() > 0) applyDeposit(group, toApply, clock.today());
        return e;
    }

    /** The organiser pays against what the group owes (not more than that — extra money is a deposit). */
    @Transactional
    public GroupEntry payment(HotelGroup group, String method, BigDecimal amount, String reference, String note) {
        BigDecimal value = positive(amount);
        BigDecimal owed = totals(group.getId()).owed();
        if (value.compareTo(owed) > 0) {
            throw ApiException.badRequest("The group owes " + owed.setScale(2, RoundingMode.HALF_UP) + " — take anything more as a deposit");
        }
        GroupEntry e = entry(group, GroupEntryKind.PAYMENT, method, value, reference, note == null || note.isBlank() ? "Payment" : note.trim());
        accountingService.postHotelGroupPayment(e.getId(), "Group payment " + group.getGroupCode(), e.getAmount());
        auditLogService.record("HOTEL_GROUP_PAYMENT", group.getGroupCode() + " " + e.getAmount(), "HotelGroup", group.getId());
        return e;
    }

    /** Hands back deposit that was never used. */
    @Transactional
    public GroupEntry refund(HotelGroup group, String method, BigDecimal amount, String note) {
        BigDecimal value = positive(amount);
        BigDecimal held = totals(group.getId()).held();
        if (value.compareTo(held) > 0) {
            throw ApiException.badRequest("Only " + held.setScale(2, RoundingMode.HALF_UP) + " of deposit is held");
        }
        GroupEntry e = entry(group, GroupEntryKind.REFUND, method, value, null, note == null || note.isBlank() ? "Deposit refunded" : note.trim());
        accountingService.postHotelGroupRefund(e.getId(), "Group deposit refund " + group.getGroupCode(), e.getAmount());
        auditLogService.record("HOTEL_GROUP_REFUND", group.getGroupCode() + " " + e.getAmount(), "HotelGroup", group.getId());
        return e;
    }

    private GroupEntry entry(HotelGroup group, GroupEntryKind kind, String method, BigDecimal amount, String reference, String description) {
        GroupEntry e = new GroupEntry();
        e.setGroup(group);
        e.setKind(kind);
        e.setMethod(payable(method == null || method.isBlank() ? "CASH" : method.trim()));
        e.setAmount(amount);
        e.setReference(reference == null || reference.isBlank() ? null : reference.trim());
        e.setDescription(description);
        e.setEntryDate(clock.today());
        return entryRepository.save(e);
    }

    private String payable(String method) {
        String code = paymentMethodService.validateActiveCodeOrThrow(method);
        java.util.Set<String> blocked = new java.util.HashSet<>(paymentMethodService.listRoomChargeCodes());
        blocked.addAll(paymentMethodService.listCreditCodes());
        if (blocked.stream().anyMatch(code::equalsIgnoreCase)) {
            throw ApiException.badRequest("'" + code + "' cannot be used to pay a hotel bill");
        }
        return code;
    }

    private static BigDecimal positive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("The amount must be greater than zero");
        return money(amount);
    }

    private static void requireOpen(HotelGroup group) {
        if (group.getStatus() == GroupStatus.CANCELLED || group.getStatus() == GroupStatus.COMPLETED) {
            throw ApiException.badRequest("This group is " + group.getStatus().name().toLowerCase() + " — it takes no more deposits");
        }
    }
}
