import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/models/user.models';
import { ConfirmService } from '../../core/services/confirm.service';
import { TicketDetail } from '../tickets/tickets.models';
import { ArticleEditorPage } from './article-editor.page';
import { ArticlePage } from './article.page';
import { KnowledgeArticle } from './knowledge.models';
import { TicketKnowledgeComponent } from './ticket-knowledge.component';

const ARTICLE: KnowledgeArticle = {
  id: 7,
  title: 'Résoudre l’erreur VPN 809 en télétravail',
  summary: 'Vérifications à faire chez soi.',
  status: 'DRAFT',
  category: { id: 1, name: 'Réseau & VPN', icon: 'wifi' },
  author: { id: 2, fullName: 'Karim Belhadj', avatarColor: '#4f46e5' },
  viewCount: 0,
  helpfulCount: 0,
  publishedAt: null,
  updatedAt: '2026-11-03T08:00:00Z',
  content: '## Vérifications\n\n1. Redémarrez la box.\n2. Relancez le VPN.',
  sourceTicket: { id: 12, reference: 'INC-2026-0012' },
  linkedTickets: 1,
  helpedByMe: false,
  createdAt: '2026-11-03T08:00:00Z',
  version: 3,
  availableActions: ['EDIT', 'PUBLISH', 'ARCHIVE'],
};

