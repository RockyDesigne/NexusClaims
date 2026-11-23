package com.titu.nexus_claims.dtos;

import java.time.Instant;

public record ClaimDocumentDTO(long id, String name, String contentType, String category, long size,
                               String tag, String uploadedBy, Instant uploadedAt) { }
