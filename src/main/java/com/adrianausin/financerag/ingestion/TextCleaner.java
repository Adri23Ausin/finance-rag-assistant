package com.adrianausin.financerag.ingestion;

import java.util.regex.Pattern;

/** Normalises extracted text before it is chunked and embedded. */
final class TextCleaner {

    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[\\t\\x0B\\f\\u00A0 ]+");
    private static final Pattern SPACE_AROUND_NEWLINE = Pattern.compile(" ?\\n ?");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");
    // PostgreSQL text columns reject NUL, and PDFs do contain it now and then
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0E-\\x1F]");

    private TextCleaner() {
    }

    static String clean(String text) {
        String cleaned = text.replace("\r\n", "\n").replace('\r', '\n');
        cleaned = CONTROL.matcher(cleaned).replaceAll("");
        // Layout-preserving PDF extraction pads table columns with long runs of spaces
        cleaned = HORIZONTAL_SPACE.matcher(cleaned).replaceAll(" ");
        cleaned = SPACE_AROUND_NEWLINE.matcher(cleaned).replaceAll("\n");
        cleaned = BLANK_LINES.matcher(cleaned).replaceAll("\n\n");
        return cleaned.strip();
    }
}
