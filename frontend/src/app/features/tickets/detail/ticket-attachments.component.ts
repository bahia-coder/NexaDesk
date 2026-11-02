import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { Download, FileText, LucideAngularModule, Paperclip, Trash2 } from 'lucide-angular';
import { switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { formatDateTime } from '../../../core/format/time';
import { toApiProblem } from '../../../core/http/api-error';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ToastService } from '../../../core/services/toast.service';
import { SpinnerComponent } from '../../../shared/ui/spinner.component';
import { formatSize } from '../tickets.labels';
import { Attachment, TicketDetail } from '../tickets.models';
import { TicketsService } from '../tickets.service';

/** Accepted by the API (it checks the real content, not just the name): PNG, JPG, PDF, TXT, LOG, DOCX, XLSX. */
export const ACCEPTED_FILES = '.png,.jpg,.jpeg,.pdf,.txt,.log,.docx,.xlsx';
export const MAX_FILE_BYTES = 10 * 1024 * 1024;

@Component({
  selector: 'nx-ticket-attachments',
  imports: [LucideAngularModule, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="files">
      @for (a of attachments(); track a.id) {
        <li class="file">
          <lucide-icon [img]="icons.FileText" [size]="18" aria-hidden="true" />
          <div class="file__info">
            <span class="file__name">{{ a.originalName }}</span>
            <span class="muted small">
              {{ size(a.sizeBytes) }} · {{ a.uploadedBy.fullName }} · {{ exact(a.createdAt) }}
            </span>
          </div>
          <button
            type="button"
            class="btn btn--ghost btn--icon btn--sm"
            [attr.aria-label]="'Télécharger ' + a.originalName"
            (click)="download(a)"
          >
            <lucide-icon [img]="icons.Download" [size]="16" aria-hidden="true" />
          </button>
          @if (canDelete(a)) {
            <button
              type="button"
              class="btn btn--ghost btn--icon btn--sm"
              [attr.aria-label]="'Supprimer ' + a.originalName"
              (click)="remove(a)"
            >
              <lucide-icon [img]="icons.Trash2" [size]="16" aria-hidden="true" />
            </button>
          }
        </li>
      } @empty {
        <li class="muted empty">Aucune pièce jointe.</li>
      }
    </ul>

    @if (ticket().availableActions.includes('ADD_ATTACHMENT')) {
      <label class="upload btn btn--secondary">
        @if (uploading()) {
          <nx-spinner />
        } @else {
          <lucide-icon [img]="icons.Paperclip" [size]="16" aria-hidden="true" />
        }
        Ajouter un fichier
        <input
          class="sr-only"
          type="file"
          [accept]="accepted"
          [disabled]="uploading()"
          (change)="upload($any($event.target))"
        />
      </label>
      <p class="muted small">
        PNG, JPG, PDF, TXT, LOG, DOCX, XLSX · 10 Mo maximum · 5 fichiers par ticket.
      </p>
    }
  `,
  styleUrl: './ticket-detail.scss',
})
export class TicketAttachmentsComponent {
  private readonly api = inject(TicketsService);
  private readonly auth = inject(AuthService);
  private readonly confirmService = inject(ConfirmService);
  private readonly toasts = inject(ToastService);
  private readonly document = inject(DOCUMENT);

  readonly ticket = input.required<TicketDetail>();
  readonly attachments = input.required<Attachment[]>();
  readonly changed = output<void>();

  protected readonly icons = { Download, FileText, Paperclip, Trash2 };
  protected readonly accepted = ACCEPTED_FILES;
  protected readonly uploading = signal(false);

  private readonly terminal = computed(() => ['CLOSED', 'REJECTED'].includes(this.ticket().status));

  /** Same rule as the API: an admin while the ticket is open, the uploader while it is new. */
  protected canDelete(a: Attachment): boolean {
    const user = this.auth.user();
    if (!user) {
      return false;
    }
    return (
      (user.role === 'ADMIN' && !this.terminal()) ||
      (a.uploadedBy.id === user.id && this.ticket().status === 'NEW')
    );
  }

  protected upload(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      this.toasts.error(`« ${file.name} » dépasse 10 Mo.`);
      return;
    }
    this.uploading.set(true);
    this.api.upload(this.ticket().id, file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.toasts.success('Fichier ajouté.');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.uploading.set(false);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }

  protected download(a: Attachment): void {
    this.api.download(this.ticket().id, a.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const link = this.document.createElement('a');
      link.href = url;
      link.download = a.originalName;
      link.click();
      URL.revokeObjectURL(url);
    });
  }

  protected remove(a: Attachment): void {
    this.confirmService
      .confirm({
        title: 'Supprimer la pièce jointe',
        message: `« ${a.originalName} » sera définitivement supprimé du ticket.`,
        confirmLabel: 'Supprimer',
        tone: 'danger',
      })
      .pipe(
        switchMap((result) => (result ? this.api.deleteAttachment(this.ticket().id, a.id) : [])),
      )
      .subscribe({
        next: () => {
          this.toasts.success('Pièce jointe supprimée.');
          this.changed.emit();
        },
        error: (error: unknown) => this.toasts.error(toApiProblem(error).detail),
      });
  }

  protected size(bytes: number): string {
    return formatSize(bytes);
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
