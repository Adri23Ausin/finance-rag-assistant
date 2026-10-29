package com.adrianausin.financerag.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ChatConfig {

    /** The last messages of each conversation, stored in PostgreSQL by the JDBC repository. */
    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository, RetrievalProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.historySize())
                .build();
    }

    /** Turns a follow-up ("and in 2024?") into a question that can be searched on its own. */
    @Bean
    QueryTransformer followUpRewriter(ChatClient.Builder chatClientBuilder) {
        return CompressionQueryTransformer.builder().chatClientBuilder(chatClientBuilder).build();
    }
}
