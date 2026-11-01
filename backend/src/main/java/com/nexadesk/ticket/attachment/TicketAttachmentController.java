package com.nexadesk.ticket.attachment;

import com.nexadesk.ticket.dto.TicketDtos.AttachmentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tickets/{ticketId}/attachments")
@RequiredArgsConstructor
@Tag(name = "Tickets")
public class TicketAttachmentController {

    private final TicketAttachmentService service;

    @GetMapping
    @Operation(summary = "Pièces jointes du ticket")
    public List<AttachmentDto> list(@PathVariable Long ticketId) {
        return service.list(ticketId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Joindre un fichier",
            description = "10 Mo maximum, 5 par ticket. Type vérifié sur le contenu : PNG, JPG, PDF, TXT, LOG, DOCX, XLSX.")
    public AttachmentDto upload(@PathVariable Long ticketId, @RequestParam("file") MultipartFile file) {
        return service.upload(ticketId, file);
    }

    /** Always downloaded as an attachment, never rendered inline by the browser. */
    @GetMapping("/{attachmentId}")
    @Operation(summary = "Télécharger une pièce jointe")
    public ResponseEntity<Resource> download(@PathVariable Long ticketId, @PathVariable Long attachmentId) {
        TicketAttachmentService.Download download = service.download(ticketId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(download.resource());
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une pièce jointe")
    public void delete(@PathVariable Long ticketId, @PathVariable Long attachmentId) {
        service.delete(ticketId, attachmentId);
    }
}
