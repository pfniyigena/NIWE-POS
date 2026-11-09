package com.posapp.realestate;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.dto.RealEstateDtos.LeadDto;
import com.posapp.realestate.dto.RealEstateDtos.LeadRow;
import com.posapp.realestate.dto.RealEstateDtos.Match;
import com.posapp.realestate.dto.RealEstateDtos.NoteDto;
import com.posapp.realestate.dto.RealEstateRequests.LeadRequest;
import com.posapp.realestate.dto.RealEstateRequests.NoteRequest;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Leads: people asking about a property, their follow-up, and the properties that match what they want. */
@Service
public class ReLeadService {

    private final ReLeadRepository repository;
    private final ReLeadNoteRepository noteRepository;
    private final RePropertyRepository propertyRepository;
    private final ReViewingRepository viewingRepository;
    private final ReDealRepository dealRepository;
    private final ReUnitSaleRepository unitSaleRepository;
    private final ReDocumentService documentService;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final RePropertyService propertyService;
    private final ReSettingsService settings;
    private final RealEstateAccess access;
    private final AuditLogService auditLogService;

    public ReLeadService(ReLeadRepository repository, ReLeadNoteRepository noteRepository, RePropertyRepository propertyRepository, ReViewingRepository viewingRepository,
                         ReDealRepository dealRepository, ReUnitSaleRepository unitSaleRepository, UserRepository userRepository, CustomerRepository customerRepository, RePropertyService propertyService, ReSettingsService settings,
                         RealEstateAccess access, AuditLogService auditLogService, ReDocumentService documentService) {
        this.documentService = documentService;
        this.repository = repository;
        this.noteRepository = noteRepository;
        this.propertyRepository = propertyRepository;
        this.viewingRepository = viewingRepository;
        this.dealRepository = dealRepository;
        this.unitSaleRepository = unitSaleRepository;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.propertyService = propertyService;
        this.settings = settings;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    public ReLead find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Lead not found."));
    }

    static boolean open(ReLead l) {
        return l.getStatus() != LeadStatus.WON && l.getStatus() != LeadStatus.LOST;
    }

