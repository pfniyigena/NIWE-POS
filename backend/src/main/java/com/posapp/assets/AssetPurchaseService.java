package com.posapp.assets;

import com.posapp.accounting.AccountingService;
import com.posapp.assets.FixedAssetService.PurchaseLink;
import com.posapp.assets.dto.AssetDtos.AssetDto;
import com.posapp.assets.dto.AssetDtos.PurchaseLineDto;
import com.posapp.assets.dto.AssetRequests.AssetRequest;
import com.posapp.assets.dto.AssetRequests.FromPurchaseRequest;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.inventory.PurchaseOrder;
import com.posapp.inventory.PurchaseOrderLine;
import com.posapp.inventory.PurchaseOrderRepository;
import com.posapp.inventory.PurchaseOrderStatus;
import com.posapp.inventory.SupplierPaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns goods received on a purchase order into fixed assets. The receipt already booked the net cost to Inventory (and the
 * tax to Input VAT), so registering an asset from a line only moves that cost from Inventory to Fixed Assets — it is never
 * counted twice. One asset is created per unit, each costing the line's net unit cost (after discount, without tax).
 *
 * <p>The product on the line must not track stock: otherwise the units would stay in the stock count (and its valuation)
 * while also being an asset.
 */
@Service
public class AssetPurchaseService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final FixedAssetRepository assetRepository;
    private final FixedAssetService assetService;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;

    public AssetPurchaseService(PurchaseOrderRepository purchaseOrderRepository, FixedAssetRepository assetRepository,
                                FixedAssetService assetService, AccountingService accountingService, AuditLogService auditLogService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.assetRepository = assetRepository;
        this.assetService = assetService;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
    }

    /** Every line with goods received, newest purchase order first, with how many units are already registered as assets. */
    @Transactional(readOnly = true)
    public List<PurchaseLineDto> purchaseLines() {
        List<PurchaseLineDto> rows = new ArrayList<>();
        List<PurchaseOrder> orders = purchaseOrderRepository.findByStatusIn(List.of(PurchaseOrderStatus.PARTIALLY_RECEIVED, PurchaseOrderStatus.RECEIVED));
        orders.sort((a, b) -> b.getOrderDate().compareTo(a.getOrderDate()));
        for (PurchaseOrder po : orders) {
            for (PurchaseOrderLine line : po.getLines()) {
                if (line.getQuantityReceived() <= 0) continue;
                rows.add(new PurchaseLineDto(line.getId(), po.getId(), po.getOrderNumber(),
                        po.getSupplier() != null ? po.getSupplier().getName() : null,
                        po.getOrderDate().atZone(ZoneOffset.UTC).toLocalDate(), line.getProductName(), line.getQuantityReceived(),
                        (int) assetRepository.countByPurchaseOrderLineId(line.getId()), unitNetCost(po, line), isStockTracked(line)));
            }
        }
        return rows;
    }

    @Transactional
    public List<AssetDto> register(FromPurchaseRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findFirstByLinesId(request.purchaseOrderLineId())
                .orElseThrow(() -> ApiException.notFound("Purchase order line not found: " + request.purchaseOrderLineId()));
        PurchaseOrderLine line = po.getLines().stream().filter(l -> l.getId().equals(request.purchaseOrderLineId())).findFirst().orElseThrow();
        if (line.getQuantityReceived() <= 0) {
            throw ApiException.badRequest("Nothing has been received on this line yet.");
        }
        if (isStockTracked(line)) {
            throw ApiException.badRequest("'" + line.getProductName() + "' tracks stock, so its units would stay in the stock count as well. "
                    + "Buy assets under a product that has Track stock switched off.");
        }
        int already = (int) assetRepository.countByPurchaseOrderLineId(line.getId());
        int quantity = request.quantity();
        if (quantity < 1 || already + quantity > line.getQuantityReceived()) {
            throw ApiException.badRequest("You can register " + (line.getQuantityReceived() - already) + " more unit(s) from this line ("
                    + line.getQuantityReceived() + " received, " + already + " already registered).");
        }
        BigDecimal unitCost = unitNetCost(po, line);
        if (unitCost.signum() <= 0) {
            throw ApiException.badRequest("This line has no cost to register.");
        }
        LocalDate date = request.purchaseDate() != null ? request.purchaseDate() : po.getOrderDate().atZone(ZoneOffset.UTC).toLocalDate();
        String baseName = request.name() != null && !request.name().isBlank() ? request.name().trim() : line.getProductName();
        PurchaseLink link = new PurchaseLink(po.getId(), po.getOrderNumber(), line.getId());
        List<AssetDto> created = new ArrayList<>();
        for (int i = 1; i <= quantity; i++) {
            String name = quantity > 1 ? baseName + " #" + (already + i) : baseName;
            AssetRequest asset = new AssetRequest(null, name, request.categoryId(), date, unitCost, null, null, null, null,
                    request.location(), request.assignedTo(), null, po.getSupplier() != null ? po.getSupplier().getName() : null,
                    request.notes(), AcquisitionFunding.NONE, null, request.prorateFirstMonth(), po.getBranchId());
            created.add(assetService.toDto(assetService.createAsset(asset, link)));
        }
        BigDecimal total = unitCost.multiply(BigDecimal.valueOf(quantity));
        accountingService.postAssetFromPurchase(line.getId(), "Asset(s) from purchase order " + po.getOrderNumber() + ": " + quantity + " x " + baseName, total, po.getBranchId());
        auditLogService.record("ASSET_FROM_PURCHASE", "Registered " + quantity + " asset(s) from purchase order " + po.getOrderNumber()
                + " (" + baseName + ", cost " + total + ")", "PurchaseOrder", po.getId());
        return created;
    }

    /** Net (no tax) cost of one unit: the line's net cost after discount, divided by the quantity ordered. */
    private static BigDecimal unitNetCost(PurchaseOrder po, PurchaseOrderLine line) {
        if (line.getQuantityOrdered() <= 0) return BigDecimal.ZERO;
        return SupplierPaymentService.lineNet(po.getDiscountPercent(), line)
                .divide(BigDecimal.valueOf(line.getQuantityOrdered()), 2, RoundingMode.HALF_UP);
    }

    private static boolean isStockTracked(PurchaseOrderLine line) {
        return line.getProduct() != null && line.getProduct().isTrackStock();
    }
}
