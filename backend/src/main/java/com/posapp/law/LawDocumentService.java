package com.posapp.law;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawDocumentDtos.*;
import com.posapp.law.dto.LawDocumentRequests.*;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsRepository;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Documents on matters. Files are kept in the database, one row per version (a saved version is never changed), so a backup of the database
 * is a backup of the documents. A document follows its matter's access rules; a confidential one is seen only by the matter's team and
 * admins. One person at a time can check a document out so two people do not overwrite each other. Letters and contracts can be made from
 * templates whose {{placeholders}} are filled in from the matter.
 */
@Service
public class LawDocumentService {

    /** The biggest file accepted (25 MB). */
    public static final long MAX_BYTES = 25L * 1024 * 1024;

    private static final Set<String> BLOCKED = Set.of("exe", "bat", "cmd", "com", "scr", "msi", "dll", "js", "jse", "vbs", "vbe", "wsf", "ps1", "sh", "jar", "apk", "app", "html",
            "htm", "svg", "hta", "lnk", "reg", "pif", "cpl");
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final LawAccess access;
    private final LawDocumentRepository repository;
    private final LawDocumentVersionRepository versionRepository;
    private final LawDocumentBlobRepository blobRepository;
    private final LawDocTemplateRepository templateRepository;
    private final LawMatterPartyRepository partyRepository;
    private final LawMatterTeamRepository teamRepository;
    private final LawContactRepository contactRepository;
    private final CustomerRepository customerRepository;
    private final StoreSettingsRepository storeSettingsRepository;
    private final AuditLogService auditLogService;

