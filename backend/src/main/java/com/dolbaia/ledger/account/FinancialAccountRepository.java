package com.dolbaia.ledger.account;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    @Query("select a from FinancialAccount a where a.archived = false order by a.name")
    List<FinancialAccount> findActive();
}
