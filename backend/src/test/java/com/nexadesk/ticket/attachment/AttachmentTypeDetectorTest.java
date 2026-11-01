package com.nexadesk.ticket.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexadesk.support.TestFiles;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachmentTypeDetectorTest {

    private static final byte[] PNG = TestFiles.PNG;
    private static final byte[] EXE = TestFiles.EXE;

    private final AttachmentTypeDetector detector = new AttachmentTypeDetector();

    @TempDir
    Path dir;

    private Optional<String> detect(byte[] content, String name) throws IOException {
        Path file = Files.write(dir.resolve("upload.bin"), content);
        return detector.detect(file, name);
    }

    private static byte[] officeZip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void acceptsImagesAndPdfFromTheirMagicBytes() throws IOException {
        assertThat(detect(PNG, "capture.png")).contains("image/png");
        assertThat(detect(PNG, "capture-renommee.pdf")).as("content wins over the name").contains("image/png");
        assertThat(detect("%PDF-1.7\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1), "doc.pdf"))
                .contains("application/pdf");
    }

    @Test
    void acceptsPlainTextAndLogs() throws IOException {
        byte[] log = "2026-11-02 10:00:01 ERROR Connection refused\n".getBytes(StandardCharsets.UTF_8);
        assertThat(detect(log, "vpn.log")).contains("text/x-log");
        assertThat(detect(log, "notes.txt")).contains("text/plain");
    }

    @Test
    void acceptsOfficeDocumentsUsingTheNameOnlyToRefineAZipContainer() throws IOException {
        byte[] zip = officeZip();
        assertThat(detect(zip, "rapport.docx")).contains(AttachmentTypeDetector.DOCX);
        assertThat(detect(zip, "budget.xlsx")).contains(AttachmentTypeDetector.XLSX);
        assertThat(detect(zip, "archive.zip")).isEmpty();
    }

    @Test
    void rejectsExecutablesAndDisguisedFiles() throws IOException {
        assertThat(detect(EXE, "setup.exe")).isEmpty();
        assertThat(detect(EXE, "facture.pdf"))
                .as("executable renamed .pdf").isEmpty();
        byte[] binary = {0x00, 0x01, (byte) 0xFE, (byte) 0xFF, 0x13, 0x37, 0x00, 0x42};
        assertThat(detect(binary, "facture.pdf")).as("unknown binary renamed .pdf").isEmpty();
        assertThat(detect("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8), "page.txt"))
                .as("HTML renamed .txt").isEmpty();
    }

    @Test
    void fileNamesAreReducedToASafeDisplayName() {
        assertThat(TicketAttachmentService.sanitizeFileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(TicketAttachmentService.sanitizeFileName("C:\\Users\\sara\\capture écran.png"))
                .isEqualTo("capture écran.png");
        assertThat(TicketAttachmentService.sanitizeFileName("a\u0000b\"c.txt")).isEqualTo("abc.txt");
        assertThat(TicketAttachmentService.sanitizeFileName("..")).isEqualTo("fichier");
        assertThat(TicketAttachmentService.sanitizeFileName(null)).isEqualTo("fichier");
    }
}
