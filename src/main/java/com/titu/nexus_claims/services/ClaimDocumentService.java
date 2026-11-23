package com.titu.nexus_claims.services;

import com.titu.nexus_claims.dtos.*;
import com.titu.nexus_claims.models.*;
import com.titu.nexus_claims.repositories.*;
import com.titu.nexus_claims.validation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClaimDocumentService {
    private final ClaimFileRepository claims;
    private final ClaimDocumentRepository documents;
    private final ClaimAccessPolicy access;
    private final ClaimDocumentRules rules;

    @Transactional(readOnly = true)
    public ClaimDocumentsDTO list(long claimId) {
        var claim = findClaim(claimId);
        return new ClaimDocumentsDTO(claimId, claim.getClaimNumber(), access.canCurrentActorEdit(),
                documents.listMetadata(claimId), ClaimDocumentRules.TAGS, rules.extensions(), rules.maxFileSize());
    }

    @Transactional
    public ClaimDocumentDTO upload(long claimId, MultipartFile file, String tag, String key) {
        if (!access.canCurrentActorEdit()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to attach documents.");
        var claim = findClaim(claimId);
        String safeTag = tag == null ? "" : tag.trim();
        List<ClaimValidationError> errors = new ArrayList<>();
        if (!safeTag.isEmpty() && !ClaimDocumentRules.TAGS.contains(safeTag)) errors.add(new ClaimValidationError("tag", "Choose one of the available document tags."));
        if (key == null || key.isBlank() || key.length() > 100) errors.add(new ClaimValidationError("idempotencyKey", "A valid upload request key is required."));
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty() || name.length() > 180 || name.chars().anyMatch(Character::isISOControl)) errors.add(new ClaimValidationError("file", "Use a filename of 1–180 characters without control characters."));
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = ClaimDocumentRules.CONTENT_TYPES.get(extension);
        if (type == null) errors.add(new ClaimValidationError("file", "This file format is not supported. Choose PDF, an image, a text file, or a Word/Excel document."));
        if (file == null || file.isEmpty()) errors.add(new ClaimValidationError("file", "Empty files cannot be attached."));
        else if (file.getSize() > rules.maxFileSize()) errors.add(new ClaimValidationError("file", "File exceeds the attachment size limit."));
        if (!errors.isEmpty()) throw new ClaimValidationException(errors);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read the attachment. Please retry.", exception);
        }
        if (content.length > rules.maxFileSize()) throw new ClaimValidationException(List.of(new ClaimValidationError("file", "File exceeds the attachment size limit.")));
        if (!ClaimDocumentRules.matchesFormat(extension, content)) throw new ClaimValidationException(List.of(new ClaimValidationError("file", "The file content does not match its extension.")));
        String hash = hash(content);
        var existing = documents.findByClaimIdAndUploadKey(claimId, key.trim());
        if (existing.isPresent()) {
            var saved = existing.get();
            if (!saved.getSha256().equals(hash) || !saved.getName().equals(name) || !saved.getTag().equals(safeTag))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This upload request key was already used for a different attachment.");
            return toDto(saved);
        }
        return toDto(documents.saveAndFlush(ClaimDocument.builder().claim(claim).uploadKey(key.trim()).name(name)
                .contentType(type).category(type.startsWith("image/") ? "IMAGE" : "OTHER").size(content.length).tag(safeTag)
                .uploadedBy("Fictional Insurance Director").uploadedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)).sha256(hash).content(content).build()));
    }

    @Transactional(readOnly = true)
    public Content content(long claimId, long documentId) {
        findClaim(claimId);
        var file = documents.findByIdAndClaimId(documentId, claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found for this claim."));
        return new Content(file.getName(), file.getContentType(), file.getContent());
    }
    public record Content(String name, String contentType, byte[] bytes) { }

    private ClaimFile findClaim(long id) {
        return claims.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim file was not found."));
    }
    private ClaimDocumentDTO toDto(ClaimDocument file) {
        return new ClaimDocumentDTO(file.getId(), file.getName(), file.getContentType(), file.getCategory(), file.getSize(), file.getTag(), file.getUploadedBy(), file.getUploadedAt());
    }
    private String hash(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
