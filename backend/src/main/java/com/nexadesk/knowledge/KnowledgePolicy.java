package com.nexadesk.knowledge;

import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleAction;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import java.util.EnumSet;
import java.util.Set;

/**
 * Who may read and change knowledge articles. Pure logic, mirrored by the SQL visibility of
 * {@link KnowledgeSearch}: employees read published articles; technicians also read and edit their own drafts;
 * admins read everything, edit any article, publish and archive.
 */
public final class KnowledgePolicy {

    private KnowledgePolicy() {
    }

    public static boolean canView(KnowledgeArticle article, User user) {
        return isAdmin(user) || article.getStatus() == ArticleStatus.PUBLISHED || isOwnDraft(article, user);
    }

    public static Set<ArticleAction> availableActions(KnowledgeArticle article, User user) {
        Set<ArticleAction> actions = EnumSet.noneOf(ArticleAction.class);
        if (!canView(article, user)) {
            return actions;
        }
        boolean admin = isAdmin(user);
        if (admin || isOwnDraft(article, user)) {
            actions.add(ArticleAction.EDIT);
        }
        if (admin && article.getStatus() != ArticleStatus.PUBLISHED) {
            actions.add(ArticleAction.PUBLISH);
        }
        if (admin && article.getStatus() != ArticleStatus.ARCHIVED) {
            actions.add(ArticleAction.ARCHIVE);
        }
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            actions.add(ArticleAction.MARK_HELPFUL);
        }
        return actions;
    }

    private static boolean isOwnDraft(KnowledgeArticle article, User user) {
        return user.getRoleCode() == RoleCode.TECHNICIAN && article.getStatus() == ArticleStatus.DRAFT
                && article.isWrittenBy(user.getId());
    }

    private static boolean isAdmin(User user) {
        return user.getRoleCode() == RoleCode.ADMIN;
    }
}
