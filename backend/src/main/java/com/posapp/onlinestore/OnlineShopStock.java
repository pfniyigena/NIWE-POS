package com.posapp.onlinestore;

import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.i18n.I18n;
import com.posapp.salesorder.StockReservationService;
import com.posapp.settings.StoreSettingsService;
import com.posapp.warehouse.LocationType;
import com.posapp.warehouse.ProductStock;
import com.posapp.warehouse.ProductStockRepository;
import com.posapp.warehouse.Warehouse;
import com.posapp.warehouse.WarehouseLocation;
import com.posapp.warehouse.WarehouseLocationRepository;
import com.posapp.warehouse.WarehouseRepository;
import com.posapp.warehouse.WarehouseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * v1.65.0: which stock the online shop sells. With multi-warehouse off nothing changes (the shop sees the total of every location, as before).
 * With it on, a sale can only leave a Sales location outside the Main warehouse, so only that stock is offered: the one location chosen in
 * Shop settings, or (nothing chosen) every such location together. The same rule picks the location a delivery is taken from when nobody chose
 * one (the driver's code, the customer's "I received my order").
 */
@Service
public class OnlineShopStock {

    @Autowired
    private StoreSettingsService storeSettingsService;
    @Autowired
    private OnlineStoreSettingsRepository settingsRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private WarehouseLocationRepository locationRepository;
    @Autowired
    private ProductStockRepository productStockRepository;
    @Autowired
    private WarehouseService warehouseService;
    @Autowired
    private StockReservationService stockReservationService;
    @Autowired
    private OnlineListingRepository listingRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;
    @Autowired
    private com.posapp.packaging.PackagingRuleRepository packagingRules;
    @Autowired
    private com.posapp.packaging.PackagingService packagingService;

    public record LocationDto(Long id, String label, boolean chosen) {
    }

    public record StockSetup(boolean multiWarehouse, Long locationId, List<LocationDto> options, String note) {
    }

    public record SaveRequest(Long locationId) {
    }

    public boolean multiWarehouse() {
        return storeSettingsService.getSettingsEntity().isMultiWarehouseEnabled();
    }

    /** Every Sales location a sale may leave from (active, in an active warehouse, not the Main one), by warehouse and name. */
    @Transactional(readOnly = true)
    public List<WarehouseLocation> eligible() {
        List<WarehouseLocation> out = new ArrayList<>();
        for (Warehouse w : warehouseRepository.findByActiveTrueOrderByNameAsc()) {
            if (w.isPrimary()) continue;
            for (WarehouseLocation l : locationRepository.findAllByWarehouseOrderByNameAsc(w)) {
                if (l.isActive() && l.getType() == LocationType.SALES) out.add(l);
            }
        }
        return out;
    }

    /** The locations the shop sells from: empty when multi-warehouse is off (the old rule applies) or when none is set up. */
    @Transactional(readOnly = true)
    public List<WarehouseLocation> locations() {
        if (!multiWarehouse()) return List.of();
        List<WarehouseLocation> all = eligible();
        Long chosen = chosenId();
        if (chosen != null) {
            List<WarehouseLocation> one = all.stream().filter(l -> l.getId().equals(chosen)).toList();
            if (!one.isEmpty()) return one;
        }
        return all;
    }

    private Long chosenId() {
        return settingsRepository.findAll().stream().findFirst().map(OnlineStoreSettings::getShopLocationId).orElse(null);
    }

    /** Units of this product the shop may sell from, before reservations. */
    @Transactional(readOnly = true)
    public BigDecimal onHand(Product p) {
        if (!multiWarehouse()) return warehouseService.totalSellable(p);
        List<Long> ids = locations().stream().map(WarehouseLocation::getId).toList();
        BigDecimal total = BigDecimal.ZERO;
        for (ProductStock row : productStockRepository.findByProduct(p)) {
            if (row.getLocation() != null && ids.contains(row.getLocation().getId()) && row.getQuantity() != null) total = total.add(row.getQuantity());
        }
        return total;
    }

