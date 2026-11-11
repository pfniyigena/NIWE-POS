package com.posapp.hr;

import com.posapp.i18n.I18n;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.common.ExportFiles;
import com.posapp.common.PdfKit;
import com.posapp.reports.ReportExportService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.posapp.common.PdfKit.SOFT;
import static com.posapp.common.PdfKit.bold;
import static com.posapp.common.PdfKit.cell;
import static com.posapp.common.PdfKit.headerCells;
import static com.posapp.common.PdfKit.money;
import static com.posapp.common.PdfKit.muted;
import static com.posapp.common.PdfKit.nz;
import static com.posapp.common.PdfKit.plain;
import static com.posapp.common.PdfKit.section;

/**
 * The papers of a payroll: the payslip as a PDF (one, or every payslip of a month in one file), the payroll register of a month, the yearly
 * earnings and tax certificate of one employee and the yearly summary of everybody. Only the figures already saved on the payroll lines are
 * used, so a paper always agrees with the payroll screen and with what was posted to Accounting.
 */
@Service
public class PayrollPaperService {

    static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

    private final PayrollRunRepository runRepository;
    private final EmployeeRepository employeeRepository;
    private final PdfKit kit;
    private final ReportExportService exportService;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;

    public PayrollPaperService(PayrollRunRepository runRepository, EmployeeRepository employeeRepository, PdfKit kit, ReportExportService exportService,
                               BranchAccessService branchAccessService, AuditLogService auditLogService) {
        this.runRepository = runRepository;
        this.employeeRepository = employeeRepository;
        this.kit = kit;
        this.exportService = exportService;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
    }

    /** A file to send back. */
    public record Pdf(String filename, byte[] bytes) {
    }

    private void assertCompanyWide() {
        if (branchAccessService.isRestricted()) {
            throw ApiException.forbidden(I18n.t("Payroll covers all branches — it's only available to users who aren't limited to certain branches."));
        }
    }

    private PayrollRun run(Long id) {
        return runRepository.findById(id).orElseThrow(() -> ApiException.notFound(I18n.t("Payroll run not found")));
    }

    static String period(PayrollRun r) {
        return I18n.t(MONTHS[r.getPeriodMonth() - 1]) + " " + r.getPeriodYear();
    }

    private static List<PayrollItem> sorted(PayrollLine l) {
        return l.getItems().stream().sorted(Comparator.comparingInt(PayrollItem::getSortOrder)).toList();
    }

    private static String mask(String account) {
        String a = nz(account).trim();
        return a.length() <= 4 ? a : "****" + a.substring(a.length() - 4);
    }

    // ------------------------------------------------------------------ payslips

