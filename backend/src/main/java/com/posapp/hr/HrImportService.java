package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.Branch;
import com.posapp.branch.BranchRepository;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrImportDtos.ImportResult;
import com.posapp.hr.dto.HrImportDtos.ImportRow;
import com.posapp.hr.dto.HrRequests.ContractRequest;
import com.posapp.hr.dto.HrRequests.EmployeeRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bringing employees in from an Excel or CSV file. The file is read the same way for a check and for the real import: a check (dry run) tries every row in a
 * transaction that is rolled back, so it reports exactly what the import would do and saves nothing. A row that is wrong, or whose employee is already in the
 * system, is skipped and reported with its spreadsheet row number; the others go in. A "Base salary" column also creates and activates a contract.
 */
@Service
public class HrImportService {

    static final int MAX_ROWS = 2000;
    private static final int MAX_PROBLEMS = 500;

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final BranchRepository branchRepository;
    private final ContractService contractService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate tx;

    public HrImportService(EmployeeService employeeService, EmployeeRepository employeeRepository, DepartmentRepository departmentRepository, PositionRepository positionRepository,
                           BranchRepository branchRepository, ContractService contractService, AuditLogService auditLogService, PlatformTransactionManager txManager) {
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.branchRepository = branchRepository;
        this.contractService = contractService;
        this.auditLogService = auditLogService;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ the columns

    private static final Map<String, String[]> COLUMNS = new LinkedHashMap<>();

    static {
        COLUMNS.put("firstName", new String[]{"firstname", "first", "givenname", "forename"});
        COLUMNS.put("lastName", new String[]{"lastname", "last", "surname", "familyname"});
        COLUMNS.put("name", new String[]{"name", "fullname", "employeename", "staffname"});
        COLUMNS.put("employeeNumber", new String[]{"employeenumber", "employeeno", "empno", "staffnumber", "staffno", "number", "employeeid", "staffid"});
        COLUMNS.put("hireDate", new String[]{"hiredate", "startdate", "datehired", "joindate", "datejoined", "dateofjoining", "employmentdate", "dateofhire", "hired"});
        COLUMNS.put("employmentType", new String[]{"employmenttype", "type", "worktype"});
        COLUMNS.put("jobTitle", new String[]{"jobtitle", "title", "position", "role", "designation"});
        COLUMNS.put("department", new String[]{"department", "dept", "division", "section"});
        COLUMNS.put("branch", new String[]{"branch", "branchname", "branchcode", "site", "office"});
        COLUMNS.put("manager", new String[]{"manager", "reportsto", "supervisor", "linemanager", "boss"});
        COLUMNS.put("dateOfBirth", new String[]{"dateofbirth", "birthdate", "dob", "born"});
        COLUMNS.put("nationalId", new String[]{"nationalid", "nid", "idnumber", "idno", "identitynumber", "nationalidnumber", "passport"});
        COLUMNS.put("phone", new String[]{"phone", "phonenumber", "mobile", "mobilenumber", "tel", "telephone", "cell"});
        COLUMNS.put("email", new String[]{"email", "emailaddress", "mail"});
        COLUMNS.put("address", new String[]{"address", "homeaddress", "residence"});
        COLUMNS.put("emergencyContactName", new String[]{"emergencycontact", "emergencycontactname", "nextofkin", "nextofkinname"});
        COLUMNS.put("emergencyContactPhone", new String[]{"emergencycontactphone", "emergencyphone", "nextofkinphone", "nextofkintel"});
        COLUMNS.put("bankName", new String[]{"bankname", "bank"});
        COLUMNS.put("bankAccount", new String[]{"bankaccount", "accountnumber", "accountno", "bankaccountnumber", "account"});
        COLUMNS.put("taxNumber", new String[]{"taxnumber", "tin", "taxid", "taxno", "taxidentificationnumber"});
        COLUMNS.put("socialSecurityNumber", new String[]{"socialsecuritynumber", "socialsecurity", "ssn", "rssb", "nssf", "cnps", "socialsecurityno", "pensionnumber"});
        COLUMNS.put("baseSalary", new String[]{"basesalary", "salary", "monthlysalary", "grosssalary", "basicsalary", "pay", "basic"});
        COLUMNS.put("notes", new String[]{"notes", "comments", "remarks", "note"});
    }

    private static String key(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------ reading a file

    private record Table(List<String> headers, List<Map<String, String>> rows, List<Integer> numbers) {
    }

    private static final DateTimeFormatter[] DATES = {DateTimeFormatter.ISO_LOCAL_DATE, strict("d/M/uuuu"), strict("d-M-uuuu"), strict("d MMM uuuu"), strict("d MMMM uuuu"), strict("uuuu/M/d")};

    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT);
    }

