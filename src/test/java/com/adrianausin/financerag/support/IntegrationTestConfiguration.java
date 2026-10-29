package com.adrianausin.financerag.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL with pgvector, plus fake models so the tests need neither Ollama nor network access.
 * The container is static so every Spring test context shares it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfiguration {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }

    @Bean
    HashingEmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel();
    }

    @Bean
    ScriptedChatModel chatModel() {
        return new ScriptedChatModel();
    }
}
