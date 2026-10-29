package com.adrianausin.financerag.document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private static final String COLUMNS = """
            id, filename, content_type, size_bytes, checksum, status, pages, chunks, error, uploaded_at, indexed_at""";

    private final JdbcClient jdbc;

    DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(StoredDocument document, byte[] content) {
        jdbc.sql("""
                        INSERT INTO documents (id, filename, content_type, size_bytes, checksum, content, status, uploaded_at)
                        VALUES (:id, :filename, :contentType, :size, :checksum, :content, :status, :uploadedAt)
                        """)
                .param("id", document.id())
                .param("filename", document.filename())
                .param("contentType", document.contentType())
                .param("size", document.sizeBytes())
                .param("checksum", document.checksum())
                .param("content", content)
                .param("status", document.status().name())
                .param("uploadedAt", Timestamp.from(document.uploadedAt()))
                .update();
    }

    public Optional<StoredDocument> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM documents WHERE id = :id")
                .param("id", id)
                .query(DocumentRepository::map)
                .optional();
    }

    public Optional<StoredDocument> findByChecksum(String checksum) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM documents WHERE checksum = :checksum")
                .param("checksum", checksum)
                .query(DocumentRepository::map)
                .optional();
    }

    public List<StoredDocument> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM documents ORDER BY uploaded_at DESC")
                .query(DocumentRepository::map)
                .list();
    }

    public List<StoredDocument> findByStatus(DocumentStatus status) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM documents WHERE status = :status")
                .param("status", status.name())
                .query(DocumentRepository::map)
                .list();
    }

    /** Ids among the given ones whose documents are indexed. */
    public List<UUID> findIndexedIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id FROM documents WHERE status = 'INDEXED' AND id IN (:ids)")
                .param("ids", ids)
                .query(UUID.class)
                .list();
    }

    public Optional<byte[]> findContent(UUID id) {
        return jdbc.sql("SELECT content FROM documents WHERE id = :id")
                .param("id", id)
                .query(byte[].class)
                .optional();
    }

    public void markIndexing(UUID id) {
        jdbc.sql("UPDATE documents SET status = 'INDEXING', error = NULL WHERE id = :id")
                .param("id", id)
                .update();
    }

    /** Returns false when the document no longer exists. */
    public boolean markIndexed(UUID id, @Nullable Integer pages, int chunks, Instant indexedAt) {
        return jdbc.sql("""
                        UPDATE documents SET status = 'INDEXED', pages = :pages, chunks = :chunks, error = NULL,
                               indexed_at = :indexedAt
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("pages", pages)
                .param("chunks", chunks)
                .param("indexedAt", Timestamp.from(indexedAt))
                .update() > 0;
    }

    public void markFailed(UUID id, String error) {
        jdbc.sql("UPDATE documents SET status = 'FAILED', chunks = 0, error = :error WHERE id = :id")
                .param("id", id)
                .param("error", error)
                .update();
    }

    public boolean delete(UUID id) {
        return jdbc.sql("DELETE FROM documents WHERE id = :id").param("id", id).update() > 0;
    }

    private static StoredDocument map(ResultSet rs, int row) throws SQLException {
        return new StoredDocument(
                rs.getObject("id", UUID.class),
                rs.getString("filename"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("checksum"),
                DocumentStatus.valueOf(rs.getString("status")),
                rs.getObject("pages", Integer.class),
                rs.getObject("chunks", Integer.class),
                rs.getString("error"),
                rs.getTimestamp("uploaded_at").toInstant(),
                toInstant(rs.getTimestamp("indexed_at")));
    }

    private static @Nullable Instant toInstant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
