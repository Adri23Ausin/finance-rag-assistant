package com.adrianausin.financerag.api;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.adrianausin.financerag.document.DocumentService;
import com.adrianausin.financerag.document.StoredDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Upload and manage the documents the assistant answers from")
class DocumentController {

    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document",
            description = "202 while it is indexed in the background; 200 with the existing document if the same file was uploaded before")
    ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        DocumentService.Upload upload = documents.upload(file.getOriginalFilename(), file.getBytes());
        DocumentResponse body = DocumentResponse.from(upload.document());
        if (!upload.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.accepted().location(URI.create("/api/v1/documents/" + body.id())).body(body);
    }

    @GetMapping
    @Operation(summary = "List documents, newest first")
    List<DocumentResponse> list() {
        return documents.findAll().stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a document and its indexing status")
    DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(documents.get(id));
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "Download the original file", description = "PDFs open at a page with #page=N")
    ResponseEntity<byte[]> file(@PathVariable UUID id) {
        StoredDocument document = documents.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(document.filename()).build().toString())
                .body(documents.content(id));
    }

    @PostMapping("/{id}/reindex")
    @Operation(summary = "Index a document again", description = "After a failure or a change in the chunking or embedding configuration")
    ResponseEntity<DocumentResponse> reindex(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(DocumentResponse.from(documents.reindex(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document and its chunks")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        documents.delete(id);
        return ResponseEntity.noContent().build();
    }
}