    /**
     * v1.66.0: units promised to shop orders that staff have not answered yet (waiting for a payment, or placed and not accepted). They have no sale order yet,
     * so the shop holds them itself; once staff accept, the sale order reserves them instead.
     */
    @Transactional(readOnly = true)
    public BigDecimal held(Product p) {
        BigDecimal h = em.createQuery("select coalesce(sum(l.quantity), 0) from OnlineOrderLine l where l.productId = :id and l.onlineOrder.status in ('AWAITING_PAYMENT', 'PLACED')", BigDecimal.class)
                .setParameter("id", p.getId()).getSingleResult();
        return h == null ? BigDecimal.ZERO : h;
    }

    /** v1.67.0: the first expiry date the shop will sell: today plus the "min days left" setting. Batches expiring before it are not sold online. */
    @Transactional(readOnly = true)
    public java.time.LocalDate expiryCut() {
        int days = settingsRepository.findAll().stream().findFirst().map(OnlineStoreSettings::getMinDaysLeft).orElse(0);
        return java.time.LocalDate.now().plusDays(Math.max(0, days));
    }

    /**
     * v1.67.0: units of batch-tracked goods at the shop's locations that are expired, or have fewer days left than the shop's setting. They stay in the stock
     * but the shop does not offer them (staff can still sell or write them off). Stock that was never given a batch is never held back.
     */
    @Transactional(readOnly = true)
    public BigDecimal expired(Product p) {
        if (com.posapp.tracking.TrackingService.modeOf(p) != com.posapp.tracking.TrackingMode.BATCH) return BigDecimal.ZERO;
        BigDecimal sum = em.createQuery("select coalesce(sum(b.quantity), 0) from StockBatch b where b.product.id = :id and b.quantity > 0 and b.expiryDate < :cut "
                + "and b.location.active = true and (b.location.type is null or b.location.type <> com.posapp.warehouse.LocationType.DAMAGED)" + searchCondition("b"), BigDecimal.class)
                .setParameter("id", p.getId()).setParameter("cut", expiryCut()).getSingleResult();
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private boolean breakdownOn() {
        return storeSettingsService.getSettingsEntity().isProductBreakdownEnabled();
    }

    /**
     * v1.68.0: units of this product the shop could make by breaking down cartons (packaging rules) it holds at its locations: the cartons that are free (not
     * reserved by sale orders, not held by shop orders) times the units each gives. Nothing unless "Product packaging and breakdown" is on in Inventory settings.
     */
    @Transactional(readOnly = true)
    public BigDecimal breakable(Product child) {
        BigDecimal total = BigDecimal.ZERO;
        if (!breakdownOn() || !child.isTrackStock()) return total;
        for (com.posapp.packaging.PackagingRule r : packagingRules.findByChildProductIdAndActiveTrueOrderByIdAsc(child.getId())) {
            Product parent = r.getParentProduct();
            if (parent == null || !parent.isTrackStock() || r.getUnitsPerParent() == null) continue;
            BigDecimal free = onHand(parent).subtract(stockReservationService.reserved(parent)).subtract(held(parent));
            if (free.signum() > 0) total = total.add(free.setScale(0, java.math.RoundingMode.DOWN).multiply(r.getUnitsPerParent()));
        }
        return total;
    }

    /** The same figure as a piece of the search query for the product under {@code alias} ("" when breakdown is off). The caller adds it to the stock. */
    @Transactional(readOnly = true)
    public String breakableSql(String alias) {
        if (!breakdownOn()) return "";
        String free = "((select coalesce(sum(pp.quantity), 0) from ProductStock pp where pp.product = r.parentProduct and pp.location.active = true" + searchCondition("pp")
                + " and (pp.location.type is null or pp.location.type <> com.posapp.warehouse.LocationType.DAMAGED)) "
                + "- (select coalesce(sum(sl2.quantityOrdered - sl2.quantityFulfilled), 0) from SalesOrderLine sl2 where sl2.product = r.parentProduct "
                + "and sl2.salesOrder.status in (com.posapp.salesorder.SalesOrderStatus.CONFIRMED, com.posapp.salesorder.SalesOrderStatus.PARTIALLY_FULFILLED)) "
                + "- (select coalesce(sum(ol2.quantity), 0) from OnlineOrderLine ol2 where ol2.productId = r.parentProduct.id and ol2.onlineOrder.status in ('AWAITING_PAYMENT', 'PLACED')))";
        return " + (select coalesce(sum(r.unitsPerParent * floor(case when " + free + " > 0 then " + free + " else 0 end)), 0) from PackagingRule r "
                + "where r.active = true and r.childProduct = " + alias + " and r.parentProduct.trackStock = true)";
    }

    /**
     * v1.68.0: makes the single units an order needs when the shop holds only cartons: for each product of {@code need} that is short, breaks down cartons
     * (active packaging rules) where the stock is, recording a normal breakdown. {@code delivering} = the sale order already reserves the units, so the shortage is
     * measured against what is physically there; otherwise against what is free. {@code onlyLocationId} limits it to one location (the one a delivery leaves
     * from). Returns how many cartons were broken down. A refusal inside leaves things as they were (the caller's normal stock check then speaks).
     */
    @Transactional
    public int breakDownFor(Map<Long, BigDecimal> need, String reference, boolean delivering, Long onlyLocationId) {
        if (!breakdownOn() || need == null) return 0;
        int cartons = 0;
        for (Map.Entry<Long, BigDecimal> e : need.entrySet()) {
            Product child = em.find(Product.class, e.getKey());
            if (child == null || !child.isTrackStock() || child.isService()) continue;
            List<com.posapp.packaging.PackagingRule> rules = packagingRules.findByChildProductIdAndActiveTrueOrderByIdAsc(child.getId());
            if (rules.isEmpty()) continue;
            BigDecimal position = onHand(child);
            if (!delivering) position = position.subtract(stockReservationService.reserved(child));
            BigDecimal short1 = e.getValue().subtract(position.max(BigDecimal.ZERO));
            for (com.posapp.packaging.PackagingRule r : rules) {
                if (short1.signum() <= 0) break;
                Product parent = r.getParentProduct();
                if (parent == null || !parent.isTrackStock() || r.getUnitsPerParent() == null || r.getUnitsPerParent().signum() <= 0) continue;
                List<WarehouseLocation> places = new ArrayList<>();
                if (!multiWarehouse()) places.add(null);
                else for (WarehouseLocation l : locations()) if (onlyLocationId == null || l.getId().equals(onlyLocationId)) places.add(l);
                for (WarehouseLocation loc : places) {
                    if (short1.signum() <= 0) break;
                    BigDecimal here = warehouseService.stockAt(parent, loc);
                    if (!delivering) here = here.subtract(stockReservationService.reserved(parent)).subtract(held(parent));
                    BigDecimal canDo = here.setScale(0, java.math.RoundingMode.DOWN);
                    BigDecimal want = short1.divide(r.getUnitsPerParent(), 0, java.math.RoundingMode.CEILING);
                    BigDecimal n = want.min(canDo);
                    if (n.signum() <= 0) continue;
                    com.posapp.packaging.dto.PackagingBreakdownRequest req = new com.posapp.packaging.dto.PackagingBreakdownRequest();
                    req.setRuleId(r.getId());
                    req.setParentQuantity(n);
                    req.setLocationId(loc == null ? null : loc.getId());
                    req.setNote(I18n.t("Automatic: online order {0}", reference));
                    packagingService.breakdown(req);
                    cartons += n.intValue();
                    short1 = short1.subtract(n.multiply(r.getUnitsPerParent()));
                }
            }
        }
        return cartons;
    }

    /** On hand at the shop's locations (plus what cartons could make) minus what is expired, what confirmed sale orders still owe and what unanswered shop orders hold. */
    @Transactional(readOnly = true)
    public BigDecimal available(Product p) {
        return onHand(p).add(breakable(p)).subtract(expired(p)).subtract(stockReservationService.reserved(p)).subtract(held(p));
    }

    /** The safety buffer of one product: its own when staff set one, otherwise the shop-wide one. */
    @Transactional(readOnly = true)
    public int bufferOf(Product p, int shopBuffer) {
        return listingRepository.findByProductId(p.getId()).map(OnlineListing::getStockBuffer).filter(b -> b != null && b >= 0).orElse(shopBuffer);
    }

    /** The share (percent) of the available units kept for the till. */
    @Transactional(readOnly = true)
    public int tillPercent() {
        return settingsRepository.findAll().stream().findFirst().map(OnlineStoreSettings::getTillSharePercent).orElse(0);
    }

    /** Units kept for the till out of {@code available}: the percentage, rounded up (none when nothing is available). */
    public static BigDecimal tillShare(BigDecimal available, int percent) {
        if (percent <= 0 || available == null || available.signum() <= 0) return BigDecimal.ZERO;
        return available.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.CEILING);
    }

