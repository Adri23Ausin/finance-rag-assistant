package com.adrianausin.financerag.ingestion;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Detects the real format from the file's bytes (magic numbers) and name. The Content-Type sent
 * by the client is not trusted: browsers often send application/octet-stream, or the wrong type.
 */
@Component
public class ContentTypeDetector {

    private final Tika tika = new Tika();

    public String detect(byte[] content, String filename) {
        return tika.detect(content, filename);
    }
}
