package com.posapp.onlinestore;

import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.common.PdfKit;
import com.posapp.i18n.I18n;
import com.posapp.onlinestore.OnlineStoreDtos.DriverDto;
import com.posapp.onlinestore.OnlineStoreDtos.DriverRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Delivery work of the shop (v1.52.0): the list of drivers, the delivery slip of one order and the run sheet of one driver. Which driver takes an order is
 * chosen in {@link OnlineOrderService#assignDriver}; here are the drivers themselves and the two printed papers.
 */
@Service
public class OnlineDeliveryService {

    /** Delivery orders that are still to be delivered. */
    static final Set<String> TO_DELIVER = Set.of("ACCEPTED", "PACKED", "OUT_FOR_DELIVERY");

    private final OnlineDriverRepository drivers;
    private final OnlineOrderRepository orders;
    private final OnlineStoreService storeService;
    private final PdfKit kit;
    private final AuditLogService auditLogService;

    public OnlineDeliveryService(OnlineDriverRepository drivers, OnlineOrderRepository orders, OnlineStoreService storeService, PdfKit kit, AuditLogService auditLogService) {
        this.drivers = drivers;
        this.orders = orders;
        this.storeService = storeService;
        this.kit = kit;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ drivers

    @Transactional(readOnly = true)
    public List<DriverDto> drivers() {
        List<OnlineOrder> all = orders.findAllByOrderByCreatedAtDesc();
        return drivers.findAllByOrderByActiveDescNameAsc().stream().map(d -> dto(d, all.stream()
                .filter(o -> d.getId().equals(o.getDriverId()) && TO_DELIVER.contains(o.getStatus())).count())).toList();
    }

    @Transactional
    public DriverDto save(Long id, DriverRequest in) {
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty()) throw ApiException.badRequest(I18n.t("Enter the driver's name."));
        OnlineDriver d = id == null ? new OnlineDriver() : drivers.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Driver not found")));
        d.setName(name);
        d.setPhone(blank(in.phone()));
        d.setNotes(blank(in.notes()));
        if (in.active() != null) d.setActive(in.active());
        drivers.save(d);
        auditLogService.record("SHOP_DRIVER_SAVED", "Saved the delivery driver " + name);
        return dto(d, 0);
    }

    @Transactional
    public void delete(Long id) {
        OnlineDriver d = drivers.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Driver not found")));
        boolean busy = orders.findAllByOrderByCreatedAtDesc().stream().anyMatch(o -> id.equals(o.getDriverId()) && TO_DELIVER.contains(o.getStatus()));
        if (busy) throw ApiException.badRequest(I18n.t("This driver still has orders to deliver. Give them to another driver first, or switch the driver off."));
        drivers.delete(d);
        auditLogService.record("SHOP_DRIVER_DELETED", "Deleted the delivery driver " + d.getName());
    }

    private static DriverDto dto(OnlineDriver d, long open) {
        return new DriverDto(d.getId(), d.getName(), d.getPhone(), d.getNotes(), d.isActive(), open);
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    // ------------------------------------------------------------------ papers

    /** What the driver collects at the door: nothing when the order was paid online. */
    static BigDecimal toCollect(OnlineOrder o) {
        return "PAID".equals(o.getPaymentStatus()) && o.getPaidAmount() != null ? BigDecimal.ZERO : o.getTotal();
    }

    private static String qty(BigDecimal q) {
        return q == null ? "" : q.stripTrailingZeros().toPlainString();
    }

    /** The delivery slip of one order: who, where, what, how much to collect, with signature lines for the driver and the customer. */
    @Transactional(readOnly = true)
    public byte[] slip(Long id) {
        OnlineOrder o = orders.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Order not found")));
        if (!"DELIVERY".equals(o.getDeliveryMethod())) throw ApiException.badRequest(I18n.t("This is a pick-up order"));
        String cur = storeService.currencyCode();
        return kit.build(false, I18n.t("Delivery slip") + " " + o.getCode(), doc -> {
            kit.head(doc, I18n.t("Delivery slip"), I18n.t("Order {0}", o.getCode()));
            doc.add(PdfKit.facts(new String[][]{
                    {I18n.t("Customer"), o.getCustomerName()},
                    {I18n.t("Phone"), o.getCustomerPhone()},
                    {I18n.t("Address"), o.getCustomerAddress()},
                    {I18n.t("Area"), o.getZoneName()},
                    {I18n.t("Note"), o.getNotes()},
                    {I18n.t("Driver"), o.getDriverName() == null ? "" : o.getDriverName() + (o.getDriverPhone() == null ? "" : " · " + o.getDriverPhone())},
                    {I18n.t("Placed"), o.getCreatedAt() == null ? "" : java.time.LocalDate.ofInstant(o.getCreatedAt(), java.time.ZoneId.systemDefault()).format(I18n.shortDay())},
            }));
            PdfKit.section(doc, I18n.t("Goods to hand over"));
            PdfPTable t = new PdfPTable(new float[]{0.8f, 4.5f, 1.3f, 1.4f});
            t.setWidthPercentage(100);
            PdfKit.headerCells(t, new String[]{I18n.t("Qty"), I18n.t("Item"), I18n.t("Price"), I18n.t("Total")},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
            for (OnlineOrderLine l : o.getLines()) {
                t.addCell(PdfKit.cell(qty(l.getQuantity()), PdfKit.plain(10), Element.ALIGN_LEFT, null));
                t.addCell(PdfKit.cell(l.getName(), PdfKit.plain(10), Element.ALIGN_LEFT, null));
                t.addCell(PdfKit.cell(PdfKit.money(l.getUnitPrice()), PdfKit.plain(10), Element.ALIGN_RIGHT, null));
                t.addCell(PdfKit.cell(PdfKit.money(l.getLineTotal()), PdfKit.plain(10), Element.ALIGN_RIGHT, null));
            }
            doc.add(t);
            PdfPTable sums = new PdfPTable(new float[]{4.5f, 1.5f});
            sums.setWidthPercentage(60);
            sums.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sum(sums, I18n.t("Items"), o.getSubtotal(), false);
            if (o.getDiscountAmount() != null && o.getDiscountAmount().signum() > 0) sum(sums, I18n.t("Discount"), o.getDiscountAmount().negate(), false);
            if (o.getDeliveryFee() != null && o.getDeliveryFee().signum() > 0) sum(sums, I18n.t("Delivery fee"), o.getDeliveryFee(), false);
            sum(sums, I18n.t("Order total"), o.getTotal(), true);
            doc.add(sums);

            BigDecimal collect = toCollect(o);
            Paragraph p = new Paragraph(collect.signum() > 0 ? (o.getReceivedAt() != null ? I18n.t("Collected from the customer: {0}", PdfKit.amount(cur, collect)) : I18n.t("To collect from the customer: {0}", PdfKit.amount(cur, collect)))
                    : I18n.t("Already paid online: nothing to collect."), PdfKit.bold(13));
            p.setSpacingBefore(14);
            doc.add(p);
            if (o.getReceivedAt() != null) {
                String how = "CODE".equals(o.getReceivedVia()) ? I18n.t("the customer's code was typed") : "DRIVER".equals(o.getReceivedVia())
                        ? I18n.t("the customer signed on the driver's phone") : I18n.t("the customer confirmed it in the shop");
                Paragraph r = new Paragraph(I18n.t("Received on {0}: {1}", java.time.LocalDateTime.ofInstant(o.getReceivedAt(), java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), how) + (o.getSignedName() == null ? "" : " (" + o.getSignedName() + ")"), PdfKit.bold(11));
                r.setSpacingBefore(10);
                doc.add(r);
                if (o.getSignature() != null) {
                    try {
                        byte[] png = java.util.Base64.getDecoder().decode(o.getSignature().substring(o.getSignature().indexOf(',') + 1));
                        com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(png);
                        img.scaleToFit(220, 90);
                        doc.add(img);
                    } catch (Exception e) {
                        // a signature that cannot be drawn is simply left out; the line above still says it was received
                    }
                }
            } else {
                Paragraph hint = new Paragraph(I18n.t("Ask the customer for the delivery code and type it in the app, or let the customer sign here or on the phone."), PdfKit.muted(9));
                hint.setSpacingBefore(6);
                doc.add(hint);
                PdfKit.signatures(doc, I18n.t("Driver"), I18n.t("Received by the customer"));
            }
        });
    }

    private static void sum(PdfPTable t, String label, BigDecimal v, boolean strong) {
        t.addCell(noBorder(label, strong, Element.ALIGN_RIGHT));
        t.addCell(noBorder(PdfKit.money(v), strong, Element.ALIGN_RIGHT));
    }

    private static PdfPCell noBorder(String text, boolean strong, int align) {
        PdfPCell c = PdfKit.cell(text, strong ? PdfKit.bold(10.5f) : PdfKit.plain(10), align, null);
        c.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        return c;
    }

    /**
     * The run sheet of one driver: every delivery order assigned to the driver that is still to be delivered, by area, with what to collect and a column to
     * tick. driverId 0 lists the delivery orders nobody has taken yet.
     */
    @Transactional(readOnly = true)
    public byte[] runSheet(Long driverId) {
        String who;
        if (driverId == 0) {
            who = I18n.t("Not assigned yet");
        } else {
            who = drivers.findById(driverId).map(OnlineDriver::getName).orElseThrow(() -> ApiException.notFound(I18n.t("Driver not found")));
        }
        List<OnlineOrder> list = orders.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> "DELIVERY".equals(o.getDeliveryMethod()) && TO_DELIVER.contains(o.getStatus()))
                .filter(o -> driverId == 0 ? o.getDriverId() == null : driverId.equals(o.getDriverId()))
                .sorted(Comparator.comparing((OnlineOrder o) -> o.getZoneName() == null ? "" : o.getZoneName()).thenComparing(OnlineOrder::getCreatedAt)).toList();
        String cur = storeService.currencyCode();
        BigDecimal cash = list.stream().map(OnlineDeliveryService::toCollect).reduce(BigDecimal.ZERO, BigDecimal::add);
        return kit.build(true, I18n.t("Delivery run sheet") + " · " + who, doc -> {
            kit.head(doc, I18n.t("Delivery run sheet"), I18n.t("Driver: {0}", who) + "  |  " + java.time.LocalDate.now().format(I18n.longDay()));
            if (list.isEmpty()) {
                doc.add(new Paragraph(I18n.t("Nothing to deliver."), PdfKit.plain(11)));
                return;
            }
            PdfPTable t = new PdfPTable(new float[]{0.4f, 1.1f, 2.2f, 3f, 3.2f, 1.5f, 0.6f});
            t.setWidthPercentage(100);
            PdfKit.headerCells(t, new String[]{"#", I18n.t("Order"), I18n.t("Customer"), I18n.t("Address"), I18n.t("Goods"), I18n.t("To collect"), "✓"},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_CENTER});
            int n = 0;
            for (OnlineOrder o : list) {
                n++;
                StringBuilder goods = new StringBuilder();
                for (OnlineOrderLine l : o.getLines()) goods.append(goods.length() == 0 ? "" : "\n").append(qty(l.getQuantity())).append(" × ").append(l.getName());
                String addr = (o.getZoneName() == null ? "" : o.getZoneName() + "\n") + PdfKit.nz(o.getCustomerAddress()) + (o.getNotes() == null || o.getNotes().isBlank() ? "" : "\n“" + o.getNotes() + "”");
                BigDecimal c = toCollect(o);
                t.addCell(PdfKit.cell(String.valueOf(n), PdfKit.plain(9), Element.ALIGN_LEFT, null));
                t.addCell(PdfKit.cell(o.getCode(), PdfKit.bold(9), Element.ALIGN_LEFT, null));
                t.addCell(PdfKit.cell(o.getCustomerName() + "\n" + o.getCustomerPhone(), PdfKit.plain(9), Element.ALIGN_LEFT, null));
                t.addCell(PdfKit.cell(addr, PdfKit.plain(9), Element.ALIGN_LEFT, null));
                t.addCell(PdfKit.cell(goods.toString(), PdfKit.plain(9), Element.ALIGN_LEFT, null));
                t.addCell(PdfKit.cell(c.signum() > 0 ? PdfKit.money(c) : I18n.t("Paid"), c.signum() > 0 ? PdfKit.bold(9) : PdfKit.muted(9), Element.ALIGN_RIGHT, null));
                t.addCell(PdfKit.cell("☐", PdfKit.plain(11), Element.ALIGN_CENTER, null));
            }
            doc.add(t);
            Paragraph p = new Paragraph(I18n.t("Stops: {0} · cash to collect: {1}", list.size(), PdfKit.amount(cur, cash)), PdfKit.bold(12));
            p.setSpacingBefore(10);
            doc.add(p);
            PdfKit.signatures(doc, I18n.t("Driver"), I18n.t("Handed over by"));
        });
    }
}