    static String digits(String phone) {
        if (phone == null) return null;
        String d = phone.replaceAll("\\D", "");
        if (d.isEmpty()) return null;
        // 0772 123 456 and +256 772 123 456 are the same number: compare the last nine digits
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    /** List rows. A lead is stale when it is open and nothing has happened for the configured number of days. */
    static List<LeadRow> rows(List<ReLead> leads, Map<Long, ReProperty> properties, List<ReViewing> viewings, int staleDays) {
        LocalDate today = LocalDate.now();
        Instant staleBefore = Instant.now().minus(staleDays, ChronoUnit.DAYS);
        Map<Long, Long> viewingCount = viewings.stream().collect(Collectors.groupingBy(ReViewing::getLeadId, Collectors.counting()));
        return leads.stream().map(l -> {
            ReProperty p = l.getPropertyId() == null ? null : properties.get(l.getPropertyId());
            Instant last = l.getLastContactAt() != null ? l.getLastContactAt() : l.getCreatedAt();
            return new LeadRow(l.getId(), l.getName(), l.getPhone(), l.getEmail(), l.getInterest(), l.getStatus(), l.getSource(), l.getBudgetMin(), l.getBudgetMax(), l.getPreferredArea(),
                    l.getPropertyId(), p == null ? null : p.getCode(), l.getAssignedName(), l.getAssignedUserId(), l.getNextFollowUp(),
                    open(l) && l.getNextFollowUp() != null && l.getNextFollowUp().isBefore(today), open(l) && last.isBefore(staleBefore), l.getLastContactAt(), l.getCreatedAt(),
                    viewingCount.getOrDefault(l.getId(), 0L).intValue());
        }).toList();
    }

    private List<LeadRow> rowsOf(List<ReLead> leads) {
        Map<Long, ReProperty> props = propertyRepository.findAll().stream().collect(Collectors.toMap(ReProperty::getId, Function.identity()));
        return rows(leads, props, viewingRepository.findAll(), settings.entity().getStaleLeadDays());
    }

    @Transactional(readOnly = true)
    public List<LeadRow> list(String q, LeadStatus status, LeadInterest interest, Long assignedId, Long propertyId, Boolean dueOnly, Boolean openOnly) {
        access.on();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<ReLead> list = repository.findAllByOrderByIdDesc().stream()
                .filter(l -> status == null || l.getStatus() == status)
                .filter(l -> interest == null || l.getInterest() == interest)
                .filter(l -> assignedId == null || assignedId.equals(l.getAssignedUserId()))
                .filter(l -> propertyId == null || propertyId.equals(l.getPropertyId()))
                .filter(l -> !Boolean.TRUE.equals(openOnly) || open(l))
                .filter(l -> needle.isEmpty() || (l.getName() + " " + (l.getPhone() == null ? "" : l.getPhone()) + " " + (l.getEmail() == null ? "" : l.getEmail())).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        List<LeadRow> rows = rowsOf(list);
        if (Boolean.TRUE.equals(dueOnly)) rows = rows.stream().filter(LeadRow::followUpOverdue).toList();
        return rows;
    }

    @Transactional(readOnly = true)
    public LeadDto get(Actor a, Long id) {
        access.on();
        return dto(a, find(id));
    }

    LeadDto dto(Actor a, ReLead l) {
        ReProperty p = l.getPropertyId() == null ? null : propertyRepository.findById(l.getPropertyId()).orElse(null);
        Map<Long, ReProperty> props = propertyRepository.findAll().stream().collect(Collectors.toMap(ReProperty::getId, Function.identity()));
        List<NoteDto> notes = noteRepository.findByLeadIdOrderByIdDesc(l.getId()).stream().map(n -> new NoteDto(n.getId(), n.getKind(), n.getText(), n.getCreatedByName(), n.getCreatedAt())).toList();
        List<com.posapp.realestate.dto.RealEstateDtos.ViewingDto> viewings = viewingRepository.findByLeadIdOrderByStartAtDesc(l.getId()).stream()
                .map(v -> ReViewingService.dto(v, props.get(v.getPropertyId()), l, a.manager())).toList();
        return new LeadDto(l.getId(), l.getName(), l.getPhone(), l.getEmail(), l.getInterest(), l.getStatus(), l.getSource(), l.getBudgetMin(), l.getBudgetMax(), l.getPreferredArea(),
                l.getPreferredType(), l.getMinBedrooms(), l.getRequirements(), l.getPropertyId(), p == null ? null : p.getCode(), p == null ? null : p.getTitle(), l.getAssignedUserId(),
                l.getAssignedName(), l.getNextFollowUp(), l.getLastContactAt(), l.getLostReason(), l.getCustomerId(), l.getCreatedByName(), l.getCreatedAt(), notes, viewings, a.manager());
    }

    private static String clean(String s, int max, String what) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.badRequest(what + " can be at most " + max + " characters.");
        return t.isEmpty() ? null : t;
    }

    private void apply(ReLead l, LeadRequest r, boolean creating) {
        // A request that carries a name is the whole form (an empty box clears the field); one without is a partial change.
        boolean full = creating || r.name() != null;
        if (full) {
            String n = clean(r.name(), 200, "The name");
            if (n == null) throw ApiException.badRequest("Enter the lead's name.");
            l.setName(n);
        }
        if (full || r.phone() != null) {
            l.setPhone(clean(r.phone(), 40, "The phone number"));
            l.setPhoneKey(digits(l.getPhone()));
        }
        if (full || r.email() != null) {
            String e = clean(r.email(), 200, "The e-mail");
            if (e != null && !e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.badRequest("That e-mail address does not look right.");
            l.setEmail(e);
        }
        if (full && l.getPhone() == null && l.getEmail() == null) throw ApiException.badRequest("Give a phone number or an e-mail so the lead can be reached.");
        if (r.interest() != null) l.setInterest(r.interest());
        if (r.source() != null) l.setSource(r.source());
        for (BigDecimal b : new BigDecimal[] {r.budgetMin(), r.budgetMax()}) {
            if (b != null && (b.signum() < 0 || b.compareTo(new BigDecimal("100000000000000")) >= 0)) throw ApiException.badRequest("A budget must be zero or more, and a sensible amount.");
        }
        if (full || r.budgetMin() != null) l.setBudgetMin(r.budgetMin());
        if (full || r.budgetMax() != null) l.setBudgetMax(r.budgetMax());
        if (l.getBudgetMin() != null && l.getBudgetMax() != null && l.getBudgetMin().compareTo(l.getBudgetMax()) > 0) throw ApiException.badRequest("The lowest budget is above the highest.");
        if (full || r.preferredArea() != null) l.setPreferredArea(clean(r.preferredArea(), 120, "The preferred area"));
        if (full || r.preferredType() != null) l.setPreferredType(r.preferredType());
        if (r.minBedrooms() != null && (r.minBedrooms() < 0 || r.minBedrooms() > 100)) throw ApiException.badRequest("Bedrooms must be between 0 and 100.");
        if (full || r.minBedrooms() != null) l.setMinBedrooms(r.minBedrooms());
        if (full || r.requirements() != null) l.setRequirements(clean(r.requirements(), 1000, "The requirements"));
        if (r.propertyId() != null) propertyRepository.findById(r.propertyId()).orElseThrow(() -> ApiException.badRequest("Property not found."));
        if (full || r.propertyId() != null) l.setPropertyId(r.propertyId());
        if (r.assignedUserId() != null) {
            AppUser u = userRepository.findById(r.assignedUserId()).filter(AppUser::isActive).orElseThrow(() -> ApiException.badRequest("Agent not found."));
            l.setAssignedUserId(u.getId());
            l.setAssignedName(RealEstateAccess.name(u));
        }
        if (full || r.nextFollowUp() != null) l.setNextFollowUp(r.nextFollowUp());
    }

    @Transactional
    public LeadDto create(Actor a, LeadRequest r) {
        access.on();
        ReLead l = new ReLead();
        l.setCreatedByName(RealEstateAccess.name(a.user()));
        apply(l, r, true);
        if (!Boolean.TRUE.equals(r.force()) && l.getPhoneKey() != null) {
            List<ReLead> same = repository.findByPhoneKey(l.getPhoneKey()).stream().filter(ReLeadService::open).toList();
            if (!same.isEmpty()) {
                throw ApiException.conflict("There is already an open lead with this phone number: " + same.get(0).getName() + ". Open that lead, or save again to add this one anyway.");
            }
        }
        if (r.assignedUserId() == null) {
            l.setAssignedUserId(a.user().getId());
            l.setAssignedName(RealEstateAccess.name(a.user()));
        }
        repository.save(l);
        auditLogService.record("RE_LEAD_CREATED", "Added lead " + l.getName(), "ReLead", l.getId());
        return dto(a, l);
    }

    @Transactional
    public LeadDto update(Actor a, Long id, LeadRequest r) {
        access.on();
        ReLead l = find(id);
        apply(l, r, false);
        repository.save(l);
        return dto(a, l);
    }

    @Transactional
    public LeadDto setStatus(Actor a, Long id, LeadStatus status, String lostReason) {
        access.on();
        if (status == null) throw ApiException.badRequest("Choose a status.");
        ReLead l = find(id);
        if (status == LeadStatus.LOST) {
            String reason = clean(lostReason, 300, "The reason");
            if (reason == null) throw ApiException.badRequest("Say why the lead was lost.");
            l.setLostReason(reason);
        } else {
            l.setLostReason(null);
        }
        if (status == LeadStatus.WON && l.getCustomerId() == null) throw ApiException.badRequest("Turn the lead into a customer first (that is the buyer or tenant), then mark it won.");
        LeadStatus before = l.getStatus();
        l.setStatus(status);
        l.setLastContactAt(Instant.now());
        repository.save(l);
        auditLogService.record("RE_LEAD_STATUS", "Lead " + l.getName() + " changed from " + before + " to " + status, "ReLead", id);
        return dto(a, l);
    }

    /** Moves the next call of an open lead to today or a later day, or clears it. It is not contact, so the last-contact date stays as it was. */
    @Transactional
    public LeadDto setFollowUp(Actor a, Long id, LocalDate day) {
        access.on();
        ReLead l = find(id);
        if (!open(l)) throw ApiException.conflict("This lead is closed, so it has no next call.");
        if (day != null && day.isBefore(LocalDate.now())) throw ApiException.badRequest("Choose today or a later day.");
        if (day != null && day.isAfter(LocalDate.now().plusYears(2))) throw ApiException.badRequest("That is too far ahead.");
        l.setNextFollowUp(day);
        repository.save(l);
        return dto(a, l);
    }

    @Transactional
    public LeadDto addNote(Actor a, Long id, NoteRequest r) {
        access.on();
        ReLead l = find(id);
        String text = clean(r.text(), 2000, "The note");
        if (text == null) throw ApiException.badRequest("Write the note.");
        ReLeadNote n = new ReLeadNote();
        n.setLeadId(id);
        n.setKind(r.kind() == null ? NoteKind.NOTE : r.kind());
        n.setText(text);
        n.setCreatedByUserId(a.user().getId());
        n.setCreatedByName(RealEstateAccess.name(a.user()));
        noteRepository.save(n);
        l.setLastContactAt(Instant.now());
        if (l.getStatus() == LeadStatus.NEW && n.getKind() != NoteKind.NOTE) l.setStatus(LeadStatus.CONTACTED);
        if (r.nextFollowUp() != null) l.setNextFollowUp(r.nextFollowUp());
        repository.save(l);
        return dto(a, l);
    }

    @Transactional
    public LeadDto deleteNote(Actor a, Long id, Long noteId) {
        access.on();
        ReLead l = find(id);
        ReLeadNote n = noteRepository.findById(noteId).filter(x -> x.getLeadId().equals(id)).orElseThrow(() -> ApiException.notFound("Note not found."));
        if (!a.admin() && !a.user().getId().equals(n.getCreatedByUserId())) throw ApiException.forbidden("Only the person who wrote a note, or an admin, can delete it.");
        noteRepository.delete(n);
        return dto(a, l);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        ReLead l = find(id);
        if (viewingRepository.countByLeadId(id) > 0) throw ApiException.conflict("This lead has viewings. Mark it lost instead of deleting it.");
        if (dealRepository.countByLeadId(id) > 0) throw ApiException.conflict("This lead has a sales deal. Mark it lost instead of deleting it.");
        if (unitSaleRepository.countByLeadId(id) > 0) throw ApiException.conflict("This lead has a unit sale. Mark it lost instead of deleting it.");
        noteRepository.findByLeadIdOrderByIdDesc(id).forEach(noteRepository::delete);
        documentService.deleteFor(DocSubject.LEAD, id);
        repository.delete(l);
        auditLogService.record("RE_LEAD_DELETED", "Deleted lead " + l.getName());
    }

    /** Turns the lead into a customer (the buyer or tenant). An existing customer with the same phone is reused. */
    @Transactional
    public LeadDto convert(Actor a, Long id) {
        access.on();
        ReLead l = find(id);
        if (l.getCustomerId() != null) return dto(a, l);
        Customer c = null;
        if (l.getPhoneKey() != null) {
            c = customerRepository.findAll().stream().filter(x -> l.getPhoneKey().equals(digits(x.getPhone()))).findFirst().orElse(null);
        }
        if (c == null) {
            c = new Customer();
            c.setName(l.getName());
            c.setPhone(l.getPhone());
            c.setEmail(l.getEmail());
            customerRepository.save(c);
        }
        l.setCustomerId(c.getId());
        repository.save(l);
        auditLogService.record("RE_LEAD_CONVERTED", "Lead " + l.getName() + " is customer #" + c.getId(), "ReLead", id);
        return dto(a, l);
    }

    // ------------------------------------------------------------------ matches

    /** Available properties that fit what the lead wants, best first. Each match says why. */
    @Transactional(readOnly = true)
    public List<Match> matches(Long id) {
        access.on();
        ReLead l = find(id);
        List<ReProperty> candidates = propertyRepository.findByStatus(PropertyStatus.AVAILABLE).stream().filter(p -> fitsPurpose(l, p)).toList();
        Map<Long, com.posapp.realestate.dto.RealEstateDtos.PropertyRow> rows = propertyService.rows(candidates).stream().collect(Collectors.toMap(r -> r.id(), Function.identity()));
        List<Match> out = new ArrayList<>();
        for (ReProperty p : candidates) {
            int score = 0;
            List<String> why = new ArrayList<>();
            BigDecimal price = l.getInterest() == LeadInterest.RENT ? p.getRentPrice() : l.getInterest() == LeadInterest.BUY ? p.getSalePrice() : p.getSalePrice() != null ? p.getSalePrice() : p.getRentPrice();
            if (price != null && (l.getBudgetMin() != null || l.getBudgetMax() != null)) {
                boolean aboveMin = l.getBudgetMin() == null || price.compareTo(l.getBudgetMin()) >= 0;
                boolean belowMax = l.getBudgetMax() == null || price.compareTo(l.getBudgetMax()) <= 0;
                if (aboveMin && belowMax) {
                    score += 40;
                    why.add("Within budget");
                } else if (l.getBudgetMax() != null && price.compareTo(l.getBudgetMax().multiply(new BigDecimal("1.10"))) <= 0 && aboveMin) {
                    score += 15;
                    why.add("Up to 10% over budget");
                } else {
                    continue;
                }
            }
            if (l.getPreferredArea() != null) {
                if (p.getArea() != null && p.getArea().equalsIgnoreCase(l.getPreferredArea())) {
                    score += 30;
                    why.add("In " + p.getArea());
                } else {
                    score -= 10;
                }
            }
            if (l.getPreferredType() != null) {
                if (p.getType() == l.getPreferredType()) {
                    score += 20;
                    why.add("A " + p.getType().name().toLowerCase(Locale.ROOT));
                } else {
                    continue;
                }
            }
            if (l.getMinBedrooms() != null) {
                if (p.getBedrooms() != null && p.getBedrooms() >= l.getMinBedrooms()) {
                    score += 10;
                    why.add(p.getBedrooms() + " bedrooms");
                } else {
                    continue;
                }
            }
            if (p.isFeatured()) score += 2;
            out.add(new Match(rows.get(p.getId()), score, why));
        }
        out.sort(Comparator.comparingInt(Match::score).reversed().thenComparing(m -> m.property().code()));
        return out.size() > 10 ? out.subList(0, 10) : out;
    }

    private static boolean fitsPurpose(ReLead l, ReProperty p) {
        return switch (l.getInterest()) {
            case BUY -> p.getPurpose() != ListingPurpose.RENT;
            case RENT -> p.getPurpose() != ListingPurpose.SALE;
            case EITHER -> true;
        };
    }
}
