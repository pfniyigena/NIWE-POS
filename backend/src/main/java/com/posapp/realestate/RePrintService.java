package com.posapp.realestate;

import com.posapp.common.ApiException;
import com.posapp.credit.CustomerCreditService;
import com.posapp.credit.dto.CustomerPaymentDto;
import com.posapp.invoice.InvoiceService;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.dto.RealEstateDealDtos.DealDto;
import com.posapp.realestate.dto.RealEstateDealDtos.MilestoneDto;
import com.posapp.realestate.dto.RealEstateDtos.PhotoDto;
import com.posapp.realestate.dto.RealEstateDtos.PropertyDto;
import com.posapp.realestate.dto.RealEstatePrintDtos.PaymentRow;
import com.posapp.realestate.dto.RealEstateProjectDtos.ProjectDto;
import com.posapp.realestate.dto.RealEstateProjectDtos.UnitRow;
import com.posapp.realestate.dto.RealEstateProjectDtos.UnitSaleDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The printed papers: offer letter, reservation letter, sale agreement, payment receipts, the project price list and the property flyer. Each paper is built from the
 * record as it is now and refused (409) when the record has not reached the stage that paper belongs to.
 */
@Service
public class RePrintService {

    /** A paper and the name it is saved under. */
    public record Printed(byte[] content, String fileName) {
    }

    private final RealEstateAccess access;
    private final ReDealService dealService;
    private final RePropertyService propertyService;
    private final ReProjectService projectService;
    private final ReUnitSaleService unitSaleService;
    private final ReSettingsService settingsService;
    private final RePrintFiles files;
    private final InvoiceService invoiceService;
    private final CustomerCreditService creditService;

    public RePrintService(RealEstateAccess access, ReDealService dealService, RePropertyService propertyService, ReProjectService projectService, ReUnitSaleService unitSaleService,
                          ReSettingsService settingsService, RePrintFiles files, InvoiceService invoiceService, CustomerCreditService creditService) {
        this.access = access;
        this.dealService = dealService;
        this.propertyService = propertyService;
        this.projectService = projectService;
        this.unitSaleService = unitSaleService;
        this.settingsService = settingsService;
        this.files = files;
        this.invoiceService = invoiceService;
        this.creditService = creditService;
    }

