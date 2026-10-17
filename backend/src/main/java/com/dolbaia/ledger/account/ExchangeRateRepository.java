package com.dolbaia.ledger.account;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    /** Newest first within each currency, so the first row per currency is its latest rate. */
    @Query("select r from ExchangeRate r order by r.currency, r.date desc")
    List<ExchangeRate> findAllLatestFirst();

    Optional<ExchangeRate> findByCurrencyAndDate(String currency, LocalDate date);
}
