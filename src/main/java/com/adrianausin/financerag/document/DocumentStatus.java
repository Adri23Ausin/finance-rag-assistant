package com.adrianausin.financerag.document;

public enum DocumentStatus {
    /** Stored, being parsed, chunked and embedded. */
    INDEXING,
    /** Searchable. */
    INDEXED,
    /** Indexing failed; the error is kept and the document can be re-indexed. */
    FAILED
}
