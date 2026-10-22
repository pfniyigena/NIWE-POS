package com.posapp.law;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawDtos.MatterDto;
import com.posapp.law.dto.LawImportDtos.ImportResult;
import com.posapp.law.dto.LawImportDtos.ImportRow;
import com.posapp.law.dto.LawRequests.MatterRequest;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bringing clients and open matters in from an Excel or CSV file. The file is read the same way for a check and for the real import: a check (dry run) tries every row in a
 * transaction that is rolled back, so it reports what the import would do and saves nothing. A row that is wrong, or that is already in the system, is skipped and reported
 * with its spreadsheet row number; the others go in. A matter row can add its client when the client is not there yet.
 */
@Service
public class LawImportService {

    static final int MAX_ROWS = 2000;
    private static final int MAX_PROBLEMS = 500;

    private final LawAccess access;
    private final LawService lawService;
    private final LawMatterRepository matterRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final TransactionTemplate tx;

    public LawImportService(LawAccess access, LawService lawService, LawMatterRepository matterRepository, CustomerRepository customerRepository, UserRepository userRepository,
                            AuditLogService auditLogService, PlatformTransactionManager txManager) {
        this.access = access;
        this.lawService = lawService;
        this.matterRepository = matterRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ the columns

    private static final Map<String, String[]> CLIENT_COLUMNS = new LinkedHashMap<>();
    private static final Map<String, String[]> MATTER_COLUMNS = new LinkedHashMap<>();

    static {
        CLIENT_COLUMNS.put("name", new String[]{"name", "clientname", "client", "fullname", "customer", "customername", "company", "companyname"});
        CLIENT_COLUMNS.put("phone", new String[]{"phone", "phonenumber", "mobile", "mobilenumber", "tel", "telephone", "contact", "cell", "whatsapp"});
        CLIENT_COLUMNS.put("email", new String[]{"email", "emailaddress", "mail"});
        CLIENT_COLUMNS.put("taxId", new String[]{"taxid", "tin", "tinnumber", "taxnumber", "vat", "vatnumber", "taxidentificationnumber"});

        MATTER_COLUMNS.put("title", new String[]{"title", "matter", "mattertitle", "matterdescription", "casetitle", "casename", "subject", "mattername"});
        MATTER_COLUMNS.put("client", new String[]{"client", "clientname", "customer", "customername"});
        MATTER_COLUMNS.put("clientPhone", new String[]{"clientphone", "clientmobile", "clienttel", "phone", "mobile", "tel"});
        MATTER_COLUMNS.put("clientEmail", new String[]{"clientemail", "email", "emailaddress"});
        MATTER_COLUMNS.put("practiceArea", new String[]{"practicearea", "area", "practice", "department", "category", "type", "mattertype"});
        MATTER_COLUMNS.put("jurisdiction", new String[]{"jurisdiction", "country", "venue"});
        MATTER_COLUMNS.put("court", new String[]{"court", "courtorforum", "forum", "tribunal", "courtname"});
        MATTER_COLUMNS.put("caseNumber", new String[]{"casenumber", "courtcasenumber", "courtcaseno", "caseno", "suitnumber", "suitno", "causenumber", "courtfile", "courtfilenumber"});
        MATTER_COLUMNS.put("billing", new String[]{"billing", "billingtype", "feetype", "feebasis", "fees", "billedas"});
        MATTER_COLUMNS.put("billingNote", new String[]{"billingnote", "feenote", "feeterms", "feenotes"});
        MATTER_COLUMNS.put("description", new String[]{"description", "details", "summary", "about", "scope", "notes"});
        MATTER_COLUMNS.put("lead", new String[]{"lead", "leadlawyer", "leader", "lawyer", "attorney", "advocate", "responsible", "partner", "responsiblelawyer", "handledby"});
        MATTER_COLUMNS.put("opened", new String[]{"opened", "openeddate", "dateopened", "startdate", "started", "filedon", "date", "openedon"});
        MATTER_COLUMNS.put("restricted", new String[]{"restricted", "confidential", "private"});
    }

    private static String key(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------ reading a file

    private record Table(List<String> headers, List<Map<String, String>> rows, List<Integer> numbers) {
    }

    private static final DateTimeFormatter[] DATES = {DateTimeFormatter.ISO_LOCAL_DATE, strict("d/M/uuuu"), strict("d-M-uuuu"), strict("d MMM uuuu"), strict("d MMMM uuuu"), strict("uuuu/M/d")};

    /** A layout that refuses days that do not exist (31/02/2025 is an error, not 28 February). */
    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withResolverStyle(java.time.format.ResolverStyle.STRICT);
    }

    /** The file as a list of rows keyed by the column position. Excel (.xlsx) and CSV (comma, semicolon or tab) are understood. */
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
        while (h < grid.size() && grid.get(h).stream().allMatch(c -> c == null || c.isBlank())) h++;
        if (h >= grid.size()) throw ApiException.badRequest("The file is empty.");
        List<String> headers = grid.get(h).stream().map(c -> c == null ? "" : c.trim()).toList();
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

    private static Boolean yesNo(Rec r, String k, String label) {
        String s = r.text(k);
        if (s == null) return null;
        return switch (key(s)) {
            case "yes", "y", "true", "1", "x" -> Boolean.TRUE;
            case "no", "n", "false", "0" -> Boolean.FALSE;
            default -> throw new RowError(label + " '" + s + "': use Yes or No.");
        };
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

    private static final Map<String, BillingType> BILLING = new LinkedHashMap<>();

    static {
        for (String a : "hourly,hour,time,timebased,byhour,hourlyrate".split(",")) BILLING.put(a, BillingType.HOURLY);
        for (String a : "fixed,fixedfee,flat,flatfee,lumpsum".split(",")) BILLING.put(a, BillingType.FIXED);
        for (String a : "retainer,retained,retainerfee".split(",")) BILLING.put(a, BillingType.RETAINER);
        for (String a : "contingency,contingent,successfee,percentage,nowinnofee".split(",")) BILLING.put(a, BillingType.CONTINGENCY);
        for (String a : "probono,legalaid,free,nocharge".split(",")) BILLING.put(a, BillingType.PRO_BONO);
    }

    private static BillingType billing(Rec r) {
        String s = r.text("billing");
        if (s == null) return null;
        BillingType b = BILLING.get(key(s));
        if (b == null) throw new RowError("Billing '" + s + "' is not one of: Hourly, Fixed fee, Retainer, Contingency, Pro bono.");
        return b;
    }

    // ------------------------------------------------------------------ mapping the headings

    private record Prepared(Rec rec, int row) {
    }

    private record Mapped(List<Prepared> rows, List<String> columns, List<String> ignored) {
    }

    private static Mapped mapColumns(Table t, Map<String, String[]> cols, String requiredKey, String requiredLabel) {
        Map<String, String> aliasToCanon = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : cols.entrySet()) for (String a : e.getValue()) aliasToCanon.putIfAbsent(a, e.getKey());
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
        if (!used.contains(requiredKey)) throw ApiException.badRequest("The first row of the file must have a \"" + requiredLabel + "\" column. Download the template to see the columns.");
        List<Prepared> out = new ArrayList<>();
        for (int r = 0; r < t.rows().size(); r++) {
            Map<String, String> v = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : byIndex.entrySet()) v.put(e.getValue(), t.rows().get(r).get(String.valueOf(e.getKey())));
            out.add(new Prepared(new Rec(v), t.numbers().get(r)));
        }
        return new Mapped(out, new ArrayList<>(used), ignored);
    }

