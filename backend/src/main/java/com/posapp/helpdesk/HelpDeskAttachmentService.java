package com.posapp.helpdesk;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.helpdesk.dto.HelpDeskAttachmentDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Screenshots and files on tickets: photos, PDF and text files up to 10 MB, 20 per ticket. */
@Service
public class HelpDeskAttachmentService {

    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final int MAX_FILES = 20;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf", "text/plain");

    private final HelpDeskAttachmentRepository repository;
    private final HelpDeskTicketRepository ticketRepository;
    private final HelpDeskEvents events;
    private final AuditLogService auditLogService;

    public HelpDeskAttachmentService(HelpDeskAttachmentRepository repository, HelpDeskTicketRepository ticketRepository, HelpDeskEvents events,
                                     AuditLogService auditLogService) {
        this.repository = repository;
        this.ticketRepository = ticketRepository;
        this.events = events;
        this.auditLogService = auditLogService;
    }

    public record FileData(String fileName, String contentType, byte[] data) {
    }

    @Transactional(readOnly = true)
    public List<HelpDeskAttachmentDto> list(Long ticketId) {
        require(ticketId);
        return repository.findByTicketIdOrderByIdAsc(ticketId).stream().map(HelpDeskAttachmentDto::from).toList();
    }

    @Transactional
    public HelpDeskAttachmentDto addByStaff(Long ticketId, MultipartFile file, boolean visibleToRequester) throws IOException {
        HelpDeskTicket t = require(ticketId);
        if (t.getStatus() == HelpDeskStatus.MERGED) throw ApiException.badRequest("This ticket was merged into " + t.getMergedIntoNumber() + ". Add the file there.");
        HelpDeskAttachment a = store(t, file, false, visibleToRequester, HelpDeskEvents.actorName());
        events.record(t, HelpDeskEventKind.FILE, "Added " + a.getFileName() + (visibleToRequester ? " (the requester can see it)" : " (internal)"));
        auditLogService.record("HELPDESK_FILE_ADDED", "Added " + a.getFileName() + " to ticket " + t.getTicketNumber(), "HelpDeskTicket", t.getId());
        return HelpDeskAttachmentDto.from(a);
    }

    /** From the public page; the caller has already matched the ticket number and e-mail. */
    @Transactional
    public HelpDeskAttachment addByRequester(HelpDeskTicket t, MultipartFile file) throws IOException {
        String name = t.getRequesterName() == null || t.getRequesterName().isBlank() ? "Requester" : t.getRequesterName();
        HelpDeskAttachment a = store(t, file, true, true, name);
        events.record(t, HelpDeskEventKind.FILE, "Requester added " + a.getFileName(), name);
        return a;
    }

    @Transactional(readOnly = true)
    public FileData file(Long ticketId, Long attachmentId) {
        require(ticketId);
        HelpDeskAttachment a = repository.findById(attachmentId).filter(x -> x.getTicketId().equals(ticketId)).orElseThrow(() -> ApiException.notFound("File not found."));
        return new FileData(a.getFileName(), a.getContentType(), a.getData());
    }

    /** A file the requester may see (their own, or a staff file marked visible). */
    @Transactional(readOnly = true)
    public FileData publicFile(HelpDeskTicket t, Long attachmentId) {
        HelpDeskAttachment a = repository.findById(attachmentId)
                .filter(x -> x.getTicketId().equals(t.getId()) && (x.isFromRequester() || x.isVisibleToRequester()))
                .orElseThrow(() -> ApiException.notFound("File not found."));
        return new FileData(a.getFileName(), a.getContentType(), a.getData());
    }

    @Transactional(readOnly = true)
    public List<HelpDeskAttachment> visibleTo(Long ticketId) {
        return repository.findByTicketIdOrderByIdAsc(ticketId).stream().filter(a -> a.isFromRequester() || a.isVisibleToRequester()).toList();
    }

    @Transactional
    public void delete(Long ticketId, Long attachmentId) {
        HelpDeskTicket t = require(ticketId);
        HelpDeskAttachment a = repository.findById(attachmentId).filter(x -> x.getTicketId().equals(ticketId)).orElseThrow(() -> ApiException.notFound("File not found."));
        repository.delete(a);
        events.record(t, HelpDeskEventKind.FILE, "Removed " + a.getFileName());
        auditLogService.record("HELPDESK_FILE_DELETED", "Removed " + a.getFileName() + " from ticket " + t.getTicketNumber(), "HelpDeskTicket", t.getId());
    }

    @Transactional
    public void setVisible(Long ticketId, Long attachmentId, boolean visible) {
        HelpDeskAttachment a = repository.findById(attachmentId).filter(x -> x.getTicketId().equals(ticketId)).orElseThrow(() -> ApiException.notFound("File not found."));
        a.setVisibleToRequester(visible);
        repository.save(a);
    }

    private HelpDeskAttachment store(HelpDeskTicket t, MultipartFile file, boolean fromRequester, boolean visible, String by) throws IOException {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to add.");
        if (file.getSize() > MAX_FILE_BYTES) throw ApiException.badRequest("A file can be at most 10 MB.");
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "file" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\\u0000]", "_");
        if (fileName.length() > 200) fileName = fileName.substring(fileName.length() - 200);
        String type = detectType(file, fileName);
        if (type == null) throw ApiException.badRequest("Files must be photos (JPEG, PNG, GIF, WebP), PDF or text files.");
        byte[] data = file.getBytes();
        if (!looksRight(data, type)) throw ApiException.badRequest("That file does not look like a valid " + type.substring(type.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT) + " file.");
        if (repository.countByTicketId(t.getId()) >= MAX_FILES) throw ApiException.badRequest("At most " + MAX_FILES + " files can be added to one ticket.");
        HelpDeskAttachment a = new HelpDeskAttachment();
        a.setTicketId(t.getId());
        a.setFileName(fileName);
        a.setContentType(type);
        a.setSizeBytes(data.length);
        a.setUploadedByName(by);
        a.setFromRequester(fromRequester);
        a.setVisibleToRequester(visible);
        a.setData(data);
        return repository.save(a);
    }

    private HelpDeskTicket require(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> ApiException.notFound("Ticket not found: " + id));
    }

    private static String detectType(MultipartFile file, String fileName) {
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (TYPES.contains(declared)) return declared;
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            default -> null;
        };
    }

    /** Checks the first bytes match the type, so a renamed file is not stored as something it is not. */
    static boolean looksRight(byte[] d, String type) {
        if (d.length < 4) return false;
        return switch (type) {
            case "application/pdf" -> d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F';
            case "image/jpeg" -> (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8;
            case "image/png" -> (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
            case "image/gif" -> d[0] == 'G' && d[1] == 'I' && d[2] == 'F';
            case "image/webp" -> d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
            default -> {
                for (int i = 0; i < Math.min(d.length, 4096); i++) if (d[i] == 0) yield false;
                yield true;
            }
        };
    }
}
