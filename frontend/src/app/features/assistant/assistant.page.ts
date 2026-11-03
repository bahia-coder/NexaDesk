import { Dialog } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  BookOpen,
  Info,
  LucideAngularModule,
  RefreshCw,
  RotateCcw,
  SendHorizontal,
  Sparkles,
  TicketPlus,
} from 'lucide-angular';
import { catchError, of } from 'rxjs';

import { ArticleSummary, AiStatus } from '../../core/ai/ai.models';
import { AiService } from '../../core/ai/ai.service';
import { AuthService } from '../../core/auth/auth.service';
import { AiSourceBadgeComponent } from '../../shared/ui/ai-source-badge.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { ArticleDialogComponent } from './article-dialog.component';
import {
  CONFIDENCE_LABELS,
  Exchange,
  RESPONSE_MODES,
  historyFor,
  ticketTitleFor,
} from './conversation';
import { MarkdownTextComponent } from '../../shared/ui/markdown-text.component';

const MAX_QUESTION = 1000;

/** Shortcuts for a first question; the answer always comes from the knowledge base. */
const STARTERS = [
  'Le VPN affiche une erreur et ne se connecte plus',
  'Comment changer mon mot de passe ?',
  'Le Wi-Fi coupe pendant les réunions',
];

/**
 * Knowledge-base assistant. It never pretends: with a connected model it shows the model's answer and the
 * articles it is based on; without one it says so and offers the matching articles only.
 */
@Component({
  selector: 'nx-assistant-page',
  imports: [
    FormsModule,
    RouterLink,
    LucideAngularModule,
    AiSourceBadgeComponent,
    MarkdownTextComponent,
    SpinnerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './assistant.page.html',
  styleUrl: './assistant.scss',
})
export class AssistantPage {
  private readonly ai = inject(AiService);
  private readonly dialog = inject(Dialog);
  private readonly injector = inject(Injector);
  protected readonly auth = inject(AuthService);

  protected readonly icons = {
    BookOpen,
    Info,
    RefreshCw,
    RotateCcw,
    SendHorizontal,
    Sparkles,
    TicketPlus,
  };
  protected readonly modes = RESPONSE_MODES;
  protected readonly confidenceLabels = CONFIDENCE_LABELS;
  protected readonly starters = STARTERS;
  protected readonly maxQuestion = MAX_QUESTION;

  protected readonly status = toSignal(
    this.ai.status().pipe(catchError(() => of(null as AiStatus | null))),
    { initialValue: undefined },
  );
  /** What the user is told about the AI behind the answers, before asking anything. */
  protected readonly notice = computed(() => {
    const status = this.status();
    if (status === undefined) {
      return null;
    }
    switch (status?.mode) {
      case 'LLM':
        return `Un modèle d’IA (${status.model ?? status.provider}) répond d’abord à partir des articles publiés de la base de connaissances. Si aucun ne couvre votre question, il propose une suggestion tirée de ses connaissances générales, signalée comme telle. Vérifiez les étapes avant de les appliquer.`;
      case 'HEURISTIC':
        return 'Aucun modèle d’IA n’est connecté : l’assistant ne rédige pas de réponse et ne propose pas de suggestion générale ; il recherche les articles de la base de connaissances qui correspondent à votre question.';
      case 'DISABLED':
        return 'Les fonctions d’IA sont désactivées sur cette installation : l’assistant recherche seulement les articles correspondants.';
      default:
        return 'L’état de l’assistant n’a pas pu être vérifié.';
    }
  });

  protected readonly exchanges = signal<Exchange[]>([]);
  /** ?q= from the knowledge base ("Demander à l’assistant"): ready to send, not sent. */
  readonly q = input<string>();
  protected readonly draft = linkedSignal(() => (this.q() ?? '').slice(0, MAX_QUESTION));
  protected readonly pending = computed(() => this.exchanges().some((e) => !e.reply && !e.failed));
  protected readonly canSend = computed(() => {
    const text = this.draft().trim();
    return text.length > 0 && text.length <= MAX_QUESTION && !this.pending();
  });

  private nextId = 1;
  private readonly thread = viewChild<ElementRef<HTMLElement>>('thread');

  protected ask(question: string): void {
    const text = question.trim();
    if (!text || text.length > MAX_QUESTION || this.pending()) {
      return;
    }
    const exchange: Exchange = { id: this.nextId++, question: text, reply: null, failed: false };
    const history = historyFor(this.exchanges());
    this.exchanges.update((list) => [...list, exchange]);
    this.draft.set('');
    this.scrollToEnd();
    this.ai.assistant(text, history).subscribe({
      next: (reply) => {
        this.replace(exchange.id, { reply });
        this.scrollToEnd();
      },
      error: () => this.replace(exchange.id, { failed: true }),
    });
  }

  /** Sends a failed question again, with the same history as the first time. */
  protected retry(exchange: Exchange): void {
    const index = this.exchanges().findIndex((e) => e.id === exchange.id);
    const history = historyFor(this.exchanges().slice(0, index));
    this.replace(exchange.id, { failed: false });
    this.ai.assistant(exchange.question, history).subscribe({
      next: (reply) => {
        this.replace(exchange.id, { reply });
        this.scrollToEnd();
      },
      error: () => this.replace(exchange.id, { failed: true }),
    });
  }

  protected onKeydown(event: KeyboardEvent): void {
    // Enter sends, Shift+Enter starts a new line.
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.ask(this.draft());
    }
  }

  protected restart(): void {
    this.exchanges.set([]);
    this.draft.set('');
  }

  protected ticketTitle(question: string): string {
    return ticketTitleFor(question);
  }

  protected read(article: ArticleSummary): void {
    this.dialog.open(ArticleDialogComponent, {
      data: { id: article.id, title: article.title },
      ariaLabelledBy: 'nx-article-title',
      panelClass: 'nx-dialog-panel',
      backdropClass: 'nx-backdrop',
    });
  }

  private replace(id: number, change: Partial<Exchange>): void {
    this.exchanges.update((list) => list.map((e) => (e.id === id ? { ...e, ...change } : e)));
  }

  private scrollToEnd(): void {
    afterNextRender(
      () => {
        const el = this.thread()?.nativeElement;
        el?.lastElementChild?.scrollIntoView?.({ behavior: 'smooth', block: 'end' });
      },
      { injector: this.injector },
    );
  }
}
