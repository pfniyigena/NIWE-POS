package com.posapp.law;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.customer.Customer;
import com.posapp.customer.CustomerRepository;
import com.posapp.law.LawAccess.Actor;
import com.posapp.law.dto.LawDtos.*;
import com.posapp.law.dto.LawRequests.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The conflict check: before acting for someone, find out whether the firm already knows them, and on which side. It compares a name,
 * phone number, ID number and e-mail against every client and every outside contact, and reports the matters (open or closed) where they
 * appear. A hit between opposite sides (our client against an opposing party) is flagged high; any other overlap is for review. Matters the
 * person may not open are reported without their title, because the overlap itself still matters.
 */
@Service
public class LawConflictService {

    private record Subject(String label, String role, String name, String phone, String idNumber, String email, String selfKind, Long selfId) {
    }

    private final LawAccess access;
    private final CustomerRepository customerRepository;
    private final LawContactRepository contactRepository;
    private final LawMatterRepository matterRepository;
    private final LawMatterPartyRepository partyRepository;
    private final LawConflictCheckRepository checkRepository;
    private final AuditLogService auditLogService;

    public LawConflictService(LawAccess access, CustomerRepository customerRepository, LawContactRepository contactRepository, LawMatterRepository matterRepository,
                              LawMatterPartyRepository partyRepository, LawConflictCheckRepository checkRepository, AuditLogService auditLogService) {
        this.access = access;
        this.customerRepository = customerRepository;
        this.contactRepository = contactRepository;
        this.matterRepository = matterRepository;
        this.partyRepository = partyRepository;
        this.checkRepository = checkRepository;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ matching

    static String normName(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim();
    }

    static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    static String alnum(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]", "");
    }

