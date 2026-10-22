package com.posapp.realestate;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.realestate.RealEstateAccess.Actor;
import com.posapp.realestate.dto.RealEstateDtos.ViewingDto;
import com.posapp.realestate.dto.RealEstateRequests.ViewingRequest;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Viewings: a lead visits a property with an agent. An agent cannot be in two places at once unless the person insists. */
@Service
public class ReViewingService {

    private final ReViewingRepository repository;
    private final RePropertyRepository propertyRepository;
    private final ReLeadRepository leadRepository;
    private final UserRepository userRepository;
    private final ReSettingsService settings;
    private final RealEstateAccess access;
    private final AuditLogService auditLogService;

    public ReViewingService(ReViewingRepository repository, RePropertyRepository propertyRepository, ReLeadRepository leadRepository, UserRepository userRepository,
                            ReSettingsService settings, RealEstateAccess access, AuditLogService auditLogService) {
        this.repository = repository;
        this.propertyRepository = propertyRepository;
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
        this.settings = settings;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    static ViewingDto dto(ReViewing v, ReProperty p, ReLead l, boolean canManage) {
        return new ViewingDto(v.getId(), v.getPropertyId(), p == null ? null : p.getCode(), p == null ? null : p.getTitle(), p == null ? null : p.getArea(), v.getLeadId(),
                l == null ? null : l.getName(), l == null ? null : l.getPhone(), v.getAgentUserId(), v.getAgentName(), v.getStartAt(), v.getEndAt(), v.getStatus(), v.getFeedback(), canManage, v.getClientRemindedAt(),
                l != null && l.getEmail() != null && !l.getEmail().isBlank());
    }

    private List<ViewingDto> dtos(List<ReViewing> list, boolean manage) {
        Map<Long, ReProperty> props = propertyRepository.findAll().stream().collect(Collectors.toMap(ReProperty::getId, Function.identity()));
        Map<Long, ReLead> leads = leadRepository.findAll().stream().collect(Collectors.toMap(ReLead::getId, Function.identity()));
        return list.stream().map(v -> dto(v, props.get(v.getPropertyId()), leads.get(v.getLeadId()), manage)).toList();
    }

    ReViewing find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Viewing not found."));
    }

    @Transactional(readOnly = true)
    public List<ViewingDto> list(Actor a, LocalDate from, LocalDate to, Long agentId, ViewingStatus status) {
        access.on();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusDays(14);
        if (end.isBefore(start)) throw ApiException.badRequest("The end date is before the start date.");
        if (ChronoUnit.DAYS.between(start, end) > 366) throw ApiException.badRequest("Choose a period of at most a year.");
        Instant a0 = start.atStartOfDay(zone).toInstant();
        Instant b0 = end.plusDays(1).atStartOfDay(zone).toInstant();
        List<ReViewing> list = repository.findByStartAtBetweenOrderByStartAtAsc(a0, b0).stream()
                .filter(v -> agentId == null || agentId.equals(v.getAgentUserId())).filter(v -> status == null || v.getStatus() == status).toList();
        return dtos(list, a.manager());
    }

    private void checkAgentFree(Long agentId, Instant start, Instant end, Long ignoreId) {
        if (agentId == null) return;
        for (ReViewing o : repository.findByAgentUserIdAndStatus(agentId, ViewingStatus.SCHEDULED)) {
            if (o.getId().equals(ignoreId)) continue;
            if (o.getStartAt().isBefore(end) && o.getEndAt().isAfter(start)) {
                ReProperty p = propertyRepository.findById(o.getPropertyId()).orElse(null);
                throw ApiException.conflict(o.getAgentName() + " already has a viewing at that time" + (p == null ? "" : " (" + p.getCode() + " " + p.getTitle() + ")")
                        + ". Choose another time or another agent, or save again to keep both.");
            }
        }
    }

