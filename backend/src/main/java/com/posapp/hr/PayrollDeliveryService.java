package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.hr.dto.HrDtos.PayslipEmailResultDto;
import com.posapp.reports.ReportExportService;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Gets a finished payroll out of the system: payslips by email and the bank transfer file. */
@Service
public class PayrollDeliveryService {

    /** A downloadable file. */
    public record FileOut(String filename, String contentType, byte[] bytes) {
    }

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

    private final PayrollRunRepository runRepository;
    private final EmployeeRepository employeeRepository;
    private final MailService mailService;
    private final ReportExportService exportService;
    private final StoreSettingsRepository storeSettingsRepository;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;

    public PayrollDeliveryService(PayrollRunRepository runRepository, EmployeeRepository employeeRepository, MailService mailService,
                                  ReportExportService exportService, StoreSettingsRepository storeSettingsRepository,
                                  BranchAccessService branchAccessService, AuditLogService auditLogService) {
        this.runRepository = runRepository;
        this.employeeRepository = employeeRepository;
        this.mailService = mailService;
        this.exportService = exportService;
        this.storeSettingsRepository = storeSettingsRepository;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
    }

    // ---- payslips by email ----

    /** Emails the payslip (HTML body plus a PDF) of one line, or of every line when {@code lineId} is null. */
    @Transactional(readOnly = true)
    public PayslipEmailResultDto emailPayslips(Long runId, Long lineId) {
        PayrollRun run = approvedRun(runId);
        if (!mailService.isConfigured()) {
            throw ApiException.badRequest("Email isn't configured yet — set up SMTP in Admin > Settings > Email first");
        }
        StoreSettings store = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        String business = store != null && store.getBusinessName() != null ? store.getBusinessName() : "Payslip";
        String currency = store != null && store.getCurrencySymbol() != null ? store.getCurrencySymbol() : "";
        String period = MONTHS[run.getPeriodMonth() - 1] + " " + run.getPeriodYear();

        List<PayrollLine> lines = run.getLines().stream().filter(l -> lineId == null || l.getId().equals(lineId))
                .sorted(Comparator.comparing(PayrollLine::getEmployeeName, Comparator.nullsLast(String::compareToIgnoreCase))).toList();
        if (lineId != null && lines.isEmpty()) {
            throw ApiException.notFound("Payslip not found");
        }
        int sent = 0;
        List<String> noEmail = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (PayrollLine l : lines) {
            Employee e = l.getEmployeeId() != null ? employeeRepository.findById(l.getEmployeeId()).orElse(null) : null;
            if (e == null || e.getEmail() == null || e.getEmail().isBlank()) {
                noEmail.add(l.getEmployeeName());
                continue;
            }
            try {
                String filename = "payslip-" + l.getEmployeeNumber() + "-" + YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()) + ".pdf";
                byte[] pdf = exportService.toPdf("Payslip " + period + " — " + l.getEmployeeName(), List.of("Item", "Amount"), pdfRows(l));
                mailService.send(e.getEmail(), "Your payslip for " + period + " — " + business, html(business, period, currency, l),
                        List.of(new EmailAttachment(filename, "application/pdf", pdf)));
                sent++;
            } catch (RuntimeException ex) {
                failed.add(l.getEmployeeName() + ": " + ex.getMessage());
            }
        }
        auditLogService.record("PAYSLIPS_EMAILED", "Emailed " + sent + " payslip(s) for " + period + (failed.isEmpty() ? "" : ", " + failed.size() + " failed"), "PayrollRun", runId);
        return new PayslipEmailResultDto(sent, noEmail, failed);
    }

    private List<List<String>> pdfRows(PayrollLine l) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Employee", l.getEmployeeName() + " (" + l.getEmployeeNumber() + ")"));
        rows.add(List.of("Days paid", l.getPayableDays() + " of " + l.getDaysInMonth()));
        for (PayrollItem i : sorted(l)) {
            if (i.getKind() == ItemKind.EARNING) {
                rows.add(List.of(i.getName(), fmt(i.getAmount())));
            }
        }
        rows.add(List.of("GROSS PAY", fmt(l.getGrossPay())));
        for (PayrollItem i : sorted(l)) {
            if (i.getKind() == ItemKind.TAX || i.getKind() == ItemKind.DEDUCTION) {
                rows.add(List.of(i.getName() + " (deduction)", fmt(i.getAmount())));
            }
        }
        rows.add(List.of("NET PAY", fmt(l.getNetPay())));
        for (PayrollItem i : sorted(l)) {
            if (i.getKind() == ItemKind.EMPLOYER) {
                rows.add(List.of(i.getName() + " (paid by employer)", fmt(i.getAmount())));
            }
        }
        return rows;
    }

    private String html(String business, String period, String currency, PayrollLine l) {
        StringBuilder b = new StringBuilder();
        b.append("<p>Dear ").append(esc(l.getEmployeeName())).append(",</p>");
        b.append("<p>Here is your payslip for <strong>").append(esc(period)).append("</strong> (PDF attached).</p>");
        b.append("<table cellpadding=\"4\" style=\"border-collapse:collapse;\">");
        for (PayrollItem i : sorted(l)) {
            if (i.getKind() == ItemKind.EARNING) {
                row(b, i.getName(), i.getAmount(), false);
            }
        }
        row(b, "Gross pay", l.getGrossPay(), true);
        for (PayrollItem i : sorted(l)) {
            if (i.getKind() == ItemKind.TAX || i.getKind() == ItemKind.DEDUCTION) {
                row(b, i.getName(), i.getAmount().negate(), false);
            }
        }
        row(b, "Net pay " + (currency.isBlank() ? "" : "(" + currency + ")"), l.getNetPay(), true);
        b.append("</table>");
        b.append("<p>Kind regards,<br/>").append(esc(business)).append("</p>");
        return b.toString();
    }

    private void row(StringBuilder b, String label, BigDecimal amount, boolean bold) {
        String style = bold ? " style=\"border-top:1px solid #999;font-weight:bold;\"" : "";
        b.append("<tr").append(style).append("><td>").append(esc(label)).append("</td><td align=\"right\">").append(fmt(amount)).append("</td></tr>");
    }

    private List<PayrollItem> sorted(PayrollLine l) {
        return l.getItems().stream().sorted(Comparator.comparingInt(PayrollItem::getSortOrder)).toList();
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---- bank transfer file ----

    /** One payment on the bank list, laid out like the payroll sheet banks are used to: No, SURNAME, NAME, NET SALARY, ACCOUNT NO, BANK NAME. */
    private record BankRow(String surname, String name, BigDecimal net, String account, String bank) {
    }

    /**
     * The bank transfer list of an approved payroll, as Excel or CSV. Same columns as the usual payroll sheet; amounts are whole
     * currency units, people with nothing to pay are left out, and the list is in alphabetical order of surname.
     */
    @Transactional
    public FileOut bankFile(Long runId, String format) {
        PayrollRun run = approvedRun(runId);
        String period = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).toString();
        Map<Long, Employee> employees = new HashMap<>();
        employeeRepository.findAllById(run.getLines().stream().map(PayrollLine::getEmployeeId).filter(Objects::nonNull).toList())
                .forEach(e -> employees.put(e.getId(), e));
        List<BankRow> rows = new ArrayList<>();
        for (PayrollLine l : run.getLines()) {
            BigDecimal net = l.getNetPay().setScale(0, RoundingMode.HALF_UP);
            if (net.signum() <= 0) {
                continue;
            }
            Employee e = l.getEmployeeId() == null ? null : employees.get(l.getEmployeeId());
            String surname;
            String name;
            if (e != null) {
                surname = nz(e.getLastName()).trim();
                name = nz(e.getFirstName()).trim();
            } else {
                // The employee record is gone; fall back to the name saved on the payslip ("First Last").
                String full = nz(l.getEmployeeName()).trim();
                int sp = full.lastIndexOf(' ');
                surname = sp < 0 ? full : full.substring(sp + 1);
                name = sp < 0 ? "" : full.substring(0, sp);
            }
            rows.add(new BankRow(surname, name, net, nz(l.getBankAccount()).trim(), nz(l.getBankName()).trim()));
        }
        rows.sort(Comparator.comparing(BankRow::surname, String.CASE_INSENSITIVE_ORDER).thenComparing(BankRow::name, String.CASE_INSENSITIVE_ORDER));
        auditLogService.record("PAYROLL_BANK_FILE", "Downloaded the bank transfer file for " + period + " (" + rows.size() + " payments)", "PayrollRun", runId);

        String[] headers = {"No", "SURNAME", "NAME", "NET SALARY", "ACCOUNT NO", "BANK NAME"};
        if ("xlsx".equalsIgnoreCase(format)) {
            return new FileOut("bank-transfers-" + period + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bankWorkbook(headers, rows));
        }
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(",", headers)).append("\r\n");
        int no = 1;
        for (BankRow r : rows) {
            csv.append(no++).append(',').append(csv(r.surname())).append(',').append(csv(r.name())).append(',')
                    .append(r.net().toPlainString()).append(',').append(csv(r.account())).append(',').append(csv(r.bank())).append("\r\n");
        }
        return new FileOut("bank-transfers-" + period + ".csv", "text/csv; charset=UTF-8", csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] bankWorkbook(String[] headers, List<BankRow> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            XSSFFont bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("_(* #,##0_);_(* \\(#,##0\\);_(* \"-\"??_);_(@_)"));
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));

            Row h = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(head);
            }
            int r = 1;
            for (BankRow b : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(r - 1);
                row.createCell(1).setCellValue(b.surname());
                row.createCell(2).setCellValue(b.name());
                Cell net = row.createCell(3);
                net.setCellValue(b.net().doubleValue());
                net.setCellStyle(money);
                Cell acc = row.createCell(4);
                acc.setCellValue(b.account());   // stored as text so leading zeros and dashes survive
                acc.setCellStyle(text);
                row.createCell(5).setCellValue(b.bank());
            }
            for (int c = 0; c < headers.length; c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build the bank file", e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String csv(String v) {
        // Also neutralise spreadsheet formulas typed into names or references.
        String safe = !v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("-?\\d+(\\.\\d+)?") ? "'" + v : v;
        return safe.contains(",") || safe.contains("\"") || safe.contains("\n") ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    private PayrollRun approvedRun(Long runId) {
        if (branchAccessService.isRestricted()) {
            throw ApiException.forbidden("Payroll covers all branches — it's only available to users who aren't limited to certain branches.");
        }
        PayrollRun run = runRepository.findById(runId).orElseThrow(() -> ApiException.notFound("Payroll run not found"));
        if (run.getStatus() == PayrollStatus.DRAFT) {
            throw ApiException.conflict("Approve the payroll first — payslips and the bank file are only available for an approved payroll.");
        }
        return run;
    }
}
