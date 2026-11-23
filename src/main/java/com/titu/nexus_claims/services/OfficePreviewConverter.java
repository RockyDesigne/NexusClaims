package com.titu.nexus_claims.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Converts a private temporary copy locally, using an isolated macro-disabled Office profile. */
@Component
public class OfficePreviewConverter {
    private final String configuredCommand;
    public OfficePreviewConverter(@Value("${claims.documents.preview.office-command:}") String command) { configuredCommand = command; }

    public Optional<String> command() {
        if (!configuredCommand.isBlank()) return executable(configuredCommand);
        for (String candidate : List.of("/Applications/LibreOffice.app/Contents/MacOS/soffice",
                System.getProperty("user.home") + "/Applications/LibreOffice.app/Contents/MacOS/soffice", "soffice", "libreoffice")) {
            var resolved = executable(candidate);
            if (resolved.isPresent()) return resolved;
        }
        return Optional.empty();
    }
    private Optional<String> executable(String candidate) {
        Path path = Path.of(candidate);
        if (path.isAbsolute()) return Files.isExecutable(path) ? Optional.of(path.toString()) : Optional.empty();
        for (String directory : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            Path resolved = Path.of(directory).resolve(candidate);
            if (Files.isExecutable(resolved)) return Optional.of(resolved.toAbsolutePath().toString());
        }
        return Optional.empty();
    }

    public byte[] firstPagePdf(byte[] bytes, String extension) throws IOException, InterruptedException {
        String binary = command().orElseThrow(() -> new UnavailableException("LibreOffice is not configured."));
        if (!Set.of("doc", "docx", "xls", "xlsx", "txt", "csv").contains(extension)) throw new IOException("Unsupported preview format.");
        Path temp = Files.createTempDirectory("nexus-document-preview-");
        Process process = null;
        try {
            Path profile = Files.createDirectories(temp.resolve("profile/user"));
            Files.writeString(profile.resolve("registrymodifications.xcu"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <oor:items xmlns:oor="http://openoffice.org/2001/registry">
                      <item oor:path="/org.openoffice.Office.Common/Security/Scripting">
                        <prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop>
                      </item>
                    </oor:items>
                    """);
            Path input = temp.resolve("input." + extension);
            Files.write(input, bytes);
            Path output = Files.createDirectory(temp.resolve("output"));
            String filter = Set.of("xls", "xlsx", "csv").contains(extension) ? "calc_pdf_Export" : "writer_pdf_Export";
            String export = "pdf:" + filter + ":{\"PageRange\":{\"type\":\"string\",\"value\":\"1\"}}";
            var builder = processBuilder(List.of(binary, "-env:UserInstallation=" + temp.resolve("profile").toUri(),
                    "--headless", "--nologo", "--nodefault", "--norestore", "--nolockcheck",
                    "--convert-to", export, "--outdir", output.toString(), input.toString()))
                    .redirectErrorStream(true).redirectOutput(temp.resolve("conversion.log").toFile());
            builder.environment().put("SAL_USE_VCLPLUGIN", "svp");
            process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(Duration.ofSeconds(40).toMillis(), TimeUnit.MILLISECONDS)) throw new IOException("Document conversion timed out.");
            Path pdf = output.resolve("input.pdf");
            if (process.exitValue() != 0 || !Files.isRegularFile(pdf) || Files.size(pdf) == 0 || Files.size(pdf) > 20 * 1024 * 1024)
                throw new IOException("Document could not be converted to a first-page preview.");
            return Files.readAllBytes(pdf);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            FileSystemUtils.deleteRecursively(temp);
        }
    }
    static ProcessBuilder processBuilder(List<String> arguments) {
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) {
            // macOS JVMs raise the descriptor limit dramatically. LibreOffice scans that
            // limit during startup; bound it for this child only. Arguments are forwarded
            // positionally, never interpolated into shell code.
            command.addAll(List.of("/bin/sh", "-c", "ulimit -n 4096; exec \"$@\"", "nexus-office-preview"));
        }
        command.addAll(arguments);
        return new ProcessBuilder(command);
    }

    public static class UnavailableException extends IOException {
        public UnavailableException(String message) { super(message); }
    }
}
