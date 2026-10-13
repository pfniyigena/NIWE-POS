package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {
    List<MaintenanceRequest> findAllByOrderByIdDesc();

    boolean existsByRoomIdAndBlocksRoomTrueAndStatusNot(Long roomId, MaintenanceStatus status);

    long countByRoomId(Long roomId);
}
