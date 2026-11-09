package com.posapp.modules;

import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.posapp.common.PdfKit;
import com.posapp.license.LicenseDtos.LicenseStatus;
import com.posapp.modules.ModuleService.ModuleDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.posapp.common.PdfKit.bold;
import static com.posapp.common.PdfKit.cell;
import static com.posapp.common.PdfKit.headerCells;
import static com.posapp.common.PdfKit.muted;
import static com.posapp.common.PdfKit.plain;
import static com.posapp.common.PdfKit.section;

/** Settings > Modules, "Print PDF": every module of the product with its state in this installation, and the license it runs under. */
@Component
public class ModuleListPdf {

    private final PdfKit kit;

    public ModuleListPdf(PdfKit kit) {
        this.kit = kit;
    }

    static String label(ModuleDto m) {
        return switch (m.state()) {
            case "CORE" -> "Core (always on)";
            case "ACTIVE" -> "Active";
            case "AVAILABLE" -> "In your license, not activated";
            case "REQUESTED" -> "Requested";
            case "EXPIRED" -> "License expired";
            default -> "Locked (not in license)";
        };
    }

    public byte[] build(List<ModuleDto> modules, LicenseStatus lic) {
        Map<String, String> names = modules.stream().collect(Collectors.toMap(ModuleDto::key, ModuleDto::name, (a, b) -> a));
        Function<List<String>, String> join = keys -> keys.stream().map(k -> names.getOrDefault(k, k)).collect(Collectors.joining(", "));
        return kit.build(false, "Modules", doc -> {
            kit.head(doc, "Modules", "Printed " + PdfKit.day(LocalDate.now()));
            String expiry = lic == null || !lic.valid() ? null : lic.expiresAt() == null ? "No expiry date" : PdfKit.day(lic.expiresAt()) + (lic.expired() ? " (expired)" : "");
            doc.add(PdfKit.facts(new String[][]{
                    {"Licensed to", lic == null || !lic.valid() ? null : lic.customer()},
                    {"License", lic == null || !lic.valid() ? "No license installed: only the core modules are available." : lic.licenseId() + (lic.serial() == null ? "" : ", number " + lic.serial())},
                    {"Issued", lic == null || !lic.valid() || lic.issuedAt() == null ? null : PdfKit.day(lic.issuedAt())},
                    {"Expires", expiry},
                    {"Installation id", lic == null ? null : lic.installationId()}}));

            long core = modules.stream().filter(m -> "CORE".equals(m.state())).count();
            long active = modules.stream().filter(m -> "ACTIVE".equals(m.state())).count();
            long available = modules.stream().filter(m -> "AVAILABLE".equals(m.state())).count();
            long locked = modules.size() - core - active - available;
            section(doc, "Summary");
            doc.add(new Paragraph(modules.size() + " modules: " + core + " core, " + active + " active, " + available + " in your license but not activated, "
                    + locked + " not available (locked, requested or expired).", plain(10)));

            section(doc, "All modules");
            PdfPTable t = new PdfPTable(new float[]{2.2f, 5f, 2.3f});
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            headerCells(t, new String[]{"Module", "What it does", "Status"}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_LEFT});
            for (ModuleDto m : modules) {
                t.addCell(cell(m.name(), bold(9.5f), Element.ALIGN_LEFT, null));
                StringBuilder what = new StringBuilder(m.description() == null ? "" : m.description());
                if (!m.requires().isEmpty()) what.append("\nWorks together with ").append(join.apply(m.requires())).append('.');
                t.addCell(cell(what.toString(), plain(9), Element.ALIGN_LEFT, null));
                String status = label(m);
                if ("REQUESTED".equals(m.state()) && m.requestedAt() != null) {
                    status += "\nsince " + PdfKit.shortDay(m.requestedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
                }
                t.addCell(cell(status, "ACTIVE".equals(m.state()) || "CORE".equals(m.state()) ? bold(9) : plain(9), Element.ALIGN_LEFT, null));
            }
            doc.add(t);
            Paragraph note = new Paragraph("A module can be used when it is in the license and has been activated. Activation is permanent. "
                    + "Products and stock, customers, suppliers, staff and settings are shared by every module and are always available.", muted(8.5f));
            note.setSpacingBefore(8);
            doc.add(note);
        });
    }
}