    private Long leadId(String name) {
        if (name == null) return null;
        for (AppUser u : userRepository.findByActiveTrue()) {
            if (LawAccess.name(u).equalsIgnoreCase(name.trim()) || u.getUsername().equalsIgnoreCase(name.trim())) return u.getId();
        }
        throw new RowError("Lead lawyer '" + name + "' is not a user. Use the person's name or username as they are in Users, or leave it empty for yourself.");
    }

    private static String digits(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    // ------------------------------------------------------------------ the import itself

    /** Checks (dryRun) or imports a clients or matters file. */
    public ImportResult run(Actor a, String kind, MultipartFile file, boolean dryRun, boolean createClients, boolean clearConflicts) {
        access.on();
        boolean clients = "clients".equals(kind);
        if (!clients && !"matters".equals(kind)) throw ApiException.notFound("Nothing to import for '" + kind + "'.");
        if (!a.admin() && !a.manager()) throw ApiException.forbidden("You cannot import into the law firm.");
        Table t = read(file);
        Mapped m = clients ? mapColumns(t, CLIENT_COLUMNS, "name", "Name") : mapColumns(t, MATTER_COLUMNS, "title", "Title");
        if (!clients && !m.columns().contains("client")) throw ApiException.badRequest("The first row of the file must have a \"Client\" column. Download the template to see the columns.");
        List<ImportRow> problems = new ArrayList<>();
        List<ImportRow> notes = new ArrayList<>();
        List<String> sample = new ArrayList<>();
        int ok = 0;
        int skipped = 0;
        int added = 0;

        List<Customer> customers = new ArrayList<>();
        for (Customer c : customerRepository.findAll()) if (!c.isWalkIn()) customers.add(c);
        Set<String> seen = new HashSet<>();
        Map<String, Set<String>> byName = new HashMap<>();
        // clients added earlier in this check (rolled back), by name key, so later matter rows can use them
        Set<String> virtualClients = new HashSet<>();
        if (clients) {
            for (Customer c : customers) {
                if (!digits(c.getPhone()).isEmpty()) seen.add("p:" + digits(c.getPhone()));
                if (c.getEmail() != null && !c.getEmail().isBlank()) seen.add("e:" + c.getEmail().trim().toLowerCase(Locale.ROOT));
                if (digits(c.getPhone()).isEmpty() && (c.getEmail() == null || c.getEmail().isBlank())) seen.add("n:" + key(c.getName()));
            }
        } else {
            for (LawMatter x : matterRepository.findAll()) {
                seen.add("m:" + x.getClientCustomerId() + "|" + key(x.getTitle()));
                if (x.getCourtCaseNumber() != null && !x.getCourtCaseNumber().isBlank()) seen.add("c:" + key(x.getCourtCaseNumber()));
            }
        }

        for (Prepared p : m.rows()) {
            Rec r = p.rec();
            String label = clients ? (r.text("name") == null ? "(no name)" : r.text("name")) : (r.text("title") == null ? "(no title)" : r.text("title"));
            try {
                String done;
                if (clients) {
                    List<String> keys = clientKeys(r);
                    for (String k : keys) {
                        if (seen.contains(k)) throw new RowError(k.startsWith("p:") ? "A client with this phone number is already in the system or earlier in this file."
                                : k.startsWith("e:") ? "A client with this e-mail is already in the system or earlier in this file." : "A client with this name is already in the system or earlier in this file.");
                    }
                    done = importClient(r, dryRun);
                    seen.addAll(keys);
                    added++;
                } else {
                    done = importMatter(a, r, customers, createClients, clearConflicts, dryRun, seen, virtualClients, notes, p.row(), label);
                    if (done != null && done.startsWith("+")) {
                        added++;
                        done = done.substring(1);
                    }
                }
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
        int clientsAdded = clients ? ok : added;
        String noun = clients ? (ok == 1 ? "client" : "clients") : (ok == 1 ? "matter" : "matters");
        String extra = !clients && clientsAdded > 0 ? (dryRun ? " " + clientsAdded + " new client" + (clientsAdded == 1 ? "" : "s") + " will be added." : " " + clientsAdded + " new client" + (clientsAdded == 1 ? "" : "s") + " added.") : "";
        String msg = dryRun
                ? (ok + " of " + m.rows().size() + " rows can be imported" + (skipped > 0 ? "; " + skipped + " will be skipped." : ".") + extra)
                : ("Imported " + ok + " " + noun + (skipped > 0 ? "; " + skipped + " skipped." : ".") + extra);
        if (!dryRun && ok > 0) auditLogService.record("LAW_IMPORT", "Imported " + ok + " " + kind + " from " + file.getOriginalFilename(), clients ? "Customer" : "LawMatter", null);
        return new ImportResult(kind, dryRun, file.getOriginalFilename(), m.rows().size(), ok, skipped, clientsAdded, problems, notes, sample, m.columns(), m.ignored(), msg);
    }

    private static List<String> clientKeys(Rec r) {
        List<String> k = new ArrayList<>();
        String phone = digits(r.text("phone"));
        if (!phone.isEmpty()) k.add("p:" + phone);
        if (r.text("email") != null) k.add("e:" + r.text("email").toLowerCase(Locale.ROOT));
        if (phone.isEmpty() && r.text("email") == null) k.add("n:" + key(r.text("name")));
        return k;
    }

    private String importClient(Rec r, boolean dryRun) {
        if (r.text("name") == null) throw new RowError("The name is empty.");
        tx.execute(st -> {
            Customer c = new Customer();
            c.setName(r.text("name"));
            c.setPhone(r.text("phone"));
            c.setEmail(r.text("email"));
            c.setTaxId(r.text("taxId"));
            customerRepository.save(c);
            if (dryRun) st.setRollbackOnly();
            return null;
        });
        return null;
    }

    /** Finds the client a matter row names: by e-mail, then phone, then name (which must be unique). Null when there is none. */
    private static Customer findClient(Rec r, List<Customer> customers) {
        String email = r.text("clientEmail");
        if (email != null) {
            for (Customer c : customers) if (c.getEmail() != null && c.getEmail().trim().equalsIgnoreCase(email)) return c;
        }
        String phone = digits(r.text("clientPhone"));
        if (!phone.isEmpty()) {
            for (Customer c : customers) if (phone.equals(digits(c.getPhone()))) return c;
        }
        String name = r.text("client");
        if (name == null) return null;
        List<Customer> hits = new ArrayList<>();
        for (Customer c : customers) if (c.getName() != null && key(c.getName()).equals(key(name))) hits.add(c);
        if (hits.size() > 1) throw new RowError("More than one client is called '" + name + "'. Put the client's phone or e-mail in the file so the right one can be found.");
        return hits.isEmpty() ? null : hits.get(0);
    }

    /** Returns the label for the sample; a leading "+" means a new client was added with the matter. */
    private String importMatter(Actor a, Rec r, List<Customer> customers, boolean createClients, boolean clearConflicts, boolean dryRun, Set<String> seen, Set<String> virtualClients,
                                List<ImportRow> notes, int row, String label) {
        if (r.text("title") == null) throw new RowError("The matter title is empty.");
        if (r.text("client") == null) throw new RowError("The client is empty.");
        BillingType billing = billing(r);
        LocalDate opened = date(r, "opened", "Opened date");
        if (opened != null && opened.isAfter(LocalDate.now())) throw new RowError("Opened date " + opened + " is in the future.");
        Boolean restricted = yesNo(r, "restricted", "Restricted");
        Long lead = leadId(r.text("lead"));
        Customer client = findClient(r, customers);
        String nameKey = "v:" + key(r.text("client"));
        boolean addClient = false;      // a client is added with this row
        boolean virtual = false;        // the client exists only inside an earlier, rolled-back row of this check
        if (client == null) {
            if (dryRun && virtualClients.contains(nameKey)) virtual = true;
            else if (!createClients) throw new RowError("Client '" + r.text("client") + "' is not in the system. Import the clients first, or tick \"Add clients that are not there yet\".");
            else addClient = true;
        }
        String clientKey = client != null ? String.valueOf(client.getId()) : nameKey;
        String dupMatter = "m:" + clientKey + "|" + key(r.text("title"));
        if (seen.contains(dupMatter)) throw new RowError("This client already has a matter with this title, in the system or earlier in this file.");
        String caseKey = r.text("caseNumber") == null ? null : "c:" + key(r.text("caseNumber"));
        if (caseKey != null && seen.contains(caseKey)) throw new RowError("A matter with court case number '" + r.text("caseNumber") + "' is already in the system or earlier in this file.");

        final boolean doAdd = addClient;
        final Customer found = client;
        String[] number = new String[1];
        Customer[] created = new Customer[1];
        if (virtual) {
            number[0] = null; // nothing to look up: the client only exists inside an earlier row of this check
        } else {
            tx.execute(st -> {
                Customer c = found;
                if (doAdd) {
                    c = new Customer();
                    c.setName(r.text("client"));
                    c.setPhone(r.text("clientPhone"));
                    c.setEmail(r.text("clientEmail"));
                    customerRepository.save(c);
                    created[0] = c;
                }
                MatterRequest req = new MatterRequest(r.text("title"), r.text("description"), c.getId(), r.text("practiceArea"), r.text("jurisdiction"), r.text("court"), r.text("caseNumber"), null,
                        billing, r.text("billingNote"), restricted, lead, opened);
                MatterDto d = lawService.create(a, req);
                number[0] = d.number();
                if (clearConflicts) {
                    LawMatter mm = matterRepository.findById(d.id()).orElseThrow();
                    mm.setConflictCleared(true);
                    mm.setConflictNote("Imported from a file: the firm was already acting on this matter.");
                    mm.setConflictClearedBy(LawAccess.name(a.user()));
                    mm.setConflictClearedAt(Instant.now());
                    matterRepository.save(mm);
                }
                if (dryRun) st.setRollbackOnly();
                return null;
            });
        }
        seen.add(dupMatter);
        if (caseKey != null) seen.add(caseKey);
        if (addClient) {
            if (dryRun) virtualClients.add(nameKey);
            else if (created[0] != null) {
                customers.add(created[0]);   // later rows of the same file find the new client
                seen.add("m:" + created[0].getId() + "|" + key(r.text("title")));
            }
            if (r.text("clientPhone") == null && r.text("clientEmail") == null) notes.add(new ImportRow(row, label, "New client '" + r.text("client") + "' has no phone or e-mail yet."));
        }
        String shown = (number[0] != null && !dryRun ? number[0] + " " : "") + label;
        return (addClient ? "+" : "") + shown;
    }

    // ------------------------------------------------------------------ templates

    private static final String[] CLIENT_HEADS = {"Name", "Phone", "Email", "Tax ID"};
    private static final String[][] CLIENT_EXAMPLES = {
            {"Jane Mukasa", "0772 123 456", "jane@example.com", ""},
            {"Kampala Traders Ltd", "0701 987 654", "legal@kampalatraders.example", "1000123456"}};
    private static final String[] MATTER_HEADS = {"Title", "Client", "Client phone", "Client email", "Practice area", "Jurisdiction", "Court", "Case number", "Billing", "Billing note", "Description", "Lead lawyer", "Opened", "Restricted"};
    private static final String[][] MATTER_EXAMPLES = {
            {"Mukasa v. Okello (land dispute)", "Jane Mukasa", "0772 123 456", "jane@example.com", "Litigation", "Uganda", "High Court, Kampala", "HCCS 123/2025", "Hourly", "", "Dispute over the boundary of Plot 12", "", "2025-03-14", "No"},
            {"Sale of Plot 45 Ntinda", "Kampala Traders Ltd", "", "", "Conveyancing", "Uganda", "", "", "Fixed fee", "Agreed in the engagement letter", "Purchase from the previous owner", "", "2026-01-20", "No"}};

    private static final String[][] CLIENT_HELP = {
            {"Name", "Required. A person or a company."}, {"Phone", "A phone number. Used to spot a client that is already in the system."}, {"Email", "Used to spot a client that is already in the system, and for invoices and reminders."},
            {"Tax ID", "The client's TIN or tax number, if you keep it."},
            {"Duplicates", "A client whose phone number or e-mail is already in the system is skipped. A client with neither is skipped when the same name is already there."},
            {"Where they show", "Clients are the same people as in Customers. They appear in the Law Firm Clients screen once they have a matter."}};
    private static final String[][] MATTER_HELP = {
            {"Title", "Required. The name of the matter."}, {"Client", "Required. The client's name as it is in the system. A phone number or e-mail in the next two columns finds the right client when two have the same name."},
            {"Client phone / Client email", "Optional. Used to find the client, and to add a new client if you tick \"Add clients that are not there yet\"."},
            {"Practice area / Jurisdiction / Court / Case number", "Free text."}, {"Billing", "Hourly, Fixed fee, Retainer, Contingency or Pro bono. Empty uses the default from the settings. Fee amounts, stages and rates are set on the matter afterwards."},
            {"Billing note", "Free text."}, {"Description", "Free text."}, {"Lead lawyer", "The lawyer's name or username as in Users. Empty means whoever imports. The lead lawyer becomes the matter's team."},
            {"Opened", "The date the firm opened the matter, like 2025-03-14 or 14/03/2025. Empty means today. It cannot be in the future."}, {"Restricted", "Yes or No. Empty uses the default from the settings."},
            {"Duplicates", "A matter with the same title for the same client, or the same court case number, is skipped."},
            {"Open matters only", "Every imported matter is open. Time, deadlines, documents and invoices are not imported."}};

    public byte[] templateCsv(String kind) {
        boolean clients = "clients".equals(kind);
        if (!clients && !"matters".equals(kind)) throw ApiException.notFound("No template for '" + kind + "'.");
        StringBuilder b = new StringBuilder("﻿");
        csvLine(b, clients ? CLIENT_HEADS : MATTER_HEADS);
        for (String[] row : clients ? CLIENT_EXAMPLES : MATTER_EXAMPLES) csvLine(b, row);
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

    public byte[] templateExcel(String kind) {
        boolean clients = "clients".equals(kind);
        if (!clients && !"matters".equals(kind)) throw ApiException.notFound("No template for '" + kind + "'.");
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.CellStyle bold = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            Sheet s = wb.createSheet(clients ? "Clients" : "Matters");
            String[] heads = clients ? CLIENT_HEADS : MATTER_HEADS;
            Row h = s.createRow(0);
            for (int i = 0; i < heads.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(heads[i]);
                c.setCellStyle(bold);
                s.setColumnWidth(i, Math.max(14, heads[i].length() + 4) * 256);
            }
            String[][] ex = clients ? CLIENT_EXAMPLES : MATTER_EXAMPLES;
            for (int r = 0; r < ex.length; r++) {
                Row row = s.createRow(r + 1);
                for (int i = 0; i < ex[r].length; i++) row.createCell(i).setCellValue(ex[r][i]);
            }
            Sheet help = wb.createSheet("How to fill it in");
            Row hr = help.createRow(0);
            hr.createCell(0).setCellValue("Column");
            hr.createCell(1).setCellValue("What to put in it");
            hr.getCell(0).setCellStyle(bold);
            hr.getCell(1).setCellStyle(bold);
            String[][] helpRows = clients ? CLIENT_HELP : MATTER_HELP;
            for (int r = 0; r < helpRows.length; r++) {
                Row row = help.createRow(r + 1);
                row.createCell(0).setCellValue(helpRows[r][0]);
                row.createCell(1).setCellValue(helpRows[r][1]);
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