    private static String slug(String s) {
        return s == null ? "document" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    // ------------------------------------------------------------------ deals

    private DealDto deal(Actor a, Long id) {
        DealDto d = dealService.get(a, id);
        if (d.stage() == DealStage.CANCELLED) throw ApiException.conflict("Deal " + d.code() + " is cancelled, so there is nothing to print.");
        return d;
    }

    @Transactional
    public Printed dealOffer(Actor a, Long id) {
        access.on();
        DealDto d = deal(a, id);
        PropertyDto p = propertyService.get(a, d.propertyId());
        return new Printed(files.offer(d, p, settingsService.entity().getOfferTerms(), files.currency()), "offer-" + slug(d.code()) + ".pdf");
    }

    @Transactional
    public Printed dealReservation(Actor a, Long id) {
        access.on();
        DealDto d = deal(a, id);
        if (d.stage() == DealStage.OFFER) throw ApiException.conflict("Reserve the property first; the reservation letter is printed once it is reserved.");
        PropertyDto p = propertyService.get(a, d.propertyId());
        return new Printed(files.dealReservation(d, p, settingsService.entity().getReservationTerms(), files.currency()), "reservation-" + slug(d.code()) + ".pdf");
    }

    @Transactional
    public Printed dealAgreement(Actor a, Long id) {
        access.on();
        DealDto d = deal(a, id);
        if (d.stage() != DealStage.AGREEMENT && d.stage() != DealStage.COMPLETED) throw ApiException.conflict("The sale agreement is printed once the agreement has been recorded on the deal.");
        PropertyDto p = propertyService.get(a, d.propertyId());
        String seller = d.sellerName() != null && !d.sellerName().isBlank() ? d.sellerName() : p.ownerName();
        return new Printed(files.dealAgreement(d, p, seller == null ? "The owner" : seller, settingsService.entity().getAgreementTerms(), files.currency()), "sale-agreement-" + slug(d.code()) + ".pdf");
    }

    /** The payments received on the deal's milestone invoices, newest first. */
    @Transactional(readOnly = true)
    public List<PaymentRow> dealPayments(Actor a, Long id) {
        access.on();
        DealDto d = dealService.get(a, id);
        List<PaymentRow> out = new ArrayList<>();
        for (MilestoneDto m : d.milestones() == null ? List.<MilestoneDto>of() : d.milestones()) {
            if (m.invoiceId() == null) continue;
            for (CustomerPaymentDto p : invoiceService.paymentHistory(m.invoiceId())) {
                if (p.id() != null) out.add(new PaymentRow(p.id(), p.paidAt(), p.amount(), p.method(), p.receiptNumber(), m.label() + (m.invoiceNumber() == null ? "" : " (" + m.invoiceNumber() + ")")));
            }
        }
        out.sort(Comparator.comparing(PaymentRow::paidAt).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public Printed dealReceipt(Actor a, Long id, Long paymentId) {
        access.on();
        PaymentRow row = dealPayments(a, id).stream().filter(r -> r.id().equals(paymentId)).findFirst().orElseThrow(() -> ApiException.notFound("That payment does not belong to this deal."));
        return new Printed(creditService.pdf(paymentId), "receipt-" + slug(row.receiptNumber() == null ? String.valueOf(paymentId) : row.receiptNumber()) + ".pdf");
    }

    // ------------------------------------------------------------------ unit sales

    private UnitSaleDto sale(Actor a, Long id) {
        UnitSaleDto s = unitSaleService.get(a, id);
        if (s.stage() == UnitSaleStage.CANCELLED) throw ApiException.conflict("Sale " + s.code() + " is cancelled, so there is nothing to print.");
        return s;
    }

    private static UnitRow unitRow(ProjectDto pr, Long unitId) {
        return pr.units().stream().filter(u -> u.id().equals(unitId)).findFirst().orElse(null);
    }

    @Transactional
    public Printed saleReservation(Actor a, Long id) {
        access.on();
        UnitSaleDto s = sale(a, id);
        ProjectDto pr = projectService.get(s.projectId());
        return new Printed(files.saleReservation(s, pr, unitRow(pr, s.unitId()), settingsService.entity().getReservationTerms(), files.currency()), "reservation-" + slug(s.code()) + ".pdf");
    }

    @Transactional
    public Printed saleAgreement(Actor a, Long id) {
        access.on();
        UnitSaleDto s = sale(a, id);
        if (s.stage() == UnitSaleStage.RESERVED) throw ApiException.conflict("The sale agreement is printed once the agreement has been signed and the unit is sold.");
        ProjectDto pr = projectService.get(s.projectId());
        return new Printed(files.saleAgreement(s, pr, unitRow(pr, s.unitId()), settingsService.entity().getAgreementTerms(), files.currency()), "sale-agreement-" + slug(s.code()) + ".pdf");
    }

    /** The payments received on the sale's invoice, newest first. */
    @Transactional(readOnly = true)
    public List<PaymentRow> salePayments(Actor a, Long id) {
        access.on();
        UnitSaleDto s = unitSaleService.get(a, id);
        if (s.invoice() == null) return List.of();
        List<PaymentRow> out = new ArrayList<>();
        for (CustomerPaymentDto p : invoiceService.paymentHistory(s.invoice().id())) {
            if (p.id() != null) out.add(new PaymentRow(p.id(), p.paidAt(), p.amount(), p.method(), p.receiptNumber(), "Invoice " + s.invoice().invoiceNumber()));
        }
        out.sort(Comparator.comparing(PaymentRow::paidAt).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public Printed saleReceipt(Actor a, Long id, Long paymentId) {
        access.on();
        PaymentRow row = salePayments(a, id).stream().filter(r -> r.id().equals(paymentId)).findFirst().orElseThrow(() -> ApiException.notFound("That payment does not belong to this sale."));
        return new Printed(creditService.pdf(paymentId), "receipt-" + slug(row.receiptNumber() == null ? String.valueOf(paymentId) : row.receiptNumber()) + ".pdf");
    }

    /** The buyer statement of a sold unit. */
    @Transactional(readOnly = true)
    public Printed statement(Actor a, Long id) {
        access.on();
        UnitSaleDto s = sale(a, id);
        if (s.stage() != UnitSaleStage.SOLD && s.stage() != UnitSaleStage.COMPLETED) throw ApiException.conflict("The statement is printed once the agreement has been signed and the unit is sold.");
        ProjectDto pr = projectService.get(s.projectId());
        return new Printed(files.statement(s, pr, unitRow(pr, s.unitId()), salePayments(a, id), files.currency()), "statement-" + slug(s.code()) + ".pdf");
    }

    // ------------------------------------------------------------------ price list and flyer

    private String contact() {
        ReSettings s = settingsService.entity();
        StringBuilder b = new StringBuilder();
        for (String part : new String[]{s.getWebsitePhone(), s.getWebsiteEmail()}) if (part != null && !part.isBlank()) b.append(b.length() == 0 ? "" : "  |  ").append(part.trim());
        return b.length() > 0 ? b.toString() : files.contactLine();
    }

    /** The units of a project to put on a price list: the ones still available, or every unit that is not withheld. */
    @Transactional
    public Printed priceList(Long projectId, boolean all) {
        access.on();
        ProjectDto pr = projectService.get(projectId);
        List<UnitRow> units = pr.units().stream().filter(u -> u.status() != UnitStatus.WITHHELD && (all || u.status() == UnitStatus.AVAILABLE)).toList();
        return new Printed(files.priceList(pr, units, all, contact(), files.currency()), "price-list-" + slug(pr.code()) + ".pdf");
    }

    @Transactional
    public Printed flyer(Actor a, Long propertyId) {
        access.on();
        PropertyDto p = propertyService.get(a, propertyId);
        List<PhotoDto> ordered = new ArrayList<>(p.photos() == null ? List.<PhotoDto>of() : p.photos());
        ordered.sort(Comparator.comparing((PhotoDto x) -> !x.cover()));
        List<byte[]> data = new ArrayList<>();
        for (PhotoDto ph : ordered) {
            if (data.size() >= 4) break;
            try {
                data.add(propertyService.photo(propertyId, ph.id()).data());
            } catch (ApiException ignore) {
                // a photo that cannot be read is left out
            }
        }
        String agentContact = contact();
        return new Printed(files.flyer(p, data, agentContact, files.currency()), "flyer-" + slug(p.code()) + ".pdf");
    }
}
