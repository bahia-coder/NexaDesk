import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth.guards';

export const KNOWLEDGE_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./knowledge.page').then((m) => m.KnowledgePage),
    title: 'NexaDesk · Base de connaissances',
  },
  {
    path: 'new',
    canMatch: [roleGuard('TECHNICIAN', 'ADMIN')],
    loadComponent: () => import('./article-editor.page').then((m) => m.ArticleEditorPage),
    title: 'NexaDesk · Nouvel article',
  },
  {
    path: ':id/edit',
    canMatch: [roleGuard('TECHNICIAN', 'ADMIN')],
    loadComponent: () => import('./article-editor.page').then((m) => m.ArticleEditorPage),
    title: 'NexaDesk · Modifier l’article',
  },
  {
    path: ':id',
    loadComponent: () => import('./article.page').then((m) => m.ArticlePage),
    title: 'NexaDesk · Article',
  },
];
