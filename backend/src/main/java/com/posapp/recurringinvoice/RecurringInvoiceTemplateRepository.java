package com.posapp.recurringinvoice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RecurringInvoiceTemplateRepository extends JpaRepository<RecurringInvoiceTemplate, Long> {

    List<RecurringInvoiceTemplate> findAllByOrderByNameAsc();

    /** One customer's own recurring invoice templates — backs the Recurring invoices screen's own `?customerId=` filter, same convention as SubscriptionRepository.findByCustomerIdOrderByIdDesc. */
    List<RecurringInvoiceTemplate> findByCustomerIdOrderByNameAsc(Long customerId);

    /** The scheduled sweep's own input — every still-active template whose next run date has arrived (or passed, e.g. the app was offline that day). */
    List<RecurringInvoiceTemplate> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);
}
