package com.posapp.hr;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import com.posapp.common.ApiException;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * The employee's Service Card: a staff card the size of a bank card (85.6 x 54 mm), front and back side by side on an A4 page
 * with cut marks, ready to print on card stock and laminate. The front carries the business name and logo, the employee's name,
 * number, job title, department, hire date and a barcode of the employee number; the back says who the card belongs to and what
 * to do if it is found, with a signature line.
 */
@Component
public class HrPrintService {

    private static final float MM = 72f / 25.4f;
    private static final float CARD_W = 85.6f * MM;
    private static final float CARD_H = 54f * MM;
    private static final Color BAND = new Color(0x1F, 0x3A, 0x5F);
    private static final Color GREY = new Color(0x99, 0x99, 0x99);

    private final EmployeeRepository employeeRepository;
    private final StoreSettingsRepository storeSettingsRepository;

    public HrPrintService(EmployeeRepository employeeRepository, StoreSettingsRepository storeSettingsRepository) {
        this.employeeRepository = employeeRepository;
        this.storeSettingsRepository = storeSettingsRepository;
    }

    public record Printed(String fileName, byte[] content) {
    }

    private static Font font(boolean bold, float size, Color color) {
        Font f = FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size);
        f.setColor(color);
        return f;
    }

    @Transactional(readOnly = true)
    public Printed serviceCard(Long employeeId) {
        Employee e = employeeRepository.findById(employeeId).orElseThrow(() -> ApiException.notFound("Employee not found"));
        if (e.getStatus() == EmployeeStatus.TERMINATED) {
            throw ApiException.badRequest("A service card is not printed for an employee who has left the company.");
        }
        StoreSettings store = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        String business = store != null && store.getBusinessName() != null && !store.getBusinessName().isBlank() ? store.getBusinessName() : "Service Card";
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();
            PdfContentByte cb = writer.getDirectContent();
            float pageW = PageSize.A4.getWidth();
            float top = PageSize.A4.getHeight() - 90;
            float gap = 16;
            float x = (pageW - (2 * CARD_W + gap)) / 2;
            float y = top - CARD_H;

            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase("Service Card - " + e.fullName(), font(true, 12, Color.BLACK)), pageW / 2, top + 30, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase("Print on card stock at 100% size, cut along the lines (front left, back right).", font(false, 8, GREY)), pageW / 2, top + 16, 0);

            front(cb, e, store, business, x, y);
            back(cb, e, store, business, x + CARD_W + gap, y);
            doc.close();
            String safe = e.getEmployeeNumber().replaceAll("[^A-Za-z0-9._-]", "_");
            return new Printed("service-card-" + safe + ".pdf", out.toByteArray());
        } catch (Exception ex) {
            throw ApiException.badRequest("Could not build the service card: " + ex.getMessage());
        }
    }

    private void frame(PdfContentByte cb, float x, float y) {
        cb.saveState();
        cb.setColorStroke(GREY);
        cb.setLineWidth(0.5f);
        cb.roundRectangle(x, y, CARD_W, CARD_H, 6);
        cb.stroke();
        cb.restoreState();
    }

    private void front(PdfContentByte cb, Employee e, StoreSettings store, String business, float x, float y) throws Exception {
        frame(cb, x, y);
        float bandH = 20 * MM / 2f * 1.2f;
        cb.saveState();
        cb.setColorFill(BAND);
        cb.rectangle(x + 0.5f, y + CARD_H - bandH - 0.5f, CARD_W - 1f, bandH);
        cb.fill();
        cb.restoreState();
        float textX = x + 8;
        Image logo = logo(store);
        if (logo != null) {
            logo.scaleToFit(bandH - 6, bandH - 6);
            logo.setAbsolutePosition(x + 6, y + CARD_H - bandH + (bandH - logo.getScaledHeight()) / 2 - 0.5f);
            cb.addImage(logo);
            textX = x + 6 + logo.getScaledWidth() + 6;
        }
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(business, font(true, 10, Color.WHITE)), textX, y + CARD_H - bandH / 2 - 3, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("SERVICE CARD", font(true, 7, new Color(0xCC, 0xD6, 0xE6))), x + CARD_W - 8, y + CARD_H - bandH / 2 - 2, 0);

        float cy = y + CARD_H - bandH - 18;
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(e.fullName(), font(true, 13, Color.BLACK)), x + 10, cy, 0);
        cy -= 13;
        if (e.getJobTitle() != null && !e.getJobTitle().isBlank()) {
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(e.getJobTitle(), font(false, 9, new Color(0x33, 0x33, 0x33))), x + 10, cy, 0);
            cy -= 11;
        }
        if (e.getDepartment() != null) {
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(e.getDepartment().getName(), font(false, 8, GREY)), x + 10, cy, 0);
            cy -= 11;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase("No. " + e.getEmployeeNumber(), font(true, 8, Color.BLACK)), x + 10, y + 36, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase("Joined " + fmt.format(e.getHireDate()), font(false, 8, Color.BLACK)), x + 10, y + 25, 0);
        Barcode128 bc = new Barcode128();
        bc.setCode(e.getEmployeeNumber());
        bc.setBarHeight(16);
        bc.setSize(6);
        bc.setCodeType(Barcode128.CODE128);
        Image img = bc.createImageWithBarcode(cb, Color.BLACK, Color.BLACK);
        float maxW = CARD_W * 0.45f;
        if (img.getWidth() > maxW) {
            img.scaleToFit(maxW, 30);
        }
        img.setAbsolutePosition(x + CARD_W - img.getScaledWidth() - 8, y + 8);
        cb.addImage(img);
    }

    private void back(PdfContentByte cb, Employee e, StoreSettings store, String business, float x, float y) {
        frame(cb, x, y);
        float cy = y + CARD_H - 16;
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(business, font(true, 10, BAND)), x + CARD_W / 2, cy, 0);
        cy -= 14;
        String[] lines = {
                "This card identifies " + e.fullName() + " as a member of staff.",
                "It remains the property of " + business + " and must be",
                "returned on request or when employment ends.",
                "If found, please return it to:"
        };
        for (String l : lines) {
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(l, font(false, 7, new Color(0x33, 0x33, 0x33))), x + CARD_W / 2, cy, 0);
            cy -= 9;
        }
        if (store != null) {
            StringBuilder where = new StringBuilder();
            if (store.getAddress() != null && !store.getAddress().isBlank()) where.append(store.getAddress().replaceAll("\\s*\\R\\s*", ", "));
            if (store.getPhone() != null && !store.getPhone().isBlank()) where.append(where.length() > 0 ? " - " : "").append(store.getPhone());
            if (where.length() > 0) {
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(where.toString(), font(true, 7, Color.BLACK)), x + CARD_W / 2, cy, 0);
            }
        }
        cb.saveState();
        cb.setColorStroke(GREY);
        cb.setLineWidth(0.5f);
        cb.moveTo(x + 14, y + 20);
        cb.lineTo(x + CARD_W / 2 - 6, y + 20);
        cb.stroke();
        cb.moveTo(x + CARD_W / 2 + 6, y + 20);
        cb.lineTo(x + CARD_W - 14, y + 20);
        cb.stroke();
        cb.restoreState();
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase("Holder's signature", font(false, 6, GREY)), x + CARD_W / 4 + 4, y + 11, 0);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase("Authorised by", font(false, 6, GREY)), x + 3 * CARD_W / 4 - 4, y + 11, 0);
    }

    private static Image logo(StoreSettings store) {
        try {
            if (store == null || store.getLogoDataUrl() == null) return null;
            String data = store.getLogoDataUrl();
            int comma = data.indexOf(',');
            if (!data.startsWith("data:image") || comma < 0) return null;
            return Image.getInstance(Base64.getDecoder().decode(data.substring(comma + 1)));
        } catch (Exception ex) {
            return null;
        }
    }
}
