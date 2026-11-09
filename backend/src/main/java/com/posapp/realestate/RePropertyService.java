package com.posapp.realestate;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.dto.RealEstateDtos.LeadRow;
import com.posapp.realestate.dto.RealEstateDtos.Option;
import com.posapp.realestate.dto.RealEstateDtos.PhotoDto;
import com.posapp.realestate.dto.RealEstateDtos.PropertyDto;
import com.posapp.realestate.dto.RealEstateDtos.PropertyRow;
import com.posapp.realestate.dto.RealEstateRequests.PropertyRequest;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The property register: listings, their status, photos and who looks after them. */
@Service
public class RePropertyService {

    public static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    public static final int MAX_PHOTOS = 12;
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final RePropertyRepository repository;
    private final RePropertyPhotoRepository photoRepository;
    private final RePhotoBlobRepository blobRepository;
    private final ReViewingRepository viewingRepository;
    private final ReLeadRepository leadRepository;
    private final UserRepository userRepository;
    private final ReSettingsService settings;
    private final RealEstateAccess access;
    private final AuditLogService auditLogService;
    private final ReDocumentService documentService;
    private final ReValuationService valuationService;
    private final ReDealRepository dealRepository;

    public RePropertyService(RePropertyRepository repository, RePropertyPhotoRepository photoRepository, RePhotoBlobRepository blobRepository,
                             ReViewingRepository viewingRepository, ReLeadRepository leadRepository, ReDealRepository dealRepository, UserRepository userRepository, ReSettingsService settings,
                             RealEstateAccess access, AuditLogService auditLogService, ReDocumentService documentService, ReValuationService valuationService) {
        this.documentService = documentService;
        this.valuationService = valuationService;
        this.repository = repository;
        this.photoRepository = photoRepository;
        this.blobRepository = blobRepository;
        this.viewingRepository = viewingRepository;
        this.leadRepository = leadRepository;
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
        this.settings = settings;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ helpers

    private static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    private static void positive(BigDecimal v, String what) {
        if (v != null && (v.signum() < 0 || v.compareTo(new BigDecimal("100000000000000")) >= 0)) throw ApiException.badRequest(what + " must be zero or more, and a sensible amount.");
    }

    private static void nonNegative(Integer v, int max, String what) {
        if (v != null && (v < 0 || v > max)) throw ApiException.badRequest(what + " must be between 0 and " + max + ".");
    }

    public ReProperty find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Property not found."));
    }

