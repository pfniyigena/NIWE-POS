package com.posapp.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    /** Task: "Enable credit for customer" — used by CustomerCreditService callers that need only credit customers. */
    List<Customer> findByCreditEnabledTrue();

    /** DataSeeder's walk-in-customer backfill — see Customer.walkIn's javadoc. */
    boolean existsByWalkInTrue();

    /** DataSeeder's walk-in-customer backfill — matches an existing store's own "Walk-in Customer" row by name so upgrading doesn't require an admin to do anything by hand. */
    Optional<Customer> findFirstByNameIgnoreCase(String name);

    /**
     * Fourier: "Add Help Desk module ... supporting both authenticated and guest ticket creation"
     * — a guest submitting the public Help Desk form gives an email but no customer id; {@code
     * HelpDeskTicketService.createByGuest} tries this match so a returning customer's ticket
     * still links to their own record automatically. Email isn't a unique column on Customer, so
     * this is a best-effort "first match wins" lookup, same shape as {@code findFirstByNameIgnoreCase}
     * above — good enough for a denormalized convenience link, not something anything else in the
     * app depends on being exact.
     */
    Optional<Customer> findFirstByEmailIgnoreCase(String email);
}
