package com.posapp.tracking;

import com.posapp.catalog.Product;
import com.posapp.common.ApiException;
import com.posapp.tracking.dto.BatchDto;
import com.posapp.tracking.dto.BatchReportRow;
import com.posapp.tracking.dto.SerialReportRow;
import com.posapp.tracking.dto.SerialDto;
import com.posapp.warehouse.WarehouseLocation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Batch / expiry and serial-number bookkeeping that sits beside the plain stock quantity.
 * The quantity per location stays the source of truth; batches and serials describe what that
 * quantity is made of. Stock that was never given a batch or serial (opening balances, customer
 * returns, count surpluses) simply remains "untracked" — it is never blocked, and the batch report
 * shows the difference.
 */
@Service
public class TrackingService {

    private final StockBatchRepository batchRepository;
    private final StockSerialRepository serialRepository;

    public TrackingService(StockBatchRepository batchRepository, StockSerialRepository serialRepository) {
        this.batchRepository = batchRepository;
        this.serialRepository = serialRepository;
    }

    public static TrackingMode modeOf(Product product) {
        return product != null && product.getTrackingMode() != null ? product.getTrackingMode() : TrackingMode.NONE;
    }

    // ------------------------------------------------------------------ receiving

    /** Adds units to a batch (creating it, or topping up the same batch number + expiry at that location). */
    @Transactional
    public void receiveBatch(Product product, WarehouseLocation location, String batchNumber, LocalDate expiryDate,
                             int quantity, BigDecimal unitCost, String source) {
        if (quantity <= 0 || location == null) return;
        String number = batchNumber != null && !batchNumber.isBlank() ? batchNumber.trim() : null;
        if (number == null) {
            number = source != null && !source.isBlank() ? source : "LOT-" + LocalDate.now();
        }
        StockBatch existing = findSame(product, location, number, expiryDate, null);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            batchRepository.save(existing);
            return;
        }
        StockBatch batch = new StockBatch();
        batch.setProduct(product);
        batch.setLocation(location);
        batch.setBatchNumber(number);
        batch.setExpiryDate(expiryDate);
        batch.setQuantity(quantity);
        batch.setUnitCost(unitCost);
        batch.setSource(source);
        batchRepository.save(batch);
    }

    /** Registers serial numbers as in stock at a location. Rejects a duplicate or a serial that is already in stock. */
    @Transactional
    public void receiveSerials(Product product, WarehouseLocation location, List<String> serials, BigDecimal unitCost, String source) {
        if (serials == null || serials.isEmpty() || location == null) return;
        List<String> clean = cleanSerials(product, serials);
        for (String number : clean) {
            StockSerial serial = serialRepository.findByProductAndSerialNumber(product, number).orElse(null);
            if (serial != null && (serial.getStatus() == SerialStatus.IN_STOCK || serial.getStatus() == SerialStatus.IN_TRANSIT)) {
                throw ApiException.badRequest("Serial number '" + number + "' of '" + product.getName() + "' is already in stock");
            }
            if (serial == null) {
                serial = new StockSerial();
                serial.setProduct(product);
                serial.setSerialNumber(number);
            }
            serial.setLocation(location);
            serial.setTransferId(null);
            serial.setStatus(SerialStatus.IN_STOCK);
            serial.setUnitCost(unitCost);
            serial.setReceivedAt(Instant.now());
            serial.setSource(source);
            serial.setSoldAt(null);
            serial.setSoldRef(null);
            serialRepository.save(serial);
        }
    }

    /** Trims, drops blanks, and rejects duplicates inside the list. */
    public List<String> cleanSerials(Product product, List<String> serials) {
        List<String> clean = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : serials) {
            if (raw == null || raw.isBlank()) continue;
            String number = raw.trim();
            if (number.length() > 100) {
                throw ApiException.badRequest("Serial number '" + number.substring(0, 20) + "…' is too long (100 characters at most)");
            }
            if (!seen.add(number.toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("Serial number '" + number + "' is listed twice");
            }
            clean.add(number);
        }
        return clean;
    }

    // ------------------------------------------------------------------ leaving stock

    /**
     * Takes units out of a location because they were sold or otherwise used up. Batches go
     * earliest-expiry-first (expired batches last); serials are the ones the sale chose, or the
     * oldest in stock. If fewer are tracked than requested the rest is untracked stock and is left alone.
     */
    @Transactional
    public void consume(Product product, WarehouseLocation location, int quantity) {
        if (quantity <= 0 || location == null) return;
        switch (modeOf(product)) {
            case BATCH -> {
                int left = quantity;
                for (StockBatch batch : orderedBatches(product, location)) {
                    if (left <= 0) break;
                    int take = Math.min(left, batch.getQuantity());
                    batch.setQuantity(batch.getQuantity() - take);
                    left -= take;
                    if (batch.getQuantity() <= 0) batchRepository.delete(batch); else batchRepository.save(batch);
                }
            }
            case SERIAL -> {
                List<StockSerial> units = pickSerials(product, location, quantity);
                String saleRef = TrackingContext.saleReference();
                for (StockSerial serial : units) {
                    serial.setStatus(saleRef != null ? SerialStatus.SOLD : SerialStatus.REMOVED);
                    serial.setSoldAt(Instant.now());
                    serial.setSoldRef(saleRef);
                    serial.setLocation(null);
                    serialRepository.save(serial);
                }
            }
            default -> { }
        }
    }

    /** Serials chosen explicitly for this product (validated), otherwise the oldest in stock at the location. */
    private List<StockSerial> pickSerials(Product product, WarehouseLocation location, int quantity) {
        List<String> chosen = TrackingContext.takeSerials(product.getId());
        if (chosen != null && !chosen.isEmpty()) {
            List<String> clean = cleanSerials(product, chosen);
            if (clean.size() != quantity) {
                throw ApiException.badRequest("'" + product.getName() + "' needs " + quantity + " serial number" + (quantity == 1 ? "" : "s")
                        + " but " + clean.size() + " " + (clean.size() == 1 ? "was" : "were") + " given");
            }
            List<StockSerial> units = new ArrayList<>();
            for (String number : clean) {
                StockSerial serial = serialRepository.findByProductAndSerialNumber(product, number)
                        .orElseThrow(() -> ApiException.badRequest("Serial number '" + number + "' of '" + product.getName() + "' is not in the system"));
                if (serial.getStatus() != SerialStatus.IN_STOCK) {
                    throw ApiException.badRequest("Serial number '" + number + "' of '" + product.getName() + "' is not in stock (" + serial.getStatus() + ")");
                }
                if (serial.getLocation() == null || !serial.getLocation().getId().equals(location.getId())) {
                    throw ApiException.badRequest("Serial number '" + number + "' of '" + product.getName() + "' is not at "
                            + location.getName() + " — it is at " + (serial.getLocation() != null ? serial.getLocation().getName() : "an unknown place"));
                }
                units.add(serial);
            }
            return units;
        }
        List<StockSerial> available = serialRepository.findByProductAndLocationAndStatusOrderByIdAsc(product, location, SerialStatus.IN_STOCK);
        return new ArrayList<>(available.subList(0, Math.min(quantity, available.size())));
    }

    // ------------------------------------------------------------------ moving between locations

    /** Same-branch transfer: the batches/serials travel with the stock. */
    @Transactional
    public void moveNow(Product product, WarehouseLocation from, WarehouseLocation to, int quantity) {
        if (quantity <= 0 || from == null || to == null) return;
        switch (modeOf(product)) {
            case BATCH -> {
                for (StockBatch part : detachBatches(product, from, quantity)) {
                    placeBatch(part, to);
                }
            }
            case SERIAL -> {
                for (StockSerial serial : pickAuto(product, from, quantity)) {
                    serial.setLocation(to);
                    serialRepository.save(serial);
                }
            }
            default -> { }
        }
    }

    /** Inter-branch dispatch: the tracked units leave the source and ride with the transfer until it is received or returned. */
    @Transactional
    public void dispatch(Product product, WarehouseLocation from, int quantity, Long transferId) {
        if (quantity <= 0 || from == null || transferId == null) return;
        switch (modeOf(product)) {
            case BATCH -> {
                for (StockBatch part : detachBatches(product, from, quantity)) {
                    part.setLocation(null);
                    part.setTransferId(transferId);
                    batchRepository.save(part);
                }
            }
            case SERIAL -> {
                for (StockSerial serial : pickAuto(product, from, quantity)) {
                    serial.setLocation(null);
                    serial.setTransferId(transferId);
                    serial.setStatus(SerialStatus.IN_TRANSIT);
                    serialRepository.save(serial);
                }
            }
            default -> { }
        }
    }

    /** The receiving end: up to {@code received} of the carried units land at the destination; the rest are written off. */
    @Transactional
    public void arrive(Product product, Long transferId, WarehouseLocation to, int received) {
        if (transferId == null || to == null) return;
        switch (modeOf(product)) {
            case BATCH -> {
                List<StockBatch> carried = new ArrayList<>(batchRepository.findByTransferIdAndProduct(transferId, product));
                carried.sort(BATCH_ORDER);
                int left = Math.max(0, received);
                for (StockBatch batch : carried) {
                    if (left <= 0) {
                        batchRepository.delete(batch);
                        continue;
                    }
                    if (batch.getQuantity() > left) {
                        batch.setQuantity(left);
                    }
                    left -= batch.getQuantity();
                    placeBatch(batch, to);
                }
            }
            case SERIAL -> {
                int left = Math.max(0, received);
                for (StockSerial serial : serialRepository.findByTransferIdAndProductOrderByIdAsc(transferId, product)) {
                    serial.setTransferId(null);
                    if (left > 0) {
                        serial.setLocation(to);
                        serial.setStatus(SerialStatus.IN_STOCK);
                        left--;
                    } else {
                        serial.setLocation(null);
                        serial.setStatus(SerialStatus.REMOVED);
                        serial.setSoldAt(Instant.now());
                    }
                    serialRepository.save(serial);
                }
            }
            default -> { }
        }
    }

    /** The goods never arrived: everything the transfer carried goes back to the source location. */
    @Transactional
    public void returnToSender(Product product, Long transferId, WarehouseLocation from) {
        if (transferId == null || from == null) return;
        switch (modeOf(product)) {
            case BATCH -> {
                for (StockBatch batch : batchRepository.findByTransferIdAndProduct(transferId, product)) {
                    placeBatch(batch, from);
                }
            }
            case SERIAL -> {
                for (StockSerial serial : serialRepository.findByTransferIdAndProductOrderByIdAsc(transferId, product)) {
                    serial.setTransferId(null);
                    serial.setLocation(from);
                    serial.setStatus(SerialStatus.IN_STOCK);
                    serialRepository.save(serial);
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<BatchDto> batchesForProduct(Long productId) {
        return batchRepository.findLiveByProductId(productId).stream().map(BatchDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SerialDto> serialsForProduct(Long productId) {
        return serialRepository.findByProductIdWithDetails(productId).stream().map(SerialDto::from).toList();
    }

    /** Serial lookup across all products (contains match on the serial number, newest first, capped). */
    @Transactional(readOnly = true)
    public List<SerialDto> searchSerials(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return serialRepository.findAllWithDetails().stream()
                .filter(s -> q.isEmpty() || s.getSerialNumber().toLowerCase(Locale.ROOT).contains(q)
                        || (s.getProduct() != null && s.getProduct().getName().toLowerCase(Locale.ROOT).contains(q)))
                .limit(300)
                .map(SerialDto::from)
                .toList();
    }

    /**
     * Batch rows with stock left. {@code withinDays} null = every batch; otherwise only batches that are already
     * expired or expire within that many days (batches with no expiry date are left out).
     */
    @Transactional(readOnly = true)
    public List<BatchReportRow> batchReport(Long categoryId, Long warehouseId, Integer withinDays) {
        LocalDate today = LocalDate.now();
        List<BatchReportRow> rows = new ArrayList<>();
        for (StockBatch b : batchRepository.findAllLive()) {
            if (categoryId != null && (b.getProduct().getCategory() == null || !categoryId.equals(b.getProduct().getCategory().getId()))) continue;
            if (warehouseId != null && (b.getLocation() == null || b.getLocation().getWarehouse() == null
                    || !warehouseId.equals(b.getLocation().getWarehouse().getId()))) continue;
            Long days = b.getExpiryDate() != null ? java.time.temporal.ChronoUnit.DAYS.between(today, b.getExpiryDate()) : null;
            if (withinDays != null && (days == null || days > withinDays)) continue;
            String status;
            if (b.getLocation() == null) status = "In transit";
            else if (days == null) status = "No expiry";
            else if (days < 0) status = "Expired";
            else if (days <= 30) status = "Expires within 30 days";
            else status = "OK";
            BigDecimal cost = b.getUnitCost() != null ? b.getUnitCost() : BigDecimal.ZERO;
            rows.add(new BatchReportRow(
                    b.getProduct().getId(), b.getProduct().getSku(), b.getProduct().getName(),
                    b.getLocation() != null && b.getLocation().getWarehouse() != null ? b.getLocation().getWarehouse().getName() : "—",
                    b.getLocation() != null ? b.getLocation().getName() : "In transit",
                    b.getBatchNumber(),
                    b.getExpiryDate() != null ? b.getExpiryDate().toString() : "—",
                    days != null ? String.valueOf(days) : "—",
                    b.getQuantity(), cost, cost.multiply(BigDecimal.valueOf(b.getQuantity())), status));
        }
        rows.sort(Comparator.comparing((BatchReportRow r) -> "—".equals(r.expiryDate()))
                .thenComparing(BatchReportRow::expiryDate)
                .thenComparing(BatchReportRow::productName, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    /** Serial register. {@code status} blank = all. */
    @Transactional(readOnly = true)
    public List<SerialReportRow> serialReport(Long categoryId, Long warehouseId, String status) {
        List<SerialReportRow> rows = new ArrayList<>();
        for (StockSerial s : serialRepository.findAllWithDetails()) {
            if (status != null && !status.isBlank() && !s.getStatus().name().equalsIgnoreCase(status.trim())) continue;
            if (categoryId != null && (s.getProduct().getCategory() == null || !categoryId.equals(s.getProduct().getCategory().getId()))) continue;
            if (warehouseId != null && (s.getLocation() == null || s.getLocation().getWarehouse() == null
                    || !warehouseId.equals(s.getLocation().getWarehouse().getId()))) continue;
            rows.add(new SerialReportRow(
                    s.getProduct().getId(), s.getProduct().getSku(), s.getProduct().getName(), s.getSerialNumber(),
                    s.getStatus().name(),
                    s.getLocation() != null && s.getLocation().getWarehouse() != null ? s.getLocation().getWarehouse().getName() : "—",
                    s.getLocation() != null ? s.getLocation().getName() : "—",
                    s.getReceivedAt() != null ? java.time.LocalDate.ofInstant(s.getReceivedAt(), java.time.ZoneId.systemDefault()).toString() : "—",
                    s.getSource() != null ? s.getSource() : "—",
                    s.getSoldAt() != null ? java.time.LocalDate.ofInstant(s.getSoldAt(), java.time.ZoneId.systemDefault()).toString() : "—",
                    s.getSoldRef() != null ? s.getSoldRef() : "—"));
        }
        return rows;
    }

    // ------------------------------------------------------------------ helpers

    private static final Comparator<StockBatch> BATCH_ORDER = Comparator
            .comparing((StockBatch b) -> b.getExpiryDate() != null && b.getExpiryDate().isBefore(LocalDate.now()))
            .thenComparing(StockBatch::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StockBatch::getReceivedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StockBatch::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private List<StockBatch> orderedBatches(Product product, WarehouseLocation location) {
        List<StockBatch> batches = new ArrayList<>(batchRepository.findByProductAndLocationAndQuantityGreaterThan(product, location, 0));
        batches.sort(BATCH_ORDER);
        return batches;
    }

    private List<StockSerial> pickAuto(Product product, WarehouseLocation location, int quantity) {
        List<StockSerial> available = serialRepository.findByProductAndLocationAndStatusOrderByIdAsc(product, location, SerialStatus.IN_STOCK);
        return new ArrayList<>(available.subList(0, Math.min(quantity, available.size())));
    }

    /** Splits off up to {@code quantity} units, earliest-expiry-first, as rows that can be re-homed. */
    private List<StockBatch> detachBatches(Product product, WarehouseLocation from, int quantity) {
        List<StockBatch> parts = new ArrayList<>();
        int left = quantity;
        for (StockBatch batch : orderedBatches(product, from)) {
            if (left <= 0) break;
            int take = Math.min(left, batch.getQuantity());
            left -= take;
            if (take == batch.getQuantity()) {
                parts.add(batch);
            } else {
                batch.setQuantity(batch.getQuantity() - take);
                batchRepository.save(batch);
                StockBatch piece = new StockBatch();
                piece.setProduct(batch.getProduct());
                piece.setBatchNumber(batch.getBatchNumber());
                piece.setExpiryDate(batch.getExpiryDate());
                piece.setUnitCost(batch.getUnitCost());
                piece.setReceivedAt(batch.getReceivedAt());
                piece.setSource(batch.getSource());
                piece.setQuantity(take);
                parts.add(batchRepository.save(piece));
            }
        }
        return parts;
    }

    /** Homes a batch row at a location, merging into the same batch already there. */
    private void placeBatch(StockBatch moving, WarehouseLocation to) {
        moving.setTransferId(null);
        StockBatch same = findSame(moving.getProduct(), to, moving.getBatchNumber(), moving.getExpiryDate(), moving.getId());
        if (same != null) {
            same.setQuantity(same.getQuantity() + moving.getQuantity());
            batchRepository.save(same);
            if (moving.getId() != null) batchRepository.delete(moving);
            return;
        }
        moving.setLocation(to);
        batchRepository.save(moving);
    }

    private StockBatch findSame(Product product, WarehouseLocation location, String batchNumber, LocalDate expiry, Long exceptId) {
        for (StockBatch b : batchRepository.findByProductAndLocationAndQuantityGreaterThan(product, location, 0)) {
            if (exceptId != null && exceptId.equals(b.getId())) continue;
            boolean sameNumber = b.getBatchNumber() == null ? batchNumber == null : b.getBatchNumber().equalsIgnoreCase(batchNumber);
            boolean sameExpiry = b.getExpiryDate() == null ? expiry == null : b.getExpiryDate().equals(expiry);
            if (sameNumber && sameExpiry) return b;
        }
        return null;
    }
}
