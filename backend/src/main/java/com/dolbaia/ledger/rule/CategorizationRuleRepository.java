package com.dolbaia.ledger.rule;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, UUID> {

    @EntityGraph(attributePaths = "category")
    @Query("select r from CategorizationRule r order by r.priority desc, r.keyword")
    List<CategorizationRule> findAllOrdered();
}
