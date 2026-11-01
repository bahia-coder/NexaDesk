package com.nexadesk.ticket.attachment;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketAccess;
import com.nexadesk.ticket.TicketAction;
import com.nexadesk.ticket.TicketMapper;
import com.nexadesk.ticket.TicketPolicy;
import com.nexadesk.ticket.TicketProperties;
import com.nexadesk.ticket.TicketStatus;
import com.nexadesk.ticket.dto.TicketDtos.AttachmentDto;
import com.nexadesk.ticket.history.HistoryAction;
import com.nexadesk.ticket.history.TicketHistoryRecorder;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ticket attachments. Files are only reachable through {@link #download}, after the same visibility check as
 * the ticket itself — never served as static content.
 */
@Service
@RequiredArgsConstructor
public class TicketAttachmentService {

    public record Download(Resource resource, String fileName, String contentType, long sizeBytes) {
    }

    private static final int MAX_NAME_LENGTH = 255;

    private final TicketAccess access;
    private final TicketPolicy policy;
    private final TicketAttachmentRepository repository;
    private final FileStorageService storage;
    private final AttachmentTypeDetector typeDetector;
    private final TicketHistoryRecorder history;
    private final TicketProperties properties;
    private final TicketMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AttachmentDto> list(Long ticketId) {
        User actor = access.actor();
        access.visibleTicket(ticketId, actor);
        return repository.findByTicket_IdOrderByCreatedAtAsc(ticketId).stream().map(mapper::toDto).toList();
    }

    @Transactional
    public AttachmentDto upload(Long ticketId, MultipartFile file) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(ticketId, actor);
        Instant now = clock.instant();
        policy.require(TicketAction.ADD_ATTACHMENT, ticket, actor, now);
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Le fichier est vide.");
        }
        if (repository.countByTicket_Id(ticketId) >= properties.maxAttachmentsPerTicket()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_LIMIT,
                    "Un ticket peut avoir au plus " + properties.maxAttachmentsPerTicket() + " pièces jointes.");
        }

        String name = sanitizeFileName(file.getOriginalFilename());
        String key = "tickets/" + ticketId + "/" + UUID.randomUUID();
        FileStorageService.StoredFile stored = storeAndDeleteOnRollback(key, file);
        String contentType;
        try {
            contentType = typeDetector.detect(storage.path(key), name)
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        TicketAttachment attachment = repository.save(TicketAttachment.builder()
                .ticket(ticket)
                .uploadedBy(actor)
                .originalName(name)
                .storageKey(key)
                .contentType(contentType)
                .sizeBytes(stored.sizeBytes())
                .sha256(stored.sha256())
                .createdAt(now)
                .build());
        history.change(ticket, actor, HistoryAction.ATTACHMENT_ADDED, "attachment", null, name, null);
        return mapper.toDto(attachment);
    }

    @Transactional(readOnly = true)
    public Download download(Long ticketId, Long attachmentId) {
        User actor = access.actor();
        access.visibleTicket(ticketId, actor);
        TicketAttachment attachment = find(ticketId, attachmentId);
        return new Download(storage.load(attachment.getStorageKey()), attachment.getOriginalName(),
                attachment.getContentType(), attachment.getSizeBytes());
    }

    /** An admin may remove any attachment of an open ticket; an uploader only theirs while the ticket is NEW. */
    @Transactional
    public void delete(Long ticketId, Long attachmentId) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(ticketId, actor);
        TicketAttachment attachment = find(ticketId, attachmentId);
        boolean admin = actor.getRoleCode() == RoleCode.ADMIN && !ticket.getStatus().isTerminal();
        boolean ownWhileNew = attachment.getUploadedBy().getId().equals(actor.getId())
                && ticket.getStatus() == TicketStatus.NEW;
        if (!admin && !ownWhileNew) {
            throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED,
                    "Vous ne pouvez pas supprimer cette pièce jointe.");
        }
        repository.delete(attachment);
        history.change(ticket, actor, HistoryAction.ATTACHMENT_REMOVED, "attachment", attachment.getOriginalName(),
                null, null);
        String key = attachment.getStorageKey();
        afterCompletion(committed -> {
            if (committed) {
                storage.delete(key);
            }
        });
    }

    /** The file is written before the row; if the transaction then fails, the file is removed. */
    private FileStorageService.StoredFile storeAndDeleteOnRollback(String key, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            FileStorageService.StoredFile stored = storage.store(key, in);
            afterCompletion(committed -> {
                if (!committed) {
                    storage.delete(key);
                }
            });
            return stored;
        } catch (IOException e) {
            storage.delete(key);
            throw new UncheckedIOException(e);
        }
    }

    private static void afterCompletion(Consumer<Boolean> callback) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                callback.accept(status == STATUS_COMMITTED);
            }
        });
    }

    private TicketAttachment find(Long ticketId, Long attachmentId) {
        return repository.findByIdAndTicket_Id(attachmentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Pièce jointe", attachmentId));
    }

    /** Keeps a display name only: no directories, no control characters, bounded length. */
    static String sanitizeFileName(String original) {
        if (original == null) {
            return "fichier";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "fichier";
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(name.length() - MAX_NAME_LENGTH);
    }
}
