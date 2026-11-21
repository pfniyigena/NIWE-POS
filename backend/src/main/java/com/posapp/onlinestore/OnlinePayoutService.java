package com.posapp.onlinestore;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/**
 * v1.71.0: payouts from the payment providers. Money customers pay online sits in a clearing account per provider (MTN, Airtel, card) until the provider pays it to
 * its wallet or bank account (v1.75.0: each has an account of its own); staff record each payout here with the fee the provider kept, and the books move the gross amount out of the clearing account (net to Cash and Bank, fee
 * to Payment Provider Fees). The balances shown are the clearing accounts of the ledger, so they agree with the books by construction.
 */
@Service
public class OnlinePayoutService {

    public record PayoutRequest(@NotNull @Size(max = 20) String channel, @NotNull BigDecimal gross, BigDecimal fee, LocalDate paidOn,
                                @Size(max = 80) String reference, @Size(max = 300) String note) {
    }

    public record PayoutDto(Long id, String channel, String label, BigDecimal gross, BigDecimal fee, BigDecimal net, String reference, String note, LocalDate paidOn, String createdBy, Instant createdAt) {
    }

    /** label: the provider's name; empty for the shared Card and Mobile money channels. */
    public record ClearingBalance(String channel, String label, BigDecimal balance) {
    }

    public record PayoutOverview(List<ClearingBalance> balances, List<PayoutDto> payouts, BigDecimal feesToDate) {
    }

    private final OnlinePayoutRepository repository;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired
    private com.posapp.accounting.OnlineProviderAccounts providerAccounts;
    @org.springframework.beans.factory.annotation.Autowired
    private List<com.posapp.onlinestore.payment.PaymentProvider> paymentProviders;

    public OnlinePayoutService(OnlinePayoutRepository repository, AccountingService accounting, AuditLogService auditLogService, JdbcTemplate jdbc) {
        this.repository = repository;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
        this.jdbc = jdbc;
    }

    private static String user() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || a.getName() == null ? "system" : a.getName();
    }

    /** What the books hold on an account today (debits minus credits); zero when the account does not exist yet. */
    private BigDecimal balanceOf(com.posapp.accounting.Account account) {
        if (account == null) return BigDecimal.ZERO;
        BigDecimal b = jdbc.queryForObject("select coalesce(sum(debit - credit), 0) from journal_entry_line where account_id = ?", BigDecimal.class, account.getId());
        return b == null ? BigDecimal.ZERO : b;
    }

    private BigDecimal balanceOfKey(String wellKnownKey) {
        BigDecimal b = jdbc.queryForObject("select coalesce(sum(l.debit - l.credit), 0) from journal_entry_line l join account a on a.id = l.account_id where a.well_known_key = ?",
                BigDecimal.class, wellKnownKey);
        return b == null ? BigDecimal.ZERO : b;
    }

    private String labelOf(String channel) {
        return paymentProviders.stream().filter(p -> p.code().equalsIgnoreCase(channel)).map(com.posapp.onlinestore.payment.PaymentProvider::label).findFirst().orElse(null);
    }

    /** The channels a payout can be recorded for: each provider that has a clearing account, then Card, then the old shared Mobile money one. */
    private List<String> providerChannels() {
        List<String> out = new java.util.ArrayList<>();
        for (com.posapp.onlinestore.payment.PaymentProvider p : paymentProviders) {
            if (providerAccounts.find(p.code()).clearing() != null) out.add(p.code().toUpperCase(Locale.ROOT));
        }
        out.sort(String::compareTo);
        return out;
    }

    @Transactional(readOnly = true)
    public PayoutOverview overview() {
        // v1.76.0: one balance per provider that has a clearing account (Online Store > Payments > Accounts by provider), then Card; the old shared mobile money balance is
        // listed only while it still holds something
        List<ClearingBalance> balances = new java.util.ArrayList<>();
        for (String c : providerChannels()) balances.add(new ClearingBalance(c, labelOf(c), balanceOf(providerAccounts.clearingOf(c))));
        balances.add(new ClearingBalance("CARD", null, balanceOfKey(com.posapp.accounting.AccountService.ONLINE_CLEARING_CARD)));
        BigDecimal other = balanceOfKey(com.posapp.accounting.AccountService.ONLINE_CLEARING_MOBILE);
        if (other.signum() != 0) balances.add(new ClearingBalance("MOBILE_MONEY", null, other));
        List<PayoutDto> rows = repository.findTop50ByOrderByPaidOnDescIdDesc().stream().map(this::dto).toList();
        BigDecimal fees = repository.findAll().stream().map(OnlinePayout::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayoutOverview(balances, rows, fees);
    }

    @Transactional
    public PayoutDto record(PayoutRequest in) {
        String channel = in.channel() == null ? "" : in.channel().trim().toUpperCase(Locale.ROOT);
        if (!channel.equals("CARD") && !channel.equals("MOBILE_MONEY") && !providerChannels().contains(channel)) throw ApiException.badRequest(I18n.t("Choose the provider that paid out"));
        BigDecimal gross = in.gross() == null ? BigDecimal.ZERO : in.gross().setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal fee = in.fee() == null ? BigDecimal.ZERO : in.fee().setScale(2, java.math.RoundingMode.HALF_UP);
        if (gross.signum() <= 0) throw ApiException.badRequest(I18n.t("Enter the amount customers paid that this payout covers"));
        if (fee.signum() < 0 || fee.compareTo(gross) >= 0) throw ApiException.badRequest(I18n.t("The fee must be zero or more, and less than the amount"));
        LocalDate day = in.paidOn() == null ? LocalDate.now(ZoneOffset.UTC) : in.paidOn();
        if (day.isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1))) throw ApiException.badRequest(I18n.t("The payout date is in the future"));
        OnlinePayout p = new OnlinePayout();
        p.setChannel(channel);
        p.setGross(gross);
        p.setFee(fee);
        p.setNet(gross.subtract(fee));
        p.setReference(in.reference() == null || in.reference().isBlank() ? null : in.reference().trim());
        p.setNote(in.note() == null || in.note().isBlank() ? null : in.note().trim());
        p.setPaidOn(day);
        p.setCreatedBy(user());
        repository.save(p);
        String label = labelOf(channel);
        String what = label != null ? label : channel.equals("CARD") ? "card" : "mobile money";
        accounting.postOnlinePayout(p.getId(), channel, gross, fee, "Online payout (" + what + ")" + (p.getReference() == null ? "" : " " + p.getReference()), day.atTime(12, 0).toInstant(ZoneOffset.UTC));
        auditLogService.record("ONLINE_PAYOUT", "Payout of " + OnlineStoreService.money(gross) + " from " + what + ", fee " + OnlineStoreService.money(fee), "OnlinePayout", p.getId());
        return dto(p);
    }

    private PayoutDto dto(OnlinePayout p) {
        return new PayoutDto(p.getId(), p.getChannel(), labelOf(p.getChannel()), p.getGross(), p.getFee(), p.getNet(), p.getReference(), p.getNote(), p.getPaidOn(), p.getCreatedBy(), p.getCreatedAt());
    }
}
