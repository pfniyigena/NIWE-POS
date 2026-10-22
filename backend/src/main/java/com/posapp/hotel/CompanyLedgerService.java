package com.posapp.hotel;

import com.posapp.accounting.AccountingService;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.CustomerRepository;
import com.posapp.hotel.dto.HotelDtos.CompanyBillDto;
import com.posapp.hotel.dto.HotelDtos.CompanyDto;
import com.posapp.hotel.dto.HotelDtos.CompanyReceiptDto;
import com.posapp.hotel.dto.HotelDtos.CompanySummaryDto;
import com.posapp.hotel.dto.HotelRequests.CompanyReceiptRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What companies owe the hotel for stays billed to them (the city ledger): the list, the money received, and a summary per company. */
@Service
public class CompanyLedgerService {

    private final CompanyBillRepository billRepository;
    private final CompanyReceiptRepository receiptRepository;
    private final HotelFolioService folioService;
    private final AccountingService accountingService;
    private final AuditLogService auditLogService;
    private final HotelClock clock;
    private final CustomerRepository customerRepository;
    private final com.posapp.credit.CustomerCreditService creditService;

    public CompanyLedgerService(CompanyBillRepository billRepository, CompanyReceiptRepository receiptRepository, HotelFolioService folioService,
                                AccountingService accountingService, AuditLogService auditLogService, HotelClock clock,
                                CustomerRepository customerRepository, com.posapp.credit.CustomerCreditService creditService) {
        this.customerRepository = customerRepository;
        this.creditService = creditService;
        this.billRepository = billRepository;
        this.receiptRepository = receiptRepository;
        this.folioService = folioService;
        this.accountingService = accountingService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** Company bills, newest first. status: OPEN, PAID or OVERDUE; q matches the number, company, guest, or booking. */
    @Transactional(readOnly = true)
    public List<CompanyBillDto> list(String q, String status, Long companyId) {
        LocalDate today = clock.today();
        List<CompanyBillDto> out = new ArrayList<>();
        for (CompanyBill b : billRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("id")))) {
            CompanyBillDto d = toDto(b, today, false);
            if (companyId != null && !companyId.equals(d.companyId())) continue;
            if (status != null && !status.isBlank()) {
                String s = status.trim().toUpperCase();
                boolean match = switch (s) {
                    case "OVERDUE" -> d.overdue();
                    case "OPEN", "PAID" -> d.status().equals(s);
                    default -> true;
                };
                if (!match) continue;
            }
            if (!com.posapp.common.ListPaging.has(q, d.billNumber(), d.companyName(), d.guestName(), d.bookingNumber(), d.description())) continue;
            out.add(d);
        }
        return out;
    }

    /** Customers set up for credit, by name — the companies a bill can be sent to. */
    @Transactional(readOnly = true)
    public List<CompanyDto> companies() {
        List<CompanyDto> out = new ArrayList<>();
        for (var c : customerRepository.findAll()) {
            if (!c.isCreditEnabled() || c.isWalkIn()) continue;
            BigDecimal owing = creditService.outstandingBalance(c.getId()).add(billRepository.outstandingFor(c.getId()));
            out.add(new CompanyDto(c.getId(), c.getName(), c.getTaxId(), c.getCreditLimit(), owing, c.getDefaultCreditTermDays()));
        }
        out.sort(Comparator.comparing(CompanyDto::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Transactional(readOnly = true)
    public CompanyBillDto get(Long id) {
        return toDto(getOrThrow(id), clock.today(), true);
    }

    /** Who owes what: one row per company with an account, largest balance first. */
    @Transactional(readOnly = true)
    public List<CompanySummaryDto> summary() {
        LocalDate today = clock.today();
        Map<Long, BigDecimal[]> sums = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, Integer> open = new LinkedHashMap<>();
        for (CompanyBill b : billRepository.findAll()) {
            Long id = b.getCompany().getId();
            names.put(id, b.getCompany().getName());
            BigDecimal[] s = sums.computeIfAbsent(id, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            s[0] = s[0].add(b.getAmount());
            s[1] = s[1].add(b.getPaid());
            BigDecimal owing = b.outstanding();
            if (owing.signum() > 0) {
                open.merge(id, 1, Integer::sum);
                if (b.getDueDate() != null && b.getDueDate().isBefore(today)) s[2] = s[2].add(owing);
            }
        }
        List<CompanySummaryDto> out = new ArrayList<>();
        for (var e : sums.entrySet()) {
            BigDecimal[] s = e.getValue();
            out.add(new CompanySummaryDto(e.getKey(), names.get(e.getKey()), s[0], s[1], s[0].subtract(s[1]), s[2], open.getOrDefault(e.getKey(), 0)));
        }
        out.sort(Comparator.comparing(CompanySummaryDto::outstanding).reversed().thenComparing(CompanySummaryDto::companyName));
        return out;
    }

    @Transactional
    public CompanyBillDto receive(Long id, CompanyReceiptRequest request) {
        CompanyBill bill = getOrThrow(id);
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : HotelFolioService.money(request.amount());
        if (amount.signum() <= 0) throw ApiException.badRequest("The amount must be greater than zero");
        if (amount.compareTo(bill.outstanding()) > 0) {
            throw ApiException.badRequest("Only " + bill.outstanding() + " is still owed on " + bill.getBillNumber());
        }
        String code = folioService.payableMethod(request.method());
        CompanyReceipt r = new CompanyReceipt();
        r.setCompanyBill(bill);
        r.setMethod(code);
        r.setAmount(amount);
        r.setReference(request.reference() == null || request.reference().isBlank() ? null : request.reference().trim());
        r.setReceivedOn(request.receivedOn() == null ? clock.today() : request.receivedOn());
        CompanyReceipt saved = receiptRepository.save(r);
        bill.setPaid(bill.getPaid().add(amount));
        if (bill.outstanding().signum() == 0) bill.setStatus("PAID");
        billRepository.save(bill);
        accountingService.postHotelCompanyReceipt(saved.getId(), "Received " + bill.getBillNumber() + " " + bill.getCompany().getName(), amount);
        auditLogService.record("HOTEL_COMPANY_PAID", bill.getBillNumber() + ": " + amount + " from " + bill.getCompany().getName() + " (" + code + ")", "CompanyBill", bill.getId());
        return toDto(bill, clock.today(), true);
    }

    CompanyBill getOrThrow(Long id) {
        return billRepository.findById(id).orElseThrow(() -> ApiException.notFound("Company bill not found: " + id));
    }

    private CompanyBillDto toDto(CompanyBill b, LocalDate today, boolean withReceipts) {
        List<CompanyReceiptDto> receipts = withReceipts
                ? receiptRepository.findByCompanyBillIdOrderByIdAsc(b.getId()).stream()
                        .map(r -> new CompanyReceiptDto(r.getId(), r.getMethod(), r.getAmount(), r.getReference(), r.getReceivedOn())).toList()
                : List.of();
        BigDecimal outstanding = b.outstanding();
        boolean overdue = outstanding.signum() > 0 && b.getDueDate() != null && b.getDueDate().isBefore(today);
        return new CompanyBillDto(b.getId(), b.getBillNumber(), b.getCompany().getId(), b.getCompany().getName(), b.getBooking().getId(),
                b.getBooking().getBookingNumber(), b.getBooking().getGuest().getFullName(), b.getBillNo(), b.getDescription(), b.getAmount(), b.getPaid(),
                outstanding, b.getStatus(), b.getIssuedOn(), b.getDueDate(), overdue, receipts);
    }
}
