package com.posapp.assets;

import com.posapp.assets.dto.AssetDtos.*;
import com.posapp.assets.dto.AssetRequests.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.posapp.assets.AssetSettingsService.SettingsDto;
import com.posapp.assets.AssetSettingsService.SettingsRequest;
import com.posapp.assets.AssetAttachmentService.AttachmentDto;
import com.posapp.user.UserRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

/** Everything under /api/assets (the Assets Management module). Reading needs the Accounting area; changing things needs manage. */
@RestController
@RequestMapping("/api/assets")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_VIEW')")
public class AssetController {

    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_ACCOUNTING_MANAGE')";

    private final FixedAssetService service;
    private final AssetDepreciationService depreciationService;
    private final AssetPurchaseService purchaseService;
    private final AssetValueService valueService;
    private final AssetCustodyService custodyService;
    private final AssetActionService actionService;
    private final AssetBulkService bulkService;
    private final AssetAttachmentService attachmentService;
    private final AssetHistoryService historyService;
    private final AssetSettingsService settingsService;
    private final AssetOverviewService overviewService;
    private final AssetVerificationService verificationService;
    private final UserRepository userRepository;

    public AssetController(FixedAssetService service, AssetDepreciationService depreciationService, AssetPurchaseService purchaseService,
                           AssetValueService valueService, AssetCustodyService custodyService, AssetActionService actionService,
                           AssetBulkService bulkService, AssetAttachmentService attachmentService, AssetHistoryService historyService,
                           AssetSettingsService settingsService, AssetOverviewService overviewService,
                           AssetVerificationService verificationService, UserRepository userRepository) {
        this.valueService = valueService;
        this.custodyService = custodyService;
        this.actionService = actionService;
        this.bulkService = bulkService;
        this.attachmentService = attachmentService;
        this.historyService = historyService;
        this.settingsService = settingsService;
        this.overviewService = overviewService;
        this.verificationService = verificationService;
        this.userRepository = userRepository;
        this.service = service;
        this.purchaseService = purchaseService;
        this.depreciationService = depreciationService;
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return service.listCategories();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/categories")
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return service.createCategory(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return service.updateCategory(id, request);
    }

    @GetMapping("/summary")
    public SummaryDto summary(@RequestParam(required = false) Long branchId) {
        return service.summary(branchId);
    }

    /** The register. Every filter is optional; {@code view} is one of no-custodian, held, warranty-expiring, insurance-expiring, expired, soon. */
    @GetMapping
    public List<AssetDto> list(@RequestParam(required = false) AssetStatus status, @RequestParam(required = false) Long branchId,
                               @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String q,
                               @RequestParam(required = false) String location, @RequestParam(required = false) String custodian,
                               @RequestParam(required = false) String view) {
        return service.list(new FixedAssetService.Filter(status, branchId, categoryId, q, location, custodian, view));
    }

    @GetMapping("/overview")
    public OverviewDto overview(@RequestParam(required = false) Long branchId) {
        return overviewService.overview(branchId);
    }

    @GetMapping("/settings")
    public SettingsDto settings() {
        return settingsService.get();
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/settings")
    public SettingsDto updateSettings(@RequestBody SettingsRequest request) {
        return settingsService.update(request);
    }

    /** Active users, for choosing who holds an asset. */
    @GetMapping("/custodians")
    public List<java.util.Map<String, Object>> custodians() {
        return userRepository.findAll().stream().filter(u -> u.isActive())
                .map(u -> java.util.Map.<String, Object>of("id", u.getId(), "name", u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername()))
                .sorted(java.util.Comparator.comparing((java.util.Map<String, Object> m) -> String.valueOf(m.get("name")).toLowerCase())).toList();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/bulk")
    public BulkResult bulk(@Valid @RequestBody BulkRequest request) {
        return bulkService.run(request);
    }

    // ---- verification rounds ----

    @GetMapping("/verifications")
    public List<VerificationDto> verifications() {
        return verificationService.list();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/verifications")
    public VerificationDto startVerification(@Valid @RequestBody VerificationRequest request) {
        return verificationService.start(request);
    }

    @GetMapping("/verifications/{id}")
    public VerificationDto verification(@PathVariable Long id) {
        return verificationService.get(id);
    }

    @GetMapping("/verifications/{id}/lines")
    public List<VerificationLineDto> verificationLines(@PathVariable Long id) {
        return verificationService.lines(id);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/verifications/{id}/lines/{lineId}")
    public VerificationLineDto markLine(@PathVariable Long id, @PathVariable Long lineId, @Valid @RequestBody MarkRequest request) {
        return verificationService.mark(id, lineId, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/verifications/{id}/lines/{lineId}/write-off")
    public VerificationLineDto writeOffLine(@PathVariable Long id, @PathVariable Long lineId) {
        return verificationService.writeOff(id, lineId);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/verifications/{id}/mark-rest")
    public VerificationDto markRest(@PathVariable Long id, @Valid @RequestBody MarkRestRequest request) {
        return verificationService.markRest(id, request.result());
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/verifications/{id}/close")
    public VerificationDto closeVerification(@PathVariable Long id, @RequestBody(required = false) CloseVerificationRequest request) {
        return verificationService.close(id, request != null && Boolean.TRUE.equals(request.applyLocations()));
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/verifications/{id}")
    public void deleteVerification(@PathVariable Long id) {
        verificationService.delete(id);
    }

    /** Received purchase order lines that can be turned into assets. */
    @GetMapping("/purchase-lines")
    public List<PurchaseLineDto> purchaseLines() {
        return purchaseService.purchaseLines();
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/from-purchase")
    public List<AssetDto> fromPurchase(@Valid @RequestBody FromPurchaseRequest request) {
        return purchaseService.register(request);
    }

    @GetMapping("/{id}")
    public AssetDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/depreciation")
    public List<DepreciationEntryDto> depreciation(@PathVariable Long id) {
        return service.depreciationOf(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping
    public AssetDto create(@Valid @RequestBody AssetRequest request) {
        return service.create(request);
    }

    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public AssetDto update(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/transfers")
    public List<TransferDto> transfers(@PathVariable Long id) {
        return service.transfersOf(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/transfer")
    public AssetDto transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return service.transfer(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/dispose")
    public AssetDto dispose(@PathVariable Long id, @Valid @RequestBody DisposeRequest request) {
        return service.dispose(id, request);
    }

    @GetMapping("/{id}/events")
    public List<EventDto> events(@PathVariable Long id) {
        service.get(id);
        return historyService.events(id).stream().map(EventDto::from).toList();
    }

    @GetMapping("/{id}/adjustments")
    public List<AdjustmentDto> adjustments(@PathVariable Long id) {
        return valueService.adjustmentsOf(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/improvement")
    public AssetDto improvement(@PathVariable Long id, @Valid @RequestBody ImprovementRequest request) {
        return valueService.improve(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/impairment")
    public AssetDto impairment(@PathVariable Long id, @Valid @RequestBody ImpairmentRequest request) {
        return valueService.impair(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/terms")
    public AssetDto terms(@PathVariable Long id, @Valid @RequestBody TermsRequest request) {
        return valueService.changeTerms(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/depreciation/reverse")
    public AssetDto reverseDepreciation(@PathVariable Long id, @RequestBody(required = false) ReasonRequest request) {
        return valueService.reverseLastDepreciation(id, request != null ? request.reason() : null);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/undo-disposal")
    public AssetDto undoDisposal(@PathVariable Long id, @RequestBody(required = false) ReasonRequest request) {
        return valueService.undoDisposal(id, request != null ? request.reason() : null);
    }

    @GetMapping("/{id}/custody")
    public List<CustodyDto> custody(@PathVariable Long id) {
        service.get(id);
        return custodyService.historyOf(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/check-out")
    public AssetDto checkOut(@PathVariable Long id, @RequestBody CheckOutRequest request) {
        return actionService.checkOut(id, request);
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/check-in")
    public AssetDto checkIn(@PathVariable Long id, @RequestBody(required = false) CheckInRequest request) {
        return actionService.checkIn(id, request != null ? request : new CheckInRequest(null, null, null));
    }

    /** A handover sheet (PDF) to print and sign. */
    @GetMapping("/{id}/handover-pdf")
    public ResponseEntity<byte[]> handoverPdf(@PathVariable Long id) {
        AssetDto asset = service.get(id);
        byte[] pdf = custodyService.handoverPdf(service.entity(id), asset.categoryName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("handover-" + asset.code() + ".pdf").build().toString())
                .body(pdf);
    }

    @GetMapping("/{id}/files")
    public List<AttachmentDto> files(@PathVariable Long id) {
        return attachmentService.list(id);
    }

    @PreAuthorize(MANAGE)
    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentDto addFile(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        return attachmentService.add(id, file);
    }

    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<byte[]> file(@PathVariable Long id, @PathVariable Long fileId) {
        AssetAttachmentService.FileData f = attachmentService.file(id, fileId);
        boolean inline = f.contentType().startsWith("image/") || f.contentType().equals("application/pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(f.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.data());
    }

    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/files/{fileId}")
    public void deleteFile(@PathVariable Long id, @PathVariable Long fileId) {
        attachmentService.delete(id, fileId);
    }

    /** Charges the monthly depreciation of every active asset up to the date (default: end of last month). */
    @PreAuthorize(MANAGE)
    @PostMapping("/depreciation/run")
    public DepreciationRunResult runDepreciation(@RequestBody(required = false) RunDepreciationRequest request) {
        return depreciationService.run(request != null ? request.upTo() : null);
    }
}
