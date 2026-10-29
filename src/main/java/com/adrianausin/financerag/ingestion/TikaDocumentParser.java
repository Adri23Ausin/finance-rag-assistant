package com.adrianausin.financerag.ingestion;

import java.util.List;
import java.util.Set;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Word, HTML, Markdown and plain text through Apache Tika. These formats have no pages. */
@Component
@Order(2)
class TikaDocumentParser implements DocumentParser {

    private static final Set<String> CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.oasis.opendocument.text",
            "application/rtf",
            "text/html",
            "text/markdown",
            "text/plain");

    @Override
    public boolean supports(String contentType) {
        return CONTENT_TYPES.contains(contentType);
    }

    @Override
    public List<Document> parse(Resource resource) {
        return new TikaDocumentReader(resource).get().stream()
                .map(section -> new Document(section.getText()))
                .toList();
    }
}
