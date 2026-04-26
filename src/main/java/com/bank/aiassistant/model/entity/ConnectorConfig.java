package com.bank.aiassistant.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Persisted configuration for a data source connector.
 * Credentials are AES-256 encrypted at rest (see ConnectorCredentialService).
 */
@Entity
@Table(name = "connector_configs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "connector_type", "name"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConnectorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 50)
    private String connectorType;   // JIRA, CONFLUENCE, GITHUB, SHAREPOINT, EMAIL, DOCUMENTS

    @Column(nullable = false, length = 100)
    private String name;            // human-readable label (e.g. "Acme JIRA")

    /** Encrypted JSON blob: { "baseUrl": "...", "token": "...", ... } */
    @Column(columnDefinition = "TEXT")
    private String encryptedCredentials;

    /** Non-sensitive config JSON: { "projectKeys": [...], "maxResults": 50 } */
    @Column(columnDefinition = "JSONB")
    private String config;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    private Instant lastSyncAt;

    @Column(length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
