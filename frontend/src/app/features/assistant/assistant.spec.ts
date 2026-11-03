import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AiStatus, AssistantAnswer } from '../../core/ai/ai.models';
import { AuthService } from '../../core/auth/auth.service';
import { AssistantPage } from './assistant.page';
import { markdownBlocks } from '../../core/format/markdown';
import { Exchange, historyFor, ticketTitleFor } from './conversation';

const VPN = {
  article: {
    id: 3,
    title: 'VPN : erreur 809 à la connexion',
    summary: 'Les vérifications à faire avant d’ouvrir un ticket.',
    category: { id: 1, name: 'Réseau & VPN', icon: 'wifi' },
  },
  score: 100,
};

const HEURISTIC: AiStatus = {
  mode: 'HEURISTIC',
  provider: null,
  model: null,
  disclaimer: 'Mode démonstration — heuristique locale, sans modèle d’IA.',
  requestsPerMinute: 20,
};

function reply(change: Partial<AssistantAnswer>): AssistantAnswer {
  return {
    source: 'HEURISTIC',
    disclaimer: HEURISTIC.disclaimer,
    responseMode: 'KNOWLEDGE_BASE',
    answer: null,
    articles: [VPN],
    confidence: null,
    suggestTicket: false,
    ...change,
  };
}

describe('Assistant conversation', () => {
  it('turns light Markdown into blocks, never into HTML', () => {
    expect(
      markdownBlocks(
        '## Vérifications\n\n1. Redémarrez la **box**.\n2. Relancez le VPN.\n\n- <b>Note</b>\nfin',
      ),
    ).toEqual([
      { kind: 'heading', text: 'Vérifications' },
      { kind: 'ordered', items: ['Redémarrez la box.', 'Relancez le VPN.'] },
      { kind: 'bullets', items: ['<b>Note</b>'] },
      { kind: 'paragraph', text: 'fin' },
    ]);
  });

  it('sends the previous turns, saying which articles were offered when no model answered', () => {
    const exchanges: Exchange[] = [
      { id: 1, question: 'Erreur 809', reply: reply({}), failed: false },
      {
        id: 2,
        question: 'Et sur Mac ?',
        reply: reply({ source: 'LLM', answer: 'Ouvrez les réglages réseau.' }),
        failed: false,
      },
      { id: 3, question: 'Sans réponse', reply: null, failed: true },
    ];
    expect(historyFor(exchanges)).toEqual([
      { role: 'USER', content: 'Erreur 809' },
      { role: 'ASSISTANT', content: 'Articles proposés : VPN : erreur 809 à la connexion' },
      { role: 'USER', content: 'Et sur Mac ?' },
      { role: 'ASSISTANT', content: 'Ouvrez les réglages réseau.' },
    ]);
  });

  it('turns a question into a one-line ticket title of 150 characters at most', () => {
    expect(ticketTitleFor('  Mon VPN\n  ne marche plus ')).toBe('Mon VPN ne marche plus');
    const long = ticketTitleFor('a'.repeat(400));
    expect(long).toHaveLength(150);
    expect(long.endsWith('…')).toBe(true);
  });
});

