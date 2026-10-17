package com.dolbaia.ledger.obligation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ObligationEntryRepository extends JpaRepository<ObligationEntry, UUID> {

    @EntityGraph(attributePaths = {"party", "obligationCase", "category"})
    @Query("select e from ObligationEntry e order by e.date desc, e.createdAt desc")
    List<ObligationEntry> findLatest(Limit limit);

    /** Outstanding per party over all of its entries: outlays minus reimbursements. */
    @Query("select e.party.id as partyId,"
            + " sum(case when e.type = com.dolbaia.ledger.obligation.ObligationType.OUTLAY"
            + " then abs(e.amount) else -abs(e.amount) end) as outstanding"
            + " from ObligationEntry e group by e.party.id")
    List<PartyOutstanding> outstandingByParty();

    interface PartyOutstanding {
        UUID getPartyId();
        BigDecimal getOutstanding();
    }
}
