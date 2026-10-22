package com.posapp.giftcards;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.giftcards.GiftCardDtos.*;
import com.posapp.order.OrderRepository;
import com.posapp.order.Payment;
import com.posapp.order.SaleOrder;
import com.posapp.order.Tenders;
import com.posapp.paymentmethod.PaymentMethodService;
import com.posapp.register.CashierSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gift cards. A card is sold for money, and that money is owed back in goods: it goes to Gift Card Liability, not to sales. When the card is spent at the till the liability turns into
 * sales (see AccountingService.postPosSale). A card can be topped up, blocked, given an expiry date, and once expired its leftover balance can be released into income.
 * A sale is paid from a card by a payment whose method is {@link Tenders#GIFT_CARD}; the card is locked while it is spent so the same money cannot be used twice.
 */
@Service
public class GiftCardService {

    public static final String COMPLIMENTARY = "COMPLIMENTARY";
    private static final Pattern CUSTOM_CODE = Pattern.compile("[A-Z0-9-]{6,40}");
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GiftCardRepository cards;
    private final GiftCardTxnRepository txns;
    private final CustomerRepository customers;
    private final PaymentMethodService paymentMethods;
    private final AccountingService accounting;
    private final AuditLogService audit;
    private final OrderRepository orders;

    public GiftCardService(GiftCardRepository cards, GiftCardTxnRepository txns, CustomerRepository customers, PaymentMethodService paymentMethods,
                           AccountingService accounting, AuditLogService audit, OrderRepository orders) {
        this.cards = cards;
        this.txns = txns;
        this.customers = customers;
        this.paymentMethods = paymentMethods;
        this.accounting = accounting;
        this.audit = audit;
        this.orders = orders;
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<Row> list(String q, String filter) {
        LocalDate today = LocalDate.now();
        List<GiftCard> all = cards.findAllByOrderByIdDesc();
        Map<Long, String> names = customerNames(all);
        String needle = q == null ? "" : q.trim().toLowerCase();
        String f = filter == null ? "ALL" : filter.trim().toUpperCase();
        List<Row> out = new ArrayList<>();
        for (GiftCard c : all) {
            boolean expired = c.isExpired(today);
            switch (f) {
                case "WITH_BALANCE" -> { if (c.getBalance().signum() <= 0) continue; }
                case "EMPTY" -> { if (c.getBalance().signum() > 0) continue; }
                case "BLOCKED" -> { if (c.getStatus() != GiftCardStatus.BLOCKED) continue; }
                case "EXPIRED" -> { if (!expired) continue; }
                case "USABLE" -> { if (!c.isUsable(today)) continue; }
                default -> { }
            }
            if (!needle.isEmpty()) {
                String cust = c.getCustomerId() == null ? "" : names.getOrDefault(c.getCustomerId(), "");
                String hay = (c.getCode() + " " + (c.getRecipientName() == null ? "" : c.getRecipientName()) + " " + cust).toLowerCase();
                if (!hay.contains(needle)) continue;
            }
            out.add(row(c, names.get(c.getCustomerId()), today));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        LocalDate today = LocalDate.now();
        List<GiftCard> all = cards.findAll();
        long withBalance = 0, blocked = 0, expiredWithBalance = 0;
        BigDecimal expiredBalance = BigDecimal.ZERO;
        for (GiftCard c : all) {
            if (c.getBalance().signum() > 0) withBalance++;
            if (c.getStatus() == GiftCardStatus.BLOCKED) blocked++;
            if (c.isExpired(today) && c.getBalance().signum() > 0) {
                expiredWithBalance++;
                expiredBalance = expiredBalance.add(c.getBalance());
            }
        }
        return new Summary(money(cards.totalBalance()), all.size(), withBalance, blocked, expiredWithBalance, money(expiredBalance));
    }

    @Transactional(readOnly = true)
    public Detail detail(Long id) {
        GiftCard c = orThrow(id);
        List<TxnRow> history = new ArrayList<>();
        for (GiftCardTxn t : txns.findByGiftCardIdOrderByIdDesc(id)) {
            String inv = t.getOrderId() == null ? null : orders.findById(t.getOrderId()).map(SaleOrder::getInvoiceNumber).orElse(null);
            history.add(new TxnRow(t.getId(), t.getType().name(), t.getAmount(), t.getBalanceAfter(), t.getPaymentMethod(), t.getOrderId(), inv, t.getNote(), t.getCreatedAt()));
        }
        return new Detail(row(c, nameOf(c.getCustomerId()), LocalDate.now()), history);
    }

    /** What the till shows when a code is typed: the card and what is left on it. */
    @Transactional(readOnly = true)
    public Row lookup(String code) {
        GiftCard c = byCodeOrThrow(code);
        return row(c, nameOf(c.getCustomerId()), LocalDate.now());
    }

    // ------------------------------------------------------------------ selling

    /**
     * Sells a card (or issues one free). {@code cashierSession} is the till session the money came in at (null for a back-office sale). {@code allowComplimentary}: only an admin may give one away.
     */
    @Transactional
    public Row sell(SellRequest r, CashierSession cashierSession, boolean allowComplimentary) {
        if (r == null) throw ApiException.badRequest("Nothing to sell.");
        BigDecimal amount = checkAmount(r.amount());
        String method = checkMethod(r.paymentMethod(), allowComplimentary);
        LocalDate expires = r.expiresOn();
        if (expires != null && !expires.isAfter(LocalDate.now())) throw ApiException.badRequest("The expiry date must be after today.");
        Long customerId = null;
        if (r.customerId() != null) {
            customerId = customers.findById(r.customerId()).orElseThrow(() -> ApiException.notFound("Customer not found: " + r.customerId())).getId();
        }
        String code;
        if (r.code() != null && !r.code().isBlank()) {
            code = r.code().trim().toUpperCase();
            if (!CUSTOM_CODE.matcher(code).matches()) throw ApiException.badRequest("A card code is 6 to 40 letters, digits or '-'.");
            if (cards.findByCodeIgnoreCase(code).isPresent()) throw ApiException.conflict("The code " + code + " is already on another gift card.");
        } else {
            code = newCode();
        }
        String recipient = r.recipientName() == null || r.recipientName().isBlank() ? null : r.recipientName().trim();
        if (recipient != null && recipient.length() > 120) throw ApiException.badRequest("The recipient's name can be at most 120 characters.");
        GiftCard c = new GiftCard();
        c.setCode(code);
        c.setInitialAmount(amount);
        c.setBalance(amount);
        c.setCustomerId(customerId);
        c.setRecipientName(recipient);
        c.setExpiresOn(expires);
        c.setNote(trimNote(r.note()));
        c = cards.save(c);
        GiftCardTxn t = txn(c, GiftCardTxnType.SOLD, amount, method, cashierSession, null, null);
        accounting.postGiftCardSold(t.getId(), c.getCode(), amount, !COMPLIMENTARY.equals(method));
        audit.record("GIFT_CARD_SOLD", (COMPLIMENTARY.equals(method) ? "Issued free gift card " : "Sold gift card ") + c.getCode() + " for " + amount, "GiftCard", c.getId());
        return row(c, nameOf(customerId), LocalDate.now());
    }

    @Transactional
    public Row topUp(Long id, TopUpRequest r, CashierSession cashierSession, boolean allowComplimentary) {
        if (r == null) throw ApiException.badRequest("Nothing to add.");
        BigDecimal amount = checkAmount(r.amount());
        String method = checkMethod(r.paymentMethod(), allowComplimentary);
        GiftCard c = cards.lockById(id).orElseThrow(() -> ApiException.notFound("Gift card not found: " + id));
        if (c.getStatus() == GiftCardStatus.BLOCKED) throw ApiException.badRequest("This card is blocked. Unblock it first.");
        if (c.isExpired(LocalDate.now())) throw ApiException.badRequest("This card has expired. Set a new expiry date first.");
        c.setBalance(c.getBalance().add(amount));
        cards.save(c);
        GiftCardTxn t = txn(c, GiftCardTxnType.TOPUP, amount, method, cashierSession, null, trimNote(r.note()));
        accounting.postGiftCardSold(t.getId(), c.getCode(), amount, !COMPLIMENTARY.equals(method));
        audit.record("GIFT_CARD_TOPPED_UP", "Added " + amount + " to gift card " + c.getCode(), "GiftCard", c.getId());
        return row(c, nameOf(c.getCustomerId()), LocalDate.now());
    }

    @Transactional
    public Row setBlocked(Long id, boolean blocked) {
        GiftCard c = cards.lockById(id).orElseThrow(() -> ApiException.notFound("Gift card not found: " + id));
        c.setStatus(blocked ? GiftCardStatus.BLOCKED : GiftCardStatus.ACTIVE);
        cards.save(c);
        audit.record(blocked ? "GIFT_CARD_BLOCKED" : "GIFT_CARD_UNBLOCKED", (blocked ? "Blocked" : "Unblocked") + " gift card " + c.getCode(), "GiftCard", c.getId());
        return row(c, nameOf(c.getCustomerId()), LocalDate.now());
    }

    @Transactional
    public Row setExpiry(Long id, LocalDate expiresOn) {
        if (expiresOn != null && !expiresOn.isAfter(LocalDate.now())) throw ApiException.badRequest("The expiry date must be after today. Leave it empty for a card that never expires.");
        GiftCard c = cards.lockById(id).orElseThrow(() -> ApiException.notFound("Gift card not found: " + id));
        c.setExpiresOn(expiresOn);
        cards.save(c);
        audit.record("GIFT_CARD_EXPIRY_CHANGED", "Gift card " + c.getCode() + " now " + (expiresOn == null ? "never expires" : "expires on " + expiresOn), "GiftCard", c.getId());
        return row(c, nameOf(c.getCustomerId()), LocalDate.now());
    }

    /** Moves the balance left on every expired card out of what the store owes and into income. One entry in the books for the lot. */
    @Transactional
    public Released releaseExpired() {
        LocalDate today = LocalDate.now();
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        Long firstTxn = null;
        for (GiftCard listed : cards.findAll()) {
            if (!listed.isExpired(today) || listed.getBalance().signum() <= 0) continue;
            GiftCard c = cards.lockById(listed.getId()).orElse(null);
            if (c == null || !c.isExpired(today) || c.getBalance().signum() <= 0) continue;
            BigDecimal left = c.getBalance();
            c.setBalance(BigDecimal.ZERO);
            cards.save(c);
            GiftCardTxn t = txn(c, GiftCardTxnType.RELEASED, left.negate(), null, null, null, "Expired on " + c.getExpiresOn());
            if (firstTxn == null) firstTxn = t.getId();
            total = total.add(left);
            count++;
        }
        if (count > 0) {
            accounting.postGiftCardReleased(firstTxn, total);
            audit.record("GIFT_CARD_RELEASED", "Released " + total + " from " + count + " expired gift card(s)", "GiftCard", null);
        }
        return new Released(count, money(total));
    }

    // ------------------------------------------------------------------ paying with a card

    /** Checks a card can pay this amount (used while a sale is being put together); the money is taken later, under a lock, by {@link #redeemForOrder}. */
    @Transactional(readOnly = true)
    public GiftCard checkTender(String code, BigDecimal amount) {
        if (code == null || code.isBlank()) throw ApiException.badRequest("Enter the gift card's code.");
        GiftCard c = byCodeOrThrow(code);
        assertUsable(c, amount);
        return c;
    }

    /** Takes the gift card payments of a saved sale off their cards. Throws (and so undoes the whole sale) if a card no longer has the money. */
    @Transactional
    public void redeemForOrder(SaleOrder saved) {
        for (Payment p : saved.getPayments()) {
            if (!Tenders.GIFT_CARD.equals(p.getMethod())) continue;
            GiftCard c = cards.lockById(p.getGiftCardId()).orElseThrow(() -> ApiException.badRequest("The gift card on this payment no longer exists."));
            assertUsable(c, p.getAmount());
            c.setBalance(c.getBalance().subtract(p.getAmount()));
            cards.save(c);
            txn(c, GiftCardTxnType.REDEEM, p.getAmount().negate(), null, null, saved.getId(), null);
        }
    }

    /**
     * Puts up to {@code wanted} back onto the gift cards that paid for {@code order}, never more than each card paid (less what already went back). A card that is blocked or
     * expired is skipped, so that part is left to the normal refund. Returns how much was put back.
     */
    @Transactional
    public BigDecimal refundToCards(SaleOrder order, BigDecimal wanted) {
        if (wanted == null || wanted.signum() <= 0) return BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        Map<Long, BigDecimal> paidByCard = new LinkedHashMap<>();
        for (Payment p : order.getPayments()) {
            if (Tenders.GIFT_CARD.equals(p.getMethod()) && p.getGiftCardId() != null) paidByCard.merge(p.getGiftCardId(), p.getAmount(), BigDecimal::add);
        }
        BigDecimal remaining = wanted;
        BigDecimal done = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : paidByCard.entrySet()) {
            if (remaining.signum() <= 0) break;
            GiftCard c = cards.lockById(e.getKey()).orElse(null);
            if (c == null || c.getStatus() == GiftCardStatus.BLOCKED || c.isExpired(today)) continue;
            BigDecimal room = e.getValue().subtract(txns.refundedForOrder(order.getId(), c.getId()));
            BigDecimal give = remaining.min(room);
            if (give.signum() <= 0) continue;
            c.setBalance(c.getBalance().add(give));
            cards.save(c);
            txn(c, GiftCardTxnType.REFUND, give, null, null, order.getId(), "Return");
            remaining = remaining.subtract(give);
            done = done.add(give);
        }
        return done;
    }

    /** The most that could still go back onto cards for this sale (what they paid, less what already went back). */
    @Transactional(readOnly = true)
    public BigDecimal refundableToCards(SaleOrder order) {
        Map<Long, BigDecimal> paidByCard = new LinkedHashMap<>();
        for (Payment p : order.getPayments()) {
            if (Tenders.GIFT_CARD.equals(p.getMethod()) && p.getGiftCardId() != null) paidByCard.merge(p.getGiftCardId(), p.getAmount(), BigDecimal::add);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : paidByCard.entrySet()) {
            total = total.add(e.getValue().subtract(txns.refundedForOrder(order.getId(), e.getKey())).max(BigDecimal.ZERO));
        }
        return total;
    }

    // ------------------------------------------------------------------ the shift

    /** Money that came in at this till by selling or topping up cards: {cash, nonCash}. Cards given free are not money. */
    @Transactional(readOnly = true)
    public BigDecimal[] soldInRegisterSession(Long sessionId) {
        return split(txns.soldInRegisterSession(sessionId));
    }

    @Transactional(readOnly = true)
    public BigDecimal[] soldInCashierSession(Long cashierSessionId) {
        return split(txns.soldInCashierSession(cashierSessionId));
    }

    private BigDecimal[] split(List<GiftCardTxn> list) {
        Set<String> cashCodes = paymentMethods.listCashCodes();
        BigDecimal cash = BigDecimal.ZERO, other = BigDecimal.ZERO;
        for (GiftCardTxn t : list) {
            if (t.getPaymentMethod() == null || COMPLIMENTARY.equals(t.getPaymentMethod())) continue;
            if (cashCodes.contains(t.getPaymentMethod())) cash = cash.add(t.getAmount());
            else other = other.add(t.getAmount());
        }
        return new BigDecimal[]{cash, other};
    }

    // ------------------------------------------------------------------ helpers

    public static String mask(String code) {
        if (code == null || code.length() <= 4) return code;
        return "****" + code.substring(code.length() - 4);
    }

    private void assertUsable(GiftCard c, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        if (c.getStatus() == GiftCardStatus.BLOCKED) throw ApiException.badRequest("The gift card " + mask(c.getCode()) + " is blocked.");
        if (c.isExpired(today)) throw ApiException.badRequest("The gift card " + mask(c.getCode()) + " expired on " + c.getExpiresOn() + ".");
        if (c.getBalance().compareTo(amount) < 0) {
            throw ApiException.badRequest("The gift card " + mask(c.getCode()) + " only has " + c.getBalance().toPlainString() + " left.");
        }
    }

    private GiftCard byCodeOrThrow(String code) {
        String c = code == null ? "" : code.trim();
        if (c.isEmpty()) throw ApiException.badRequest("Enter the gift card's code.");
        return cards.findByCodeIgnoreCase(c).orElseThrow(() -> ApiException.badRequest("No gift card has the code " + c.toUpperCase() + "."));
    }

    private GiftCard orThrow(Long id) {
        return cards.findById(id).orElseThrow(() -> ApiException.notFound("Gift card not found: " + id));
    }

    private BigDecimal checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw ApiException.badRequest("Enter an amount above zero.");
        if (amount.compareTo(MAX_AMOUNT) > 0) throw ApiException.badRequest("That amount is too large for a gift card.");
        return money(amount);
    }

    private String checkMethod(String raw, boolean allowComplimentary) {
        String m = raw == null ? "" : raw.trim();
        if (m.isEmpty()) throw ApiException.badRequest("Choose how the card is paid for.");
        if (COMPLIMENTARY.equalsIgnoreCase(m)) {
            if (!allowComplimentary) throw ApiException.forbidden("Only an administrator can give a gift card away free.");
            return COMPLIMENTARY;
        }
        String code = paymentMethods.validateActiveCodeOrThrow(m);
        if (paymentMethods.listCreditCodes().contains(code) || paymentMethods.listRoomChargeCodes().contains(code)) {
            throw ApiException.badRequest("A gift card has to be paid for now: choose cash, card or mobile money, not credit or a room charge.");
        }
        return code;
    }

    private GiftCardTxn txn(GiftCard c, GiftCardTxnType type, BigDecimal amount, String method, CashierSession cs, Long orderId, String note) {
        GiftCardTxn t = new GiftCardTxn();
        t.setGiftCardId(c.getId());
        t.setType(type);
        t.setAmount(amount);
        t.setBalanceAfter(c.getBalance());
        t.setPaymentMethod(method);
        if (cs != null) {
            t.setCashierSessionId(cs.getId());
            t.setRegisterSessionId(cs.getRegisterSession() == null ? null : cs.getRegisterSession().getId());
        }
        t.setOrderId(orderId);
        t.setNote(note);
        return txns.save(t);
    }

    private String newCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder("GC");
            for (int i = 0; i < 12; i++) {
                if (i % 4 == 0) sb.append('-');
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (cards.findByCodeIgnoreCase(code).isEmpty()) return code;
        }
        throw ApiException.conflict("Could not make a new card code. Try again.");
    }

    private static String trimNote(String note) {
        if (note == null || note.isBlank()) return null;
        String n = note.trim();
        return n.length() > 300 ? n.substring(0, 300) : n;
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<Long, String> customerNames(List<GiftCard> list) {
        Set<Long> ids = list.stream().map(GiftCard::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> out = new HashMap<>();
        for (Customer c : customers.findAllById(ids)) out.put(c.getId(), c.getName());
        return out;
    }

    private String nameOf(Long customerId) {
        return customerId == null ? null : customers.findById(customerId).map(Customer::getName).orElse(null);
    }

    private Row row(GiftCard c, String customerName, LocalDate today) {
        return new Row(c.getId(), c.getCode(), c.getInitialAmount(), c.getBalance(), c.getStatus().name(), c.getCustomerId(), customerName, c.getRecipientName(), c.getExpiresOn(),
                c.isExpired(today), c.isUsable(today), c.getNote(), c.getCreatedAt());
    }
}
