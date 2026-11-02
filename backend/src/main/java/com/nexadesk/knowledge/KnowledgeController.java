package com.nexadesk.knowledge;

import com.nexadesk.common.web.PageResponse;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleDto;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleFilter;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleRequest;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleSummaryDto;
import com.nexadesk.knowledge.dto.KnowledgeDtos.HelpfulDto;
import com.nexadesk.knowledge.dto.KnowledgeDtos.TicketLinksRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Base de connaissances", description = "Articles, recherche plein texte et liens avec les tickets")
public class KnowledgeController {

    private final KnowledgeService service;

    @GetMapping("/knowledge")
    @Operation(summary = "Rechercher des articles",
            description = "Recherche plein texte (français) sur titre, résumé et contenu ; filtres categoryId, "
                    + "status (technicien : DRAFT = ses brouillons ; admin : tous) ; tri RELEVANCE, RECENT, "
                    + "POPULAR. Un employé ne voit que les articles publiés. Pagination : page, size.")
    public PageResponse<ArticleSummaryDto> list(@ParameterObject ArticleFilter filter,
                                                @ParameterObject Pageable pageable) {
        return service.list(filter, pageable);
    }

    @GetMapping("/knowledge/{id}")
    @Operation(summary = "Lire un article", description = "Compte une vue si l'article est publié.")
    public ArticleDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/knowledge")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Rédiger un brouillon")
    public ResponseEntity<ArticleDto> create(@Valid @RequestBody ArticleRequest request) {
        ArticleDto created = service.create(request);
        return ResponseEntity.created(URI.create("/api/knowledge/" + created.id())).body(created);
    }

    @PutMapping("/knowledge/{id}")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Modifier un article", description = "Technicien : ses brouillons. Admin : tout article.")
    public ArticleDto update(@PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/knowledge/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publier un article")
    public ArticleDto publish(@PathVariable Long id) {
        return service.publish(id);
    }

    @PostMapping("/knowledge/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archiver un article", description = "Il disparaît de la recherche et des suggestions.")
    public ArticleDto archive(@PathVariable Long id) {
        return service.archive(id);
    }

    @PostMapping("/knowledge/{id}/helpful")
    @Operation(summary = "« Cet article m'a aidé »", description = "Compté une seule fois par utilisateur.")
    public HelpfulDto helpful(@PathVariable Long id) {
        return service.markHelpful(id);
    }

    @PostMapping("/knowledge/from-ticket/{ticketId}")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Rédiger un brouillon à partir d'un ticket résolu",
            description = "Description → symptômes, résolution → solution. Le ticket est lié à l'article.")
    public ResponseEntity<ArticleDto> fromTicket(@PathVariable Long ticketId) {
        ArticleDto created = service.fromTicket(ticketId);
        return ResponseEntity.created(URI.create("/api/knowledge/" + created.id())).body(created);
    }

    @GetMapping("/tickets/{ticketId}/knowledge-links")
    @Operation(summary = "Articles liés à un ticket")
    public List<ArticleSummaryDto> ticketArticles(@PathVariable Long ticketId) {
        return service.ticketArticles(ticketId);
    }

    @PutMapping("/tickets/{ticketId}/knowledge-links")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Lier des articles à un ticket", description = "Remplace la liste (articles publiés).")
    public List<ArticleSummaryDto> replaceTicketArticles(@PathVariable Long ticketId,
                                                         @Valid @RequestBody TicketLinksRequest request) {
        return service.replaceTicketArticles(ticketId, request.articleIds());
    }
}
