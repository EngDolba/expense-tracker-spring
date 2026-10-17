package com.dolbaia.ledger.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    long countByUsernameAndSucceededFalseAndAttemptedAtAfter(String username, Instant since);

    long countByIpAddressAndSucceededFalseAndAttemptedAtAfter(String ipAddress, Instant since);

    @Modifying
    @Query("delete from LoginAttempt a where a.attemptedAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
