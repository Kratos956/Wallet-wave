package com.example.majorproject.Controllers;


import com.example.majorproject.Dtos.CreateTransactionDTO;
import com.example.majorproject.Models.Transaction;
import com.example.majorproject.Security.JwtService;
import com.example.majorproject.Services.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/transaction")
public class TransactionController {

    @Autowired
    TransactionService transactionService;

    @Autowired
    private JwtService jwtService;


    @Operation(
            summary = "Send a new transaction",
            description = "Creates a new transaction between a sender and a receiver in WalletWave"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data (e.g., negative amount or missing fields)"),
            @ApiResponse(responseCode = "404", description = "Sender or receiver not found")
    })
    @PostMapping
    public Transaction send(Authentication authentication,
                            @Valid @RequestBody CreateTransactionDTO createTransactionDTO) {
        @SuppressWarnings("unchecked")
        Map<String, Object> principal = (Map<String, Object>) authentication.getPrincipal();

        String email = (String) principal.get("email");
        Integer senderId = (Integer) principal.get("userId");

        createTransactionDTO.setSender(senderId);

        return transactionService.send(createTransactionDTO);
    }
}
