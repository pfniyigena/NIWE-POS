package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HkDtos.CheckResultDto;
import com.posapp.hotel.dto.HkDtos.ChecklistItemDto;
import com.posapp.hotel.dto.HkDtos.HkBoardDto;
import com.posapp.hotel.dto.HkDtos.HkSummaryDto;
import com.posapp.hotel.dto.HkDtos.HkTaskDto;
import com.posapp.hotel.dto.HkDtos.ProductivityDto;
import com.posapp.hotel.dto.HkDtos.StaffDto;
import com.posapp.hotel.dto.HkDtos.StaffLoadDto;
import com.posapp.hotel.dto.HkRequests.ChecklistRequest;
import com.posapp.hotel.dto.HkRequests.InspectCheck;
import com.posapp.hotel.dto.HkRequests.InspectRequest;
import com.posapp.hotel.dto.HkRequests.StaffRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Housekeeping v2: a board of the day's cleaning tasks (departure and stay-over), housekeepers, assignment, start / finish, inspection with a
 * checklist (a failed inspection sends the room back), and do-not-disturb. Room cleaning status stays on the room; this keeps it in step.
 */
@Service
public class HousekeepingService {

    private static final int DEPARTURE_CREDITS = 2;
    private static final int STAYOVER_CREDITS = 1;
    static final String DND_NOTE = "Do not disturb";

    private final HkTaskRepository taskRepository;
    private final HkTaskCheckRepository checkRepository;
    private final HkStaffRepository staffRepository;
    private final HkChecklistItemRepository checklistRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final AuditLogService auditLogService;
    private final HotelClock clock;

