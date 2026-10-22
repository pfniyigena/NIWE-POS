package com.posapp.law;

import com.posapp.audit.AuditLogService;
import com.posapp.branch.Branch;
import com.posapp.branch.BranchService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawDtos.*;
import com.posapp.law.dto.LawRequests.*;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Matters, their team and parties and diary, outside contacts, clients and the module settings. */
@Service
public class LawService {

    private final LawAccess access;
    private final LawSettingsRepository settingsRepository;
    private final LawMatterCounterRepository counterRepository;
    private final LawContactRepository contactRepository;
    private final LawMatterRepository matterRepository;
    private final LawMatterTeamRepository teamRepository;
    private final LawMatterPartyRepository partyRepository;
    private final LawMatterDeadlineRepository deadlineRepository;
    private final LawMatterTaskRepository taskRepository;
    private final LawMatterNoteRepository noteRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final BranchService branchService;
    private final AuditLogService auditLogService;
    private final LawTimeLink timeLink;
    private final LawBillingService billing;
    private final LawTrustService trust;
    private final LawDocumentService documents;
    private final LawPortalAdminService portal;

    public LawService(LawAccess access, LawSettingsRepository settingsRepository, LawMatterCounterRepository counterRepository, LawContactRepository contactRepository,
                      LawMatterRepository matterRepository, LawMatterTeamRepository teamRepository, LawMatterPartyRepository partyRepository,
                      LawMatterDeadlineRepository deadlineRepository, LawMatterTaskRepository taskRepository, LawMatterNoteRepository noteRepository,
                      CustomerRepository customerRepository, UserRepository userRepository, BranchService branchService, AuditLogService auditLogService, LawTimeLink timeLink, LawBillingService billing, LawTrustService trust, LawDocumentService documents, LawPortalAdminService portal) {
        this.trust = trust;
        this.portal = portal;
        this.documents = documents;
        this.timeLink = timeLink;
        this.billing = billing;
        this.access = access;
        this.settingsRepository = settingsRepository;
        this.counterRepository = counterRepository;
        this.contactRepository = contactRepository;
        this.matterRepository = matterRepository;
        this.teamRepository = teamRepository;
        this.partyRepository = partyRepository;
        this.deadlineRepository = deadlineRepository;
        this.taskRepository = taskRepository;
        this.noteRepository = noteRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.branchService = branchService;
        this.auditLogService = auditLogService;
    }

