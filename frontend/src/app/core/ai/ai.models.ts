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
