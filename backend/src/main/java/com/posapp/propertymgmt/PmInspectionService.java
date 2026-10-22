package com.posapp.propertymgmt;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.propertymgmt.PmViews.Snapshot;
import com.posapp.propertymgmt.PropertyManagementAccess.Actor;
import com.posapp.propertymgmt.dto.PropertyMgmtDepositDtos.*;
import com.posapp.propertymgmt.dto.PropertyMgmtDtos.LeaseRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Inspections of a unit with the tenant: a move-in list when the keys are handed over, a move-out list when they come back (started from the move-in list, so every item shows what it
 * was and what it is now), and visits in between. A list is filled in as a draft, then signed off, which locks it. Photos and papers go on it as documents. The repair costs on a
 * move-out list are what the deposit screen offers to keep.
 */
@Service
public class PmInspectionService {

    static final int MAX_ITEMS = 300;

    /** The standard list for each kind of room. */
    private static final Map<String, List<String>> TEMPLATE = new LinkedHashMap<>();

    static {
        TEMPLATE.put("Living room", List.of("Walls and paint", "Floor", "Ceiling", "Windows and curtains", "Doors and locks", "Lights and sockets"));
        TEMPLATE.put("Kitchen", List.of("Sink and taps", "Cupboards", "Cooker or hob", "Walls and floor", "Lights and sockets"));
        TEMPLATE.put("Bathroom", List.of("Toilet", "Shower or bath", "Basin and taps", "Tiles", "Water heater"));
        TEMPLATE.put("Bedroom", List.of("Walls and paint", "Floor", "Windows", "Wardrobe", "Doors and locks", "Lights and sockets"));
        TEMPLATE.put("Outside", List.of("Gate and fence", "Compound", "Water tank", "Security lights"));
        TEMPLATE.put("General", List.of("Keys and remotes", "Electricity meter", "Water meter", "Smoke or fire equipment"));
    }

    private final PmInspectionRepository repository;
    private final PmInspectionItemRepository itemRepository;
    private final PmLeaseRepository leaseRepository;
    private final PmViews views;
    private final PmSettingsService settings;
    private final PmDocumentService documentService;
    private final PropertyManagementAccess access;
    private final AuditLogService auditLogService;

