import { CategoryRef, PriorityCode, TicketSummary } from '../models/ticket.models';

// Mirrors com.nexadesk.ai.dto.AiDtos. Every generated answer says where it comes from.

/** LLM: a connected model; HEURISTIC: local rules and search, no model; DISABLED: AI turned off. */
export type AiSource = 'LLM' | 'HEURISTIC' | 'DISABLED';

export interface Classification {
  source: AiSource;
  disclaimer: string;
  category: CategoryRef | null;
  subcategory: { id: number; name: string } | null;
  impact: 'LOW' | 'MEDIUM' | 'HIGH' | null;
  urgency: 'LOW' | 'MEDIUM' | 'HIGH' | null;
  priority: PriorityCode | null;
  /** 0 to 1. */
  confidence: number;
  reasons: string[];
}

export interface ArticleSummary {
  id: number;
  title: string;
  summary: string;
  category: CategoryRef;
}

export interface SimilarContent {
  tickets: { ticket: TicketSummary; score: number; resolution: string | null }[];
  articles: { article: ArticleSummary; score: number }[];
}

/** GET /api/ai/status: which AI is behind the answers (provider and model only when one is connected). */
export interface AiStatus {
  mode: AiSource;
  provider: string | null;
  model: string | null;
  disclaimer: string;
  requestsPerMinute: number;
}

export interface ArticleMatch {
  article: ArticleSummary;
  /** Relevance from 1 to 100, relative to the other results. */
  score: number;
}

export interface ChatTurn {
  role: 'USER' | 'ASSISTANT';
  content: string;
}

/**
 * Knowledge-base assistant. `answer` is null when no model wrote one: the user then gets the matching articles.
 * `suggestTicket`: nothing in the knowledge base covers the question.
 */
/**
 * Where an assistant answer comes from, set by the server from the articles actually used:
 * KNOWLEDGE_BASE (internal articles), HYBRID (articles + general advice kept apart), AI_FALLBACK (the model's
 * general knowledge, without sources), NO_ANSWER (nothing fits and no model to fall back on).
 */
export type ResponseMode = 'KNOWLEDGE_BASE' | 'HYBRID' | 'AI_FALLBACK' | 'NO_ANSWER';

export type Confidence = 'LOW' | 'MEDIUM' | 'HIGH';

export interface AssistantAnswer {
  source: AiSource;
  disclaimer: string;
  responseMode: ResponseMode;
  answer: string | null;
  /** The sources actually used, and only those (empty for AI_FALLBACK). */
  articles: ArticleMatch[];
  /** Null when no model wrote the answer. */
  confidence: Confidence | null;
  suggestTicket: boolean;
}

/** GET /api/knowledge/{id} (fields used by the reader). */
export interface Article {
  id: number;
  title: string;
  summary: string;
  /** Light Markdown: "## " headings, numbered or bulleted lists, paragraphs. */
  content: string;
  category: CategoryRef;
  helpfulCount: number;
  helpedByMe: boolean;
  updatedAt: string;
}
