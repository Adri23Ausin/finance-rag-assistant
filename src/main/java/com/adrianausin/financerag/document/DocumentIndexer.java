package com.adrianausin.financerag.document;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.adrianausin.financerag.ingestion.ChunkMetadata;
import com.adrianausin.financerag.ingestion.DocumentChunker;
import com.adrianausin.financerag.ingestion.DocumentParsers;
import com.adrianausin.financerag.ingestion.IngestionProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Parses, chunks and embeds stored documents into the vector store, in the background.
 * Indexing is idempotent: the document's previous chunks are replaced, so a document can be
 * re-indexed at any time (after a failure, or with a new chunking configuration).
 */
@Component
public class DocumentIndexer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    private final DocumentRepository documents;
    private final DocumentParsers parsers;
    private final DocumentChunker chunker;
    private final VectorStore vectorStore;
    private final Clock clock;
    // Virtual threads, but only a few documents at a time: embedding is bound by the model server
    private final SimpleAsyncTaskExecutor executor;

    DocumentIndexer(DocumentRepository documents, DocumentParsers parsers, DocumentChunker chunker,
                    VectorStore vectorStore, Clock clock, IngestionProperties properties) {
        this.documents = documents;
        this.parsers = parsers;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.clock = clock;
        this.executor = new SimpleAsyncTaskExecutor("indexing-");
        this.executor.setVirtualThreads(true);
        this.executor.setConcurrencyLimit(properties.maxConcurrentIndexing());
        this.executor.setTaskTerminationTimeout(30_000);
    }

    public void indexAsync(UUID id) {
        executor.execute(() -> index(id));
    }

    void index(UUID id) {
        StoredDocument document = documents.findById(id).orElse(null);
        byte[] content = documents.findContent(id).orElse(null);
        if (document == null || content == null) {
            return; // deleted in the meantime
        }
        try {
            List<Document> sections = parsers.parse(new ByteArrayResource(content), document.contentType());
            List<Document> chunks = chunker.chunk(sections, id, document.filename());
            if (chunks.isEmpty()) {
                throw new IllegalStateException("No text found in the document. Scanned documents need OCR, which is not supported.");
            }
            removeChunks(id);
            vectorStore.add(chunks);
            if (!documents.markIndexed(id, lastPage(sections), chunks.size(), clock.instant())) {
                removeChunks(id); // deleted while it was being indexed
                return;
            }
            log.info("Indexed {} ({} chunks)", document.filename(), chunks.size());
        }
        catch (RuntimeException e) {
            log.warn("Could not index {}", document.filename(), e);
            removeChunks(id);
            documents.markFailed(id, Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    public void removeChunks(UUID id) {
        vectorStore.delete(new FilterExpressionBuilder().eq(ChunkMetadata.DOCUMENT_ID, id.toString()).build());
    }

    private static @Nullable Integer lastPage(List<Document> sections) {
        return sections.stream()
                .map(section -> (Integer) section.getMetadata().get(ChunkMetadata.PAGE))
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(null);
    }

    @Override
    public void destroy() {
        executor.close();
    }
}
