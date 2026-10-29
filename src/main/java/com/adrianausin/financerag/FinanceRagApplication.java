package com.adrianausin.financerag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinanceRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceRagApplication.class, args);
    }
}
