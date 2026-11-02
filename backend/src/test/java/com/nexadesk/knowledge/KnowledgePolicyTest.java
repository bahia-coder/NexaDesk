package com.nexadesk.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexadesk.category.TicketCategory;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleAction;
import com.nexadesk.support.TestUsers;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KnowledgePolicyTest {

    private final User author = TestUsers.user(1, "author@nexadesk.test", RoleCode.TECHNICIAN, "x");
    private final User otherTechnician = TestUsers.user(2, "other@nexadesk.test", RoleCode.TECHNICIAN, "x");
    private final User employee = TestUsers.user(3, "emp@nexadesk.test", RoleCode.EMPLOYEE, "x");
    private final User admin = TestUsers.user(4, "admin@nexadesk.test", RoleCode.ADMIN, "x");

    private KnowledgeArticle article(ArticleStatus status) {
        KnowledgeArticle article = new KnowledgeArticle("Titre d'article", "Résumé", "Contenu de l'article",
                new TicketCategory("Réseau", null, "wifi", null), author, null);
        if (status != ArticleStatus.DRAFT) {
            article.publish(Instant.parse("2026-01-01T10:00:00Z"));
        }
        if (status == ArticleStatus.ARCHIVED) {
            article.archive();
        }
        return article;
    }

    @Test
    void draftsAreVisibleToTheirAuthorAndAdminsOnly() {
        KnowledgeArticle draft = article(ArticleStatus.DRAFT);

        assertThat(KnowledgePolicy.canView(draft, author)).isTrue();
        assertThat(KnowledgePolicy.canView(draft, admin)).isTrue();
        assertThat(KnowledgePolicy.canView(draft, otherTechnician)).isFalse();
        assertThat(KnowledgePolicy.canView(draft, employee)).isFalse();
        assertThat(KnowledgePolicy.availableActions(draft, author)).containsExactly(ArticleAction.EDIT);
        assertThat(KnowledgePolicy.availableActions(draft, admin))
                .containsExactlyInAnyOrder(ArticleAction.EDIT, ArticleAction.PUBLISH, ArticleAction.ARCHIVE);
    }

    @Test
    void publishedArticlesAreReadByAllButOnlyAdminsChangeThem() {
        KnowledgeArticle published = article(ArticleStatus.PUBLISHED);

        assertThat(KnowledgePolicy.availableActions(published, employee)).containsExactly(ArticleAction.MARK_HELPFUL);
        assertThat(KnowledgePolicy.availableActions(published, author)).containsExactly(ArticleAction.MARK_HELPFUL);
        assertThat(KnowledgePolicy.availableActions(published, admin))
                .containsExactlyInAnyOrder(ArticleAction.EDIT, ArticleAction.ARCHIVE, ArticleAction.MARK_HELPFUL);
    }

    @Test
    void archivedArticlesAreForAdminsAndKeepTheirFirstPublicationDate() {
        KnowledgeArticle archived = article(ArticleStatus.ARCHIVED);
        Instant firstPublication = archived.getPublishedAt();

        assertThat(KnowledgePolicy.canView(archived, employee)).isFalse();
        assertThat(KnowledgePolicy.canView(archived, author)).isFalse();
        assertThat(KnowledgePolicy.availableActions(archived, admin))
                .containsExactlyInAnyOrder(ArticleAction.EDIT, ArticleAction.PUBLISH);

        archived.publish(Instant.parse("2026-06-01T10:00:00Z"));
        assertThat(archived.getPublishedAt()).isEqualTo(firstPublication);
    }

    @Test
    void summariesFromTicketsAreCutOnAWord() {
        assertThat(KnowledgeService.abbreviate("court", 300)).isEqualTo("court");
        String cut = KnowledgeService.abbreviate("un deux trois quatre cinq six", 15);
        assertThat(cut).isEqualTo("un deux trois…");
    }
}
