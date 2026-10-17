package com.dolbaia.ledger.obligation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ObligationCaseRepository extends JpaRepository<ObligationCase, UUID> {

    @Query("select c from ObligationCase c where c.closed = false order by c.createdAt desc")
    List<ObligationCase> findOpen();
}
