package com.dolbaia.ledger.expense;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExpenseEntryRepository extends JpaRepository<ExpenseEntry, UUID> {

    @EntityGraph(attributePaths = "category")
    @Query("select e from ExpenseEntry e order by e.date desc, e.createdAt desc")
    List<ExpenseEntry> findLatest(Limit limit);

    @EntityGraph(attributePaths = "category")
    @Query("select e from ExpenseEntry e order by e.date, e.createdAt")
    List<ExpenseEntry> findAllChronological();
}
