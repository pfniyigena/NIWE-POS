package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtDocumentDtos.DocumentDto;
import com.posapp.propertymgmt.dto.PropertyMgmtDocumentDtos.DocumentRequest;
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

/** Files attached to properties, units, owners, tenants, leases and repair requests: signed agreements, ID copies, title deeds, inspection reports, permits, insurance, photos, notices. */
@Service
public class PmDocumentService {

    static final long MAX_BYTES = 20L * 1024 * 1024;
    static final int MAX_PER_SUBJECT = 100;
    static final int SOON_DAYS = 30;
    private static final Map<String, String> BY_EXTENSION = Map.of("pdf", "application/pdf", "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "txt", "text/plain");
    private static final Set<String> TYPES = Set.copyOf(BY_EXTENSION.values());

    private final PmDocumentRepository repository;
    private final PmDocumentBlobRepository blobRepository;
    private final PmOwnerRepository ownerRepository;
    private final PmPropertyRepository propertyRepository;
    private final PmUnitRepository unitRepository;
    private final PmTenantRepository tenantRepository;
    private final PmLeaseRepository leaseRepository;
    private final PmMaintenanceRepository maintenanceRepository;
    private final PmInspectionRepository inspectionRepository;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    public PmDocumentService(PmDocumentRepository repository, PmDocumentBlobRepository blobRepository, PmOwnerRepository ownerRepository, PmPropertyRepository propertyRepository,
                             PmUnitRepository unitRepository, PmTenantRepository tenantRepository, PmLeaseRepository leaseRepository, PmMaintenanceRepository maintenanceRepository,
                             PmInspectionRepository inspectionRepository, PropertyManagementAccess access, AuditLogService auditLogService) {
        this.repository = repository;
        this.blobRepository = blobRepository;
        this.ownerRepository = ownerRepository;
        this.propertyRepository = propertyRepository;
        this.unitRepository = unitRepository;
        this.tenantRepository = tenantRepository;
        this.leaseRepository = leaseRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.inspectionRepository = inspectionRepository;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    // ------------------------------------------------------------------ labels

    /** What the subject is called, or null when it no longer exists. */
    private String label(PmDocSubject type, Long id) {
        return switch (type) {
            case PROPERTY -> propertyRepository.findById(id).map(p -> p.getCode() + " " + p.getName()).orElse(null);
            case UNIT -> unitRepository.findById(id).map(u -> "Unit " + u.getUnitNo() + propertyRepository.findById(u.getPropertyId()).map(p -> " of " + p.getName()).orElse("")).orElse(null);
            case OWNER -> ownerRepository.findById(id).map(o -> o.getCode() + " " + o.getName()).orElse(null);
            case TENANT -> tenantRepository.findById(id).map(t -> t.getCode() + " " + t.getName()).orElse(null);
            case LEASE -> leaseRepository.findById(id).map(l -> "Lease " + l.getCode()).orElse(null);
            case MAINTENANCE -> maintenanceRepository.findById(id).map(m -> "Repair " + m.getCode() + " " + m.getTitle()).orElse(null);
            case INSPECTION -> inspectionRepository.findById(id).map(i -> "Inspection " + i.getCode()).orElse(null);
        };
    }

    static String expiryState(LocalDate expiry, LocalDate today) {
        if (expiry == null) return "NONE";
        if (expiry.isBefore(today)) return "EXPIRED";
        return ChronoUnit.DAYS.between(today, expiry) <= SOON_DAYS ? "SOON" : "OK";
    }

    private DocumentDto dto(PmDocument d, String label, LocalDate today) {
        Long days = d.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, d.getExpiryDate());
        return new DocumentDto(d.getId(), d.getSubjectType(), d.getSubjectId(), label, d.getName(), d.getCategory(), d.getDescription(), d.getExpiryDate(), expiryState(d.getExpiryDate(), today),
                days, d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getUploadedByName(), d.getCreatedAt());
    }

    // ------------------------------------------------------------------ reading

    /** Documents, newest first; with {@code expiryOnly} only those that have an expiry date, soonest first. A document whose record is gone is not listed. */
    @Transactional(readOnly = true)
    public List<DocumentDto> list(PmDocSubject type, Long subjectId, PmDocCategory category, String q, Boolean expiryOnly) {
        access.on();
        LocalDate today = LocalDate.now();
        List<PmDocument> docs = type != null && subjectId != null ? repository.findBySubjectTypeAndSubjectIdOrderByIdDesc(type, subjectId) : repository.findAll();
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        Map<String, String> labels = new HashMap<>();
        List<DocumentDto> out = new ArrayList<>();
        for (PmDocument d : docs) {
            if (type != null && d.getSubjectType() != type) continue;
            if (category != null && d.getCategory() != category) continue;
            if (Boolean.TRUE.equals(expiryOnly) && d.getExpiryDate() == null) continue;
            String label = labels.computeIfAbsent(d.getSubjectType() + ":" + d.getSubjectId(), k -> label(d.getSubjectType(), d.getSubjectId()));
            if (label == null) continue;
            if (needle != null && !(d.getName() + " " + d.getFileName() + " " + label).toLowerCase(Locale.ROOT).contains(needle)) continue;
            out.add(dto(d, label, today));
        }
        if (Boolean.TRUE.equals(expiryOnly)) out.sort(Comparator.comparing(DocumentDto::expiryDate).thenComparing(DocumentDto::id));
        else out.sort(Comparator.comparing(DocumentDto::id, Comparator.reverseOrder()));
        return out;
    }

    /** Documents already past their expiry date. */
    @Transactional(readOnly = true)
    public long countExpired() {
        return repository.countByExpiryDateBefore(LocalDate.now());
    }

    /** Documents that expire within the next 30 days (today included). */
    @Transactional(readOnly = true)
    public long countExpiringSoon() {
        LocalDate today = LocalDate.now();
        return repository.countByExpiryDateBetween(today, today.plusDays(SOON_DAYS));
    }

    public record FileData(String fileName, String contentType, byte[] data) {
    }

    @Transactional(readOnly = true)
    public FileData file(Long id) {
        access.on();
        PmDocument d = find(id);
        PmDocumentBlob b = blobRepository.findByDocumentId(id).orElseThrow(() -> ApiException.notFound("The file is missing."));
        return new FileData(d.getFileName(), d.getContentType(), b.getData());
    }

    private PmDocument find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Document not found: " + id));
    }

