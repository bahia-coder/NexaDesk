import { AssistantAnswer, ChatTurn } from '../../core/ai/ai.models';

/** One exchange of the conversation: the user's question, then the assistant's reply once it arrives. */
export interface Exchange {
  id: number;
  question: string;
  reply: AssistantAnswer | null;
  /** The request failed: the question can be sent again. */
  failed: boolean;
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
