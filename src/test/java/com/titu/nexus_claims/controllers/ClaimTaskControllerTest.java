package com.titu.nexus_claims.controllers;
import com.titu.nexus_claims.dtos.*;
import com.titu.nexus_claims.services.ClaimTaskService;
import com.titu.nexus_claims.validation.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@WebMvcTest(ClaimTaskController.class) @AutoConfigureMockMvc(addFilters = false)
class ClaimTaskControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ClaimTaskService service;
    @Test void getAndCreateAreClaimScoped() throws Exception {
        when(service.get(7)).thenReturn(new ClaimTasksDTO(7, "TEST-7", "Open", true, "fictional-insurance_director", "Fictional Insurance Director", "CLAIM_SETTLEMENT", List.of(), List.of(), List.of(), List.of()));
        mvc.perform(get("/claims/7/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$.claimId").value(7));
        mvc.perform(post("/claims/7/tasks").contentType("application/json").content("""
                {"idempotencyKey":"demo","title":"Test","type":"DAMAGE_ASSESSMENT","assignee":""}
                """))
            .andExpect(status().isOk());
        verify(service).create(eq(7L), any(CreateClaimTaskRequest.class));
    }
    @Test void validationForbiddenAndConflictHaveUsefulHttpResponses() throws Exception {
        when(service.create(eq(7L), any())).thenThrow(new ClaimValidationException(List.of(new ClaimValidationError("title", "Enter a title."))));
        mvc.perform(post("/claims/7/tasks").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].path").value("title"));
        when(service.update(eq(7L), eq(2L), any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Read only"));
        mvc.perform(put("/claims/7/tasks/2").contentType("application/json").content("""
                {"expectedRevision":0,"assignee":"demo"}
                """))
            .andExpect(status().isForbidden());
        when(service.update(eq(7L), eq(2L), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Stale task"));
        mvc.perform(put("/claims/7/tasks/2").contentType("application/json").content("""
                {"expectedRevision":0,"assignee":"demo"}
                """))
            .andExpect(status().isConflict());
    }
}
