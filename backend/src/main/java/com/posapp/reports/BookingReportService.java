package com.posapp.reports;

import com.posapp.booking.Appointment;
import com.posapp.booking.AppointmentRepository;
import com.posapp.booking.AppointmentStatus;
import com.posapp.booking.BookableResource;
import com.posapp.booking.BookableResourceRepository;
import com.posapp.booking.BookableServiceStaffRepository;
import com.posapp.booking.BookingSettings;
import com.posapp.booking.BookingSettingsRepository;
import com.posapp.reports.dto.BookingReportRows.*;
import com.posapp.user.AppUser;
import com.posapp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Booking reports: the appointment list, how busy people and rooms are, and how each service performs. */
@Service
public class BookingReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private final AppointmentRepository repository;
    private final BookingSettingsRepository settingsRepository;
    private final BookableServiceStaffRepository serviceStaffRepository;
    private final BookableResourceRepository resourceRepository;
    private final UserRepository userRepository;

    public BookingReportService(AppointmentRepository repository, BookingSettingsRepository settingsRepository, BookableServiceStaffRepository serviceStaffRepository,
                                BookableResourceRepository resourceRepository, UserRepository userRepository) {
        this.repository = repository;
        this.settingsRepository = settingsRepository;
        this.serviceStaffRepository = serviceStaffRepository;
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
    }

    private List<Appointment> between(LocalDate from, LocalDate to) {
        return repository.findByStartAtBetweenOrderByStartAtAsc(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public List<AppointmentRow> appointments(LocalDate from, LocalDate to) {
        return between(from, to).stream().map(a -> new AppointmentRow(a.getStartAt().toLocalDate(), a.getStartAt().toLocalTime().toString(), a.getNumber(), a.getServiceName(),
                a.getGuestName(), a.getStaffName(), a.getResourceName(), a.getStatus().name(), a.getPrice(), a.getAmountPaid(), a.getInvoiceNumber())).toList();
    }

    private static boolean counts(Appointment a) {
        return a.getStatus() == AppointmentStatus.CONFIRMED || a.getStatus() == AppointmentStatus.COMPLETED;
    }

    private static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<UtilisationRow> utilisation(LocalDate from, LocalDate to) {
        BookingSettings s = settingsRepository.findAll().stream().findFirst().orElse(new BookingSettings());
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : s.getWorkDays().split(",")) {
            String t = token.trim().toUpperCase();
            if (t.length() < 3) continue;
            for (DayOfWeek d : DayOfWeek.values()) if (d.name().startsWith(t.substring(0, 3))) days.add(d);
        }
        long workDays = from.datesUntil(to.plusDays(1)).filter(d -> days.contains(d.getDayOfWeek())).count();
        long dayMinutes = Duration.between(s.getDayStart(), s.getDayEnd()).toMinutes();
        BigDecimal available = hours(workDays * dayMinutes);
        List<Appointment> list = between(from, to).stream().filter(BookingReportService::counts).toList();

        Map<Long, String> staffNames = userRepository.findAllById(serviceStaffRepository.findAll().stream().map(x -> x.getUserId()).distinct().toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername(), (a, b) -> a, LinkedHashMap::new));
        List<UtilisationRow> out = new java.util.ArrayList<>();
        for (Map.Entry<Long, String> e : staffNames.entrySet()) {
            List<Appointment> mine = list.stream().filter(a -> e.getKey().equals(a.getStaffUserId())).toList();
            out.add(row("Staff", e.getValue(), mine, available));
        }
        for (BookableResource r : resourceRepository.findAllByOrderByKindAscNameAsc()) {
            if (!r.isActive()) continue;
            List<Appointment> mine = list.stream().filter(a -> r.getId().equals(a.getResourceId())).toList();
            out.add(row("Room / equipment", r.getName(), mine, available));
        }
        return out;
    }

    private static UtilisationRow row(String type, String name, List<Appointment> mine, BigDecimal available) {
        long minutes = mine.stream().mapToLong(a -> Duration.between(a.getStartAt(), a.getEndAt()).toMinutes()).sum();
        BigDecimal booked = hours(minutes);
        BigDecimal pct = available.signum() == 0 ? null : booked.multiply(HUNDRED).divide(available, 1, RoundingMode.HALF_UP);
        return new UtilisationRow(type, name, mine.size(), booked, available, pct);
    }

    @Transactional(readOnly = true)
    public List<ServiceRow> services(LocalDate from, LocalDate to) {
        Map<String, List<Appointment>> byService = between(from, to).stream().collect(Collectors.groupingBy(Appointment::getServiceName, LinkedHashMap::new, Collectors.toList()));
        return byService.entrySet().stream().map(e -> {
            List<Appointment> l = e.getValue();
            int completed = (int) l.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
            int cancelled = (int) l.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();
            int noShows = (int) l.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
            int booked = l.size() - cancelled;
            BigDecimal value = l.stream().filter(BookingReportService::counts).map(Appointment::getPrice).reduce(ZERO, BigDecimal::add);
            BigDecimal pct = booked == 0 ? null : BigDecimal.valueOf(noShows).multiply(HUNDRED).divide(BigDecimal.valueOf(booked), 1, RoundingMode.HALF_UP);
            return new ServiceRow(e.getKey(), booked, completed, cancelled, noShows, pct, value);
        }).sorted(Comparator.comparing(ServiceRow::booked).reversed()).toList();
    }
}
