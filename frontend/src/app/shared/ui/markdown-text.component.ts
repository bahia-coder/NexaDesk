import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { markdownBlocks, outline } from '../../core/format/markdown';

/** Renders article content or an answer (light Markdown) as plain elements, never as raw HTML. */
@Component({
  selector: 'nx-markdown-text',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (block of blocks(); track $index) {
      @switch (block.kind) {
        @case ('heading') {
          <h3 class="heading" [attr.id]="headingId($index)">{{ block.text }}</h3>
        }
        @case ('paragraph') {
          <p>{{ block.text }}</p>
        }
        @case ('ordered') {
          <ol>
            @for (item of block.items; track $index) {
              <li>{{ item }}</li>
            }
          </ol>
        }
        @case ('bullets') {
          <ul>
            @for (item of block.items; track $index) {
              <li>{{ item }}</li>
            }
          </ul>
        }
      }
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      overflow-wrap: anywhere;
    }

    .heading {
      scroll-margin-top: var(--space-6);
      margin-top: var(--space-2);
      font: 600 0.9375rem/1.4 var(--font-sans);
    }

    p,
    ol,
    ul {
      margin: 0;
    }

    ol,
    ul {
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
      padding-left: var(--space-5);
    }

    ol {
      list-style: decimal;
    }

    ul {
      list-style: disc;
    }
  `,
})
export class MarkdownTextComponent {
  readonly text = input.required<string>();
  /** When set, section titles get anchor ids (prefix + outline id) for a table of contents. */
  readonly anchorPrefix = input<string>();

  protected readonly blocks = computed(() => markdownBlocks(this.text()));
  /** Block index of each heading → its anchor id, in the order of {@link outline}. */
  private readonly anchors = computed(() => {
    const prefix = this.anchorPrefix();
    const ids = new Map<number, string>();
    if (prefix === undefined) {
      return ids;
    }
    const entries = outline(this.text());
    let next = 0;
    this.blocks().forEach((block, index) => {
      if (block.kind === 'heading') {
        ids.set(index, prefix + entries[next++].id);
      }
    });
    return ids;
  });

  protected headingId(index: number): string | null {
    return this.anchors().get(index) ?? null;
  }
}
