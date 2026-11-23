package com.titu.nexus_claims.services;

import com.titu.nexus_claims.models.ClaimDocumentPreview;
import com.titu.nexus_claims.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ClaimDocumentPreviewService {
    private final ClaimDocumentRepository documents;
    private final ClaimDocumentPreviewRepository previews;
    private final DocumentFirstPageRenderer renderer;
    private final Object[] locks = IntStream.range(0, 32).mapToObj(index -> new Object()).toArray();

    // Conversion is deliberately outside a long-running database transaction.
    public ClaimDocumentService.Content preview(long claimId, long documentId) {
        var source = documents.findPreviewSourceByIdAndClaimId(documentId, claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found for this claim."));
        if (source.getCategory().equals("IMAGE")) {
            var file = documents.findByIdAndClaimId(documentId, claimId).orElseThrow();
            return new ClaimDocumentService.Content(file.getName(), file.getContentType(), file.getContent());
        }
        synchronized (locks[Math.floorMod(documentId, locks.length)]) {
            var cached = previews.findById(documentId);
            String name = source.getName() + ".page-1.png";
            if (cached.isPresent() && cached.get().getSourceHash().equals(source.getSha256()))
                return new ClaimDocumentService.Content(name, "image/png", cached.get().getPng());
            var file = documents.findByIdAndClaimId(documentId, claimId).orElseThrow();
            try {
                byte[] png = renderer.render(file.getName(), file.getContentType(), file.getContent());
                previews.saveAndFlush(new ClaimDocumentPreview(documentId, file.getSha256(), png));
                return new ClaimDocumentService.Content(name, "image/png", png);
            } catch (OfficePreviewConverter.UnavailableException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Preview renderer is temporarily unavailable. Please retry.", exception);
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unable to preview this document. It may be damaged or password-protected.", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Preview generation was interrupted. Please retry.", exception);
            }
        }
    }
}
