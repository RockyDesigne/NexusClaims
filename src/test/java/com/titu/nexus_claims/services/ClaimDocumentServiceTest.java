package com.titu.nexus_claims.services;

import com.titu.nexus_claims.repositories.*;
import com.titu.nexus_claims.validation.ClaimValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite::memory:", "spring.datasource.hikari.maximum-pool-size=1"})
@Transactional
class ClaimDocumentServiceTest {
    @Autowired private ClaimDocumentService service;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private ClaimDocumentRepository documents;
    @Autowired private ClaimFileRepository claims;
    private static final byte[] PDF = "%PDF-1.4\nFictional document fixture\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aok0AAAAASUVORK5CYII=");
    private MockMultipartFile pdf(String name) { return new MockMultipartFile("file", name, "application/pdf", PDF); }

    @Test void listsEmptyDocumentsAndScreenshotTagChoices() {
        var result = service.list(1L);
        assertThat(result.documents()).isEmpty();
        assertThat(result.canUpload()).isTrue();
        assertThat(result.tags()).contains("Damaged Vehicle", "Accident Documents", "Amicable Report");
        assertThat(result.allowedExtensions()).contains(".pdf", ".jpg", ".docx");
        assertThat(result.maxFileSize()).isEqualTo(10485760);
    }
    @Test void persistsMetadataTagAndExactDownloadBytes() {
        var saved = service.upload(1L, pdf("demo-report.pdf"), "Accident Documents", UUID.randomUUID().toString());
        assertThat(saved.name()).isEqualTo("demo-report.pdf");
        assertThat(saved.category()).isEqualTo("OTHER");
        assertThat(saved.uploadedBy()).isEqualTo("Fictional Insurance Director");
        assertThat(saved.tag()).isEqualTo("Accident Documents");
        entityManager.flush();
        entityManager.clear();
        assertThat(service.content(1L, saved.id()).bytes()).isEqualTo(PDF);
        assertThat(service.list(1L).documents()).containsExactly(saved);
    }
    @Test void filenamesDoNotOverwriteExistingAttachments() {
        var first = service.upload(1L, pdf("same-name.pdf"), "", "first");
        var second = service.upload(1L, new MockMultipartFile("file", "same-name.pdf", "application/pdf", "%PDF-1.4\nSecond fictional file".getBytes(StandardCharsets.UTF_8)), "", "second");
        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(service.list(1L).documents()).hasSize(2);
        assertThat(service.content(1L, first.id()).bytes()).isEqualTo(PDF);
    }
    @Test void duplicateRetryReturnsTheSameAttachment() {
        var first = service.upload(1L, pdf("report.pdf"), "Claim Request", "retry-key");
        entityManager.flush(); entityManager.clear();
        var retried = service.upload(1L, pdf("report.pdf"), "Claim Request", "retry-key");
        assertThat(retried).isEqualTo(first);
        assertThat(documents.count()).isEqualTo(1);
    }
    @Test void changedPayloadCannotReuseAnUploadKey() {
        service.upload(1L, pdf("report.pdf"), "Claim Request", "same-key");
        assertThatThrownBy(() -> service.upload(1L, pdf("report.pdf"), "Accident Documents", "same-key"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
    }
    @Test void attachmentsCannotBeReadThroughAnotherClaim() {
        var saved = service.upload(1L, pdf("private-to-claim.pdf"), "", "isolation");
        assertThat(service.list(2L).documents()).isEmpty();
        assertThatThrownBy(() -> service.content(2L, saved.id()))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.list(999999L))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }
    @Test void contentTypeComesFromTheValidatedFormatAndDownloadsKeepOriginalBytes() {
        var image = service.upload(1L, new MockMultipartFile("file", "photo.PNG", "text/html", PNG), "Damaged Vehicle", "image");
        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.category()).isEqualTo("IMAGE");
        entityManager.flush(); entityManager.clear();
        assertThat(service.content(1L, image.id()).bytes()).isEqualTo(PNG);
        var pdf = service.upload(1L, pdf("report.pdf"), "", "pdf");
        assertThat(service.content(1L, pdf.id()).bytes()).isEqualTo(PDF);
    }
    @Test void rejectsEmptyUnsupportedMismatchedOrInvalidTaggedFiles() {
        for (var file : List.of(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]),
                new MockMultipartFile("file", "script.html", "text/html", "<script>1</script>".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("file", "fake.png", "image/png", PDF))) {
            assertThatThrownBy(() -> service.upload(1L, file, "", UUID.randomUUID().toString())).isInstanceOf(ClaimValidationException.class);
        }
        assertThatThrownBy(() -> service.upload(1L, pdf("report.pdf"), "unknown-tag", "tag")).isInstanceOf(ClaimValidationException.class);
        assertThat(documents.count()).isZero();
    }
    @Test void enforcesTheServerSizeLimit() {
        var smallLimit = new ClaimDocumentService(claims, documents, new ClaimAccessPolicy(ClaimAccessPolicy.Role.INSURANCE_DIRECTOR), new ClaimDocumentRules(5));
        assertThatThrownBy(() -> smallLimit.upload(1L, pdf("report.pdf"), "", "large")).isInstanceOf(ClaimValidationException.class);
        assertThat(documents.count()).isZero();
    }
    @Test void stripsPathComponentsAndRejectsControlCharacters() {
        var saved = service.upload(1L, pdf("../../demo/report.pdf"), "", "path");
        assertThat(saved.name()).isEqualTo("report.pdf");
        assertThatThrownBy(() -> service.upload(1L, pdf("bad\r\nname.pdf"), "", "controls")).isInstanceOf(ClaimValidationException.class);
    }
    @Test void viewerCanBrowseAndDownloadButCannotUpload() {
        var saved = service.upload(1L, pdf("report.pdf"), "", "director");
        var viewer = new ClaimDocumentService(claims, documents, new ClaimAccessPolicy(ClaimAccessPolicy.Role.VIEWER), new ClaimDocumentRules(10485760));
        assertThat(viewer.list(1L).canUpload()).isFalse();
        assertThat(viewer.content(1L, saved.id()).bytes()).isEqualTo(PDF);
        assertThatThrownBy(() -> viewer.upload(1L, pdf("new.pdf"), "", "viewer"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
    }
}
