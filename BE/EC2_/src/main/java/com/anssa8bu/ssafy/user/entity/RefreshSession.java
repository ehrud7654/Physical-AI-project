package com.anssa8bu.ssafy.user.entity;

import com.anssa8bu.ssafy.common.entity.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession extends CreatedEntity {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_session_id")
    private UUID replacedBySessionId;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected RefreshSession() {
    }

    public RefreshSession(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.tokenHash = requireSha256(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    private static String requireSha256(String tokenHash) {
        if (tokenHash == null || !SHA256_PATTERN.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("tokenHash must be a lowercase SHA-256 value");
        }
        return tokenHash;
    }

    public void markUsed(Instant usedAt) {
        this.lastUsedAt = Objects.requireNonNull(usedAt, "usedAt must not be null");
    }

    public void revoke(Instant revokedAt) {
        this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
    }

    public void replaceWith(UUID replacementSessionId, Instant revokedAt) {
        this.replacedBySessionId = Objects.requireNonNull(replacementSessionId, "replacementSessionId must not be null");
        revoke(revokedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedBySessionId() {
        return replacedBySessionId;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
