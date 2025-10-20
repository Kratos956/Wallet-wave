package com.example.majorproject.Models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true, nullable = false)
    private String externalTransactionId;

    @NotNull(message = "Sender is required")
    private Integer sender;

    @NotNull(message = "Receiver is required")
    private Integer receiver;

    @Positive(message = "Amount must be positive")
    private Long amount;

    private String comment;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @CreationTimestamp
    private Date createdAt;

    @UpdateTimestamp
    private Date updatedAt;

    @PrePersist
    private void prePersist() {
        if (externalTransactionId == null || externalTransactionId.isBlank()) {
            externalTransactionId = UUID.randomUUID().toString();
        }
    }
}