    @Transactional
    public ViewingDto schedule(Actor a, ViewingRequest r) {
        access.on();
        if (r.propertyId() == null || r.leadId() == null) throw ApiException.badRequest("Choose the property and the lead.");
        if (r.startAt() == null) throw ApiException.badRequest("Choose the date and time.");
        ReProperty p = propertyRepository.findById(r.propertyId()).orElseThrow(() -> ApiException.badRequest("Property not found."));
        ReLead l = leadRepository.findById(r.leadId()).orElseThrow(() -> ApiException.badRequest("Lead not found."));
        if (p.getStatus() != PropertyStatus.AVAILABLE) throw ApiException.badRequest("Only an available property can be viewed. This one is " + p.getStatus().name().toLowerCase() + ".");
        if (!ReLeadService.open(l)) throw ApiException.badRequest("This lead is closed. Reopen it before booking a viewing.");
        Instant now = Instant.now();
        if (r.startAt().isBefore(now.minus(1, ChronoUnit.DAYS))) throw ApiException.badRequest("That time is in the past.");
        Instant end = r.endAt() != null ? r.endAt() : r.startAt().plus(settings.entity().getViewingMinutes(), ChronoUnit.MINUTES);
        if (!end.isAfter(r.startAt())) throw ApiException.badRequest("The viewing must end after it starts.");
        if (ChronoUnit.HOURS.between(r.startAt(), end) > 12) throw ApiException.badRequest("A viewing cannot last more than 12 hours.");
        Long agentId = r.agentUserId() != null ? r.agentUserId() : p.getAgentUserId() != null ? p.getAgentUserId() : a.user().getId();
        AppUser agent = userRepository.findById(agentId).filter(AppUser::isActive).orElseThrow(() -> ApiException.badRequest("Agent not found."));
        if (!Boolean.TRUE.equals(r.force())) checkAgentFree(agentId, r.startAt(), end, null);
        ReViewing v = new ReViewing();
        v.setPropertyId(p.getId());
        v.setLeadId(l.getId());
        v.setAgentUserId(agent.getId());
        v.setAgentName(RealEstateAccess.name(agent));
        v.setStartAt(r.startAt());
        v.setEndAt(end);
        v.setCreatedByName(RealEstateAccess.name(a.user()));
        repository.save(v);
        if (l.getStatus() == LeadStatus.NEW || l.getStatus() == LeadStatus.CONTACTED) l.setStatus(LeadStatus.VIEWING);
        l.setLastContactAt(now);
        leadRepository.save(l);
        auditLogService.record("RE_VIEWING_SCHEDULED", "Viewing of " + p.getCode() + " for " + l.getName(), "ReViewing", v.getId());
        return dto(v, p, l, a.manager());
    }

    @Transactional
    public ViewingDto reschedule(Actor a, Long id, ViewingRequest r) {
        access.on();
        ReViewing v = find(id);
        if (v.getStatus() != ViewingStatus.SCHEDULED) throw ApiException.conflict("Only a scheduled viewing can be moved.");
        if (r.startAt() == null) throw ApiException.badRequest("Choose the date and time.");
        Instant end = r.endAt() != null ? r.endAt() : r.startAt().plus(settings.entity().getViewingMinutes(), ChronoUnit.MINUTES);
        if (!end.isAfter(r.startAt())) throw ApiException.badRequest("The viewing must end after it starts.");
        Long agentId = r.agentUserId() != null ? r.agentUserId() : v.getAgentUserId();
        AppUser agent = userRepository.findById(agentId).filter(AppUser::isActive).orElseThrow(() -> ApiException.badRequest("Agent not found."));
        if (!Boolean.TRUE.equals(r.force())) checkAgentFree(agentId, r.startAt(), end, id);
        v.setStartAt(r.startAt());
        v.setEndAt(end);
        v.setAgentUserId(agent.getId());
        v.setAgentName(RealEstateAccess.name(agent));
        v.setClientRemindedAt(null);
        v.setAgentRemindedAt(null);
        repository.save(v);
        return dtos(List.of(v), a.manager()).get(0);
    }

    @Transactional
    public ViewingDto setStatus(Actor a, Long id, ViewingStatus status, String feedback) {
        access.on();
        if (status == null) throw ApiException.badRequest("Choose a status.");
        ReViewing v = find(id);
        if (v.getStatus() != ViewingStatus.SCHEDULED) throw ApiException.conflict("This viewing is already " + v.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        if (status == ViewingStatus.SCHEDULED) throw ApiException.badRequest("Choose done, cancelled or no-show.");
        if (status == ViewingStatus.DONE && v.getStartAt().isAfter(Instant.now().plus(1, ChronoUnit.HOURS))) throw ApiException.badRequest("This viewing has not happened yet.");
        if (feedback != null && feedback.length() > 2000) throw ApiException.badRequest("The feedback can be at most 2000 characters.");
        v.setStatus(status);
        v.setFeedback(feedback == null || feedback.isBlank() ? null : feedback.trim());
        repository.save(v);
        ReLead l = leadRepository.findById(v.getLeadId()).orElse(null);
        if (l != null) {
            l.setLastContactAt(Instant.now());
            leadRepository.save(l);
        }
        return dtos(List.of(v), a.manager()).get(0);
    }
}
