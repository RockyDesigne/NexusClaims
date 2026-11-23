package com.titu.nexus_claims.controllers;

import com.titu.nexus_claims.dtos.*;
import com.titu.nexus_claims.services.ClaimDocumentService;
import com.titu.nexus_claims.services.ClaimDocumentPreviewService;
import com.titu.nexus_claims.validation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/claims/{claimId}/documents")
@RequiredArgsConstructor
public class ClaimDocumentController {
    private final ClaimDocumentService documents;
    private final ClaimDocumentPreviewService previews;
    @GetMapping public ClaimDocumentsDTO list(@PathVariable long claimId) { return documents.list(claimId); }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClaimDocumentDTO upload(@PathVariable long claimId, @RequestParam("file") MultipartFile file,
                                   @RequestParam(defaultValue = "") String tag, @RequestParam String idempotencyKey) {
        return documents.upload(claimId, file, tag, idempotencyKey);
    }
    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable long claimId, @PathVariable long documentId) {
        return content(claimId, documentId, false);
    }
    @GetMapping("/{documentId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable long claimId, @PathVariable long documentId) {
        return content(claimId, documentId, true);
    }
    private ResponseEntity<byte[]> content(long claimId, long id, boolean preview) {
        var file = preview ? previews.preview(claimId, id) : documents.content(claimId, id);
        var disposition = (preview ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(file.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.bytes().length).header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(file.bytes());
    }
    @ExceptionHandler(ClaimValidationException.class)
    public ResponseEntity<List<ClaimValidationError>> invalid(ClaimValidationException exception) { return ResponseEntity.badRequest().body(exception.getErrors()); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> tooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("File exceeds the attachment size limit.");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> duplicate(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("This upload may already be saved. Retry using the same request key.");
    }
}
