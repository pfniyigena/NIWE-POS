package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.propertymgmt.PmOwnership.Share;
import com.posapp.propertymgmt.PmOwnership.Split;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtOwnershipDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Who owns a property. A property has one or more owners, each with a percentage; the owners and percentages can change from a date (a part is sold, an heir joins). The rent paid on a day
 * is shared by the split in force that day, and every co-owner gets their own statement for their part. A split that an agreed statement already covers cannot be changed. The lead owner
 * of the newest split is kept on the property itself ({@code owner_id}), so everything that shows "the owner" keeps working.
 */
@Service
public class PmOwnershipService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MAX_OWNERS = 12;
    private static final LocalDate LONG_AGO = LocalDate.of(1900, 1, 1);

    private final PmPropertyOwnerRepository repository;
    private final PmPropertyRepository propertyRepository;
    private final PmOwnerRepository ownerRepository;
    private final PmOwnerStatementRepository statementRepository;
    private final PmStatementLineRepository lineRepository;
    private final PmViews views;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    public PmOwnershipService(PmPropertyOwnerRepository repository, PmPropertyRepository propertyRepository, PmOwnerRepository ownerRepository, PmOwnerStatementRepository statementRepository,
                              PmStatementLineRepository lineRepository, PmViews views, PropertyManagementAccess access, AuditLogService auditLogService) {
        this.repository = repository;
        this.propertyRepository = propertyRepository;
        this.ownerRepository = ownerRepository;
        this.statementRepository = statementRepository;
        this.lineRepository = lineRepository;
        this.views = views;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private PmProperty property(Long id) {
        return propertyRepository.findById(id).orElseThrow(() -> ApiException.notFound("Property not found: " + id));
    }

    private static String key(LocalDate from) {
        return from == null ? "start" : from.toString();
    }

    private static LocalDate parseKey(String key) {
        if (key == null || key.isBlank() || key.equalsIgnoreCase("start")) return null;
        try {
            return LocalDate.parse(key.trim());
        } catch (Exception e) {
            throw ApiException.badRequest("That is not a date: " + key);
        }
    }

    /** Called when a property is made: its owner has all of it. */
    void start(PmProperty p) {
        PmPropertyOwner r = new PmPropertyOwner();
        r.setPropertyId(p.getId());
        r.setOwnerId(p.getOwnerId());
        r.setSharePercent(HUNDRED);
        r.setLeadOwner(true);
        repository.save(r);
    }

    void deleteFor(Long propertyId) {
        repository.deleteByPropertyId(propertyId);
    }

    // ------------------------------------------------------------------ reading

    /** Why a split from this date cannot change: the last day an agreed statement covers is on or after its start. Null when it can. */
    private static String lock(LocalDate from, LocalDate lockedThrough) {
        if (lockedThrough == null) return null;
        if (from != null && from.isAfter(lockedThrough)) return null;
        return "An agreed owner statement already covers this property up to " + lockedThrough + ".";
    }

    @Transactional(readOnly = true)
    public OwnershipView get(Actor a, Long propertyId) {
        access.on();
        PmProperty p = property(propertyId);
        return view(a, views.snapshot(), p, List.of());
    }

    private OwnershipView view(Actor a, Snapshot s, PmProperty p, List<String> rebuild) {
        List<Split> splits = s.splits.getOrDefault(p.getId(), List.of());
        LocalDate lockedThrough = lineRepository.lastAgreedDay(p.getId(), StatementStatus.DRAFT);
        List<SplitDto> out = new ArrayList<>();
        for (int i = splits.size() - 1; i >= 0; i--) {
            Split sp = splits.get(i);
            List<ShareDto> shares = sp.shares().stream().map(x -> {
                PmOwner o = s.owners.get(x.ownerId());
                return new ShareDto(x.ownerId(), o == null ? null : o.getCode(), o == null ? "owner " + x.ownerId() : o.getName(), x.percent(), x.lead());
            }).toList();
            String why = lock(sp.from(), lockedThrough);
            out.add(new SplitDto(sp.from(), key(sp.from()), i == splits.size() - 1, shares, why != null, why, why == null && splits.size() > 1));
        }
        PmOwner lead = s.owners.get(p.getOwnerId());
        return new OwnershipView(p.getId(), p.getCode(), p.getName(), p.getOwnerId(), lead == null ? null : lead.getName(), out, lockedThrough, rebuild, a.manager());
    }

    // ------------------------------------------------------------------ changing

    @Transactional
    public OwnershipView set(Actor a, Long propertyId, OwnershipRequest r) {
        access.on();
        PmProperty p = property(propertyId);
        if (r == null || r.shares() == null || r.shares().isEmpty()) throw ApiException.badRequest("Give at least one owner.");
        if (r.shares().size() > MAX_OWNERS) throw ApiException.badRequest("A property can have at most " + MAX_OWNERS + " owners.");
        LocalDate from = r.validFrom();
        if (from != null && from.isAfter(LocalDate.now())) throw ApiException.badRequest("The change cannot start in the future: pick today or an earlier day.");

        Snapshot s = views.snapshot();
        List<Split> before = s.splits.getOrDefault(propertyId, List.of());
        Split latest = PmOwnership.latest(before);

        Set<Long> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ShareRequest x : r.shares()) {
            if (x == null || x.ownerId() == null) throw ApiException.badRequest("Choose the owner on every line.");
            PmOwner o = ownerRepository.findById(x.ownerId()).orElseThrow(() -> ApiException.badRequest("An owner you chose does not exist."));
            if (!seen.add(o.getId())) throw ApiException.badRequest(o.getName() + " is on the list twice.");
            if (!o.isActive() && (latest == null || latest.of(o.getId()) == null)) throw ApiException.badRequest(o.getName() + " is inactive.");
            if (x.percent() == null) throw ApiException.badRequest("Give the percentage of " + o.getName() + ".");
            if (x.percent().signum() <= 0 || x.percent().compareTo(HUNDRED) > 0) throw ApiException.badRequest("The percentage of " + o.getName() + " must be above 0 and at most 100.");
            if (x.percent().stripTrailingZeros().scale() > 2) throw ApiException.badRequest("Use at most two decimals in a percentage.");
            total = total.add(x.percent());
        }
        if (total.compareTo(HUNDRED) != 0) throw ApiException.badRequest("The percentages add up to " + total.stripTrailingZeros().toPlainString() + "%. They must add up to 100%.");

        Long leadId = r.leadOwnerId();
        if (leadId == null) {
            leadId = r.shares().stream().max(Comparator.comparing(ShareRequest::percent).thenComparing(x -> -x.ownerId())).get().ownerId();
        } else if (!seen.contains(leadId)) {
            throw ApiException.badRequest("The main owner must be one of the owners in the list.");
        }

        LocalDate lockedThrough = lineRepository.lastAgreedDay(propertyId, StatementStatus.DRAFT);
        String why = lock(from, lockedThrough);
        if (why != null) throw ApiException.conflict(why + " Start the new split on a day after that.");

        List<PmPropertyOwner> existing = repository.findByPropertyId(propertyId);
        List<PmPropertyOwner> same = existing.stream().filter(x -> java.util.Objects.equals(x.getValidFrom(), from)).toList();
        repository.deleteAll(same);
        repository.flush();
        List<PmPropertyOwner> fresh = new ArrayList<>();
        for (ShareRequest x : r.shares()) {
            PmPropertyOwner row = new PmPropertyOwner();
            row.setPropertyId(propertyId);
            row.setOwnerId(x.ownerId());
            row.setSharePercent(x.percent().setScale(2, java.math.RoundingMode.UNNECESSARY));
            row.setValidFrom(from);
            row.setLeadOwner(x.ownerId().equals(leadId));
            fresh.add(row);
        }
        repository.saveAll(fresh);
        repository.flush();
        String label = describe(r.shares(), s);
        return afterChange(a, p, from, same.isEmpty() ? "Set the owners of " + p.getName() + " from " + (from == null ? "the start" : from) + ": " + label : "Changed the owners of " + p.getName() + " from " + (from == null ? "the start" : from) + ": " + label);
    }

    @Transactional
    public OwnershipView remove(Actor a, Long propertyId, String key) {
        access.on();
        PmProperty p = property(propertyId);
        LocalDate from = parseKey(key);
        List<PmPropertyOwner> existing = repository.findByPropertyId(propertyId);
        List<PmPropertyOwner> mine = existing.stream().filter(x -> java.util.Objects.equals(x.getValidFrom(), from)).toList();
        if (mine.isEmpty()) throw ApiException.notFound("There is no split starting " + (from == null ? "at the beginning" : "on " + from) + ".");
        if (existing.stream().map(PmPropertyOwner::getValidFrom).distinct().count() < 2) throw ApiException.badRequest("A property must keep at least one split: change it instead.");
        String why = lock(from, lineRepository.lastAgreedDay(propertyId, StatementStatus.DRAFT));
        if (why != null) throw ApiException.conflict(why);
        repository.deleteAll(mine);
        repository.flush();
        return afterChange(a, p, from, "Removed the owners split of " + p.getName() + " that started " + (from == null ? "at the beginning" : "on " + from));
    }

    private OwnershipView afterChange(Actor a, PmProperty p, LocalDate from, String audit) {
        Snapshot s = views.snapshot();
        Split latest = PmOwnership.latest(s.splits.get(p.getId()));
        if (latest != null) {
            p.setOwnerId(latest.lead().ownerId());
            propertyRepository.save(p);
        }
        auditLogService.record("PM_PROPERTY_OWNERS_CHANGED", audit, "PmProperty", p.getId());
        List<String> rebuild = lineRepository.draftStatementIds(p.getId(), from == null ? LONG_AGO : from, StatementStatus.DRAFT).stream()
                .map(id -> statementRepository.findById(id).map(PmOwnerStatement::getCode).orElse(null)).filter(x -> x != null).sorted().toList();
        return view(a, s, p, rebuild);
    }

    private static String describe(List<ShareRequest> shares, Snapshot s) {
        List<String> parts = new ArrayList<>();
        for (ShareRequest x : shares) {
            PmOwner o = s.owners.get(x.ownerId());
            parts.add((o == null ? "owner " + x.ownerId() : o.getName()) + " " + x.percent().stripTrailingZeros().toPlainString() + "%");
        }
        return String.join(", ", parts);
    }

    /** A short text for lists: "Ann 60% · Bob 40%" (just the name when there is one owner). */
    public static String label(Snapshot s, Long propertyId) {
        Split sp = PmOwnership.latest(s.splits.get(propertyId));
        if (sp == null) return null;
        if (sp.shares().size() == 1) {
            PmOwner o = s.owners.get(sp.shares().get(0).ownerId());
            return o == null ? null : o.getName();
        }
        List<String> parts = new ArrayList<>();
        for (Share x : sp.shares()) {
            PmOwner o = s.owners.get(x.ownerId());
            parts.add((o == null ? "?" : o.getName()) + " " + x.percent().stripTrailingZeros().toPlainString() + "%");
        }
        return String.join(" · ", parts);
    }
}
