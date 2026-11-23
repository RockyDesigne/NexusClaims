package com.titu.nexus_claims.dtos;

import java.util.List;

public record ClaimDocumentsDTO(long claimId, String claimNumber, boolean canUpload,
                                List<ClaimDocumentDTO> documents, List<String> tags,
                                List<String> allowedExtensions, long maxFileSize) { }