    /** One payslip page: who, what was earned, what was taken off, what is paid, and what the employer pays on top. */
    void payslipPage(Document doc, PayrollRun run, PayrollLine l, Employee e) throws Exception {
        String cur = kit.currency();
        kit.head(doc, I18n.t("Payslip"), period(run) + (run.getStatus() == PayrollStatus.DRAFT ? I18n.t("  |  DRAFT - not yet approved") : ""));
        doc.add(PdfKit.facts(new String[][]{
                {I18n.t("Employee"), l.getEmployeeName() + "  (" + nz(l.getEmployeeNumber()) + ")"},
                {I18n.t("Department"), l.getDepartmentName()},
                {I18n.t("Job title"), e != null ? e.getJobTitle() : null},
                {I18n.t("Contract"), l.getContractNumber()},
                {I18n.t("Days paid"), l.getPayableDays() + " of " + l.getDaysInMonth()},
                {I18n.t("Tax number"), e != null ? e.getTaxNumber() : null},
                {I18n.t("Social security no."), e != null ? e.getSocialSecurityNumber() : null},
                {I18n.t("Bank"), (nz(l.getBankName()) + " " + mask(l.getBankAccount())).trim()}}));

        section(doc, I18n.t("Earnings"));
        PdfPTable earn = new PdfPTable(new float[]{5f, 2f});
        earn.setWidthPercentage(100);
        headerCells(earn, new String[]{I18n.t("Description"), I18n.t("Amount ") + cur}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
        for (PayrollItem i : sorted(l)) {
            if (i.getKind() == ItemKind.EARNING) {
                earn.addCell(cell(i.getName(), plain(9), Element.ALIGN_LEFT, null));
                earn.addCell(cell(money(i.getAmount()), plain(9), Element.ALIGN_RIGHT, null));
            }
        }
        earn.addCell(cell(I18n.t("Gross pay"), bold(9.5f), Element.ALIGN_LEFT, SOFT));
        earn.addCell(cell(money(l.getGrossPay()), bold(9.5f), Element.ALIGN_RIGHT, SOFT));
        doc.add(earn);

        section(doc, I18n.t("Deductions"));
        PdfPTable ded = new PdfPTable(new float[]{5f, 2f});
        ded.setWidthPercentage(100);
        headerCells(ded, new String[]{I18n.t("Description"), I18n.t("Amount ") + cur}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollItem i : sorted(l)) {
            if (i.getKind() == ItemKind.TAX || i.getKind() == ItemKind.DEDUCTION) {
                ded.addCell(cell(i.getName(), plain(9), Element.ALIGN_LEFT, null));
                ded.addCell(cell(money(i.getAmount()), plain(9), Element.ALIGN_RIGHT, null));
                total = total.add(i.getAmount());
            }
        }
        ded.addCell(cell(I18n.t("Total deductions"), bold(9.5f), Element.ALIGN_LEFT, SOFT));
        ded.addCell(cell(money(total), bold(9.5f), Element.ALIGN_RIGHT, SOFT));
        doc.add(ded);

        PdfPTable net = new PdfPTable(new float[]{5f, 2f});
        net.setWidthPercentage(100);
        net.setSpacingBefore(10);
        net.addCell(cell(I18n.t("NET PAY"), bold(12), Element.ALIGN_LEFT, PdfKit.DARK));
        net.addCell(cell(money(l.getNetPay()), bold(12), Element.ALIGN_RIGHT, PdfKit.DARK));
        // white text on the dark band
        for (com.lowagie.text.pdf.PdfPCell c : new com.lowagie.text.pdf.PdfPCell[]{net.getRow(0).getCells()[0], net.getRow(0).getCells()[1]}) {
            c.getPhrase().getFont().setColor(java.awt.Color.WHITE);
        }
        doc.add(net);

        boolean anyEmployer = l.getItems().stream().anyMatch(i -> i.getKind() == ItemKind.EMPLOYER);
        if (anyEmployer) {
            section(doc, I18n.t("Paid by the employer on top (not taken from your pay)"));
            PdfPTable er = new PdfPTable(new float[]{5f, 2f});
            er.setWidthPercentage(100);
            for (PayrollItem i : sorted(l)) {
                if (i.getKind() == ItemKind.EMPLOYER) {
                    er.addCell(cell(i.getName(), plain(8.5f), Element.ALIGN_LEFT, null));
                    er.addCell(cell(money(i.getAmount()), plain(8.5f), Element.ALIGN_RIGHT, null));
                }
            }
            doc.add(er);
        }
        Paragraph foot = new Paragraph(I18n.t("Amounts") + (cur.isBlank() ? "" : " in " + cur) + I18n.t(". This payslip was produced by the payroll system and is valid without a signature."), muted(8));
        foot.setSpacingBefore(14);
        doc.add(foot);
    }

    private Map<Long, Employee> employeesOf(PayrollRun run) {
        Map<Long, Employee> m = new HashMap<>();
        employeeRepository.findAllById(run.getLines().stream().map(PayrollLine::getEmployeeId).filter(Objects::nonNull).toList()).forEach(e -> m.put(e.getId(), e));
        return m;
    }

    @Transactional(readOnly = true)
    public Pdf payslip(Long runId, Long lineId) {
        assertCompanyWide();
        PayrollRun run = run(runId);
        PayrollLine line = run.getLines().stream().filter(l -> l.getId().equals(lineId)).findFirst().orElseThrow(() -> ApiException.notFound(I18n.t("Payslip not found")));
        return payslipFile(run, line, line.getEmployeeId() == null ? null : employeeRepository.findById(line.getEmployeeId()).orElse(null));
    }

    /** Also used by the e-mail of payslips and by the employee's own view. */
    public Pdf payslipFile(PayrollRun run, PayrollLine line, Employee e) {
        byte[] bytes = kit.build(false, I18n.t("Payslip ") + period(run) + " - " + line.getEmployeeName(), doc -> payslipPage(doc, run, line, e));
        return new Pdf("payslip-" + nz(line.getEmployeeNumber()) + "-" + run.getPeriodYear() + "-" + String.format("%02d", run.getPeriodMonth()) + ".pdf", bytes);
    }

    @Transactional
    public Pdf allPayslips(Long runId) {
        assertCompanyWide();
        PayrollRun run = run(runId);
        if (run.getLines().isEmpty()) {
            throw ApiException.badRequest(I18n.t("This payroll has nobody in it."));
        }
        Map<Long, Employee> emps = employeesOf(run);
        List<PayrollLine> lines = run.getLines().stream().sorted(Comparator.comparing(PayrollLine::getEmployeeName, Comparator.nullsLast(String::compareToIgnoreCase))).toList();
        byte[] bytes = kit.build(false, I18n.t("Payslips ") + period(run), doc -> {
            boolean first = true;
            for (PayrollLine l : lines) {
                if (!first) {
                    doc.newPage();
                }
                first = false;
                payslipPage(doc, run, l, l.getEmployeeId() == null ? null : emps.get(l.getEmployeeId()));
            }
        });
        auditLogService.record("PAYSLIPS_PDF", I18n.t("Downloaded all payslips of ") + period(run) + " (" + lines.size() + ")", I18n.t("PayrollRun"), runId);
        return new Pdf("payslips-" + run.getPeriodYear() + "-" + String.format("%02d", run.getPeriodMonth()) + ".pdf", bytes);
    }

    // ------------------------------------------------------------------ register of one month

    @Transactional
    public ResponseEntity<byte[]> register(Long runId, String format) {
        assertCompanyWide();
        PayrollRun run = run(runId);
        List<String> h = List.of(I18n.t("No."), I18n.t("Employee"), I18n.t("Department"), I18n.t("Days"), I18n.t("Base salary"), I18n.t("Gross pay"), I18n.t("Taxable pay"), "PAYE", I18n.t("Employee contributions"), I18n.t("Other deductions"), I18n.t("Net pay"), I18n.t("Employer contributions"));
        List<List<String>> body = new ArrayList<>();
        BigDecimal base = BigDecimal.ZERO, gross = BigDecimal.ZERO, taxable = BigDecimal.ZERO, paye = BigDecimal.ZERO, ee = BigDecimal.ZERO, other = BigDecimal.ZERO, net = BigDecimal.ZERO, er = BigDecimal.ZERO;
        for (PayrollLine l : run.getLines().stream().sorted(Comparator.comparing(PayrollLine::getEmployeeName, Comparator.nullsLast(String::compareToIgnoreCase))).toList()) {
            body.add(List.of(nz(l.getEmployeeNumber()), nz(l.getEmployeeName()), nz(l.getDepartmentName()), l.getPayableDays() + "/" + l.getDaysInMonth(), n2(l.getBaseSalary()), n2(l.getGrossPay()),
                    n2(l.getTaxablePay()), n2(l.getPayeTax()), n2(l.getEmployeeContributions()), n2(l.getOtherDeductions()), n2(l.getNetPay()), n2(l.getEmployerContributions())));
            base = base.add(l.getBaseSalary()); gross = gross.add(l.getGrossPay()); taxable = taxable.add(l.getTaxablePay()); paye = paye.add(l.getPayeTax());
            ee = ee.add(l.getEmployeeContributions()); other = other.add(l.getOtherDeductions()); net = net.add(l.getNetPay()); er = er.add(l.getEmployerContributions());
        }
        body.add(List.of("", I18n.t("TOTAL (") + run.getLines().size() + ")", "", "", n2(base), n2(gross), n2(taxable), n2(paye), n2(ee), n2(other), n2(net), n2(er)));
        auditLogService.record("PAYROLL_REGISTER", I18n.t("Downloaded the payroll register of ") + period(run), I18n.t("PayrollRun"), runId);
        return ExportFiles.file(exportService, I18n.t("Payroll register ") + period(run) + (run.getStatus() == PayrollStatus.DRAFT ? " (DRAFT)" : ""), "payroll-register-" + run.getPeriodYear() + "-" + String.format("%02d", run.getPeriodMonth()), h, body, format);
    }

    private static String n2(BigDecimal v) {
        return v == null ? "" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    // ------------------------------------------------------------------ a year

    private record Slice(PayrollRun run, PayrollLine line) {
    }

    /** Every line of the year of approved or paid payrolls (a draft is not a fact yet), oldest month first. */
    private List<Slice> yearLines(int year) {
        List<Slice> out = new ArrayList<>();
        for (PayrollRun r : runRepository.findAllByOrderByPeriodYearDescPeriodMonthDesc()) {
            if (r.getPeriodYear() != year || r.getStatus() == PayrollStatus.DRAFT) {
                continue;
            }
            for (PayrollLine l : r.getLines()) {
                out.add(new Slice(r, l));
            }
        }
        out.sort(Comparator.comparingInt((Slice s) -> s.run().getPeriodMonth()));
        return out;
    }

    private static void checkYear(int year) {
        if (year < 2000 || year > 2200) {
            throw ApiException.badRequest(I18n.t("Choose a valid year."));
        }
    }

    /** The earnings and tax certificate of one employee for one year (approved and paid payrolls only). */
    @Transactional
    public Pdf certificate(Long employeeId, int year) {
        assertCompanyWide();
        checkYear(year);
        Employee e = employeeRepository.findById(employeeId).orElseThrow(() -> ApiException.notFound(I18n.t("Employee not found")));
        branchAccessService.assertCanAccess(e.getBranchId());
        return certificateOf(e, year);
    }

    /** The same certificate for the signed-in employee's own record (no company-wide or branch rule: it is their own pay). */
    @Transactional
    public Pdf ownCertificate(Employee e, int year) {
        checkYear(year);
        return certificateOf(e, year);
    }

    private Pdf certificateOf(Employee e, int year) {
        Long employeeId = e.getId();
        List<Slice> mine = yearLines(year).stream().filter(s -> employeeId.equals(s.line().getEmployeeId())).toList();
        if (mine.isEmpty()) {
            throw ApiException.notFound(e.fullName() + I18n.t(" has no approved payroll in ") + year + ".");
        }
        String cur = kit.currency();
        byte[] bytes = kit.build(false, I18n.t("Earnings and tax certificate ") + year + " - " + e.fullName(), doc -> {
            kit.head(doc, I18n.t("Annual earnings and tax certificate"), I18n.t("Year ") + year + I18n.t("  |  Issued ") + PdfKit.day(LocalDate.now()));
            doc.add(PdfKit.facts(new String[][]{{I18n.t("Employee"), e.fullName() + "  (" + nz(e.getEmployeeNumber()) + ")"}, {I18n.t("National ID"), e.getNationalId()}, {I18n.t("Tax number"), e.getTaxNumber()},
                    {I18n.t("Social security no."), e.getSocialSecurityNumber()}, {I18n.t("Hired"), PdfKit.day(e.getHireDate())}, {I18n.t("Left"), e.getTerminationDate() == null ? null : PdfKit.day(e.getTerminationDate())}}));
            section(doc, I18n.t("Month by month") + (cur.isBlank() ? "" : " (" + cur + ")"));
            PdfPTable t = new PdfPTable(new float[]{1.6f, 1.6f, 1.6f, 1.4f, 1.6f, 1.4f, 1.6f});
            t.setWidthPercentage(100);
            headerCells(t, new String[]{I18n.t("Month"), I18n.t("Gross pay"), I18n.t("Taxable pay"), "PAYE", I18n.t("Employee contrib."), I18n.t("Other ded."), I18n.t("Net pay")},
                    new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT});
            BigDecimal gross = BigDecimal.ZERO, taxable = BigDecimal.ZERO, paye = BigDecimal.ZERO, ee = BigDecimal.ZERO, other = BigDecimal.ZERO, net = BigDecimal.ZERO, er = BigDecimal.ZERO;
            Map<String, BigDecimal> byName = new LinkedHashMap<>();
            for (Slice s : mine) {
                PayrollLine l = s.line();
                t.addCell(cell(I18n.t(MONTHS[s.run().getPeriodMonth() - 1]), plain(8.5f), Element.ALIGN_LEFT, null));
                for (BigDecimal v : new BigDecimal[]{l.getGrossPay(), l.getTaxablePay(), l.getPayeTax(), l.getEmployeeContributions(), l.getOtherDeductions(), l.getNetPay()}) {
                    t.addCell(cell(money(v), plain(8.5f), Element.ALIGN_RIGHT, null));
                }
                gross = gross.add(l.getGrossPay()); taxable = taxable.add(l.getTaxablePay()); paye = paye.add(l.getPayeTax());
                ee = ee.add(l.getEmployeeContributions()); other = other.add(l.getOtherDeductions()); net = net.add(l.getNetPay()); er = er.add(l.getEmployerContributions());
                for (PayrollItem i : l.getItems()) {
                    if (i.getKind() == ItemKind.DEDUCTION && i.getTarget() == PayableTarget.SOCIAL_SECURITY || i.getKind() == ItemKind.EMPLOYER) {
                        byName.merge(i.getName() + (i.getKind() == ItemKind.EMPLOYER ? " (employer)" : ""), i.getAmount(), BigDecimal::add);
                    }
                }
            }
            t.addCell(cell("TOTAL", bold(9), Element.ALIGN_LEFT, SOFT));
            for (BigDecimal v : new BigDecimal[]{gross, taxable, paye, ee, other, net}) {
                t.addCell(cell(money(v), bold(9), Element.ALIGN_RIGHT, SOFT));
            }
            doc.add(t);
            if (!byName.isEmpty()) {
                section(doc, I18n.t("Social security contributions of the year"));
                PdfPTable c = new PdfPTable(new float[]{5f, 2f});
                c.setWidthPercentage(100);
                headerCells(c, new String[]{I18n.t("Contribution"), I18n.t("Amount")}, new int[]{Element.ALIGN_LEFT, Element.ALIGN_RIGHT});
                for (Map.Entry<String, BigDecimal> en : byName.entrySet()) {
                    c.addCell(cell(en.getKey(), plain(8.5f), Element.ALIGN_LEFT, null));
                    c.addCell(cell(money(en.getValue()), plain(8.5f), Element.ALIGN_RIGHT, null));
                }
                doc.add(c);
            }
            Paragraph p = new Paragraph(I18n.t("Income tax withheld during ") + year + ": " + PdfKit.amount(cur, paye) + I18n.t(". Total employer contributions: ") + PdfKit.amount(cur, er)
                    + I18n.t(". Figures are those of the approved and paid payrolls of ") + year + ".", muted(9));
            p.setSpacingBefore(12);
            doc.add(p);
            PdfKit.signatures(doc, I18n.t("For the employer"));
        });
        auditLogService.record("PAYROLL_CERTIFICATE", I18n.t("Issued the ") + year + I18n.t(" earnings and tax certificate of ") + e.fullName(), I18n.t("Employee"), employeeId);
        return new Pdf("certificate-" + nz(e.getEmployeeNumber()) + "-" + year + ".pdf", bytes);
    }

    /** One row per employee for the year: what the yearly return to the tax authority and the social security fund is made from. */
    @Transactional
    public ResponseEntity<byte[]> annualSummary(int year, String format) {
        assertCompanyWide();
        checkYear(year);
        Map<Long, Employee> emps = new HashMap<>();
        employeeRepository.findAll().forEach(e -> emps.put(e.getId(), e));
        record Acc(String no, String name, Employee e, int months, BigDecimal[] v) {
        }
        Map<String, Acc> acc = new LinkedHashMap<>();
        for (Slice s : yearLines(year)) {
            PayrollLine l = s.line();
            String key = l.getEmployeeId() != null ? "e" + l.getEmployeeId() : "n" + l.getEmployeeName();
            Acc a = acc.get(key);
            if (a == null) {
                a = new Acc(nz(l.getEmployeeNumber()), nz(l.getEmployeeName()), l.getEmployeeId() == null ? null : emps.get(l.getEmployeeId()), 0, new BigDecimal[7]);
                java.util.Arrays.fill(a.v(), BigDecimal.ZERO);
                acc.put(key, a);
            }
            BigDecimal[] x = {l.getGrossPay(), l.getTaxablePay(), l.getPayeTax(), l.getEmployeeContributions(), l.getOtherDeductions(), l.getNetPay(), l.getEmployerContributions()};
            for (int i = 0; i < 7; i++) {
                a.v()[i] = a.v()[i].add(x[i]);
            }
            acc.put(key, new Acc(a.no(), a.name(), a.e(), a.months() + 1, a.v()));
        }
        List<String> h = List.of(I18n.t("No."), I18n.t("Employee"), I18n.t("Tax number"), I18n.t("Social security no."), I18n.t("Months paid"), I18n.t("Gross pay"), I18n.t("Taxable pay"), "PAYE", I18n.t("Employee contributions"), I18n.t("Other deductions"), I18n.t("Net pay"), I18n.t("Employer contributions"));
        List<List<String>> body = new ArrayList<>();
        BigDecimal[] tot = new BigDecimal[7];
        java.util.Arrays.fill(tot, BigDecimal.ZERO);
        for (Acc a : acc.values().stream().sorted(Comparator.comparing(Acc::name, String.CASE_INSENSITIVE_ORDER)).toList()) {
            body.add(List.of(a.no(), a.name(), a.e() == null ? "" : nz(a.e().getTaxNumber()), a.e() == null ? "" : nz(a.e().getSocialSecurityNumber()), String.valueOf(a.months()),
                    n2(a.v()[0]), n2(a.v()[1]), n2(a.v()[2]), n2(a.v()[3]), n2(a.v()[4]), n2(a.v()[5]), n2(a.v()[6])));
            for (int i = 0; i < 7; i++) {
                tot[i] = tot[i].add(a.v()[i]);
            }
        }
        body.add(List.of("", I18n.t("TOTAL (") + acc.size() + ")", "", "", "", n2(tot[0]), n2(tot[1]), n2(tot[2]), n2(tot[3]), n2(tot[4]), n2(tot[5]), n2(tot[6])));
        auditLogService.record("PAYROLL_ANNUAL_SUMMARY", I18n.t("Downloaded the ") + year + I18n.t(" payroll summary (") + acc.size() + " employees)", I18n.t("PayrollRun"), null);
        return ExportFiles.file(exportService, I18n.t("Payroll summary ") + year + I18n.t(" (approved and paid payrolls)"), "payroll-summary-" + year, h, body, format);
    }
}
