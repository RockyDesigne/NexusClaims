package com.titu.nexus_claims.services;

import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.*;
import static org.assertj.core.api.Assertions.*;

class DocumentFirstPageRendererTest {
    @Test void rendersTheFirstPageAndNeverTheSecondPage() throws Exception {
        byte[] original;
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (Color color : new Color[]{Color.RED, Color.BLUE}) {
                var page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(200, 300));
                pdf.addPage(page);
                try (var stream = new PDPageContentStream(pdf, page)) {
                    stream.setNonStrokingColor(color); stream.addRect(0, 0, 200, 300); stream.fill();
                }
            }
            pdf.save(output); original = output.toByteArray();
        }
        var renderer = new DocumentFirstPageRenderer(new OfficePreviewConverter(""));
        byte[] preview = renderer.render("two-pages.pdf", "application/pdf", original);
        var image = ImageIO.read(new ByteArrayInputStream(preview));
        assertThat(image).isNotNull();
        assertThat(new Color(image.getRGB(10, 10))).isEqualTo(Color.RED);
        assertThat(Math.max(image.getWidth(), image.getHeight())).isLessThanOrEqualTo(1200);
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(original)) { assertThat(pdf.getNumberOfPages()).isEqualTo(2); }
    }
    @Test void malformedPdfIsAnExplicitFailureRatherThanAFakePreview() {
        var renderer = new DocumentFirstPageRenderer(new OfficePreviewConverter(""));
        assertThatThrownBy(() -> renderer.render("broken.pdf", "application/pdf", "%PDF-broken".getBytes()))
                .isInstanceOf(IOException.class);
    }
}
