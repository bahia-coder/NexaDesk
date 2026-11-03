// Light Markdown of the knowledge base articles and of the model's answers.

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
