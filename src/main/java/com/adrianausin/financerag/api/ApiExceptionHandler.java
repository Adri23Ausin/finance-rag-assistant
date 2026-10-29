package com.adrianausin.financerag.api;

import com.adrianausin.financerag.document.DocumentNotFoundException;
import com.adrianausin.financerag.document.InvalidDocumentException;
import com.adrianausin.financerag.ingestion.UnsupportedFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Errors as RFC 9457 problem details. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail notFound(DocumentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Document not found", e.getMessage());
    }

    @ExceptionHandler(UnsupportedFormatException.class)
    ProblemDetail unsupportedFormat(UnsupportedFormatException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported document format", e.getMessage());
    }

    @ExceptionHandler(InvalidDocumentException.class)
    ProblemDetail invalidDocument(InvalidDocumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid document", e.getMessage());
    }

    /** The model server is down, or does not have the model (e.g. it was never pulled). */
    @ExceptionHandler({TransientAiException.class, NonTransientAiException.class, ResourceAccessException.class})
    ProblemDetail modelUnavailable(RuntimeException e) {
        log.warn("Model server call failed", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Model unavailable",
                "The model server could not answer: " + e.getMessage());
    }

    static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
