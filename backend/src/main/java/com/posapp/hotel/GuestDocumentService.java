package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.GuestDtos.GuestDocumentDto;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Scans and photos of a guest's passport, ID, licence or visa, kept with the guest so the next stay needs no re-copying. */
@Service
public class GuestDocumentService {

    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int MAX_PER_GUEST = 20;
    static final int SOON_DAYS = 30;
    private static final Map<String, String> BY_EXTENSION = Map.of("pdf", "application/pdf", "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");
    private static final Set<String> TYPES = Set.copyOf(BY_EXTENSION.values());

    private final GuestDocumentRepository repository;
    private final GuestDocumentBlobRepository blobRepository;
    private final GuestRepository guestRepository;
    private final AuditLogService auditLogService;

    public GuestDocumentService(GuestDocumentRepository repository, GuestDocumentBlobRepository blobRepository, GuestRepository guestRepository, AuditLogService auditLogService) {
        this.repository = repository;
        this.blobRepository = blobRepository;
        this.guestRepository = guestRepository;
        this.auditLogService = auditLogService;
    }

    static String expiryState(LocalDate expiry, LocalDate today) {
        if (expiry == null) return "NONE";
        if (expiry.isBefore(today)) return "EXPIRED";
        return ChronoUnit.DAYS.between(today, expiry) <= SOON_DAYS ? "SOON" : "OK";
    }

    private GuestDocumentDto dto(GuestDocument d) {
        return new GuestDocumentDto(d.getId(), d.getGuestId(), d.getDocType().name(), d.getName(), d.getDocNumber(), d.getExpiryDate(), expiryState(d.getExpiryDate(), LocalDate.now()),
                d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getUploadedByName(), d.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<GuestDocumentDto> list(Long guestId) {
        if (!guestRepository.existsById(guestId)) throw ApiException.notFound("Guest not found: " + guestId);
        return repository.findByGuestIdOrderByIdDesc(guestId).stream().map(this::dto).toList();
    }

    public record FileData(String fileName, String contentType, byte[] data) {
    }

    @Transactional(readOnly = true)
    public FileData file(Long id) {
        GuestDocument d = find(id);
        GuestDocumentBlob b = blobRepository.findByDocumentId(id).orElseThrow(() -> ApiException.notFound("The file is missing."));
        return new FileData(d.getFileName(), d.getContentType(), b.getData());
    }

    private GuestDocument find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Document not found: " + id));
    }

    private static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    private static String detectType(MultipartFile file, String fileName) {
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (TYPES.contains(declared)) return declared;
        int dot = fileName.lastIndexOf('.');
        return BY_EXTENSION.get(dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** The first bytes must match the type, so a renamed file is not stored as something it is not. */
    static boolean looksRight(byte[] d, String type) {
        if (d.length < 12) return false;
        return switch (type) {
            case "application/pdf" -> d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F';
            case "image/jpeg" -> (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8;
            case "image/png" -> (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
            case "image/webp" -> d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
            default -> false;
        };
    }

    private static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    @Transactional
    public GuestDocumentDto upload(Long guestId, MultipartFile file, String docType, String name, String docNumber, LocalDate expiryDate) throws IOException {
        Guest guest = guestRepository.findById(guestId).orElseThrow(() -> ApiException.notFound("Guest not found: " + guestId));
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to add.");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("A document can be at most 10 MB.");
        GuestDocType type = GuestDocType.OTHER;
        if (docType != null && !docType.isBlank()) {
            try {
                type = GuestDocType.valueOf(docType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Unknown document type: " + docType);
            }
        }
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "document" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n]", "_");
        if (fileName.length() > 200) fileName = fileName.substring(fileName.length() - 200);
        String ctype = detectType(file, fileName);
        if (ctype == null) throw ApiException.badRequest("ID documents must be a PDF, JPEG, PNG or WebP file.");
        byte[] data = file.getBytes();
        if (!looksRight(data, ctype)) throw ApiException.badRequest("That file does not look like a valid " + ctype.substring(ctype.indexOf('/') + 1).toUpperCase(Locale.ROOT) + " file.");
        if (repository.countByGuestId(guestId) >= MAX_PER_GUEST) throw ApiException.badRequest("At most " + MAX_PER_GUEST + " documents can be kept for one guest.");
        GuestDocument d = new GuestDocument();
        d.setGuestId(guestId);
        d.setDocType(type);
        String n = clean(name, 200, "The name");
        d.setName(n != null ? n : type.name().charAt(0) + type.name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' '));
        d.setDocNumber(clean(docNumber, 120, "The document number"));
        d.setExpiryDate(expiryDate);
        d.setFileName(fileName);
        d.setContentType(ctype);
        d.setSizeBytes(data.length);
        d.setUploadedByName(currentUser());
        repository.save(d);
        GuestDocumentBlob b = new GuestDocumentBlob();
        b.setDocumentId(d.getId());
        b.setData(data);
        blobRepository.save(b);
        auditLogService.record("GUEST_DOCUMENT_ADDED", "Added \"" + d.getName() + "\" to guest " + guest.getFullName(), "GuestDocument", d.getId());
        return dto(d);
    }

    @Transactional
    public void delete(Long id) {
        GuestDocument d = find(id);
        Guest guest = guestRepository.findById(d.getGuestId()).orElse(null);
        blobRepository.deleteByDocumentId(id);
        repository.delete(d);
        auditLogService.record("GUEST_DOCUMENT_DELETED", "Deleted \"" + d.getName() + "\" of guest " + (guest == null ? "#" + d.getGuestId() : guest.getFullName()), "GuestDocument", id);
    }
}
