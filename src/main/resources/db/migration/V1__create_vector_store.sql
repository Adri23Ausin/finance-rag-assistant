-- Chunks and their embeddings, in the layout Spring AI's PgVectorStore expects.
-- The dimension must match the embedding model (nomic-embed-text: 768); changing
-- the model means a new migration and re-indexing every document.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id        UUID PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(768)
);

-- Approximate nearest neighbour search on cosine distance
CREATE INDEX vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);

-- Metadata filters (document_id == ..., document_id IN [...]) are translated to jsonpath
CREATE INDEX vector_store_metadata_idx ON vector_store USING gin (metadata jsonb_path_ops);
