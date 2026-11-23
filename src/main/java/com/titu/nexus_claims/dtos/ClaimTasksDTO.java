package com.titu.nexus_claims.dtos;
import java.time.*;
import java.util.List;
public record ClaimTasksDTO(long claimId, String claimNumber, String claimStatus, boolean canManage,
        String currentActorUsername, String currentActorName, String finalTaskType,
        List<Assignee> adjusters, List<Task> tasks, List<Activity> activities, List<String> taskTypes) {
    public record Assignee(String username, String name) {}
    public record Task(long id, String title, String type, String description, String status,
            String assignee, String assigneeName, LocalDate dueDate, Instant createdAt,
            String createdBy, boolean automaticAssignment, long revision, String assignedBy, Instant assignedAt, Instant closedAt, Long predecessorTaskId, String closureReason, boolean canWork, boolean canEditDescription, boolean canReplace) {}
    public record Activity(long id, long taskId, String details, String actor, Instant createdAt) {}
}
