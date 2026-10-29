package com.adrianausin.financerag.ingestion;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class DocumentParsers {

    private final List<DocumentParser> parsers;

    DocumentParsers(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public boolean supports(String contentType) {
        return parsers.stream().anyMatch(parser -> parser.supports(contentType));
    }

    public List<Document> parse(Resource resource, String contentType) {
        return parsers.stream()
                .filter(parser -> parser.supports(contentType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedFormatException(contentType))
                .parse(resource);
    }
}
