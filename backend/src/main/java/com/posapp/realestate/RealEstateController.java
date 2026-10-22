package com.posapp.realestate;

import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.RePropertyService.Photo;
import com.posapp.realestate.dto.RealEstateDealDtos.*;
import com.posapp.realestate.dto.RealEstateDealRequests.*;
import com.posapp.realestate.dto.RealEstateDtos.*;
import com.posapp.realestate.dto.RealEstateRequests.*;
import com.posapp.security.AppUserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /api/realestate}: properties for sale or rent, the people who ask about them, and the viewings. Operations viewers read; Operations managers and
 * admins change things; the settings are for admins.
 */
@RestController
@RequestMapping("/api/realestate")
public class RealEstateController {

    private static final String VIEW = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_VIEW') or hasAuthority('AREA_OPERATIONS_MANAGE')";
    private static final String MANAGE = "hasRole('ADMIN') or hasAuthority('AREA_OPERATIONS_MANAGE')";

    private final ReSettingsService settingsService;
    private final RePropertyService propertyService;
    private final ReLeadService leadService;
    private final ReViewingService viewingService;
    private final ReOverviewService overviewService;
    private final ReDealService dealService;

    public RealEstateController(ReSettingsService settingsService, RePropertyService propertyService, ReLeadService leadService, ReViewingService viewingService,
                                ReOverviewService overviewService, ReDealService dealService) {
        this.settingsService = settingsService;
        this.propertyService = propertyService;
        this.leadService = leadService;
        this.viewingService = viewingService;
        this.overviewService = overviewService;
        this.dealService = dealService;
    }

    static Actor actor(AppUserPrincipal me) {
        Set<String> auth = me.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        boolean admin = auth.contains("ROLE_ADMIN");
        boolean manager = admin || auth.contains("AREA_OPERATIONS_MANAGE");
        boolean viewer = manager || auth.contains("AREA_OPERATIONS_VIEW");
        return new Actor(me.getUser(), admin, viewer, manager);
    }

    // ---- settings, people, overview

