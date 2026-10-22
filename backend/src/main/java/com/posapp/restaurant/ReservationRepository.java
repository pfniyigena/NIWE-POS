package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findAllByOrderByReservedForAsc();

    /** Looks up a booking by its "reservation number" — see Reservation's own javadoc. */
    Optional<Reservation> findByConfirmationCode(String confirmationCode);

    boolean existsByConfirmationCode(String confirmationCode);

    /** Bookings of the given statuses that start inside a window — what the table-clash check looks at. */
    List<Reservation> findByStatusInAndReservedForBetween(Collection<ReservationStatus> statuses, Instant from, Instant to);

    List<Reservation> findByStatusAndReservedForBetween(ReservationStatus status, Instant from, Instant to);

    List<Reservation> findByStatus(ReservationStatus status);
}
