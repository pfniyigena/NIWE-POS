package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findAllByOrderByNumberAsc();

    List<Room> findByRoomTypeIdAndActiveTrueOrderByNumberAsc(Long roomTypeId);

    List<Room> findByActiveTrueOrderByNumberAsc();

    long countByRoomTypeId(Long roomTypeId);

    boolean existsByNumberIgnoreCase(String number);

    boolean existsByNumberIgnoreCaseAndIdNot(String number, Long id);
}
