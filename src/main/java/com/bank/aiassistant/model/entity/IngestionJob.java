package com.bank.aiassistant.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "ingestion_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 50)
    private String connectorType;

    @Column(length = 100)
    private String sourceRef;   // filename, URL, ticket ID, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    private Integer chunksProcessed;
    private Integer chunksTotal;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Instant startedAt;
    private Instant completedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public enum JobStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }
}
