package com.adrianausin.financerag.ingestion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

/**
 * Cleans the parsed sections and splits them into token-sized chunks. Pages are split one by one,
 * so a chunk never spans two pages and its citation is always exact.
 */
@Component
public class DocumentChunker {

    private final TokenTextSplitter splitter;

    DocumentChunker(IngestionProperties properties) {
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSize())
                .withMinChunkSizeChars(properties.minChunkSizeChars())
                .build();
    }

    public List<Document> chunk(List<Document> sections, UUID documentId, String filename) {
        List<Document> cleaned = sections.stream()
                .map(section -> new Document(TextCleaner.clean(section.getText()), section.getMetadata()))
                .filter(section -> !section.getText().isBlank())
                .toList();
        List<Document> chunks = splitter.split(cleaned);

        return IntStream.range(0, chunks.size())
                .mapToObj(index -> {
                    Document chunk = chunks.get(index);
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put(ChunkMetadata.DOCUMENT_ID, documentId.toString());
                    metadata.put(ChunkMetadata.FILENAME, filename);
                    metadata.put(ChunkMetadata.CHUNK, index);
                    Object page = chunk.getMetadata().get(ChunkMetadata.PAGE);
                    if (page != null) {
                        metadata.put(ChunkMetadata.PAGE, page);
                    }
                    return new Document(chunk.getText(), metadata);
                })
                .toList();
    }
}
