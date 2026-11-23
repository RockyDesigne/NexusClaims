package com.titu.nexus_claims.services;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class ClaimDocumentRules {
    // Visible tag choices transcribed from PDF page 10. Further taxonomy can be configured later.
    public static final List<String> TAGS = List.of("Accident Documents", "Agreement Compensation Request", "Alienation Document",
            "Amicable Report", "Annex 2", "Auction Report", "Audatex Report", "Chassis Number", "Claim Request",
            "Claimant Documents", "Compensation Request", "Damaged Parts", "Damaged Vehicle", "Damaged Vehicle Documents",
            "Driving License", "Event Statement", "GDPR", "Guilty Car Documents", "Guilty Person Documents");
    public static final Map<String, String> CONTENT_TYPES;
    static {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("pdf", "application/pdf"); types.put("jpg", "image/jpeg"); types.put("jpeg", "image/jpeg");
        types.put("png", "image/png"); types.put("gif", "image/gif"); types.put("webp", "image/webp");
        types.put("txt", "text/plain"); types.put("csv", "text/csv");
        types.put("doc", "application/msword"); types.put("xls", "application/vnd.ms-excel");
        types.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        types.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        CONTENT_TYPES = Collections.unmodifiableMap(types);
    }
    private final long maxFileSize;
    public ClaimDocumentRules(@Value("${claims.documents.max-file-size-bytes:10485760}") long maxFileSize) { this.maxFileSize = maxFileSize; }
    public long maxFileSize() { return maxFileSize; }
    public List<String> extensions() { return CONTENT_TYPES.keySet().stream().map(extension -> "." + extension).toList(); }

    public static boolean matchesFormat(String extension, byte[] bytes) {
        return switch (extension) {
            case "pdf" -> starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "jpg", "jpeg" -> starts(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            case "png" -> starts(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "gif" -> starts(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII)) || starts(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "webp" -> bytes.length >= 12 && starts(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            case "doc", "xls" -> starts(bytes, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});
            case "docx", "xlsx" -> starts(bytes, new byte[]{0x50, 0x4b, 0x03, 0x04});
            case "txt", "csv" -> {
                boolean text = true;
                for (byte value : bytes) if (value == 0) { text = false; break; }
                yield text;
            }
            default -> false;
        };
    }
    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) if (bytes[index] != prefix[index]) return false;
        return true;
    }
}
