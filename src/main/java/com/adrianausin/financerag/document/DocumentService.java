package com.adrianausin.financerag.document;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.adrianausin.financerag.ingestion.ContentTypeDetector;
import com.adrianausin.financerag.ingestion.DocumentParsers;
import com.adrianausin.financerag.ingestion.UnsupportedFormatException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DocumentService {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final DocumentRepository documents;
    private final DocumentIndexer indexer;
    private final ContentTypeDetector detector;
    private final DocumentParsers parsers;
    private final Clock clock;

    DocumentService(DocumentRepository documents, DocumentIndexer indexer, ContentTypeDetector detector,
                    DocumentParsers parsers, Clock clock) {
        this.documents = documents;
        this.indexer = indexer;
        this.detector = detector;
        this.parsers = parsers;
        this.clock = clock;
    }

    /** Stores the document and indexes it in the background. A file uploaded before is not stored twice. */
    public Upload upload(@Nullable String originalFilename, byte[] content) {
        if (content.length == 0) {
            throw new InvalidDocumentException("The file is empty");
        }
        String filename = sanitize(originalFilename);
        String contentType = detector.detect(content, filename);
        if (!parsers.supports(contentType)) {
            throw new UnsupportedFormatException(contentType);
        }
        String checksum = sha256(content);
        var existing = documents.findByChecksum(checksum);
        if (existing.isPresent()) {
            return new Upload(existing.get(), false);
        }

        StoredDocument document = new StoredDocument(UUID.randomUUID(), filename, contentType, content.length, checksum,
                DocumentStatus.INDEXING, null, null, null, clock.instant(), null);
        try {
            documents.insert(document, content);
        }
        catch (DuplicateKeyException e) {
            // The same file uploaded twice at the same time: the other request stored it
            return new Upload(documents.findByChecksum(checksum).orElseThrow(), false);
        }
        indexer.indexAsync(document.id());
        return new Upload(document, true);
    }

    public List<StoredDocument> findAll() {
        return documents.findAll();
    }

    public StoredDocument get(UUID id) {
        return documents.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    public byte[] content(UUID id) {
        return documents.findContent(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    public StoredDocument reindex(UUID id) {
        get(id);
        documents.markIndexing(id);
        indexer.indexAsync(id);
        return get(id);
    }

    public void delete(UUID id) {
        if (!documents.delete(id)) {
            throw new DocumentNotFoundException(id);
        }
        indexer.removeChunks(id);
    }

    /** Documents left half-indexed by a restart are indexed again. */
    @EventListener(ApplicationReadyEvent.class)
    void resumeInterruptedIndexing() {
        documents.findByStatus(DocumentStatus.INDEXING).forEach(document -> indexer.indexAsync(document.id()));
    }

    private static String sanitize(@Nullable String filename) {
        String name = filename == null ? null : StringUtils.getFilename(StringUtils.cleanPath(filename));
        if (!StringUtils.hasText(name)) {
            return "document";
        }
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(name.length() - MAX_FILENAME_LENGTH) : name;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @param created false when the same file had already been uploaded */
    public record Upload(StoredDocument document, boolean created) {
    }
}
