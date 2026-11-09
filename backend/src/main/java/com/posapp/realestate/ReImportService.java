package com.posapp.realestate;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.dto.RealEstateDtos.PropertyDto;
import com.posapp.realestate.dto.RealEstateImportDtos.ImportResult;
import com.posapp.realestate.dto.RealEstateImportDtos.ImportRow;
import com.posapp.realestate.dto.RealEstateRequests.LeadRequest;
import com.posapp.realestate.dto.RealEstateRequests.PropertyRequest;
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
import java.util.stream.Collectors;

/**
 * Bringing leads and properties in from an Excel or CSV file. The file is read the same way for a check and for the real import: a check (dry run) tries every row in a
 * transaction that is rolled back, so it reports exactly what the import would do and saves nothing. A row that is wrong, or that is already in the system, is skipped and
 * reported with its spreadsheet row number; the others go in.
 */
@Service
public class ReImportService {

    static final int MAX_ROWS = 2000;
    private static final int MAX_PROBLEMS = 500;

    private final RealEstateAccess access;
    private final ReLeadService leadService;
    private final RePropertyService propertyService;
    private final ReLeadRepository leadRepository;
    private final RePropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final TransactionTemplate tx;

    public ReImportService(RealEstateAccess access, ReLeadService leadService, RePropertyService propertyService, ReLeadRepository leadRepository, RePropertyRepository propertyRepository,
                           UserRepository userRepository, AuditLogService auditLogService, PlatformTransactionManager txManager) {
        this.access = access;
        this.leadService = leadService;
        this.propertyService = propertyService;
        this.leadRepository = leadRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ the columns

    private static final Map<String, String[]> LEAD_COLUMNS = new LinkedHashMap<>();
    private static final Map<String, String[]> PROPERTY_COLUMNS = new LinkedHashMap<>();

    static {
        LEAD_COLUMNS.put("name", new String[]{"name", "fullname", "leadname", "client", "clientname", "contactname", "customer", "customername"});
        LEAD_COLUMNS.put("phone", new String[]{"phone", "phonenumber", "mobile", "mobilenumber", "tel", "telephone", "contact", "cell", "whatsapp"});
        LEAD_COLUMNS.put("email", new String[]{"email", "emailaddress", "mail"});
        LEAD_COLUMNS.put("interest", new String[]{"interest", "lookingto", "wants", "intent", "lookingfor"});
        LEAD_COLUMNS.put("source", new String[]{"source", "leadsource", "channel", "howtheyfoundus"});
        LEAD_COLUMNS.put("budgetMin", new String[]{"budgetmin", "minbudget", "budgetfrom", "minimumbudget", "budgetlow"});
        LEAD_COLUMNS.put("budgetMax", new String[]{"budgetmax", "maxbudget", "budgetto", "budget", "maximumbudget", "budgethigh"});
        LEAD_COLUMNS.put("preferredArea", new String[]{"preferredarea", "area", "location", "preferredlocation", "wantedarea"});
        LEAD_COLUMNS.put("preferredType", new String[]{"preferredtype", "propertytype", "type", "wantedtype"});
        LEAD_COLUMNS.put("minBedrooms", new String[]{"minbedrooms", "bedrooms", "beds", "minbeds", "minimumbedrooms"});
        LEAD_COLUMNS.put("requirements", new String[]{"requirements", "notes", "comments", "remarks", "note"});
        LEAD_COLUMNS.put("agent", new String[]{"agent", "assignedto", "agentname", "assignedagent", "owner"});
        LEAD_COLUMNS.put("nextFollowUp", new String[]{"nextfollowup", "followup", "followupdate", "nextcall", "callon", "nextcalldate"});
        LEAD_COLUMNS.put("property", new String[]{"property", "propertycode", "interestedin"});

        PROPERTY_COLUMNS.put("title", new String[]{"title", "name", "propertytitle", "listing", "propertyname"});
        PROPERTY_COLUMNS.put("type", new String[]{"type", "propertytype", "kind", "category"});
        PROPERTY_COLUMNS.put("purpose", new String[]{"purpose", "listingtype", "for", "offer", "listedfor"});
        PROPERTY_COLUMNS.put("description", new String[]{"description", "details", "about"});
        PROPERTY_COLUMNS.put("area", new String[]{"area", "location", "neighbourhood", "neighborhood", "district", "suburb"});
        PROPERTY_COLUMNS.put("address", new String[]{"address", "street", "streetaddress"});
        PROPERTY_COLUMNS.put("titleReference", new String[]{"titlereference", "titledeed", "titlenumber", "landtitle", "titleref", "titledeednumber", "plotnumber"});
        PROPERTY_COLUMNS.put("bedrooms", new String[]{"bedrooms", "beds", "bedroom"});
        PROPERTY_COLUMNS.put("bathrooms", new String[]{"bathrooms", "baths", "bathroom"});
        PROPERTY_COLUMNS.put("parking", new String[]{"parking", "parkingspaces", "garages", "parkingspots"});
        PROPERTY_COLUMNS.put("sizeSqm", new String[]{"sizesqm", "size", "sqm", "floorarea", "builtarea", "areasqm", "buildingsize", "housesize"});
        PROPERTY_COLUMNS.put("plotSqm", new String[]{"plotsqm", "plot", "plotsize", "landsize", "plotarea"});
        PROPERTY_COLUMNS.put("yearBuilt", new String[]{"yearbuilt", "year", "built", "builtin"});
        PROPERTY_COLUMNS.put("furnished", new String[]{"furnished"});
        PROPERTY_COLUMNS.put("features", new String[]{"features", "amenities", "extras"});
        PROPERTY_COLUMNS.put("salePrice", new String[]{"saleprice", "price", "askingprice", "sellingprice", "priceforsale"});
        PROPERTY_COLUMNS.put("rentPrice", new String[]{"rentprice", "rent", "monthlyrent", "rental", "rentpermonth"});
        PROPERTY_COLUMNS.put("negotiable", new String[]{"negotiable"});
        PROPERTY_COLUMNS.put("ownerName", new String[]{"ownername", "owner", "landlord", "seller", "ownersname"});
        PROPERTY_COLUMNS.put("ownerPhone", new String[]{"ownerphone", "ownertel", "ownermobile", "landlordphone", "ownerphonenumber"});
        PROPERTY_COLUMNS.put("ownerEmail", new String[]{"owneremail", "landlordemail"});
        PROPERTY_COLUMNS.put("agent", new String[]{"agent", "assignedto", "agentname", "assignedagent"});
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

    /** The file as a list of rows keyed by the heading texts. Excel (.xlsx) and CSV (comma, semicolon or tab) are understood. */
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
        // The heading is the first row that has anything in it.
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

    /** A row with the columns the importer knows, by their canonical names. */
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
        String t = s.replace(",", "").replace(" ", "").replace(" ", "");
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new RowError(label + " '" + s + "' is not a number.");
        }
    }

