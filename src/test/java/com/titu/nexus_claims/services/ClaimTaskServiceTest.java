package com.titu.nexus_claims.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titu.nexus_claims.dtos.*;
import com.titu.nexus_claims.repositories.*;
import com.titu.nexus_claims.validation.ClaimValidationException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite::memory:", "spring.datasource.hikari.maximum-pool-size=1"})
@Transactional
class ClaimTaskServiceTest {
    @Autowired ClaimTaskService service;
    @Autowired ClaimFileService files;
    @Autowired ClaimFileRepository claims;
    @Autowired ClaimStaffAccountRepository accounts;
    @Autowired ClaimTaskRepository tasks;
    @Autowired ClaimTaskActivityRepository activities;
    @Autowired ClaimTaskRotationRepository rotation;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private boolean active(ClaimTasksDTO.Task task) { return Set.of("OPEN", "IN_PROGRESS").contains(task.status()); }
    private ClaimTasksDTO.Task current(long id) { return service.get(id).tasks().stream().filter(this::active).findFirst().orElseThrow(); }
    private ClaimTaskService actor(ClaimAccessPolicy.Role role, String username) {
        return new ClaimTaskService(claims, accounts, tasks, activities, rotation, new ClaimAccessPolicy(role, username));
    }
    private ClaimTaskService owner(String username) { return actor(ClaimAccessPolicy.Role.LOSS_ADJUSTER, username); }
    private CreateClaimTaskRequest request(long claimId, String type, String recipient) {
        var source = current(claimId);
        return new CreateClaimTaskRequest(UUID.randomUUID().toString(), "Arbitrary title ignored", type, "Fictional task notes", "", recipient, source.id(), source.revision());
    }
    private HandoffClaimTaskRequest handoff(ClaimTasksDTO.Task source, String type, String recipient) {
        return new HandoffClaimTaskRequest(UUID.randomUUID().toString(), source.revision(), type, "Fictional handoff", recipient);
    }
    private void oneActive(long claimId) { assertThat(service.get(claimId).tasks()).filteredOn(this::active).hasSize(1); }

    @Test void startupAndNewClaimCreationHaveExactlyOneInitialAssessment() {
        for (long id : List.of(1L, 2L, 3L)) {
            var initial = current(id); assertThat(initial.type()).isEqualTo("DAMAGE_ASSESSMENT");
            service.ensureOpenTask(id); service.ensureOpenTask(id);
            assertThat(service.get(id).tasks()).hasSize(1); oneActive(id);
        }
        assertThat(List.of(current(1).assignee(), current(2).assignee(), current(3).assignee()))
                .containsExactly("demo-adjuster-alex", "demo-adjuster-dana", "demo-adjuster-alex");
        var request = new CreateClaimFileRequest(UUID.randomUUID().toString(), DemoClaimFixtures.load(mapper).getFirst().path("claim"));
        var created = files.createClaim(request); assertThat(files.createClaim(request).id()).isEqualTo(created.id());
        assertThat(current(created.id()).title()).isEqualTo("Damage Assessment"); oneActive(created.id());
    }

    @Test void directorCreationTakesOverClosesPreviousTaskAndPreservesItsOwner() {
        var source = current(1);
        var result = service.create(1, request(1, "PAYMENT", "demo-adjuster-dana"));
        var next = current(1); assertThat(next.predecessorTaskId()).isEqualTo(source.id());
        assertThat(next.assignedBy()).isEqualTo("Fictional Insurance Director");
        assertThat(next.assignee()).isEqualTo("demo-adjuster-dana"); oneActive(1);
        assertThat(result.tasks()).filteredOn(task -> task.id() == source.id()).anySatisfy(task -> {
            assertThat(task.status()).isEqualTo("COMPLETED"); assertThat(task.closedAt()).isNotNull();
            assertThat(task.assignee()).isEqualTo(source.assignee());
            assertThat(task.closureReason()).contains("took over from Alex Marin", "task #" + next.id());
        });
        assertThat(result.activities()).anySatisfy(event -> {
            assertThat(event.details()).contains("task-management override"); assertThat(event.actor()).isEqualTo("Fictional Insurance Director");
        });
        em.flush(); em.clear(); oneActive(1);
    }

