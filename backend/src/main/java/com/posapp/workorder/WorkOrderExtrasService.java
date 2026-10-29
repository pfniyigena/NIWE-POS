package com.posapp.workorder;

import com.posapp.audit.AuditLog;
import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.email.DocumentPdfService;
import com.posapp.email.EmailAttachment;
import com.posapp.email.MailService;
import com.posapp.settings.StoreSettings;
import com.posapp.settings.StoreSettingsService;
import com.posapp.timetracking.TimeTrackingService;
import com.posapp.workorder.WorkOrderService.Actor;
import com.posapp.workorder.dto.WorkOrderDtos.*;
import com.posapp.workorder.dto.WorkOrderRequests.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Work Orders, v1.15.0: the customer's approval of an estimate, notes, photos and files, the history of a job, the printable documents
 * (job card, estimate, completion note), e-mailing them to the customer, and the module's small settings.
 */
@Service
public class WorkOrderExtrasService {
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final int MAX_FILES = 40;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf", "text/plain");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final WorkOrderService service;
    private final WorkOrderRepository repository;
    private final WorkOrderLineRepository lineRepository;
    private final WorkOrderCostRepository costRepository;
    private final WorkOrderNoteRepository noteRepository;
    private final WorkOrderAttachmentRepository attachmentRepository;
    private final CustomerRepository customerRepository;
    private final StoreSettingsService storeSettingsService;
    private final DocumentPdfService pdfService;
    private final MailService mailService;
    private final AuditLogService auditLogService;
    @PersistenceContext
    private EntityManager em;