    static boolean nameMatch(String a, String b) {
        String x = normName(a);
        String y = normName(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        Set<String> tx = new HashSet<>(Arrays.asList(x.split(" ")));
        Set<String> ty = new HashSet<>(Arrays.asList(y.split(" ")));
        return Math.min(tx.size(), ty.size()) >= 2 && (tx.containsAll(ty) || ty.containsAll(tx));
    }

    static boolean phoneMatch(String a, String b) {
        String x = digits(a);
        String y = digits(b);
        return x.length() >= 7 && y.length() >= 7 && (x.endsWith(y) || y.endsWith(x));
    }

    static boolean idMatch(String a, String b) {
        String x = alnum(a);
        String y = alnum(b);
        return x.length() >= 4 && x.equals(y);
    }

    private static List<String> reasons(Subject s, String name, String phone, String id, String email) {
        List<String> out = new ArrayList<>();
        if (nameMatch(s.name(), name)) out.add("Name");
        if (phoneMatch(s.phone(), phone)) out.add("Phone");
        if (idMatch(s.idNumber(), id)) out.add("ID number");
        if (s.email() != null && email != null && !s.email().isBlank() && s.email().trim().equalsIgnoreCase(email.trim())) out.add("E-mail");
        return out;
    }

    private static String side(String role) {
        if (role == null) return "";
        return switch (role) {
            case "CLIENT", "CO_CLIENT" -> "OURS";
            case "OPPOSING_PARTY", "OPPOSING_COUNSEL" -> "THEIRS";
            default -> "";
        };
    }

    private MatterRef ref(Actor a, LawMatter m, String role) {
        if (!access.canView(m, a)) return new MatterRef(null, null, null, m.getStatus(), role, true);
        return new MatterRef(m.getId(), m.getNumber(), m.getTitle(), m.getStatus(), role, false);
    }

    private List<ConflictHit> hitsFor(Actor a, Subject s, Long excludeMatterId) {
        List<ConflictHit> out = new ArrayList<>();
        List<LawMatter> allMatters = matterRepository.findAll();
        Map<Long, LawMatter> byId = allMatters.stream().collect(Collectors.toMap(LawMatter::getId, Function.identity()));
        for (Customer c : customerRepository.findAll()) {
            if (c.isWalkIn() || ("CLIENT".equals(s.selfKind()) && c.getId().equals(s.selfId()))) continue;
            List<String> why = reasons(s, c.getName(), c.getPhone(), c.getTaxId(), c.getEmail());
            if (why.isEmpty()) continue;
            List<MatterRef> refs = allMatters.stream().filter(m -> c.getId().equals(m.getClientCustomerId()) && !m.getId().equals(excludeMatterId)).map(m -> ref(a, m, "CLIENT")).toList();
            if (!refs.isEmpty()) out.add(new ConflictHit("CLIENT", c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getTaxId(), why, severity(s, refs), refs));
        }
        for (LawContact c : contactRepository.findAll()) {
            if ("CONTACT".equals(s.selfKind()) && c.getId().equals(s.selfId())) continue;
            List<String> why = reasons(s, c.getName(), c.getPhone(), c.getIdNumber(), c.getEmail());
            if (why.isEmpty()) continue;
            List<MatterRef> refs = new ArrayList<>();
            for (LawMatterParty p : partyRepository.findByContactId(c.getId())) {
                LawMatter m = byId.get(p.getMatterId());
                if (m != null && !m.getId().equals(excludeMatterId)) refs.add(ref(a, m, p.getRole().name()));
            }
            if (!refs.isEmpty()) out.add(new ConflictHit("CONTACT", c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getIdNumber(), why, severity(s, refs), refs));
        }
        out.sort((x, y) -> x.severity().equals(y.severity()) ? x.name().compareToIgnoreCase(y.name()) : ("HIGH".equals(x.severity()) ? -1 : 1));
        return out;
    }

    private static String severity(Subject s, List<MatterRef> refs) {
        String mine = side(s.role());
        if (mine.isEmpty()) return "REVIEW";
        for (MatterRef r : refs) {
            String theirs = side(r.role());
            if (!theirs.isEmpty() && !theirs.equals(mine)) return "HIGH";
        }
        return "REVIEW";
    }

    private LawConflictCheck log(Actor a, String query, LawMatter m, int hits) {
        LawConflictCheck c = new LawConflictCheck();
        c.setQuery(query.length() > 500 ? query.substring(0, 500) : query);
        c.setHits(hits);
        if (m != null) {
            c.setMatterId(m.getId());
            c.setMatterNumber(m.getNumber());
        }
        c.setCheckedByUserId(a.user().getId());
        c.setCheckedByName(LawAccess.name(a.user()));
        return checkRepository.save(c);
    }

    // ------------------------------------------------------------------ searches

    private void requireLaw(Actor a) {
        access.on();
        if (!a.admin() && !a.viewer() && !access.isLawyer(a)) throw ApiException.forbidden("You cannot run a conflict check.");
    }

    @Transactional
    public ConflictResult search(Actor a, ConflictSearchRequest r) {
        requireLaw(a);
        boolean blank = (r.name() == null || r.name().isBlank()) && (r.phone() == null || r.phone().isBlank()) && (r.idNumber() == null || r.idNumber().isBlank())
                && (r.email() == null || r.email().isBlank());
        if (blank) throw ApiException.badRequest("Enter a name, phone number, ID number or e-mail to search for.");
        Subject s = new Subject(r.name() != null && !r.name().isBlank() ? r.name().trim() : "Search", null, r.name(), r.phone(), r.idNumber(), r.email(), null, null);
        List<ConflictHit> hits = hitsFor(a, s, r.excludeMatterId());
        StringBuilder q = new StringBuilder();
        for (String part : new String[]{r.name(), r.phone(), r.idNumber(), r.email()}) {
            if (part != null && !part.isBlank()) q.append(q.length() > 0 ? " · " : "").append(part.trim());
        }
        LawConflictCheck c = log(a, q.toString(), null, hits.size());
        return new ConflictResult(c.getId(), hits.size(), List.of(new ConflictSubject(s.label(), null, hits)));
    }

    private ConflictResult checkMatter(Actor a, LawMatter m, boolean logIt) {
        List<ConflictSubject> subjects = new ArrayList<>();
        int total = 0;
        Customer client = customerRepository.findById(m.getClientCustomerId()).orElse(null);
        if (client != null) {
            Subject s = new Subject(client.getName(), "CLIENT", client.getName(), client.getPhone(), client.getTaxId(), client.getEmail(), "CLIENT", client.getId());
            List<ConflictHit> hits = hitsFor(a, s, m.getId());
            total += hits.size();
            subjects.add(new ConflictSubject(client.getName(), "CLIENT", hits));
        }
        Map<Long, LawContact> contacts = contactRepository.findAllById(partyRepository.findByMatterIdOrderByIdAsc(m.getId()).stream().map(LawMatterParty::getContactId).toList())
                .stream().collect(Collectors.toMap(LawContact::getId, Function.identity()));
        for (LawMatterParty p : partyRepository.findByMatterIdOrderByIdAsc(m.getId())) {
            LawContact c = contacts.get(p.getContactId());
            if (c == null) continue;
            Subject s = new Subject(c.getName(), p.getRole().name(), c.getName(), c.getPhone(), c.getIdNumber(), c.getEmail(), "CONTACT", c.getId());
            List<ConflictHit> hits = hitsFor(a, s, m.getId());
            total += hits.size();
            subjects.add(new ConflictSubject(c.getName(), p.getRole().name(), hits));
        }
        Long logId = logIt ? log(a, "Matter " + m.getNumber() + " (" + subjects.size() + " name(s))", m, total).getId() : null;
        return new ConflictResult(logId, total, subjects);
    }

    @Transactional
    public ConflictResult check(Actor a, Long matterId) {
        LawMatter m = access.workable(a, matterId);
        return checkMatter(a, m, true);
    }

    @Transactional
    public void clear(Actor a, Long matterId, ClearRequest r) {
        LawMatter m = access.leadable(a, matterId);
        ConflictResult result = checkMatter(a, m, true);
        String note = r.note() != null && !r.note().isBlank() ? r.note().trim() : null;
        if (result.hits() > 0 && note == null) {
            throw ApiException.badRequest("The check found " + result.hits() + " overlap(s). Write a note saying why they do not stop the firm acting, then clear the matter.");
        }
        m.setConflictCleared(true);
        m.setConflictNote(note);
        m.setConflictClearedBy(LawAccess.name(a.user()));
        m.setConflictClearedAt(Instant.now());
        matterRepository.save(m);
        auditLogService.record("LAW_CONFLICT_CLEARED", "Cleared conflicts on matter " + m.getNumber() + (result.hits() > 0 ? " despite " + result.hits() + " overlap(s)" : ""), "LawMatter", m.getId());
    }

    @Transactional(readOnly = true)
    public List<ConflictLogDto> log(Actor a) {
        access.on();
        if (!a.admin() && !a.viewer()) throw ApiException.forbidden("You cannot see the conflict check log.");
        return checkRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(c -> new ConflictLogDto(c.getId(), c.getQuery(), c.getMatterId(), c.getMatterNumber(), c.getHits(), c.getCheckedByName(), c.getCreatedAt())).toList();
    }
}
