package com.titu.nexus_claims.services;

import com.titu.nexus_claims.dtos.*;
import com.titu.nexus_claims.models.*;
import com.titu.nexus_claims.repositories.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.titu.nexus_claims.validation.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ClaimTaskService {
    public static final String FINAL_TASK_TYPE = "CLAIM_SETTLEMENT";
    public static final List<String> TYPES = List.copyOf(ClaimTaskCatalog.NAMES.keySet());
    private final ClaimFileRepository claims;
    private final ClaimStaffAccountRepository accounts;
    private final ClaimTaskRepository tasks;
    private final ClaimTaskActivityRepository activities;
    private final ClaimTaskRotationRepository rotations;
    private final ClaimAccessPolicy policy;

    @PostConstruct
    public void seedStaff() {
        for (var account : List.of(
                new ClaimStaffAccount("demo-adjuster-alex", "Alex Marin (demo)", "LOSS_ADJUSTER", true),
                new ClaimStaffAccount("demo-adjuster-dana", "Dana Pavel (demo)", "LOSS_ADJUSTER", true))) {
            if (!accounts.existsById(account.getUsername())) accounts.save(account);
        }
        if (!rotations.existsById(1L)) rotations.save(new ClaimTaskRotation(1L));
    }

    @Transactional
    public void ensureOpenTask(long claimId) {
        lock();
        ClaimFile claim = claim(claimId);
        var all = tasks.findByClaimIdOrderByCreatedAtDescIdDesc(claimId);
        var activeTasks = all.stream().filter(this::active).toList();
        boolean open = "Open".equalsIgnoreCase(claim.getStatus());
        var keep = open && !activeTasks.isEmpty() ? activeTasks.getFirst() : null;
        for (var task : activeTasks) {
            if (task != keep) closeTask(task, "CANCELLED", keep == null ? "Claim is closed; active task cancelled"
                    : "Superseded by task #" + keep.getId() + " during single-active-task update", "System", now());
        }
        tasks.flush();
        if (open && activeTasks.isEmpty()) {
            String key = all.isEmpty() ? "automatic-opening-task" : "automatic-recovery-" + UUID.randomUUID();
            create(claim, key, ClaimTaskCatalog.NAMES.get("DAMAGE_ASSESSMENT"), "DAMAGE_ASSESSMENT",
                    "Inspect and document the damage to the insured loss.", null, null, "Automatic assignment");
        }
    }

    /** Upgrade the prototype catalog and recover assignment/closure metadata from existing audit events. */
    @Transactional
    public void upgradeTasks(long claimId) {
        lock();
        var existing = tasks.findByClaimIdOrderByCreatedAtDescIdDesc(claimId);
        long firstId = existing.stream().mapToLong(ClaimTask::getId).min().orElse(-1);
        var history = activities.findByTaskClaimIdOrderByIdDesc(claimId);
        for (var task : existing) {
            boolean changed = false;
            String type = task.getId() == firstId ? "DAMAGE_ASSESSMENT" : switch (task.getType()) {
                case "CLAIM_REVIEW", "DOCUMENT_REVIEW" -> "CLAIM_SETTLEMENT";
                case "FOLLOW_UP" -> "DAMAGE_REASSESSMENT";
                default -> task.getType();
            };
            String name = ClaimTaskCatalog.NAMES.get(type);
            if (name == null) throw new IllegalStateException("Unrecognized stored task type: " + type);
            if (!type.equals(task.getType()) || !name.equals(task.getTitle())) {
                record(task, "Task name changed from " + task.getTitle() + " to " + name + " (predefined task catalog)", "System");
                task.setType(type); task.setTitle(name); changed = true;
            }
            if (task.getInitialDescription() == null) { task.setInitialDescription(task.getDescription()); changed = true; }
            if (task.getAssignedAt() == null || task.getAssignedBy() == null) {
                var assignment = history.stream().filter(event -> event.getTask().getId().equals(task.getId())
                        && (event.getDetails().startsWith("Created and assigned") || event.getDetails().startsWith("Reassigned from"))).findFirst();
                task.setAssignedAt(assignment.map(ClaimTaskActivity::getCreatedAt).orElse(task.getCreatedAt()));
                task.setAssignedBy(assignment.map(ClaimTaskActivity::getActor).orElse(task.getCreatedBy()));
                changed = true;
            }
            if ("COMPLETED".equals(task.getStatus()) && task.getClosedAt() == null) {
                var closure = history.stream().filter(event -> event.getTask().getId().equals(task.getId())
                        && event.getDetails().startsWith("Status changed from ") && event.getDetails().endsWith(" to COMPLETED")).findFirst();
                if (closure.isPresent()) { task.setClosedAt(closure.get().getCreatedAt()); changed = true; }
            }
            if (changed) { task.setRevision(task.getRevision() + 1); tasks.save(task); }
        }
    }

    @Transactional(readOnly = true)
    public ClaimTasksDTO get(long claimId) { return snapshot(claim(claimId)); }

    @Transactional
    public ClaimTasksDTO create(long claimId, CreateClaimTaskRequest request) {
        requireManager(); lock();
        ClaimFile claim = claim(claimId);
        if (request == null) throw invalid("title", "Task details are required.");
        String key = clean(request.idempotencyKey());
        if (key.isEmpty() || key.length() > 100 || key.startsWith("automatic-")) throw invalid("idempotencyKey", "A valid task request key is required.");
        String title = ClaimTaskCatalog.NAMES.getOrDefault(request.type(), ""), description = clean(request.description()), assignee = clean(request.assignee());
        List<ClaimValidationError> errors = new ArrayList<>();
        if (request.type() == null || !TYPES.contains(request.type())) errors.add(new ClaimValidationError("type", "Select a supported task type."));
        if (description.length() > 4000) errors.add(new ClaimValidationError("description", "Description must be at most 4,000 characters."));
        LocalDate dueDate = null;
        try { if (!clean(request.dueDate()).isEmpty()) dueDate = LocalDate.parse(request.dueDate().trim()); }
        catch (DateTimeException exception) { errors.add(new ClaimValidationError("dueDate", "Enter a valid due date.")); }
        if (!errors.isEmpty()) throw new ClaimValidationException(errors);
        var existing = tasks.findByClaimIdAndRequestKey(claimId, key);
        if (existing.isPresent()) {
            var task = existing.get();
            // Immutable creation fields distinguish retries from a reused key. Assignment may since have changed.
            if (!task.getTitle().equals(title) || !task.getType().equals(request.type()) || !task.getInitialDescription().equals(description)
                    || !Objects.equals(task.getDueDate(), dueDate) || !task.getRequestedAssignee().equals(assignee)
                    || !Objects.equals(task.getPredecessorTaskId(), request.currentTaskId())) throw conflict("This request key was already used for another task.");
            return snapshot(claim);
        }
        requireOpenClaim(claim);
        var current = tasks.findByClaimIdOrderByCreatedAtDescIdDesc(claimId).stream().filter(this::active).toList();
        if (current.size() != 1 || !Objects.equals(request.currentTaskId(), current.getFirst().getId()))
            throw conflict("The current task changed. Reload tasks before creating its replacement.");
        ClaimTask source = current.getFirst(); checkRevision(source, request.expectedRevision());
        ClaimStaffAccount recipient = assignee.isEmpty() ? null : eligible(assignee);
        if (recipient == null && accounts.findByRoleAndActiveTrueOrderByUsernameAsc("LOSS_ADJUSTER").isEmpty())
            throw conflict("No active loss adjusters are available. Add an eligible account before creating tasks.");
        replace(claim, source, key, title, request.type(), description, dueDate, recipient);
        return snapshot(claim);
    }

    @Transactional
    public ClaimTasksDTO update(long claimId, long taskId, UpdateClaimTaskRequest request) {
        lock();
        ClaimFile claim = claim(claimId);
        ClaimTask task = tasks.findByIdAndClaimId(taskId, claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task was not found in this claim."));
        if (!policy.canCurrentActorManageTasks() && !isOwner(task)) throw forbidden("Only a task manager or its assignee can change this task.");
        requireOpenClaim(claim);
        if (request == null || request.expectedRevision() == null) throw invalid("expectedRevision", "Reload tasks before making changes.");
        if (task.getRevision() != request.expectedRevision()) throw conflict("This task changed. Reload tasks before making changes.");
        int changes = (request.assignee() == null ? 0 : 1) + (request.status() == null ? 0 : 1) + (request.description() == null ? 0 : 1);
        if (changes != 1) throw invalid("task", "Change the assignee, state, or description one at a time.");
        if (request.description() != null) {
            if (!active(task)) throw conflict("Closed tasks are read-only.");
            String description = clean(request.description());
            if (description.length() > 4000) throw invalid("description", "Description must be at most 4,000 characters.");
            if (!task.getDescription().equals(description)) {
                record(task, "Description updated", actorName());
                task.setDescription(description); task.setRevision(task.getRevision() + 1);
            }
        } else
        if (request.assignee() != null) {
            requireManager();
            if (!active(task)) throw conflict("Completed tasks cannot be reassigned.");
            var assignee = eligible(request.assignee());
            if (!assignee.getUsername().equals(task.getAssignee().getUsername())) {
                record(task, "Reassigned from " + task.getAssignee().getDisplayName() + " to " + assignee.getDisplayName(), actorName());
                task.setAssignee(assignee); task.setAssignedBy(actorName()); task.setAssignedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)); task.setAutomaticAssignment(false); task.setRevision(task.getRevision() + 1);
            }
        } else {
            requireOwner(task);
            if (!active(task)) throw conflict("Closed tasks cannot be reopened.");
            if ("COMPLETED".equals(request.status())) throw conflict("Create an assigned handoff to close this task, or confirm final payment on the final settlement task.");
            if (!"IN_PROGRESS".equals(request.status())) throw invalid("status", "The task can only be started here. Use a handoff or final settlement to close it.");
            if (!task.getStatus().equals(request.status())) {
                record(task, "Status changed from " + task.getStatus().replace('_', ' ') + " to IN PROGRESS", actorName());
                task.setStatus("IN_PROGRESS"); task.setRevision(task.getRevision() + 1);
            }
        }
        tasks.saveAndFlush(task);
        return snapshot(claim);
    }

    @Transactional
    public ClaimTasksDTO handoff(long claimId, long taskId, HandoffClaimTaskRequest request) {
        lock();
        ClaimFile claim = claim(claimId);
        ClaimTask source = findTask(claimId, taskId);
        if (request == null) throw invalid("handoff", "Handoff details are required.");
        String key = clean(request.idempotencyKey()), description = clean(request.description());
        if (key.isEmpty() || key.length() > 100 || key.startsWith("automatic-")) throw invalid("handoff", "A valid handoff request key is required.");
        var retry = tasks.findByClaimIdAndRequestKey(claimId, key);
        if (retry.isPresent()) {
            var child = retry.get();
            if (!Objects.equals(child.getPredecessorTaskId(), taskId) || !Objects.equals(child.getCreatedByUsername(), policy.currentActorUsername())
                    || !Objects.equals(child.getType(), request.type()) || !Objects.equals(child.getInitialDescription(), description)
                    || !Objects.equals(child.getRequestedAssignee(), request.assignee())) throw conflict("This request key was already used for another task.");
            if (!policy.canCurrentActorWorkTasks()) throw forbidden("Your current role cannot hand off tasks.");
            return snapshot(claim);
        }
        if (!policy.canCurrentActorManageTasks()) requireOwner(source);
        requireOpenClaim(claim);
        checkRevision(source, request.expectedRevision());
        if (!active(source)) throw conflict("This task has already been closed.");
        if (request.type() == null || !TYPES.contains(request.type())) throw invalid("handoff", "Select a predefined task name.");
        if (description.length() > 4000) throw invalid("handoff", "Description must be at most 4,000 characters.");
        var recipient = eligible(clean(request.assignee()));
        if (!policy.canCurrentActorManageTasks() && recipient.getUsername().equals(source.getAssignee().getUsername())) throw invalid("handoff", "Assign the new task to someone else.");
        replace(claim, source, key, ClaimTaskCatalog.NAMES.get(request.type()), request.type(), description, null, recipient);
        return snapshot(claim);
    }

    @Transactional
    public ClaimTasksDTO settle(long claimId, long taskId, SettleClaimTaskRequest request) {
        lock();
        ClaimFile claim = claim(claimId);
        ClaimTask task = findTask(claimId, taskId);
        requireOwner(task);
        if (!FINAL_TASK_TYPE.equals(task.getType())) throw conflict("Only the final settlement task can close the claim.");
        if (request == null || !request.finalPaymentConfirmed()) throw invalid("settlement", "Confirm that final payment has been completed.");
        if ("Closed".equalsIgnoreCase(claim.getStatus()) && Objects.equals(claim.getClosureTaskId(), taskId)) return snapshot(claim);
        requireOpenClaim(claim); checkRevision(task, request.expectedRevision());
        if (!active(task)) throw conflict("This settlement task is already closed.");
        Instant closed = now();
        closeTask(task, "COMPLETED", "Final payment confirmed; claim closed by final settlement", actorName(), closed);
        for (var remaining : tasks.findByClaimIdOrderByCreatedAtDescIdDesc(claimId)) {
            if (active(remaining)) closeTask(remaining, "CANCELLED", "Closed by final settlement task #" + taskId, actorName(), closed);
        }
        claim.setStatus("Closed"); claim.setClosedAt(closed); claim.setClosedBy(actorName()); claim.setClosureTaskId(taskId);
        claims.saveAndFlush(claim);
        record(task, "Claim " + claim.getClaimNumber() + " closed after the assignee confirmed final payment", actorName());
        tasks.flush();
        return snapshot(claim);
    }

    private void replace(ClaimFile claim, ClaimTask source, String key, String title, String type, String description,
            LocalDate dueDate, ClaimStaffAccount recipient) {
        // Flush closure first to satisfy the partial unique index. The transaction exposes both changes together.
        boolean takeover = !isOwner(source);
        source.setStatus("COMPLETED"); source.setClosedAt(now()); source.setRevision(source.getRevision() + 1);
        tasks.saveAndFlush(source);
        ClaimTask child = create(claim, key, title, type, description, dueDate, recipient, actorName());
        child.setPredecessorTaskId(source.getId()); tasks.save(child);
        String reason = (takeover ? actorName() + " took over from " + source.getAssignee().getDisplayName() + "; replaced by task #"
                : "Handed off to task #") + child.getId() + " assigned to " + child.getAssignee().getDisplayName();
        source.setClosureReason(reason); source.setClosedAt(child.getCreatedAt()); tasks.save(source);
        record(source, reason, actorName());
        record(child, "Replacement for task #" + source.getId() + " created by " + actorName()
                + (takeover ? " using task-management override" : " as its assignee"), actorName());
        tasks.flush();
    }

    private void closeTask(ClaimTask task, String status, String reason, String actor, Instant timestamp) {
        task.setStatus(status); task.setClosedAt(timestamp); task.setClosureReason(reason); task.setRevision(task.getRevision() + 1);
        tasks.save(task); record(task, reason, actor);
    }
    private boolean active(ClaimTask task) { return "OPEN".equals(task.getStatus()) || "IN_PROGRESS".equals(task.getStatus()); }
    private boolean isOwner(ClaimTask task) {
        return policy.canCurrentActorWorkTasks() && task.getAssignee().isActive() && "LOSS_ADJUSTER".equals(task.getAssignee().getRole())
                && task.getAssignee().getUsername().equals(policy.currentActorUsername());
    }
    private void requireOwner(ClaimTask task) { if (!isOwner(task)) throw forbidden("Only the assigned loss adjuster can start, hand off, or settle this task."); }
    private void requireOpenClaim(ClaimFile claim) { if (!"Open".equalsIgnoreCase(claim.getStatus())) throw conflict("This claim is closed. Its tasks are read-only."); }
    private void checkRevision(ClaimTask task, Long expected) { if (expected == null || task.getRevision() != expected) throw conflict("This task changed. Reload tasks before making changes."); }
    private ClaimTask findTask(long claimId, long taskId) { return tasks.findByIdAndClaimId(taskId, claimId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task was not found in this claim.")); }
    private ResponseStatusException forbidden(String message) { return new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private String actorName() { return accounts.findById(policy.currentActorUsername()).map(ClaimStaffAccount::getDisplayName).orElse(policy.currentActorName()); }
    private Instant now() { return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }

    private ClaimTask create(ClaimFile claim, String key, String title, String type, String description,
            LocalDate dueDate, ClaimStaffAccount chosen, String actor) {
        ClaimTask task = new ClaimTask();
        task.setClaim(claim); task.setRequestKey(key); task.setTitle(title); task.setType(type); task.setDescription(description); task.setInitialDescription(description);
        task.setDueDate(dueDate); task.setStatus("OPEN"); task.setCreatedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)); task.setCreatedBy(actor);
        task.setAssignedBy(actor); task.setAssignedAt(task.getCreatedAt());
        task.setRequestedAssignee(chosen == null ? "" : chosen.getUsername());
        task.setCreatedByUsername("Automatic assignment".equals(actor) ? null : policy.currentActorUsername());
        task.setAutomaticAssignment(chosen == null); task.setAssignee(chosen == null ? nextAdjuster() : chosen);
        tasks.saveAndFlush(task);
        record(task, "Created and assigned to " + task.getAssignee().getDisplayName() + (chosen == null ? " by round robin" : " manually"), actor);
        return task;
    }

    private ClaimStaffAccount nextAdjuster() {
        var eligible = accounts.findByRoleAndActiveTrueOrderByUsernameAsc("LOSS_ADJUSTER");
        if (eligible.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "No active loss adjusters are available. Add an eligible account before creating tasks.");
        var rotation = rotations.findById(1L).orElseThrow();
        var next = eligible.stream().filter(account -> rotation.getLastAssignee() == null || account.getUsername().compareTo(rotation.getLastAssignee()) > 0)
                .findFirst().orElse(eligible.getFirst());
        rotation.setLastAssignee(next.getUsername()); rotations.save(rotation);
        return next;
    }

    private ClaimStaffAccount eligible(String username) {
        return accounts.findById(username).filter(account -> account.isActive() && "LOSS_ADJUSTER".equals(account.getRole()))
                .orElseThrow(() -> invalid("assignee", "Select an active loss adjuster."));
    }
    private void lock() {
        if (rotations.lockRotation() != 1) throw new IllegalStateException("Task rotation has not been initialized.");
    }
    private ClaimFile claim(long id) { return claims.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim file was not found.")); }
    private void requireManager() { if (!policy.canCurrentActorManageTasks()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your current role cannot create or assign tasks."); }
    private void record(ClaimTask task, String details, String actor) {
        var event = new ClaimTaskActivity(); event.setTask(task); event.setDetails(details); event.setActor(actor); event.setCreatedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)); activities.save(event);
    }
    private ClaimTasksDTO snapshot(ClaimFile claim) {
        return new ClaimTasksDTO(claim.getId(), claim.getClaimNumber(), claim.getStatus(), policy.canCurrentActorManageTasks() && "Open".equalsIgnoreCase(claim.getStatus()),
            policy.currentActorUsername(), actorName(), FINAL_TASK_TYPE,
            accounts.findByRoleAndActiveTrueOrderByUsernameAsc("LOSS_ADJUSTER").stream().map(account -> new ClaimTasksDTO.Assignee(account.getUsername(), account.getDisplayName())).toList(),
            tasks.findByClaimIdOrderByCreatedAtDescIdDesc(claim.getId()).stream().map(task -> new ClaimTasksDTO.Task(task.getId(), task.getTitle(), task.getType(), task.getDescription(), task.getStatus(),
                task.getAssignee().getUsername(), task.getAssignee().getDisplayName(), task.getDueDate(), task.getCreatedAt(), task.getCreatedBy(), task.isAutomaticAssignment(), task.getRevision(), task.getAssignedBy(), task.getAssignedAt(), task.getClosedAt(), task.getPredecessorTaskId(), task.getClosureReason(),
                isOwner(task) && active(task) && "Open".equalsIgnoreCase(claim.getStatus()),
                (policy.canCurrentActorManageTasks() || isOwner(task)) && active(task) && "Open".equalsIgnoreCase(claim.getStatus()),
                (policy.canCurrentActorManageTasks() || isOwner(task)) && active(task) && "Open".equalsIgnoreCase(claim.getStatus()))).toList(),
            activities.findByTaskClaimIdOrderByIdDesc(claim.getId()).stream().map(event -> new ClaimTasksDTO.Activity(event.getId(), event.getTask().getId(), event.getDetails(), event.getActor(), event.getCreatedAt())).toList(), TYPES);
    }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private ClaimValidationException invalid(String path, String message) { return new ClaimValidationException(List.of(new ClaimValidationError(path, message))); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
