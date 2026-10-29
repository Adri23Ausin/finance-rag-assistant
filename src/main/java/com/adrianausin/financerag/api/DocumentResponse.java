package com.adrianausin.financerag.api;

import java.time.Instant;
import java.util.UUID;

import com.adrianausin.financerag.document.DocumentStatus;
import com.adrianausin.financerag.document.StoredDocument;
import org.jspecify.annotations.Nullable;

record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        @Nullable Integer pages,
        @Nullable Integer chunks,
        @Nullable String error,
        Instant uploadedAt,
        @Nullable Instant indexedAt) {

    static DocumentResponse from(StoredDocument document) {
        return new DocumentResponse(document.id(), document.filename(), document.contentType(), document.sizeBytes(),
                document.status(), document.pages(), document.chunks(), document.error(), document.uploadedAt(),
                document.indexedAt());
    }
}