describe('Assistant page', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { user: signal({ firstName: 'Sara' }) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(status: AiStatus) {
    const fixture = TestBed.createComponent(AssistantPage);
    fixture.autoDetectChanges();
    http.expectOne('/api/ai/status').flush(status);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  async function ask(fixture: { whenStable(): Promise<unknown> }, el: HTMLElement, text: string) {
    const input = el.querySelector<HTMLTextAreaElement>('textarea')!;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await fixture.whenStable();
  }

  it('says no model is connected and only offers the matching articles', async () => {
    const { fixture, el } = await open(HEURISTIC);
    expect(el.querySelector('.notice')?.textContent).toContain('Aucun modèle d’IA n’est connecté');

    await ask(fixture, el, 'Mon VPN affiche erreur 809');
    const request = http.expectOne('/api/ai/assistant');
    expect(request.request.body).toEqual({ question: 'Mon VPN affiche erreur 809', history: [] });
    expect(el.querySelector('textarea')!.value).toBe('');
    request.flush(reply({}));
    await fixture.whenStable();

    const bubble = el.querySelector('.bubble--assistant')!;
    expect(bubble.textContent).toContain('Réponse basée sur la documentation interne');
    expect(bubble.textContent).toContain('Mode démo — heuristique');
    expect(bubble.querySelector('.confidence')).toBeNull();
    expect(bubble.textContent).toContain('Voici les articles de la base de connaissances');
    expect(bubble.querySelector('nx-markdown-text')).toBeNull();
    expect(bubble.querySelector('.source')?.textContent).toContain('VPN : erreur 809');

    // A follow-up carries the previous exchange.
    await ask(fixture, el, 'Et sur Mac ?');
    const second = http.expectOne('/api/ai/assistant');
    expect(second.request.body.history).toHaveLength(2);
    second.flush(reply({ responseMode: 'NO_ANSWER', articles: [], suggestTicket: true }));
    await fixture.whenStable();
    expect(el.querySelectorAll('.bubble--assistant')[1].textContent).toContain(
      'Aucun article de la base de connaissances ne couvre cette question.',
    );
    expect(el.querySelector('.ticket-hint a')?.getAttribute('href')).toBe(
      '/tickets/new?title=Et%20sur%20Mac%20%3F',
    );
  });

  it('shows a model’s answer with its sources, and lets a failed question be sent again', async () => {
    const { fixture, el } = await open({
      ...HEURISTIC,
      mode: 'LLM',
      provider: 'anthropic',
      model: 'claude-opus-5-5',
    });
    expect(el.querySelector('.notice')?.textContent).toContain('claude-opus-5-5');

    await ask(fixture, el, 'Erreur 809');
    http.expectOne('/api/ai/assistant').flush('boom', { status: 502, statusText: 'Bad Gateway' });
    await fixture.whenStable();
    expect(el.querySelector('.bubble--assistant')?.textContent).toContain('n’a pas pu répondre');

    el.querySelector<HTMLButtonElement>('.bubble--assistant button')!.click();
    http.expectOne('/api/ai/assistant').flush(
      reply({
        source: 'LLM',
        disclaimer: 'Réponse générée par IA à partir de la base de connaissances.',
        answer: '1. Redémarrez la box.\n2. Relancez le VPN.',
        confidence: 'HIGH',
      }),
    );
    await fixture.whenStable();
    const bubble = el.querySelector('.bubble--assistant')!;
    expect(bubble.textContent).toContain('Modèle IA');
    expect(Array.from(bubble.querySelectorAll('ol li')).map((li) => li.textContent)).toEqual([
      'Redémarrez la box.',
      'Relancez le VPN.',
    ]);
    expect(bubble.textContent).toContain('Sources');
    expect(bubble.querySelector('.confidence')?.textContent).toBe('Confiance élevée');
  });

  it('flags a general-knowledge suggestion, without sources, and offers a ticket', async () => {
    const { fixture, el } = await open({ ...HEURISTIC, mode: 'LLM', model: 'claude-opus-5-5' });
    await ask(fixture, el, 'Excel plante quand j’ouvre un gros fichier');
    http.expectOne('/api/ai/assistant').flush(
      reply({
        source: 'LLM',
        responseMode: 'AI_FALLBACK',
        disclaimer: 'Suggestion issue de connaissances générales, non validée par l’entreprise.',
        answer: 'Je n’ai pas trouvé de solution…\n\n1. Ouvrez Excel en mode sans échec.',
        articles: [],
        confidence: 'MEDIUM',
        suggestTicket: true,
      }),
    );
    await fixture.whenStable();
    const bubble = el.querySelector('.bubble--assistant')!;
    expect(bubble.querySelector('.badge--warning')?.textContent).toContain(
      'Suggestion générée par l’IA – connaissances générales',
    );
    expect(bubble.querySelector('.answer--fallback')).not.toBeNull();
    expect(bubble.querySelector('.sources')).toBeNull();
    expect(bubble.querySelector('.ticket-hint')?.textContent).toContain('un technicien');
    expect(bubble.querySelector('.ticket-hint a')?.getAttribute('href')).toContain(
      'title=Excel%20plante',
    );
  });
});
