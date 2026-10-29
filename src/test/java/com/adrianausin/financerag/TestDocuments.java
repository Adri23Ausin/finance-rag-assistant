package com.adrianausin.financerag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Builds small documents for tests. */
public final class TestDocuments {

    private TestDocuments() {
    }

    /** A PDF with one page per argument; lines are separated by {@code \n}, an empty string is a blank page. */
    public static byte[] pdf(String... pages) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (text.isEmpty()) {
                    continue;
                }
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 11);
                    content.setLeading(14);
                    content.newLineAtOffset(50, 740);
                    for (String line : text.split("\n")) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
