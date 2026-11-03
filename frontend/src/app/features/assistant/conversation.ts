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

export type Block =
  | { kind: 'heading'; text: string }
  | { kind: 'paragraph'; text: string }
  | { kind: 'ordered'; items: string[] }
  | { kind: 'bullets'; items: string[] };

const HEADING = /^#{1,6}\s+(.*)$/;
const ORDERED = /^\d+[.)]\s+(.*)$/;
const BULLET = /^[-*•]\s+(.*)$/;

/**
 * The light Markdown of the articles and of the model's answers, as blocks rendered by Angular templates: text
 * stays text (no innerHTML), so nothing in an article or an answer can inject markup. Emphasis markers are
 * dropped.
 */
export function markdownBlocks(text: string): Block[] {
  const blocks: Block[] = [];
  let paragraph: string[] = [];
  const flush = () => {
    if (paragraph.length) {
      blocks.push({ kind: 'paragraph', text: paragraph.join(' ') });
      paragraph = [];
    }
  };
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim().replace(/\*\*|__/g, '');
    if (!line) {
      flush();
      continue;
    }
    const heading = HEADING.exec(line);
    const ordered = ORDERED.exec(line);
    const bullet = BULLET.exec(line);
    if (heading) {
      flush();
      blocks.push({ kind: 'heading', text: heading[1] });
    } else if (ordered || bullet) {
      flush();
      const kind = ordered ? 'ordered' : 'bullets';
      const item = (ordered ?? bullet)![1];
      const last = blocks.at(-1);
      if (last && last.kind === kind) {
        last.items.push(item);
      } else {
        blocks.push({ kind, items: [item] });
      }
    } else {
      paragraph.push(line);
    }
  }
  flush();
  return blocks;
}