    private static String clean(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static LocalDate today() {
        return LocalDate.now();
    }

    // ------------------------------------------------------------------ settings and options

    @Transactional
    public LawSettings settingsEntity() {
        List<LawSettings> all = settingsRepository.findAll();
        if (!all.isEmpty()) return all.get(0);
        return settingsRepository.save(new LawSettings());
    }

    private static List<String> lines(String text) {
        return Arrays.stream(text == null ? new String[0] : text.split("\\R")).map(String::trim).filter(x -> !x.isEmpty()).distinct().toList();
    }

    private static SettingsDto toDto(LawSettings s) {
        return new SettingsDto(lines(s.getPracticeAreas()), lines(s.getJurisdictions()), s.getReminderDays(), s.isDigestEnabled(), s.isRestrictedByDefault(),
                s.getDefaultBillingType(), s.isTrustEnabled(), s.getTrustBankName(), s.getTrustAccountNumber(), s.isPortalEnabled(), s.isEscalationEnabled(), s.getEscalationDays(),
                s.isCollectionsEnabled(), s.getCollectionsFirstDays(), s.getCollectionsRepeatDays(), s.getCollectionsMax(), s.getDefaultTermDays());
    }

    @Transactional
    public SettingsDto settings() {
        access.on();
        return toDto(settingsEntity());
    }

    @Transactional
    public SettingsDto updateSettings(SettingsRequest r) {
        access.on();
        LawSettings s = settingsEntity();
        if (r.practiceAreas() != null) {
            List<String> l = lines(r.practiceAreas());
            if (l.isEmpty()) throw ApiException.badRequest("List at least one practice area.");
            s.setPracticeAreas(String.join("\n", l));
        }
        if (r.jurisdictions() != null) {
            List<String> l = lines(r.jurisdictions());
            if (l.isEmpty()) throw ApiException.badRequest("List at least one jurisdiction.");
            s.setJurisdictions(String.join("\n", l));
        }
        if (r.reminderDays() != null) s.setReminderDays(Math.min(60, Math.max(1, r.reminderDays())));
        if (r.digestEnabled() != null) s.setDigestEnabled(r.digestEnabled());
        if (r.restrictedByDefault() != null) s.setRestrictedByDefault(r.restrictedByDefault());
        if (r.defaultBillingType() != null) s.setDefaultBillingType(r.defaultBillingType());
        if (r.trustEnabled() != null) {
            if (!r.trustEnabled() && s.isTrustEnabled() && trust.hasMoneyOrRequests()) {
                throw ApiException.conflict("Client money is still held, or a payout is waiting for approval. Pay it out or move it first, then switch the trust account off.");
            }
            s.setTrustEnabled(r.trustEnabled());
        }
        if (r.portalEnabled() != null) s.setPortalEnabled(r.portalEnabled());
        if (r.escalationEnabled() != null) s.setEscalationEnabled(r.escalationEnabled());
        if (r.collectionsEnabled() != null) s.setCollectionsEnabled(r.collectionsEnabled());
        if (r.collectionsFirstDays() != null) s.setCollectionsFirstDays(Math.min(90, Math.max(1, r.collectionsFirstDays())));
        if (r.collectionsRepeatDays() != null) s.setCollectionsRepeatDays(Math.min(90, Math.max(3, r.collectionsRepeatDays())));
        if (r.collectionsMax() != null) s.setCollectionsMax(Math.min(10, Math.max(1, r.collectionsMax())));
        if (r.defaultTermDays() != null) s.setDefaultTermDays(Math.min(365, Math.max(0, r.defaultTermDays())));
        if (r.escalationDays() != null) s.setEscalationDays(Math.min(90, Math.max(4, r.escalationDays())));
        if (r.trustBankName() != null) s.setTrustBankName(clean(r.trustBankName()));
        if (r.trustAccountNumber() != null) s.setTrustAccountNumber(clean(r.trustAccountNumber()));
        settingsRepository.save(s);
        auditLogService.record("LAW_SETTINGS_UPDATED", "Updated law firm settings");
        return toDto(s);
    }

    @Transactional(readOnly = true)
    public List<Option> staff() {
        access.on();
        return userRepository.findByActiveTrue().stream().map(u -> new Option(u.getId(), LawAccess.name(u)))
                .sorted(Comparator.comparing(Option::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    // ------------------------------------------------------------------ matter lists

    /** List rows with open-task and deadline counts, worked out in one pass over the open items. */
    @Transactional(readOnly = true)
    public List<MatterRow> rows(List<LawMatter> matters) {
        LocalDate today = today();
        Map<Long, List<LawMatterTask>> tasks = taskRepository.findByStatusNot(TaskStatus.DONE).stream().collect(Collectors.groupingBy(LawMatterTask::getMatterId));
        Map<Long, List<LawMatterDeadline>> deadlines = deadlineRepository.findByStatus(DeadlineStatus.OPEN).stream().collect(Collectors.groupingBy(LawMatterDeadline::getMatterId));
        List<MatterRow> out = new ArrayList<>();
        for (LawMatter m : matters) {
            List<LawMatterTask> ts = tasks.getOrDefault(m.getId(), List.of());
            List<LawMatterDeadline> ds = deadlines.getOrDefault(m.getId(), List.of());
            LocalDate next = ds.stream().map(LawMatterDeadline::getDueDate).filter(d -> !d.isBefore(today)).min(Comparator.naturalOrder()).orElse(null);
            out.add(new MatterRow(m.getId(), m.getNumber(), m.getTitle(), m.getStatus(), m.getPracticeArea(), m.getJurisdiction(), m.getClientCustomerId(), m.getClientName(),
                    m.getLeadUserId(), m.getLeadName(), m.getOpenedDate(), m.getClosedDate(), m.getBillingType(), m.isRestricted(), m.isConflictCleared(), ts.size(),
                    (int) ts.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today)).count(), next,
                    (int) ds.stream().filter(d -> d.getDueDate().isBefore(today)).count()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<LawMatter> visible(Actor a) {
        return matterRepository.findAllByOrderByOpenedDateDescIdDesc().stream().filter(m -> access.canView(m, a)).toList();
    }

    @Transactional(readOnly = true)
    public List<MatterRow> list(Actor a, MatterStatus status, String q, String practiceArea, Long leadId, Long clientId) {
        access.on();
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<LawMatter> ms = visible(a).stream()
                .filter(m -> status == null || m.getStatus() == status)
                .filter(m -> practiceArea == null || practiceArea.isBlank() || practiceArea.equalsIgnoreCase(m.getPracticeArea()))
                .filter(m -> leadId == null || leadId.equals(m.getLeadUserId()))
                .filter(m -> clientId == null || clientId.equals(m.getClientCustomerId()))
                .filter(m -> needle.isEmpty() || (m.getNumber() + " " + m.getTitle() + " " + m.getClientName() + " " + (m.getCourtCaseNumber() == null ? "" : m.getCourtCaseNumber()))
                        .toLowerCase().contains(needle))
                .toList();
        return rows(ms);
    }

    @Transactional(readOnly = true)
    public SummaryDto summary(Actor a) {
        access.on();
        List<LawMatter> ms = visible(a);
        LocalDate today = today();
        Set<Long> openIds = ms.stream().filter(m -> m.getStatus() != MatterStatus.CLOSED).map(LawMatter::getId).collect(Collectors.toSet());
        int dueWeek = 0;
        int overdueD = 0;
        for (LawMatterDeadline d : deadlineRepository.findByStatus(DeadlineStatus.OPEN)) {
            if (!openIds.contains(d.getMatterId())) continue;
            if (d.getDueDate().isBefore(today)) overdueD++;
            else if (!d.getDueDate().isAfter(today.plusDays(7))) dueWeek++;
        }
        int overdueT = (int) taskRepository.findByStatusNot(TaskStatus.DONE).stream()
                .filter(t -> openIds.contains(t.getMatterId()) && t.getDueDate() != null && t.getDueDate().isBefore(today)).count();
        return new SummaryDto((int) ms.stream().filter(m -> m.getStatus() == MatterStatus.OPEN).count(), (int) ms.stream().filter(m -> m.getStatus() == MatterStatus.ON_HOLD).count(),
                dueWeek, overdueD, overdueT, (int) ms.stream().filter(m -> m.getStatus() != MatterStatus.CLOSED && !m.isConflictCleared()).count());
    }

    // ------------------------------------------------------------------ one matter

    private NoteDto note(LawMatterNote n, boolean canDelete) {
        return new NoteDto(n.getId(), n.getKind(), n.getText(), n.getCreatedByName(), n.getCreatedAt(), canDelete);
    }

    private PartyDto party(LawMatterParty p, Map<Long, LawContact> contacts) {
        LawContact c = contacts.get(p.getContactId());
        return new PartyDto(p.getId(), p.getContactId(), c != null ? c.getName() : "(removed)", c != null ? c.getType() : ContactType.PERSON, c != null ? c.getPhone() : null,
                c != null ? c.getEmail() : null, c != null ? c.getIdNumber() : null, p.getRole(), p.getNote());
    }

    @Transactional(readOnly = true)
    public MatterDto dto(Actor a, LawMatter m) {
        LocalDate today = today();
        boolean work = access.canWork(m, a);
        boolean lead = access.canLead(m, a);
        Customer client = customerRepository.findById(m.getClientCustomerId()).orElse(null);
        List<LawMatterParty> ps = partyRepository.findByMatterIdOrderByIdAsc(m.getId());
        Map<Long, LawContact> contacts = contactRepository.findAllById(ps.stream().map(LawMatterParty::getContactId).toList()).stream()
                .collect(Collectors.toMap(LawContact::getId, c -> c));
        return new MatterDto(m.getId(), m.getNumber(), m.getTitle(), m.getDescription(), m.getStatus(), m.getPracticeArea(), m.getJurisdiction(), m.getCourtOrForum(),
                m.getCourtCaseNumber(), m.getBranchId(), m.getClientCustomerId(), m.getClientName(), client != null ? client.getPhone() : null, client != null ? client.getEmail() : null,
                m.getBillingType(), m.getBillingNote(), m.getOpenedDate(), m.getClosedDate(), m.getOutcome(), m.isRestricted(), m.isConflictCleared(), m.getConflictNote(),
                m.getConflictClearedBy(), m.getConflictClearedAt(), m.getLeadUserId(), m.getLeadName(), m.getCreatedByName(),
                teamRepository.findByMatterId(m.getId()).stream().sorted(Comparator.comparing((LawMatterTeam t) -> t.getRole() != TeamRole.LEAD).thenComparing(LawMatterTeam::getUserName))
                        .map(t -> new TeamDto(t.getId(), t.getUserId(), t.getUserName(), t.getRole())).toList(),
                ps.stream().map(p -> party(p, contacts)).toList(),
                deadlineRepository.findByMatterIdOrderByDueDateAscDueTimeAsc(m.getId()).stream().map(d -> LawMapper.deadline(d, m, today)).toList(),
                taskRepository.findByMatterIdOrderByIdAsc(m.getId()).stream().map(t -> LawMapper.task(t, m, today)).toList(),
                noteRepository.findByMatterIdOrderByCreatedAtDesc(m.getId()).stream()
                        .map(n -> note(n, lead || (n.getCreatedByUserId() != null && n.getCreatedByUserId().equals(a.user().getId())))).toList(),
                work, lead);
    }

    @Transactional(readOnly = true)
    public MatterDto get(Actor a, Long id) {
        return dto(a, access.viewable(a, id));
    }

    private String nextNumber(Long branchId, LocalDate opened) {
        settingsRepository.lockAll();
        Branch b = branchService.getEntityOrThrow(branchService.resolveForDocument(branchId));
        String prefix = b.getDocumentPrefix() != null && !b.getDocumentPrefix().isBlank() ? b.getDocumentPrefix().trim().replaceAll("[-\\s]+$", "") : b.getCode();
        String key = prefix + "-" + opened.getYear();
        LawMatterCounter c = counterRepository.findByCounterKey(key).orElseGet(() -> {
            LawMatterCounter n = new LawMatterCounter();
            n.setCounterKey(key);
            return n;
        });
        c.setLastSeq(c.getLastSeq() + 1);
        counterRepository.save(c);
        return String.format("%s-%04d", key, c.getLastSeq());
    }

    private AppUser activeUser(Long id, String what) {
        AppUser u = userRepository.findById(id).orElseThrow(() -> ApiException.badRequest(what + " not found."));
        if (!u.isActive()) throw ApiException.badRequest(u.getUsername() + " is not an active user.");
        return u;
    }

    private void applyCommon(LawMatter m, MatterRequest r) {
        if (r.title() == null || r.title().isBlank()) throw ApiException.badRequest("Give the matter a title.");
        m.setTitle(r.title().trim());
        m.setDescription(clean(r.description()));
        m.setPracticeArea(clean(r.practiceArea()));
        m.setJurisdiction(clean(r.jurisdiction()));
        m.setCourtOrForum(clean(r.courtOrForum()));
        m.setCourtCaseNumber(clean(r.courtCaseNumber()));
        if (r.billingType() != null) m.setBillingType(r.billingType());
        m.setBillingNote(clean(r.billingNote()));
        if (r.restricted() != null) m.setRestricted(r.restricted());
    }

    @Transactional
    public MatterDto create(Actor a, MatterRequest r) {
        access.on();
        if (!a.admin() && !a.manager()) throw ApiException.forbidden("You cannot open matters.");
        if (r.clientCustomerId() == null) throw ApiException.badRequest("Choose the client.");
        Customer client = customerRepository.findById(r.clientCustomerId()).orElseThrow(() -> ApiException.badRequest("Client not found."));
        if (client.isWalkIn()) throw ApiException.badRequest("A matter needs a real client, not the walk-in customer.");
        LawSettings settings = settingsEntity();
        LawMatter m = new LawMatter();
        m.setRestricted(settings.isRestrictedByDefault());
        m.setBillingType(settings.getDefaultBillingType());
        applyCommon(m, r);
        m.setClientCustomerId(client.getId());
        m.setClientName(client.getName());
        m.setBranchId(branchService.resolveForDocument(r.branchId()));
        m.setOpenedDate(r.openedDate() != null ? r.openedDate() : today());
        AppUser lead = r.leadUserId() != null ? activeUser(r.leadUserId(), "Lead lawyer") : a.user();
        m.setLeadUserId(lead.getId());
        m.setLeadName(LawAccess.name(lead));
        m.setCreatedByName(LawAccess.name(a.user()));
        m.setNumber(nextNumber(m.getBranchId(), m.getOpenedDate()));
        matterRepository.save(m);
        LawMatterTeam t = new LawMatterTeam();
        t.setMatterId(m.getId());
        t.setUserId(lead.getId());
        t.setUserName(LawAccess.name(lead));
        t.setRole(TeamRole.LEAD);
        teamRepository.save(t);
        timeLink.sync(m);
        auditLogService.record("LAW_MATTER_OPENED", "Opened matter " + m.getNumber() + " for " + m.getClientName(), "LawMatter", m.getId());
        return dto(a, m);
    }

    @Transactional
    public MatterDto update(Actor a, Long id, MatterRequest r) {
        LawMatter m = access.leadable(a, id);
        boolean clientChanged = false;
        if (r.clientCustomerId() != null && !r.clientCustomerId().equals(m.getClientCustomerId())) {
            Customer client = customerRepository.findById(r.clientCustomerId()).orElseThrow(() -> ApiException.badRequest("Client not found."));
            if (client.isWalkIn()) throw ApiException.badRequest("A matter needs a real client, not the walk-in customer.");
            m.setClientCustomerId(client.getId());
            m.setClientName(client.getName());
            clientChanged = true;
        }
        applyCommon(m, r);
        if (r.openedDate() != null) m.setOpenedDate(r.openedDate());
        if (r.leadUserId() != null && !r.leadUserId().equals(m.getLeadUserId())) {
            AppUser lead = activeUser(r.leadUserId(), "Lead lawyer");
            for (LawMatterTeam t : teamRepository.findByMatterId(m.getId())) {
                if (t.getRole() == TeamRole.LEAD) {
                    t.setRole(TeamRole.ASSOCIATE);
                    teamRepository.save(t);
                }
            }
            LawMatterTeam t = teamRepository.findByMatterId(m.getId()).stream().filter(x -> x.getUserId().equals(lead.getId())).findFirst().orElseGet(() -> {
                LawMatterTeam n = new LawMatterTeam();
                n.setMatterId(m.getId());
                n.setUserId(lead.getId());
                n.setUserName(LawAccess.name(lead));
                return n;
            });
            t.setRole(TeamRole.LEAD);
            teamRepository.save(t);
            m.setLeadUserId(lead.getId());
            m.setLeadName(LawAccess.name(lead));
        }
        if (clientChanged) resetClearance(m);
        matterRepository.save(m);
        timeLink.sync(m);
        auditLogService.record("LAW_MATTER_UPDATED", "Updated matter " + m.getNumber(), "LawMatter", m.getId());
        return dto(a, m);
    }

    private void resetClearance(LawMatter m) {
        m.setConflictCleared(false);
        m.setConflictNote(null);
        m.setConflictClearedBy(null);
        m.setConflictClearedAt(null);
    }

    @Transactional
    public MatterDto setStatus(Actor a, Long id, StatusRequest r) {
        LawMatter m = access.leadable(a, id);
        if (r.status() == null) throw ApiException.badRequest("Choose a status.");
        if (r.status() == MatterStatus.CLOSED && m.getStatus() != MatterStatus.CLOSED) {
            trust.requireNothingHeld(m);
            long openDeadlines = deadlineRepository.findByMatterIdOrderByDueDateAscDueTimeAsc(m.getId()).stream().filter(d -> d.getStatus() == DeadlineStatus.OPEN).count();
            long openTasks = taskRepository.findByMatterIdOrderByIdAsc(m.getId()).stream().filter(t -> t.getStatus() != TaskStatus.DONE).count();
            if ((openDeadlines > 0 || openTasks > 0) && !Boolean.TRUE.equals(r.force())) {
                throw ApiException.conflict("This matter still has " + openDeadlines + " open deadline(s) and " + openTasks + " open task(s). Finish them, or close it anyway.");
            }
            m.setClosedDate(today());
            m.setOutcome(clean(r.outcome()));
        } else if (r.status() != MatterStatus.CLOSED) {
            m.setClosedDate(null);
        }
        m.setStatus(r.status());
        matterRepository.save(m);
        timeLink.sync(m);
        auditLogService.record("LAW_MATTER_STATUS", "Matter " + m.getNumber() + " is now " + r.status(), "LawMatter", m.getId());
        return dto(a, m);
    }

    @Transactional
    public void delete(Actor a, Long id) {
        LawMatter m = access.leadable(a, id);
        if (!a.admin() && !a.manager()) throw ApiException.forbidden("Only a manager or an admin can delete a matter.");
        if (noteRepository.countByMatterId(id) > 0 || taskRepository.countByMatterId(id) > 0 || deadlineRepository.countByMatterId(id) > 0) {
            throw ApiException.conflict("This matter has diary entries, tasks or deadlines. Close it instead of deleting it.");
        }
        if (billing.hasRecords(m)) throw ApiException.conflict("This matter has hours, costs or bills. Close it instead of deleting it.");
        if (trust.hasRecords(m)) throw ApiException.conflict("This matter has client money entries. Close it instead of deleting it.");
        if (documents.hasRecords(m)) throw ApiException.conflict("This matter has documents. Close it instead of deleting it.");
        if (portal.hasRecords(m)) throw ApiException.conflict("This matter is open to the client portal or has portal messages. Close it instead of deleting it.");
        teamRepository.deleteByMatterId(id);
        partyRepository.deleteByMatterId(id);
        portal.deleteFor(id);
        matterRepository.delete(m);
        timeLink.remove(m);
        auditLogService.record("LAW_MATTER_DELETED", "Deleted matter " + m.getNumber(), "LawMatter", id);
    }

    // ------------------------------------------------------------------ team

    @Transactional
    public MatterDto setTeam(Actor a, Long id, TeamRequest r) {
        LawMatter m = access.leadable(a, id);
        List<TeamMemberRequest> members = r.members() == null ? List.of() : r.members();
        if (members.isEmpty()) throw ApiException.badRequest("A matter needs at least a lead lawyer.");
        Map<Long, TeamRole> byUser = new LinkedHashMap<>();
        for (TeamMemberRequest t : members) {
            if (t.userId() == null) continue;
            byUser.putIfAbsent(t.userId(), t.role() != null ? t.role() : TeamRole.ASSOCIATE);
        }
        Long leadId = byUser.entrySet().stream().filter(e -> e.getValue() == TeamRole.LEAD).map(Map.Entry::getKey).findFirst().orElse(byUser.keySet().iterator().next());
        teamRepository.deleteByMatterId(id);
        teamRepository.flush();
        for (Map.Entry<Long, TeamRole> e : byUser.entrySet()) {
            AppUser u = activeUser(e.getKey(), "Team member");
            LawMatterTeam t = new LawMatterTeam();
            t.setMatterId(id);
            t.setUserId(u.getId());
            t.setUserName(LawAccess.name(u));
            t.setRole(e.getKey().equals(leadId) ? TeamRole.LEAD : (e.getValue() == TeamRole.LEAD ? TeamRole.ASSOCIATE : e.getValue()));
            teamRepository.save(t);
            if (t.getRole() == TeamRole.LEAD) {
                m.setLeadUserId(u.getId());
                m.setLeadName(LawAccess.name(u));
            }
        }
        matterRepository.save(m);
        auditLogService.record("LAW_TEAM_UPDATED", "Updated the team of matter " + m.getNumber(), "LawMatter", id);
        return dto(a, m);
    }

    // ------------------------------------------------------------------ parties and diary

    @Transactional
    public MatterDto addParty(Actor a, Long id, PartyRequest r) {
        LawMatter m = access.workable(a, id);
        if (r.contactId() == null) throw ApiException.badRequest("Choose the person or organisation.");
        contactRepository.findById(r.contactId()).orElseThrow(() -> ApiException.badRequest("Contact not found."));
        PartyRole role = r.role() != null ? r.role() : PartyRole.OTHER;
        boolean dup = partyRepository.findByMatterIdOrderByIdAsc(id).stream().anyMatch(p -> p.getContactId().equals(r.contactId()) && p.getRole() == role);
        if (dup) throw ApiException.conflict("That person is already on this matter in that role.");
        LawMatterParty p = new LawMatterParty();
        p.setMatterId(id);
        p.setContactId(r.contactId());
        p.setRole(role);
        p.setNote(clean(r.note()));
        partyRepository.save(p);
        if (m.isConflictCleared()) {
            resetClearance(m);
            matterRepository.save(m);
        }
        auditLogService.record("LAW_PARTY_ADDED", "Added a party to matter " + m.getNumber(), "LawMatter", id);
        return dto(a, m);
    }

    @Transactional
    public MatterDto removeParty(Actor a, Long id, Long partyId) {
        LawMatter m = access.workable(a, id);
        LawMatterParty p = partyRepository.findById(partyId).filter(x -> x.getMatterId().equals(id)).orElseThrow(() -> ApiException.notFound("Party not found."));
        partyRepository.delete(p);
        return dto(a, m);
    }

    @Transactional
    public MatterDto addNote(Actor a, Long id, NoteRequest r) {
        LawMatter m = access.workable(a, id);
        if (r.text() == null || r.text().isBlank()) throw ApiException.badRequest("Write the note.");
        LawMatterNote n = new LawMatterNote();
        n.setMatterId(id);
        n.setKind(r.kind() != null ? r.kind() : NoteKind.NOTE);
        n.setText(r.text().trim());
        n.setCreatedByUserId(a.user().getId());
        n.setCreatedByName(LawAccess.name(a.user()));
        noteRepository.save(n);
        return dto(a, m);
    }

    @Transactional
    public MatterDto deleteNote(Actor a, Long id, Long noteId) {
        LawMatter m = access.workable(a, id);
        LawMatterNote n = noteRepository.findById(noteId).filter(x -> x.getMatterId().equals(id)).orElseThrow(() -> ApiException.notFound("Note not found."));
        boolean own = n.getCreatedByUserId() != null && n.getCreatedByUserId().equals(a.user().getId());
        if (!own && !access.canLead(m, a)) throw ApiException.forbidden("Only the author or the lead lawyer can delete a diary entry.");
        noteRepository.delete(n);
        return dto(a, m);
    }

    // ------------------------------------------------------------------ outside contacts

    private boolean contactsRead(Actor a) {
        return a.admin() || a.viewer() || access.isLawyer(a);
    }

    private boolean contactsWrite(Actor a) {
        return a.admin() || a.manager() || access.isLawyer(a);
    }

    private ContactDto toDto(LawContact c, long matters) {
        return new ContactDto(c.getId(), c.getName(), c.getType(), c.getPhone(), c.getEmail(), c.getIdNumber(), c.getAddress(), c.getNotes(), matters);
    }

    @Transactional(readOnly = true)
    public List<ContactDto> contacts(Actor a, String q) {
        access.on();
        if (!contactsRead(a)) throw ApiException.forbidden("You cannot see the contacts.");
        String needle = q == null ? "" : q.trim().toLowerCase();
        Map<Long, Long> counts = partyRepository.findAll().stream().collect(Collectors.groupingBy(LawMatterParty::getContactId, Collectors.counting()));
        return contactRepository.findAll().stream()
                .filter(c -> needle.isEmpty() || (c.getName() + " " + (c.getPhone() == null ? "" : c.getPhone()) + " " + (c.getEmail() == null ? "" : c.getEmail()) + " "
                        + (c.getIdNumber() == null ? "" : c.getIdNumber())).toLowerCase().contains(needle))
                .sorted(Comparator.comparing(LawContact::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> toDto(c, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    private void applyContact(LawContact c, ContactRequest r) {
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Enter a name.");
        c.setName(r.name().trim());
        c.setType(r.type() != null ? r.type() : ContactType.PERSON);
        c.setPhone(clean(r.phone()));
        c.setEmail(clean(r.email()));
        c.setIdNumber(clean(r.idNumber()));
        c.setAddress(clean(r.address()));
        c.setNotes(clean(r.notes()));
    }

    @Transactional
    public ContactDto createContact(Actor a, ContactRequest r) {
        access.on();
        if (!contactsWrite(a)) throw ApiException.forbidden("You cannot add contacts.");
        LawContact c = new LawContact();
        applyContact(c, r);
        c.setCreatedByName(LawAccess.name(a.user()));
        contactRepository.save(c);
        return toDto(c, 0);
    }

    @Transactional
    public ContactDto updateContact(Actor a, Long id, ContactRequest r) {
        access.on();
        if (!contactsWrite(a)) throw ApiException.forbidden("You cannot change contacts.");
        LawContact c = contactRepository.findById(id).orElseThrow(() -> ApiException.notFound("Contact not found."));
        applyContact(c, r);
        contactRepository.save(c);
        return toDto(c, partyRepository.countByContactId(id));
    }

    @Transactional
    public void deleteContact(Actor a, Long id) {
        access.on();
        if (!contactsWrite(a)) throw ApiException.forbidden("You cannot delete contacts.");
        if (partyRepository.countByContactId(id) > 0) throw ApiException.conflict("This contact is on a matter. Remove it from the matter first.");
        contactRepository.deleteById(id);
    }

    // ------------------------------------------------------------------ clients

    /** Customers that have at least one matter. */
    @Transactional(readOnly = true)
    public List<ClientRow> clients(Actor a) {
        access.on();
        if (!a.admin() && !a.viewer()) throw ApiException.forbidden("You cannot see the clients.");
        Map<Long, List<LawMatter>> byClient = matterRepository.findAll().stream().filter(m -> access.canView(m, a)).collect(Collectors.groupingBy(LawMatter::getClientCustomerId));
        List<ClientRow> out = new ArrayList<>();
        for (Customer c : customerRepository.findAllById(new HashSet<>(byClient.keySet()))) {
            List<LawMatter> ms = byClient.get(c.getId());
            out.add(new ClientRow(c.getId(), c.getName(), c.getPhone(), c.getEmail(), ms.size(), ms.stream().filter(m -> m.getStatus() != MatterStatus.CLOSED).count()));
        }
        out.sort(Comparator.comparing(ClientRow::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }
}
