package com.nexadesk.ticket.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Decides the real type of an uploaded file from its content.
 *
 * <p>Detection is done on the bytes alone first. Only when the content is recognised as an Office (zip)
 * container is the file name used, to tell DOCX from XLSX. A binary renamed {@code .pdf} is therefore rejected:
 * relying on the name would let it through, because Tika falls back to the extension when no magic matches.
 */
@Component
public class AttachmentTypeDetector {

    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Types recognised from their own magic bytes, accepted as detected. */
    private static final Set<String> CONTENT_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");

    /** Office documents are zip containers: the name refines the container into a document type. */
    private static final Set<String> OFFICE_CONTAINERS = Set.of("application/zip", "application/x-tika-ooxml");
    private static final Set<String> OFFICE_TYPES = Set.of(DOCX, XLSX);

    /** Plain text (logs, notes) has no magic: accepted when the content looks like text. */
    private static final Map<String, String> TEXT_EXTENSIONS = Map.of(".log", "text/x-log", ".txt", "text/plain");

    private final Tika tika = new Tika();

    /** @return the accepted MIME type, or empty if this kind of file is not allowed */
    public Optional<String> detect(Path file, String fileName) throws IOException {
        String fromContent;
        try (InputStream in = Files.newInputStream(file)) {
            fromContent = tika.detect(in);
        }
        if (CONTENT_TYPES.contains(fromContent)) {
            return Optional.of(fromContent);
        }
        if (OFFICE_CONTAINERS.contains(fromContent)) {
            String refined;
            try (InputStream in = Files.newInputStream(file)) {
                refined = tika.detect(in, fileName);
            }
            return OFFICE_TYPES.contains(refined) ? Optional.of(refined) : Optional.empty();
        }
        if ("text/plain".equals(fromContent)) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            return Optional.of(TEXT_EXTENSIONS.entrySet().stream()
                    .filter(e -> lower.endsWith(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("text/plain"));
        }
        return Optional.empty();
    }
}
