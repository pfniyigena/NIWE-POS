package com.posapp.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findAllByOrderByReservedForAsc();

    /** Looks up a booking by its "reservation number" — see Reservation's own javadoc. */
    Optional<Reservation> findByConfirmationCode(String confirmationCode);

    boolean existsByConfirmationCode(String confirmationCode);

    /** Backs both the public availability check and the once-per-day table-exclusivity check — see ReservationService.assertTableAvailable/availableTables. */
    List<Reservation> findByStatusAndReservedForBetween(ReservationStatus status, Instant from, Instant to);
}
