package com.titu.nexus_claims.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite::memory:", "spring.datasource.hikari.maximum-pool-size=1"})
@AutoConfigureMockMvc
@Transactional
class ClaimDocumentControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    private static final byte[] PDF = "%PDF-1.4\nFictional HTTP fixture\n%%EOF".getBytes(StandardCharsets.UTF_8);
    @Test void multipartUploadListsAndDownloadsWithoutExposingStoredContent() throws Exception {
        var uploaded = mvc.perform(multipart("/claims/1/documents")
                        .file(new MockMultipartFile("file", "demo.pdf", "application/pdf", PDF))
                        .param("tag", "Accident Documents").param("idempotencyKey", "http-upload"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("demo.pdf"))
                .andExpect(jsonPath("$.content").doesNotExist()).andReturn();
        long id = new ObjectMapper().readTree(uploaded.getResponse().getContentAsString()).path("id").asLong();
        entityManager.flush(); entityManager.clear();
        mvc.perform(get("/claims/1/documents")).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].tag").value("Accident Documents"))
                .andExpect(jsonPath("$.documents[0].content").doesNotExist());
        mvc.perform(get("/claims/1/documents/" + id + "/download")).andExpect(status().isOk())
                .andExpect(content().bytes(PDF)).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
        mvc.perform(get("/claims/2/documents/" + id + "/download")).andExpect(status().isNotFound());
    }
    @Test void invalidUploadReturnsUsefulValidationMessages() throws Exception {
        mvc.perform(multipart("/claims/1/documents")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))
                        .param("idempotencyKey", "bad-upload"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$[0].path").value("file"));
    }
}
