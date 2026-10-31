package com.posapp.accounting;

import com.posapp.accounting.dto.ExpenseExtras.AttachmentDto;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Receipts, bills and other files kept with an expense: photos, PDF and text files up to 10 MB, 20 per expense. */
@Service
public class ExpenseAttachmentService {

    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final int MAX_FILES = 20;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf", "text/plain");

    private final ExpenseAttachmentRepository repository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;
    private final AuditLogService auditLogService;
    private final com.posapp.branch.BranchAccessService branchAccessService;

    public ExpenseAttachmentService(ExpenseAttachmentRepository repository, ExpenseRepository expenseRepository, ExpenseService expenseService,
                                    AuditLogService auditLogService, com.posapp.branch.BranchAccessService branchAccessService) {
        this.repository = repository;
        this.expenseRepository = expenseRepository;
        this.expenseService = expenseService;
        this.auditLogService = auditLogService;
        this.branchAccessService = branchAccessService;
    }

    public record FileData(String fileName, String contentType, byte[] data) {
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> list(Long expenseId) {
        require(expenseId);
        return repository.findByExpenseIdOrderByIdAsc(expenseId).stream().map(ExpenseAttachmentService::toDto).toList();
    }

    @Transactional
    public AttachmentDto add(Long expenseId, MultipartFile file) throws IOException {
        Expense e = require(expenseId);
        branchAccessService.assertCanAccess(e.getBranchId());
        if (e.getStatus() == ExpenseStatus.REJECTED || e.getStatus() == ExpenseStatus.VOIDED) {
            throw ApiException.badRequest("Files cannot be added to a " + e.getStatus().name().toLowerCase(Locale.ROOT) + " expense.");
        }
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to add.");
        if (file.getSize() > MAX_FILE_BYTES) throw ApiException.badRequest("A file can be at most 10 MB.");
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "file" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\\u0000]", "_");
        if (fileName.length() > 200) fileName = fileName.substring(fileName.length() - 200);
        String type = detectType(file, fileName);
        if (type == null) throw ApiException.badRequest("Files must be photos (JPEG, PNG, GIF, WebP), PDF or text files.");
        byte[] data = file.getBytes();
        if (!looksRight(data, type)) throw ApiException.badRequest("That file does not look like a valid " + type.substring(type.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT) + " file.");
        if (repository.countByExpenseId(expenseId) >= MAX_FILES) throw ApiException.badRequest("At most " + MAX_FILES + " files can be added to one expense.");
        ExpenseAttachment a = new ExpenseAttachment();
        a.setExpenseId(expenseId);
        a.setFileName(fileName);
        a.setContentType(type);
        a.setSizeBytes(data.length);
        a.setUploadedByName(ExpenseService.currentActorName());
        a.setData(data);
        a = repository.save(a);
        expenseService.event(expenseId, "FILE", "Added " + fileName);
        auditLogService.record("EXPENSE_FILE_ADDED", "Added " + fileName + " to expense " + e.getExpenseNumber(), "Expense", expenseId);
        return toDto(a);
    }

    @Transactional(readOnly = true)
    public FileData file(Long expenseId, Long attachmentId) {
        require(expenseId);
        ExpenseAttachment a = repository.findById(attachmentId).filter(x -> x.getExpenseId().equals(expenseId)).orElseThrow(() -> ApiException.notFound("File not found."));
        return new FileData(a.getFileName(), a.getContentType(), a.getData());
    }

    @Transactional
    public void delete(Long expenseId, Long attachmentId) {
        Expense e = require(expenseId);
        branchAccessService.assertCanAccess(e.getBranchId());
        ExpenseAttachment a = repository.findById(attachmentId).filter(x -> x.getExpenseId().equals(expenseId)).orElseThrow(() -> ApiException.notFound("File not found."));
        repository.delete(a);
        expenseService.event(expenseId, "FILE", "Removed " + a.getFileName());
        auditLogService.record("EXPENSE_FILE_DELETED", "Removed " + a.getFileName() + " from expense " + e.getExpenseNumber(), "Expense", expenseId);
    }

    private Expense require(Long id) {
        return expenseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Expense not found: " + id));
    }

    private static AttachmentDto toDto(ExpenseAttachment a) {
        return new AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getUploadedByName(), a.getCreatedAt(), a.getContentType().startsWith("image/"));
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