    public PmInspectionService(PmInspectionRepository repository, PmInspectionItemRepository itemRepository, PmLeaseRepository leaseRepository, PmViews views, PmSettingsService settings,
                               PmDocumentService documentService, PropertyManagementAccess access, AuditLogService auditLogService) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.leaseRepository = leaseRepository;
        this.views = views;
        this.settings = settings;
        this.documentService = documentService;
        this.access = access;
        this.auditLogService = auditLogService;
    }

    private PmInspection find(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Inspection not found: " + id));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static boolean problem(PmItemCondition c) {
        return c == PmItemCondition.POOR || c == PmItemCondition.DAMAGED || c == PmItemCondition.MISSING;
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<InspectionRow> list(Long leaseId, PmInspectionKind kind, PmInspectionStatus status, String q, Long tenantId, Long propertyId) {
        access.on();
        Snapshot s = views.snapshot();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<PmInspection> found = (leaseId != null ? repository.findByLeaseIdOrderByInspectionDateDescIdDesc(leaseId) : repository.findAll()).stream()
                .filter(i -> kind == null || i.getKind() == kind).filter(i -> status == null || i.getStatus() == status).toList();
        Map<Long, List<PmInspectionItem>> items = itemRepository.findAll().stream().collect(Collectors.groupingBy(PmInspectionItem::getInspectionId));
        List<InspectionRow> out = new ArrayList<>();
        for (PmInspection i : found) {
            PmLease l = s.byId.get(i.getLeaseId());
            if (l == null) continue;
            LeaseRow r = views.leaseRow(s, l);
            if (tenantId != null && !tenantId.equals(r.tenantId())) continue;
            if (propertyId != null && !propertyId.equals(r.propertyId())) continue;
            if (!needle.isEmpty() && !(i.getCode().toLowerCase(Locale.ROOT).contains(needle) || r.code().toLowerCase(Locale.ROOT).contains(needle)
                    || r.tenantName() != null && r.tenantName().toLowerCase(Locale.ROOT).contains(needle) || r.propertyName() != null && r.propertyName().toLowerCase(Locale.ROOT).contains(needle)
                    || r.unitNo() != null && r.unitNo().toLowerCase(Locale.ROOT).contains(needle))) continue;
            List<PmInspectionItem> its = items.getOrDefault(i.getId(), List.of());
            out.add(new InspectionRow(i.getId(), i.getCode(), l.getId(), l.getCode(), r.tenantName(), r.propertyName(), r.unitNo(), i.getKind(), i.getStatus(), i.getInspectionDate(), i.getInspectorName(),
                    its.size(), (int) its.stream().filter(x -> x.getItemCondition() != null).count(), (int) its.stream().filter(x -> problem(x.getItemCondition())).count(),
                    its.stream().map(x -> nz(x.getRepairCost())).reduce(BigDecimal.ZERO, BigDecimal::add), i.isTenantAcknowledged()));
        }
        out.sort(Comparator.comparing(InspectionRow::date).reversed().thenComparing(Comparator.comparing(InspectionRow::id).reversed()));
        return out;
    }

    @Transactional(readOnly = true)
    public InspectionDto get(Actor a, Long id) {
        access.on();
        return dto(a, views.snapshot(), find(id));
    }

    private InspectionDto dto(Actor a, Snapshot s, PmInspection i) {
        PmLease l = s.byId.get(i.getLeaseId());
        LeaseRow r = views.leaseRow(s, l);
        List<PmInspectionItem> items = itemRepository.findByInspectionIdOrderBySortOrderAscIdAsc(i.getId());
        List<ItemDto> dtos = items.stream().map(x -> new ItemDto(x.getId(), x.getSortOrder(), x.getRoom(), x.getItem(), x.getItemCondition(), x.getBaselineCondition(),
                PmItemCondition.worse(x.getItemCondition(), x.getBaselineCondition()), x.getNotes(), x.getRepairCost())).toList();
        Long moveIn = i.getKind() == PmInspectionKind.MOVE_OUT ? repository.findByLeaseIdOrderByInspectionDateDescIdDesc(l.getId()).stream().filter(x -> x.getKind() == PmInspectionKind.MOVE_IN)
                .map(PmInspection::getId).findFirst().orElse(null) : null;
        return new InspectionDto(i.getId(), i.getCode(), l.getId(), l.getCode(), r.tenantId(), r.tenantName(), r.propertyId(), r.propertyName(), r.unitId(), r.unitNo(), i.getKind(), i.getStatus(),
                i.getInspectionDate(), i.getInspectorName(), i.getElectricityReading(), i.getWaterReading(), i.getKeysHanded(), i.getNotes(), i.isTenantAcknowledged(), i.getTenantSignedName(),
                i.getSignedOffOn(), i.getSignedOffByName(), i.getCreatedByName(), dtos, (int) items.stream().filter(x -> x.getItemCondition() != null).count(),
                (int) items.stream().filter(x -> problem(x.getItemCondition())).count(), (int) dtos.stream().filter(ItemDto::worse).count(),
                items.stream().map(x -> nz(x.getRepairCost())).reduce(BigDecimal.ZERO, BigDecimal::add), moveIn, a.manager());
    }

    // ------------------------------------------------------------------ writing

    private static LocalDate date(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d == null) return today;
        if (d.isAfter(today)) throw ApiException.badRequest("The date cannot be in the future.");
        if (d.isBefore(today.minusYears(15))) throw ApiException.badRequest("That date is too far back.");
        return d;
    }

    private void addRoom(Long inspectionId, String room, int[] order) {
        String key = room.replaceAll("\\s*\\d+$", "").trim();
        List<String> template = TEMPLATE.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(key)).map(Map.Entry::getValue).findFirst().orElse(List.of("General condition"));
        for (String name : template) {
            PmInspectionItem it = new PmInspectionItem();
            it.setInspectionId(inspectionId);
            it.setSortOrder(order[0]++);
            it.setRoom(room);
            it.setItem(name);
            itemRepository.save(it);
        }
    }

    @Transactional
    public InspectionDto create(Actor a, Long leaseId, InspectionRequest r) {
        access.on();
        PmLease l = leaseRepository.findById(leaseId).orElseThrow(() -> ApiException.notFound("Lease not found: " + leaseId));
        if (r == null || r.kind() == null) throw ApiException.badRequest("Choose the kind of inspection.");
        boolean over = l.getStatus() == LeaseStatus.ENDED || l.getStatus() == LeaseStatus.TERMINATED;
        switch (r.kind()) {
            case MOVE_IN -> {
                if (over) throw ApiException.conflict("This lease is over, so there is no move-in to record.");
            }
            case MOVE_OUT -> {
                if (l.getStatus() == LeaseStatus.DRAFT) throw ApiException.conflict("This lease has not started, so there is no move-out yet.");
            }
            case PERIODIC -> {
                if (l.getStatus() != LeaseStatus.ACTIVE) throw ApiException.conflict("A visit can be recorded only on a lease in force.");
            }
        }
        if (r.kind() != PmInspectionKind.PERIODIC && repository.existsByLeaseIdAndKind(leaseId, r.kind()))
            throw ApiException.conflict("This lease already has a " + (r.kind() == PmInspectionKind.MOVE_IN ? "move-in" : "move-out") + " inspection.");
        Snapshot s = views.snapshot();
        PmInspection i = new PmInspection();
        i.setCode(settings.nextCode("INS"));
        i.setLeaseId(leaseId);
        i.setKind(r.kind());
        i.setInspectionDate(date(r.date()));
        i.setInspectorName(PmUtil.clean(r.inspectorName(), 200, "The inspector") != null ? PmUtil.clean(r.inspectorName(), 200, "The inspector") : PropertyManagementAccess.name(a.user()));
        i.setCreatedByName(PropertyManagementAccess.name(a.user()));
        i = repository.save(i);
        int[] order = {0};
        PmInspection moveIn = r.kind() == PmInspectionKind.MOVE_OUT && !Boolean.FALSE.equals(r.copyFromMoveIn())
                ? repository.findByLeaseIdOrderByInspectionDateDescIdDesc(leaseId).stream().filter(x -> x.getKind() == PmInspectionKind.MOVE_IN).findFirst().orElse(null) : null;
        if (moveIn != null) {
            for (PmInspectionItem src : itemRepository.findByInspectionIdOrderBySortOrderAscIdAsc(moveIn.getId())) {
                PmInspectionItem it = new PmInspectionItem();
                it.setInspectionId(i.getId());
                it.setSortOrder(order[0]++);
                it.setRoom(src.getRoom());
                it.setItem(src.getItem());
                it.setBaselineCondition(src.getItemCondition());
                itemRepository.save(it);
            }
        } else {
            List<String> rooms = new ArrayList<>();
            if (r.rooms() != null && !r.rooms().isEmpty()) {
                for (String room : r.rooms()) {
                    String t = PmUtil.clean(room, 80, "A room name");
                    if (t != null) rooms.add(t);
                }
                if (rooms.size() > 30) throw ApiException.badRequest("At most 30 rooms.");
            } else {
                PmUnit u = s.units.get(l.getUnitId());
                int bedrooms = u == null || u.getBedrooms() == null ? 1 : u.getBedrooms();
                int bathrooms = u == null || u.getBathrooms() == null ? 1 : Math.max(1, u.getBathrooms());
                rooms.add("Living room");
                rooms.add("Kitchen");
                for (int b = 1; b <= bathrooms; b++) rooms.add(bathrooms == 1 ? "Bathroom" : "Bathroom " + b);
                for (int b = 1; b <= bedrooms; b++) rooms.add(bedrooms == 1 ? "Bedroom" : "Bedroom " + b);
                rooms.add("General");
            }
            for (String room : rooms) addRoom(i.getId(), room, order);
        }
        if (order[0] > MAX_ITEMS) throw ApiException.badRequest("An inspection can have at most " + MAX_ITEMS + " items.");
        auditLogService.record("PM_INSPECTION_CREATED", "Started " + r.kind().name().toLowerCase(Locale.ROOT).replace('_', '-') + " inspection " + i.getCode() + " on lease " + l.getCode(), "PmInspection", i.getId());
        return dto(a, s, i);
    }

    private PmInspection draft(Long id) {
        PmInspection i = find(id);
        if (i.getStatus() != PmInspectionStatus.DRAFT) throw ApiException.conflict("This inspection is signed off. Reopen it to change it.");
        return i;
    }

    @Transactional
    public InspectionDto update(Actor a, Long id, InspectionUpdate r) {
        access.on();
        PmInspection i = draft(id);
        if (r.date() != null) i.setInspectionDate(date(r.date()));
        if (r.inspectorName() != null) i.setInspectorName(PmUtil.clean(r.inspectorName(), 200, "The inspector"));
        if (r.electricityReading() != null) i.setElectricityReading(PmUtil.clean(r.electricityReading(), 40, "The electricity reading"));
        if (r.waterReading() != null) i.setWaterReading(PmUtil.clean(r.waterReading(), 40, "The water reading"));
        if (r.keysHanded() != null) {
            if (r.keysHanded() < 0 || r.keysHanded() > 99) throw ApiException.badRequest("The number of keys is between 0 and 99.");
            i.setKeysHanded(r.keysHanded());
        }
        if (r.notes() != null) i.setNotes(PmUtil.clean(r.notes(), 2000, "The notes"));
        repository.save(i);
        return dto(a, views.snapshot(), i);
    }

    private PmInspectionItem item(PmInspection i, Long itemId) {
        PmInspectionItem it = itemRepository.findById(itemId).orElseThrow(() -> ApiException.notFound("Item not found: " + itemId));
        if (!it.getInspectionId().equals(i.getId())) throw ApiException.notFound("Item not found: " + itemId);
        return it;
    }

    private void applyItem(PmInspectionItem it, ItemRequest r) {
        if (r.room() != null) it.setRoom(PmUtil.required(r.room(), 80, "The room"));
        if (r.item() != null) it.setItem(PmUtil.required(r.item(), 120, "The item"));
        if (Boolean.TRUE.equals(r.clearCondition())) it.setItemCondition(null);
        else if (r.condition() != null) it.setItemCondition(r.condition());
        if (r.notes() != null) it.setNotes(PmUtil.clean(r.notes(), 500, "The note"));
        if (Boolean.TRUE.equals(r.clearCost())) it.setRepairCost(null);
        else if (r.repairCost() != null) {
            if (r.repairCost().signum() < 0) throw ApiException.badRequest("The repair cost cannot be negative.");
            it.setRepairCost(r.repairCost().setScale(2, RoundingMode.HALF_UP));
        }
    }

    @Transactional
    public InspectionDto updateItem(Actor a, Long id, Long itemId, ItemRequest r) {
        access.on();
        PmInspection i = draft(id);
        PmInspectionItem it = item(i, itemId);
        applyItem(it, r);
        itemRepository.save(it);
        return dto(a, views.snapshot(), i);
    }

    @Transactional
    public InspectionDto addItem(Actor a, Long id, ItemRequest r) {
        access.on();
        PmInspection i = draft(id);
        if (itemRepository.countByInspectionId(id) >= MAX_ITEMS) throw ApiException.badRequest("An inspection can have at most " + MAX_ITEMS + " items.");
        PmInspectionItem it = new PmInspectionItem();
        it.setInspectionId(id);
        it.setSortOrder(itemRepository.findByInspectionIdOrderBySortOrderAscIdAsc(id).stream().mapToInt(PmInspectionItem::getSortOrder).max().orElse(-1) + 1);
        it.setRoom(PmUtil.required(r.room(), 80, "The room"));
        it.setItem(PmUtil.required(r.item(), 120, "The item"));
        applyItem(it, r);
        itemRepository.save(it);
        return dto(a, views.snapshot(), i);
    }

    @Transactional
    public InspectionDto deleteItem(Actor a, Long id, Long itemId) {
        access.on();
        PmInspection i = draft(id);
        itemRepository.delete(item(i, itemId));
        return dto(a, views.snapshot(), i);
    }

    @Transactional
    public InspectionDto signOff(Actor a, Long id, SignOffRequest r) {
        access.on();
        PmInspection i = draft(id);
        List<PmInspectionItem> items = itemRepository.findByInspectionIdOrderBySortOrderAscIdAsc(id);
        if (items.isEmpty()) throw ApiException.badRequest("Add at least one item before signing off.");
        long open = items.stream().filter(x -> x.getItemCondition() == null).count();
        if (open > 0) throw ApiException.badRequest(open + " item" + (open == 1 ? " has" : "s have") + " not been looked at yet. Give each a condition (or Not applicable).");
        boolean ack = r != null && Boolean.TRUE.equals(r.tenantAcknowledged());
        String name = r == null ? null : PmUtil.clean(r.tenantSignedName(), 200, "The tenant's name");
        if (ack && name == null) throw ApiException.badRequest("Enter the name of the person who signed for the tenant.");
        i.setTenantAcknowledged(ack);
        i.setTenantSignedName(ack ? name : null);
        i.setStatus(PmInspectionStatus.SIGNED_OFF);
        i.setSignedOffOn(LocalDate.now());
        i.setSignedOffByName(PropertyManagementAccess.name(a.user()));
        repository.save(i);
        auditLogService.record("PM_INSPECTION_SIGNED_OFF", "Signed off inspection " + i.getCode(), "PmInspection", id);
        return dto(a, views.snapshot(), i);
    }

    @Transactional
    public InspectionDto reopen(Actor a, Long id) {
        access.on();
        PmInspection i = find(id);
        if (i.getStatus() != PmInspectionStatus.SIGNED_OFF) throw ApiException.conflict("This inspection is not signed off.");
        i.setStatus(PmInspectionStatus.DRAFT);
        i.setSignedOffOn(null);
        i.setSignedOffByName(null);
        i.setTenantAcknowledged(false);
        i.setTenantSignedName(null);
        repository.save(i);
        auditLogService.record("PM_INSPECTION_REOPENED", "Reopened inspection " + i.getCode(), "PmInspection", id);
        return dto(a, views.snapshot(), i);
    }

    @Transactional
    public void delete(Long id) {
        access.on();
        PmInspection i = draft(id);
        removeAll(i);
        auditLogService.record("PM_INSPECTION_DELETED", "Deleted inspection " + i.getCode());
    }

    private void removeAll(PmInspection i) {
        documentService.deleteFor(PmDocSubject.INSPECTION, i.getId());
        itemRepository.deleteByInspectionId(i.getId());
        repository.delete(i);
    }

    /** The lease is being deleted (a draft): its inspections go with it. */
    @Transactional
    public void deleteForLease(Long leaseId) {
        for (PmInspection i : repository.findByLeaseIdOrderByInspectionDateDescIdDesc(leaseId)) removeAll(i);
    }

    // ------------------------------------------------------------------ the printed list

    /** The columns and rows of the printed list, for the PDF and spreadsheet. */
    @Transactional(readOnly = true)
    public Printout printout(Long id) {
        access.on();
        PmInspection i = find(id);
        Snapshot s = views.snapshot();
        PmLease l = s.byId.get(i.getLeaseId());
        LeaseRow r = views.leaseRow(s, l);
        boolean compare = i.getKind() == PmInspectionKind.MOVE_OUT;
        String kind = switch (i.getKind()) {
            case MOVE_IN -> "Move-in inspection";
            case MOVE_OUT -> "Move-out inspection";
            case PERIODIC -> "Inspection visit";
        };
        String title = kind + " " + i.getCode() + " - lease " + l.getCode() + " - " + r.propertyName() + " unit " + r.unitNo() + " - " + r.tenantName() + " - " + i.getInspectionDate();
        List<String> headers = compare ? List.of("Room", "Item", "At move-in", "Now", "Notes", "Repair cost") : List.of("Room", "Item", "Condition", "Notes");
        List<List<String>> rows = new ArrayList<>();
        java.util.function.BiConsumer<String, String> info = (k, v) -> {
            if (compare) rows.add(List.of("Handover", k, "", v, "", ""));
            else rows.add(List.of("Handover", k, v, ""));
        };
        info.accept("Inspector", i.getInspectorName() == null ? "-" : i.getInspectorName());
        info.accept("Electricity meter", i.getElectricityReading() == null ? "-" : i.getElectricityReading());
        info.accept("Water meter", i.getWaterReading() == null ? "-" : i.getWaterReading());
        info.accept("Keys handed", i.getKeysHanded() == null ? "-" : String.valueOf(i.getKeysHanded()));
        for (PmInspectionItem x : itemRepository.findByInspectionIdOrderBySortOrderAscIdAsc(id)) {
            String now = x.getItemCondition() == null ? "-" : x.getItemCondition().name();
            String note = x.getNotes() == null ? "" : x.getNotes();
            if (compare) rows.add(List.of(x.getRoom(), x.getItem(), x.getBaselineCondition() == null ? "-" : x.getBaselineCondition().name(), now, note, x.getRepairCost() == null ? "" : x.getRepairCost().toPlainString()));
            else rows.add(List.of(x.getRoom(), x.getItem(), now, note));
        }
        String sign = i.getStatus() == PmInspectionStatus.SIGNED_OFF ? "Signed off " + i.getSignedOffOn() + " by " + i.getSignedOffByName()
                + (i.isTenantAcknowledged() ? "; acknowledged for the tenant by " + i.getTenantSignedName() : "; not acknowledged by the tenant") : "Draft - not signed off";
        if (compare) rows.add(List.of("Sign-off", sign, "", "", i.getNotes() == null ? "" : i.getNotes(), ""));
        else rows.add(List.of("Sign-off", sign, "", i.getNotes() == null ? "" : i.getNotes()));
        return new Printout(title, headers, rows);
    }

    public record Printout(String title, List<String> headers, List<List<String>> rows) {
    }
}