    private static Integer whole(Rec r, String k, String label) {
        BigDecimal d = decimal(r, k, label);
        if (d == null) return null;
        if (d.stripTrailingZeros().scale() > 0) throw new RowError(label + " '" + r.text(k) + "' must be a whole number.");
        try {
            return d.intValueExact();
        } catch (ArithmeticException e) {
            throw new RowError(label + " '" + r.text(k) + "' is too big.");
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

    private static <E extends Enum<E>> E choice(Rec r, String k, String label, Class<E> type, Map<String, E> aliases) {
        String s = r.text(k);
        if (s == null) return null;
        String kk = key(s);
        E hit = aliases.get(kk);
        if (hit == null) {
            for (E e : type.getEnumConstants()) if (key(e.name()).equals(kk)) return e;
            String all = java.util.Arrays.stream(type.getEnumConstants()).map(e -> nice(e.name())).collect(Collectors.joining(", "));
            throw new RowError(label + " '" + s + "' is not one of: " + all + ".");
        }
        return hit;
    }

    private static String nice(String n) {
        String s = n.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static <E> Map<String, E> map(Object... kv) {
        Map<String, E> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked") E e = (E) kv[i + 1];
            for (String a : ((String) kv[i]).split(",")) m.put(key(a), e);
        }
        return m;
    }

    private static final Map<String, PropertyType> TYPES = map("house,home,bungalow,townhouse", PropertyType.HOUSE, "apartment,flat,condo,unit", PropertyType.APARTMENT, "villa", PropertyType.VILLA,
            "land,plot,lot,farm", PropertyType.LAND, "commercial,shop,retail,building", PropertyType.COMMERCIAL, "office", PropertyType.OFFICE, "warehouse,store,godown", PropertyType.WAREHOUSE, "other", PropertyType.OTHER);
    private static final Map<String, ListingPurpose> PURPOSES = map("sale,sell,forsale,buy,selling", ListingPurpose.SALE, "rent,let,tolet,forrent,lease,renting", ListingPurpose.RENT, "both,saleandrent,saleorrent", ListingPurpose.BOTH);
    private static final Map<String, LeadInterest> INTERESTS = map("buy,purchase,buying,tobuy", LeadInterest.BUY, "rent,lease,let,renting,torent", LeadInterest.RENT, "either,both,any", LeadInterest.EITHER);
    private static final Map<String, LeadSource> SOURCES = map("walkin,walk in,walk-in", LeadSource.WALK_IN, "phone,call,phonecall,telephone", LeadSource.PHONE, "website,web,online,site", LeadSource.WEBSITE,
            "referral,referred,friend", LeadSource.REFERRAL, "social,facebook,instagram,whatsapp,twitter,tiktok,socialmedia", LeadSource.SOCIAL, "signboard,sign,board", LeadSource.SIGNBOARD, "other", LeadSource.OTHER);

    // ------------------------------------------------------------------ the import itself

    private record Prepared(Rec rec, int row) {
    }

    /** Maps the file's headings to canonical column names; returns the table rows as {@link Rec}s plus the headings that were recognised and ignored. */
    private record Mapped(List<Prepared> rows, List<String> columns, List<String> ignored) {
    }

    private Mapped mapColumns(Table t, Map<String, String[]> cols, String requiredKey, String requiredLabel) {
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

    private Long agentId(String name) {
        if (name == null) return null;
        for (AppUser u : userRepository.findByActiveTrue()) {
            if (RealEstateAccess.name(u).equalsIgnoreCase(name.trim()) || u.getUsername().equalsIgnoreCase(name.trim())) return u.getId();
        }
        throw new RowError("Agent '" + name + "' is not a user. Use the agent's name or username as they are in Users.");
    }

    /** Checks (dryRun) or imports a leads or properties file. */
    public ImportResult run(Actor a, String kind, MultipartFile file, boolean dryRun, boolean listThem) {
        access.on();
        boolean leads = "leads".equals(kind);
        if (!leads && !"properties".equals(kind)) throw ApiException.notFound("Nothing to import for '" + kind + "'.");
        Table t = read(file);
        Mapped m = leads ? mapColumns(t, LEAD_COLUMNS, "name", "Name") : mapColumns(t, PROPERTY_COLUMNS, "title", "Title");
        List<ImportRow> problems = new ArrayList<>();
        List<ImportRow> notes = new ArrayList<>();
        List<String> sample = new ArrayList<>();
        int ok = 0;
        int skipped = 0;

        Set<String> seen = new HashSet<>();
        if (leads) {
            for (ReLead l : leadRepository.findAll()) {
                if (!ReLeadService.open(l)) continue;
                if (l.getPhoneKey() != null) seen.add("p:" + l.getPhoneKey());
                if (l.getEmail() != null) seen.add("e:" + l.getEmail().toLowerCase(Locale.ROOT));
            }
        } else {
            for (ReProperty p : propertyRepository.findAll()) seen.addAll(propertyKeys(p.getTitle(), p.getAddress(), p.getArea(), p.getTitleReference()));
        }
        Map<String, Long> propertyByCode = leads ? propertyRepository.findAll().stream().collect(Collectors.toMap(p -> p.getCode().toLowerCase(Locale.ROOT), ReProperty::getId, (x, y) -> x)) : Map.of();

        for (Prepared p : m.rows()) {
            Rec r = p.rec();
            String label = leads ? (r.text("name") == null ? "(no name)" : r.text("name")) : (r.text("title") == null ? "(no title)" : r.text("title"));
            try {
                List<String> keys = leads ? leadKeys(r) : propertyKeys(r.text("title"), r.text("address"), r.text("area"), r.text("titleReference"));
                for (String k : keys) {
                    if (seen.contains(k)) throw new RowError(leads ? (k.startsWith("p:") ? "A lead with this phone number is already in the system or earlier in this file." : "A lead with this e-mail is already in the system or earlier in this file.")
                            : "This property is already in the system or earlier in this file.");
                }
                String done = leads ? importLead(a, r, propertyByCode, dryRun) : importProperty(a, r, dryRun, listThem, notes, p.row(), label);
                seen.addAll(keys);
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
        String noun = leads ? "lead" : "propert" + (ok == 1 ? "y" : "ies");
        if (leads && ok != 1) noun = "leads";
        String msg = dryRun
                ? (ok + " of " + m.rows().size() + " rows can be imported" + (skipped > 0 ? "; " + skipped + " will be skipped." : "."))
                : ("Imported " + ok + " " + noun + (skipped > 0 ? "; " + skipped + " skipped." : "."));
        if (!dryRun && ok > 0) auditLogService.record("RE_IMPORT", "Imported " + ok + " " + kind + " from " + file.getOriginalFilename(), kind.equals("leads") ? "ReLead" : "ReProperty", null);
        return new ImportResult(kind, dryRun, file.getOriginalFilename(), m.rows().size(), ok, skipped, problems, notes, sample, m.columns(), m.ignored(), msg);
    }

    private static List<String> leadKeys(Rec r) {
        List<String> k = new ArrayList<>();
        String phone = r.text("phone");
        if (phone != null) {
            String d = phone.replaceAll("\\D", "");
            if (!d.isEmpty()) k.add("p:" + d);
        }
        if (r.text("email") != null) k.add("e:" + r.text("email").toLowerCase(Locale.ROOT));
        return k;
    }

    private static List<String> propertyKeys(String title, String address, String area, String ref) {
        List<String> k = new ArrayList<>();
        if (title != null) k.add("t:" + key(title) + "|" + key(address) + "|" + (address == null ? key(area) : ""));
        if (ref != null && !ref.isBlank()) k.add("r:" + key(ref));
        return k;
    }

    private String importLead(Actor a, Rec r, Map<String, Long> propertyByCode, boolean dryRun) {
        Long propertyId = null;
        if (r.text("property") != null) {
            propertyId = propertyByCode.get(r.text("property").toLowerCase(Locale.ROOT));
            if (propertyId == null) throw new RowError("Property '" + r.text("property") + "' is not in the system. Use its code, like PR-0001, or leave it empty.");
        }
        LeadRequest req = new LeadRequest(r.text("name"), r.text("phone"), r.text("email"), choice(r, "interest", "Interest", LeadInterest.class, INTERESTS), choice(r, "source", "Source", LeadSource.class, SOURCES),
                decimal(r, "budgetMin", "Budget (min)"), decimal(r, "budgetMax", "Budget (max)"), r.text("preferredArea"), choice(r, "preferredType", "Preferred type", PropertyType.class, TYPES),
                whole(r, "minBedrooms", "Bedrooms"), r.text("requirements"), propertyId, agentId(r.text("agent")), date(r, "nextFollowUp", "Next follow-up"), Boolean.FALSE);
        tx.execute(st -> {
            leadService.create(a, req);
            if (dryRun) st.setRollbackOnly();
            return null;
        });
        return null;
    }

    private String importProperty(Actor a, Rec r, boolean dryRun, boolean listThem, List<ImportRow> notes, int row, String label) {
        BigDecimal sale = decimal(r, "salePrice", "Sale price");
        BigDecimal rent = decimal(r, "rentPrice", "Rent");
        ListingPurpose purpose = choice(r, "purpose", "Purpose", ListingPurpose.class, PURPOSES);
        if (purpose == null) purpose = sale != null && rent != null ? ListingPurpose.BOTH : rent != null ? ListingPurpose.RENT : ListingPurpose.SALE;
        PropertyRequest req = new PropertyRequest(r.text("title"), choice(r, "type", "Type", PropertyType.class, TYPES), purpose, r.text("description"), r.text("area"), r.text("address"), r.text("titleReference"),
                null, null, whole(r, "bedrooms", "Bedrooms"), whole(r, "bathrooms", "Bathrooms"), whole(r, "parking", "Parking"), decimal(r, "sizeSqm", "Size"), decimal(r, "plotSqm", "Plot size"),
                whole(r, "yearBuilt", "Year built"), yesNo(r, "furnished", "Furnished"), r.text("features"), sale, rent, yesNo(r, "negotiable", "Negotiable"), null, null,
                r.text("ownerName"), r.text("ownerPhone"), r.text("ownerEmail"), agentId(r.text("agent")));
        final ListingPurpose finalPurpose = purpose;
        boolean needSale = finalPurpose == ListingPurpose.SALE || finalPurpose == ListingPurpose.BOTH;
        boolean needRent = finalPurpose == ListingPurpose.RENT || finalPurpose == ListingPurpose.BOTH;
        boolean listable = !(needSale && sale == null) && !(needRent && rent == null);
        String[] code = new String[1];
        tx.execute(st -> {
            PropertyDto d = propertyService.create(a, req);
            code[0] = d.code();
            if (listThem && !listable) notes.add(new ImportRow(row, label, "Imported as a draft, not listed: " + (needSale && sale == null ? "it has no sale price." : "it has no rent.")));
            else if (listThem) propertyService.setStatus(a, d.id(), PropertyStatus.AVAILABLE);
            if (dryRun) st.setRollbackOnly();
            return null;
        });
        return dryRun ? label : code[0] + " " + label;
    }

    // ------------------------------------------------------------------ templates

    private static final String[] LEAD_HEADS = {"Name", "Phone", "Email", "Interest", "Source", "Budget min", "Budget max", "Preferred area", "Preferred type", "Min bedrooms", "Requirements", "Agent", "Next follow-up", "Property"};
    private static final String[][] LEAD_EXAMPLES = {
            {"Jane Mukasa", "0772 123 456", "jane@example.com", "Buy", "Walk in", "300000000", "450000000", "Kololo", "House", "3", "Garden, near a good school", "", "2026-11-05", ""},
            {"Peter Okello", "0701 987 654", "", "Rent", "Phone", "1500000", "2500000", "Ntinda", "Apartment", "2", "", "", "", ""}};
    private static final String[] PROPERTY_HEADS = {"Title", "Type", "Purpose", "Area", "Address", "Title reference", "Description", "Bedrooms", "Bathrooms", "Parking", "Size sqm", "Plot sqm", "Year built", "Furnished", "Features",
            "Sale price", "Rent", "Negotiable", "Owner name", "Owner phone", "Owner email", "Agent"};
    private static final String[][] PROPERTY_EXAMPLES = {
            {"3-bedroom house in Kololo", "House", "Sale", "Kololo", "Plot 12, Kololo Hill Road", "LRV 1234 Folio 5", "Quiet street, mature garden", "3", "2", "2", "220", "800", "2015", "No", "Borehole, Solar, Security wall",
                    "450000000", "", "Yes", "Mr Owner", "0772 000 111", "", ""},
            {"2-bedroom apartment in Ntinda", "Apartment", "Rent", "Ntinda", "", "", "Third floor, lift", "2", "2", "1", "95", "", "2020", "Yes", "Lift, Parking", "", "2500000", "No", "Ms Landlord", "0701 222 333", "", ""}};

    private static final String[][] LEAD_HELP = {
            {"Name", "Required. The person's name."}, {"Phone", "A phone number or an e-mail is needed so the lead can be reached."}, {"Email", "A phone number or an e-mail is needed."},
            {"Interest", "Buy, Rent or Either. Empty means Buy."}, {"Source", "Walk in, Phone, Website, Referral, Social, Signboard or Other. Empty means Other."}, {"Budget min", "A number, no currency sign."},
            {"Budget max", "A number, no currency sign."}, {"Preferred area", "Free text."}, {"Preferred type", "House, Apartment, Villa, Land, Commercial, Office, Warehouse or Other."},
            {"Min bedrooms", "A whole number."}, {"Requirements", "Free text."}, {"Agent", "The agent's name or username as in Users. Empty means whoever imports."}, {"Next follow-up", "A date, like 2026-12-31 or 31/12/2026."},
            {"Property", "A property code like PR-0001 (the property must already exist)."}, {"Duplicates", "A lead whose phone number or e-mail is already on an open lead is skipped."}};
    private static final String[][] PROPERTY_HELP = {
            {"Title", "Required. The name of the listing."}, {"Type", "House, Apartment (or Flat), Villa, Land (or Plot), Commercial, Office, Warehouse or Other."}, {"Purpose", "Sale, Rent or Both. Empty: worked out from the prices."},
            {"Area", "The neighbourhood."}, {"Address", "Free text."}, {"Title reference", "The land title number. A property with the same reference is skipped as a duplicate."},
            {"Bedrooms / Bathrooms / Parking", "Whole numbers."}, {"Size sqm / Plot sqm", "Numbers."}, {"Year built", "A year."}, {"Furnished / Negotiable", "Yes or No."},
            {"Features", "Free text, separated by commas."}, {"Sale price / Rent", "Numbers, no currency sign. Rent is per month."}, {"Owner name / phone / email", "Kept private; never on flyers or the website."},
            {"Agent", "The agent's name or username as in Users. Empty means whoever imports."}, {"Status", "Imported properties are drafts unless you tick \"List them as available\" when importing (a property with no price stays a draft)."},
            {"Duplicates", "A property with the same title and address as one that exists is skipped."}};

    public byte[] templateCsv(String kind) {
        boolean leads = "leads".equals(kind);
        if (!leads && !"properties".equals(kind)) throw ApiException.notFound("No template for '" + kind + "'.");
        StringBuilder b = new StringBuilder("﻿");
        csvLine(b, leads ? LEAD_HEADS : PROPERTY_HEADS);
        for (String[] row : leads ? LEAD_EXAMPLES : PROPERTY_EXAMPLES) csvLine(b, row);
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
        boolean leads = "leads".equals(kind);
        if (!leads && !"properties".equals(kind)) throw ApiException.notFound("No template for '" + kind + "'.");
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.CellStyle bold = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            Sheet s = wb.createSheet(leads ? "Leads" : "Properties");
            String[] heads = leads ? LEAD_HEADS : PROPERTY_HEADS;
            Row h = s.createRow(0);
            for (int i = 0; i < heads.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(heads[i]);
                c.setCellStyle(bold);
                s.setColumnWidth(i, Math.max(14, heads[i].length() + 4) * 256);
            }
            String[][] ex = leads ? LEAD_EXAMPLES : PROPERTY_EXAMPLES;
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
            String[][] helpRows = leads ? LEAD_HELP : PROPERTY_HELP;
            for (int r = 0; r < helpRows.length; r++) {
                Row row = help.createRow(r + 1);
                row.createCell(0).setCellValue(helpRows[r][0]);
                row.createCell(1).setCellValue(helpRows[r][1]);
            }
            help.setColumnWidth(0, 30 * 256);
            help.setColumnWidth(1, 100 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the template", e);
        }
    }
}
