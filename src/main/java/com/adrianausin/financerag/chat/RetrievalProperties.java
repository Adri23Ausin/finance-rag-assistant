package com.adrianausin.financerag.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param topK                chunks given to the model as sources
 * @param similarityThreshold minimum cosine similarity (0..1) for a chunk to count as relevant;
 *                            below it the question is answered as "not found" without calling the model
 * @param historySize         messages of a conversation kept and sent with each question
 * @param rewriteFollowUps    rewrite follow-up questions into standalone ones before searching
 *                            (one extra model call per follow-up)
 */
@ConfigurationProperties("rag.retrieval")
public record RetrievalProperties(
        @DefaultValue("5") int topK,
        @DefaultValue("0.5") double similarityThreshold,
        @DefaultValue("10") int historySize,
        @DefaultValue("true") boolean rewriteFollowUps) {
}
