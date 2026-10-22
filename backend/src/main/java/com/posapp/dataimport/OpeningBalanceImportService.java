package com.posapp.dataimport;

import com.posapp.bill.BillService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.dataimport.dto.ImportResultDto;
import com.posapp.dataimport.dto.SkippedRowDto;
import com.posapp.inventory.Supplier;
import com.posapp.inventory.SupplierRepository;
import com.posapp.bill.BillRepository;
import com.posapp.invoice.InvoiceRepository;
import com.posapp.invoice.InvoiceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.posapp.dataimport.ImportSupport.normalized;
import static com.posapp.dataimport.ImportSupport.requireDate;
import static com.posapp.dataimport.ImportSupport.requireDecimal;
import static com.posapp.dataimport.ImportSupport.text;

/**
 * What customers already owed, and what the business already owed suppliers, on the day it started using the system. Each row becomes one opening invoice (customer) or
 * opening bill (supplier) dated {@code asOf}, posted against Owner's Equity, collected and paid through the normal screens. One opening balance per customer / supplier;
 * the customer or supplier must already exist (import them first).
 */
@Service
public class OpeningBalanceImportService {

    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final InvoiceService invoiceService;
    private final BillService billService;
    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final com.posapp.controls.AccountingLockService lockService;

    public OpeningBalanceImportService(CustomerRepository customerRepository, SupplierRepository supplierRepository, InvoiceService invoiceService, BillService billService,
                                       InvoiceRepository invoiceRepository, BillRepository billRepository, com.posapp.controls.AccountingLockService lockService) {
        this.lockService = lockService;
        this.invoiceRepository = invoiceRepository;
        this.billRepository = billRepository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.invoiceService = invoiceService;
        this.billService = billService;
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * The same rules the invoice / bill services enforce, checked here first. A service that throws marks the whole import transaction rollback-only even when the
     * importer catches the exception, so a bad row must be turned away before the service is called, never by it.
     */
    private void checkAmountAndDate(BigDecimal amount, LocalDate asOf) {
        lockService.assertOpen(asOf);
        if (amount.signum() <= 0) throw ApiException.badRequest("The amount must be more than zero.");
        if (amount.stripTrailingZeros().scale() > 2) throw ApiException.badRequest("The amount can have at most two decimals.");
        if (asOf.isAfter(LocalDate.now())) throw ApiException.badRequest("asOf cannot be in the future.");
    }

    private Customer findCustomer(String name, String phone, String email) {
        List<Customer> found = customerRepository.findAll().stream().filter(c -> !c.isWalkIn()).filter(c -> {
            if (email != null && c.getEmail() != null && c.getEmail().equalsIgnoreCase(email)) return true;
            return c.getName() != null && c.getName().equalsIgnoreCase(name);
        }).toList();
        if (found.size() > 1 && phone != null) {
            List<Customer> byPhone = found.stream().filter(c -> java.util.Objects.equals(normalized(c.getPhone()), normalized(phone))).toList();
            if (!byPhone.isEmpty()) found = byPhone;
        }
        if (found.isEmpty()) throw ApiException.notFound("No customer named '" + name + "'. Import the customers first.");
        if (found.size() > 1) throw ApiException.conflict("More than one customer is named '" + name + "'. Add the phone column to say which one.");
        return found.get(0);
    }

    private Supplier findSupplier(String name, String phone) {
        List<Supplier> found = supplierRepository.findAll().stream().filter(s -> s.getName() != null && s.getName().equalsIgnoreCase(name)).toList();
        if (found.size() > 1 && phone != null) {
            List<Supplier> byPhone = found.stream().filter(s -> java.util.Objects.equals(normalized(s.getPhone()), normalized(phone))).toList();
            if (!byPhone.isEmpty()) found = byPhone;
        }
        if (found.isEmpty()) throw ApiException.notFound("No supplier named '" + name + "'. Import the suppliers first.");
        if (found.size() > 1) throw ApiException.conflict("More than one supplier is named '" + name + "'. Add the phone column to say which one.");
        return found.get(0);
    }

    /** Columns: customer (name, required), amount (required), asOf (required, 2026-01-31), phone, email, dueDate, reference. */
    @Transactional
    public ImportResultDto importCustomerBalances(MultipartFile file, boolean dryRun) {
        List<Map<String, String>> rows = ImportSupport.readRows(file);
        ImportSupport.requireColumns(rows, "customer", "amount", "asOf");
        List<SkippedRowDto> skipped = new ArrayList<>();
        List<SkippedRowDto> accepted = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int n = 0, imported = 0;
        for (Map<String, String> row : rows) {
            n++;
            try {
                String name = ImportSupport.requireText(row, "customer");
                BigDecimal amount = requireDecimal(row, "amount");
                LocalDate asOf = requireDate(row, "asOf");
                LocalDate due = ImportSupport.dateOrNull(row, "dueDate");
                if (due != null && due.isBefore(asOf)) throw ApiException.badRequest("dueDate cannot be before asOf");
                checkAmountAndDate(amount, asOf);
                Customer c = findCustomer(name, text(row, "phone"), text(row, "email"));
                if (invoiceRepository.existsOpeningBalance(c.getId())) throw ApiException.conflict(c.getName() + " already has an opening balance. Void it first if it was wrong.");
                invoiceService.generateOpening(c.getId(), amount, asOf, due, text(row, "reference"));
                imported++;
                total = total.add(amount);
                accepted.add(new SkippedRowDto(n, c.getName() + " owes " + money(amount) + " as of " + asOf + (due != null ? ", due " + due : "")));
            } catch (ApiException ex) {
                skipped.add(new SkippedRowDto(n, ex.getMessage()));
            } catch (RuntimeException ex) {
                skipped.add(new SkippedRowDto(n, "Unexpected error: " + ex.getMessage()));
            }
        }
        return ImportSupport.result(dryRun, n, imported, skipped, accepted, imported > 0 ? List.of("Total customers owe you: " + money(total)) : List.of());
    }

    /** Columns: supplier (name, required), amount (required), asOf (required, 2026-01-31), phone, reference. */
    @Transactional
    public ImportResultDto importSupplierBalances(MultipartFile file, boolean dryRun) {
        List<Map<String, String>> rows = ImportSupport.readRows(file);
        ImportSupport.requireColumns(rows, "supplier", "amount", "asOf");
        List<SkippedRowDto> skipped = new ArrayList<>();
        List<SkippedRowDto> accepted = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int n = 0, imported = 0;
        for (Map<String, String> row : rows) {
            n++;
            try {
                String name = ImportSupport.requireText(row, "supplier");
                BigDecimal amount = requireDecimal(row, "amount");
                LocalDate asOf = requireDate(row, "asOf");
                checkAmountAndDate(amount, asOf);
                Supplier s = findSupplier(name, text(row, "phone"));
                if (billRepository.existsOpeningBalance(s.getId())) throw ApiException.conflict(s.getName() + " already has an opening balance. Void it first if it was wrong.");
                billService.generateOpening(s.getId(), amount, asOf, text(row, "reference"));
                imported++;
                total = total.add(amount);
                accepted.add(new SkippedRowDto(n, "You owe " + s.getName() + " " + money(amount) + " as of " + asOf));
            } catch (ApiException ex) {
                skipped.add(new SkippedRowDto(n, ex.getMessage()));
            } catch (RuntimeException ex) {
                skipped.add(new SkippedRowDto(n, "Unexpected error: " + ex.getMessage()));
            }
        }
        return ImportSupport.result(dryRun, n, imported, skipped, accepted, imported > 0 ? List.of("Total you owe suppliers: " + money(total)) : List.of());
    }

    public byte[] customerBalancesTemplateCsv() {
        return ("customer,amount,asOf,dueDate,phone,email,reference\n"
                + "Masindi Traders Ltd,1250000,2026-01-31,2026-02-28,0772987654,accounts@masinditraders.co.ug,Ledger page 12\n"
                + "John Okello,45000,2026-01-31,,0700112233,,\n").getBytes(StandardCharsets.UTF_8);
    }

    public byte[] supplierBalancesTemplateCsv() {
        return ("supplier,amount,asOf,phone,reference\n"
                + "Kampala Wholesalers Ltd,3200000,2026-01-31,0772345678,Invoice 4471\n"
                + "Green Farms Co-op,150000,2026-01-31,,\n").getBytes(StandardCharsets.UTF_8);
    }
}