    @Test void directorCanReplaceThroughTheTicketWithoutBecomingItsAssignee() {
        var source = current(1); assertThat(source.canWork()).isFalse(); assertThat(source.canReplace()).isTrue();
        service.handoff(1, source.id(), handoff(source, "DAMAGE_REASSESSMENT", source.assignee()));
        assertThat(current(1).predecessorTaskId()).isEqualTo(source.id());
        assertThat(current(1).assignee()).isEqualTo(source.assignee()); oneActive(1);
    }

    @Test void inspectorKeepsAuthorizedCreationAndAssignmentCapabilities() {
        var inspector = actor(ClaimAccessPolicy.Role.CLAIMS_INSPECTOR, "");
        inspector.create(1, request(1, "RECOVERY", "demo-adjuster-dana"));
        var next = current(1); assertThat(next.createdBy()).isEqualTo("Fictional Claims Inspector");
        inspector.update(1, next.id(), new UpdateClaimTaskRequest(next.revision(), "demo-adjuster-alex", null));
        assertThat(current(1).assignee()).isEqualTo("demo-adjuster-alex"); oneActive(1);
        assertThat(new ClaimAccessPolicy(ClaimAccessPolicy.Role.CLAIMS_INSPECTOR).canCurrentActorEdit()).isFalse();
    }

