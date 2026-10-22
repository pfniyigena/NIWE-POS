package com.posapp.realestate;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReSettingsRepository extends JpaRepository<ReSettings, Long> {
    /** Locks the settings row(s): used to hand out codes one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ReSettings s")
    List<ReSettings> lockAll();
}