describe('Knowledge base', () => {
  let http: HttpTestingController;
  const role = signal<Role>('ADMIN');
  const userId = signal(1);

  beforeEach(() => {
    role.set('ADMIN');
    userId.set(1);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            role: computed(() => role()),
            user: computed(() => ({ id: userId(), role: role() })),
          },
        },
        { provide: ConfirmService, useValue: { confirm: () => of({}) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function buttons(el: HTMLElement, selector: string): string[] {
    return Array.from(el.querySelectorAll(selector)).map((b) => (b.textContent ?? '').trim());
  }

  it('shows the actions the API allows, and publishes a draft', async () => {
    const fixture = TestBed.createComponent(ArticlePage);
    fixture.componentRef.setInput('id', '7');
    fixture.autoDetectChanges();
    http.expectOne('/api/knowledge/7').flush(ARTICLE);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.title-row .badge')?.textContent).toContain('Brouillon');
    expect(
      Array.from(el.querySelectorAll('nx-markdown-text li')).map((li) => li.textContent),
    ).toEqual(['Redémarrez la box.', 'Relancez le VPN.']);
    expect(buttons(el, '.actions .btn')).toEqual(['Modifier', 'Publier', 'Archiver']);
    expect(el.textContent).toContain('INC-2026-0012');

    Array.from(el.querySelectorAll<HTMLButtonElement>('.actions button'))
      .find((b) => b.textContent?.includes('Publier'))!
      .click();
    http
      .expectOne({ method: 'POST', url: '/api/knowledge/7/publish' })
      .flush({
        ...ARTICLE,
        status: 'PUBLISHED',
        availableActions: ['EDIT', 'ARCHIVE', 'MARK_HELPFUL'],
      });
    await fixture.whenStable();
    expect(el.querySelector('.title-row .badge')).toBeNull();
    expect(buttons(el, '.actions .btn')).toEqual(['Modifier', 'Archiver']);
    expect(el.textContent).toContain('Oui, il m’a aidé');
  });

  it('lets an employee say a published article helped, once', async () => {
    role.set('EMPLOYEE');
    const fixture = TestBed.createComponent(ArticlePage);
    fixture.componentRef.setInput('id', '7');
    fixture.autoDetectChanges();
    http
      .expectOne('/api/knowledge/7')
      .flush({
        ...ARTICLE,
        status: 'PUBLISHED',
        sourceTicket: null,
        availableActions: ['MARK_HELPFUL'],
      });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.actions')).toBeNull();
    expect(el.textContent).not.toContain('Tickets liés');

    const helpful = Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent?.includes('il m’a aidé'),
    )!;
    helpful.click();
    http
      .expectOne({ method: 'POST', url: '/api/knowledge/7/helpful' })
      .flush({ helpfulCount: 1, helpedByMe: true });
    await fixture.whenStable();
    expect(helpful.disabled).toBe(true);
    expect(helpful.textContent).toContain('Merci pour votre retour');
  });

  it('saves an edit with the version read, and shows the API’s field errors', async () => {
    const fixture = TestBed.createComponent(ArticleEditorPage);
    fixture.componentRef.setInput('id', '7');
    fixture.autoDetectChanges();
    http.expectOne('/api/categories').flush([{ id: 1, name: 'Réseau & VPN', subcategories: [] }]);
    http.expectOne('/api/knowledge/7').flush(ARTICLE);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const title = el.querySelector<HTMLInputElement>('input[formcontrolname=title]')!;
    expect(title.value).toBe(ARTICLE.title);
    expect(el.querySelector('.preview')?.textContent).toContain('Vérifications');

    title.value = 'VPN : erreur 809';
    title.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    const put = http.expectOne({ method: 'PUT', url: '/api/knowledge/7' });
    expect(put.request.body).toMatchObject({
      title: 'VPN : erreur 809',
      categoryId: 1,
      version: 3,
    });
    put.flush(
      {
        code: 'VALIDATION_FAILED',
        detail: 'Certains champs sont invalides.',
        errors: [{ field: 'title', message: 'Le titre doit contenir entre 5 et 150 caractères.' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    expect(title.parentElement?.querySelector('.field__error')?.textContent).toContain(
      'entre 5 et 150',
    );

    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.expectOne({ method: 'PUT', url: '/api/knowledge/7' }).flush(ARTICLE);
    expect(navigate).toHaveBeenCalledWith(['/knowledge', 7]);
  });

  it('refuses to edit an article the API does not let the user change', async () => {
    role.set('TECHNICIAN');
    const fixture = TestBed.createComponent(ArticleEditorPage);
    fixture.componentRef.setInput('id', '7');
    fixture.autoDetectChanges();
    http.expectOne('/api/categories').flush([]);
    http
      .expectOne('/api/knowledge/7')
      .flush({ ...ARTICLE, status: 'PUBLISHED', availableActions: [] });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Article non modifiable');
    expect(el.querySelector('form')).toBeNull();
  });

  it('lets the ticket’s technician unlink an article and draft one from a resolved ticket', async () => {
    role.set('TECHNICIAN');
    userId.set(2);
    const ticket = {
      id: 12,
      status: 'RESOLVED',
      resolution: 'Box redémarrée.',
      assignee: { id: 2, fullName: 'Karim Belhadj', avatarColor: '#4f46e5' },
    } as TicketDetail;
    const fixture = TestBed.createComponent(TicketKnowledgeComponent);
    fixture.componentRef.setInput('ticket', ticket);
    fixture.autoDetectChanges();
    http
      .expectOne('/api/tickets/12/knowledge-links')
      .flush([ARTICLE, { ...ARTICLE, id: 8, title: 'Wi-Fi instable', status: 'PUBLISHED' }]);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(buttons(el, '.buttons .btn')).toEqual(['Lier un article', 'Rédiger un article']);

    el.querySelector<HTMLButtonElement>('[aria-label="Délier « Wi-Fi instable »"]')!.click();
    const put = http.expectOne({ method: 'PUT', url: '/api/tickets/12/knowledge-links' });
    expect(put.request.body).toEqual({ articleIds: [7] });
    put.flush([ARTICLE]);
    await fixture.whenStable();
    expect(el.querySelectorAll('.links li')).toHaveLength(1);

    // Another technician's ticket: read-only, and nothing shown when no article is linked.
    fixture.componentRef.setInput('ticket', { ...ticket, id: 13, assignee: null });
    await fixture.whenStable();
    http.expectOne('/api/tickets/13/knowledge-links').flush([]);
    await fixture.whenStable();
    expect(el.querySelector('section')).toBeNull();
  });
});