    @GetMapping("/settings")
    @PreAuthorize(VIEW)
    public SettingsDto settings() {
        return settingsService.get();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto saveSettings(@RequestBody SettingsRequest r) {
        return settingsService.update(r);
    }

    @GetMapping("/agents")
    @PreAuthorize(VIEW)
    public List<Option> agents() {
        return propertyService.agents();
    }

    @GetMapping("/overview")
    @PreAuthorize(VIEW)
    public Overview overview(@AuthenticationPrincipal AppUserPrincipal me) {
        return overviewService.overview(actor(me));
    }

    // ---- properties

    @GetMapping("/properties")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<PropertyRow>> properties(@RequestParam(required = false) String q, @RequestParam(required = false) PropertyStatus status, @RequestParam(required = false) PropertyType type,
                                        @RequestParam(required = false) ListingPurpose purpose, @RequestParam(required = false) Long agentId, @RequestParam(required = false) String area,
                                        @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                                        @RequestParam(required = false) Integer bedrooms, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return RePaging.of(propertyService.list(q, status, type, purpose, agentId, area, minPrice, maxPrice, bedrooms), page, size);
    }

    @GetMapping("/properties/{id}")
    @PreAuthorize(VIEW)
    public PropertyDto property(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return propertyService.get(actor(me), id);
    }

    @PostMapping("/properties")
    @PreAuthorize(MANAGE)
    public PropertyDto createProperty(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody PropertyRequest r) {
        return propertyService.create(actor(me), r);
    }

    @PutMapping("/properties/{id}")
    @PreAuthorize(MANAGE)
    public PropertyDto updateProperty(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody PropertyRequest r) {
        return propertyService.update(actor(me), id, r);
    }

    @PutMapping("/properties/{id}/status")
    @PreAuthorize(MANAGE)
    public PropertyDto propertyStatus(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody PropertyStatusRequest r) {
        return propertyService.setStatus(actor(me), id, r.status());
    }

    @DeleteMapping("/properties/{id}")
    @PreAuthorize(MANAGE)
    public void deleteProperty(@PathVariable Long id) {
        propertyService.delete(id);
    }

    @PostMapping(value = "/properties/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    public PropertyDto addPhoto(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestPart(value = "file", required = false) MultipartFile file)
            throws IOException {
        return propertyService.addPhoto(actor(me), id, file);
    }

    @GetMapping("/properties/{id}/photos/{photoId}")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> photo(@PathVariable Long id, @PathVariable Long photoId) {
        Photo p = propertyService.photo(id, photoId);
        MediaType type;
        try {
            type = MediaType.parseMediaType(p.contentType());
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(p.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(type)
                .contentLength(p.data().length)
                .body(p.data());
    }

    @PutMapping("/properties/{id}/photos/{photoId}/cover")
    @PreAuthorize(MANAGE)
    public PropertyDto cover(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long photoId) {
        return propertyService.setCover(actor(me), id, photoId);
    }

    @DeleteMapping("/properties/{id}/photos/{photoId}")
    @PreAuthorize(MANAGE)
    public PropertyDto deletePhoto(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long photoId) {
        return propertyService.deletePhoto(actor(me), id, photoId);
    }

    // ---- leads

    @GetMapping("/leads")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<LeadRow>> leads(@RequestParam(required = false) String q, @RequestParam(required = false) LeadStatus status, @RequestParam(required = false) LeadInterest interest,
                               @RequestParam(required = false) Long assignedId, @RequestParam(required = false) Long propertyId, @RequestParam(required = false) Boolean dueOnly,
                               @RequestParam(required = false) Boolean openOnly, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return RePaging.of(leadService.list(q, status, interest, assignedId, propertyId, dueOnly, openOnly), page, size);
    }

    @GetMapping("/leads/{id}")
    @PreAuthorize(VIEW)
    public LeadDto lead(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return leadService.get(actor(me), id);
    }

    @PostMapping("/leads")
    @PreAuthorize(MANAGE)
    public LeadDto createLead(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody LeadRequest r) {
        return leadService.create(actor(me), r);
    }

    @PutMapping("/leads/{id}")
    @PreAuthorize(MANAGE)
    public LeadDto updateLead(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody LeadRequest r) {
        return leadService.update(actor(me), id, r);
    }

    @PutMapping("/leads/{id}/status")
    @PreAuthorize(MANAGE)
    public LeadDto leadStatus(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody LeadStatusRequest r) {
        return leadService.setStatus(actor(me), id, r.status(), r.lostReason());
    }

    @PostMapping("/leads/{id}/notes")
    @PreAuthorize(MANAGE)
    public LeadDto addNote(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody NoteRequest r) {
        return leadService.addNote(actor(me), id, r);
    }

    @DeleteMapping("/leads/{id}/notes/{noteId}")
    @PreAuthorize(MANAGE)
    public LeadDto deleteNote(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long noteId) {
        return leadService.deleteNote(actor(me), id, noteId);
    }

    @PostMapping("/leads/{id}/convert")
    @PreAuthorize(MANAGE)
    public LeadDto convert(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return leadService.convert(actor(me), id);
    }

    @GetMapping("/leads/{id}/matches")
    @PreAuthorize(VIEW)
    public List<Match> matches(@PathVariable Long id) {
        return leadService.matches(id);
    }

    @DeleteMapping("/leads/{id}")
    @PreAuthorize(MANAGE)
    public void deleteLead(@PathVariable Long id) {
        leadService.delete(id);
    }

    // ---- viewings

    @GetMapping("/viewings")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<ViewingDto>> viewings(@AuthenticationPrincipal AppUserPrincipal me, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) Long agentId,
                                     @RequestParam(required = false) ViewingStatus status, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return RePaging.of(viewingService.list(actor(me), from, to, agentId, status), page, size);
    }

    @PostMapping("/viewings")
    @PreAuthorize(MANAGE)
    public ViewingDto schedule(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody ViewingRequest r) {
        return viewingService.schedule(actor(me), r);
    }

    @PutMapping("/viewings/{id}")
    @PreAuthorize(MANAGE)
    public ViewingDto reschedule(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ViewingRequest r) {
        return viewingService.reschedule(actor(me), id, r);
    }

    @PutMapping("/viewings/{id}/status")
    @PreAuthorize(MANAGE)
    public ViewingDto viewingStatus(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody ViewingStatusRequest r) {
        return viewingService.setStatus(actor(me), id, r.status(), r.feedback());
    }

    // ---- sales deals

    @GetMapping("/deals")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<DealRow>> deals(@RequestParam(required = false) String q, @RequestParam(required = false) DealStage stage, @RequestParam(required = false) Long propertyId,
                               @RequestParam(required = false) Long agentId, @RequestParam(required = false) Boolean openOnly, @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        return RePaging.of(dealService.list(q, stage, propertyId, agentId, openOnly), page, size);
    }

    @GetMapping("/deals/{id}")
    @PreAuthorize(VIEW)
    public DealDto deal(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return dealService.get(actor(me), id);
    }

    @PostMapping("/deals")
    @PreAuthorize(MANAGE)
    public DealDto createDeal(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody DealRequest r) {
        return dealService.create(actor(me), r);
    }

    @PutMapping("/deals/{id}")
    @PreAuthorize(MANAGE)
    public DealDto updateDeal(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody DealRequest r) {
        return dealService.update(actor(me), id, r);
    }

    @PostMapping("/deals/{id}/reserve")
    @PreAuthorize(MANAGE)
    public DealDto reserve(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) ReserveRequest r) {
        return dealService.reserve(actor(me), id, r);
    }

    @PostMapping("/deals/{id}/agreement")
    @PreAuthorize(MANAGE)
    public DealDto agreement(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) AgreementRequest r) {
        return dealService.agreement(actor(me), id, r);
    }

    @PostMapping("/deals/{id}/complete")
    @PreAuthorize(MANAGE)
    public DealDto completeDeal(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) CompleteRequest r) {
        return dealService.complete(actor(me), id, r);
    }

