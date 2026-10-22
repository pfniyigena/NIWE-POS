package com.posapp.law;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LawSettingsRepository extends JpaRepository<LawSettings, Long> {

    /** Locks the settings row so matter numbers are handed out one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LawSettings s order by s.id")
    List<LawSettings> lockAll();
}