    @Transactional(readOnly = true)
    public List<Option> agents() {
        access.on();
        return userRepository.findByActiveTrue().stream().map(u -> new Option(u.getId(), RealEstateAccess.name(u)))
                .sorted(Comparator.comparing(Option::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    PropertyRow row(ReProperty p, Map<Long, List<RePropertyPhoto>> photos, Map<Long, Long> leadCount, Map<Long, Long> upcoming, LocalDate today) {
        List<RePropertyPhoto> ph = photos.getOrDefault(p.getId(), List.of());
        Long cover = ph.stream().filter(RePropertyPhoto::isCover).map(RePropertyPhoto::getId).findFirst().orElse(ph.isEmpty() ? null : ph.get(0).getId());
        long days = p.getListedDate() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(p.getListedDate(), today));
        return new PropertyRow(p.getId(), p.getCode(), p.getTitle(), p.getType(), p.getPurpose(), p.getStatus(), p.getArea(), p.getBedrooms(), p.getBathrooms(), p.getSizeSqm(),
                p.getSalePrice(), p.getRentPrice(), p.getAgentName(), cover, ph.size(), p.getListedDate(), days, p.isFeatured(), p.isPublicListing(), leadCount.getOrDefault(p.getId(), 0L).intValue(),
                upcoming.getOrDefault(p.getId(), 0L).intValue());
    }

    List<PropertyRow> rows(List<ReProperty> list) {
        LocalDate today = LocalDate.now();
        Map<Long, List<RePropertyPhoto>> photos = list.isEmpty() ? Map.of()
                : photoRepository.findByPropertyIdIn(list.stream().map(ReProperty::getId).toList()).stream()
                .sorted(Comparator.comparing(RePropertyPhoto::getSortOrder).thenComparing(RePropertyPhoto::getId)).collect(Collectors.groupingBy(RePropertyPhoto::getPropertyId));
        Map<Long, Long> leads = leadRepository.findAll().stream().filter(l -> l.getPropertyId() != null && l.getStatus() != LeadStatus.WON && l.getStatus() != LeadStatus.LOST)
                .collect(Collectors.groupingBy(ReLead::getPropertyId, Collectors.counting()));
        Instant now = Instant.now();
        Map<Long, Long> upcoming = viewingRepository.findAll().stream().filter(v -> v.getStatus() == ViewingStatus.SCHEDULED && v.getEndAt().isAfter(now))
                .collect(Collectors.groupingBy(ReViewing::getPropertyId, Collectors.counting()));
        return list.stream().map(p -> row(p, photos, leads, upcoming, today)).toList();
    }

    // ------------------------------------------------------------------ list and detail

    @Transactional(readOnly = true)
    public List<PropertyRow> list(String q, PropertyStatus status, PropertyType type, ListingPurpose purpose, Long agentId, String area, BigDecimal minPrice, BigDecimal maxPrice,
                                  Integer bedrooms) {
        access.on();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<ReProperty> list = repository.findAllByOrderByIdDesc().stream()
                .filter(p -> status == null || p.getStatus() == status)
                .filter(p -> type == null || p.getType() == type)
                .filter(p -> purpose == null || p.getPurpose() == purpose || p.getPurpose() == ListingPurpose.BOTH && purpose != null)
                .filter(p -> agentId == null || agentId.equals(p.getAgentUserId()))
                .filter(p -> area == null || area.isBlank() || (p.getArea() != null && p.getArea().equalsIgnoreCase(area.trim())))
                .filter(p -> bedrooms == null || (p.getBedrooms() != null && p.getBedrooms() >= bedrooms))
                .filter(p -> inRange(p, purpose, minPrice, maxPrice))
                .filter(p -> needle.isEmpty() || (p.getCode() + " " + p.getTitle() + " " + nz(p.getArea()) + " " + nz(p.getAddress()) + " " + nz(p.getTitleReference()) + " " + nz(p.getOwnerName()))
                        .toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        return rows(list);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** The price a buyer or tenant compares: rent for a rental search, the sale price otherwise. */
    private static boolean inRange(ReProperty p, ListingPurpose purpose, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return true;
        BigDecimal price = purpose == ListingPurpose.RENT ? p.getRentPrice() : p.getSalePrice() != null ? p.getSalePrice() : p.getRentPrice();
        if (price == null) return false;
        return (min == null || price.compareTo(min) >= 0) && (max == null || price.compareTo(max) <= 0);
    }

    @Transactional(readOnly = true)
    public PropertyDto get(Actor a, Long id) {
        access.on();
        return dto(a, find(id));
    }

    PropertyDto dto(Actor a, ReProperty p) {
        List<PhotoDto> photos = photoRepository.findByPropertyIdOrderBySortOrderAscIdAsc(p.getId()).stream().map(x -> new PhotoDto(x.getId(), x.getFileName(), x.getSizeBytes(), x.isCover())).toList();
        Map<Long, ReLead> leadsById = leadRepository.findAll().stream().collect(Collectors.toMap(ReLead::getId, Function.identity()));
        List<com.posapp.realestate.dto.RealEstateDtos.ViewingDto> viewings = viewingRepository.findByPropertyIdOrderByStartAtDesc(p.getId()).stream()
                .map(v -> ReViewingService.dto(v, p, leadsById.get(v.getLeadId()), a.manager())).toList();
        List<LeadRow> leads = ReLeadService.rows(leadRepository.findAll().stream().filter(l -> p.getId().equals(l.getPropertyId())).toList(), Map.of(p.getId(), p),
                viewingRepository.findAll(), settings.entity().getStaleLeadDays());
        return new PropertyDto(p.getId(), p.getCode(), p.getTitle(), p.getType(), p.getPurpose(), p.getStatus(), p.getDescription(), p.getArea(), p.getAddress(), p.getTitleReference(),
                p.getLatitude(), p.getLongitude(), p.getBedrooms(), p.getBathrooms(), p.getParkingSpaces(), p.getSizeSqm(), p.getPlotSqm(), p.getYearBuilt(), p.isFurnished(), p.getFeatures(),
                p.getSalePrice(), p.getRentPrice(), p.isNegotiable(), p.isFeatured(), p.isPublicListing(), p.getOwnerName(), p.getOwnerPhone(), p.getOwnerEmail(), p.getAgentUserId(), p.getAgentName(),
                p.getListedDate(), p.getCreatedByName(), p.getCreatedAt(), photos, viewings, leads, a.manager());
    }

    // ------------------------------------------------------------------ create and change

    private void apply(ReProperty p, PropertyRequest r, boolean creating) {
        // A request that carries a title is the whole form (an empty box clears the field); one without is a partial change and leaves what it does not mention alone.
        boolean full = creating || r.title() != null;
        if (full) {
            String t = clean(r.title(), 200, "The title");
            if (t == null) throw ApiException.badRequest("Give the property a title.");
            p.setTitle(t);
        }
        if (r.type() != null) p.setType(r.type());
        if (r.purpose() != null) p.setPurpose(r.purpose());
        if (full || r.description() != null) p.setDescription(clean(r.description(), 4000, "The description"));
        if (full || r.area() != null) p.setArea(clean(r.area(), 120, "The area"));
        if (full || r.address() != null) p.setAddress(clean(r.address(), 300, "The address"));
        if (full || r.titleReference() != null) p.setTitleReference(clean(r.titleReference(), 120, "The title reference"));
        if (full || r.latitude() != null) {
            if (r.latitude() != null && (r.latitude() < -90 || r.latitude() > 90)) throw ApiException.badRequest("Latitude must be between -90 and 90.");
            p.setLatitude(r.latitude());
        }
        if (full || r.longitude() != null) {
            if (r.longitude() != null && (r.longitude() < -180 || r.longitude() > 180)) throw ApiException.badRequest("Longitude must be between -180 and 180.");
            p.setLongitude(r.longitude());
        }
        nonNegative(r.bedrooms(), 100, "Bedrooms");
        nonNegative(r.bathrooms(), 100, "Bathrooms");
        nonNegative(r.parkingSpaces(), 1000, "Parking spaces");
        if (r.yearBuilt() != null && (r.yearBuilt() < 1800 || r.yearBuilt() > LocalDate.now().getYear() + 5)) throw ApiException.badRequest("Check the year built.");
        positive(r.sizeSqm(), "The size");
        positive(r.plotSqm(), "The plot size");
        positive(r.salePrice(), "The sale price");
        positive(r.rentPrice(), "The rent");
        if (full || r.bedrooms() != null) p.setBedrooms(r.bedrooms());
        if (full || r.bathrooms() != null) p.setBathrooms(r.bathrooms());
        if (full || r.parkingSpaces() != null) p.setParkingSpaces(r.parkingSpaces());
        if (full || r.sizeSqm() != null) p.setSizeSqm(r.sizeSqm());
        if (full || r.plotSqm() != null) p.setPlotSqm(r.plotSqm());
        if (full || r.yearBuilt() != null) p.setYearBuilt(r.yearBuilt());
        if (r.furnished() != null) p.setFurnished(r.furnished());
        if (full || r.features() != null) p.setFeatures(clean(r.features(), 1000, "The features"));
        if (full || r.salePrice() != null) p.setSalePrice(r.salePrice());
        if (full || r.rentPrice() != null) p.setRentPrice(r.rentPrice());
        if (r.negotiable() != null) p.setNegotiable(r.negotiable());
        if (r.featured() != null) p.setFeatured(r.featured());
        if (r.publicListing() != null) p.setPublicListing(r.publicListing());
        if (full || r.ownerName() != null) p.setOwnerName(clean(r.ownerName(), 200, "The owner's name"));
        if (full || r.ownerPhone() != null) p.setOwnerPhone(clean(r.ownerPhone(), 40, "The owner's phone"));
        if (full || r.ownerEmail() != null) p.setOwnerEmail(clean(r.ownerEmail(), 200, "The owner's e-mail"));
        if (r.agentUserId() != null) {
            AppUser u = userRepository.findById(r.agentUserId()).filter(AppUser::isActive).orElseThrow(() -> ApiException.badRequest("Agent not found."));
            p.setAgentUserId(u.getId());
            p.setAgentName(RealEstateAccess.name(u));
        }
        ListingPurpose purpose = p.getPurpose();
        if ((purpose == ListingPurpose.SALE || purpose == ListingPurpose.BOTH) && p.getStatus() == PropertyStatus.AVAILABLE && p.getSalePrice() == null) {
            throw ApiException.badRequest("A property for sale needs a sale price before it can be listed.");
        }
        if ((purpose == ListingPurpose.RENT || purpose == ListingPurpose.BOTH) && p.getStatus() == PropertyStatus.AVAILABLE && p.getRentPrice() == null) {
            throw ApiException.badRequest("A property for rent needs a rent before it can be listed.");
        }
    }

    @Transactional
    public PropertyDto create(Actor a, PropertyRequest r) {
        access.on();
        ReProperty p = new ReProperty();
        p.setCode(settings.nextCode("PR"));
        p.setCreatedByName(RealEstateAccess.name(a.user()));
        if (r.agentUserId() == null) {
            p.setAgentUserId(a.user().getId());
            p.setAgentName(RealEstateAccess.name(a.user()));
        }
        apply(p, r, true);
        repository.save(p);
        auditLogService.record("RE_PROPERTY_CREATED", "Added property " + p.getCode() + " " + p.getTitle(), "ReProperty", p.getId());
        return dto(a, p);
    }

    @Transactional
    public PropertyDto update(Actor a, Long id, PropertyRequest r) {
        access.on();
        ReProperty p = find(id);
        apply(p, r, false);
        repository.save(p);
        auditLogService.record("RE_PROPERTY_UPDATED", "Updated property " + p.getCode(), "ReProperty", p.getId());
        return dto(a, p);
    }

    /** List a property (AVAILABLE), take it off the market (WITHDRAWN), or mark it RESERVED / SOLD / LET by hand. Deals do this themselves from phase 2. */
    @Transactional
    public PropertyDto setStatus(Actor a, Long id, PropertyStatus status) {
        access.on();
        if (status == null) throw ApiException.badRequest("Choose a status.");
        ReProperty p = find(id);
        if (p.getStatus() == status) return dto(a, p);
        if (status == PropertyStatus.AVAILABLE) {
            ListingPurpose purpose = p.getPurpose();
            if ((purpose == ListingPurpose.SALE || purpose == ListingPurpose.BOTH) && p.getSalePrice() == null) throw ApiException.badRequest("Add a sale price before listing the property.");
            if ((purpose == ListingPurpose.RENT || purpose == ListingPurpose.BOTH) && p.getRentPrice() == null) throw ApiException.badRequest("Add a rent before listing the property.");
            if (p.getListedDate() == null) p.setListedDate(LocalDate.now());
        }
        if (status == PropertyStatus.SOLD && p.getPurpose() == ListingPurpose.RENT) throw ApiException.badRequest("A property for rent cannot be marked sold. Mark it let.");
        if (status == PropertyStatus.LET && p.getPurpose() == ListingPurpose.SALE) throw ApiException.badRequest("A property for sale cannot be marked let. Mark it sold.");
        if (dealRepository.countByPropertyIdAndStageIn(id, java.util.EnumSet.of(DealStage.RESERVED, DealStage.AGREEMENT)) > 0) {
            throw ApiException.conflict("This property is held by a sales deal. Complete or cancel the deal and the status follows.");
        }
        PropertyStatus before = p.getStatus();
        p.setStatus(status);
        repository.save(p);
        auditLogService.record("RE_PROPERTY_STATUS", "Property " + p.getCode() + " changed from " + before + " to " + status, "ReProperty", p.getId());
        return dto(a, p);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        ReProperty p = find(id);
        if (viewingRepository.countByPropertyId(id) > 0 || leadRepository.countByPropertyId(id) > 0 || dealRepository.countByPropertyId(id) > 0) {
            throw ApiException.conflict("This property has leads, viewings or deals. Take it off the market (withdrawn) instead of deleting it.");
        }
        if (p.getStatus() != PropertyStatus.DRAFT && p.getStatus() != PropertyStatus.WITHDRAWN) {
            throw ApiException.conflict("Only a draft or a withdrawn property can be deleted.");
        }
        for (RePropertyPhoto ph : photoRepository.findByPropertyIdOrderBySortOrderAscIdAsc(id)) {
            blobRepository.deleteByPhotoId(ph.getId());
            photoRepository.delete(ph);
        }
        valuationService.deleteFor(id);
        documentService.deleteFor(DocSubject.PROPERTY, id);
        repository.delete(p);
        auditLogService.record("RE_PROPERTY_DELETED", "Deleted property " + p.getCode() + " " + p.getTitle());
    }

    // ------------------------------------------------------------------ photos

    @Transactional
    public PropertyDto addPhoto(Actor a, Long id, MultipartFile file) throws IOException {
        access.on();
        ReProperty p = find(id);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a picture to add.");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!PHOTO_TYPES.contains(type)) throw ApiException.badRequest("Photos must be JPEG, PNG or WebP pictures.");
        if (file.getSize() > MAX_PHOTO_BYTES) throw ApiException.badRequest("A photo can be at most 5 MB.");
        byte[] data = file.getBytes();
        if (!looksLikeImage(data, type)) throw ApiException.badRequest("That file is not a valid " + type.substring(6).toUpperCase(Locale.ROOT) + " picture.");
        long count = photoRepository.countByPropertyId(id);
        if (count >= MAX_PHOTOS) throw ApiException.badRequest("A property can have at most " + MAX_PHOTOS + " photos.");
        RePropertyPhoto ph = new RePropertyPhoto();
        ph.setPropertyId(id);
        String name = file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n]", "_");
        ph.setFileName(name.length() > 200 ? name.substring(name.length() - 200) : name);
        ph.setContentType(type);
        ph.setSizeBytes(data.length);
        ph.setCover(count == 0);
        ph.setSortOrder((int) count);
        photoRepository.save(ph);
        RePhotoBlob b = new RePhotoBlob();
        b.setPhotoId(ph.getId());
        b.setData(data);
        blobRepository.save(b);
        auditLogService.record("RE_PHOTO_ADDED", "Added a photo to property " + p.getCode(), "ReProperty", id);
        return dto(a, p);
    }

    /** Checks the first bytes match the picture type, so a renamed file is not stored as a photo. */
    static boolean looksLikeImage(byte[] d, String type) {
        if (d.length < 12) return false;
        return switch (type) {
            case "image/jpeg" -> (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8;
            case "image/png" -> (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
            case "image/webp" -> d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
            default -> false;
        };
    }

    public record Photo(String fileName, String contentType, byte[] data) {
    }

    @Transactional(readOnly = true)
    public Photo photo(Long propertyId, Long photoId) {
        access.on();
        RePropertyPhoto ph = photoRepository.findById(photoId).filter(x -> x.getPropertyId().equals(propertyId)).orElseThrow(() -> ApiException.notFound("Photo not found."));
        RePhotoBlob b = blobRepository.findByPhotoId(photoId).orElseThrow(() -> ApiException.notFound("Photo not found."));
        return new Photo(ph.getFileName(), ph.getContentType(), b.getData());
    }

    @Transactional
    public PropertyDto setCover(Actor a, Long propertyId, Long photoId) {
        access.on();
        ReProperty p = find(propertyId);
        List<RePropertyPhoto> all = photoRepository.findByPropertyIdOrderBySortOrderAscIdAsc(propertyId);
        if (all.stream().noneMatch(x -> x.getId().equals(photoId))) throw ApiException.notFound("Photo not found.");
        all.forEach(x -> x.setCover(x.getId().equals(photoId)));
        photoRepository.saveAll(all);
        return dto(a, p);
    }

    @Transactional
    public PropertyDto deletePhoto(Actor a, Long propertyId, Long photoId) {
        access.on();
        ReProperty p = find(propertyId);
        RePropertyPhoto ph = photoRepository.findById(photoId).filter(x -> x.getPropertyId().equals(propertyId)).orElseThrow(() -> ApiException.notFound("Photo not found."));
        blobRepository.deleteByPhotoId(photoId);
        photoRepository.delete(ph);
        List<RePropertyPhoto> rest = photoRepository.findByPropertyIdOrderBySortOrderAscIdAsc(propertyId);
        for (int i = 0; i < rest.size(); i++) {
            rest.get(i).setSortOrder(i);
            rest.get(i).setCover(ph.isCover() ? i == 0 : rest.get(i).isCover());
        }
        photoRepository.saveAll(rest);
        return dto(a, p);
    }
}
