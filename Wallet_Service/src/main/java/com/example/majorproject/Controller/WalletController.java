package com.example.majorproject.Controller;

import com.example.majorproject.Services.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    @Autowired
    WalletService walletService;


    @Operation(
            summary = "Get wallet balance",
            description = "Fetches the current balance of a user's wallet by userId"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved wallet balance"),
            @ApiResponse(responseCode = "400", description = "Invalid userId supplied"),
            @ApiResponse(responseCode = "404", description = "Wallet not found for the given userId")
    })
    @GetMapping("/me")
    public Long getWallet(Authentication authentication) {
        @SuppressWarnings("unchecked")
        Map<String, Object> principal = (Map<String, Object>) authentication.getPrincipal();
        Integer userId = (Integer) principal.get("userId");
        return walletService.getBalance(userId);
    }
}
