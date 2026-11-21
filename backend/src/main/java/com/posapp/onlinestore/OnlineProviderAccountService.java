package com.posapp.onlinestore;

import com.posapp.accounting.Account;
import com.posapp.accounting.AccountRepository;
import com.posapp.accounting.AccountService;
import com.posapp.accounting.AccountType;
import com.posapp.accounting.OnlineProviderAccounts;
import com.posapp.accounting.dto.AccountRequest;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.payment.PaymentProvider;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v1.76.0: Online Store > Payments > Accounts by provider. For each payment provider staff choose the clearing account (what the provider still owes the shop) and the payout
 * account (the wallet or bank account its payouts arrive in), either an account that already exists or a new one made on the spot. See {@link OnlineProviderAccounts}.
 */
@Service
public class OnlineProviderAccountService {

    public record AccountOption(Long id, String code, String name) {
    }

    public record ProviderRow(String code, String label, boolean active, boolean available, List<String> methods, AccountOption clearing, AccountOption payout, String source,
                              BigDecimal clearingBalance) {
    }

    public record View(List<ProviderRow> providers, List<AccountOption> accounts, String suggestedClearingCode, String suggestedPayoutCode) {
    }

    /** Either an existing account (clearingAccountId) or a new one (newClearingName, with an optional code), or nothing for no account of its own. Same for the payout account. */
    public record SaveRequest(Long clearingAccountId, @Size(max = 120) String newClearingName, @Size(max = 20) String newClearingCode,
                              Long payoutAccountId, @Size(max = 120) String newPayoutName, @Size(max = 20) String newPayoutCode) {
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private AccountService accountService;
    @Autowired
    private OnlineProviderAccounts resolver;
    @Autowired
    private List<PaymentProvider> providers;
    @Autowired
    private OnlinePaymentService payments;
    @Autowired
    private OnlineStoreSettingsRepository settingsRepository;
    @Autowired
    private AuditLogService auditLogService;

    private static AccountOption option(Account a) {
        return a == null ? null : new AccountOption(a.getId(), a.getCode(), a.getName());
    }

    private BigDecimal balance(Account a) {
        if (a == null) return BigDecimal.ZERO;
        BigDecimal b = jdbc.queryForObject("select coalesce(sum(debit - credit), 0) from journal_entry_line where account_id = ?", BigDecimal.class, a.getId());
        return b == null ? BigDecimal.ZERO : b;
    }

    private List<PaymentProvider> listed() {
        return providers.stream().filter(p -> !com.posapp.onlinestore.payment.SimulatedPaymentProvider.CODE.equals(p.code()) || p.available())
                .sorted(Comparator.comparing(PaymentProvider::label)).toList();
    }

    /** The first account codes that are free, counting up from the highest code the online accounts use now (null when the codes are not numbers). */
    private String[] suggestCodes() {
        Set<String> used = new HashSet<>();
        long max = -1;
        for (Account a : accounts.findAll()) {
            used.add(a.getCode().toLowerCase(Locale.ROOT));
            String k = a.getWellKnownKey();
            if (k != null && (k.startsWith("ONLINE_CLEARING") || k.startsWith("WALLET_") || k.equals("BANK_CARD"))) {
                try { max = Math.max(max, Long.parseLong(a.getCode().trim())); } catch (NumberFormatException ignored) { /* not a number */ }
            }
        }
        for (Account a : accounts.findAll()) {
            if (a.getId() == null) continue;
            if (jdbcHasProviderAccount(a.getId())) {
                try { max = Math.max(max, Long.parseLong(a.getCode().trim())); } catch (NumberFormatException ignored) { /* not a number */ }
            }
        }
        if (max < 0) return new String[]{null, null};
        long n = max + 1;
        while (used.contains(String.valueOf(n))) n++;
        String first = String.valueOf(n);
        n++;
        while (used.contains(String.valueOf(n))) n++;
        return new String[]{first, String.valueOf(n)};
    }

    private boolean jdbcHasProviderAccount(Long id) {
        Integer c = jdbc.queryForObject("select count(*) from online_provider_account where clearing_account_id = ? or payout_account_id = ?", Integer.class, id, id);
        return c != null && c > 0;
    }

    @Transactional(readOnly = true)
    public View view() {
        OnlineStoreSettings s = settingsRepository.findAll().stream().findFirst().orElse(null);
        Set<String> active = new HashSet<>();
        for (PaymentProvider p : payments.activeProviders(s)) active.add(p.code());
        List<ProviderRow> rows = new ArrayList<>();
        for (PaymentProvider p : listed()) {
            OnlineProviderAccounts.Pair pair = resolver.find(p.code());
            rows.add(new ProviderRow(p.code(), p.label(), active.contains(p.code()), p.available(), p.methods(), option(pair.clearing()), option(pair.payout()), pair.source(), balance(pair.clearing())));
        }
        List<AccountOption> options = accounts.findAll().stream().filter(a -> a.getType() == AccountType.ASSET && a.isActive())
                .sorted(Comparator.comparing(Account::getCode)).map(OnlineProviderAccountService::option).toList();
        String[] codes = suggestCodes();
        return new View(rows, options, codes[0], codes[1]);
    }

    private Account choose(Long id, String newName, String newCode, String suggestedCode, String what) {
        if (newName != null && !newName.isBlank()) {
            AccountRequest r = new AccountRequest();
            String code = newCode == null || newCode.isBlank() ? suggestedCode : newCode.trim();
            if (code == null || code.isBlank()) throw ApiException.badRequest(I18n.t("Enter a code for the new account"));
            r.setCode(code);
            r.setName(newName.trim());
            r.setType(AccountType.ASSET);
            r.setDescription("Online Store: " + what);
            r.setActive(true);
            return accounts.findById(accountService.create(r).id()).orElseThrow();
        }
        if (id == null) return null;
        Account a = accounts.findById(id).orElseThrow(() -> ApiException.badRequest(I18n.t("That account does not exist")));
        if (a.getType() != AccountType.ASSET || !a.isActive()) throw ApiException.badRequest(I18n.t("Choose an active asset account"));
        return a;
    }

    @Transactional
    public View save(String providerCode, SaveRequest in) {
        String code = providerCode == null ? "" : providerCode.trim().toUpperCase(Locale.ROOT);
        PaymentProvider provider = providers.stream().filter(p -> p.code().equalsIgnoreCase(code)).findFirst().orElseThrow(() -> ApiException.notFound(I18n.t("That provider does not exist")));
        OnlineProviderAccounts.Pair current = resolver.find(code);
        String[] suggested = suggestCodes();
        // an existing account is checked before anything is created
        if (in.clearingAccountId() != null && in.payoutAccountId() != null && in.clearingAccountId().equals(in.payoutAccountId())) {
            throw ApiException.badRequest(I18n.t("The clearing account and the payout account must be different"));
        }
        Account clearing = choose(in.clearingAccountId(), in.newClearingName(), in.newClearingCode(), suggested[0], "what " + provider.label() + " still owes the shop");
        Account payout = choose(in.payoutAccountId(), in.newPayoutName(), in.newPayoutCode(), suggested[1], "where " + provider.label() + " pays out to");
        if (clearing != null && payout != null && clearing.getId().equals(payout.getId())) throw ApiException.badRequest(I18n.t("The clearing account and the payout account must be different"));
        if (clearing != null) {
            String k = clearing.getWellKnownKey();
            if (k != null && !k.startsWith("ONLINE_CLEARING_")) throw ApiException.badRequest(I18n.t("Choose an ordinary account for what the provider owes, or create a new one"));
            for (PaymentProvider other : providers) {
                if (other.code().equalsIgnoreCase(code)) continue;
                Account oc = resolver.find(other.code()).clearing();
                if (oc != null && oc.getId().equals(clearing.getId())) throw ApiException.badRequest(I18n.t("That account is already used by {0}", other.label()));
            }
            if (k != null && ONLINE_SHARED.contains(k)) throw ApiException.badRequest(I18n.t("That account is shared by the providers that have none of their own"));
        }
        if (payout != null) {
            String k = payout.getWellKnownKey();
            if (k != null && !k.equals(AccountService.CASH) && !k.startsWith("WALLET_") && !k.equals(AccountService.BANK_CARD)) {
                throw ApiException.badRequest(I18n.t("Choose a wallet or bank account for the payouts, or create a new one"));
            }
        }
        // the old clearing account may not be left while the provider still owes money on it
        Long currentId = current.clearing() == null ? null : current.clearing().getId();
        Long newId = clearing == null ? null : clearing.getId();
        if (currentId != null && !currentId.equals(newId)) {
            BigDecimal owed = balance(current.clearing());
            if (owed.signum() != 0) throw ApiException.badRequest(I18n.t("{0} still owes you {1}. Record its payouts first, then change the account.", provider.label(), OnlineStoreService.money(owed)));
        }
        jdbc.update("insert into online_provider_account(provider_code, clearing_account_id, payout_account_id, created_at, updated_at) values (?, ?, ?, now(), now()) "
                + "on conflict (provider_code) do update set clearing_account_id = excluded.clearing_account_id, payout_account_id = excluded.payout_account_id, updated_at = now()",
                code, newId, payout == null ? null : payout.getId());
        auditLogService.record("ONLINE_PROVIDER_ACCOUNTS", "Accounts of " + provider.label() + ": owed " + (clearing == null ? "none" : clearing.getCode()) + ", payouts " + (payout == null ? "Cash and Bank" : payout.getCode()), "OnlineProvider", null);
        return view();
    }

    private static final Set<String> ONLINE_SHARED = Set.of(AccountService.ONLINE_CLEARING_MOBILE, AccountService.ONLINE_CLEARING_CARD);
}
