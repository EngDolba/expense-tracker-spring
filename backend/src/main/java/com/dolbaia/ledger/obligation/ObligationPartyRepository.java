package com.dolbaia.ledger.obligation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ObligationPartyRepository extends JpaRepository<ObligationParty, UUID> {

    @Query("select p from ObligationParty p where p.archived = false order by p.name")
    List<ObligationParty> findActive();
}
