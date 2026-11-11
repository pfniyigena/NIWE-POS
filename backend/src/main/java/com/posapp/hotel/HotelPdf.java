package com.posapp.hotel;

import com.posapp.i18n.I18n;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.posapp.hotel.dto.HotelDtos.BillDto;
import com.posapp.hotel.dto.HotelDtos.FolioChargeDto;
import com.posapp.hotel.dto.HotelDtos.FolioDto;
import com.posapp.hotel.dto.HotelDtos.FolioPaymentDto;
import com.posapp.settings.StoreSettings;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/** The hotel's printed documents on A4: the guest bill (or one bill of a split folio, or a company invoice) and the deposit / payment receipt. */
@Component
public class HotelPdf {

    private static String money(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", I18n.symbols());
        return f.format(v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP));
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "" : s.trim();
    }

    private static PdfPCell cell(String text, Font font, int align, boolean border) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        c.setBorder(border ? Rectangle.BOTTOM : Rectangle.NO_BORDER);
        return c;
    }

    private static void header(Document doc, StoreSettings store) throws Exception {
        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14), small = FontFactory.getFont(FontFactory.HELVETICA, 9);
        if (store == null) return;
        if (!nz(store.getBusinessName()).isEmpty()) doc.add(new Paragraph(store.getBusinessName(), h1));
        StringBuilder line = new StringBuilder();
        for (String part : new String[]{nz(store.getAddress()), nz(store.getPhone()), nz(store.getEmail())}) {
            if (!part.isEmpty()) {
                if (line.length() > 0) line.append(" - ");
                line.append(part);
            }
        }
        if (line.length() > 0) doc.add(new Paragraph(line.toString(), small));
        if (!nz(store.getTaxId()).isEmpty()) doc.add(new Paragraph(I18n.t("Tax ID: ") + store.getTaxId(), small));
        Paragraph gap = new Paragraph(" ", small);
        gap.setSpacingAfter(6);
        doc.add(gap);
    }

    private static String currency(StoreSettings store) {
        return store != null && !nz(store.getCurrencyCode()).isEmpty() ? " " + store.getCurrencyCode() : "";
    }

    /**
     * The bill of a stay. {@code billNo} 0 prints everything; otherwise only that bill's lines and money. A company invoice names the
     * company and its number; an unsettled bill shows the balance.
     */
    public byte[] bill(FolioDto folio, int billNo, String companyName, String invoiceNumber, StoreSettings store) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), normal = FontFactory.getFont(FontFactory.HELVETICA, 10),
                    small = FontFactory.getFont(FontFactory.HELVETICA, 9), title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            header(doc, store);
            var b = folio.booking();
            Paragraph t = new Paragraph(invoiceNumber != null ? "INVOICE" : I18n.t("GUEST BILL"), title);
            t.setSpacingAfter(2);
            doc.add(t);
            String number = invoiceNumber != null ? invoiceNumber : b.bookingNumber() + (billNo > 0 ? " / bill " + billNo : "");
            Paragraph num = new Paragraph(number, normal);
            num.setSpacingAfter(6);
            doc.add(num);

            String billTo = !nz(companyName).isEmpty() ? companyName : b.guestName();
            PdfPTable info = new PdfPTable(new float[]{1.1f, 3f, 1.1f, 3f});
            info.setWidthPercentage(100);
            info.setSpacingAfter(10);
            info.addCell(cell(I18n.t("Bill to"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(billTo, normal, Element.ALIGN_LEFT, false));
            info.addCell(cell(I18n.t("Booking"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(b.bookingNumber(), normal, Element.ALIGN_LEFT, false));
            info.addCell(cell(I18n.t("Guest"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(b.guestName(), normal, Element.ALIGN_LEFT, false));
            info.addCell(cell(I18n.t("Room"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell((b.roomNumber() == null ? "-" : b.roomNumber()) + " " + nz(b.roomTypeName()), normal, Element.ALIGN_LEFT, false));
            info.addCell(cell(I18n.t("Arrival"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(String.valueOf(b.checkInDate()), normal, Element.ALIGN_LEFT, false));
            info.addCell(cell(I18n.t("Departure"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(String.valueOf(b.checkOutDate()), normal, Element.ALIGN_LEFT, false));
            doc.add(info);

            PdfPTable table = new PdfPTable(new float[]{3.6f, 1.3f, 1.1f, 1.4f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : new String[]{I18n.t("Description"), I18n.t("Net"), I18n.t("Tax"), I18n.t("Amount")}) {
                PdfPCell c = cell(h, bold, h.equals("Description") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT, true);
                c.setBackgroundColor(new java.awt.Color(235, 235, 235));
                table.addCell(c);
            }
            BigDecimal net = BigDecimal.ZERO, tax = BigDecimal.ZERO, total = BigDecimal.ZERO;
            for (FolioChargeDto c : folio.charges()) {
                if (c.voided() || (billNo > 0 && c.billNo() != billNo)) continue;
                table.addCell(cell(c.description() == null ? "" : c.description(), normal, Element.ALIGN_LEFT, true));
                table.addCell(cell(money(c.net()), normal, Element.ALIGN_RIGHT, true));
                table.addCell(cell(money(c.tax()), normal, Element.ALIGN_RIGHT, true));
                table.addCell(cell(money(c.amount()), normal, Element.ALIGN_RIGHT, true));
                net = net.add(c.net());
                tax = tax.add(c.tax());
                total = total.add(c.amount());
            }
            doc.add(table);
            String suffix = currency(store);
            right(doc, I18n.t("Subtotal: ") + money(net) + suffix, bold, 10);
            right(doc, I18n.t("Tax: ") + money(tax) + suffix, bold, 0);
            right(doc, I18n.t("Total: ") + money(total) + suffix, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13), 0);

            BigDecimal received = BigDecimal.ZERO;
            List<FolioPaymentDto> pays = folio.payments().stream().filter(p -> billNo == 0 || p.billNo() == billNo).toList();
            if (!pays.isEmpty() && invoiceNumber == null) {
                Paragraph ph = new Paragraph(I18n.t("Payments"), bold);
                ph.setSpacingBefore(10);
                doc.add(ph);
                for (FolioPaymentDto p : pays) {
                    boolean refund = "REFUND".equals(p.kind());
                    received = refund ? received.subtract(p.amount()) : received.add(p.amount());
                    doc.add(new Paragraph(p.createdAt().toString().substring(0, 10) + "  " + pretty(p.kind()) + " (" + p.method() + ")  " + (refund ? "-" : "") + money(p.amount()) + suffix, small));
                }
            }
            if (invoiceNumber != null) {
                BillDto bill = folio.bills().stream().filter(x -> x.billNo() == billNo).findFirst().orElse(null);
                Paragraph due = new Paragraph(I18n.t("Payable by ") + nz(companyName) + I18n.t(" on account.") + (bill != null && bill.companyBillStatus() != null ? "" : ""), small);
                due.setSpacingBefore(10);
                doc.add(due);
            } else {
                BigDecimal balance = total.subtract(received);
                right(doc, (balance.signum() > 0 ? I18n.t("Balance due: ") : I18n.t("Balance: ")) + money(balance) + suffix, bold, 8);
            }
            Paragraph sign = new Paragraph(I18n.t("\n\nGuest signature: ______________________"), small);
            sign.setSpacingBefore(14);
            doc.add(sign);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(I18n.t("Failed to build the bill PDF"), e);
        }
    }

    /** Proof that money was taken: a deposit before arrival, a payment during the stay, or a refund. */
    public byte[] receipt(FolioPaymentDto payment, String bookingNumber, String guestName, StoreSettings store) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A5, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), normal = FontFactory.getFont(FontFactory.HELVETICA, 10),
                    title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            header(doc, store);
            String kind = "DEPOSIT".equals(payment.kind()) ? I18n.t("DEPOSIT RECEIPT") : "REFUND".equals(payment.kind()) ? I18n.t("REFUND RECEIPT") : I18n.t("PAYMENT RECEIPT");
            Paragraph t = new Paragraph(kind, title);
            t.setSpacingAfter(8);
            doc.add(t);
            PdfPTable info = new PdfPTable(new float[]{1.4f, 3f});
            info.setWidthPercentage(100);
            String[][] rows = {
                    {I18n.t("Receipt no."), String.format(I18n.t("R-%06d"), payment.id())},
                    {I18n.t("Date"), payment.createdAt().toString().substring(0, 10)},
                    {I18n.t("Booking"), bookingNumber},
                    {I18n.t("Received from"), guestName},
                    {I18n.t("Method"), payment.method()},
                    {I18n.t("Reference"), nz(payment.reference())},
            };
            for (String[] r : rows) {
                info.addCell(cell(r[0], bold, Element.ALIGN_LEFT, false));
                info.addCell(cell(r[1], normal, Element.ALIGN_LEFT, false));
            }
            doc.add(info);
            Paragraph amount = new Paragraph(I18n.t("Amount: ") + money(payment.amount()) + currency(store), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15));
            amount.setSpacingBefore(12);
            doc.add(amount);
            if (!nz(payment.note()).isEmpty()) doc.add(new Paragraph(payment.note(), normal));
            doc.add(new Paragraph(I18n.t("\n\nReceived by: ______________________"), normal));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(I18n.t("Failed to build the receipt PDF"), e);
        }
    }

    private static void right(Document doc, String text, Font font, float before) throws Exception {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        if (before > 0) p.setSpacingBefore(before);
        doc.add(p);
    }

    private static String pretty(String code) {
        String t = code.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** The group's master statement: what was billed to it, what it has paid, and what it still owes. */
    public byte[] groupStatement(com.posapp.hotel.dto.GroupDtos.GroupDetailDto g, StoreSettings store) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), normal = FontFactory.getFont(FontFactory.HELVETICA, 10),
                    title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            header(doc, store);
            Paragraph t = new Paragraph(I18n.t("GROUP STATEMENT"), title);
            t.setSpacingAfter(2);
            doc.add(t);
            Paragraph num = new Paragraph(g.groupCode() + " - " + g.name(), normal);
            num.setSpacingAfter(6);
            doc.add(num);
            PdfPTable info = new PdfPTable(new float[]{1.1f, 3f, 1.1f, 3f});
            info.setWidthPercentage(100);
            info.setSpacingAfter(10);
            info.addCell(cell(I18n.t("Contact"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(nz(g.contactName()), normal, Element.ALIGN_LEFT, false));
            info.addCell(cell(I18n.t("Invoice to"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(nz(g.companyName()), normal, Element.ALIGN_LEFT, false));
            info.addCell(cell(I18n.t("Arrival"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(String.valueOf(g.arrivalDate()), normal, Element.ALIGN_LEFT, false));
            info.addCell(cell(I18n.t("Departure"), bold, Element.ALIGN_LEFT, false));
            info.addCell(cell(String.valueOf(g.departureDate()), normal, Element.ALIGN_LEFT, false));
            doc.add(info);

            PdfPTable table = new PdfPTable(new float[]{1.2f, 1.3f, 3.6f, 1.4f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : new String[]{I18n.t("Date"), I18n.t("Type"), I18n.t("Description"), I18n.t("Amount")}) {
                PdfPCell c = cell(h, bold, h.equals("Amount") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, true);
                c.setBackgroundColor(new java.awt.Color(235, 235, 235));
                table.addCell(c);
            }
            for (var e : g.entries()) {
                boolean minus = !"BILL".equals(e.kind()) && !"DEPOSIT".equals(e.kind());
                table.addCell(cell(String.valueOf(e.entryDate()), normal, Element.ALIGN_LEFT, true));
                table.addCell(cell(pretty(e.kind()), normal, Element.ALIGN_LEFT, true));
                table.addCell(cell(nz(e.description()) + (nz(e.reference()).isEmpty() ? "" : " (" + e.reference() + ")"), normal, Element.ALIGN_LEFT, true));
                table.addCell(cell((minus ? "-" : "") + money(e.amount()), normal, Element.ALIGN_RIGHT, true));
            }
            doc.add(table);
            String suffix = currency(store);
            var t2 = g.totals();
            right(doc, I18n.t("Billed: ") + money(t2.billed()) + suffix, bold, 10);
            right(doc, I18n.t("Deposits received: ") + money(t2.deposits()) + suffix, bold, 0);
            right(doc, I18n.t("Paid: ") + money(t2.paid()) + suffix, bold, 0);
            right(doc, I18n.t("Amount owed: ") + money(t2.owed()) + suffix, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13), 0);
            if (t2.held().signum() > 0) right(doc, I18n.t("Deposit still held: ") + money(t2.held()) + suffix, normal, 0);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(I18n.t("Could not build the statement"), e);
        }
    }

    // ---------------------------------------------------------------- HO-6: registration card and confirmation letter

    private static String blank(String s) {
        return nz(s).isEmpty() ? "" : nz(s);
    }

    private static PdfPCell line(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.BOTTOM);
        c.setPadding(5);
        c.setMinimumHeight(20);
        return c;
    }

    private static void pair(PdfPTable t, String label, String value, Font bold, Font normal) {
        PdfPCell l = new PdfPCell(new Phrase(label, bold));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPadding(4);
        t.addCell(l);
        t.addCell(line(value, normal));
    }

    /** The card a guest signs at the desk: stay details from the booking, personal details from the guest record (blank lines left to fill in by hand). */
    public byte[] registrationCard(com.posapp.hotel.dto.HotelDtos.BookingDto b, com.posapp.hotel.dto.HotelDtos.GuestDto g, com.posapp.hotel.dto.HotelDtos.HotelSettingsDto hs,
                                   StoreSettings store) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), normal = FontFactory.getFont(FontFactory.HELVETICA, 10),
                    small = FontFactory.getFont(FontFactory.HELVETICA, 8), title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20),
                    h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            header(doc, store);
            Paragraph t = new Paragraph(I18n.t("REGISTRATION CARD"), title);
            t.setSpacingAfter(2);
            doc.add(t);
            Paragraph num = new Paragraph(b.bookingNumber() + (g.vip() ? I18n.t("  -  VIP") : ""), normal);
            num.setSpacingAfter(8);
            doc.add(num);

            Paragraph stay = new Paragraph(I18n.t("Stay"), h2);
            stay.setSpacingAfter(2);
            doc.add(stay);
            PdfPTable st = new PdfPTable(new float[]{1.3f, 2.2f, 1.3f, 2.2f});
            st.setWidthPercentage(100);
            st.setSpacingAfter(10);
            pair(st, I18n.t("Arrival"), b.checkInDate() + (nz(hs.checkInTime()).isEmpty() ? "" : "  (from " + hs.checkInTime() + ")"), bold, normal);
            pair(st, I18n.t("Departure"), b.checkOutDate() + (nz(hs.checkOutTime()).isEmpty() ? "" : "  (by " + hs.checkOutTime() + ")"), bold, normal);
            pair(st, I18n.t("Nights"), String.valueOf(b.nights()), bold, normal);
            pair(st, I18n.t("Room"), (b.roomNumber() == null ? "" : b.roomNumber() + "  ") + nz(b.roomTypeName()), bold, normal);
            pair(st, I18n.t("Adults"), String.valueOf(b.adults()), bold, normal);
            pair(st, I18n.t("Children"), String.valueOf(b.children()), bold, normal);
            pair(st, I18n.t("Rate / night"), money(b.nightlyRate()), bold, normal);
            pair(st, I18n.t("Booked through"), blank(b.source()), bold, normal);
            doc.add(st);

            Paragraph gp = new Paragraph(I18n.t("Guest"), h2);
            gp.setSpacingAfter(2);
            doc.add(gp);
            PdfPTable gt = new PdfPTable(new float[]{1.3f, 2.2f, 1.3f, 2.2f});
            gt.setWidthPercentage(100);
            gt.setSpacingAfter(10);
            pair(gt, I18n.t("Full name"), g.fullName(), bold, normal);
            pair(gt, I18n.t("Nationality"), blank(g.nationality()), bold, normal);
            pair(gt, I18n.t("Date of birth"), g.dateOfBirth() == null ? "" : g.dateOfBirth().toString(), bold, normal);
            pair(gt, I18n.t("Profession"), blank(g.profession()), bold, normal);
            pair(gt, I18n.t("ID type"), blank(g.idType()), bold, normal);
            pair(gt, I18n.t("ID number"), blank(g.idNumber()), bold, normal);
            pair(gt, I18n.t("Phone"), blank(g.phone()), bold, normal);
            pair(gt, I18n.t("E-mail"), blank(g.email()), bold, normal);
            doc.add(gt);
            PdfPTable addr = new PdfPTable(new float[]{1.3f, 6.2f});
            addr.setWidthPercentage(100);
            addr.setSpacingAfter(10);
            pair(addr, I18n.t("Home address"), blank(g.address()), bold, normal);
            pair(addr, I18n.t("Coming from"), "", bold, normal);
            pair(addr, I18n.t("Going to"), "", bold, normal);
            pair(addr, I18n.t("Purpose of visit"), "", bold, normal);
            pair(addr, I18n.t("Vehicle plate"), "", bold, normal);
            pair(addr, I18n.t("Payment by"), I18n.t("[ ] Cash   [ ] Card   [ ] Mobile money   [ ] Company: ______________"), bold, normal);
            doc.add(addr);
            if (!blank(g.preferences()).isEmpty()) {
                Paragraph pf = new Paragraph(I18n.t("Preferences on file: ") + g.preferences(), small);
                pf.setSpacingAfter(8);
                doc.add(pf);
            }
            Paragraph terms = new Paragraph(I18n.t("I confirm the details above are correct and agree to settle my account on departure. The hotel is not responsible for valuables left in rooms. ")
                    + (nz(hs.checkOutTime()).isEmpty() ? "" : I18n.t("Check-out time is ") + hs.checkOutTime() + "."), small);
            terms.setSpacingAfter(28);
            doc.add(terms);
            PdfPTable sig = new PdfPTable(new float[]{3f, 0.4f, 3f, 0.4f, 1.6f});
            sig.setWidthPercentage(100);
            for (String[] col : new String[][]{{I18n.t("Guest signature")}, {""}, {I18n.t("Receptionist")}, {""}, {I18n.t("Date")}}) {
                if (col[0].isEmpty()) {
                    PdfPCell gap = new PdfPCell(new Phrase(" ", small));
                    gap.setBorder(Rectangle.NO_BORDER);
                    sig.addCell(gap);
                } else {
                    PdfPCell c = new PdfPCell(new Phrase(col[0], small));
                    c.setBorder(Rectangle.TOP);
                    c.setPaddingTop(3);
                    sig.addCell(c);
                }
            }
            doc.add(sig);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(I18n.t("Could not build the registration card"), e);
        }
    }

    /** A letter confirming a reservation: dates, room, the price quoted for the whole stay, the deposit received and the cancellation terms. */
    public byte[] confirmationLetter(FolioDto folio, com.posapp.hotel.dto.HotelDtos.GuestDto g, com.posapp.hotel.dto.HotelDtos.HotelSettingsDto hs, StoreSettings store) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), normal = FontFactory.getFont(FontFactory.HELVETICA, 10.5f),
                    title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20), small = FontFactory.getFont(FontFactory.HELVETICA, 9);
            var b = folio.booking();
            header(doc, store);
            Paragraph t = new Paragraph(I18n.t("BOOKING CONFIRMATION"), title);
            t.setSpacingAfter(2);
            doc.add(t);
            Paragraph num = new Paragraph(I18n.t("Reference ") + b.bookingNumber(), normal);
            num.setSpacingAfter(12);
            doc.add(num);
            Paragraph hello = new Paragraph(I18n.t("Dear ") + g.fullName() + ",", normal);
            hello.setSpacingAfter(6);
            doc.add(hello);
            Paragraph intro = new Paragraph(I18n.t("Thank you for choosing us. We are pleased to confirm your reservation as follows."), normal);
            intro.setSpacingAfter(10);
            doc.add(intro);
            PdfPTable info = new PdfPTable(new float[]{1.6f, 4f});
            info.setWidthPercentage(100);
            info.setSpacingAfter(10);
            pair(info, I18n.t("Arrival"), b.checkInDate() + (nz(hs.checkInTime()).isEmpty() ? "" : I18n.t("  (check-in from ") + hs.checkInTime() + ")"), bold, normal);
            pair(info, I18n.t("Departure"), b.checkOutDate() + (nz(hs.checkOutTime()).isEmpty() ? "" : I18n.t("  (check-out by ") + hs.checkOutTime() + ")"), bold, normal);
            pair(info, I18n.t("Nights"), String.valueOf(b.nights()), bold, normal);
            pair(info, I18n.t("Room"), nz(b.roomTypeName()) + (b.roomNumber() == null ? "" : I18n.t("  -  room ") + b.roomNumber()), bold, normal);
            pair(info, I18n.t("Guests"), b.adults() + " adult" + (b.adults() == 1 ? "" : "s") + (b.children() > 0 ? ", " + b.children() + " child" + (b.children() == 1 ? "" : "ren") : ""), bold, normal);
            if (b.ratePlanName() != null) pair(info, I18n.t("Rate plan"), b.ratePlanName(), bold, normal);
            String suffix = currency(store);
            pair(info, I18n.t("Price for the stay"), money(b.chargesTotal()) + suffix + I18n.t("  (tax included)"), bold, normal);
            if (folio.deposits().signum() > 0) {
                pair(info, I18n.t("Deposit received"), money(folio.deposits()) + suffix, bold, normal);
                pair(info, I18n.t("Balance on arrival"), money(folio.balance()) + suffix, bold, normal);
            }
            if (b.groupName() != null) pair(info, I18n.t("Group"), b.groupName(), bold, normal);
            if (!blank(b.guestPreferences()).isEmpty()) pair(info, I18n.t("Your preferences"), b.guestPreferences(), bold, normal);
            doc.add(info);

            StringBuilder terms = new StringBuilder();
            if (hs.cancelFreeDays() > 0) {
                terms.append(I18n.t("Free cancellation until ")).append(b.checkInDate().minusDays(hs.cancelFreeDays())).append(" (").append(hs.cancelFreeDays()).append(" day")
                        .append(hs.cancelFreeDays() == 1 ? "" : "s").append(I18n.t(" before arrival). "));
            }
            if (hs.cancelFeeNights() > 0) terms.append(I18n.t("Later cancellations are charged ")).append(hs.cancelFeeNights()).append(" night").append(hs.cancelFeeNights() == 1 ? "" : "s").append(". ");
            if (hs.noShowFeeNights() > 0) terms.append(I18n.t("If you do not arrive, ")).append(hs.noShowFeeNights()).append(" night").append(hs.noShowFeeNights() == 1 ? " is" : I18n.t("s are")).append(" charged. ");
            if (terms.length() > 0) {
                Paragraph tp = new Paragraph(I18n.t("Cancellation terms: ") + terms.toString().trim(), small);
                tp.setSpacingAfter(10);
                doc.add(tp);
            }
            Paragraph close = new Paragraph(I18n.t("Please bring a valid passport or national ID for registration. We look forward to welcoming you."), normal);
            close.setSpacingAfter(14);
            doc.add(close);
            if (store != null && !nz(store.getBusinessName()).isEmpty()) doc.add(new Paragraph(store.getBusinessName(), bold));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(I18n.t("Could not build the confirmation letter"), e);
        }
    }
}
