package com.titu.nexus_claims.dtos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ClaimReservesDTO(long claimId, String claimNumber, boolean canEdit, long revision,
                               List<Entry> entries, List<Balance> balances, List<Total> totals) {
    public record Entry(long id, String category, String type, String operation, LocalDate date, String amount,
                        String currency, String details, String user, Instant createdAt, String reserveValue, String totalValue) { }
    public record Balance(String category, String type, String amount, String currency) { }
    public record Total(String category, String amount, String currency) { }
}
