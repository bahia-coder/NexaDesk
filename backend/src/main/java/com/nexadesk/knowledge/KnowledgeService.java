package com.nexadesk.knowledge;

import com.nexadesk.audit.AuditAction;
import com.nexadesk.audit.AuditService;
import com.nexadesk.category.CategoryMapper;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.common.web.PageResponse;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleAction;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleDto;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleFilter;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleRequest;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleSort;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleSummaryDto;
import com.nexadesk.knowledge.dto.KnowledgeDtos.HelpfulDto;
import com.nexadesk.knowledge.dto.KnowledgeDtos.SourceTicketDto;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketAccess;
import com.nexadesk.ticket.TicketMapper;
import com.nexadesk.ticket.TicketStatus;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Knowledge base: articles written by technicians (often from a solved ticket), published by an admin, found
 * through PostgreSQL full-text search, and linked to the tickets they helped solve. Also the retrieval step used
 * by the AI module ({@link #related}).
 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final String AUDITED = "ARTICLE";
    private static final int SUMMARY_FROM_TICKET = 300;

    private final KnowledgeArticleRepository repository;
    private final KnowledgeSearch search;
    private final TicketCategoryRepository categoryRepository;
    private final TicketAccess access;
    private final TicketMapper ticketMapper;
    private final CategoryMapper categoryMapper;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Clock clock;

    /** An article found by {@link #related}, with its full text (the AI module quotes it). */
    public record RelatedArticle(ArticleSummaryDto article, String content, double score) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleSummaryDto> list(ArticleFilter filter, Pageable pageable) {
        User actor = access.actor();
        String text = filter.search() == null ? "" : filter.search().trim();
        ArticleSort sort = filter.sort() == null || (filter.sort() == ArticleSort.RELEVANCE && text.isEmpty())
                ? (text.isEmpty() ? ArticleSort.RECENT : ArticleSort.RELEVANCE)
                : filter.sort();
        ArticleStatus status = actor.getRoleCode() == RoleCode.EMPLOYEE ? null : filter.status();
        KnowledgeSearch.Page page = search.search(actor, text, status, filter.categoryId(), sort,
                pageable.getPageNumber(), pageable.getPageSize());
        List<ArticleSummaryDto> content = load(page.ids()).stream().map(this::toSummary).toList();
        int totalPages = (int) ((page.total() + pageable.getPageSize() - 1) / pageable.getPageSize());
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), page.total(),
                totalPages);
    }

    /** Reading a published article counts as a view (drafts and archives are not counted). */
    @Transactional
    public ArticleDto get(Long id) {
        User actor = access.actor();
        KnowledgeArticle article = visible(id, actor);
        int views = article.getViewCount();
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            repository.incrementViews(id);
            views++;
        }
        return toDto(article, actor, views);
    }

    @Transactional
    public ArticleDto create(ArticleRequest request) {
        User actor = access.actor();
        KnowledgeArticle article = new KnowledgeArticle(request.title().trim(), request.summary().trim(),
                request.content().trim(), activeCategory(request.categoryId()), actor, null);
        repository.saveAndFlush(article);
        audit.record(AuditAction.ARTICLE_CREATED, AUDITED, String.valueOf(article.getId()),
                "Brouillon « " + article.getTitle() + " »", null, snapshot(article));
        return toDto(article, actor, 0);
    }

    @Transactional
    public ArticleDto update(Long id, ArticleRequest request) {
        User actor = access.actor();
        KnowledgeArticle article = visible(id, actor);
        require(article, actor, ArticleAction.EDIT, "Vous ne pouvez modifier que vos brouillons.");
        if (request.version() == null || article.getVersion() != request.version()) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        Map<String, Object> before = snapshot(article);
        article.setTitle(request.title().trim());
        article.setSummary(request.summary().trim());
        article.setContent(request.content().trim());
        if (!article.getCategory().getId().equals(request.categoryId())) {
            article.setCategory(activeCategory(request.categoryId()));
        }
        repository.flush();
        audit.record(AuditAction.ARTICLE_UPDATED, AUDITED, String.valueOf(id),
                "Modification de « " + article.getTitle() + " »", before, snapshot(article));
        return toDto(article, actor, article.getViewCount());
    }

    @Transactional
    public ArticleDto publish(Long id) {
        User actor = access.actor();
        KnowledgeArticle article = visible(id, actor);
        require(article, actor, ArticleAction.PUBLISH, "Cet article est déjà publié.");
        ArticleStatus before = article.getStatus();
        article.publish(clock.instant());
        repository.flush();
        audit.record(AuditAction.ARTICLE_PUBLISHED, AUDITED, String.valueOf(id),
                "Publication de « " + article.getTitle() + " »",
                AuditService.values("status", before), AuditService.values("status", article.getStatus()));
        return toDto(article, actor, article.getViewCount());
    }

    @Transactional
    public ArticleDto archive(Long id) {
        User actor = access.actor();
        KnowledgeArticle article = visible(id, actor);
        require(article, actor, ArticleAction.ARCHIVE, "Cet article est déjà archivé.");
        ArticleStatus before = article.getStatus();
        article.archive();
        repository.flush();
        audit.record(AuditAction.ARTICLE_ARCHIVED, AUDITED, String.valueOf(id),
                "Archivage de « " + article.getTitle() + " »",
                AuditService.values("status", before), AuditService.values("status", article.getStatus()));
        return toDto(article, actor, article.getViewCount());
    }

    /** "This article helped me": counted once per user (primary key of the vote table), idempotent. */
    @Transactional
    public HelpfulDto markHelpful(Long id) {
        User actor = access.actor();
        KnowledgeArticle article = visible(id, actor);
        require(article, actor, ArticleAction.MARK_HELPFUL, "Seul un article publié peut être recommandé.");
        int inserted = jdbc.sql("""
                        insert into knowledge_article_votes (article_id, user_id, created_at) values (:article, :user, :now)
                        on conflict do nothing""")
                .param("article", id).param("user", actor.getId())
                .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
        if (inserted == 1) {
            jdbc.sql("update knowledge_articles set helpful_count = helpful_count + 1 where id = :id")
                    .param("id", id).update();
        }
        return new HelpfulDto(article.getHelpfulCount() + inserted, true);
    }

    /**
     * Drafts an article from a solved ticket: its description becomes the symptoms, its resolution the solution.
     * The ticket is linked to the new article. Reserved to the ticket's technician and to admins.
     */
    @Transactional
    public ArticleDto fromTicket(Long ticketId) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(ticketId, actor);
        if (actor.getRoleCode() != RoleCode.ADMIN && !ticket.isAssignedTo(actor.getId())) {
            throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED,
                    "Seul le technicien du ticket ou un administrateur peut en tirer un article.");
        }
        boolean solved = ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED;
        if (!solved || ticket.getResolution() == null || ticket.getResolution().isBlank()) {
            throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED,
                    "Un article ne peut être rédigé qu'à partir d'un ticket résolu, avec sa solution.");
        }
        if (repository.existsBySourceTicket_Id(ticketId)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_ARTICLE_EXISTS);
        }
        String content = "## Symptômes\n\n" + ticket.getDescription().trim()
                + "\n\n## Solution\n\n" + ticket.getResolution().trim();
        KnowledgeArticle article = new KnowledgeArticle(ticket.getTitle(),
                abbreviate(ticket.getDescription().trim(), SUMMARY_FROM_TICKET), content, ticket.getCategory(), actor,
                ticket);
        repository.saveAndFlush(article);
        insertLinks(ticketId, List.of(article.getId()), actor);
        audit.record(AuditAction.ARTICLE_CREATED, AUDITED, String.valueOf(article.getId()),
                "Brouillon « " + article.getTitle() + " » rédigé à partir de " + ticket.getReference(),
                null, snapshot(article));
        return toDto(article, actor, 0);
    }

    // ---- Ticket links ------------------------------------------------------------------------------------

    /** Articles linked to a ticket the user can see; the requester only sees the published ones. */
    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> ticketArticles(Long ticketId) {
        User actor = access.actor();
        access.visibleTicket(ticketId, actor);
        List<Long> ids = jdbc.sql("""
                        select l.article_id from ticket_knowledge_links l
                        join knowledge_articles a on a.id = l.article_id
                        where l.ticket_id = :ticket and (:staff or a.status = 'PUBLISHED')
                        order by l.created_at, l.article_id""")
                .param("ticket", ticketId).param("staff", TicketAccess.isStaff(actor))
                .query(Long.class).list();
        return load(ids).stream().map(this::toSummary).toList();
    }

    /** Replaces the articles linked to a ticket (published articles only). Ticket's technician or admin. */
    @Transactional
    public List<ArticleSummaryDto> replaceTicketArticles(Long ticketId, List<Long> articleIds) {
        User actor = access.actor();
        Ticket ticket = access.visibleTicket(ticketId, actor);
        if (actor.getRoleCode() != RoleCode.ADMIN && !ticket.isAssignedTo(actor.getId())) {
            throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED,
                    "Seul le technicien du ticket ou un administrateur peut lier des articles.");
        }
        List<Long> wanted = List.copyOf(new LinkedHashSet<>(articleIds));
        List<KnowledgeArticle> articles = repository.findAllByIdIn(wanted);
        if (articles.size() != wanted.size()
                || articles.stream().anyMatch(a -> a.getStatus() != ArticleStatus.PUBLISHED)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Seuls des articles publiés peuvent être liés.");
        }
        jdbc.sql("delete from ticket_knowledge_links where ticket_id = :ticket"
                        + (wanted.isEmpty() ? "" : " and article_id not in (:ids)"))
                .param("ticket", ticketId).param("ids", wanted.isEmpty() ? List.of(-1L) : wanted)
                .update();
        insertLinks(ticketId, wanted, actor);
        return ticketArticles(ticketId);
    }

    // ---- Retrieval for the AI module --------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RelatedArticle> related(String text, String title, int limit) {
        List<KnowledgeSearch.Match> matches = search.related(text, title, limit);
        if (matches.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeArticle> byId = repository.findAllByIdIn(matches.stream().map(KnowledgeSearch.Match::id)
                .toList()).stream().collect(Collectors.toMap(KnowledgeArticle::getId, Function.identity()));
        return matches.stream()
                .filter(match -> byId.containsKey(match.id()))
                .map(match -> {
                    KnowledgeArticle article = byId.get(match.id());
                    return new RelatedArticle(toSummary(article), article.getContent(), match.score());
                })
                .toList();
    }

    // ---- Helpers -----------------------------------------------------------------------------------------

    private void insertLinks(Long ticketId, Collection<Long> articleIds, User actor) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (Long articleId : articleIds) {
            jdbc.sql("""
                            insert into ticket_knowledge_links (ticket_id, article_id, linked_by, created_at)
                            values (:ticket, :article, :user, :now) on conflict do nothing""")
                    .param("ticket", ticketId).param("article", articleId).param("user", actor.getId())
                    .param("now", now).update();
        }
    }

    private KnowledgeArticle visible(Long id, User actor) {
        return repository.findWithDetailsById(id)
                .filter(article -> KnowledgePolicy.canView(article, actor))
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));
    }

    private static void require(KnowledgeArticle article, User actor, ArticleAction action, String message) {
        if (!KnowledgePolicy.availableActions(article, actor).contains(action)) {
            throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED, message);
        }
    }

    private TicketCategory activeCategory(Long id) {
        TicketCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));
        if (!category.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_REFERENCE);
        }
        return category;
    }

    /** Loads entities for ids coming from SQL, keeping the SQL order. */
    private List<KnowledgeArticle> load(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeArticle> byId = repository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(KnowledgeArticle::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private ArticleSummaryDto toSummary(KnowledgeArticle a) {
        return new ArticleSummaryDto(a.getId(), a.getTitle(), a.getSummary(), a.getStatus(),
                categoryMapper.toRef(a.getCategory()), ticketMapper.user(a.getAuthor()), a.getViewCount(),
                a.getHelpfulCount(), a.getPublishedAt(), a.getUpdatedAt());
    }

    private ArticleDto toDto(KnowledgeArticle a, User viewer, int views) {
        long linked = jdbc.sql("select count(*) from ticket_knowledge_links where article_id = :id")
                .param("id", a.getId()).query(Long.class).single();
        boolean helped = jdbc.sql("""
                        select exists (select 1 from knowledge_article_votes where article_id = :id and user_id = :user)""")
                .param("id", a.getId()).param("user", viewer.getId()).query(Boolean.class).single();
        SourceTicketDto source = a.getSourceTicket() == null || !TicketAccess.isStaff(viewer) ? null
                : new SourceTicketDto(a.getSourceTicket().getId(), a.getSourceTicket().getReference());
        return new ArticleDto(a.getId(), a.getTitle(), a.getSummary(), a.getContent(), a.getStatus(),
                categoryMapper.toRef(a.getCategory()), ticketMapper.user(a.getAuthor()), source, views,
                a.getHelpfulCount(), linked, helped, a.getPublishedAt(), a.getCreatedAt(), a.getUpdatedAt(),
                a.getVersion(), KnowledgePolicy.availableActions(a, viewer));
    }

    private static Map<String, Object> snapshot(KnowledgeArticle a) {
        return AuditService.values("title", a.getTitle(), "category", a.getCategory().getName(),
                "status", a.getStatus(), "summary", a.getSummary());
    }

    static String abbreviate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int cut = text.lastIndexOf(' ', max - 1);
        return text.substring(0, cut > max / 2 ? cut : max - 1).stripTrailing() + "…";
    }
}
