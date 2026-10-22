package com.posapp.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookableServiceStaffRepository extends JpaRepository<BookableServiceStaff, Long> {
    List<BookableServiceStaff> findByServiceId(Long serviceId);

    List<BookableServiceStaff> findByServiceIdIn(Collection<Long> serviceIds);
}
