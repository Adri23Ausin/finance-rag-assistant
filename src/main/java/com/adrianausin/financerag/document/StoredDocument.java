package com.adrianausin.financerag.document;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** An uploaded document, without its content. */
public record StoredDocument(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        String checksum,
        DocumentStatus status,
        @Nullable Integer pages,
        @Nullable Integer chunks,
        @Nullable String error,
        Instant uploadedAt,
        @Nullable Instant indexedAt) {
}