    @Test void createRequiresCurrentTaskAndRevisionAndRejectsStaleReplacement() {
        var stale = request(1, "PAYMENT", "demo-adjuster-dana");
        assertThatThrownBy(() -> service.create(1, new CreateClaimTaskRequest("missing-source", "", "PAYMENT", "", "", "")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("current task changed");
        service.create(1, request(1, "DAMAGE_REASSESSMENT", "demo-adjuster-alex"));
        var next = current(1);
        assertThatThrownBy(() -> service.create(1, stale)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(current(1).id()).isEqualTo(next.id()); oneActive(1);
    }

    @Test void replacementRetryDoesNotCloseTheReplacementOrConsumeAnotherRoundRobinTurn() {
        var request = request(1, "PAYMENT", ""); service.create(1, request);
        var next = current(1); var auditSize = service.get(1).activities().size();
        assertThat(next.assignee()).isEqualTo("demo-adjuster-dana");
        service.update(1, next.id(), new UpdateClaimTaskRequest(next.revision(), null, null, "Updated free text"));
        service.create(1, request);
        assertThat(current(1).id()).isEqualTo(next.id()); assertThat(current(1).description()).isEqualTo("Updated free text");
        assertThat(service.get(1).activities()).hasSize(auditSize + 1);
        assertThat(service.get(1).tasks()).hasSize(2); oneActive(1);
        assertThat(rotation.findById(1L).orElseThrow().getLastAssignee()).isEqualTo("demo-adjuster-dana");
        assertThatThrownBy(() -> service.create(1, new CreateClaimTaskRequest(request.idempotencyKey(), "", "RECOVERY", request.description(), "", "", request.currentTaskId(), request.expectedRevision())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test void manualAssignmentsDoNotConsumeAutomaticRotationAndOnlyOneTaskRemainsActive() {
        service.create(1, request(1, "PAYMENT", ""));
        service.create(2, request(2, "RECOVERY", "demo-adjuster-alex"));
        var task = current(2); service.update(2, task.id(), new UpdateClaimTaskRequest(task.revision(), "demo-adjuster-dana", null));
        em.flush(); em.clear(); service.create(3, request(3, "ANTI_FRAUD", ""));
        assertThat(current(3).assignee()).isEqualTo("demo-adjuster-alex");
        for (long id : List.of(1L, 2L, 3L)) oneActive(id);
    }

    @Test void inactiveOrMissingRecipientsFailBeforeClosingTheCurrentTask() {
        var source = current(1); var alex = accounts.findById("demo-adjuster-alex").orElseThrow();
        alex.setActive(false); accounts.saveAndFlush(alex);
        assertThatThrownBy(() -> service.create(1, request(1, "PAYMENT", "demo-adjuster-alex"))).isInstanceOf(ClaimValidationException.class);
        assertThat(current(1).id()).isEqualTo(source.id());
        service.create(1, request(1, "PAYMENT", "")); assertThat(current(1).assignee()).isEqualTo("demo-adjuster-dana");
        var dana = accounts.findById("demo-adjuster-dana").orElseThrow(); dana.setActive(false); accounts.saveAndFlush(dana);
        var before = current(1);
        assertThatThrownBy(() -> service.create(1, request(1, "RECOVERY", ""))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("No active loss adjusters");
        assertThat(current(1).id()).isEqualTo(before.id()); oneActive(1);
    }

    @Test void viewerAndUnassignedAdjusterCannotReplaceTasksOrUseManagerCreation() {
        var source = current(1);
        for (var reader : List.of(actor(ClaimAccessPolicy.Role.VIEWER, "demo-adjuster-alex"), owner("demo-adjuster-dana"))) {
            assertThat(reader.get(1).tasks().getFirst().canReplace()).isFalse();
            assertThatThrownBy(() -> reader.create(1, request(1, "PAYMENT", "demo-adjuster-dana"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
            assertThatThrownBy(() -> reader.handoff(1, source.id(), handoff(source, "PAYMENT", "demo-adjuster-dana"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        }
        assertThatThrownBy(() -> owner("demo-adjuster-alex").create(1, request(1, "PAYMENT", "demo-adjuster-dana"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        oneActive(1);
    }

    @Test void ownerHandoffRequiresAnotherRecipientAndRetryIsIdempotent() {
        var source = current(1); var alex = owner("demo-adjuster-alex");
        assertThatThrownBy(() -> alex.handoff(1, source.id(), handoff(source, "PAYMENT", "demo-adjuster-alex"))).isInstanceOf(ClaimValidationException.class);
        var request = handoff(source, "PAYMENT", "demo-adjuster-dana");
        alex.handoff(1, source.id(), request); var next = current(1); var auditSize = service.get(1).activities().size();
        alex.handoff(1, source.id(), request);
        assertThat(current(1).id()).isEqualTo(next.id()); assertThat(service.get(1).activities()).hasSize(auditSize);
        assertThat(next.createdBy()).isEqualTo("Alex Marin (demo)"); oneActive(1);
        assertThat(owner("demo-adjuster-dana").get(1).tasks()).filteredOn(this::active).allSatisfy(task -> assertThat(task.canReplace()).isTrue());
    }

    @Test void inProgressCountsAsTheOnlyActiveTaskAndIsReplacedNormally() {
        var source = current(1); owner("demo-adjuster-alex").update(1, source.id(), new UpdateClaimTaskRequest(source.revision(), null, "IN_PROGRESS"));
        assertThat(current(1).status()).isEqualTo("IN_PROGRESS"); oneActive(1);
        service.create(1, request(1, "PAYMENT", "demo-adjuster-dana"));
        assertThat(service.get(1).tasks()).filteredOn(task -> task.status().equals("IN_PROGRESS")).isEmpty();
        assertThat(current(1).status()).isEqualTo("OPEN"); oneActive(1);
    }

    @Test void directCompletionAndCrossClaimActionsAreRejected() {
        var source = current(1);
        assertThatThrownBy(() -> owner("demo-adjuster-alex").update(1, source.id(), new UpdateClaimTaskRequest(source.revision(), null, "COMPLETED")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("handoff");
        assertThatThrownBy(() -> service.handoff(2, source.id(), handoff(source, "PAYMENT", "demo-adjuster-dana"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> service.get(999999)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        oneActive(1);
    }

    @Test void catalogValidationAndFreeTextLimitsApplyToReplacements() {
        assertThat(service.get(1).taskTypes()).containsExactly("DAMAGE_ASSESSMENT", "CLAIM_SETTLEMENT", "DAMAGE_REASSESSMENT", "PAYMENT", "RECOVERY", "ANTI_FRAUD");
        for (var entry : ClaimTaskCatalog.NAMES.entrySet()) {
            service.create(1, request(1, entry.getKey(), "demo-adjuster-alex"));
            assertThat(current(1).title()).isEqualTo(entry.getValue()); oneActive(1);
        }
        assertThatThrownBy(() -> service.create(1, request(1, "CLAIM_REVIEW", ""))).isInstanceOf(ClaimValidationException.class);
        var source = current(1);
        assertThatThrownBy(() -> service.create(1, new CreateClaimTaskRequest("invalid-text", "", "PAYMENT", "x".repeat(4001), "", "", source.id(), source.revision()))).isInstanceOf(ClaimValidationException.class);
        assertThatThrownBy(() -> service.create(1, new CreateClaimTaskRequest("invalid-null", null, null, null, null, null))).isInstanceOf(ClaimValidationException.class);
    }

    @Test void assignmentMetadataIsSeparateFromCreationAndStateChanges() {
        service.create(1, request(1, "PAYMENT", "demo-adjuster-alex")); var source = current(1);
        var inspector = actor(ClaimAccessPolicy.Role.CLAIMS_INSPECTOR, "");
        inspector.update(1, source.id(), new UpdateClaimTaskRequest(source.revision(), "demo-adjuster-dana", null));
        var reassigned = current(1);
        assertThat(reassigned.assignedBy()).isEqualTo("Fictional Claims Inspector"); assertThat(reassigned.createdBy()).isEqualTo("Fictional Insurance Director");
        owner("demo-adjuster-dana").update(1, source.id(), new UpdateClaimTaskRequest(reassigned.revision(), null, "IN_PROGRESS"));
        assertThat(current(1).assignedAt()).isEqualTo(reassigned.assignedAt());
        assertThatThrownBy(() -> service.update(1, source.id(), new UpdateClaimTaskRequest(source.revision(), "demo-adjuster-alex", null))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        owner("demo-adjuster-dana").handoff(1, source.id(), handoff(current(1), "CLAIM_SETTLEMENT", "demo-adjuster-alex"));
        em.flush(); em.clear(); assertThat(service.get(1).tasks()).filteredOn(task -> task.id() == source.id()).anySatisfy(task -> { assertThat(task.closedAt()).isNotNull(); assertThat(task.assignedBy()).isEqualTo("Fictional Claims Inspector"); });
    }

    @Test void staleReassignedOrInactiveOwnerCannotHandOff() {
        var source = current(1); var request = handoff(source, "PAYMENT", "demo-adjuster-dana");
        service.update(1, source.id(), new UpdateClaimTaskRequest(source.revision(), "demo-adjuster-dana", null));
        assertThatThrownBy(() -> owner("demo-adjuster-alex").handoff(1, source.id(), request)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> owner("demo-adjuster-dana").handoff(1, source.id(), handoff(source, "PAYMENT", "demo-adjuster-alex"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        var dana = accounts.findById("demo-adjuster-dana").orElseThrow(); dana.setActive(false); accounts.saveAndFlush(dana);
        assertThatThrownBy(() -> owner("demo-adjuster-dana").handoff(1, source.id(), handoff(current(1), "PAYMENT", "demo-adjuster-alex"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        oneActive(1);
    }

    @Test void settlementRequiresOwnerCorrectTypeAndExplicitPaymentConfirmation() {
        service.create(1, request(1, "PAYMENT", "demo-adjuster-alex")); var payment = current(1);
        assertThatThrownBy(() -> owner("demo-adjuster-alex").settle(1, payment.id(), new SettleClaimTaskRequest(payment.revision(), true))).isInstanceOf(ResponseStatusException.class);
        service.create(1, request(1, "CLAIM_SETTLEMENT", "demo-adjuster-alex")); var terminal = current(1);
        assertThatThrownBy(() -> service.settle(1, terminal.id(), new SettleClaimTaskRequest(terminal.revision(), true))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> owner("demo-adjuster-dana").settle(1, terminal.id(), new SettleClaimTaskRequest(terminal.revision(), true))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> owner("demo-adjuster-alex").settle(1, terminal.id(), new SettleClaimTaskRequest(terminal.revision(), false))).isInstanceOf(ClaimValidationException.class);
        assertThatThrownBy(() -> owner("demo-adjuster-alex").settle(1, terminal.id(), new SettleClaimTaskRequest(99L, true))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(service.get(1).claimStatus()).isEqualTo("Open"); oneActive(1);
    }

    @Test void finalSettlementLeavesNoActiveTasksClosesClaimAndNeverReopensOnRestart() {
        var first = current(1); var alex = owner("demo-adjuster-alex"); var dana = owner("demo-adjuster-dana");
        alex.handoff(1, first.id(), handoff(first, "PAYMENT", "demo-adjuster-dana"));
        var payment = current(1); dana.handoff(1, payment.id(), handoff(payment, "CLAIM_SETTLEMENT", "demo-adjuster-alex"));
        var terminal = current(1); var result = alex.settle(1, terminal.id(), new SettleClaimTaskRequest(terminal.revision(), true));
        assertThat(result.claimStatus()).isEqualTo("Closed"); assertThat(result.tasks()).allSatisfy(task -> { assertThat(task.status()).isEqualTo("COMPLETED"); assertThat(task.closedAt()).isNotNull(); assertThat(task.canReplace()).isFalse(); });
        em.flush(); em.clear(); assertThat(claims.findById(1L).orElseThrow().getClosureTaskId()).isEqualTo(terminal.id());
        assertThat(files.getClaim(1).summary().status()).isEqualTo("Closed");
        assertThat(files.getClaims("", "Closed").claims()).extracting(ClaimFileDTO::id).containsExactly(1L);
        assertThat(files.getClaims("", "").statusCounts()).containsEntry("Closed", 1L).containsEntry("Open", 2L);
        assertThat(alex.settle(1, terminal.id(), new SettleClaimTaskRequest(terminal.revision(), true)).activities()).hasSize(result.activities().size());
        service.ensureOpenTask(1); assertThat(service.get(1).tasks()).hasSize(3).noneMatch(this::active);
        assertThatThrownBy(() -> service.create(1, new CreateClaimTaskRequest("closed", "", "PAYMENT", "", "", ""))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void startupReconcilesLegacyDuplicatesAndDoesNotRepeatHistory() {
        jdbc.execute("DROP INDEX claim_tasks_one_active_per_claim");
        jdbc.update("INSERT INTO claim_tasks(claim_id,request_key,requested_assignee,title,type,description,initial_description,status,assignee,created_at,created_by,automatic_assignment,revision) SELECT claim_id,'legacy-duplicate',requested_assignee,title,type,description,description,'IN_PROGRESS',assignee,created_at+1000,created_by,0,0 FROM claim_tasks WHERE claim_id=1");
        em.clear(); var newest = service.get(1).tasks().getFirst();
        service.ensureOpenTask(1); em.flush(); em.clear();
        assertThat(current(1).id()).isEqualTo(newest.id()); oneActive(1);
        assertThat(service.get(1).tasks()).filteredOn(task -> task.id() != newest.id()).allSatisfy(task -> { assertThat(task.status()).isEqualTo("CANCELLED"); assertThat(task.closureReason()).contains("single-active-task update"); assertThat(task.closedAt()).isNotNull(); });
        var count = service.get(1).activities().size(); service.ensureOpenTask(1); assertThat(service.get(1).activities()).hasSize(count);
        jdbc.execute("CREATE UNIQUE INDEX claim_tasks_one_active_per_claim ON claim_tasks(claim_id) WHERE status IN ('OPEN','IN_PROGRESS')");
    }

    @Test void databaseConstraintRejectsASecondActiveTaskEvenOutsideTheService() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO claim_tasks(claim_id,request_key,requested_assignee,title,type,description,status,assignee,created_at,created_by,automatic_assignment,revision) SELECT claim_id,'illegal-duplicate',requested_assignee,title,type,description,'IN_PROGRESS',assignee,created_at,created_by,0,0 FROM claim_tasks WHERE claim_id=1"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("UNIQUE constraint failed: claim_tasks.claim_id");
    }

    @Test void openClaimWithOnlyOldClosedTasksGetsOneRecoveryTaskWithoutReusingItsKey() {
        var source = tasks.findById(current(1).id()).orElseThrow(); source.setStatus("COMPLETED"); tasks.saveAndFlush(source);
        service.ensureOpenTask(1); oneActive(1); assertThat(service.get(1).tasks()).hasSize(2);
        assertThat(current(1).id()).isNotEqualTo(source.getId()); service.ensureOpenTask(1); assertThat(service.get(1).tasks()).hasSize(2);
    }

    @Test void legacyCatalogMigrationPreservesDescriptionsAndAuditDates() {
        var first = tasks.findById(current(1).id()).orElseThrow(); var description = first.getDescription();
        first.setType("CLAIM_REVIEW"); first.setTitle("Old review name"); first.setAssignedAt(null); first.setAssignedBy(null); first.setInitialDescription(null); tasks.saveAndFlush(first);
        service.upgradeTasks(1); em.flush(); em.clear(); var migrated = current(1);
        assertThat(migrated.title()).isEqualTo("Damage Assessment"); assertThat(migrated.description()).isEqualTo(description);
        assertThat(migrated.assignedAt()).isNotNull(); assertThat(migrated.assignedBy()).isEqualTo("Automatic assignment");
        assertThat(service.get(1).activities()).anySatisfy(event -> assertThat(event.details()).contains("Old review name"));
        var count = service.get(1).activities().size(); service.upgradeTasks(1); assertThat(service.get(1).activities()).hasSize(count); oneActive(1);
    }
}
