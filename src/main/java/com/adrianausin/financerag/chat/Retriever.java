package com.adrianausin.financerag.chat;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

import com.adrianausin.financerag.ingestion.ChunkMetadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

/** Finds the chunks most similar to a question, optionally within some documents only. */
@Component
class Retriever {

    private final VectorStore vectorStore;
    private final RetrievalProperties properties;

    Retriever(VectorStore vectorStore, RetrievalProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /** @param documentIds empty to search every document */
    List<Source> retrieve(String query, Collection<UUID> documentIds) {
        SearchRequest.Builder request = SearchRequest.builder()
                .query(query)
                .topK(properties.topK())
                .similarityThreshold(properties.similarityThreshold());
        if (!documentIds.isEmpty()) {
            List<Object> ids = documentIds.stream().map(UUID::toString).map(Object.class::cast).toList();
            request.filterExpression(new FilterExpressionBuilder().in(ChunkMetadata.DOCUMENT_ID, ids).build());
        }
        List<Document> chunks = vectorStore.similaritySearch(request.build());

        return IntStream.range(0, chunks.size())
                .mapToObj(i -> toSource(i + 1, chunks.get(i)))
                .toList();
    }

    private static Source toSource(int number, Document chunk) {
        var metadata = chunk.getMetadata();
        Object page = metadata.get(ChunkMetadata.PAGE);
        return new Source(
                number,
                UUID.fromString((String) metadata.get(ChunkMetadata.DOCUMENT_ID)),
                (String) metadata.get(ChunkMetadata.FILENAME),
                page instanceof Number n ? n.intValue() : null,
                Objects.requireNonNullElse(chunk.getText(), ""),
                Objects.requireNonNullElse(chunk.getScore(), 0.0));
    }
}
