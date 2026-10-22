package com.posapp.hr;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import com.posapp.hr.dto.HrDocDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Files kept on an employee's record: contracts, ID copies, qualifications, permits. HR sees them all; the employee sees those shared with them under My HR. */
@Service
public class EmployeeDocumentService {

    static final long MAX_BYTES = 20L * 1024 * 1024;
    static final int MAX_PER_EMPLOYEE = 100;
    static final int SOON_DAYS = 30;
    private static final Map<String, String> BY_EXTENSION = Map.of("pdf", "application/pdf", "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "txt", "text/plain");
    private static final Set<String> TYPES = Set.copyOf(BY_EXTENSION.values());

    private final EmployeeDocumentRepository repository;
    private final EmployeeDocumentBlobRepository blobRepository;
    private final EmployeeRepository employeeRepository;
    private final BranchAccessService branchAccessService;
    private final AuditLogService auditLogService;

    public EmployeeDocumentService(EmployeeDocumentRepository repository, EmployeeDocumentBlobRepository blobRepository, EmployeeRepository employeeRepository,
                                   BranchAccessService branchAccessService, AuditLogService auditLogService) {
        this.repository = repository;
        this.blobRepository = blobRepository;
        this.employeeRepository = employeeRepository;
        this.branchAccessService = branchAccessService;
        this.auditLogService = auditLogService;
    }

    private static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    private Employee employee(Long id) {
        Employee e = employeeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Employee not found"));
        branchAccessService.assertCanAccess(e.getBranchId());
        return e;
    }

    static String expiryState(LocalDate expiry, LocalDate today) {
        if (expiry == null) return "NONE";
        if (expiry.isBefore(today)) return "EXPIRED";
        return ChronoUnit.DAYS.between(today, expiry) <= SOON_DAYS ? "SOON" : "OK";
    }

    private static EmployeeDocumentDto dto(EmployeeDocument d, Employee e, LocalDate today) {
        Long days = d.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, d.getExpiryDate());
        return new EmployeeDocumentDto(d.getId(), d.getEmployeeId(), e == null ? null : e.getEmployeeNumber(), e == null ? null : e.fullName(), d.getName(), d.getCategory(),
                d.getDescription(), d.getExpiryDate(), expiryState(d.getExpiryDate(), today), days, d.isSharedWithEmployee(), d.getFileName(), d.getContentType(), d.getSizeBytes(),
                d.getUploadedByName(), d.getCreatedAt());
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public List<EmployeeDocumentDto> forEmployee(Long employeeId) {
        Employee e = employee(employeeId);
        LocalDate today = LocalDate.now();
        return repository.findByEmployeeIdOrderByIdDesc(employeeId).stream().map(d -> dto(d, e, today)).toList();
    }

    /** Every document the user may see, newest first; {@code state} narrows to EXPIRED, SOON (expired or within 30 days = needs attention) or WITH_EXPIRY. */
    @Transactional(readOnly = true)
    public List<EmployeeDocumentDto> search(String q, EmployeeDocCategory category, String state, Long employeeId) {
        LocalDate today = LocalDate.now();
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        Map<Long, Employee> emps = new HashMap<>();
        employeeRepository.findAll().forEach(e -> emps.put(e.getId(), e));
        List<EmployeeDocumentDto> out = new ArrayList<>();
        for (EmployeeDocument d : employeeId != null ? repository.findByEmployeeIdOrderByIdDesc(employeeId) : repository.findAll()) {
            Employee e = emps.get(d.getEmployeeId());
            if (e == null || !branchAccessService.canAccess(e.getBranchId())) continue;
            if (category != null && d.getCategory() != category) continue;
            String st = expiryState(d.getExpiryDate(), today);
            if (state != null && !state.isBlank()) {
                switch (state.toUpperCase(Locale.ROOT)) {
                    case "EXPIRED" -> { if (!st.equals("EXPIRED")) continue; }
                    case "SOON" -> { if (!st.equals("SOON") && !st.equals("EXPIRED")) continue; }
                    case "WITH_EXPIRY" -> { if (d.getExpiryDate() == null) continue; }
                    default -> throw ApiException.badRequest("Unknown filter: " + state);
                }
            }
            if (needle != null && !(d.getName() + " " + d.getFileName() + " " + e.fullName() + " " + e.getEmployeeNumber()).toLowerCase(Locale.ROOT).contains(needle)) continue;
            out.add(dto(d, e, today));
        }
        if (state != null && !state.isBlank()) {
            out.sort(Comparator.comparing(EmployeeDocumentDto::expiryDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(EmployeeDocumentDto::id));
        } else {
            out.sort(Comparator.comparing(EmployeeDocumentDto::id, Comparator.reverseOrder()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public DocumentSummaryDto summary() {
        LocalDate today = LocalDate.now();
        long total = 0, expired = 0, soon = 0;
        Set<Long> allowed = new java.util.HashSet<>();
        employeeRepository.findAll().forEach(e -> { if (branchAccessService.canAccess(e.getBranchId())) allowed.add(e.getId()); });
        for (EmployeeDocument d : repository.findAll()) {
            if (!allowed.contains(d.getEmployeeId())) continue;
            total++;
            String st = expiryState(d.getExpiryDate(), today);
            if (st.equals("EXPIRED")) expired++;
            else if (st.equals("SOON")) soon++;
        }
        return new DocumentSummaryDto(total, expired, soon);
    }

    public record FileData(String fileName, String contentType, byte[] data) {
    }

    @Transactional(readOnly = true)
    public FileData file(Long id) {
        EmployeeDocument d = find(id);
        employee(d.getEmployeeId());
        return data(d);
    }

    private FileData data(EmployeeDocument d) {
        EmployeeDocumentBlob b = blobRepository.findByDocumentId(d.getId()).orElseThrow(() -> ApiException.notFound("The file is missing."));
        return new FileData(d.getFileName(), d.getContentType(), b.getData());
    }

    private EmployeeDocument find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Document not found"));
    }

    // ---- the employee's own view ----

    @Transactional(readOnly = true)
    public List<EmployeeDocumentDto> sharedWith(Employee me) {
        LocalDate today = LocalDate.now();
        return repository.findByEmployeeIdAndSharedWithEmployeeTrueOrderByIdDesc(me.getId()).stream().map(d -> dto(d, me, today)).toList();
    }

    @Transactional(readOnly = true)
    public FileData sharedFile(Employee me, Long id) {
        EmployeeDocument d = find(id);
        if (!d.getEmployeeId().equals(me.getId()) || !d.isSharedWithEmployee()) {
            throw ApiException.notFound("Document not found");
        }
        return data(d);
    }

    // ---- writing ----

    private static String detectType(MultipartFile file, String fileName) {
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (TYPES.contains(declared)) return declared;
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return BY_EXTENSION.get(ext);
    }

    /** Checks the first bytes match the type, so a renamed file is not stored as something it is not. */
    static boolean looksRight(byte[] d, String type) {
        if (d.length == 0) return false;
        return switch (type) {
            case "application/pdf" -> d.length > 4 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F';
            case "image/jpeg" -> d.length > 12 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8;
            case "image/png" -> d.length > 12 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
            case "image/webp" -> d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
            case "text/plain" -> {
                for (int i = 0; i < Math.min(d.length, 4096); i++) if (d[i] == 0) yield false;
                yield true;
            }
            default -> d.length > 4 && d[0] == 'P' && d[1] == 'K' && d[2] == 3 && d[3] == 4;   // docx and xlsx are zip files
        };
    }

    @Transactional
    public EmployeeDocumentDto upload(Long employeeId, MultipartFile file, String name, EmployeeDocCategory category, String description, LocalDate expiryDate, boolean shared) throws IOException {
        Employee e = employee(employeeId);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to add.");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("A document can be at most 20 MB.");
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "document" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n]", "_");
        if (fileName.length() > 200) fileName = fileName.substring(fileName.length() - 200);
        String ctype = detectType(file, fileName);
        if (ctype == null) throw ApiException.badRequest("Documents must be PDF, JPEG, PNG, WebP, Word (.docx), Excel (.xlsx) or text files.");
        byte[] data = file.getBytes();
        if (!looksRight(data, ctype)) throw ApiException.badRequest("That file does not look like a valid " + ctype.substring(ctype.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT).replace("VND.OPENXMLFORMATS-OFFICEDOCUMENT.", "") + " file.");
        if (repository.countByEmployeeId(employeeId) >= MAX_PER_EMPLOYEE) throw ApiException.badRequest("At most " + MAX_PER_EMPLOYEE + " documents can be kept for one employee.");
        EmployeeDocument d = new EmployeeDocument();
        d.setEmployeeId(employeeId);
        String n = clean(name, 200, "The name");
        d.setName(n != null ? n : fileName);
        d.setCategory(category != null ? category : EmployeeDocCategory.OTHER);
        d.setDescription(clean(description, 1000, "The description"));
        d.setExpiryDate(expiryDate);
        d.setSharedWithEmployee(shared);
        d.setFileName(fileName);
        d.setContentType(ctype);
        d.setSizeBytes(data.length);
        d.setUploadedByName(HrSupport.currentUserName());
        repository.save(d);
        EmployeeDocumentBlob b = new EmployeeDocumentBlob();
        b.setDocumentId(d.getId());
        b.setData(data);
        blobRepository.save(b);
        auditLogService.record("EMPLOYEE_DOCUMENT_ADDED", "Added \"" + d.getName() + "\" to " + e.fullName(), "EmployeeDocument", d.getId());
        return dto(d, e, LocalDate.now());
    }

    @Transactional
    public EmployeeDocumentDto update(Long id, EmployeeDocumentRequest r) {
        EmployeeDocument d = find(id);
        Employee e = employee(d.getEmployeeId());
        if (r.name() != null) {
            String n = clean(r.name(), 200, "The name");
            if (n == null) throw ApiException.badRequest("Give the document a name.");
            d.setName(n);
        }
        if (r.category() != null) d.setCategory(r.category());
        if (r.description() != null) d.setDescription(clean(r.description(), 1000, "The description"));
        if (Boolean.TRUE.equals(r.clearExpiry())) d.setExpiryDate(null);
        else if (r.expiryDate() != null) d.setExpiryDate(r.expiryDate());
        if (r.sharedWithEmployee() != null) d.setSharedWithEmployee(r.sharedWithEmployee());
        repository.save(d);
        auditLogService.record("EMPLOYEE_DOCUMENT_UPDATED", "Updated document \"" + d.getName() + "\" of " + e.fullName(), "EmployeeDocument", id);
        return dto(d, e, LocalDate.now());
    }

    @Transactional
    public void delete(Long id) {
        EmployeeDocument d = find(id);
        Employee e = employee(d.getEmployeeId());
        blobRepository.deleteByDocumentId(id);
        repository.delete(d);
        auditLogService.record("EMPLOYEE_DOCUMENT_DELETED", "Deleted document \"" + d.getName() + "\" of " + e.fullName(), "EmployeeDocument", id);
    }
}