    @PostMapping("/deals/{id}/cancel")
    @PreAuthorize(MANAGE)
    public DealDto cancelDeal(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody CancelRequest r) {
        return dealService.cancel(actor(me), id, r);
    }

    @DeleteMapping("/deals/{id}")
    @PreAuthorize(MANAGE)
    public void deleteDeal(@PathVariable Long id) {
        dealService.delete(id);
    }

    @PostMapping("/deals/{id}/milestones")
    @PreAuthorize(MANAGE)
    public DealDto addMilestone(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody MilestoneRequest r) {
        return dealService.addMilestone(actor(me), id, r);
    }

    @PutMapping("/deals/{id}/milestones/{milestoneId}")
    @PreAuthorize(MANAGE)
    public DealDto updateMilestone(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long milestoneId, @RequestBody MilestoneRequest r) {
        return dealService.updateMilestone(actor(me), id, milestoneId, r);
    }

    @DeleteMapping("/deals/{id}/milestones/{milestoneId}")
    @PreAuthorize(MANAGE)
    public DealDto deleteMilestone(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long milestoneId) {
        return dealService.deleteMilestone(actor(me), id, milestoneId);
    }

    @PostMapping("/deals/{id}/plan")
    @PreAuthorize(MANAGE)
    public DealDto plan(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody PlanRequest r) {
        return dealService.plan(actor(me), id, r);
    }

    @PostMapping("/deals/{id}/milestones/{milestoneId}/invoice")
    @PreAuthorize(MANAGE)
    public DealDto invoiceMilestone(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long milestoneId,
                                    @RequestBody(required = false) InvoiceMilestoneRequest r) {
        return dealService.invoiceMilestone(actor(me), id, milestoneId, r);
    }

    @PostMapping("/deals/{id}/milestones/{milestoneId}/issue")
    @PreAuthorize(MANAGE)
    public DealDto issueMilestone(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long milestoneId) {
        return dealService.issueMilestoneInvoice(actor(me), id, milestoneId);
    }

    @PostMapping("/deals/{id}/commissions")
    @PreAuthorize(MANAGE)
    public DealDto addCommission(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody CommissionRequest r) {
        return dealService.addCommission(actor(me), id, r);
    }

    @PutMapping("/deals/{id}/commissions/{commissionId}")
    @PreAuthorize(MANAGE)
    public DealDto updateCommission(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long commissionId, @RequestBody CommissionRequest r) {
        return dealService.updateCommission(actor(me), id, commissionId, r);
    }

    @DeleteMapping("/deals/{id}/commissions/{commissionId}")
    @PreAuthorize(MANAGE)
    public DealDto deleteCommission(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long commissionId) {
        return dealService.deleteCommission(actor(me), id, commissionId);
    }

    @PostMapping("/deals/{id}/commissions/{commissionId}/post")
    @PreAuthorize(MANAGE)
    public DealDto postCommission(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long commissionId,
                                  @RequestBody(required = false) PostCommissionRequest r) {
        return dealService.postCommission(actor(me), id, commissionId, r);
    }

    @PostMapping("/deals/{id}/commissions/{commissionId}/unpost")
    @PreAuthorize(MANAGE)
    public DealDto unpostCommission(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @PathVariable Long commissionId) {
        return dealService.unpostCommission(actor(me), id, commissionId);
    }

    @PostMapping("/deals/{id}/seller")
    @PreAuthorize(MANAGE)
    public DealDto sellerCustomer(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return dealService.sellerCustomer(actor(me), id);
    }

    @PostMapping("/deals/{id}/fee-invoice")
    @PreAuthorize(MANAGE)
    public DealDto feeInvoice(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id, @RequestBody(required = false) InvoiceMilestoneRequest r) {
        return dealService.invoiceFee(actor(me), id, r);
    }
}
