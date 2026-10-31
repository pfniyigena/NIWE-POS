package com.posapp.assets;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.BranchAccessService;
import com.posapp.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Photos, invoices, warranty cards and other files kept with an asset: JPEG/PNG/GIF/WebP, PDF and text files up to 10 MB, 20 per asset. */
@Service
public class AssetAttachmentService {

    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final int MAX_FILES = 20;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf", "text/plain");

    public record AttachmentDto(Long id, String fileName, String contentType, long sizeBytes, String uploadedByName, Instant createdAt, boolean image) {
    }

    public record FileData(String fileName, String contentType, byte[] data) {
    }

    private final AssetAttachmentRepository repository;
    private final FixedAssetRepository assetRepository;
    private final AssetHistoryService history;
    private final AuditLogService auditLogService;
    private final BranchAccessService branchAccessService;

    public AssetAttachmentService(AssetAttachmentRepository repository, FixedAssetRepository assetRepository, AssetHistoryService history,
                                  AuditLogService auditLogService, BranchAccessService branchAccessService) {
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.history = history;
        this.auditLogService = auditLogService;
        this.branchAccessService = branchAccessService;
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> list(Long assetId) {
        require(assetId);
        return repository.findByAssetIdOrderByIdAsc(assetId).stream().map(AssetAttachmentService::toDto).toList();
    }

    @Transactional
    public AttachmentDto add(Long assetId, MultipartFile file) throws IOException {
        FixedAsset asset = require(assetId);
        branchAccessService.assertCanAccess(asset.getBranchId());
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to add.");
        if (file.getSize() > MAX_FILE_BYTES) throw ApiException.badRequest("A file can be at most 10 MB.");
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "file" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\\u0000]", "_");
        if (fileName.length() > 200) fileName = fileName.substring(fileName.length() - 200);
        String type = detectType(file, fileName);
        if (type == null) throw ApiException.badRequest("Files must be photos (JPEG, PNG, GIF, WebP), PDF or text files.");
        byte[] data = file.getBytes();
        if (!looksRight(data, type)) throw ApiException.badRequest("That file does not look like a valid " + type.substring(type.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT) + " file.");
        if (repository.countByAssetId(assetId) >= MAX_FILES) throw ApiException.badRequest("At most " + MAX_FILES + " files can be added to one asset.");
        AssetAttachment a = new AssetAttachment();
        a.setAssetId(assetId);
        a.setFileName(fileName);
        a.setContentType(type);
        a.setSizeBytes(data.length);
        a.setUploadedByName(AssetHistoryService.actor().name());
        a.setData(data);
        a = repository.save(a);
        history.event(assetId, "FILE", "Added " + fileName);
        auditLogService.record("ASSET_FILE_ADDED", "Added " + fileName + " to asset " + asset.getCode(), "FixedAsset", assetId);
        return toDto(a);
    }

    @Transactional(readOnly = true)
    public FileData file(Long assetId, Long attachmentId) {
        require(assetId);
        AssetAttachment a = repository.findById(attachmentId).filter(x -> x.getAssetId().equals(assetId)).orElseThrow(() -> ApiException.notFound("File not found."));
        return new FileData(a.getFileName(), a.getContentType(), a.getData());
    }

    @Transactional
    public void delete(Long assetId, Long attachmentId) {
        FixedAsset asset = require(assetId);
        branchAccessService.assertCanAccess(asset.getBranchId());
        AssetAttachment a = repository.findById(attachmentId).filter(x -> x.getAssetId().equals(assetId)).orElseThrow(() -> ApiException.notFound("File not found."));
        repository.delete(a);
        history.event(assetId, "FILE", "Removed " + a.getFileName());
        auditLogService.record("ASSET_FILE_DELETED", "Removed " + a.getFileName() + " from asset " + asset.getCode(), "FixedAsset", assetId);
    }

    private FixedAsset require(Long id) {
        FixedAsset asset = assetRepository.findById(id).orElseThrow(() -> ApiException.notFound("Asset not found: " + id));
        branchAccessService.assertCanAccess(asset.getBranchId());
        return asset;
    }

    private static AttachmentDto toDto(AssetAttachment a) {
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
