package com.dolbaia.ledger.account;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.dolbaia.ledger.common.BaseEntity;

/** GEL per unit of a currency on a date; the latest one values accounts in GEL. */
@Entity
@Table(name = "exchange_rate")
public class ExchangeRate extends BaseEntity {

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "rate_date", nullable = false)
    private LocalDate date;

    @Column(name = "gel_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal gelRate;

    protected ExchangeRate() {}

    public ExchangeRate(String currency, LocalDate date, BigDecimal gelRate) {
        this.currency = currency;
        this.date = date;
        this.gelRate = gelRate;
    }

    public String getCurrency() { return currency; }
    public LocalDate getDate() { return date; }
    public BigDecimal getGelRate() { return gelRate; }
    public void setGelRate(BigDecimal gelRate) { this.gelRate = gelRate; }
}
