package com.posapp.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HkTaskCheckRepository extends JpaRepository<HkTaskCheck, Long> {

    java.util.List<HkTaskCheck> findByTaskIdOrderByIdAsc(Long taskId);

    void deleteByTaskId(Long taskId);
}