    // ------------------------------------------------------------------ writing

    private static String detectType(MultipartFile file, String fileName) {
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (TYPES.contains(declared)) return declared;
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return BY_EXTENSION.get(ext);
    }

    private static boolean looksLikeImage(byte[] d, String type) {
        if (d.length < 12) return false;
        return switch (type) {
            case "image/jpeg" -> (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8;
            case "image/png" -> (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
            case "image/webp" -> d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
            default -> false;
        };
    }

    /** Checks the first bytes match the type, so a renamed file is not stored as something it is not. */
    static boolean looksRight(byte[] d, String type) {
        if (d.length == 0) return false;
        return switch (type) {
            case "application/pdf" -> d.length > 4 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F';
            case "image/jpeg", "image/png", "image/webp" -> looksLikeImage(d, type);
            case "text/plain" -> {
                for (int i = 0; i < Math.min(d.length, 4096); i++) if (d[i] == 0) yield false;
                yield true;
            }
            default -> d.length > 4 && d[0] == 'P' && d[1] == 'K' && d[2] == 3 && d[3] == 4;   // docx and xlsx are zip files
        };
    }

    @Transactional
    public DocumentDto upload(Actor a, MultipartFile file, PmDocSubject type, Long subjectId, String name, PmDocCategory category, String description, LocalDate expiryDate) throws IOException {
        access.on();
        if (type == null || subjectId == null) throw ApiException.badRequest("Say what the document belongs to.");
        String label = label(type, subjectId);
        if (label == null) throw ApiException.notFound("That " + type.name().toLowerCase(Locale.ROOT) + " does not exist.");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to add.");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("A document can be at most 20 MB.");
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "document" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n]", "_");
        if (fileName.length() > 200) fileName = fileName.substring(fileName.length() - 200);
        String ctype = detectType(file, fileName);
        if (ctype == null) throw ApiException.badRequest("Documents must be PDF, JPEG, PNG, WebP, Word (.docx), Excel (.xlsx) or text files.");
        byte[] data = file.getBytes();
        if (!looksRight(data, ctype)) throw ApiException.badRequest("That file does not look like a valid " + ctype.substring(ctype.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT).replace("VND.OPENXMLFORMATS-OFFICEDOCUMENT.", "") + " file.");
        if (repository.countBySubjectTypeAndSubjectId(type, subjectId) >= MAX_PER_SUBJECT) throw ApiException.badRequest("At most " + MAX_PER_SUBJECT + " documents can be attached to one record.");
        PmDocument d = new PmDocument();
        d.setSubjectType(type);
        d.setSubjectId(subjectId);
        String n = clean(name, 200, "The name");
        d.setName(n != null ? n : fileName);
        d.setCategory(category != null ? category : PmDocCategory.OTHER);
        d.setDescription(clean(description, 1000, "The description"));
        d.setExpiryDate(expiryDate);
        d.setFileName(fileName);
        d.setContentType(ctype);
        d.setSizeBytes(data.length);
        d.setUploadedByName(PropertyManagementAccess.name(a.user()));
        repository.save(d);
        PmDocumentBlob b = new PmDocumentBlob();
        b.setDocumentId(d.getId());
        b.setData(data);
        blobRepository.save(b);
        auditLogService.record("PM_DOCUMENT_ADDED", "Added \"" + d.getName() + "\" to " + label, "PmDocument", d.getId());
        return dto(d, label, LocalDate.now());
    }

    @Transactional
    public DocumentDto update(Long id, DocumentRequest r) {
        access.on();
        PmDocument d = find(id);
        if (r.name() != null) {
            String n = clean(r.name(), 200, "The name");
            if (n == null) throw ApiException.badRequest("Give the document a name.");
            d.setName(n);
        }
        if (r.category() != null) d.setCategory(r.category());
        if (r.description() != null) d.setDescription(clean(r.description(), 1000, "The description"));
        if (Boolean.TRUE.equals(r.clearExpiry())) d.setExpiryDate(null);
        else if (r.expiryDate() != null) d.setExpiryDate(r.expiryDate());
        repository.save(d);
        auditLogService.record("PM_DOCUMENT_UPDATED", "Updated document \"" + d.getName() + "\"", "PmDocument", id);
        return dto(d, label(d.getSubjectType(), d.getSubjectId()), LocalDate.now());
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        PmDocument d = find(id);
        blobRepository.deleteByDocumentId(id);
        repository.delete(d);
        auditLogService.record("PM_DOCUMENT_DELETED", "Deleted document \"" + d.getName() + "\"");
    }

    /** Removes every document of a record that is being deleted. */
    @Transactional
    public void deleteFor(PmDocSubject type, Long subjectId) {
        for (PmDocument d : repository.findBySubjectTypeAndSubjectIdOrderByIdDesc(type, subjectId)) {
            blobRepository.deleteByDocumentId(d.getId());
            repository.delete(d);
        }
    }
}
