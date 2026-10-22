package com.posapp.papers;

import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.inventory.StockReceipt;
import com.posapp.inventory.StockReceiptRepository;
import com.posapp.inventory.Supplier;
import com.posapp.inventory.SupplierRepository;
import com.posapp.inventory.PurchaseOrderService;
import com.posapp.inventory.StockCountService;
import com.posapp.inventory.dto.PurchaseOrderDto;
import com.posapp.inventory.dto.StockCountDto;
import com.posapp.papers.PaperFiles.Delivery;
import com.posapp.papers.PaperFiles.Receipt;
import com.posapp.salesorder.SalesOrder;
import com.posapp.salesorder.SalesOrderFulfillment;
import com.posapp.salesorder.SalesOrderFulfillmentRepository;
import com.posapp.salesorder.SalesOrderRepository;
import com.posapp.salesorder.SalesOrderService;
import com.posapp.salesorder.dto.SalesOrderDto;
import com.posapp.warehouse.StockTransfer;
import com.posapp.warehouse.StockTransferRepository;
import com.posapp.warehouse.dto.StockTransferDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Loads what a paper needs and refuses a paper that makes no sense yet (a delivery note for a draft order, a sheet for a cancelled count). */
@Service
public class PaperService {

    private final SalesOrderService salesOrderService;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderFulfillmentRepository fulfillmentRepository;
    private final CustomerRepository customerRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final StockReceiptRepository receiptRepository;
    private final SupplierRepository supplierRepository;
    private final StockCountService stockCountService;
    private final StockTransferRepository transferRepository;
    private final BranchAccessService branchAccessService;
    private final PaperFiles files;

    public PaperService(SalesOrderService salesOrderService, SalesOrderRepository salesOrderRepository, SalesOrderFulfillmentRepository fulfillmentRepository, CustomerRepository customerRepository,
                        PurchaseOrderService purchaseOrderService, StockReceiptRepository receiptRepository, SupplierRepository supplierRepository, StockCountService stockCountService,
                        StockTransferRepository transferRepository, BranchAccessService branchAccessService, PaperFiles files) {
        this.salesOrderService = salesOrderService;
        this.salesOrderRepository = salesOrderRepository;
        this.fulfillmentRepository = fulfillmentRepository;
        this.customerRepository = customerRepository;
        this.purchaseOrderService = purchaseOrderService;
        this.receiptRepository = receiptRepository;
        this.supplierRepository = supplierRepository;
        this.stockCountService = stockCountService;
        this.transferRepository = transferRepository;
        this.branchAccessService = branchAccessService;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public byte[] deliveryNote(Long id) {
        SalesOrderDto so = salesOrderService.getById(id);
        if ("DRAFT".equals(so.status())) throw ApiException.badRequest("Confirm sale order " + so.orderNumber() + " before printing its delivery note.");
        if ("CANCELLED".equals(so.status())) throw ApiException.badRequest("Sale order " + so.orderNumber() + " is cancelled, so there is nothing to deliver.");
        Customer c = so.customerId() == null ? null : customerRepository.findById(so.customerId()).orElse(null);
        SalesOrder entity = salesOrderRepository.findById(id).orElseThrow(() -> ApiException.notFound("Sale order not found."));
        List<Delivery> done = fulfillmentRepository.findBySalesOrderOrderByCreatedAtAsc(entity).stream()
                .map((SalesOrderFulfillment f) -> new Delivery(f.getCreatedAt(), f.getProductName(), f.getQuantityFulfilled(), place(f.getWarehouseName(), f.getLocationName()))).toList();
        return files.deliveryNote(so, c == null ? null : c.getPhone(), c == null ? null : c.getEmail(), done);
    }

    private static String place(String warehouse, String location) {
        if (warehouse == null || warehouse.isBlank()) return location;
        return location == null || location.isBlank() ? warehouse : warehouse + " / " + location;
    }

    @Transactional(readOnly = true)
    public byte[] goodsReceived(Long id) {
        PurchaseOrderDto po = purchaseOrderService.getById(id);
        if ("DRAFT".equals(po.status())) throw ApiException.badRequest("Send purchase order " + po.orderNumber() + " before printing its goods-received note.");
        if ("CANCELLED".equals(po.status())) throw ApiException.badRequest("Purchase order " + po.orderNumber() + " is cancelled, so nothing is expected.");
        Supplier s = po.supplierId() == null ? null : supplierRepository.findById(po.supplierId()).orElse(null);
        List<Receipt> got = receiptRepository.findByPurchaseOrderIdOrderByCreatedAtAsc(id).stream().map((StockReceipt r) -> new Receipt(r.getCreatedAt(), r.getProductName(), r.getQuantityReceived())).toList();
        return files.goodsReceived(po, s == null ? null : s.getContactName(), s == null ? null : s.getPhone(), got);
    }

    @Transactional(readOnly = true)
    public byte[] countSheet(Long id, boolean showSystem) {
        StockCountDto c = stockCountService.getById(id);
        if ("CANCELLED".equals(c.status())) throw ApiException.badRequest("Stock count " + c.countNumber() + " is cancelled.");
        return files.countSheet(c, showSystem);
    }

    @Transactional(readOnly = true)
    public byte[] transferNote(Long id) {
        StockTransfer t = transferRepository.findById(id).orElseThrow(() -> ApiException.notFound("Stock transfer not found."));
        branchAccessService.assertCanAccess(t.getFromBranchId());
        return files.transferNote(StockTransferDto.from(t));
    }
}
