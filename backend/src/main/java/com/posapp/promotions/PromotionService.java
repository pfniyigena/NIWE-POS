package com.posapp.promotions;
import com.posapp.common.Qty;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.Category;
import com.posapp.catalog.CategoryRepository;
import com.posapp.catalog.Product;
import com.posapp.catalog.ProductRepository;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.order.OrderLine;
import com.posapp.order.SaleOrder;
import com.posapp.pricing.CustomerGroup;
import com.posapp.pricing.CustomerGroupRepository;
import com.posapp.promotions.PromotionDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Promotions and coupons. Admin side: create, change, switch off, see how often each was used. Selling side: {@link #apply} works out, for one cart, what every usable promotion takes off.
 *
 * <p>The rule, in the order it is applied: (1) each line gets the single best promotion for its product or category (a percent off, an amount off each unit, or buy X get Y free);
 * (2) then the single best whole-cart promotion is taken off what is left (a percent off, or an amount off, optionally with a minimum spend). Both come after the cashier's own
 * discount, and are stored on the line as a percent ({@code promoPercent}) so every total, tax, return and receipt keeps working from the one percent the line carries.
 * A promotion is usable when it is switched on, inside its dates, behind a coupon the cashier typed (if it has a code), for the customer's group (if it has one), and under its limits.</p>
 */
@Service
public class PromotionService {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{3,40}");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PromotionRepository promotions;
    private final PromotionUseRepository uses;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CustomerGroupRepository groups;
    private final AuditLogService audit;

    public PromotionService(PromotionRepository promotions, PromotionUseRepository uses, ProductRepository products, CategoryRepository categories,
                            CustomerGroupRepository groups, AuditLogService audit) {
        this.promotions = promotions;
        this.uses = uses;
        this.products = products;
        this.categories = categories;
        this.groups = groups;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public List<Row> list() {
        Map<Long, Object[]> totals = new HashMap<>();
        for (Object[] t : uses.totals()) totals.put(((Number) t[0]).longValue(), t);
        List<Row> out = new ArrayList<>();
        for (Promotion p : promotions.findAllByOrderByNameAsc()) out.add(row(p, totals.get(p.getId())));
        return out;
    }

    @Transactional(readOnly = true)
    public Row get(Long id) {
        Promotion p = orThrow(id);
        Object[] t = uses.totals().stream().filter(x -> ((Number) x[0]).longValue() == id).findFirst().orElse(null);
        return row(p, t);
    }

    @Transactional(readOnly = true)
    public List<UseRow> usesOf(Long id, com.posapp.order.OrderRepository orders) {
        orThrow(id);
        List<UseRow> out = new ArrayList<>();
        for (PromotionUse u : uses.findByPromotionIdOrderByIdDesc(id)) {
            SaleOrder o = orders.findById(u.getOrderId()).orElse(null);
            out.add(new UseRow(u.getOrderId(), o == null ? null : o.getInvoiceNumber(), o == null || o.getCustomer() == null ? null : o.getCustomer().getName(), u.getAmount(), u.getCreatedAt()));
        }
        return out;
    }

    @Transactional
    public Row create(Request r) {
        Promotion p = new Promotion();
        fill(p, r);
        p = promotions.save(p);
        audit.record("PROMOTION_CREATED", "Created promotion " + p.getName(), "Promotion", p.getId());
        return row(p, null);
    }

    @Transactional
    public Row update(Long id, Request r) {
        Promotion p = orThrow(id);
        fill(p, r);
        p = promotions.save(p);
        audit.record("PROMOTION_UPDATED", "Updated promotion " + p.getName(), "Promotion", p.getId());
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        Promotion p = orThrow(id);
        long n = uses.countByPromotionId(id);
        if (n > 0) throw ApiException.conflict("'" + p.getName() + "' has been used on " + n + " sale(s), so it cannot be deleted. Switch it off instead.");
        promotions.delete(p);
        audit.record("PROMOTION_DELETED", "Deleted promotion " + p.getName(), "Promotion", id);
    }

    private Promotion orThrow(Long id) {
        return promotions.findById(id).orElseThrow(() -> ApiException.notFound("Promotion not found: " + id));
    }

    private void fill(Promotion p, Request r) {
        if (r == null) throw ApiException.badRequest("Nothing to save.");
        String name = r.name() == null ? "" : r.name().trim();
        if (name.isEmpty()) throw ApiException.badRequest("Give the promotion a name.");
        if (name.length() > 120) throw ApiException.badRequest("The name can be at most 120 characters.");
        Optional<Promotion> same = promotions.findByNameIgnoreCase(name);
        if (same.isPresent() && !same.get().getId().equals(p.getId())) throw ApiException.conflict("There is already a promotion called '" + name + "'.");

        PromotionType type;
        PromotionScope scope;
        try {
            type = PromotionType.valueOf(String.valueOf(r.type()).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Choose what the promotion does: percent off, amount off, or buy X get Y free.");
        }
        try {
            scope = PromotionScope.valueOf(String.valueOf(r.scope()).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Choose what it applies to: a product, a category or the whole cart.");
        }

        Long targetId = null;
        if (scope == PromotionScope.CART) {
            if (type == PromotionType.BUY_X_GET_Y) throw ApiException.badRequest("Buy X get Y free works on a product or a category, not on the whole cart.");
        } else {
            if (r.targetId() == null) throw ApiException.badRequest(scope == PromotionScope.PRODUCT ? "Choose the product." : "Choose the category.");
            if (scope == PromotionScope.PRODUCT) products.findById(r.targetId()).orElseThrow(() -> ApiException.notFound("Product not found: " + r.targetId()));
            else categories.findById(r.targetId()).orElseThrow(() -> ApiException.notFound("Category not found: " + r.targetId()));
            targetId = r.targetId();
        }

        BigDecimal value = null;
        BigDecimal buy = null, get = null;
        if (type == PromotionType.PERCENT_OFF) {
            if (r.value() == null || r.value().signum() <= 0 || r.value().compareTo(HUNDRED) > 0) throw ApiException.badRequest("The percent must be above 0 and at most 100.");
            value = r.value().setScale(2, RoundingMode.HALF_UP);
        } else if (type == PromotionType.AMOUNT_OFF) {
            if (r.value() == null || r.value().signum() <= 0) throw ApiException.badRequest("The amount off must be above 0.");
            value = r.value().setScale(2, RoundingMode.HALF_UP);
        } else {
            if (r.buyQuantity() == null || r.buyQuantity().compareTo(BigDecimal.ONE) < 0) throw ApiException.badRequest("Say how many must be bought (at least 1).");
            if (r.freeQuantity() == null || r.freeQuantity().compareTo(BigDecimal.ONE) < 0) throw ApiException.badRequest("Say how many come free (at least 1).");
            buy = r.buyQuantity();
            get = r.freeQuantity();
        }

        BigDecimal minQty = r.minQuantity() == null ? BigDecimal.ONE : r.minQuantity();
        if (minQty.compareTo(BigDecimal.ONE) < 0) throw ApiException.badRequest("The minimum quantity must be at least 1.");
        BigDecimal minSpend = r.minSpend();
        if (minSpend != null && minSpend.signum() == 0) minSpend = null;
        if (minSpend != null) {
            if (minSpend.signum() < 0) throw ApiException.badRequest("The minimum spend cannot be negative.");
            if (scope != PromotionScope.CART) throw ApiException.badRequest("A minimum spend applies to whole-cart promotions only. Use a minimum quantity for a product or category.");
            minSpend = minSpend.setScale(2, RoundingMode.HALF_UP);
        }
        if (r.validFrom() != null && r.validTo() != null && r.validTo().isBefore(r.validFrom())) throw ApiException.badRequest("'Valid to' cannot be before 'valid from'.");

        String code = r.couponCode() == null ? "" : r.couponCode().trim().toUpperCase();
        if (code.isEmpty()) {
            code = null;
        } else {
            if (!CODE.matcher(code).matches()) throw ApiException.badRequest("A coupon code is 3 to 40 letters, digits, '-' or '_'.");
            Optional<Promotion> sameCode = promotions.findByCouponCodeIgnoreCase(code);
            if (sameCode.isPresent() && !sameCode.get().getId().equals(p.getId())) throw ApiException.conflict("The coupon code " + code + " is already used by '" + sameCode.get().getName() + "'.");
        }
        if (r.maxUses() != null && r.maxUses() < 1) throw ApiException.badRequest("The most times it can be used must be at least 1.");
        if (r.maxUsesPerCustomer() != null && r.maxUsesPerCustomer() < 1) throw ApiException.badRequest("The most times one customer can use it must be at least 1.");
        if (r.customerGroupId() != null) groups.findById(r.customerGroupId()).orElseThrow(() -> ApiException.notFound("Customer group not found: " + r.customerGroupId()));

        p.setName(name);
        p.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        p.setType(type);
        p.setScope(scope);
        p.setTargetId(targetId);
        p.setValue(value);
        p.setBuyQuantity(buy);
        p.setFreeQuantity(get);
        p.setMinQuantity(minQty);
        p.setMinSpend(minSpend);
        p.setValidFrom(r.validFrom());
        p.setValidTo(r.validTo());
        p.setCouponCode(code);
        p.setMaxUses(r.maxUses());
        p.setMaxUsesPerCustomer(r.maxUsesPerCustomer());
        p.setCustomerGroupId(r.customerGroupId());
        p.setActive(r.active() == null || r.active());
    }

    private Row row(Promotion p, Object[] totals) {
        String targetName = null;
        if (p.getScope() == PromotionScope.PRODUCT && p.getTargetId() != null) targetName = products.findById(p.getTargetId()).map(Product::getName).orElse("(deleted product)");
        if (p.getScope() == PromotionScope.CATEGORY && p.getTargetId() != null) targetName = categories.findById(p.getTargetId()).map(Category::getName).orElse("(deleted category)");
        String groupName = p.getCustomerGroupId() == null ? null : groups.findById(p.getCustomerGroupId()).map(CustomerGroup::getName).orElse(null);
        long n = totals == null ? 0 : ((Number) totals[1]).longValue();
        BigDecimal given = totals == null ? BigDecimal.ZERO : ((BigDecimal) totals[2]).setScale(2, RoundingMode.HALF_UP);
        return new Row(p.getId(), p.getName(), p.getDescription(), p.getType().name(), p.getScope().name(), p.getTargetId(), targetName, p.getValue(), p.getBuyQuantity(), p.getFreeQuantity(),
                p.getMinQuantity(), p.getMinSpend(), p.getValidFrom(), p.getValidTo(), p.getCouponCode(), p.getMaxUses(), p.getMaxUsesPerCustomer(), p.getCustomerGroupId(), groupName,
                p.isActive(), p.isActive() && p.isInDates(LocalDate.now()), n, given, describe(p, targetName));
    }

    private static String describe(Promotion p, String targetName) {
        String what = p.getScope() == PromotionScope.CART ? "the whole cart" : targetName;
        return switch (p.getType()) {
            case PERCENT_OFF -> p.getValue().stripTrailingZeros().toPlainString() + "% off " + what;
            case AMOUNT_OFF -> p.getValue().stripTrailingZeros().toPlainString() + " off " + (p.getScope() == PromotionScope.CART ? what : "each " + what);
            case BUY_X_GET_Y -> "Buy " + Qty.str(p.getBuyQuantity()) + " get " + Qty.str(p.getFreeQuantity()) + " free: " + what;
        };
    }

    // ---------------------------------------------------------------- selling

    /** The coupon codes typed on this cart. */
    public static List<String> couponList(SaleOrder order) {
        String s = order.getCouponCodes();
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String c : s.split(",")) if (!c.isBlank()) out.add(c.trim());
        return out;
    }

    /** What promotions took off this cart, as stored by the last {@link #apply}. */
    public static List<Applied> appliedList(SaleOrder order) {
        String s = order.getPromotionSummary();
        if (s == null || s.isBlank()) return List.of();
        List<Applied> out = new ArrayList<>();
        for (String line : s.split("\n")) {
            String[] f = line.split("\\|", -1);
            if (f.length < 4) continue;
            try {
                out.add(new Applied(Long.valueOf(f[0]), f[1], f[2].isEmpty() ? null : f[2], new BigDecimal(f[3])));
            } catch (NumberFormatException ignored) {
                // a damaged line is skipped, never shown wrong
            }
        }
        return out;
    }

    /** Adds a coupon to the cart, or says exactly why it cannot be used. Works the cart's promotions out again. */
    @Transactional
    public void addCoupon(SaleOrder order, String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase();
        if (code.isEmpty()) throw ApiException.badRequest("Type the coupon code.");
        Promotion p = promotions.findByCouponCodeIgnoreCase(code).orElseThrow(() -> ApiException.badRequest("The coupon '" + code + "' was not found."));
        if (!p.isActive()) throw ApiException.badRequest("The coupon " + code + " is switched off.");
        LocalDate today = LocalDate.now();
        if (p.getValidFrom() != null && today.isBefore(p.getValidFrom())) throw ApiException.badRequest("The coupon " + code + " starts on " + p.getValidFrom() + ".");
        if (p.getValidTo() != null && today.isAfter(p.getValidTo())) throw ApiException.badRequest("The coupon " + code + " ended on " + p.getValidTo() + ".");
        Customer customer = order.getCustomer();
        if (p.getCustomerGroupId() != null) {
            String groupName = groups.findById(p.getCustomerGroupId()).map(CustomerGroup::getName).orElse("a customer group");
            if (customer == null || customer.isWalkIn()) throw ApiException.badRequest("The coupon " + code + " is for customers in '" + groupName + "'. Choose the customer first.");
            if (!p.getCustomerGroupId().equals(customer.getCustomerGroupId())) throw ApiException.badRequest("The coupon " + code + " is only for customers in '" + groupName + "'.");
        }
        if (limitReached(p, customer, true)) throw ApiException.badRequest("The coupon " + code + " has already been used the most times it can be.");
        if (limitReached(p, customer, false)) throw ApiException.badRequest("This customer has already used the coupon " + code + " the most times they can.");
        List<String> have = new ArrayList<>(couponList(order));
        if (have.contains(code)) throw ApiException.badRequest("The coupon " + code + " is already on this cart.");
        have.add(code);
        order.setCouponCodes(String.join(",", have));
        apply(order);
    }

    @Transactional
    public void removeCoupon(SaleOrder order, String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase();
        List<String> have = new ArrayList<>(couponList(order));
        if (!have.remove(code)) throw ApiException.notFound("The coupon " + code + " is not on this cart.");
        order.setCouponCodes(have.isEmpty() ? null : String.join(",", have));
        apply(order);
    }

    private boolean limitReached(Promotion p, Customer customer, boolean total) {
        if (total) return p.getMaxUses() != null && uses.countByPromotionId(p.getId()) >= p.getMaxUses();
        if (p.getMaxUsesPerCustomer() == null || customer == null || customer.isWalkIn()) return false;
        return uses.countByPromotionIdAndCustomerId(p.getId(), customer.getId()) >= p.getMaxUsesPerCustomer();
    }

    private boolean usable(Promotion p, Customer customer, Set<String> coupons, LocalDate today) {
        if (!p.isActive() || !p.isInDates(today)) return false;
        if (p.getCouponCode() != null && !coupons.contains(p.getCouponCode().toUpperCase())) return false;
        if (p.getCustomerGroupId() != null) {
            if (customer == null || customer.isWalkIn() || !p.getCustomerGroupId().equals(customer.getCustomerGroupId())) return false;
        }
        return !limitReached(p, customer, true) && !limitReached(p, customer, false);
    }

    private static boolean matches(OrderLine line, Promotion p) {
        Product product = line.getProduct();
        if (product == null || p.getTargetId() == null) return false;
        if (p.getScope() == PromotionScope.PRODUCT) return p.getTargetId().equals(product.getId());
        return product.getCategory() != null && p.getTargetId().equals(product.getCategory().getId());
    }

    /** What the promotion takes off this line, in money, given the line's amount after the cashier's own discount. */
    private static BigDecimal lineSaving(Promotion p, OrderLine line, BigDecimal base) {
        BigDecimal qty = line.getQuantity();
        if (qty.compareTo(p.getMinQuantity()) < 0) return BigDecimal.ZERO;
        BigDecimal saving;
        switch (p.getType()) {
            case PERCENT_OFF -> saving = base.multiply(p.getValue()).divide(HUNDRED, 8, RoundingMode.HALF_UP);
            case AMOUNT_OFF -> saving = p.getValue().multiply(qty);
            default -> {
                BigDecimal group = p.getBuyQuantity().add(p.getFreeQuantity());
                BigDecimal free = (qty.divide(group, 0, java.math.RoundingMode.DOWN)).multiply(p.getFreeQuantity()); // whole groups only (buy 2 get 1: 2.5 kg earns no free unit, 4 earns one)
                BigDecimal perUnit = base.divide(qty, 8, RoundingMode.HALF_UP);
                saving = perUnit.multiply(free);
            }
        }
        return saving.min(base).max(BigDecimal.ZERO);
    }

    /**
     * Works out what promotions take off this cart and stores it on the lines (promoPercent, promoNote) and on the cart (summary). Safe to call after every change to the cart: it
     * starts by clearing what it set last time.
     */
    @Transactional
    public void apply(SaleOrder order) {
        List<OrderLine> lines = order.getLines();
        for (OrderLine l : lines) {
            l.setPromoPercent(BigDecimal.ZERO);
            l.setPromoNote(null);
        }
        order.setPromotionSummary(null);
        if (lines.isEmpty()) return;

        LocalDate today = LocalDate.now();
        Customer customer = order.getCustomer();
        Set<String> coupons = new HashSet<>();
        for (String c : couponList(order)) coupons.add(c.toUpperCase());
        List<Promotion> usable = new ArrayList<>();
        for (Promotion p : promotions.findAll()) if (usable(p, customer, coupons, today)) usable.add(p);
        if (usable.isEmpty()) return;

        Map<Long, BigDecimal> given = new LinkedHashMap<>();
        Map<Long, Promotion> byId = new HashMap<>();
        int n = lines.size();
        BigDecimal[] base = new BigDecimal[n];
        BigDecimal[] after1 = new BigDecimal[n];
        BigDecimal[] p1 = new BigDecimal[n];          // stage 1 as a fraction 0..1
        String[] note = new String[n];
        BigDecimal preSpend = BigDecimal.ZERO;
        BigDecimal totalQty = BigDecimal.ZERO;

        for (int i = 0; i < n; i++) {
            OrderLine l = lines.get(i);
            BigDecimal d = l.getDiscountPercent() == null ? BigDecimal.ZERO : l.getDiscountPercent();
            base[i] = l.getUnitPrice().multiply(l.getQuantity()).multiply(HUNDRED.subtract(d)).divide(HUNDRED, 8, RoundingMode.HALF_UP);
            after1[i] = base[i];
            p1[i] = BigDecimal.ZERO;
            preSpend = preSpend.add(base[i]);
            totalQty = totalQty.add(l.getQuantity());
            if (base[i].signum() <= 0) continue;
            Promotion best = null;
            BigDecimal bestSaving = BigDecimal.ZERO;
            for (Promotion p : usable) {
                if (p.getScope() == PromotionScope.CART || !matches(l, p)) continue;
                BigDecimal s = lineSaving(p, l, base[i]);
                if (s.compareTo(bestSaving) > 0) {
                    best = p;
                    bestSaving = s;
                }
            }
            if (best != null) {
                p1[i] = bestSaving.divide(base[i], 12, RoundingMode.HALF_UP);
                after1[i] = base[i].subtract(bestSaving);
                note[i] = best.getName();
                given.merge(best.getId(), bestSaving, BigDecimal::add);
                byId.put(best.getId(), best);
            }
        }

        BigDecimal stage1 = Arrays.stream(after1).reduce(BigDecimal.ZERO, BigDecimal::add);
        Promotion cart = null;
        BigDecimal cartSaving = BigDecimal.ZERO;
        if (stage1.signum() > 0) {
            for (Promotion p : usable) {
                if (p.getScope() != PromotionScope.CART) continue;
                if (totalQty.compareTo(p.getMinQuantity()) < 0) continue;
                if (p.getMinSpend() != null && preSpend.compareTo(p.getMinSpend()) < 0) continue;
                BigDecimal s = p.getType() == PromotionType.PERCENT_OFF ? stage1.multiply(p.getValue()).divide(HUNDRED, 8, RoundingMode.HALF_UP) : p.getValue();
                s = s.min(stage1);
                if (s.compareTo(cartSaving) > 0) {
                    cart = p;
                    cartSaving = s;
                }
            }
        }
        BigDecimal q = BigDecimal.ZERO;
        if (cart != null) {
            q = cartSaving.divide(stage1, 12, RoundingMode.HALF_UP);
            given.merge(cart.getId(), cartSaving, BigDecimal::add);
            byId.put(cart.getId(), cart);
        }

        for (int i = 0; i < n; i++) {
            BigDecimal keep = BigDecimal.ONE.subtract(p1[i]).multiply(BigDecimal.ONE.subtract(q));
            BigDecimal percent = BigDecimal.ONE.subtract(keep).multiply(HUNDRED).setScale(10, RoundingMode.HALF_UP);
            if (percent.signum() == 0) continue;
            lines.get(i).setPromoPercent(percent);
            String text = note[i];
            if (cart != null && after1[i].signum() > 0) text = text == null ? cart.getName() : text + " + " + cart.getName();
            if (text != null && text.length() > 290) text = text.substring(0, 290);
            lines.get(i).setPromoNote(text);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long, BigDecimal> e : given.entrySet()) {
            Promotion p = byId.get(e.getKey());
            if (sb.length() > 0) sb.append('\n');
            sb.append(p.getId()).append('|').append(p.getName().replace('|', '/').replace('\n', ' ')).append('|')
                    .append(p.getCouponCode() == null ? "" : p.getCouponCode()).append('|').append(e.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        order.setPromotionSummary(sb.length() == 0 ? null : sb.toString());
    }

    /** Writes down, once the sale is saved, which promotions it used and how much each took off. */
    @Transactional
    public void recordUses(SaleOrder saved) {
        for (Applied a : appliedList(saved)) {
            if (a.amount().signum() <= 0) continue;
            PromotionUse u = new PromotionUse();
            u.setPromotionId(a.promotionId());
            u.setOrderId(saved.getId());
            u.setCustomerId(saved.getCustomer() == null ? null : saved.getCustomer().getId());
            u.setAmount(a.amount());
            uses.save(u);
        }
    }
}
