package com.posapp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findAllByOrderByIncomeDateDesc();

    /** The Incomes reports' raw input (segmented and periodic alike — see AccountingReportService.incomesGrouped). */
    List<Income> findByIncomeDateBetweenOrderByIncomeDateAsc(LocalDate from, LocalDate to);
}
