CREATE TABLE documents (
    id           UUID PRIMARY KEY,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(127) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    -- SHA-256 of the content: the same file is only stored and indexed once
    checksum     CHAR(64)     NOT NULL UNIQUE,
    -- The original is kept so the index can be rebuilt (new chunking or embedding model)
    content      BYTEA        NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    pages        INTEGER,
    chunks       INTEGER,
    error        TEXT,
    uploaded_at  TIMESTAMPTZ  NOT NULL,
    indexed_at   TIMESTAMPTZ
);

CREATE INDEX documents_status_idx ON documents (status);
