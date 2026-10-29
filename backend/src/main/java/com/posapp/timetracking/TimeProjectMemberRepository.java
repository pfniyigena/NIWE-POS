package com.posapp.timetracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeProjectMemberRepository extends JpaRepository<TimeProjectMember, Long> {

    List<TimeProjectMember> findByProjectId(Long projectId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    List<TimeProjectMember> findByUserId(Long userId);

    void deleteByProjectId(Long projectId);
}
