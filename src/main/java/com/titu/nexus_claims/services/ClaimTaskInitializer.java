package com.titu.nexus_claims.services;
import com.titu.nexus_claims.repositories.ClaimFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import java.util.Comparator;
@Component @RequiredArgsConstructor
public class ClaimTaskInitializer implements ApplicationRunner {
    private final ClaimFileRepository claims;
    private final ClaimTaskService tasks;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Override public void run(ApplicationArguments arguments) {
        claims.findAll().stream().sorted(Comparator.comparing(claim -> claim.getId())).forEach(claim -> { tasks.upgradeTasks(claim.getId()); tasks.ensureOpenTask(claim.getId()); });
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS claim_tasks_one_active_per_claim ON claim_tasks(claim_id) WHERE status IN ('OPEN', 'IN_PROGRESS')");
    }
}
