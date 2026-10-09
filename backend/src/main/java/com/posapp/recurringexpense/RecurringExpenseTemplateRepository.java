package com.posapp.recurringexpense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RecurringExpenseTemplateRepository extends JpaRepository<RecurringExpenseTemplate, Long> {

    List<RecurringExpenseTemplate> findAllByOrderByNameAsc();

    /** The scheduled sweep's own input — every still-active template whose next billing date has arrived (or passed, e.g. the app was offline that day). */
    List<RecurringExpenseTemplate> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);
}
