package com.posapp.realestate;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.modules.ModuleKey;
import com.posapp.modules.ModuleService;
import com.posapp.realestate.dto.RealEstatePublicDtos.Card;
import com.posapp.realestate.dto.RealEstatePublicDtos.Detail;
import com.posapp.realestate.dto.RealEstatePublicDtos.EnquiryRequest;
import com.posapp.realestate.dto.RealEstatePublicDtos.EnquiryResult;
import com.posapp.realestate.dto.RealEstatePublicDtos.Info;
import com.posapp.realestate.dto.RealEstatePublicDtos.Page;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The public listings website. Everything here answers "not found" while the Real Estate module or the website switch is off. A visitor sees only properties that are
 * AVAILABLE and ticked "Show on website", and never the owner, the street address or the map position. An enquiry becomes a lead (or a note on the open lead with the same
 * phone number) and is limited three ways: a hidden field bots fill in, a daily limit per phone number or e-mail, and a limit per connection per hour.
 */
@Service
public class RePublicService {

    private static final int MAX_PER_IP_PER_HOUR = 10;

    private final ModuleService moduleService;
    private final ReSettingsService settings;
    private final RePropertyRepository propertyRepository;
    private final RePropertyPhotoRepository photoRepository;
    private final RePropertyService propertyService;
    private final ReLeadRepository leadRepository;
    private final ReLeadNoteRepository noteRepository;
    private final ReEnquiryRepository enquiryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Map<String, Deque<Long>> byIp = new HashMap<>();

