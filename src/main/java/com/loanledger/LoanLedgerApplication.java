package com.loanledger;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(info = @Info(title = "LoanLedger API", version = "v1",
        description = "Mortgage servicing: loans, payments, escrow and loss mitigation. Synthetic data only."))
@SpringBootApplication
public class LoanLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanLedgerApplication.class, args);
    }
}
