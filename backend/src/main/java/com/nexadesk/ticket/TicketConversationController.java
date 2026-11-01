package com.nexadesk.ticket;

import com.nexadesk.ticket.dto.TicketDtos.CommentDto;
import com.nexadesk.ticket.dto.TicketDtos.CommentRequest;
import com.nexadesk.ticket.dto.TicketDtos.FeedbackDto;
import com.nexadesk.ticket.dto.TicketDtos.FeedbackRequest;
import com.nexadesk.ticket.dto.TicketDtos.HistoryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets/{ticketId}")
@RequiredArgsConstructor
@Tag(name = "Tickets")
public class TicketConversationController {

    private final TicketConversationService service;

    @GetMapping("/comments")
    @Operation(summary = "Commentaires", description = "Les notes internes ne sont renvoyées qu'aux techniciens et admins.")
    public List<CommentDto> comments(@PathVariable Long ticketId) {
        return service.comments(ticketId);
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un commentaire",
            description = "internal=true réservé aux techniciens et admins. Une réponse du demandeur relance un "
                    + "ticket en attente de sa part.")
    public CommentDto addComment(@PathVariable Long ticketId, @Valid @RequestBody CommentRequest request) {
        return service.addComment(ticketId, request);
    }

    @GetMapping("/history")
    @Operation(summary = "Historique (timeline) du ticket")
    public List<HistoryDto> history(@PathVariable Long ticketId) {
        return service.history(ticketId);
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Évaluer le support (demandeur, une seule fois)")
    public FeedbackDto feedback(@PathVariable Long ticketId, @Valid @RequestBody FeedbackRequest request) {
        return service.giveFeedback(ticketId, request);
    }
}