    public HousekeepingService(HkTaskRepository taskRepository, HkTaskCheckRepository checkRepository, HkStaffRepository staffRepository,
                               HkChecklistItemRepository checklistRepository, RoomRepository roomRepository, BookingRepository bookingRepository,
                               AuditLogService auditLogService, HotelClock clock) {
        this.taskRepository = taskRepository;
        this.checkRepository = checkRepository;
        this.staffRepository = staffRepository;
        this.checklistRepository = checklistRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- board

    @Transactional(readOnly = true)
    public HkBoardDto board(LocalDate date) {
        LocalDate day = date == null ? clock.today() : date;
        return buildBoard(day);
    }

    /** Prepares today's board: a departure task for every room a guest has left or leaves today, a stay-over task for rooms that stay. Safe to repeat. */
    @Transactional
    public HkBoardDto generate(LocalDate date) {
        LocalDate today = clock.today();
        if (date != null && !date.equals(today)) throw ApiException.badRequest("Boards are prepared for today only");
        Map<Long, Booking> occupants = occupants();
        int created = 0;
        for (Room room : roomRepository.findByActiveTrueOrderByNumberAsc()) {
            if (room.isOutOfOrder()) continue;
            Booking occupant = occupants.get(room.getId());
            HkTaskKind kind = null;
            if (occupant != null) {
                kind = !occupant.getCheckOutDate().isAfter(today) ? HkTaskKind.DEPARTURE : HkTaskKind.STAYOVER;
            } else if (room.getHousekeepingStatus() == HousekeepingStatus.DIRTY) {
                kind = HkTaskKind.DEPARTURE;
            }
            if (kind == null) continue;
            created += ensure(room, today, kind, kind == HkTaskKind.STAYOVER && room.isDnd());
        }
        auditLogService.record("HOTEL_HK_BOARD", "Housekeeping board for " + today + ": " + created + " task(s) added");
        return buildBoard(today);
    }

    /** Called by the night audit: a new day starts, so do-not-disturb is cleared and today's board is prepared. */
    @Transactional
    public void startOfDay() {
        LocalDate today = clock.today();
        for (Room r : roomRepository.findAll()) {
            if (!r.isDnd()) continue;
            r.setDnd(false);
            roomRepository.save(r);
            for (HkTask t : taskRepository.findByRoomIdAndTaskDateOrderByIdAsc(r.getId(), today)) {
                if (t.getStatus() == HkTaskStatus.SKIPPED && DND_NOTE.equals(t.getNotes())) {
                    t.setStatus(HkTaskStatus.OPEN);
                    t.setNotes(null);
                    taskRepository.save(t);
                }
            }
        }
        generate(null);
    }

    /** A guest has left a room (check-out, room move): it needs a departure clean today. */
    @Transactional
    public void roomVacated(Room room) {
        LocalDate today = clock.today();
        room.setDnd(false);
        roomRepository.save(room);
        ensure(room, today, HkTaskKind.DEPARTURE, false);
    }

    /** Makes sure a task of this kind exists for the room and day. Returns 1 when one was added. */
    private int ensure(Room room, LocalDate day, HkTaskKind kind, boolean skip) {
        List<HkTask> existing = taskRepository.findByRoomIdAndTaskDateOrderByIdAsc(room.getId(), day);
        if (kind == HkTaskKind.DEPARTURE) {
            for (HkTask t : existing) {
                if (t.getKind() == HkTaskKind.DEPARTURE) {
                    if (t.getStatus() == HkTaskStatus.DONE) {
                        // cleaned already, then the room was used and vacated again: clean again
                        if (isDirty(room)) {
                            t.setStatus(HkTaskStatus.OPEN);
                            t.setAttempts(t.getAttempts() + 1);
                            t.setInspectionResult(null);
                            t.setFinishedAt(null);
                            t.setStartedAt(null);
                            taskRepository.save(t);
                        }
                    }
                    return 0;
                }
            }
            for (HkTask t : existing) {
                if (t.getKind() == HkTaskKind.STAYOVER && (t.getStatus() == HkTaskStatus.OPEN || t.getStatus() == HkTaskStatus.SKIPPED)) {
                    taskRepository.delete(t); // the guest left: a full clean replaces the stay-over service
                }
            }
        } else {
            if (existing.stream().anyMatch(t -> t.getKind() == HkTaskKind.STAYOVER || t.getKind() == HkTaskKind.DEPARTURE)) return 0;
        }
        HkTask t = new HkTask();
        t.setRoom(room);
        t.setTaskDate(day);
        t.setKind(kind);
        if (skip) {
            t.setStatus(HkTaskStatus.SKIPPED);
            t.setNotes(DND_NOTE);
        }
        taskRepository.save(t);
        return 1;
    }

    private static boolean isDirty(Room room) {
        return room.getHousekeepingStatus() == HousekeepingStatus.DIRTY;
    }

    private Map<Long, Booking> occupants() {
        Map<Long, Booking> map = new HashMap<>();
        for (Booking b : bookingRepository.findByStatusOrderByCheckInDateAscIdAsc(BookingStatus.CHECKED_IN)) {
            if (b.getRoom() != null) map.put(b.getRoom().getId(), b);
        }
        return map;
    }

    private HkBoardDto buildBoard(LocalDate day) {
        List<HkTask> tasks = taskRepository.findByTaskDateOrderByIdAsc(day);
        Map<Long, Booking> occupants = day.equals(clock.today()) ? occupants() : Map.of();
        Set<Long> arriving = new HashSet<>();
        if (day.equals(clock.today())) {
            for (Booking b : bookingRepository.findByStatusAndCheckInDateAndRoomIsNotNull(BookingStatus.BOOKED, day)) arriving.add(b.getRoom().getId());
        }
        List<HkTaskDto> out = new ArrayList<>();
        for (HkTask t : tasks) out.add(toDto(t, occupants.get(t.getRoom().getId()), arriving.contains(t.getRoom().getId())));
        out.sort(Comparator.comparing((HkTaskDto d) -> !d.arrivalToday()).thenComparing(d -> !"DEPARTURE".equals(d.kind()))
                .thenComparing(d -> d.floor() == null ? "" : d.floor()).thenComparing(HkTaskDto::roomNumber));
        int open = 0, prog = 0, done = 0, skipped = 0, dep = 0, stay = 0, insp = 0, failed = 0;
        Map<Long, int[]> load = new LinkedHashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (HkTaskDto d : out) {
            switch (d.status()) {
                case "OPEN" -> open++;
                case "IN_PROGRESS" -> prog++;
                case "DONE" -> done++;
                default -> skipped++;
            }
            if ("DEPARTURE".equals(d.kind())) dep++;
            else stay++;
            if ("PASS".equals(d.inspectionResult())) insp++;
            if ("FAIL".equals(d.inspectionResult()) && !"DONE".equals(d.status())) failed++;
            if (d.staffId() != null) {
                int[] l = load.computeIfAbsent(d.staffId(), k -> new int[3]);
                names.put(d.staffId(), d.staffName());
                if (!"SKIPPED".equals(d.status())) {
                    l[0]++;
                    l[2] += "DEPARTURE".equals(d.kind()) ? DEPARTURE_CREDITS : STAYOVER_CREDITS;
                }
                if ("DONE".equals(d.status())) l[1]++;
            }
        }
        List<StaffLoadDto> loads = new ArrayList<>();
        load.forEach((id, l) -> loads.add(new StaffLoadDto(id, names.get(id), l[0], l[1], l[2])));
        loads.sort(Comparator.comparing(StaffLoadDto::staffName));
        return new HkBoardDto(day, out, new HkSummaryDto(out.size(), open, prog, done, skipped, dep, stay, insp, failed), loads);
    }

    private HkTaskDto toDto(HkTask t, Booking occupant, boolean arrivalToday) {
        Room r = t.getRoom();
        List<CheckResultDto> checks = new ArrayList<>();
        for (HkTaskCheck c : checkRepository.findByTaskIdOrderByIdAsc(t.getId())) checks.add(new CheckResultDto(c.getLabel(), c.isPassed()));
        return new HkTaskDto(t.getId(), r.getId(), r.getNumber(), r.getFloor(), r.getRoomType().getName(),
                r.getHousekeepingStatus() == null ? "CLEAN" : r.getHousekeepingStatus().name(), t.getKind().name(), t.getStatus().name(),
                t.getStaff() == null ? null : t.getStaff().getId(), t.getStaff() == null ? null : t.getStaff().getName(), r.isDnd(), occupant != null,
                occupant == null ? null : occupant.getGuest().getFullName(), occupant == null ? null : occupant.getCheckOutDate(), arrivalToday,
                t.getNotes(), t.getStartedAt(), t.getFinishedAt(), t.minutes(), t.getInspectionResult(), t.getInspectionNote(), t.getAttempts(), checks);
    }

    // ---------------------------------------------------------------- working a task

    HkTask taskOrThrow(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task not found: " + id));
    }

    @Transactional
    public HkTaskDto assign(Long taskId, Long staffId) {
        HkTask t = taskOrThrow(taskId);
        if (t.getStatus() == HkTaskStatus.DONE) throw ApiException.badRequest("This room is already cleaned");
        t.setStaff(staffId == null ? null : activeStaff(staffId));
        taskRepository.save(t);
        if (t.getStaff() != null) {
            t.getRoom().setHousekeeper(t.getStaff().getName());
            roomRepository.save(t.getRoom());
        }
        return dto(t);
    }

    /** Hands the day's unassigned tasks to housekeepers, always to whoever has the least work so far (a departure counts for 2, a stay-over for 1). */
    @Transactional
    public HkBoardDto autoAssign(LocalDate date, List<Long> staffIds) {
        LocalDate day = date == null ? clock.today() : date;
        List<HkStaff> pool = new ArrayList<>();
        if (staffIds == null || staffIds.isEmpty()) {
            for (HkStaff s : staffRepository.findAllByOrderByNameAsc()) if (s.isActive()) pool.add(s);
        } else {
            for (Long id : staffIds) pool.add(activeStaff(id));
        }
        if (pool.isEmpty()) throw ApiException.badRequest("Add at least one housekeeper first");
        List<HkTask> tasks = new ArrayList<>(taskRepository.findByTaskDateOrderByIdAsc(day));
        Map<Long, Integer> credits = new HashMap<>();
        for (HkStaff s : pool) credits.put(s.getId(), 0);
        for (HkTask t : tasks) {
            if (t.getStaff() != null && credits.containsKey(t.getStaff().getId()) && t.getStatus() != HkTaskStatus.SKIPPED) {
                credits.merge(t.getStaff().getId(), weight(t), Integer::sum);
            }
        }
        List<HkTask> todo = new ArrayList<>();
        for (HkTask t : tasks) if (t.getStaff() == null && (t.getStatus() == HkTaskStatus.OPEN || t.getStatus() == HkTaskStatus.IN_PROGRESS)) todo.add(t);
        todo.sort(Comparator.comparing((HkTask t) -> t.getKind() != HkTaskKind.DEPARTURE).thenComparing(t -> t.getRoom().getFloor() == null ? "" : t.getRoom().getFloor())
                .thenComparing(t -> t.getRoom().getNumber()));
        for (HkTask t : todo) {
            HkStaff best = pool.get(0);
            for (HkStaff s : pool) if (credits.get(s.getId()) < credits.get(best.getId())) best = s;
            t.setStaff(best);
            credits.merge(best.getId(), weight(t), Integer::sum);
            taskRepository.save(t);
        }
        auditLogService.record("HOTEL_HK_ASSIGN", todo.size() + " task(s) assigned on " + day);
        return buildBoard(day);
    }

    private static int weight(HkTask t) {
        return t.getKind() == HkTaskKind.DEPARTURE ? DEPARTURE_CREDITS : STAYOVER_CREDITS;
    }

    @Transactional
    public HkTaskDto start(Long taskId, Long staffId) {
        HkTask t = taskOrThrow(taskId);
        requireToday(t);
        if (t.getStatus() != HkTaskStatus.OPEN) throw ApiException.badRequest("Only an open task can be started");
        requireVacant(t);
        if (staffId != null) t.setStaff(activeStaff(staffId));
        t.setStatus(HkTaskStatus.IN_PROGRESS);
        t.setStartedAt(Instant.now());
        taskRepository.save(t);
        Room room = t.getRoom();
        room.setHousekeepingStatus(HousekeepingStatus.CLEANING);
        if (t.getStaff() != null) room.setHousekeeper(t.getStaff().getName());
        roomRepository.save(room);
        return dto(t);
    }

    @Transactional
    public HkTaskDto finish(Long taskId) {
        HkTask t = taskOrThrow(taskId);
        requireToday(t);
        if (t.getStatus() != HkTaskStatus.OPEN && t.getStatus() != HkTaskStatus.IN_PROGRESS) throw ApiException.badRequest("This task is not being worked on");
        requireVacant(t);
        Instant now = Instant.now();
        if (t.getStartedAt() == null) t.setStartedAt(now);
        t.setFinishedAt(now);
        t.setStatus(HkTaskStatus.DONE);
        taskRepository.save(t);
        Room room = t.getRoom();
        room.setHousekeepingStatus(HousekeepingStatus.CLEAN);
        roomRepository.save(room);
        return dto(t);
    }

    @Transactional
    public HkTaskDto skip(Long taskId, String reason) {
        HkTask t = taskOrThrow(taskId);
        if (t.getStatus() != HkTaskStatus.OPEN && t.getStatus() != HkTaskStatus.IN_PROGRESS) throw ApiException.badRequest("Only a task not yet done can be skipped");
        t.setStatus(HkTaskStatus.SKIPPED);
        t.setNotes(reason == null || reason.isBlank() ? "Skipped" : reason.trim());
        taskRepository.save(t);
        if (t.getRoom().getHousekeepingStatus() == HousekeepingStatus.CLEANING) {
            t.getRoom().setHousekeepingStatus(HousekeepingStatus.DIRTY);
            roomRepository.save(t.getRoom());
        }
        return dto(t);
    }

    @Transactional
    public HkTaskDto reopen(Long taskId) {
        HkTask t = taskOrThrow(taskId);
        if (t.getStatus() != HkTaskStatus.SKIPPED) throw ApiException.badRequest("Only a skipped task can be reopened");
        if (DND_NOTE.equals(t.getNotes()) && t.getRoom().isDnd()) throw ApiException.badRequest("Room " + t.getRoom().getNumber() + " is on do-not-disturb");
        t.setStatus(HkTaskStatus.OPEN);
        t.setNotes(null);
        taskRepository.save(t);
        return dto(t);
    }

    /** A supervisor checks a cleaned room against the checklist. All good: the room is inspected. Anything failed: the task reopens and the room is dirty again. */
    @Transactional
    public HkTaskDto inspect(Long taskId, InspectRequest request) {
        HkTask t = taskOrThrow(taskId);
        if (t.getStatus() != HkTaskStatus.DONE) throw ApiException.badRequest("Only a cleaned room can be inspected");
        List<InspectCheck> checks = request == null || request.checks() == null ? List.of() : request.checks();
        boolean allOk = checks.stream().allMatch(c -> Boolean.TRUE.equals(c.passed()));
        boolean passed = checks.isEmpty() ? request != null && Boolean.TRUE.equals(request.passed()) : allOk && (request == null || request.passed() == null || request.passed());
        if (request != null && Boolean.TRUE.equals(request.passed()) && !checks.isEmpty() && !allOk) {
            throw ApiException.badRequest("A checklist line failed — the room cannot pass");
        }
        checkRepository.deleteByTaskId(taskId);
        for (InspectCheck c : checks) {
            if (c.label() == null || c.label().isBlank()) continue;
            HkTaskCheck row = new HkTaskCheck();
            row.setTask(t);
            row.setLabel(c.label().trim());
            row.setPassed(Boolean.TRUE.equals(c.passed()));
            checkRepository.save(row);
        }
        t.setInspectedAt(Instant.now());
        t.setInspectionNote(request == null || request.note() == null || request.note().isBlank() ? null : request.note().trim());
        Room room = t.getRoom();
        if (passed) {
            t.setInspectionResult("PASS");
            room.setHousekeepingStatus(HousekeepingStatus.INSPECTED);
        } else {
            t.setInspectionResult("FAIL");
            t.setStatus(HkTaskStatus.OPEN);
            t.setAttempts(t.getAttempts() + 1);
            t.setStartedAt(null);
            t.setFinishedAt(null);
            room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
        }
        roomRepository.save(room);
        taskRepository.save(t);
        auditLogService.record("HOTEL_HK_INSPECTION", "Room " + room.getNumber() + " " + t.getInspectionResult(), "Room", room.getId());
        return dto(t);
    }

    private HkTaskDto dto(HkTask t) {
        Booking occupant = occupants().get(t.getRoom().getId());
        boolean arriving = t.getTaskDate().equals(clock.today()) && bookingRepository
                .findByStatusAndCheckInDateAndRoomIsNotNull(BookingStatus.BOOKED, t.getTaskDate()).stream().anyMatch(b -> b.getRoom().getId().equals(t.getRoom().getId()));
        return toDto(t, occupant, arriving);
    }

    /** A departure clean cannot start while the departing guest is still in the room. */
    private void requireVacant(HkTask t) {
        if (t.getKind() == HkTaskKind.DEPARTURE && occupants().containsKey(t.getRoom().getId())) {
            throw ApiException.badRequest("The guest in room " + t.getRoom().getNumber() + " has not checked out yet");
        }
    }

    private void requireToday(HkTask t) {
        if (!t.getTaskDate().equals(clock.today())) throw ApiException.badRequest("Only today's tasks can be worked on");
    }

    // ---------------------------------------------------------------- do not disturb

    @Transactional
    public HkBoardDto setDnd(Long roomId, boolean dnd) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> ApiException.notFound("Room not found: " + roomId));
        if (dnd && !occupants().containsKey(roomId)) throw ApiException.badRequest("Room " + room.getNumber() + " has no guest in it");
        room.setDnd(dnd);
        roomRepository.save(room);
        LocalDate today = clock.today();
        for (HkTask t : taskRepository.findByRoomIdAndTaskDateOrderByIdAsc(roomId, today)) {
            if (t.getKind() != HkTaskKind.STAYOVER) continue;
            if (dnd && (t.getStatus() == HkTaskStatus.OPEN)) {
                t.setStatus(HkTaskStatus.SKIPPED);
                t.setNotes(DND_NOTE);
                taskRepository.save(t);
            } else if (!dnd && t.getStatus() == HkTaskStatus.SKIPPED && DND_NOTE.equals(t.getNotes())) {
                t.setStatus(HkTaskStatus.OPEN);
                t.setNotes(null);
                taskRepository.save(t);
            }
        }
        auditLogService.record("HOTEL_HK_DND", "Room " + room.getNumber() + (dnd ? " on" : " off") + " do-not-disturb", "Room", roomId);
        return buildBoard(today);
    }

    /** Keeps tasks in step when a room's status is changed directly on the rooms grid. */
    @Transactional
    public void roomStatusChanged(Room room, HousekeepingStatus status) {
        LocalDate today = clock.today();
        for (HkTask t : taskRepository.findByRoomIdAndTaskDateOrderByIdAsc(room.getId(), today)) {
            if (status == HousekeepingStatus.CLEANING && t.getStatus() == HkTaskStatus.OPEN) {
                t.setStatus(HkTaskStatus.IN_PROGRESS);
                t.setStartedAt(Instant.now());
                taskRepository.save(t);
            } else if ((status == HousekeepingStatus.CLEAN || status == HousekeepingStatus.INSPECTED)
                    && (t.getStatus() == HkTaskStatus.OPEN || t.getStatus() == HkTaskStatus.IN_PROGRESS)) {
                Instant now = Instant.now();
                if (t.getStartedAt() == null) t.setStartedAt(now);
                t.setFinishedAt(now);
                t.setStatus(HkTaskStatus.DONE);
                if (status == HousekeepingStatus.INSPECTED) {
                    t.setInspectionResult("PASS");
                    t.setInspectedAt(now);
                }
                taskRepository.save(t);
            } else if (status == HousekeepingStatus.INSPECTED && t.getStatus() == HkTaskStatus.DONE && !"PASS".equals(t.getInspectionResult())) {
                t.setInspectionResult("PASS");
                t.setInspectedAt(Instant.now());
                taskRepository.save(t);
            }
        }
    }

    // ---------------------------------------------------------------- productivity

    @Transactional(readOnly = true)
    public List<ProductivityDto> productivity(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (end.isBefore(start)) throw ApiException.badRequest("The end date is before the start");
        Map<Long, List<HkTask>> byStaff = new LinkedHashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (HkTask t : taskRepository.findByTaskDateBetweenOrderByTaskDateAscIdAsc(start, end)) {
            if (t.getStaff() == null) continue;
            byStaff.computeIfAbsent(t.getStaff().getId(), k -> new ArrayList<>()).add(t);
            names.put(t.getStaff().getId(), t.getStaff().getName());
        }
        List<ProductivityDto> out = new ArrayList<>();
        byStaff.forEach((id, list) -> {
            int dep = 0, stay = 0, insp = 0, fail = 0, done = 0;
            long depMin = 0, stayMin = 0;
            int depN = 0, stayN = 0;
            for (HkTask t : list) {
                if (t.getInspectionResult() != null) {
                    insp++;
                    if ("FAIL".equals(t.getInspectionResult())) fail++;
                }
                if (t.getStatus() != HkTaskStatus.DONE) continue;
                done++;
                Long m = t.minutes();
                if (t.getKind() == HkTaskKind.DEPARTURE) {
                    dep++;
                    if (m != null) { depMin += m; depN++; }
                } else {
                    stay++;
                    if (m != null) { stayMin += m; stayN++; }
                }
            }
            out.add(new ProductivityDto(id, names.get(id), done, dep, stay, depN == 0 ? null : depMin / depN, stayN == 0 ? null : stayMin / stayN, insp, fail));
        });
        out.sort(Comparator.comparing(ProductivityDto::staffName));
        return out;
    }

    // ---------------------------------------------------------------- staff and checklist

    @Transactional(readOnly = true)
    public List<StaffDto> staff() {
        List<StaffDto> out = new ArrayList<>();
        for (HkStaff s : staffRepository.findAllByOrderByNameAsc()) out.add(new StaffDto(s.getId(), s.getName(), s.isActive(), taskRepository.countByStaffId(s.getId())));
        return out;
    }

    private HkStaff activeStaff(Long id) {
        HkStaff s = staffRepository.findById(id).orElseThrow(() -> ApiException.badRequest("Housekeeper not found"));
        if (!s.isActive()) throw ApiException.badRequest(s.getName() + " is not active");
        return s;
    }

    @Transactional
    public StaffDto addStaff(StaffRequest r) {
        String name = r.name().trim();
        if (staffRepository.existsByNameIgnoreCase(name)) throw ApiException.conflict("There is already a housekeeper called " + name);
        HkStaff s = new HkStaff();
        s.setName(name);
        s.setActive(r.active() == null || r.active());
        HkStaff saved = staffRepository.save(s);
        return new StaffDto(saved.getId(), saved.getName(), saved.isActive(), 0);
    }

    @Transactional
    public StaffDto updateStaff(Long id, StaffRequest r) {
        HkStaff s = staffRepository.findById(id).orElseThrow(() -> ApiException.notFound("Housekeeper not found"));
        String name = r.name().trim();
        if (!s.getName().equalsIgnoreCase(name) && staffRepository.existsByNameIgnoreCase(name)) throw ApiException.conflict("There is already a housekeeper called " + name);
        s.setName(name);
        if (r.active() != null) s.setActive(r.active());
        staffRepository.save(s);
        return new StaffDto(s.getId(), s.getName(), s.isActive(), taskRepository.countByStaffId(id));
    }

    @Transactional
    public void deleteStaff(Long id) {
        HkStaff s = staffRepository.findById(id).orElseThrow(() -> ApiException.notFound("Housekeeper not found"));
        if (taskRepository.countByStaffId(id) > 0) throw ApiException.conflict(s.getName() + " has cleaning history — make them inactive instead");
        staffRepository.delete(s);
    }

    @Transactional(readOnly = true)
    public List<ChecklistItemDto> checklist(boolean activeOnly) {
        List<ChecklistItemDto> out = new ArrayList<>();
        for (HkChecklistItem i : activeOnly ? checklistRepository.findByActiveTrueOrderBySortOrderAscIdAsc() : checklistRepository.findAllByOrderBySortOrderAscIdAsc()) {
            out.add(new ChecklistItemDto(i.getId(), i.getLabel(), i.getSortOrder(), i.isActive()));
        }
        return out;
    }

    /** Replaces the whole checklist, in the order given. */
    @Transactional
    public List<ChecklistItemDto> saveChecklist(ChecklistRequest r) {
        if (r == null || r.items() == null || r.items().isEmpty()) throw ApiException.badRequest("The checklist needs at least one line");
        Set<String> seen = new HashSet<>();
        for (var line : r.items()) {
            if (!seen.add(line.label().trim().toLowerCase())) throw ApiException.badRequest("\"" + line.label().trim() + "\" is listed twice");
        }
        checklistRepository.deleteAll();
        checklistRepository.flush();
        int n = 1;
        for (var line : r.items()) {
            HkChecklistItem i = new HkChecklistItem();
            i.setLabel(line.label().trim());
            i.setSortOrder(n++);
            i.setActive(line.active() == null || line.active());
            checklistRepository.save(i);
        }
        return checklist(false);
    }
}
