package com.posapp.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Appointments whose busy period overlaps [from, to). */
    List<Appointment> findByStartAtLessThanAndBusyUntilGreaterThanOrderByStartAtAsc(LocalDateTime to, LocalDateTime from);

    List<Appointment> findByStartAtBetweenOrderByStartAtAsc(LocalDateTime from, LocalDateTime to);

    List<Appointment> findByStaffUserIdAndStartAtGreaterThanEqualOrderByStartAtAsc(Long staffUserId, LocalDateTime from);

    List<Appointment> findByStatus(AppointmentStatus status);

    Optional<Appointment> findByConfirmationCode(String code);

    Optional<Appointment> findFirstByInvoiceId(Long invoiceId);

    List<Appointment> findByStatusAndReminderSentAtIsNullAndStartAtBetweenOrderByStartAtAsc(AppointmentStatus status, LocalDateTime from, LocalDateTime to);

    List<Appointment> findByCustomerId(Long customerId);

    List<Appointment> findByGuestPhone(String guestPhone);

    long countByServiceId(Long serviceId);

    long countByResourceId(Long resourceId);

    long countBySourceAndGuestPhoneAndCreatedAtGreaterThanEqual(AppointmentSource source, String guestPhone, java.time.Instant since);
}
