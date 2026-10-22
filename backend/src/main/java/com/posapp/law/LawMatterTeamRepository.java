package com.posapp.law;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawMatterTeamRepository extends JpaRepository<LawMatterTeam, Long> {
    List<LawMatterTeam> findByMatterId(Long matterId);

    List<LawMatterTeam> findByUserId(Long userId);

    boolean existsByMatterIdAndUserId(Long matterId, Long userId);

    boolean existsByMatterIdAndUserIdAndRole(Long matterId, Long userId, TeamRole role);

    boolean existsByUserId(Long userId);

    void deleteByMatterId(Long matterId);
}
