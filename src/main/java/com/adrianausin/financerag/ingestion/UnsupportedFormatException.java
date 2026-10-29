package com.adrianausin.financerag.ingestion;

public class UnsupportedFormatException extends RuntimeException {

    public UnsupportedFormatException(String contentType) {
        super("Unsupported document format: " + contentType
                + ". Upload a PDF, Word (.docx), HTML, Markdown or plain text file.");
    }
}
