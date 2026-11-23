package com.titu.nexus_claims.controllers;
import com.titu.nexus_claims.dtos.*;
import com.titu.nexus_claims.services.ClaimTaskService;
import com.titu.nexus_claims.validation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/claims/{claimId}/tasks") @RequiredArgsConstructor
public class ClaimTaskController {
    private final ClaimTaskService tasks;
    @GetMapping public ClaimTasksDTO get(@PathVariable long claimId) { return tasks.get(claimId); }
    @PostMapping public ClaimTasksDTO create(@PathVariable long claimId, @RequestBody CreateClaimTaskRequest request) { return tasks.create(claimId, request); }
    @PutMapping("/{taskId}") public ClaimTasksDTO update(@PathVariable long claimId, @PathVariable long taskId, @RequestBody UpdateClaimTaskRequest request) { return tasks.update(claimId, taskId, request); }
    @PostMapping("/{taskId}/handoff") public ClaimTasksDTO handoff(@PathVariable long claimId, @PathVariable long taskId, @RequestBody HandoffClaimTaskRequest request) { return tasks.handoff(claimId, taskId, request); }
    @PostMapping("/{taskId}/settle") public ClaimTasksDTO settle(@PathVariable long claimId, @PathVariable long taskId, @RequestBody SettleClaimTaskRequest request) { return tasks.settle(claimId, taskId, request); }
    @ExceptionHandler(ClaimValidationException.class)
    public ResponseEntity<List<ClaimValidationError>> invalid(ClaimValidationException exception) { return ResponseEntity.badRequest().body(exception.getErrors()); }
    @ExceptionHandler({CannotAcquireLockException.class, DataIntegrityViolationException.class})
    public ResponseEntity<String> busy(RuntimeException exception) { return ResponseEntity.status(HttpStatus.CONFLICT).body("Tasks changed or another save is in progress. Reload tasks and retry."); }
}
