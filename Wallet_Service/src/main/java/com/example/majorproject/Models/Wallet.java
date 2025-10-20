package com.example.majorproject.Models;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;


    @Column(unique = true, nullable = false)
    private Integer userId;

    @PositiveOrZero(message = "Balance cannot be negative")
    private Long balance;

    @Enumerated(EnumType.STRING)
    private CurrencyType currency;

    @CreationTimestamp
    private Date createdAt;

    @UpdateTimestamp
    private Date updatedAt;

    @PrePersist
    private void setDefaults() {
        if (balance == null) balance = 0L;
        if (currency == null) currency = CurrencyType.USD; // default
    }

}
