package com.posapp.hotel;

import com.posapp.audit.AuditLogService;
import com.posapp.common.ApiException;
import com.posapp.hotel.dto.HkDtos.LinenBalanceDto;
import com.posapp.hotel.dto.HkDtos.LinenLogDto;
import com.posapp.hotel.dto.HkDtos.MinibarItemDto;
import com.posapp.hotel.dto.HkDtos.MinibarPostedDto;
import com.posapp.hotel.dto.HkRequests.LinenRequest;
import com.posapp.hotel.dto.HkRequests.MinibarItemRequest;
import com.posapp.hotel.dto.HkRequests.MinibarLine;
import com.posapp.hotel.dto.HkRequests.MinibarUseRequest;
import com.posapp.hotel.dto.HotelRequests.ChargeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minibar items and what a guest used (billed to the room), and the linen going to and coming back from the laundry. */
@Service
public class SuppliesService {

    private final MinibarItemRepository minibarRepository;
    private final LinenLogRepository linenRepository;
    private final BookingRepository bookingRepository;
    private final HotelFolioService folioService;
    private final AuditLogService auditLogService;
    private final HotelClock clock;

    public SuppliesService(MinibarItemRepository minibarRepository, LinenLogRepository linenRepository, BookingRepository bookingRepository,
                           HotelFolioService folioService, AuditLogService auditLogService, HotelClock clock) {
        this.minibarRepository = minibarRepository;
        this.linenRepository = linenRepository;
        this.bookingRepository = bookingRepository;
        this.folioService = folioService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- minibar

    @Transactional(readOnly = true)
    public List<MinibarItemDto> minibarItems(boolean activeOnly) {
        List<MinibarItemDto> out = new ArrayList<>();
        for (MinibarItem i : minibarRepository.findAllByOrderByNameAsc()) {
            if (activeOnly && !i.isActive()) continue;
            out.add(toDto(i));
        }
        return out;
    }

    @Transactional
    public MinibarItemDto addItem(MinibarItemRequest r) {
        String name = r.name().trim();
        if (minibarRepository.existsByNameIgnoreCase(name)) throw ApiException.conflict("There is already a minibar item called " + name);
        MinibarItem i = new MinibarItem();
        apply(i, r, name);
        return toDto(minibarRepository.save(i));
    }

    @Transactional
    public MinibarItemDto updateItem(Long id, MinibarItemRequest r) {
        MinibarItem i = itemOrThrow(id);
        String name = r.name().trim();
        if (!i.getName().equalsIgnoreCase(name) && minibarRepository.existsByNameIgnoreCase(name)) throw ApiException.conflict("There is already a minibar item called " + name);
        apply(i, r, name);
        return toDto(minibarRepository.save(i));
    }

    @Transactional
    public void deleteItem(Long id) {
        minibarRepository.delete(itemOrThrow(id));
    }

    private void apply(MinibarItem i, MinibarItemRequest r, String name) {
        if (r.price().signum() <= 0) throw ApiException.badRequest("The price must be more than zero");
        i.setName(name);
        i.setPrice(r.price().setScale(2, java.math.RoundingMode.HALF_UP));
        i.setTaxable(r.taxable() == null || r.taxable());
        i.setActive(r.active() == null || r.active());
    }

    private MinibarItem itemOrThrow(Long id) {
        return minibarRepository.findById(id).orElseThrow(() -> ApiException.notFound("Minibar item not found: " + id));
    }

    private static MinibarItemDto toDto(MinibarItem i) {
        return new MinibarItemDto(i.getId(), i.getName(), i.getPrice(), i.isTaxable(), i.isActive());
    }

    /** Puts what a guest took from the minibar on their bill, at the catalogue prices (one line per item). */
    @Transactional
    public MinibarPostedDto useMinibar(MinibarUseRequest r) {
        if (r.lines() == null || r.lines().isEmpty()) throw ApiException.badRequest("Nothing was entered");
        Booking booking = bookingRepository.findByStatusAndRoomId(BookingStatus.CHECKED_IN, r.roomId()).stream().findFirst()
                .orElseThrow(() -> ApiException.badRequest("There is no guest in that room"));
        BigDecimal total = BigDecimal.ZERO;
        int lines = 0;
        List<Object[]> todo = new ArrayList<>();
        for (MinibarLine l : r.lines()) {
            if (l.qty() == null || l.qty() < 0 || l.qty() > 100) throw ApiException.badRequest("Quantities must be between 0 and 100");
            if (l.qty() == 0) continue;
            MinibarItem item = itemOrThrow(l.itemId());
            if (!item.isActive()) throw ApiException.badRequest(item.getName() + " is not on sale");
            todo.add(new Object[]{item, l.qty()});
        }
        if (todo.isEmpty()) throw ApiException.badRequest("Nothing was entered");
        for (Object[] t : todo) {
            MinibarItem item = (MinibarItem) t[0];
            int qty = (Integer) t[1];
            BigDecimal net = item.getPrice().multiply(BigDecimal.valueOf(qty));
            folioService.addExtraCharge(booking.getId(), new ChargeRequest("Minibar: " + item.getName() + (qty > 1 ? " x" + qty : ""), net, item.isTaxable(), null));
            total = total.add(net);
            lines++;
        }
        auditLogService.record("HOTEL_MINIBAR", booking.getBookingNumber() + " room " + booking.getRoom().getNumber() + ": " + lines + " item(s), " + total + " before tax",
                "Booking", booking.getId());
        return new MinibarPostedDto(booking.getId(), booking.getBookingNumber(), booking.getRoom().getNumber(), booking.getGuest().getFullName(), lines, total);
    }

    // ---------------------------------------------------------------- linen

    @Transactional(readOnly = true)
    public List<LinenLogDto> linenLog(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        List<LinenLogDto> out = new ArrayList<>();
        for (LinenLog l : linenRepository.findByLogDateBetweenOrderByLogDateDescIdDesc(start, end)) {
            out.add(new LinenLogDto(l.getId(), l.getLogDate(), l.getItem(), l.getSent(), l.getReturned(), l.getDamaged(), l.getNotes()));
        }
        return out;
    }

    @Transactional
    public LinenLogDto addLinen(LinenRequest r) {
        int sent = nz(r.sent()), returned = nz(r.returned()), damaged = nz(r.damaged());
        if (sent < 0 || returned < 0 || damaged < 0) throw ApiException.badRequest("Counts cannot be negative");
        if (sent + returned + damaged == 0) throw ApiException.badRequest("Enter how many went out, came back or were damaged");
        LocalDate date = r.logDate() == null ? clock.today() : r.logDate();
        if (date.isAfter(clock.today())) throw ApiException.badRequest("The date cannot be in the future");
        LinenLog l = new LinenLog();
        l.setLogDate(date);
        l.setItem(r.item().trim());
        l.setSent(sent);
        l.setReturned(returned);
        l.setDamaged(damaged);
        l.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
        LinenLog saved = linenRepository.save(l);
        auditLogService.record("HOTEL_LINEN", saved.getItem() + ": " + sent + " sent, " + returned + " back, " + damaged + " damaged", "LinenLog", saved.getId());
        return new LinenLogDto(saved.getId(), saved.getLogDate(), saved.getItem(), sent, returned, damaged, saved.getNotes());
    }

    @Transactional
    public void deleteLinen(Long id) {
        if (!linenRepository.existsById(id)) throw ApiException.notFound("Entry not found");
        linenRepository.deleteById(id);
    }

    /** Per item over all time: pieces sent, returned, damaged, and so what should still be at the laundry. */
    @Transactional(readOnly = true)
    public List<LinenBalanceDto> linenBalance() {
        Map<String, int[]> map = new LinkedHashMap<>();
        for (LinenLog l : linenRepository.findAll()) {
            int[] v = map.computeIfAbsent(l.getItem().toLowerCase(), k -> new int[4]);
            v[0] += l.getSent();
            v[1] += l.getReturned();
            v[2] += l.getDamaged();
        }
        Map<String, String> display = new LinkedHashMap<>();
        for (LinenLog l : linenRepository.findAll()) display.putIfAbsent(l.getItem().toLowerCase(), l.getItem());
        List<LinenBalanceDto> out = new ArrayList<>();
        map.forEach((k, v) -> out.add(new LinenBalanceDto(display.get(k), v[0], v[1], v[2], v[0] - v[1] - v[2])));
        out.sort(Comparator.comparing(LinenBalanceDto::item, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
