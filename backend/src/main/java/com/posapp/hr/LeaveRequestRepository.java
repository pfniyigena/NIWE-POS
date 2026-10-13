package com.posapp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    java.util.List<LeaveRequest> findAllByOrderByStartDateDescIdDesc();

    java.util.List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    /** Requests that overlap the range, whatever their status. */
    java.util.List<LeaveRequest> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(java.time.LocalDate to, java.time.LocalDate from);

    boolean existsByLeaveTypeId(Long leaveTypeId);
}
