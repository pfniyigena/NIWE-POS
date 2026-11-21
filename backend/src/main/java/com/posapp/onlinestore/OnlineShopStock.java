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

    /** On hand at the shop's locations minus what confirmed sale orders still owe. */
    @Transactional(readOnly = true)
    public BigDecimal available(Product p) {
        return onHand(p).subtract(stockReservationService.reserved(p));
    }

    /** For the search query: a condition on the stock row alias {@code ps} that keeps only the shop's locations; empty when the old rule applies. */
    @Transactional(readOnly = true)
    public String searchCondition() {
        if (!multiWarehouse()) return "";
        List<WarehouseLocation> l = locations();
        if (l.isEmpty()) return " and ps.location.id = -1";
        StringBuilder sb = new StringBuilder(" and ps.location.id in (");
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
