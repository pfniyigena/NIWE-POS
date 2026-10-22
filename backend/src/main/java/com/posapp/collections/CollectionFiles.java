package com.posapp.collections;

import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.posapp.collections.CollectionDtos.Aging;
import com.posapp.collections.CollectionDtos.PaymentLine;
import com.posapp.collections.CollectionDtos.StatementData;
import com.posapp.collections.CollectionDtos.StatementLine;
import com.posapp.common.PdfKit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.posapp.common.PdfKit.SOFT;
import static com.posapp.common.PdfKit.bold;
import static com.posapp.common.PdfKit.cell;
import static com.posapp.common.PdfKit.headerCells;
import static com.posapp.common.PdfKit.muted;
import static com.posapp.common.PdfKit.nz;
import static com.posapp.common.PdfKit.plain;
import static com.posapp.common.PdfKit.section;
import static com.posapp.common.PdfKit.shortDay;

/** The customer statement as an A4 PDF. */
@Component
public class CollectionFiles {

    private final PdfKit kit;

    public CollectionFiles(PdfKit kit) {
        this.kit = kit;
    }

    public String businessName() {
        return kit.businessName();
    }

    public String currency() {
        return kit.currency();
    }

    public String contactLine() {
        return kit.contactLine();
    }

    static String money(BigDecimal v) {
        return PdfKit.money(v);
    }

    static String amount(String cur, BigDecimal v) {
        return PdfKit.amount(cur, v);
    }

    static String day(LocalDate d) {
        return PdfKit.day(d);
    }

    public byte[] statement(StatementData d) {
        String cur = kit.currency();
        return kit.build(false, "Statement of account " + shortDay(d.asOf()), doc -> {
            kit.head(doc, "Statement of account", "Up to " + PdfKit.day(d.asOf()));
            doc.add(PdfKit.facts(new String[][]{{"Customer", d.customerName()}, {"Phone", d.phone()}, {"E-mail", d.email()}}));
            Paragraph owed = new Paragraph("Still to pay on " + PdfKit.day(d.asOf()) + ": " + amount(cur, d.totalOutstanding()), bold(11));
            owed.setSpacingBefore(6);
            owed.setSpacingAfter(2);
            doc.add(owed);
            if (d.totalOverdue().signum() > 0) doc.add(new Paragraph("Of which overdue: " + amount(cur, d.totalOverdue()), bold(10)));
            section(doc, "What is unpaid");
            if (d.lines().isEmpty()) {
                doc.add(new Paragraph("Nothing is owed on this account.", muted(9.5f)));
            } else {
                PdfPTable t = new PdfPTable(new float[]{1.7f, 2.2f, 2f, 1.9f, 1.9f, 1.9f, 1.7f, 1.4f});
                t.setWidthPercentage(100);
                headerCells(t, new String[]{"Date", "Number", "What", "Amount", "Paid", "To pay", "Due", "Late (days)"},
                        new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                BigDecimal total = BigDecimal.ZERO, paid = BigDecimal.ZERO, due = BigDecimal.ZERO;
                for (StatementLine l : d.lines()) {
                    t.addCell(cell(shortDay(l.date()), plain(8), Element.ALIGN_LEFT, null));
                    t.addCell(cell(nz(l.number()), plain(8), Element.ALIGN_LEFT, null));
                    t.addCell(cell(nz(l.kind()), plain(8), Element.ALIGN_LEFT, null));
                    t.addCell(cell(PdfKit.money(l.total()), plain(8), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(PdfKit.money(l.paid()), plain(8), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(PdfKit.money(l.outstanding()), bold(8), Element.ALIGN_RIGHT, null));
                    t.addCell(cell(shortDay(l.dueDate()), plain(8), Element.ALIGN_LEFT, null));
                    t.addCell(cell(l.daysOverdue() > 0 ? String.valueOf(l.daysOverdue()) : "", plain(8), Element.ALIGN_RIGHT, null));
                    total = total.add(l.total());
                    paid = paid.add(l.paid());
                    due = due.add(l.outstanding());
                }
                t.addCell(cell("Total", bold(8), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(8), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(8), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell(PdfKit.money(total), bold(8), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell(PdfKit.money(paid), bold(8), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell(PdfKit.money(due), bold(8), Element.ALIGN_RIGHT, SOFT));
                t.addCell(cell("", plain(8), Element.ALIGN_LEFT, SOFT));
                t.addCell(cell("", plain(8), Element.ALIGN_LEFT, SOFT));
                doc.add(t);
                Aging g = d.aging();
                section(doc, "How late");
                PdfPTable a = new PdfPTable(5);
                a.setWidthPercentage(100);
                headerCells(a, new String[]{"Not yet due", "1 to 30 days", "31 to 60 days", "61 to 90 days", "Over 90 days"},
                        new int[]{Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
                for (BigDecimal v : new BigDecimal[]{g.current(), g.days1to30(), g.days31to60(), g.days61to90(), g.over90()}) a.addCell(cell(PdfKit.money(v), plain(9), Element.ALIGN_RIGHT, null));
                doc.add(a);
            }
            if (!d.payments().isEmpty()) {
                section(doc, "Payments received in the last 90 days");
                PdfPTable p = new PdfPTable(new float[]{2f, 2.5f, 2f, 2f});
                p.setWidthPercentage(100);
                headerCells(p, new String[]{"Date", "Receipt", "Method", "Amount"}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                for (PaymentLine l : d.payments()) {
                    p.addCell(cell(shortDay(l.date()), plain(8), Element.ALIGN_LEFT, null));
                    p.addCell(cell(nz(l.receipt()), plain(8), Element.ALIGN_LEFT, null));
                    p.addCell(cell(nz(l.method()), plain(8), Element.ALIGN_LEFT, null));
                    p.addCell(cell(PdfKit.money(l.amount()), plain(8), Element.ALIGN_RIGHT, null));
                }
                doc.add(p);
            }
            if (d.totalOverdue().signum() > 0) {
                Paragraph note = new Paragraph("Please pay the amount that is overdue as soon as you can" + (kit.contactLine().isEmpty() ? "." : ", or contact us on " + kit.contactLine() + " if there is a problem."), muted(9));
                note.setSpacingBefore(8);
                doc.add(note);
            }
        });
    }
}
