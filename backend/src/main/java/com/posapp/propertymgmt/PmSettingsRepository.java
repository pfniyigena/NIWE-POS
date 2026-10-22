package com.posapp.propertymgmt;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PmSettingsRepository extends JpaRepository<PmSettings, Long> {
    /** Locks the settings row(s): used to hand out codes one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PmSettings s")
    List<PmSettings> lockAll();
}
