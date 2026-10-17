package com.dolbaia.ledger.account;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountEventRepository extends JpaRepository<AccountEvent, UUID> {

    @Query("select e from AccountEvent e where e.account.id in :accountIds order by e.date desc, e.createdAt desc")
    List<AccountEvent> findForAccounts(Collection<UUID> accountIds);

    List<AccountEvent> findByAccountId(UUID accountId);

    @EntityGraph(attributePaths = {"account", "counterpartyAccount"})
    @Query("select e from AccountEvent e order by e.date, e.createdAt")
    List<AccountEvent> findAllChronological();
}
