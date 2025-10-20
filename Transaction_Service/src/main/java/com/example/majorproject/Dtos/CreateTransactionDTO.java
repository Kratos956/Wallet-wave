package com.example.majorproject.Dtos;

import com.example.majorproject.Models.Transaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
public class CreateTransactionDTO {

    private Integer sender;

    @NotNull(message = "Receiver userId is required")
    private Integer receiver;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Long amount;

    @Size(max = 255, message = "Comment must not exceed 255 characters")
    private String comment;

    public Transaction convertToTransaction() {
        return Transaction.builder()
                .sender(sender)
                .receiver(receiver)
                .amount(amount)
                .comment(comment)
                .build();
    }

}
