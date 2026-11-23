package com.titu.nexus_claims.services;

import com.titu.nexus_claims.repositories.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite::memory:", "spring.datasource.hikari.maximum-pool-size=1"})
@Transactional
class ClaimDocumentPreviewServiceTest {
    @Autowired private ClaimDocumentService documents;
    @Autowired private ClaimDocumentPreviewService previews;
    @Autowired private ClaimDocumentPreviewRepository cache;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Test void existingPdfGetsAPersistedPreviewWithoutChangingItsOriginal() throws Exception {
        byte[] original;
        try (var pdf = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage()); pdf.addPage(new PDPage()); pdf.save(bytes); original = bytes.toByteArray();
        }
        var file = documents.upload(1L, new MockMultipartFile("file", "two-pages.pdf", "application/pdf", original), "", "preview-pdf");
        var first = previews.preview(1L, file.id());
        assertThat(first.contentType()).isEqualTo("image/png");
        assertThat(javax.imageio.ImageIO.read(new ByteArrayInputStream(first.bytes()))).isNotNull();
        entityManager.flush(); entityManager.clear();
        assertThat(previews.preview(1L, file.id()).bytes()).isEqualTo(first.bytes());
        assertThat(cache.count()).isEqualTo(1);
        assertThat(documents.content(1L, file.id()).bytes()).isEqualTo(original);
        assertThatThrownBy(() -> previews.preview(2L, file.id()))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }
    @Test void failedRenderingDoesNotDestroyAnAttachmentOrCacheABrokenImage() {
        byte[] original = "%PDF-not-a-valid-document".getBytes();
        var file = documents.upload(1L, new MockMultipartFile("file", "broken.pdf", "application/pdf", original), "", "broken-preview");
        assertThatThrownBy(() -> previews.preview(1L, file.id()))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(422));
        assertThat(cache.count()).isZero();
        assertThat(documents.content(1L, file.id()).bytes()).isEqualTo(original);
    }
}
