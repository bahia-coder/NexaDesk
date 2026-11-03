import { Tone } from '../../core/format/labels';
import { CategoryRef, Instant, UserRef } from '../../core/models/ticket.models';

// Mirrors com.nexadesk.knowledge.dto.KnowledgeDtos and ArticleStatus.

/** A technician writes a DRAFT, an admin publishes it, and archives it once it is obsolete. */
export type ArticleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

/** What the current user may do on an article, decided by the API (KnowledgePolicy). */
export type ArticleAction = 'EDIT' | 'PUBLISH' | 'ARCHIVE' | 'MARK_HELPFUL';

/** RELEVANCE needs a search text; without one the API sorts by RECENT. */
export type ArticleSort = 'RELEVANCE' | 'RECENT' | 'POPULAR';

export interface ArticleSummary {
  id: number;
  title: string;
  summary: string;
  status: ArticleStatus;
  category: CategoryRef;
  author: UserRef;
  viewCount: number;
  helpfulCount: number;
  publishedAt: Instant | null;
  updatedAt: Instant;
}

export interface KnowledgeArticle extends ArticleSummary {
  /** Light Markdown: "## " headings, lists, paragraphs. */
  content: string;
  /** The ticket it was written from (support staff only). */
  sourceTicket: { id: number; reference: string } | null;
  linkedTickets: number;
  helpedByMe: boolean;
  createdAt: Instant;
  /** Optimistic locking: sent back when saving. */
  version: number;
  availableActions: ArticleAction[];
}

/** GET /api/knowledge/categories: published articles per category, for browsing by theme. */
export interface CategoryCount {
  category: CategoryRef;
  articles: number;
}

/** About 200 words a minute, never less than a minute. */
export function readingMinutes(content: string): number {
  const words = content.trim().split(/\s+/).filter(Boolean).length;
  return Math.max(1, Math.round(words / 200));
}

export interface ArticleFilter {
  search?: string;
  categoryId?: number;
  status?: ArticleStatus;
  sort?: ArticleSort;
}

/** Body of POST / PUT /api/knowledge. */
export interface ArticleFields {
  title: string;
  summary: string;
  content: string;
  categoryId: number;
}

export const ARTICLE_STATUS_LABELS: Record<ArticleStatus, string> = {
  DRAFT: 'Brouillon',
  PUBLISHED: 'Publié',
  ARCHIVED: 'Archivé',
};

export const ARTICLE_STATUS_TONES: Record<ArticleStatus, Tone> = {
  DRAFT: 'warning',
  PUBLISHED: 'success',
  ARCHIVED: 'slate',
};

export const ARTICLE_SORT_LABELS: Record<ArticleSort, string> = {
  RELEVANCE: 'Pertinence',
  RECENT: 'Plus récents',
  POPULAR: 'Plus consultés',
};

/** Same limits as the API (ArticleRequest). */
export const ARTICLE_LIMITS = {
  titleMin: 5,
  titleMax: 150,
  summaryMax: 500,
  contentMin: 20,
  contentMax: 20_000,
} as const;
