package com.posapp.propertymgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PmRentReminderRepository extends JpaRepository<PmRentReminder, Long> {
    List<PmRentReminder> findByRentChargeIdInOrderBySentAtAsc(Collection<Long> rentChargeIds);

    List<PmRentReminder> findByRentChargeIdOrderBySentAtDesc(Long rentChargeId);
}
