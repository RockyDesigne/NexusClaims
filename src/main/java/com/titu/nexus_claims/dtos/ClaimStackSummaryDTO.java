package com.titu.nexus_claims.dtos;

import java.util.List;
import java.util.Map;

public record ClaimStackSummaryDTO(
        List<ClaimFileDTO> claims,
        Map<String, Long> statusCounts
) {
}
