import { AssistantAnswer, ChatTurn, Confidence, ResponseMode } from '../../core/ai/ai.models';

/** One exchange of the conversation: the user's question, then the assistant's reply once it arrives. */
export interface Exchange {
  id: number;
  question: string;
  reply: AssistantAnswer | null;
  /** The request failed: the question can be sent again. */
  failed: boolean;
}

/** How the conversation labels each kind of answer. */
export const RESPONSE_MODES: Record<
  ResponseMode,
  { label: string; tone: 'success' | 'indigo' | 'warning' | 'slate'; ticketHint: string }
> = {
  KNOWLEDGE_BASE: {
    label: 'Réponse basée sur la documentation interne',
    tone: 'success',
    ticketHint: 'Si ces articles ne règlent pas votre problème, le support peut prendre le relais.',
  },
  HYBRID: {
    label: 'Réponse combinant documentation interne et IA',
    tone: 'indigo',
    ticketHint: 'Si le problème persiste, le support peut prendre le relais.',
  },
  AI_FALLBACK: {
    label: 'Suggestion générée par l’IA – connaissances générales',
    tone: 'warning',
    ticketHint: 'Si ces étapes ne règlent pas le problème, un technicien peut prendre le relais.',
  },
  NO_ANSWER: {
    label: 'Aucun article correspondant',
    tone: 'slate',
    ticketHint: 'Aucun article de la base de connaissances ne couvre cette question.',
  },
};

export const CONFIDENCE_LABELS: Record<Confidence, string> = {
  HIGH: 'Confiance élevée',
  MEDIUM: 'Confiance moyenne',
  LOW: 'Confiance faible',
};

const TICKET_TITLE_MAX = 150;

/** Title proposed for the ticket opened from a question: the question itself, on one line. */
export function ticketTitleFor(question: string): string {
  const line = question.replace(/\s+/g, ' ').trim();
  return line.length <= TICKET_TITLE_MAX
    ? line
    : line.slice(0, TICKET_TITLE_MAX - 1).trimEnd() + '…';
}

const MAX_HISTORY = 10;
const MAX_TURN = 4000;

/**
 * Previous turns sent with a new question (the API keeps the last 10 and caps each at 4,000 characters). When
 * no model wrote an answer, the assistant's turn says which articles were offered, so a follow-up still has
 * context.
 */
export function historyFor(exchanges: Exchange[]): ChatTurn[] {
  const turns: ChatTurn[] = [];
  for (const exchange of exchanges) {
    if (!exchange.reply) {
      continue;
    }
    turns.push({ role: 'USER', content: exchange.question.slice(0, MAX_TURN) });
    const reply = exchange.reply;
    const content =
      reply.answer ??
      (reply.articles.length
        ? `Articles proposés : ${reply.articles.map((m) => m.article.title).join(' ; ')}`
        : 'Aucun article ne correspond à cette question.');
    turns.push({ role: 'ASSISTANT', content: content.slice(0, MAX_TURN) });
  }
  return turns.slice(-MAX_HISTORY);
}