    private static Table read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to import.");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be read.");
        }
        boolean zip = bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K';
        if (name.endsWith(".xls") && !zip) throw ApiException.badRequest("That is an old Excel file (.xls). Save it as .xlsx or .csv and try again.");
        List<List<String>> grid = zip ? readExcel(bytes) : readCsv(bytes);
        int h = 0;
        while (h < grid.size() && grid.get(h).stream().allMatch(String::isBlank)) h++;
        if (h >= grid.size()) throw ApiException.badRequest("The file is empty.");
        List<String> headers = grid.get(h).stream().map(String::trim).toList();
        List<Map<String, String>> rows = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        for (int i = h + 1; i < grid.size(); i++) {
            List<String> cells = grid.get(i);
            if (cells.stream().allMatch(c -> c == null || c.isBlank())) continue;
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String v = c < cells.size() && cells.get(c) != null ? cells.get(c).trim() : "";
                row.put(String.valueOf(c), v);
            }
            rows.add(row);
            numbers.add(i + 1);
        }
        if (rows.size() > MAX_ROWS) throw ApiException.badRequest("The file has " + rows.size() + " rows. Import up to " + MAX_ROWS + " at a time.");
        return new Table(headers, rows, numbers);
    }

    private static List<List<String>> readExcel(byte[] bytes) {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null) throw ApiException.badRequest("The workbook has no sheets.");
            DataFormatter fmt = new DataFormatter(Locale.ENGLISH);
            List<List<String>> grid = new ArrayList<>();
            int last = sheet.getLastRowNum();
            for (int r = 0; r <= last; r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        if (cell == null) { cells.add(""); continue; }
                        CellType t = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
                        if (t == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) cells.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
                        else if (t == CellType.NUMERIC) {
                            double d = cell.getNumericCellValue();
                            cells.add(d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : BigDecimal.valueOf(d).toPlainString());
                        } else cells.add(fmt.formatCellValue(cell));
                    }
                }
                grid.add(cells);
            }
            return grid;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("That does not look like a valid Excel file (.xlsx).");
        }
    }

    private static List<List<String>> readCsv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        String firstLine = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        char delim = ',';
        long commas = firstLine.chars().filter(c -> c == ',').count(), semis = firstLine.chars().filter(c -> c == ';').count(), tabs = firstLine.chars().filter(c -> c == '\t').count();
        if (semis > commas && semis >= tabs) delim = ';';
        else if (tabs > commas && tabs > semis) delim = '\t';
        List<List<String>> grid = new ArrayList<>();
        try (CSVParser p = CSVFormat.DEFAULT.builder().setDelimiter(delim).setIgnoreEmptyLines(false).setTrim(true).build().parse(new StringReader(text))) {
            for (CSVRecord rec : p) {
                List<String> cells = new ArrayList<>();
                rec.forEach(cells::add);
                grid.add(cells);
            }
        } catch (Exception e) {
            throw ApiException.badRequest("The CSV file could not be read: " + e.getMessage());
        }
        return grid;
    }

    // ------------------------------------------------------------------ one row

    private static final class Rec {
        final Map<String, String> v;

        Rec(Map<String, String> v) {
            this.v = v;
        }

        String text(String k) {
            String s = v.get(k);
            return s == null || s.isBlank() ? null : s.trim();
        }
    }

    private static class RowError extends RuntimeException {
        RowError(String m) {
            super(m, null, false, false);
        }
    }

    private static BigDecimal decimal(Rec r, String k, String label) {
        String s = r.text(k);
        if (s == null) return null;
        String t = s.replace(",", "").replace(" ", "").replace(" ", "");
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new RowError(label + " '" + s + "' is not a number.");
        }
    }

    private static LocalDate date(Rec r, String k, String label) {
        String s = r.text(k);
        if (s == null) return null;
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(s, f);
            } catch (Exception ignored) {
                // try the next layout
            }
        }
        throw new RowError(label + " '" + s + "' is not a date. Use 2026-12-31 or 31/12/2026.");
    }

    private static EmploymentType employmentType(Rec r) {
        String s = r.text("employmentType");
        if (s == null) return EmploymentType.FULL_TIME;
        return switch (key(s)) {
            case "fulltime", "full", "permanent", "regular" -> EmploymentType.FULL_TIME;
            case "parttime", "part" -> EmploymentType.PART_TIME;
            case "casual", "temporary", "temp", "daily", "dailywage", "contract", "fixedterm" -> EmploymentType.CASUAL;
            case "intern", "internship", "trainee", "apprentice" -> EmploymentType.INTERN;
            default -> throw new RowError("Employment type '" + s + "' is not one of: Full time, Part time, Casual, Intern.");
        };
    }

    // ------------------------------------------------------------------ the import itself

    private record Prepared(Rec rec, int row) {
    }

    private record Mapped(List<Prepared> rows, List<String> columns, List<String> ignored) {
    }

    private Mapped mapColumns(Table t) {
        Map<String, String> aliasToCanon = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : COLUMNS.entrySet()) for (String a : e.getValue()) aliasToCanon.putIfAbsent(a, e.getKey());
        Map<Integer, String> byIndex = new LinkedHashMap<>();
        List<String> ignored = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (int i = 0; i < t.headers().size(); i++) {
            String h = t.headers().get(i);
            if (h.isBlank()) continue;
            String canon = aliasToCanon.get(key(h));
            if (canon == null || used.contains(canon)) ignored.add(h);
            else {
                byIndex.put(i, canon);
                used.add(canon);
            }
        }
        if (!used.contains("name") && !(used.contains("firstName") && used.contains("lastName"))) {
            throw ApiException.badRequest("The first row of the file must have a \"First name\" and a \"Last name\" column (or one \"Name\" column). Download the template to see the columns.");
        }
        if (!used.contains("hireDate")) {
            throw ApiException.badRequest("The first row of the file must have a \"Hire date\" column. Download the template to see the columns.");
        }
        List<Prepared> out = new ArrayList<>();
        for (int r = 0; r < t.rows().size(); r++) {
            Map<String, String> v = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : byIndex.entrySet()) v.put(e.getValue(), t.rows().get(r).get(String.valueOf(e.getKey())));
            out.add(new Prepared(new Rec(v), t.numbers().get(r)));
        }
        return new Mapped(out, new ArrayList<>(used), ignored);
    }

    private static String[] names(Rec r) {
        String first = r.text("firstName"), last = r.text("lastName");
        if (first != null && last != null) return new String[]{first, last};
        String full = r.text("name");
        if (full == null) {
            if (first != null) throw new RowError("The last name is empty.");
            throw new RowError("The name is empty.");
        }
        String[] parts = full.trim().split("\\s+", 2);
        if (parts.length < 2) throw new RowError("'" + full + "' needs a first name and a last name.");
        return new String[]{first != null ? first : parts[0], last != null ? last : parts[1]};
    }

    /** Checks (dryRun) or imports an employees file. */
    public ImportResult run(MultipartFile file, boolean dryRun, boolean createDepartments) {
        Table t = read(file);
        Mapped m = mapColumns(t);
        List<ImportRow> problems = new ArrayList<>();
        List<ImportRow> notes = new ArrayList<>();
        List<String> sample = new ArrayList<>();
        int ok = 0;
        int skipped = 0;

        Set<String> seen = new HashSet<>();
        Set<String> fromFile = new HashSet<>();   // people added by earlier rows of this file (a check saves nothing, so they are not in the system yet)
        List<Employee> existing = employeeRepository.findAll();
        for (Employee e : existing) {
            seen.add("n:" + key(e.getEmployeeNumber()));
            if (e.getNationalId() != null) seen.add("i:" + key(e.getNationalId()));
            seen.add("p:" + key(e.getFirstName() + e.getLastName()) + "|" + e.getHireDate());
        }

        for (Prepared p : m.rows()) {
            Rec r = p.rec();
            String label;
            try {
                String[] nm = names(r);
                label = (nm[0] + (nm[1] == null ? "" : " " + nm[1])).trim();
            } catch (RowError e) {
                label = r.text("name") != null ? r.text("name") : "(no name)";
            }
            try {
                String[] nm = names(r);
                LocalDate hire = date(r, "hireDate", "Hire date");
                if (hire == null) throw new RowError("The hire date is empty.");
                List<String> keys = new ArrayList<>();
                if (r.text("employeeNumber") != null) keys.add("n:" + key(r.text("employeeNumber")));
                if (r.text("nationalId") != null) keys.add("i:" + key(r.text("nationalId")));
                keys.add("p:" + key(nm[0] + (nm[1] == null ? "" : nm[1])) + "|" + hire);
                for (String k : keys) {
                    if (seen.contains(k)) {
                        throw new RowError(k.startsWith("n:") ? "An employee with this number is already in the system or earlier in this file."
                                : k.startsWith("i:") ? "An employee with this national ID is already in the system or earlier in this file."
                                : "This employee (same name and hire date) is already in the system or earlier in this file.");
                    }
                }
                String done = importOne(r, nm, hire, dryRun, createDepartments, notes, p.row(), label, fromFile);
                seen.addAll(keys);
                fromFile.add((nm[0] + " " + nm[1]).toLowerCase(Locale.ROOT));
                if (r.text("employeeNumber") != null) fromFile.add(r.text("employeeNumber").toLowerCase(Locale.ROOT));
                ok++;
                if (sample.size() < 10) sample.add(done != null ? done : label);
            } catch (RowError e) {
                skipped++;
                if (problems.size() < MAX_PROBLEMS) problems.add(new ImportRow(p.row(), label, e.getMessage()));
            } catch (ApiException e) {
                skipped++;
                if (problems.size() < MAX_PROBLEMS) problems.add(new ImportRow(p.row(), label, e.getMessage()));
            } catch (RuntimeException e) {
                skipped++;
                if (problems.size() < MAX_PROBLEMS) problems.add(new ImportRow(p.row(), label, "Unexpected error: " + e.getMessage()));
            }
        }
        String msg = dryRun
                ? (ok + " of " + m.rows().size() + " rows can be imported" + (skipped > 0 ? "; " + skipped + " will be skipped." : "."))
                : ("Imported " + ok + " employee" + (ok == 1 ? "" : "s") + (skipped > 0 ? "; " + skipped + " skipped." : "."));
        if (!dryRun && ok > 0) auditLogService.record("EMPLOYEES_IMPORTED", "Imported " + ok + " employees from " + file.getOriginalFilename(), "Employee", null);
        return new ImportResult(dryRun, file.getOriginalFilename(), m.rows().size(), ok, skipped, problems, notes, sample, m.columns(), m.ignored(), msg);
    }

    private String importOne(Rec r, String[] nm, LocalDate hire, boolean dryRun, boolean createDepartments, List<ImportRow> notes, int row, String label, Set<String> fromFile) {
        LocalDate dob = date(r, "dateOfBirth", "Date of birth");
        BigDecimal salary = decimal(r, "baseSalary", "Base salary");
        if (salary != null && salary.signum() <= 0) throw new RowError("Base salary must be more than zero.");
        EmploymentType type = employmentType(r);
        String[] number = new String[1];
        tx.execute(st -> {
            Long departmentId = null;
            if (r.text("department") != null) {
                Department d = departmentRepository.findAll().stream().filter(x -> x.getName().equalsIgnoreCase(r.text("department"))).findFirst().orElse(null);
                if (d == null) {
                    if (!createDepartments) throw new RowError("Department '" + r.text("department") + "' is not in the system. Add it under Organization, or tick \"Add missing departments\".");
                    d = new Department();
                    d.setName(r.text("department"));
                    d.setActive(true);
                    d = departmentRepository.save(d);
                    notes.add(new ImportRow(row, label, "Department '" + d.getName() + "' will be added."));
                }
                departmentId = d.getId();
            }
            Long positionId = null;
            if (r.text("jobTitle") != null) {
                positionId = positionRepository.findAll().stream().filter(x -> x.getTitle().equalsIgnoreCase(r.text("jobTitle"))).map(Position::getId).findFirst().orElse(null);
            }
            Long branchId = null;
            if (r.text("branch") != null) {
                String b = r.text("branch");
                Branch br = branchRepository.findAll().stream().filter(x -> x.getName().equalsIgnoreCase(b) || (x.getCode() != null && x.getCode().equalsIgnoreCase(b))).findFirst().orElse(null);
                if (br == null) throw new RowError("Branch '" + b + "' is not in the system. Use its name or code as in Branches.");
                branchId = br.getId();
            }
            Long managerId = null;
            if (r.text("manager") != null) {
                String mg = r.text("manager");
                List<Employee> hits = employeeRepository.findAll().stream()
                        .filter(x -> x.getEmployeeNumber().equalsIgnoreCase(mg) || x.fullName().equalsIgnoreCase(mg)).toList();
                if (hits.isEmpty() && dryRun && fromFile.contains(mg.toLowerCase(Locale.ROOT))) {
                    managerId = null;   // added by an earlier row of this file: it will exist when the file is really imported
                } else if (hits.isEmpty()) throw new RowError("Manager '" + mg + "' is not an employee yet. Use the employee number or the full name of someone already in the system.");
                else if (hits.size() > 1) throw new RowError("More than one employee is called '" + mg + "'. Use the employee number.");
                else managerId = hits.get(0).getId();
            }
            EmployeeRequest req = new EmployeeRequest(r.text("employeeNumber"), nm[0], nm[1], dob, r.text("nationalId"), r.text("phone"), r.text("email"), r.text("address"),
                    r.text("emergencyContactName"), r.text("emergencyContactPhone"), hire, type, r.text("jobTitle"), departmentId, positionId, managerId, branchId, null,
                    r.text("bankName"), r.text("bankAccount"), r.text("taxNumber"), r.text("socialSecurityNumber"), null, r.text("notes"));
            var created = employeeService.create(req);
            number[0] = created.employeeNumber();
            if (salary != null) {
                var c = contractService.create(new ContractRequest(created.id(), com.posapp.hr.ContractType.PERMANENT, hire, null, r.text("jobTitle"), salary, null, null));
                contractService.activate(c.id());
            }
            if (dryRun) st.setRollbackOnly();
            return null;
        });
        return dryRun ? label : number[0] + " " + label;
    }

    // ------------------------------------------------------------------ templates

    private static final String[] HEADS = {"First name", "Last name", "Employee number", "Hire date", "Employment type", "Job title", "Department", "Branch", "Manager", "Date of birth", "National ID",
            "Phone", "Email", "Address", "Emergency contact", "Emergency contact phone", "Bank name", "Bank account", "Tax number", "Social security number", "Base salary", "Notes"};
    private static final String[][] EXAMPLES = {
            {"Jane", "Mukasa", "", "2025-03-01", "Full time", "Accountant", "Finance", "", "", "1990-05-12", "1199080012345678", "0788 123 456", "jane@example.com", "Kigali", "John Mukasa", "0788 000 111", "Bank of Kigali",
                    "00012-3456789-10", "TIN-100200", "SSN-445566", "650000", ""},
            {"Peter", "Okello", "", "2026-01-15", "Part time", "Driver", "Operations", "", "Jane Mukasa", "", "", "0701 987 654", "", "", "", "", "", "", "", "", "300000", "Weekends only"}};
    private static final String[][] HELP = {
            {"First name / Last name", "Required (or one \"Name\" column with both). A name with two words in one Name column is split at the first space."},
            {"Employee number", "Optional. Empty = the next number (EMP-0001...). A number already in the system is skipped as a duplicate."},
            {"Hire date", "Required. A date, like 2026-12-31 or 31/12/2026."},
            {"Employment type", "Full time, Part time, Casual or Intern. Empty means Full time."},
            {"Job title", "Free text. If it matches a position in Organization, the employee is linked to it."},
            {"Department", "Must already exist under Organization (or tick \"Add missing departments\" when importing)."},
            {"Branch", "The branch name or code. Empty means head office."},
            {"Manager", "The employee number or full name of someone already in the system."},
            {"Date of birth", "A date in the past."},
            {"National ID / Tax number / Social security number", "Free text. The same national ID twice is skipped as a duplicate."},
            {"Bank name / Bank account", "Where the salary is paid."},
            {"Base salary", "Optional. A number per month, no currency sign. When given, a permanent contract starting on the hire date is created and activated."},
            {"Duplicates", "An employee with the same number, the same national ID, or the same name and hire date as one already in the system (or earlier in the file) is skipped."},
            {"Staff login", "Linking an employee to a staff login is done on the employee page, not by the import."}};

    public byte[] templateCsv() {
        StringBuilder b = new StringBuilder("﻿");
        csvLine(b, HEADS);
        for (String[] row : EXAMPLES) csvLine(b, row);
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void csvLine(StringBuilder b, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) b.append(',');
            String c = cells[i] == null ? "" : cells[i];
            if (c.contains(",") || c.contains("\"") || c.contains("\n")) c = "\"" + c.replace("\"", "\"\"") + "\"";
            b.append(c);
        }
        b.append("\r\n");
    }

    public byte[] templateExcel() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            Sheet s = wb.createSheet("Employees");
            Row h = s.createRow(0);
            for (int i = 0; i < HEADS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADS[i]);
                c.setCellStyle(bold);
                s.setColumnWidth(i, Math.max(14, HEADS[i].length() + 4) * 256);
            }
            for (int r = 0; r < EXAMPLES.length; r++) {
                Row row = s.createRow(r + 1);
                for (int i = 0; i < EXAMPLES[r].length; i++) row.createCell(i).setCellValue(EXAMPLES[r][i]);
            }
            Sheet help = wb.createSheet("How to fill it in");
            Row hr = help.createRow(0);
            hr.createCell(0).setCellValue("Column");
            hr.createCell(1).setCellValue("What to put in it");
            hr.getCell(0).setCellStyle(bold);
            hr.getCell(1).setCellStyle(bold);
            for (int r = 0; r < HELP.length; r++) {
                Row row = help.createRow(r + 1);
                row.createCell(0).setCellValue(HELP[r][0]);
                row.createCell(1).setCellValue(HELP[r][1]);
            }
            help.setColumnWidth(0, 40 * 256);
            help.setColumnWidth(1, 110 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the template", e);
        }
    }
}
