package com.titu.nexus_claims.controllers;

import com.titu.nexus_claims.dtos.*;
import com.titu.nexus_claims.services.ClaimReserveService;
import com.titu.nexus_claims.validation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/claims/{claimId}/reserves")
@RequiredArgsConstructor
public class ClaimReserveController {
    private final ClaimReserveService reserves;
    @GetMapping public ClaimReservesDTO get(@PathVariable long claimId) { return reserves.getReserves(claimId); }
    @PostMapping public ClaimReservesDTO add(@PathVariable long claimId, @RequestBody CreateReserveEntryRequest request) { return reserves.addEntry(claimId, request); }
    @ExceptionHandler(ClaimValidationException.class)
    public ResponseEntity<List<ClaimValidationError>> invalid(ClaimValidationException exception) {
        return ResponseEntity.badRequest().body(exception.getErrors());
    }
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class})
    public ResponseEntity<String> stale(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Reserve history changed. Reload the latest balances before saving again.");
    }
}
