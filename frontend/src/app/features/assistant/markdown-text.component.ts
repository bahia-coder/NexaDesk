import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { markdownBlocks } from './conversation';

/** Renders article content or an answer (light Markdown) as plain elements, never as raw HTML. */
@Component({
  selector: 'nx-markdown-text',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (block of blocks(); track $index) {
      @switch (block.kind) {
        @case ('heading') {
          <h3 class="heading">{{ block.text }}</h3>
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
  protected readonly blocks = computed(() => markdownBlocks(this.text()));
}
