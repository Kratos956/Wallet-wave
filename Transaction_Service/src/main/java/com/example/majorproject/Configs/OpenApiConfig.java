package com.example.majorproject.Configs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI walletWaveOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WalletWave - Transaction Service API") // unique per service
                        .description("Handles transactions for the WalletWave E-Wallet system")
                        .version("v1")
                        .contact(new Contact()
                                .name("Sanit Chhonker")
                                .email("sanitsingh111@gmail.com")
                                .url("https://www.linkedin.com/in/sanit-chhonker-07175135a/")
                        )
                );
    }
}

