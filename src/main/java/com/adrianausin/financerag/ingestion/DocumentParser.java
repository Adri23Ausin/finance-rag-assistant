package com.adrianausin.financerag.ingestion;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

/**
 * Turns an uploaded file into text sections. Formats that have pages (PDF) return one section
 * per page with {@link ChunkMetadata#PAGE} set, so answers can cite the page they come from.
 */
public interface DocumentParser {

    boolean supports(String contentType);

    List<Document> parse(Resource resource);
}
