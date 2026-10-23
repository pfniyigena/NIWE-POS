package com.posapp.pricing;
import com.posapp.common.Qty;

import com.posapp.audit.AuditLogService;
import com.posapp.catalog.Product;
import com.posapp.catalog.ProductRepository;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.pricing.PricingDtos.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Price lists and customer groups, and the one rule that turns them into a price: a customer in a group whose price list is switched on and inside its dates pays that list's
 * price for the product (the entry with the largest quantity step not above what is bought), or the normal price moved by the list's adjustment when the product has no entry.
 * A customer with no group, or a group with no usable list, pays the normal price. Used by the till, and by invoices, quotations and sales orders when no price is typed.
 */
@Service
public class PricingService {

    private final PriceListRepository lists;
    private final CustomerGroupRepository groups;
    private final PriceListEntryRepository entries;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final AuditLogService audit;
    private final EntityManager em;

    public PricingService(PriceListRepository lists, CustomerGroupRepository groups, PriceListEntryRepository entries, ProductRepository products,
                          CustomerRepository customers, AuditLogService audit, EntityManager em) {
        this.lists = lists;
        this.groups = groups;
        this.entries = entries;
        this.products = products;
        this.customers = customers;
        this.audit = audit;
        this.em = em;
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    // ---- the price rule ----

    @Transactional(readOnly = true)
    public Resolved resolve(Customer customer, Product product, BigDecimal quantity) {
        BigDecimal base = money(product.getUnitPrice());
        Resolved normal = new Resolved(base, base, null);
        if (customer == null || customer.getCustomerGroupId() == null) return normal;
        CustomerGroup group = groups.findById(customer.getCustomerGroupId()).orElse(null);
        if (group == null || !group.isActive() || group.getPriceListId() == null) return normal;
        PriceList list = lists.findById(group.getPriceListId()).orElse(null);
        if (list == null || !list.isCurrent(LocalDate.now())) return normal;
        PriceListEntry best = null;
        for (PriceListEntry e : entries.findByPriceListIdAndProductId(list.getId(), product.getId())) {
            if (e.getMinQuantity().compareTo(quantity) <= 0 && (best == null || e.getMinQuantity().compareTo(best.getMinQuantity()) > 0)) best = e;
        }
        BigDecimal price;
        if (best != null) {
            price = best.getPrice();
        } else if (list.getAdjustPercent() != null && list.getAdjustPercent().signum() != 0) {
            price = base.multiply(BigDecimal.ONE.add(list.getAdjustPercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
        } else {
            price = base;
        }
        price = money(price.max(BigDecimal.ZERO));
        return new Resolved(base, price, price.compareTo(base) != 0 ? list.getName() : null);
    }

    @Transactional(readOnly = true)
    public Resolved resolve(Long customerId, Long productId, BigDecimal quantity) {
        Product product = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product not found: " + productId));
        Customer customer = customerId == null ? null : customers.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer not found: " + customerId));
        return resolve(customer, product, Qty.max(BigDecimal.ONE, quantity));
    }

    // ---- price lists ----

    @Transactional(readOnly = true)
    public List<ListDto> lists() {
        LocalDate today = LocalDate.now();
        return lists.findAllByOrderByNameAsc().stream().map(l -> toDto(l, today)).toList();
    }

    private ListDto toDto(PriceList l, LocalDate today) {
        return new ListDto(l.getId(), l.getName(), l.getDescription(), money(l.getAdjustPercent()), l.getValidFrom(), l.getValidTo(), l.isActive(), l.isCurrent(today),
                entries.countByPriceListId(l.getId()), groups.countByPriceListId(l.getId()));
    }

    @Transactional(readOnly = true)
    public ListDto list(Long id) {
        return toDto(listOrThrow(id), LocalDate.now());
    }

    PriceList listOrThrow(Long id) {
        return lists.findById(id).orElseThrow(() -> ApiException.notFound("Price list not found: " + id));
    }

    private void apply(PriceList l, ListRequest r) {
        String name = r.name() == null ? "" : r.name().trim();
        if (name.isEmpty()) throw ApiException.badRequest("Give the price list a name.");
        lists.findByNameIgnoreCase(name).filter(o -> !o.getId().equals(l.getId())).ifPresent(o -> {
            throw ApiException.conflict("A price list called '" + name + "' already exists.");
        });
        BigDecimal adj = r.adjustPercent() == null ? BigDecimal.ZERO : r.adjustPercent();
        if (adj.compareTo(BigDecimal.valueOf(-100)) <= 0 || adj.compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw ApiException.badRequest("The adjustment must be above -100% and at most 1000%.");
        }
        if (r.validFrom() != null && r.validTo() != null && r.validTo().isBefore(r.validFrom())) {
            throw ApiException.badRequest("'Valid to' cannot be before 'valid from'.");
        }
        l.setName(name);
        l.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        l.setAdjustPercent(adj.setScale(2, RoundingMode.HALF_UP));
        l.setValidFrom(r.validFrom());
        l.setValidTo(r.validTo());
        l.setActive(r.active() == null || r.active());
    }

    @Transactional
    public ListDto createList(ListRequest r) {
        PriceList l = new PriceList();
        apply(l, r);
        l = lists.save(l);
        audit.record("PRICE_LIST_CREATED", "Created price list " + l.getName(), "PriceList", l.getId());
        return toDto(l, LocalDate.now());
    }

    @Transactional
    public ListDto updateList(Long id, ListRequest r) {
        PriceList l = listOrThrow(id);
        apply(l, r);
        l = lists.save(l);
        audit.record("PRICE_LIST_UPDATED", "Updated price list " + l.getName(), "PriceList", l.getId());
        return toDto(l, LocalDate.now());
    }

    @Transactional
    public void deleteList(Long id) {
        PriceList l = listOrThrow(id);
        long used = groups.countByPriceListId(id);
        if (used > 0) throw ApiException.conflict("'" + l.getName() + "' is used by " + used + " customer group(s). Take it off those groups first, or switch the list off instead.");
        lists.delete(l);
        audit.record("PRICE_LIST_DELETED", "Deleted price list " + l.getName(), "PriceList", id);
    }

    // ---- entries ----

    @Transactional(readOnly = true)
    public List<EntryDto> entries(Long listId, String q) {
        listOrThrow(listId);
        List<PriceListEntry> all = entries.findByPriceListId(listId);
        Map<Long, Product> byId = products.findAllById(all.stream().map(PriceListEntry::getProductId).distinct().toList()).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        String needle = q == null ? "" : q.trim().toLowerCase();
        return all.stream()
                .filter(e -> {
                    Product p = byId.get(e.getProductId());
                    return needle.isEmpty() || (p != null && ((p.getName() != null && p.getName().toLowerCase().contains(needle)) || (p.getSku() != null && p.getSku().toLowerCase().contains(needle))));
                })
                .map(e -> entryDto(e, byId.get(e.getProductId())))
                .sorted(Comparator.comparing((EntryDto d) -> d.productName() == null ? "" : d.productName().toLowerCase()).thenComparing(EntryDto::minQuantity))
                .toList();
    }

    private EntryDto entryDto(PriceListEntry e, Product p) {
        return new EntryDto(e.getId(), e.getProductId(), p == null ? null : p.getName(), p == null ? null : p.getSku(), p == null ? null : money(p.getUnitPrice()), e.getMinQuantity(), money(e.getPrice()));
    }

    /** Adds the entry, or changes the price of the one already there for that product and quantity step. */
    @Transactional
    public EntryDto saveEntry(Long listId, EntryRequest r) {
        PriceList l = listOrThrow(listId);
        if (r.productId() == null) throw ApiException.badRequest("Choose a product.");
        Product p = products.findById(r.productId()).orElseThrow(() -> ApiException.notFound("Product not found: " + r.productId()));
        BigDecimal min = r.minQuantity() == null ? BigDecimal.ONE : r.minQuantity();
        if (min.compareTo(BigDecimal.ONE) < 0) throw ApiException.badRequest("The quantity step must be at least 1.");
        checkPrice(r.price());
        PriceListEntry e = entries.findByPriceListIdAndProductIdAndMinQuantity(listId, p.getId(), min).orElseGet(PriceListEntry::new);
        e.setPriceListId(listId);
        e.setProductId(p.getId());
        e.setMinQuantity(min);
        e.setPrice(money(r.price()));
        e = entries.save(e);
        audit.record("PRICE_LIST_ENTRY_SAVED", "Price list " + l.getName() + ": " + p.getName() + " from " + Qty.str(min) + " at " + e.getPrice(), "PriceList", listId);
        return entryDto(e, p);
    }

    static void checkPrice(BigDecimal price) {
        if (price == null) throw ApiException.badRequest("Enter a price.");
        if (price.signum() < 0) throw ApiException.badRequest("A price cannot be negative.");
        if (price.scale() > 2 && price.stripTrailingZeros().scale() > 2) throw ApiException.badRequest("A price can have at most two decimals.");
    }

    @Transactional
    public void deleteEntry(Long listId, Long entryId) {
        PriceList l = listOrThrow(listId);
        PriceListEntry e = entries.findById(entryId).filter(x -> x.getPriceListId().equals(listId)).orElseThrow(() -> ApiException.notFound("That price is not on this list."));
        entries.delete(e);
        audit.record("PRICE_LIST_ENTRY_REMOVED", "Price list " + l.getName() + ": removed a price", "PriceList", listId);
    }

    // ---- customer groups ----

    @Transactional(readOnly = true)
    public List<GroupDto> groups() {
        Map<Long, String> names = lists.findAll().stream().collect(Collectors.toMap(PriceList::getId, PriceList::getName));
        return groups.findAllByOrderByNameAsc().stream().map(g -> groupDto(g, names)).toList();
    }

    private GroupDto groupDto(CustomerGroup g, Map<Long, String> listNames) {
        long members = ((Number) em.createQuery("select count(c) from Customer c where c.customerGroupId = :id").setParameter("id", g.getId()).getSingleResult()).longValue();
        return new GroupDto(g.getId(), g.getName(), g.getDescription(), g.getPriceListId(), g.getPriceListId() == null ? null : listNames.get(g.getPriceListId()), g.isActive(), members);
    }

    private void apply(CustomerGroup g, GroupRequest r) {
        String name = r.name() == null ? "" : r.name().trim();
        if (name.isEmpty()) throw ApiException.badRequest("Give the group a name.");
        groups.findByNameIgnoreCase(name).filter(o -> !o.getId().equals(g.getId())).ifPresent(o -> {
            throw ApiException.conflict("A group called '" + name + "' already exists.");
        });
        if (r.priceListId() != null) listOrThrow(r.priceListId());
        g.setName(name);
        g.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        g.setPriceListId(r.priceListId());
        g.setActive(r.active() == null || r.active());
    }

    @Transactional
    public GroupDto createGroup(GroupRequest r) {
        CustomerGroup g = new CustomerGroup();
        apply(g, r);
        g = groups.save(g);
        audit.record("CUSTOMER_GROUP_CREATED", "Created customer group " + g.getName(), "CustomerGroup", g.getId());
        return groupDto(g, lists.findAll().stream().collect(Collectors.toMap(PriceList::getId, PriceList::getName)));
    }

    @Transactional
    public GroupDto updateGroup(Long id, GroupRequest r) {
        CustomerGroup g = groups.findById(id).orElseThrow(() -> ApiException.notFound("Customer group not found: " + id));
        apply(g, r);
        g = groups.save(g);
        audit.record("CUSTOMER_GROUP_UPDATED", "Updated customer group " + g.getName(), "CustomerGroup", g.getId());
        return groupDto(g, lists.findAll().stream().collect(Collectors.toMap(PriceList::getId, PriceList::getName)));
    }

    @Transactional
    public void deleteGroup(Long id) {
        CustomerGroup g = groups.findById(id).orElseThrow(() -> ApiException.notFound("Customer group not found: " + id));
        long members = ((Number) em.createQuery("select count(c) from Customer c where c.customerGroupId = :id").setParameter("id", id).getSingleResult()).longValue();
        if (members > 0) throw ApiException.conflict("'" + g.getName() + "' has " + members + " customer(s). Move them to another group first, or switch the group off instead.");
        groups.delete(g);
        audit.record("CUSTOMER_GROUP_DELETED", "Deleted customer group " + g.getName(), "CustomerGroup", id);
    }

    /** Puts the customers in the group (or takes them out of any group when {@code groupId} is null). Returns how many were changed. */
    @Transactional
    public int assign(List<Long> customerIds, Long groupId) {
        if (customerIds == null || customerIds.isEmpty()) throw ApiException.badRequest("Choose at least one customer.");
        CustomerGroup g = null;
        if (groupId != null) g = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Customer group not found: " + groupId));
        int changed = 0;
        for (Customer c : customers.findAllById(customerIds.stream().distinct().toList())) {
            if (java.util.Objects.equals(c.getCustomerGroupId(), groupId)) continue;
            c.setCustomerGroupId(groupId);
            customers.save(c);
            changed++;
        }
        audit.record("CUSTOMER_GROUP_ASSIGNED", changed + " customer(s) " + (g == null ? "taken out of their group" : "put in group " + g.getName()));
        return changed;
    }
}
