package com.adrianausin.financerag.ingestion;

/** Metadata stored with every chunk in the vector store. */
public final class ChunkMetadata {

    public static final String DOCUMENT_ID = "document_id";
    public static final String FILENAME = "filename";
    /** 1-based page number, absent for formats without pages. */
    public static final String PAGE = "page";
    /** Position of the chunk in the whole document, starting at 0. */
    public static final String CHUNK = "chunk";

    private ChunkMetadata() {
    }
}
