package com.skycast.alert.repository;

import com.skycast.alert.domain.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByUsernameOrderByCreatedAtDesc(String username);

    List<AlertRule> findByActiveTrue();

    Optional<AlertRule> findByIdAndUsername(Long id, String username);

    long countByUsername(String username);
}
