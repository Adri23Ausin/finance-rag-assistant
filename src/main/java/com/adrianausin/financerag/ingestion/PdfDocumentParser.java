package com.adrianausin.financerag.ingestion;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** One section per page, keeping the page number for citations. */
@Component
@Order(1)
class PdfDocumentParser implements DocumentParser {

    private static final PdfDocumentReaderConfig CONFIG = PdfDocumentReaderConfig.builder()
            .withPagesPerDocument(1)
            .withPageExtractedTextFormatter(ExtractedTextFormatter.builder().withLeftAlignment(true).build())
            .build();

    @Override
    public boolean supports(String contentType) {
        return "application/pdf".equals(contentType);
    }

    @Override
    public List<Document> parse(Resource resource) {
        return new PagePdfDocumentReader(resource, CONFIG).get().stream()
                .map(page -> Document.builder()
                        .text(page.getText())
                        .metadata(ChunkMetadata.PAGE, page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER))
                        .build())
                .toList();
    }
}