    public RePublicService(ModuleService moduleService, ReSettingsService settings, RePropertyRepository propertyRepository, RePropertyPhotoRepository photoRepository,
                           RePropertyService propertyService, ReLeadRepository leadRepository, ReLeadNoteRepository noteRepository, ReEnquiryRepository enquiryRepository,
                           UserRepository userRepository, AuditLogService auditLogService) {
        this.moduleService = moduleService;
        this.settings = settings;
        this.propertyRepository = propertyRepository;
        this.photoRepository = photoRepository;
        this.propertyService = propertyService;
        this.leadRepository = leadRepository;
        this.noteRepository = noteRepository;
        this.enquiryRepository = enquiryRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    /** True when visitors can use the website: the module is active and the website switch is on. */
    @Transactional
    public boolean isOpen() {
        return moduleService.isEnabled(ModuleKey.REAL_ESTATE) && settings.entity().isWebsiteEnabled();
    }

    private ReSettings open() {
        if (!moduleService.isEnabled(ModuleKey.REAL_ESTATE)) throw ApiException.notFound("Not found.");
        ReSettings s = settings.entity();
        if (!s.isWebsiteEnabled()) throw ApiException.notFound("Not found.");
        return s;
    }

    private static boolean shown(ReProperty p) {
        return p.isPublicListing() && p.getStatus() == PropertyStatus.AVAILABLE;
    }

    private ReProperty shownProperty(Long id) {
        return propertyRepository.findById(id).filter(RePublicService::shown).orElseThrow(() -> ApiException.notFound("This property is no longer listed."));
    }

    @Transactional
    public Info info() {
        ReSettings s = open();
        List<ReProperty> shown = propertyRepository.findAll().stream().filter(RePublicService::shown).toList();
        List<String> areas = shown.stream().map(ReProperty::getArea).filter(a -> a != null && !a.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        List<String> types = shown.stream().map(p -> p.getType().name()).distinct().sorted().toList();
        return new Info(s.getWebsiteTitle(), s.getWebsiteIntro(), s.getWebsitePhone(), s.getWebsiteEmail(), areas, types);
    }

    private static BigDecimal priceFor(ReProperty p, ListingPurpose want) {
        if (want == ListingPurpose.SALE) return p.getPurpose() == ListingPurpose.RENT ? null : p.getSalePrice();
        if (want == ListingPurpose.RENT) return p.getPurpose() == ListingPurpose.SALE ? null : p.getRentPrice();
        BigDecimal sale = p.getPurpose() == ListingPurpose.RENT ? null : p.getSalePrice();
        return sale != null ? sale : p.getPurpose() == ListingPurpose.SALE ? null : p.getRentPrice();
    }

    @Transactional(readOnly = true)
    public Page list(ListingPurpose purpose, PropertyType type, String area, Integer bedrooms, BigDecimal minPrice, BigDecimal maxPrice, String q, int page, int size) {
        open();
        int pageSize = Math.min(48, Math.max(1, size));
        int pageNo = Math.max(0, page);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<ReProperty> all = propertyRepository.findAll().stream().filter(RePublicService::shown)
                .filter(p -> purpose == null || p.getPurpose() == purpose || p.getPurpose() == ListingPurpose.BOTH)
                .filter(p -> type == null || p.getType() == type)
                .filter(p -> area == null || area.isBlank() || (p.getArea() != null && p.getArea().equalsIgnoreCase(area.trim())))
                .filter(p -> bedrooms == null || (p.getBedrooms() != null && p.getBedrooms() >= bedrooms))
                .filter(p -> {
                    if (minPrice == null && maxPrice == null) return true;
                    BigDecimal price = priceFor(p, purpose);
                    return price != null && (minPrice == null || price.compareTo(minPrice) >= 0) && (maxPrice == null || price.compareTo(maxPrice) <= 0);
                })
                .filter(p -> needle.isEmpty() || (p.getTitle() + " " + nz(p.getArea()) + " " + nz(p.getFeatures()) + " " + p.getCode()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(ReProperty::isFeatured).reversed()
                        .thenComparing(Comparator.comparing((ReProperty p) -> p.getListedDate() == null ? LocalDate.MIN : p.getListedDate()).reversed())
                        .thenComparing(Comparator.comparing(ReProperty::getId).reversed()))
                .toList();
        List<ReProperty> slice = all.stream().skip((long) pageNo * pageSize).limit(pageSize).toList();
        Map<Long, List<RePropertyPhoto>> photos = slice.isEmpty() ? Map.of() : photoRepository.findByPropertyIdIn(slice.stream().map(ReProperty::getId).toList()).stream()
                .sorted(Comparator.comparing(RePropertyPhoto::getSortOrder).thenComparing(RePropertyPhoto::getId)).collect(Collectors.groupingBy(RePropertyPhoto::getPropertyId));
        List<Card> cards = slice.stream().map(p -> {
            List<RePropertyPhoto> ph = photos.getOrDefault(p.getId(), List.of());
            Long cover = ph.stream().filter(RePropertyPhoto::isCover).map(RePropertyPhoto::getId).findFirst().orElse(ph.isEmpty() ? null : ph.get(0).getId());
            return new Card(p.getId(), p.getCode(), p.getTitle(), p.getType(), p.getPurpose(), p.getArea(), p.getBedrooms(), p.getBathrooms(), p.getSizeSqm(),
                    p.getPurpose() == ListingPurpose.RENT ? null : p.getSalePrice(), p.getPurpose() == ListingPurpose.SALE ? null : p.getRentPrice(), p.isNegotiable(), p.isFeatured(),
                    cover, ph.size(), p.getListedDate());
        }).toList();
        return new Page(cards, all.size(), pageNo, pageSize);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @Transactional(readOnly = true)
    public Detail detail(Long id) {
        open();
        ReProperty p = shownProperty(id);
        List<Long> photoIds = photoRepository.findByPropertyIdOrderBySortOrderAscIdAsc(id).stream()
                .sorted(Comparator.comparing(RePropertyPhoto::isCover).reversed()).map(RePropertyPhoto::getId).toList();
        List<String> features = p.getFeatures() == null ? List.of() : Arrays.stream(p.getFeatures().split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
        return new Detail(p.getId(), p.getCode(), p.getTitle(), p.getType(), p.getPurpose(), p.getDescription(), p.getArea(), p.getBedrooms(), p.getBathrooms(), p.getParkingSpaces(),
                p.getSizeSqm(), p.getPlotSqm(), p.getYearBuilt(), p.isFurnished(), features, p.getPurpose() == ListingPurpose.RENT ? null : p.getSalePrice(),
                p.getPurpose() == ListingPurpose.SALE ? null : p.getRentPrice(), p.isNegotiable(), p.getAgentName(), p.getListedDate(), photoIds);
    }

    @Transactional(readOnly = true)
    public RePropertyService.Photo photo(Long propertyId, Long photoId) {
        open();
        shownProperty(propertyId);
        return propertyService.photo(propertyId, photoId);
    }

    private void throttle(String ip) {
        long now = System.currentTimeMillis();
        synchronized (byIp) {
            if (byIp.size() > 5000) byIp.values().removeIf(d -> d.isEmpty() || now - d.peekLast() > 3_600_000L);
            Deque<Long> d = byIp.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
            while (!d.isEmpty() && now - d.peekFirst() > 3_600_000L) d.pollFirst();
            if (d.size() >= MAX_PER_IP_PER_HOUR) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many enquiries from your connection. Please try again later.");
            d.addLast(now);
        }
    }

    private static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " is too long.");
        return t.isEmpty() ? null : t;
    }

    @Transactional
    public EnquiryResult enquire(Long propertyId, EnquiryRequest r, String ip) {
        ReSettings s = open();
        ReProperty p = shownProperty(propertyId);
        if (r == null) throw ApiException.badRequest("Fill in the form.");
        // A real visitor never sees this field; a bot that fills in every box does. Say thank you and keep nothing.
        if (r.website() != null && !r.website().isBlank()) return new EnquiryResult("Thank you. We have your enquiry and will be in touch soon.");
        String name = clean(r.name(), 200, "The name");
        String phone = clean(r.phone(), 40, "The phone number");
        String email = clean(r.email(), 200, "The e-mail");
        String message = clean(r.message(), 1000, "The message");
        if (name == null) throw ApiException.badRequest("Please tell us your name.");
        if (phone == null && email == null) throw ApiException.badRequest("Give a phone number or an e-mail so we can reach you.");
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest("That e-mail address does not look right.");
        String phoneKey = ReLeadService.digits(phone);
        if (phone != null && (phoneKey == null || phoneKey.length() < 7)) throw ApiException.badRequest("That phone number does not look right.");
        throttle(ip);
        String contactKey = phoneKey != null ? phoneKey : email.toLowerCase(Locale.ROOT);
        java.time.Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        if (enquiryRepository.countByContactKeyAndCreatedAtAfter(contactKey, startOfDay) >= s.getEnquiriesPerPhonePerDay()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "You have already sent several enquiries today. We will contact you soon.");
        }
        String text = "Website enquiry about " + p.getCode() + " " + p.getTitle() + (message == null ? "." : ": " + message);
        if (text.length() > 2000) text = text.substring(0, 2000);

        ReLead lead = phoneKey == null ? null : leadRepository.findByPhoneKey(phoneKey).stream().filter(ReLeadService::open).findFirst().orElse(null);
        LocalDate today = LocalDate.now();
        if (lead == null) {
            lead = new ReLead();
            lead.setName(name);
            lead.setPhone(phone);
            lead.setPhoneKey(phoneKey);
            lead.setEmail(email);
            lead.setInterest(p.getPurpose() == ListingPurpose.SALE ? LeadInterest.BUY : p.getPurpose() == ListingPurpose.RENT ? LeadInterest.RENT : LeadInterest.EITHER);
            lead.setSource(LeadSource.WEBSITE);
            lead.setPropertyId(p.getId());
            lead.setCreatedByName("Website");
            if (p.getAgentUserId() != null) {
                AppUser agent = userRepository.findById(p.getAgentUserId()).filter(AppUser::isActive).orElse(null);
                if (agent != null) {
                    lead.setAssignedUserId(agent.getId());
                    lead.setAssignedName(RealEstateAccess.name(agent));
                }
            }
        } else if (lead.getPropertyId() == null) {
            lead.setPropertyId(p.getId());
        }
        if (lead.getEmail() == null && email != null) lead.setEmail(email);
        if (lead.getNextFollowUp() == null || lead.getNextFollowUp().isAfter(today)) lead.setNextFollowUp(today);
        leadRepository.save(lead);
        ReLeadNote note = new ReLeadNote();
        note.setLeadId(lead.getId());
        note.setKind(NoteKind.NOTE);
        note.setText(text);
        note.setCreatedByName("Website");
        noteRepository.save(note);
        ReEnquiry e = new ReEnquiry();
        e.setPropertyId(p.getId());
        e.setLeadId(lead.getId());
        e.setContactKey(contactKey);
        e.setName(name);
        e.setMessage(message);
        enquiryRepository.save(e);
        auditLogService.record("RE_WEBSITE_ENQUIRY", "Website enquiry from " + name + " about " + p.getCode(), "ReLead", lead.getId());
        return new EnquiryResult("Thank you. We have your enquiry and will be in touch soon.");
    }
}
