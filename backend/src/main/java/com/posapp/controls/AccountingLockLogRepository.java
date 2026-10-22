package com.posapp.controls;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountingLockLogRepository extends JpaRepository<AccountingLockLog, Long> {
    List<AccountingLockLog> findTop20ByOrderByIdDesc();
}
