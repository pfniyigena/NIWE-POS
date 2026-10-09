package com.posapp.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface HelpDeskTicketRepository extends JpaRepository<HelpDeskTicket, Long> {

    boolean existsByCategoryId(Long categoryId);

    /**
     * The admin ticket list's own filtered query — same null-safe-parameter shape as {@code
     * ProductRepository.search}/{@code AuditLogRepository.search}: every filter is always bound
     * (never omitted from the query, since JPQL can't conditionally include a clause), and an
     * unset one is passed as {@code null} (type/status/priority/categoryId/assignedToUserId) or
     * {@code ""} ({@code q}, for the same PostgreSQL "could not determine data type" reason
     * {@code CatalogService.searchProducts} already works around for its own free-text filter).
     * {@code q} matches the ticket number, subject, or requester name — whichever the admin is
     * more likely to be searching by.
     */
    @Query("select t from HelpDeskTicket t where " +
           "(:type is null or t.type = :type) " +
           "and (:status is null or t.status = :status) " +
           "and (:priority is null or t.priority = :priority) " +
           "and (:categoryId is null or t.category.id = :categoryId) " +
           "and (:assignedToUserId is null or t.assignedToUserId = :assignedToUserId) " +
           "and (:q = '' or lower(t.ticketNumber) like lower(concat('%', :q, '%')) " +
           "or lower(t.subject) like lower(concat('%', :q, '%')) " +
           "or lower(t.requesterName) like lower(concat('%', :q, '%'))) " +
           "order by t.createdAt desc")
    java.util.List<HelpDeskTicket> search(HelpDeskTicketType type, HelpDeskStatus status, HelpDeskPriority priority,
                                            Long categoryId, Long assignedToUserId, String q);

    /**
     * The public "check my ticket" lookup (Fourier: "supporting both authenticated and guest
     * ticket creation" — a guest has no account to log into, so this ticket number + the email
     * they gave at creation is the only thing that proves it's "their" ticket). Case-insensitive
     * on both, same convention as {@code CustomerRepository.findFirstByNameIgnoreCase}.
     */
    Optional<HelpDeskTicket> findByTicketNumberIgnoreCaseAndRequesterEmailIgnoreCase(String ticketNumber, String requesterEmail);

    /** Fourier: "Add Helpdesk reports(XLS,PDF)" — backs HelpDeskTicketService.reportRows; a plain date-range read, same "dateOnly" shape as the simpler reports in ReportController (no other filter — the report is a period export, not the filtered working list {@link #search} already covers). */
    java.util.List<HelpDeskTicket> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
}
