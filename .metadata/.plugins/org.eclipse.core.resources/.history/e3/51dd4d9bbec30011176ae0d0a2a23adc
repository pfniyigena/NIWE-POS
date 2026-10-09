package com.posapp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByCode(String code);

    /** What AccountingService/AccountingReportService actually resolve automatic-posting accounts by — see Account.wellKnownKey's own javadoc for why this is separate from findByCode. */
    Optional<Account> findByWellKnownKey(String wellKnownKey);

    boolean existsByCodeIgnoreCase(String code);

    List<Account> findAllByOrderByCodeAsc();

    List<Account> findByActiveTrueOrderByCodeAsc();

    List<Account> findByTypeOrderByCodeAsc(AccountType type);

    /** Used by AccountService.listActiveByType — the Expense/Income entry screens' own category picker, scoped to just EXPENSE/REVENUE accounts (see Expense.account/Income.account). */
    List<Account> findByActiveTrueAndTypeOrderByCodeAsc(AccountType type);
}