    /**
     * Two customers must not take the last units together: the products of an order are locked (always in the same order) while its stock is checked and
     * the order saved, so the second customer sees what the first one took.
     */
    public void lockProducts(java.util.Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return;
        List<Long> ids = new ArrayList<>(new java.util.TreeSet<>(productIds));
        em.createQuery("select p.id from Product p where p.id in :ids order by p.id").setParameter("ids", ids)
                .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    /** For the search query: a condition on the stock row alias {@code ps} that keeps only the shop's locations; empty when the old rule applies. */
    @Transactional(readOnly = true)
    public String searchCondition() {
        return searchCondition("ps");
    }

    /** The same condition for another alias of a row that has a location (the batches use b). */
    @Transactional(readOnly = true)
    public String searchCondition(String alias) {
        if (!multiWarehouse()) return "";
        List<WarehouseLocation> l = locations();
        if (l.isEmpty()) return " and " + alias + ".location.id = -1";
        StringBuilder sb = new StringBuilder(" and " + alias + ".location.id in (");
        for (int i = 0; i < l.size(); i++) sb.append(i == 0 ? "" : ",").append(l.get(i).getId().longValue());
        return sb.append(")").toString();
    }

    /**
     * The location a delivery is taken from. {@code chosen} (staff picked one) wins; with multi-warehouse off null (the old way); otherwise the
     * shop's location, or the one of several that holds the most of what the order needs.
     */
    @Transactional(readOnly = true)
    public Long locationFor(OnlineOrder o, Long chosen) {
        if (chosen != null) return chosen;
        if (!multiWarehouse()) return null;
        List<WarehouseLocation> l = locations();
        if (l.isEmpty()) {
            throw ApiException.badRequest(I18n.t("The shop has no Sales location to sell from. In Inventory make a Sales location in a warehouse other than the Main one, then choose it in Shop settings."));
        }
        if (l.size() == 1) return l.get(0).getId();
        Map<Long, BigDecimal> need = new java.util.HashMap<>();
        for (OnlineOrderLine line : o.getLines()) need.merge(line.getProductId(), line.getQuantity(), BigDecimal::add);
        WarehouseLocation best = null;
        long bestScore = -1;
        for (WarehouseLocation loc : l) {
            long covered = 0;
            BigDecimal held = BigDecimal.ZERO;
            for (ProductStock row : productStockRepository.findByLocation(loc)) {
                BigDecimal n = row.getProduct() == null ? null : need.get(row.getProduct().getId());
                if (n == null || row.getQuantity() == null) continue;
                held = held.add(row.getQuantity().min(n));
                if (row.getQuantity().compareTo(n) >= 0) covered++;
            }
            long score = covered * 1_000_000L + held.longValue();
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best == null ? l.get(0).getId() : best.getId();
    }

    // ---------------------------------------------------------------- staff screen (Shop settings)

    @Transactional(readOnly = true)
    public StockSetup setup() {
        boolean multi = multiWarehouse();
        Long chosen = chosenId();
        List<LocationDto> options = new ArrayList<>();
        if (multi) {
            for (WarehouseLocation l : eligible()) {
                options.add(new LocationDto(l.getId(), l.getWarehouse().getName() + " / " + l.getName(), l.getId().equals(chosen)));
            }
            options.sort(Comparator.comparing(LocationDto::label));
        }
        String note = !multi ? "off" : options.isEmpty() ? "none" : "ok";
        boolean valid = chosen != null && options.stream().anyMatch(o -> o.id().equals(chosen));
        return new StockSetup(multi, valid ? chosen : null, options, note);
    }

    @Transactional
    public StockSetup save(SaveRequest in) {
        Long id = in == null ? null : in.locationId();
        if (id != null && eligible().stream().noneMatch(l -> l.getId().equals(id))) {
            throw ApiException.badRequest(I18n.t("Choose a Sales location of a warehouse other than the Main one."));
        }
        OnlineStoreSettings s = settingsRepository.findAll().stream().findFirst().orElseThrow(() -> ApiException.badRequest(I18n.t("Save the shop settings first.")));
        s.setShopLocationId(id);
        settingsRepository.save(s);
        return setup();
    }
}
