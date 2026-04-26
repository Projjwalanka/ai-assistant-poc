package com.bank.aiassistant.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "feedback")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String messageId;

    @Column(nullable = false)
    private String conversationId;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeedbackType type;  // THUMBS_UP, THUMBS_DOWN, RATING

    private Integer rating;    // 1–5, used when type=RATING

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(length = 50)
    private String category;   // WRONG_ANSWER, HALLUCINATION, INAPPROPRIATE, HELPFUL, etc.

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public enum FeedbackType {
        THUMBS_UP, THUMBS_DOWN, RATING
    }
}
