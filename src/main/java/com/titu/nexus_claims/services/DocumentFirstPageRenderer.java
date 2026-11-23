package com.titu.nexus_claims.services;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.*;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
public class DocumentFirstPageRenderer {
    private final OfficePreviewConverter office;
    private final Semaphore slots = new Semaphore(2);

    public byte[] render(String name, String contentType, byte[] original) throws IOException, InterruptedException {
        if (!slots.tryAcquire(15, TimeUnit.SECONDS)) throw new OfficePreviewConverter.UnavailableException("Preview renderer is busy.");
        try {
            String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            byte[] pdf = contentType.equals("application/pdf") ? original : office.firstPagePdf(original, extension);
            try (var document = Loader.loadPDF(pdf); var output = new ByteArrayOutputStream()) {
                if (document.getNumberOfPages() == 0) throw new IOException("Document has no pages.");
                var bounds = document.getPage(0).getCropBox();
                float longest = Math.max(bounds.getWidth(), bounds.getHeight());
                if (!Float.isFinite(longest) || longest <= 0) throw new IOException("Invalid page size.");
                var renderer = new PDFRenderer(document);
                renderer.setSubsamplingAllowed(true);
                var image = renderer.renderImage(0, Math.min(2, 1200 / longest), ImageType.RGB);
                if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoding is not available.");
                return output.toByteArray();
            }
        } finally { slots.release(); }
    }
}
