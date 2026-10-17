package com.dolbaia.ledger.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "login_attempt")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected LoginAttempt() {}

    public LoginAttempt(String username, String ipAddress, boolean succeeded, Instant attemptedAt) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.succeeded = succeeded;
        this.attemptedAt = attemptedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
