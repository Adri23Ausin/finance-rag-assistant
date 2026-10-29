package com.adrianausin.financerag.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL in a container and stand-in models, for {@code TestFinanceRagApplication}. */
@TestConfiguration(proxyBeanMethods = false)
public class OfflineConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    HashingEmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel();
    }

    @Bean
    ExtractiveChatModel chatModel() {
        return new ExtractiveChatModel();
    }
}
