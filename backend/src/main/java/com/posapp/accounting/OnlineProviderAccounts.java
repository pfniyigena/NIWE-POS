package com.posapp.accounting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v1.76.0: which accounts each online payment provider uses. Staff choose them in Online Store > Payments > Accounts by provider (table {@code online_provider_account}):
 * the clearing account (what the provider still owes the shop) and the payout account (the wallet or bank account its payouts arrive in). MTN and Airtel use the accounts that
 * v1.75.0 created until staff change them. A provider with no accounts is booked to the shared accounts: Card Clearing for card payments and Mobile Money Clearing for the rest,
 * and its payouts arrive in Cash and Bank.
 *
 * <p>The ledger channel of a payment is the provider's code when it has a clearing account, otherwise CARD or MOBILE_MONEY. It is stored on the order when the payment is
 * confirmed ({@code online_order.clearing_channel}), so later steps use the same channel.
 */
@Service
public class OnlineProviderAccounts {

    public static final String CARD = "CARD";
    public static final String MOBILE_MONEY = "MOBILE_MONEY";

    /** Providers that had accounts of their own before staff could choose them (v1.75.0): provider code -> {clearing key, payout key}. */
    private static final Map<String, String[]> DEFAULTS = Map.of(
            "MTN_MOMO", new String[]{AccountService.ONLINE_CLEARING_MTN, AccountService.WALLET_MTN},
            "AIRTEL_MONEY", new String[]{AccountService.ONLINE_CLEARING_AIRTEL, AccountService.WALLET_AIRTEL});

    /** The accounts of one provider; either may be null. source: SET (chosen by staff), DEFAULT (v1.75.0) or NONE. */
    public record Pair(Account clearing, Account payout, String source) {
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AccountRepository accounts;

    /** The accounts of a provider code (upper case), or a pair of nulls with source NONE. */
    public Pair find(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) return new Pair(null, null, "NONE");
        String code = providerCode.trim().toUpperCase(Locale.ROOT);
        try {
            List<Long[]> rows = jdbc.query("select clearing_account_id, payout_account_id from online_provider_account where provider_code = ?",
                    (rs, i) -> new Long[]{(Long) rs.getObject(1), (Long) rs.getObject(2)}, code);
            if (!rows.isEmpty()) {
                Long[] r = rows.get(0);
                return new Pair(r[0] == null ? null : accounts.findById(r[0]).orElse(null), r[1] == null ? null : accounts.findById(r[1]).orElse(null), "SET");
            }
        } catch (DataAccessException e) {
            // no table yet: use the defaults
        }
        String[] keys = DEFAULTS.get(code);
        if (keys == null) return new Pair(null, null, "NONE");
        return new Pair(accounts.findByWellKnownKey(keys[0]).orElse(null), accounts.findByWellKnownKey(keys[1]).orElse(null), "DEFAULT");
    }

    /** The ledger channel for a payment taken by {@code provider} with {@code method}. */
    public String channelFor(String provider, String method) {
        Pair p = find(provider);
        if (p.clearing() != null) return provider.trim().toUpperCase(Locale.ROOT);
        return CARD.equalsIgnoreCase(method) ? CARD : MOBILE_MONEY;
    }

    /** True for CARD and MOBILE_MONEY, the shared channels that have no provider behind them. */
    public static boolean shared(String channel) {
        return channel == null || CARD.equalsIgnoreCase(channel) || MOBILE_MONEY.equalsIgnoreCase(channel);
    }

    /** The clearing account of a provider channel, or null for a shared channel (and for a provider whose clearing account was removed). */
    public Account clearingOf(String channel) {
        return shared(channel) ? null : find(channel).clearing();
    }

    /** The account a channel's payouts arrive in, or null (Cash and Bank). */
    public Account payoutOf(String channel) {
        return shared(channel) ? null : find(channel).payout();
    }
}