    public LawDocumentService(LawAccess access, LawDocumentRepository repository, LawDocumentVersionRepository versionRepository,
                              LawDocumentBlobRepository blobRepository, LawDocTemplateRepository templateRepository, LawMatterPartyRepository partyRepository,
                              LawMatterTeamRepository teamRepository, LawContactRepository contactRepository, CustomerRepository customerRepository,
                              StoreSettingsRepository storeSettingsRepository, AuditLogService auditLogService) {
        this.access = access;
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.blobRepository = blobRepository;
        this.templateRepository = templateRepository;
        this.partyRepository = partyRepository;
        this.teamRepository = teamRepository;
        this.contactRepository = contactRepository;
        this.customerRepository = customerRepository;
        this.storeSettingsRepository = storeSettingsRepository;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ helpers

    private static String clean(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static String ext(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** The file name alone, without any folder, control characters or odd symbols. */
    static String safeFileName(String raw) {
        String n = raw == null ? "" : raw.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        if (n.isEmpty()) n = "document";
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    private static String sha256(byte[] data) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] bytesOf(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to upload.");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("That file is too big. The limit is 25 MB.");
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be read.");
        }
    }

    private static void checkType(String fileName) {
        if (BLOCKED.contains(ext(fileName))) {
            throw ApiException.badRequest("Files of this type (." + ext(fileName) + ") cannot be stored here. Put it in a zip file or save it as a PDF.");
        }
    }

    private boolean canSee(LawMatter m, LawDocument d, Actor a) {
        return !d.isConfidential() || a.admin() || access.isTeam(m, a);
    }

    /** The document, if the person may see its matter and (when confidential) the document. Anything else answers "not found". */
    private LawDocument visible(Actor a, Long id) {
        access.on();
        LawDocument d = repository.findById(id).orElseThrow(() -> ApiException.notFound("Document not found."));
        LawMatter m = access.viewable(a, d.getMatterId());
        if (!canSee(m, d, a)) throw ApiException.notFound("Document not found.");
        return d;
    }

    private LawMatter matterOf(LawDocument d) {
        return access.find(d.getMatterId());
    }

    private DocDto toDto(LawDocument d, LawMatter m, Actor a) {
        boolean work = access.canWork(m, a);
        boolean lead = access.canLead(m, a);
        boolean mine = d.getCheckedOutByUserId() != null && d.getCheckedOutByUserId().equals(a.user().getId());
        boolean locked = d.getCheckedOutByUserId() != null;
        boolean author = d.getCreatedByUserId() != null && d.getCreatedByUserId().equals(a.user().getId());
        return new DocDto(d.getId(), d.getName(), d.getCategory(), d.getDescription(), d.isConfidential(), d.isSharedWithClient(), d.isArchived(), d.getCurrentVersion(),
                d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getCheckedOutByUserId(), d.getCheckedOutByName(), d.getCheckedOutAt(), mine, d.getCreatedByName(),
                d.getCreatedAt(), d.getUpdatedAt(), d.getTemplateId(), work && !d.isArchived() && (author || lead), work && !d.isArchived() && (!locked || mine),
                locked && (mine || lead), lead, d.isFromClient(), d.getReviewStatus(), d.getReviewNote());
    }

    private LawDocumentVersion saveVersion(LawDocument d, byte[] data, String fileName, String contentType, String note, Actor a) {
        return saveVersion(d, data, fileName, contentType, note, a.user().getId(), LawAccess.name(a.user()));
    }

    private LawDocumentVersion saveVersion(LawDocument d, byte[] data, String fileName, String contentType, String note, Long userId, String userName) {
        LawDocumentVersion v = new LawDocumentVersion();
        v.setDocumentId(d.getId());
        v.setVersionNo(d.getCurrentVersion());
        v.setFileName(fileName);
        v.setContentType(contentType);
        v.setSizeBytes(data.length);
        v.setSha256(sha256(data));
        v.setNote(clean(note));
        v.setUploadedByUserId(userId);
        v.setUploadedByName(userName);
        v = versionRepository.save(v);
        LawDocumentBlob b = new LawDocumentBlob();
        b.setVersionId(v.getId());
        b.setData(data);
        blobRepository.save(b);
        d.setFileName(fileName);
        d.setContentType(contentType);
        d.setSizeBytes(data.length);
        return v;
    }

    private String contentTypeOf(MultipartFile file, String fileName) {
        String ct = file.getContentType();
        if (ct == null || ct.isBlank() || ct.length() > 120 || !ct.matches("[A-Za-z0-9.+_-]+/[A-Za-z0-9.+_-]+")) {
            return "application/octet-stream";
        }
        return ct;
    }

    /** True when the matter has any document, so it can no longer be deleted. */
    @Transactional(readOnly = true)
    public boolean hasRecords(LawMatter m) {
        return repository.countByMatterId(m.getId()) > 0;
    }

    // ------------------------------------------------------------------ documents

    @Transactional
    public DocListDto list(Actor a, Long matterId, boolean includeArchived) {
        LawMatter m = access.viewable(a, matterId);
        List<LawDocument> all = repository.findByMatterIdOrderByUpdatedAtDesc(m.getId()).stream()
                .filter(d -> canSee(m, d, a) && (includeArchived || !d.isArchived())).toList();
        long total = all.stream().mapToLong(LawDocument::getSizeBytes).sum();
        return new DocListDto(m.getId(), m.getNumber(), m.getTitle(), all.stream().map(d -> toDto(d, m, a)).toList(), total, access.canWork(m, a), access.canLead(m, a));
    }

    @Transactional
    public DocDto upload(Actor a, Long matterId, MultipartFile file, String name, DocCategory category, String description, boolean confidential) {
        LawMatter m = access.workable(a, matterId);
        byte[] data = bytesOf(file);
        String fileName = safeFileName(file.getOriginalFilename());
        checkType(fileName);
        LawDocument d = new LawDocument();
        d.setMatterId(m.getId());
        d.setName(clean(name) != null ? clean(name) : fileName);
        d.setCategory(category != null ? category : DocCategory.OTHER);
        d.setDescription(clean(description));
        d.setConfidential(confidential);
        d.setCreatedByUserId(a.user().getId());
        d.setCreatedByName(LawAccess.name(a.user()));
        d.setCurrentVersion(1);
        d.setFileName(fileName);
        d.setContentType(contentTypeOf(file, fileName));
        d.setSizeBytes(data.length);
        d = repository.save(d);
        saveVersion(d, data, fileName, d.getContentType(), "First version", a);
        repository.save(d);
        auditLogService.record("LAW_DOCUMENT_ADDED", "Added document '" + d.getName() + "' to matter " + m.getNumber(), "LawDocument", d.getId());
        return toDto(d, m, a);
    }

    /** Keeps a paper the system made (a letter, a cover sheet) in the matter's documents as a new document. */
    @Transactional
    public DocDto saveGenerated(Actor a, Long matterId, String name, DocCategory category, String fileName, byte[] data, String description) {
        LawMatter m = access.workable(a, matterId);
        String file = safeFileName(fileName);
        LawDocument d = new LawDocument();
        d.setMatterId(m.getId());
        d.setName(clean(name) != null ? clean(name) : file);
        d.setCategory(category != null ? category : DocCategory.OTHER);
        d.setDescription(clean(description));
        d.setConfidential(false);
        d.setCreatedByUserId(a.user().getId());
        d.setCreatedByName(LawAccess.name(a.user()));
        d.setCurrentVersion(1);
        d.setFileName(file);
        d.setContentType("application/pdf");
        d.setSizeBytes(data.length);
        d = repository.save(d);
        saveVersion(d, data, file, "application/pdf", "Made by the system", a);
        repository.save(d);
        auditLogService.record("LAW_DOCUMENT_ADDED", "Saved '" + d.getName() + "' to matter " + m.getNumber(), "LawDocument", d.getId());
        return toDto(d, m, a);
    }

    @Transactional
    public DocDto newVersion(Actor a, Long id, MultipartFile file, String note, boolean keepLocked) {
        LawDocument d = visible(a, id);
        LawMatter m = access.workable(a, d.getMatterId());
        if (d.isArchived()) throw ApiException.conflict("This document is archived. Restore it first.");
        boolean mine = d.getCheckedOutByUserId() != null && d.getCheckedOutByUserId().equals(a.user().getId());
        if (d.getCheckedOutByUserId() != null && !mine) {
            throw ApiException.conflict(d.getCheckedOutByName() + " has this document checked out. Ask them to check it in, or the lead lawyer can release it.");
        }
        byte[] data = bytesOf(file);
        String fileName = safeFileName(file.getOriginalFilename());
        checkType(fileName);
        d.setCurrentVersion(d.getCurrentVersion() + 1);
        saveVersion(d, data, fileName, contentTypeOf(file, fileName), note, a);
        if (mine && !keepLocked) clearLock(d);
        repository.save(d);
        auditLogService.record("LAW_DOCUMENT_VERSION", "New version " + d.getCurrentVersion() + " of '" + d.getName() + "' on matter " + m.getNumber(), "LawDocument", d.getId());
        return toDto(d, m, a);
    }

    private static void clearLock(LawDocument d) {
        d.setCheckedOutByUserId(null);
        d.setCheckedOutByName(null);
        d.setCheckedOutAt(null);
    }

    @Transactional(readOnly = true)
    public VersionsDto versions(Actor a, Long id) {
        LawDocument d = visible(a, id);
        LawMatter m = matterOf(d);
        return new VersionsDto(toDto(d, m, a), versionRepository.findByDocumentIdOrderByVersionNoDesc(d.getId()).stream()
                .map(v -> new VersionDto(v.getId(), v.getVersionNo(), v.getFileName(), v.getContentType(), v.getSizeBytes(), v.getSha256(), v.getNote(), v.getUploadedByName(),
                        v.getCreatedAt())).toList());
    }

    public record Download(String fileName, String contentType, byte[] data) {
    }

    @Transactional
    public Download download(Actor a, Long id, Integer versionNo) {
        LawDocument d = visible(a, id);
        LawDocumentVersion v = (versionNo == null ? versionRepository.findByDocumentIdAndVersionNo(d.getId(), d.getCurrentVersion())
                : versionRepository.findByDocumentIdAndVersionNo(d.getId(), versionNo)).orElseThrow(() -> ApiException.notFound("Version not found."));
        LawDocumentBlob b = blobRepository.findByVersionId(v.getId()).orElseThrow(() -> ApiException.notFound("The file is missing."));
        if (d.isConfidential()) {
            auditLogService.record("LAW_DOCUMENT_DOWNLOAD", "Downloaded confidential document '" + d.getName() + "' version " + v.getVersionNo(), "LawDocument", d.getId());
        }
        return new Download(v.getFileName(), v.getContentType() != null ? v.getContentType() : "application/octet-stream", b.getData());
    }

    @Transactional
    public DocDto update(Actor a, Long id, DocUpdateRequest r) {
        LawDocument d = visible(a, id);
        LawMatter m = access.workable(a, d.getMatterId());
        boolean author = d.getCreatedByUserId() != null && d.getCreatedByUserId().equals(a.user().getId());
        if (!author && !access.canLead(m, a)) throw ApiException.forbidden("Only the person who added it, the lead lawyer, a manager or an admin can change a document.");
        if (d.isArchived()) throw ApiException.conflict("This document is archived. Restore it first.");
        if (r == null || clean(r.name()) == null) throw ApiException.badRequest("A document needs a name.");
        d.setName(clean(r.name()));
        if (r.category() != null) d.setCategory(r.category());
        d.setDescription(clean(r.description()));
        repository.save(d);
        auditLogService.record("LAW_DOCUMENT_UPDATED", "Updated document '" + d.getName() + "' on matter " + m.getNumber(), "LawDocument", d.getId());
        return toDto(d, m, a);
    }

    @Transactional
    public DocDto setFlags(Actor a, Long id, DocFlagsRequest r) {
        LawDocument d = visible(a, id);
        LawMatter m = access.leadable(a, d.getMatterId());
        if (r == null) throw ApiException.badRequest("Nothing to change.");
        if (d.isFromClient() && d.getReviewStatus() != DocReview.ACCEPTED) throw ApiException.conflict("Accept the document the client sent before changing who can see it.");
        if (r.confidential() != null) {
            if (!r.confidential().equals(d.isConfidential()) && !a.admin() && !access.isTeam(m, a)) {
                throw ApiException.forbidden("Only a member of the matter's team or an admin can change who may see a confidential document.");
            }
            d.setConfidential(r.confidential());
            if (r.confidential()) d.setSharedWithClient(false);
        }
        if (r.sharedWithClient() != null) {
            if (r.sharedWithClient() && d.isConfidential()) throw ApiException.conflict("A confidential document cannot be shared with the client. Remove the confidential mark first.");
            d.setSharedWithClient(r.sharedWithClient());
        }
        repository.save(d);
        auditLogService.record("LAW_DOCUMENT_FLAGS", "Document '" + d.getName() + "' on matter " + m.getNumber() + ": confidential=" + d.isConfidential() + ", shared with client=" + d.isSharedWithClient(),
                "LawDocument", d.getId());
        return toDto(d, m, a);
    }

    @Transactional
    public DocDto checkout(Actor a, Long id) {
        LawDocument d = visible(a, id);
        LawMatter m = access.workable(a, d.getMatterId());
        if (d.isArchived()) throw ApiException.conflict("This document is archived. Restore it first.");
        if (d.getCheckedOutByUserId() != null) {
            if (d.getCheckedOutByUserId().equals(a.user().getId())) return toDto(d, m, a);
            throw ApiException.conflict(d.getCheckedOutByName() + " already has this document checked out.");
        }
        d.setCheckedOutByUserId(a.user().getId());
        d.setCheckedOutByName(LawAccess.name(a.user()));
        d.setCheckedOutAt(Instant.now());
        repository.save(d);
        auditLogService.record("LAW_DOCUMENT_CHECKOUT", "Checked out '" + d.getName() + "' on matter " + m.getNumber(), "LawDocument", d.getId());
        return toDto(d, m, a);
    }

    @Transactional
    public DocDto release(Actor a, Long id) {
        LawDocument d = visible(a, id);
        LawMatter m = access.workable(a, d.getMatterId());
        if (d.getCheckedOutByUserId() == null) return toDto(d, m, a);
        boolean mine = d.getCheckedOutByUserId().equals(a.user().getId());
        if (!mine && !access.canLead(m, a)) throw ApiException.forbidden("Only " + d.getCheckedOutByName() + " or the lead lawyer can release this document.");
        String by = d.getCheckedOutByName();
        clearLock(d);
        repository.save(d);
        auditLogService.record("LAW_DOCUMENT_RELEASE", (mine ? "Released" : "Released (taken over from " + by + ")") + " '" + d.getName() + "' on matter " + m.getNumber(), "LawDocument", d.getId());
        return toDto(d, m, a);
    }

    @Transactional
    public DocDto archive(Actor a, Long id, boolean archived) {
        LawDocument d = visible(a, id);
        LawMatter m = access.leadable(a, d.getMatterId());
        if (archived && d.getCheckedOutByUserId() != null) throw ApiException.conflict("Release the document before archiving it.");
        d.setArchived(archived);
        repository.save(d);
        auditLogService.record(archived ? "LAW_DOCUMENT_ARCHIVED" : "LAW_DOCUMENT_RESTORED", (archived ? "Archived" : "Restored") + " '" + d.getName() + "' on matter " + m.getNumber(), "LawDocument", d.getId());
        return toDto(d, m, a);
    }

    // ------------------------------------------------------------------ client uploads (from the portal)

    /** Stores a file the client sent through the portal. It waits for review and is not shared back, not confidential. */
    @Transactional
    public LawDocument clientUpload(LawMatter m, String clientName, byte[] data, String rawFileName, String contentType, String name) {
        if (data == null || data.length == 0) throw ApiException.badRequest("Choose a file to send.");
        if (data.length > MAX_BYTES) throw ApiException.badRequest("That file is too big. The limit is 25 MB.");
        String fileName = safeFileName(rawFileName);
        checkType(fileName);
        LawDocument d = new LawDocument();
        d.setMatterId(m.getId());
        d.setName(clean(name) != null ? clean(name) : fileName);
        d.setCategory(DocCategory.OTHER);
        d.setDescription("Sent by the client through the portal");
        d.setFromClient(true);
        d.setReviewStatus(DocReview.PENDING);
        d.setCreatedByName(clientName + " (client)");
        d.setCurrentVersion(1);
        d.setFileName(fileName);
        d.setContentType(contentType != null && contentType.matches("[A-Za-z0-9.+_-]+/[A-Za-z0-9.+_-]+") && contentType.length() <= 120 ? contentType : "application/octet-stream");
        d.setSizeBytes(data.length);
        d = repository.save(d);
        saveVersion(d, data, fileName, d.getContentType(), "Sent by the client", null, clientName + " (client)");
        return repository.save(d);
    }

    /** The current file of a document, for callers that have already decided the person may have it. */
    @Transactional(readOnly = true)
    public Download currentFile(LawDocument d) {
        LawDocumentVersion v = versionRepository.findByDocumentIdAndVersionNo(d.getId(), d.getCurrentVersion()).orElseThrow(() -> ApiException.notFound("Version not found."));
        LawDocumentBlob b = blobRepository.findByVersionId(v.getId()).orElseThrow(() -> ApiException.notFound("The file is missing."));
        return new Download(v.getFileName(), v.getContentType() != null ? v.getContentType() : "application/octet-stream", b.getData());
    }

    /** A lawyer accepts a document the client sent (it joins the matter's documents) or rejects it with a reason (it is archived). */
    @Transactional
    public DocDto review(Actor a, Long id, ReviewRequest r) {
        LawDocument d = visible(a, id);
        LawMatter m = access.workable(a, d.getMatterId());
        if (!d.isFromClient() || d.getReviewStatus() != DocReview.PENDING) throw ApiException.conflict("This document is not waiting for review.");
        if (r == null || r.accept() == null) throw ApiException.badRequest("Accept or reject the document.");
        if (!r.accept() && clean(r.note()) == null) throw ApiException.badRequest("Tell the client why the document is rejected.");
        d.setReviewStatus(r.accept() ? DocReview.ACCEPTED : DocReview.REJECTED);
        d.setReviewNote(clean(r.note()));
        d.setReviewedByName(LawAccess.name(a.user()));
        if (!r.accept()) d.setArchived(true);
        repository.save(d);
        auditLogService.record(r.accept() ? "LAW_DOCUMENT_ACCEPTED" : "LAW_DOCUMENT_REJECTED",
                (r.accept() ? "Accepted" : "Rejected") + " the client's document '" + d.getName() + "' on matter " + m.getNumber(), "LawDocument", d.getId());
        return toDto(d, m, a);
    }

    // ------------------------------------------------------------------ templates

    private TemplateDto toDto(LawDocTemplate t) {
        return new TemplateDto(t.getId(), t.getName(), t.getCategory(), t.getDescription(), t.getKind(), t.getKind() == TemplateKind.TEXT ? t.getBody() : null, t.getFileName(),
                t.isActive(), t.getCreatedByName(), t.getUpdatedAt(), repository.countByTemplateId(t.getId()));
    }

    private void needManager(boolean canManage) {
        if (!canManage) throw ApiException.forbidden("Only an admin or an Operations manager can change the document templates.");
    }

    @Transactional(readOnly = true)
    public List<TemplateDto> templates(boolean activeOnly) {
        access.on();
        return templateRepository.findAllByOrderByNameAsc().stream().filter(t -> !activeOnly || t.isActive()).map(this::toDto).toList();
    }

    @Transactional
    public TemplateDto saveTemplate(Actor a, boolean canManage, Long id, TemplateRequest r) {
        access.on();
        needManager(canManage);
        if (r == null || clean(r.name()) == null) throw ApiException.badRequest("A template needs a name.");
        LawDocTemplate t = id == null ? new LawDocTemplate() : templateRepository.findById(id).orElseThrow(() -> ApiException.notFound("Template not found."));
        TemplateKind kind = id == null ? (r.kind() != null ? r.kind() : TemplateKind.TEXT) : t.getKind();
        if (id != null && r.kind() != null && r.kind() != t.getKind()) throw ApiException.badRequest("The kind of a template cannot be changed. Make a new template instead.");
        if (kind == TemplateKind.TEXT && (id == null || r.body() != null) && clean(r.body()) == null) throw ApiException.badRequest("Write the text of the template.");
        if (kind == TemplateKind.TEXT && r.body() != null && r.body().length() > 100_000) throw ApiException.badRequest("The template text is too long.");
        t.setName(clean(r.name()));
        if (r.category() != null) t.setCategory(r.category());
        t.setDescription(clean(r.description()));
        t.setKind(kind);
        if (kind == TemplateKind.TEXT && r.body() != null) t.setBody(r.body());
        if (r.active() != null) t.setActive(r.active());
        if (id == null) t.setCreatedByName(LawAccess.name(a.user()));
        t = templateRepository.save(t);
        auditLogService.record("LAW_TEMPLATE_SAVED", "Saved document template '" + t.getName() + "'", "LawDocTemplate", t.getId());
        return toDto(t);
    }

    @Transactional
    public TemplateDto templateFile(Actor a, boolean canManage, Long id, MultipartFile file) {
        access.on();
        needManager(canManage);
        LawDocTemplate t = templateRepository.findById(id).orElseThrow(() -> ApiException.notFound("Template not found."));
        if (t.getKind() != TemplateKind.DOCX) throw ApiException.badRequest("Only a Word template takes a file.");
        byte[] data = bytesOf(file);
        String fileName = safeFileName(file.getOriginalFilename());
        if (!"docx".equals(ext(fileName))) throw ApiException.badRequest("Upload a Word document (.docx).");
        try (XWPFDocument ignored = new XWPFDocument(new ByteArrayInputStream(data))) {
            // it opens, so it is a real Word document
        } catch (Exception e) {
            throw ApiException.badRequest("That is not a valid Word (.docx) file.");
        }
        t.setData(data);
        t.setFileName(fileName);
        templateRepository.save(t);
        auditLogService.record("LAW_TEMPLATE_FILE", "Uploaded the Word file of template '" + t.getName() + "'", "LawDocTemplate", t.getId());
        return toDto(t);
    }

    @Transactional
    public void deleteTemplate(boolean canManage, Long id) {
        access.on();
        needManager(canManage);
        LawDocTemplate t = templateRepository.findById(id).orElseThrow(() -> ApiException.notFound("Template not found."));
        if (repository.countByTemplateId(id) > 0) throw ApiException.conflict("Documents were made from this template. Switch it off instead of deleting it.");
        templateRepository.delete(t);
        auditLogService.record("LAW_TEMPLATE_DELETED", "Deleted document template '" + t.getName() + "'", "LawDocTemplate", id);
    }

    @Transactional(readOnly = true)
    public Download templateFileDownload(Long id) {
        access.on();
        LawDocTemplate t = templateRepository.findById(id).orElseThrow(() -> ApiException.notFound("Template not found."));
        if (t.getData() == null) throw ApiException.notFound("This template has no file.");
        return new Download(t.getFileName(), DOCX, t.getData());
    }

    public List<PlaceholderDto> placeholders() {
        return List.of(
                new PlaceholderDto("client.name", "The client's name"), new PlaceholderDto("client.phone", "The client's phone"), new PlaceholderDto("client.email", "The client's e-mail"),
                new PlaceholderDto("matter.number", "The matter number"), new PlaceholderDto("matter.title", "The matter title"), new PlaceholderDto("matter.practiceArea", "Practice area"),
                new PlaceholderDto("matter.jurisdiction", "Jurisdiction"), new PlaceholderDto("matter.court", "Court or forum"), new PlaceholderDto("matter.courtCaseNumber", "The court's case number"),
                new PlaceholderDto("matter.status", "Open, on hold or closed"), new PlaceholderDto("matter.opened", "Date the matter was opened"),
                new PlaceholderDto("lead.name", "The lead lawyer"), new PlaceholderDto("team", "Everyone on the matter's team"),
                new PlaceholderDto("opposing.parties", "Opposing parties (names, comma separated)"), new PlaceholderDto("opposing.counsel", "Opposing counsel"),
                new PlaceholderDto("today", "Today's date, for example 14 October 2026"), new PlaceholderDto("user.name", "The person making the document"),
                new PlaceholderDto("firm.name", "The firm's name (Settings > Business details)"), new PlaceholderDto("firm.address", "The firm's address"),
                new PlaceholderDto("firm.phone", "The firm's phone"), new PlaceholderDto("firm.email", "The firm's e-mail"));
    }

    private Map<String, String> values(LawMatter m, Actor a) {
        Map<String, String> v = new HashMap<>();
        Customer c = customerRepository.findById(m.getClientCustomerId()).orElse(null);
        v.put("client.name", m.getClientName());
        v.put("client.phone", c != null && c.getPhone() != null ? c.getPhone() : "");
        v.put("client.email", c != null && c.getEmail() != null ? c.getEmail() : "");
        v.put("matter.number", m.getNumber());
        v.put("matter.title", m.getTitle());
        v.put("matter.practicearea", nz(m.getPracticeArea()));
        v.put("matter.jurisdiction", nz(m.getJurisdiction()));
        v.put("matter.court", nz(m.getCourtOrForum()));
        v.put("matter.courtcasenumber", nz(m.getCourtCaseNumber()));
        v.put("matter.status", m.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        v.put("matter.opened", m.getOpenedDate() != null ? m.getOpenedDate().format(LONG_DATE) : "");
        v.put("lead.name", nz(m.getLeadName()));
        v.put("team", teamRepository.findByMatterId(m.getId()).stream().map(LawMatterTeam::getUserName).collect(Collectors.joining(", ")));
        List<LawMatterParty> parties = partyRepository.findByMatterIdOrderByIdAsc(m.getId());
        Map<Long, String> names = contactRepository.findAllById(parties.stream().map(LawMatterParty::getContactId).toList()).stream()
                .collect(Collectors.toMap(LawContact::getId, LawContact::getName, (x, y) -> x));
        v.put("opposing.parties", parties.stream().filter(p -> p.getRole() == PartyRole.OPPOSING_PARTY).map(p -> names.getOrDefault(p.getContactId(), "")).collect(Collectors.joining(", ")));
        v.put("opposing.counsel", parties.stream().filter(p -> p.getRole() == PartyRole.OPPOSING_COUNSEL).map(p -> names.getOrDefault(p.getContactId(), "")).collect(Collectors.joining(", ")));
        v.put("today", LocalDate.now().format(LONG_DATE));
        v.put("user.name", LawAccess.name(a.user()));
        StoreSettings s = storeSettingsRepository.findAll().stream().findFirst().orElse(null);
        v.put("firm.name", s != null ? nz(s.getBusinessName()) : "");
        v.put("firm.address", s != null ? nz(s.getAddress()) : "");
        v.put("firm.phone", s != null ? nz(s.getPhone()) : "");
        v.put("firm.email", s != null ? nz(s.getEmail()) : "");
        return v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String fill(String text, Map<String, String> values, Set<String> unresolved) {
        Matcher mt = TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (mt.find()) {
            String key = mt.group(1);
            String val = values.get(key.toLowerCase(Locale.ROOT));
            if (val == null) {
                unresolved.add(key);
                mt.appendReplacement(sb, Matcher.quoteReplacement(mt.group()));
            } else {
                mt.appendReplacement(sb, Matcher.quoteReplacement(val.replaceAll("\\s*\\R\\s*", ", ")));
            }
        }
        mt.appendTail(sb);
        return sb.toString();
    }

    private static void fillParagraph(XWPFParagraph p, Map<String, String> values, Set<String> unresolved) {
        String whole = p.getText();
        if (whole == null || !whole.contains("{{")) return;
        List<XWPFRun> runs = p.getRuns();
        if (runs.isEmpty()) return;
        if (hasBrokenToken(p, whole)) {
            // A placeholder is split across runs (Word does this when the text was edited in pieces): merge the paragraph into its first run.
            String text = fill(whole, values, unresolved);
            for (int i = runs.size() - 1; i >= 1; i--) p.removeRun(i);
            XWPFRun first = runs.get(0);
            for (int i = first.getCTR().sizeOfTArray() - 1; i >= 1; i--) first.getCTR().removeT(i);
            first.setText(text, 0);
            return;
        }
        // Every placeholder sits inside one run, so the formatting is kept.
        for (XWPFRun r : runs) {
            for (int i = 0; i < r.getCTR().sizeOfTArray(); i++) {
                String t = r.getText(i);
                if (t != null && t.contains("{{")) r.setText(fill(t, values, unresolved), i);
            }
        }
    }

    /** A placeholder that starts in one run and ends in another. */
    private static boolean hasBrokenToken(XWPFParagraph p, String whole) {
        Matcher mt = TOKEN.matcher(whole);
        while (mt.find()) {
            String key = mt.group();
            boolean inOne = false;
            for (XWPFRun r : p.getRuns()) {
                for (int i = 0; i < r.getCTR().sizeOfTArray(); i++) {
                    String rt = r.getText(i);
                    if (rt != null && rt.contains(key)) inOne = true;
                }
            }
            if (!inOne) return true;
        }
        return false;
    }

    private static void fillBody(List<? extends IBodyElement> elements, Map<String, String> values, Set<String> unresolved) {
        for (IBodyElement e : elements) {
            if (e instanceof XWPFParagraph p) {
                fillParagraph(p, values, unresolved);
            } else if (e instanceof XWPFTable t) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) fillBody(cell.getBodyElements(), values, unresolved);
                }
            }
        }
    }

    private byte[] render(LawDocTemplate t, Map<String, String> values, Set<String> unresolved) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (t.getKind() == TemplateKind.DOCX) {
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(t.getData()))) {
                fillBody(doc.getBodyElements(), values, unresolved);
                for (XWPFHeader h : doc.getHeaderList()) fillBody(h.getBodyElements(), values, unresolved);
                for (XWPFFooter f : doc.getFooterList()) fillBody(f.getBodyElements(), values, unresolved);
                doc.write(out);
            }
        } else {
            try (XWPFDocument doc = new XWPFDocument()) {
                for (String line : fill(nz(t.getBody()), values, unresolved).split("\\R", -1)) {
                    XWPFParagraph p = doc.createParagraph();
                    p.createRun().setText(line);
                }
                doc.write(out);
            }
        }
        return out.toByteArray();
    }

    @Transactional
    public GenerateResult generate(Actor a, Long matterId, GenerateRequest r) {
        LawMatter m = access.workable(a, matterId);
        if (r == null || r.templateId() == null) throw ApiException.badRequest("Choose a template.");
        LawDocTemplate t = templateRepository.findById(r.templateId()).orElseThrow(() -> ApiException.notFound("Template not found."));
        if (!t.isActive()) throw ApiException.conflict("This template is switched off.");
        if (t.getKind() == TemplateKind.DOCX && t.getData() == null) throw ApiException.conflict("This Word template has no file yet.");
        Set<String> unresolved = new LinkedHashSet<>();
        byte[] data;
        try {
            data = render(t, values(m, a), unresolved);
        } catch (Exception e) {
            throw ApiException.badRequest("The document could not be made from this template: " + e.getMessage());
        }
        if (data.length > MAX_BYTES) throw ApiException.badRequest("The finished document is too big.");
        String name = clean(r.name()) != null ? clean(r.name()) : t.getName() + " — " + m.getNumber();
        LawDocument d = new LawDocument();
        d.setMatterId(m.getId());
        d.setName(name);
        d.setCategory(t.getCategory());
        d.setDescription("Made from the template '" + t.getName() + "'");
        d.setConfidential(Boolean.TRUE.equals(r.confidential()));
        d.setTemplateId(t.getId());
        d.setCreatedByUserId(a.user().getId());
        d.setCreatedByName(LawAccess.name(a.user()));
        String fileName = safeFileName(name.replaceAll("[\\\\/]", "-")) + ".docx";
        d = repository.save(d);
        d.setCurrentVersion(1);
        saveVersion(d, data, fileName, DOCX, "Made from template '" + t.getName() + "'", a);
        repository.save(d);
        auditLogService.record("LAW_DOCUMENT_GENERATED", "Made '" + name + "' from template '" + t.getName() + "' on matter " + m.getNumber(), "LawDocument", d.getId());
        return new GenerateResult(toDto(d, m, a), new ArrayList<>(unresolved));
    }
}