    public WorkOrderExtrasService(WorkOrderService service, WorkOrderRepository repository, WorkOrderLineRepository lineRepository,
                                  WorkOrderCostRepository costRepository, WorkOrderNoteRepository noteRepository,
                                  WorkOrderAttachmentRepository attachmentRepository, CustomerRepository customerRepository,
                                  StoreSettingsService storeSettingsService, DocumentPdfService pdfService, MailService mailService,
                                  AuditLogService auditLogService) {
        this.service = service;
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.costRepository = costRepository;
        this.noteRepository = noteRepository;
        this.attachmentRepository = attachmentRepository;
        this.customerRepository = customerRepository;
        this.storeSettingsService = storeSettingsService;
        this.pdfService = pdfService;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String clean(String s, int max, String what) {
        if (!notBlank(s)) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ settings

    @Transactional
    public SettingsDto settings() {
        WorkOrderSettings s = service.settings();
        return new SettingsDto(s.isRequireApprovalDefault(), s.isEmailCustomerOnComplete());
    }

    @Transactional
    public SettingsDto saveSettings(Actor actor, SettingsRequest r) {
        if (!actor.manager()) throw ApiException.forbidden("Only a manager can do that.");
        WorkOrderSettings s = service.settings();
        if (r.requireApprovalDefault() != null) s.setRequireApprovalDefault(r.requireApprovalDefault());
        if (r.emailCustomerOnComplete() != null) s.setEmailCustomerOnComplete(r.emailCustomerOnComplete());
        service.saveSettings(s);
        auditLogService.record("WORK_ORDER_SETTINGS_UPDATED", "Work Orders settings: new customer jobs need approval = " + s.isRequireApprovalDefault()
                + ", e-mail the customer on completion = " + s.isEmailCustomerOnComplete(), "WorkOrderSettings", s.getId());
        return new SettingsDto(s.isRequireApprovalDefault(), s.isEmailCustomerOnComplete());
    }

    // ------------------------------------------------------------------ estimate and approval

    private EstimateDto estimate(WorkOrder w, boolean emailSent, String emailNote) {
        boolean fixed = w.getFixedPrice().signum() > 0;
        List<EstimateLine> lines = new ArrayList<>();
        if (fixed) {
            lines.add(new EstimateLine("PRICE", w.getPackageName() != null ? w.getPackageName() + " (fixed price)" : "Fixed price for the job", BigDecimal.ONE, w.getFixedPrice(), w.getFixedPrice()));
        } else if (w.getEstimatedHours() != null && w.getEstimatedHours().signum() > 0 && w.getLabourRate() != null && w.getLabourRate().signum() > 0) {
            lines.add(new EstimateLine("LABOUR", "Labour, estimated " + w.getEstimatedHours().stripTrailingZeros().toPlainString() + " hours", w.getEstimatedHours(),
                    w.getLabourRate(), money(w.getEstimatedHours().multiply(w.getLabourRate()))));
        }
        for (WorkOrderLine l : lineRepository.findByWorkOrderIdOrderByIdAsc(w.getId())) {
            boolean included = l.isIncluded() && fixed;
            BigDecimal amount = included ? ZERO : money(l.getUnitPrice().multiply(l.getQuantity()));
            lines.add(new EstimateLine(included ? "PART_INCLUDED" : "PART", l.getProductName(), l.getQuantity(), included ? null : l.getUnitPrice(), amount));
        }
        for (WorkOrderCost c : costRepository.findByWorkOrderIdOrderByCostDateAscIdAsc(w.getId())) {
            if (c.getChargeAmount() != null && c.getChargeAmount().signum() > 0) lines.add(new EstimateLine("COST", c.getDescription(), BigDecimal.ONE, c.getChargeAmount(), c.getChargeAmount()));
        }
        BigDecimal total = money(lines.stream().map(EstimateLine::amount).reduce(ZERO, BigDecimal::add));
        boolean changed = (w.getApprovalStatus() == ApprovalStatus.REQUESTED || w.getApprovalStatus() == ApprovalStatus.APPROVED) && w.getApprovalEstimateTotal() != null
                && w.getApprovalEstimateTotal().compareTo(total) != 0;
        boolean canStart = !w.isApprovalRequired() || w.getType() != WorkOrderType.CUSTOMER || w.getApprovalStatus() == ApprovalStatus.APPROVED;
        return new EstimateDto(w.getId(), w.getNumber(), w.isApprovalRequired(), w.getApprovalStatus(), w.getEstimatedHours(), w.getLabourRate(), w.getFixedPrice(), lines, total,
                w.getApprovalEstimateTotal(), w.getApprovalRequestedAt(), w.getApprovalDecidedAt(), w.getApprovalByName(), w.getApprovalNote(), w.getApprovalRecordedBy(),
                changed, canStart, emailSent, emailNote);
    }

    @Transactional
    public EstimateDto getEstimate(Actor actor, Long id) {
        return estimate(service.requireView(actor, id), false, null);
    }

    @Transactional
    public EstimateDto saveEstimateSettings(Actor actor, Long id, EstimateSettingsRequest r) {
        WorkOrder w = service.requireManage(actor, id);
        if (w.getStatus() == WorkOrderStatus.COMPLETED || w.getStatus() == WorkOrderStatus.CANCELLED) throw ApiException.conflict("This work order is " + w.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        if (w.getType() != WorkOrderType.CUSTOMER) throw ApiException.badRequest("Only a customer job has an estimate to approve.");
        if (r.estimatedHours() != null) {
            if (r.estimatedHours().signum() < 0) throw ApiException.badRequest("The estimated hours cannot be negative.");
            BigDecimal before = w.getEstimatedHours() == null ? ZERO : w.getEstimatedHours();
            w.setEstimatedHours(r.estimatedHours().signum() == 0 ? null : r.estimatedHours().setScale(2, java.math.RoundingMode.HALF_UP));
            repository.save(w);
            if (before.compareTo(w.getEstimatedHours() == null ? ZERO : w.getEstimatedHours()) != 0) service.resetApprovalIfEstimateChanged(w);
        }
        if (r.approvalRequired() != null && r.approvalRequired() != w.isApprovalRequired()) {
            w.setApprovalRequired(r.approvalRequired());
            repository.save(w);
            auditLogService.record("WORK_ORDER_APPROVAL_REQUIREMENT", (w.isApprovalRequired() ? "Now requires" : "No longer requires") + " the customer's approval before work starts on " + w.getNumber(), "WorkOrder", id);
        }
        return estimate(w, false, null);
    }

    @Transactional
    public EstimateDto sendEstimate(Actor actor, Long id, EstimateSendRequest r) {
        WorkOrder w = service.requireManage(actor, id);
        if (w.getType() != WorkOrderType.CUSTOMER) throw ApiException.badRequest("Only a customer job has an estimate to approve.");
        if (w.getStatus() != WorkOrderStatus.OPEN) throw ApiException.conflict("An estimate can only be sent before work has started.");
        EstimateDto e = estimate(w, false, null);
        if (e.total().signum() <= 0) throw ApiException.badRequest("Add a price, estimated hours with a labour rate, or parts before sending an estimate.");
        w.setApprovalStatus(ApprovalStatus.REQUESTED);
        w.setApprovalRequestedAt(Instant.now());
        w.setApprovalEstimateTotal(e.total());
        w.setApprovalDecidedAt(null);
        w.setApprovalByName(null);
        w.setApprovalNote(null);
        w.setApprovalRecordedBy(null);
        repository.save(w);
        auditLogService.record("WORK_ORDER_ESTIMATE_SENT", "Estimate of " + e.total().toPlainString() + " for " + w.getNumber() + " sent to the customer for approval", "WorkOrder", id);
        boolean sent = false;
        String note = null;
        if (r != null && Boolean.TRUE.equals(r.email())) {
            String problem = emailProblem(w);
            if (problem != null) {
                note = problem;
            } else {
                try {
                    String extra = r.note() != null && !r.note().isBlank() ? "<p>" + html(r.note().trim()) + "</p>" : "";
                    sendMail(w, "Estimate " + w.getNumber(), "<p>Please find our estimate for <b>" + html(w.getTitle()) + "</b> attached. Sign it and send it back to approve the work.</p>" + extra,
                            "estimate-" + w.getNumber() + ".pdf", pdf("ESTIMATE", w));
                    sent = true;
                    note = "Estimate e-mailed to " + customerEmail(w) + ".";
                    auditLogService.record("WORK_ORDER_EMAILED", "E-mailed the estimate of " + w.getNumber() + " to " + customerEmail(w), "WorkOrder", id);
                } catch (ApiException ex) {
                    note = ex.getMessage();
                }
            }
        }
        return estimate(w, sent, note);
    }

    @Transactional
    public EstimateDto approve(Actor actor, Long id, ApprovalDecisionRequest r) {
        WorkOrder w = service.requireManage(actor, id);
        if (w.getType() != WorkOrderType.CUSTOMER) throw ApiException.badRequest("Only a customer job has an estimate to approve.");
        if (w.getStatus() != WorkOrderStatus.OPEN) throw ApiException.conflict("The estimate can only be approved before work has started.");
        String by = clean(r == null ? null : r.byName(), 200, "The name");
        if (by == null) throw ApiException.badRequest("Enter who at the customer approved the estimate.");
        EstimateDto e = estimate(w, false, null);
        if (e.total().signum() <= 0) throw ApiException.badRequest("Add a price, estimated hours with a labour rate, or parts first, so there is something to approve.");
        w.setApprovalStatus(ApprovalStatus.APPROVED);
        if (w.getApprovalRequestedAt() == null) w.setApprovalRequestedAt(Instant.now());
        w.setApprovalEstimateTotal(e.total());
        w.setApprovalDecidedAt(Instant.now());
        w.setApprovalByName(by);
        w.setApprovalNote(clean(r.note(), 1000, "The note"));
        w.setApprovalRecordedBy(TimeTrackingService.actorName(actor.user()));
        repository.save(w);
        auditLogService.record("WORK_ORDER_ESTIMATE_APPROVED", "Estimate of " + e.total().toPlainString() + " for " + w.getNumber() + " approved by " + by, "WorkOrder", id);
        return estimate(w, false, null);
    }

    @Transactional
    public EstimateDto reject(Actor actor, Long id, ApprovalDecisionRequest r) {
        WorkOrder w = service.requireManage(actor, id);
        if (w.getApprovalStatus() != ApprovalStatus.REQUESTED) throw ApiException.conflict("There is no estimate waiting for an answer.");
        if (w.getStatus() != WorkOrderStatus.OPEN) throw ApiException.conflict("The job has already started.");
        w.setApprovalStatus(ApprovalStatus.REJECTED);
        w.setApprovalDecidedAt(Instant.now());
        w.setApprovalByName(clean(r == null ? null : r.byName(), 200, "The name"));
        w.setApprovalNote(clean(r == null ? null : r.note(), 1000, "The note"));
        w.setApprovalRecordedBy(TimeTrackingService.actorName(actor.user()));
        repository.save(w);
        auditLogService.record("WORK_ORDER_ESTIMATE_REJECTED", "Estimate for " + w.getNumber() + " was not approved by the customer", "WorkOrder", id);
        return estimate(w, false, null);
    }

    // ------------------------------------------------------------------ notes

    @Transactional
    public List<NoteDto> notes(Actor actor, Long id) {
        WorkOrder w = service.requireView(actor, id);
        return noteRepository.findByWorkOrderIdOrderByCreatedAtDescIdDesc(w.getId()).stream().map(n -> noteDto(actor, n)).toList();
    }

    private static NoteDto noteDto(Actor actor, WorkOrderNote n) {
        boolean mine = n.getAuthorUserId() != null && n.getAuthorUserId().equals(actor.user().getId());
        return new NoteDto(n.getId(), n.getAuthorUserId(), n.getAuthorName(), n.getText(), n.getCreatedAt(), actor.manager() || mine);
    }

    @Transactional
    public NoteDto addNote(Actor actor, Long id, NoteRequest r) {
        WorkOrder w = service.requireWork(actor, id);
        String text = clean(r == null ? null : r.text(), 2000, "A note");
        if (text == null) throw ApiException.badRequest("Write the note first.");
        WorkOrderNote n = new WorkOrderNote();
        n.setWorkOrderId(w.getId());
        n.setAuthorUserId(actor.user().getId());
        n.setAuthorName(TimeTrackingService.actorName(actor.user()));
        n.setText(text);
        noteRepository.save(n);
        auditLogService.record("WORK_ORDER_NOTE_ADDED", "Added a note to " + w.getNumber(), "WorkOrder", id);
        return noteDto(actor, n);
    }

    @Transactional
    public void deleteNote(Actor actor, Long id, Long noteId) {
        WorkOrder w = service.requireWork(actor, id);
        WorkOrderNote n = noteRepository.findById(noteId).filter(x -> x.getWorkOrderId().equals(id)).orElseThrow(() -> ApiException.notFound("Note not found."));
        boolean mine = n.getAuthorUserId() != null && n.getAuthorUserId().equals(actor.user().getId());
        if (!actor.manager() && !mine) throw ApiException.forbidden("Only the person who wrote a note, or a manager, can delete it.");
        noteRepository.delete(n);
        auditLogService.record("WORK_ORDER_NOTE_DELETED", "Deleted a note from " + w.getNumber(), "WorkOrder", id);
    }

    // ------------------------------------------------------------------ photos and files

    @Transactional
    public List<AttachmentDto> attachments(Actor actor, Long id) {
        WorkOrder w = service.requireView(actor, id);
        return attachmentRepository.rows(w.getId()).stream().map(r -> new AttachmentDto(r.getId(), r.getFileName(), r.getContentType(), r.getSizeBytes(), r.getCaption(),
                r.getUploadedByName(), r.getCreatedAt(), r.getContentType().startsWith("image/"), true)).toList();
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

    @Transactional
    public AttachmentDto addAttachment(Actor actor, Long id, MultipartFile file, String caption) throws IOException {
        WorkOrder w = service.requireWork(actor, id);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to add.");
        if (file.getSize() > MAX_FILE_BYTES) throw ApiException.badRequest("A file can be at most 10 MB.");
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "file" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\\u0000]", "_");
        if (fileName.length() > 200) fileName = fileName.substring(fileName.length() - 200);
        String type = detectType(file, fileName);
        if (type == null) throw ApiException.badRequest("Files must be photos (JPEG, PNG, GIF, WebP), PDF or text files.");
        byte[] data = file.getBytes();
        if (!looksRight(data, type)) throw ApiException.badRequest("That file does not look like a valid " + type.substring(type.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT) + " file.");
        if (attachmentRepository.countByWorkOrderId(id) >= MAX_FILES) throw ApiException.badRequest("At most " + MAX_FILES + " files can be added to one job.");
        WorkOrderAttachment a = new WorkOrderAttachment();
        a.setWorkOrderId(id);
        a.setFileName(fileName);
        a.setContentType(type);
        a.setSizeBytes(data.length);
        a.setCaption(clean(caption, 300, "The caption"));
        a.setUploadedByName(TimeTrackingService.actorName(actor.user()));
        a.setData(data);
        attachmentRepository.save(a);
        auditLogService.record("WORK_ORDER_FILE_ADDED", "Added " + fileName + " to " + w.getNumber(), "WorkOrder", id);
        return new AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getCaption(), a.getUploadedByName(), a.getCreatedAt(), type.startsWith("image/"), true);
    }

    public record FileData(String fileName, String contentType, byte[] data) {
    }

    @Transactional
    public FileData file(Actor actor, Long id, Long attachmentId) {
        service.requireView(actor, id);
        WorkOrderAttachment a = attachmentRepository.findById(attachmentId).filter(x -> x.getWorkOrderId().equals(id)).orElseThrow(() -> ApiException.notFound("File not found."));
        return new FileData(a.getFileName(), a.getContentType(), a.getData());
    }

    @Transactional
    public void deleteAttachment(Actor actor, Long id, Long attachmentId) {
        WorkOrder w = service.requireWork(actor, id);
        WorkOrderAttachment a = attachmentRepository.findById(attachmentId).filter(x -> x.getWorkOrderId().equals(id)).orElseThrow(() -> ApiException.notFound("File not found."));
        attachmentRepository.delete(a);
        auditLogService.record("WORK_ORDER_FILE_DELETED", "Removed " + a.getFileName() + " from " + w.getNumber(), "WorkOrder", id);
    }

    // ------------------------------------------------------------------ history

    @Transactional
    @SuppressWarnings("unchecked")
    public List<HistoryDto> history(Actor actor, Long id) {
        service.requireView(actor, id);
        List<AuditLog> rows = em.createQuery("select a from AuditLog a where a.entityType = 'WorkOrder' and a.entityId = :id order by a.createdAt desc, a.id desc")
                .setParameter("id", id).setMaxResults(300).getResultList();
        return rows.stream().map(a -> new HistoryDto(a.getCreatedAt(), a.getActorFullName() != null && !a.getActorFullName().isBlank() ? a.getActorFullName() : a.getActorUsername(),
                a.getAction(), a.getDescription())).toList();
    }

    // ------------------------------------------------------------------ documents and e-mail

    private byte[] pdf(String kind, WorkOrder w) {
        Actor system = new Actor(null, true, true);
        WorkOrderDetail d = service.detailFor(system, w.getId());
        List<NoteDto> notes = noteRepository.findByWorkOrderIdOrderByCreatedAtAscIdAsc(w.getId()).stream()
                .map(n -> new NoteDto(n.getId(), n.getAuthorUserId(), n.getAuthorName(), n.getText(), n.getCreatedAt(), false)).toList();
        StoreSettings settings = storeSettingsService.getSettingsEntity();
        return pdfService.workOrderPdf(kind, d, "ESTIMATE".equals(kind) ? estimate(w, false, null) : null, notes, settings);
    }

    /** The PDF for a person who may see the job: kind is jobcard, estimate or completion. */
    @Transactional
    public byte[] document(Actor actor, Long id, String kind) {
        WorkOrder w = service.requireView(actor, id);
        String k = kind == null ? "jobcard" : kind.toLowerCase(Locale.ROOT);
        return switch (k) {
            case "jobcard" -> pdf("JOBCARD", w);
            case "estimate" -> {
                if (w.getType() != WorkOrderType.CUSTOMER) throw ApiException.badRequest("Only a customer job has an estimate.");
                if (!actor.viewer()) throw ApiException.forbidden("Only a manager can print the estimate.");
                yield pdf("ESTIMATE", w);
            }
            case "completion" -> {
                if (w.getType() != WorkOrderType.CUSTOMER) throw ApiException.badRequest("Only a customer job has a completion note.");
                if (w.getStatus() != WorkOrderStatus.COMPLETED) throw ApiException.conflict("The completion note is available once the job is completed.");
                yield pdf("COMPLETION", w);
            }
            default -> throw ApiException.badRequest("Unknown document: " + kind);
        };
    }

    private String customerEmail(WorkOrder w) {
        if (w.getCustomerId() == null) return null;
        return customerRepository.findById(w.getCustomerId()).map(Customer::getEmail).filter(e -> e != null && !e.isBlank()).map(String::trim).orElse(null);
    }

    private String emailProblem(WorkOrder w) {
        if (!mailService.isConfigured()) return "E-mail is not set up yet (Settings > Email settings), so nothing was sent. You can print the PDF instead.";
        if (customerEmail(w) == null) return "The customer has no e-mail address, so nothing was sent. You can print the PDF instead.";
        return null;
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void sendMail(WorkOrder w, String subject, String bodyHtml, String fileName, byte[] pdf) {
        String business = storeSettingsService.getSettingsEntity().getBusinessName();
        String greeting = "<p>Dear " + html(w.getCustomerName() == null ? "customer" : w.getCustomerName()) + ",</p>";
        String footer = "<p>" + (business == null || business.isBlank() ? "Thank you." : "Thank you,<br>" + html(business)) + "</p>";
        mailService.send(customerEmail(w), subject + (business == null || business.isBlank() ? "" : " - " + business), greeting + bodyHtml + footer,
                List.of(new EmailAttachment(fileName, "application/pdf", pdf)));
    }

    /** E-mails the completion note of a finished customer job to the customer. */
    @Transactional
    public Result emailCompletion(Actor actor, Long id) {
        WorkOrder w = service.requireManage(actor, id);
        if (w.getType() != WorkOrderType.CUSTOMER) throw ApiException.badRequest("Only a customer job has a completion note.");
        if (w.getStatus() != WorkOrderStatus.COMPLETED) throw ApiException.conflict("The job is not completed yet.");
        String problem = emailProblem(w);
        if (problem != null) throw ApiException.badRequest(problem);
        sendMail(w, "Job completed " + w.getNumber(), "<p>We have finished <b>" + html(w.getTitle()) + "</b>. The completion note is attached.</p>",
                "completion-" + w.getNumber() + ".pdf", pdf("COMPLETION", w));
        w.setCompletionEmailedAt(Instant.now());
        repository.save(w);
        auditLogService.record("WORK_ORDER_EMAILED", "E-mailed the completion note of " + w.getNumber() + " to " + customerEmail(w), "WorkOrder", id);
        return new Result(true, "Completion note e-mailed to " + customerEmail(w) + ".");
    }

    public record Result(boolean ok, String message) {
    }

    /** After a job is completed: sends the completion note automatically when that is switched on and possible. Never fails the completion. */
    @Transactional
    public void afterComplete(Long id) {
        try {
            WorkOrder w = repository.findById(id).orElse(null);
            if (w == null || w.getType() != WorkOrderType.CUSTOMER || w.getStatus() != WorkOrderStatus.COMPLETED || !service.settings().isEmailCustomerOnComplete()) return;
            if (emailProblem(w) != null) {
                auditLogService.record("WORK_ORDER_EMAIL_SKIPPED", "The completion note of " + w.getNumber() + " was not e-mailed: " + emailProblem(w), "WorkOrder", id);
                return;
            }
            sendMail(w, "Job completed " + w.getNumber(), "<p>We have finished <b>" + html(w.getTitle()) + "</b>. The completion note is attached.</p>",
                    "completion-" + w.getNumber() + ".pdf", pdf("COMPLETION", w));
            w.setCompletionEmailedAt(Instant.now());
            repository.save(w);
            auditLogService.record("WORK_ORDER_EMAILED", "E-mailed the completion note of " + w.getNumber() + " to " + customerEmail(w), "WorkOrder", id);
        } catch (Exception ex) {
            auditLogService.record("WORK_ORDER_EMAIL_FAILED", "The completion note could not be e-mailed: " + ex.getMessage(), "WorkOrder", id);
        }
    }
}
