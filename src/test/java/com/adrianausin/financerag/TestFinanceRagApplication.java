package com.adrianausin.financerag;

import com.adrianausin.financerag.support.OfflineConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Runs the application without Ollama, for UI and API work: {@code ./mvnw spring-boot:test-run}.
 * PostgreSQL starts in a container and the models are stand-ins (see {@link OfflineConfiguration}).
 */
public class TestFinanceRagApplication {

    public static void main(String[] args) {
        SpringApplication.from(FinanceRagApplication::main)
                .with(OfflineConfiguration.class)
                .withAdditionalProfiles("test")
                .run(args);
    }
}
