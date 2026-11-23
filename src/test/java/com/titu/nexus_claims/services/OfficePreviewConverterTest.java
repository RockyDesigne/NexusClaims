package com.titu.nexus_claims.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OfficePreviewConverterTest {
    @Test void rendersAllSixOfficeAndTextFormatsLocally() throws Exception {
        var converter = new OfficePreviewConverter("");
        assumeTrue(converter.command().isPresent(), "LibreOffice must be installed for native Office integration tests.");
        Path temp = Files.createTempDirectory("nexus-office-fixtures-");
        try {
            try (var word = new XWPFDocument(); var output = Files.newOutputStream(temp.resolve("sample.docx"))) {
                word.createParagraph().createRun().setText("FIRST PAGE - fictional claim document");
                var second = word.createParagraph(); second.setPageBreak(true); second.createRun().setText("SECOND PAGE - must not appear in preview");
                word.write(output);
            }
            try (var excel = new XSSFWorkbook(); var output = Files.newOutputStream(temp.resolve("sample.xlsx"))) {
                var sheet = excel.createSheet("Fictional valuation");
                sheet.createRow(0).createCell(0).setCellValue("FIRST PAGE - fictional estimate");
                sheet.createRow(1).createCell(0).setCellValue("Repair estimate 4500 RON");
                excel.write(output);
            }
            Files.writeString(temp.resolve("sample.txt"), "FIRST PAGE - fictional statement\nNo real claim data.\n");
            Files.writeString(temp.resolve("sample.csv"), "Item,Amount,Currency\nFictional repair,4500,RON\n");
            for (String[] conversion : new String[][]{{"docx", "doc:MS Word 97"}, {"xlsx", "xls:MS Excel 97"}}) {
                Path target = Files.createDirectory(temp.resolve("legacy-" + conversion[0]));
                var builder = OfficePreviewConverter.processBuilder(List.of(converter.command().orElseThrow(), "-env:UserInstallation=" + temp.resolve("fixture-profile-" + conversion[0]).toUri(),
                        "--headless", "--nologo", "--nodefault", "--norestore", "--nolockcheck", "--convert-to", conversion[1], "--outdir", target.toString(), temp.resolve("sample." + conversion[0]).toString()))
                        .redirectErrorStream(true).redirectOutput(temp.resolve("fixture-" + conversion[0] + ".log").toFile());
                builder.environment().put("SAL_USE_VCLPLUGIN", "svp");
                var process = builder.start();
            process.getOutputStream().close();
                if (!process.waitFor(40, TimeUnit.SECONDS)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();
                    throw new AssertionError("Fixture conversion timed out: " + conversion[0] + " " + Files.readString(temp.resolve("fixture-" + conversion[0] + ".log")));
                }
                Files.copy(target.resolve("sample." + conversion[1].split(":")[0]), temp.resolve("sample." + conversion[1].split(":")[0]));
            }
            for (String extension : List.of("doc", "docx", "xls", "xlsx", "txt", "csv")) {
                byte[] original = Files.readAllBytes(temp.resolve("sample." + extension));
                byte[] page = converter.firstPagePdf(original, extension);
                try (var pdf = Loader.loadPDF(page)) {
                    assertThat(pdf.getNumberOfPages()).as(extension).isEqualTo(1);
                    assertThat(new PDFTextStripper().getText(pdf)).as(extension).isNotBlank();
                    if (extension.startsWith("doc")) {
                        assertThat(new PDFTextStripper().getText(pdf)).contains("FIRST PAGE").doesNotContain("SECOND PAGE");
                    }
                }
                assertThat(Files.readAllBytes(temp.resolve("sample." + extension))).isEqualTo(original);
            }
        } finally { org.springframework.util.FileSystemUtils.deleteRecursively(temp); }
    }
}
