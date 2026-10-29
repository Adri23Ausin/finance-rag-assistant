package com.adrianausin.financerag.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param chunkSize         target chunk size in tokens: small enough for precise retrieval, big
 *                          enough to keep a table or a paragraph together
 * @param minChunkSizeChars a chunk is cut at the last sentence end after this many characters
 * @param maxConcurrentIndexing documents indexed at the same time; embedding is bound by the
 *                          model server, so more parallelism only queues up requests there
 */
@ConfigurationProperties("rag.ingestion")
public record IngestionProperties(
        @DefaultValue("350") int chunkSize,
        @DefaultValue("300") int minChunkSizeChars,
        @DefaultValue("2") int maxConcurrentIndexing) {
}
