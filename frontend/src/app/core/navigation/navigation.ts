import {
  BookOpen,
  ChartColumn,
  LayoutDashboard,
  LucideIconData,
  Monitor,
  ScrollText,
  Settings,
  Sparkles,
  Ticket,
  Users,
  Wrench,
} from 'lucide-angular';

import { Role } from '../models/user.models';

export interface NavItem {
  label: string;
  /** First segment of the route, without slash ("tickets"). */
  path: string;
  icon: LucideIconData;
}

export interface NavSection {
  /** Null for the first, untitled section. */
  title: string | null;
  items: NavItem[];
}

const DASHBOARD: NavItem = { label: 'Tableau de bord', path: 'dashboard', icon: LayoutDashboard };
const KNOWLEDGE: NavItem = { label: 'Base de connaissances', path: 'knowledge', icon: BookOpen };
const ASSISTANT: NavItem = { label: 'Assistant IA', path: 'assistant', icon: Sparkles };

/**
 * One shell for every role: only the navigation differs. This hides what a role cannot use; the API enforces
 * the actual permissions (a hidden link is not a security measure).
 */
export const NAVIGATION: Record<Role, NavSection[]> = {
  EMPLOYEE: [
    {
      title: null,
      items: [
        DASHBOARD,
        { label: 'Mes tickets', path: 'tickets', icon: Ticket },
        { label: 'Mes équipements', path: 'equipment', icon: Monitor },
      ],
    },
    { title: 'Aide', items: [KNOWLEDGE, ASSISTANT] },
  ],
  TECHNICIAN: [
    {
      title: null,
      items: [
        DASHBOARD,
        { label: 'Tickets', path: 'tickets', icon: Ticket },
        { label: 'Mes interventions', path: 'interventions', icon: Wrench },
        { label: 'Équipements', path: 'equipment', icon: Monitor },
        { label: 'Mes statistiques', path: 'reports', icon: ChartColumn },
      ],
    },
    { title: 'Connaissances', items: [KNOWLEDGE, ASSISTANT] },
  ],
  ADMIN: [
    {
      title: null,
      items: [
        DASHBOARD,
        { label: 'Tickets', path: 'tickets', icon: Ticket },
        // The API lists the signed-in user's own interventions (an admin's too).
        { label: 'Mes interventions', path: 'interventions', icon: Wrench },
        { label: 'Équipements', path: 'equipment', icon: Monitor },
        { label: 'Rapports', path: 'reports', icon: ChartColumn },
      ],
    },
    { title: 'Connaissances', items: [KNOWLEDGE, ASSISTANT] },
    {
      title: 'Administration',
      items: [
        { label: 'Utilisateurs', path: 'users', icon: Users },
        { label: 'Paramètres', path: 'settings', icon: Settings },
        { label: 'Journal d’audit', path: 'audit', icon: ScrollText },
      ],
    },
  ],
};

export interface NavEntry extends NavItem {
  /** False while the page is not built yet: such links are hidden in production. */
  available: boolean;
}

/**
 * Navigation of a role, each item flagged by whether a route exists for it. Hidden: sections whose items are all
 * unavailable when `keepUnavailable` is false (production), so the menu never contains a dead link.
 */
export function navigationFor(
  role: Role,
  routedPaths: ReadonlySet<string>,
  keepUnavailable: boolean,
): { title: string | null; items: NavEntry[] }[] {
  return NAVIGATION[role]
    .map((section) => ({
      title: section.title,
      items: section.items
        .map((item) => ({ ...item, available: routedPaths.has(item.path) }))
        .filter((item) => item.available || keepUnavailable),
    }))
    .filter((section) => section.items.length > 0);
}
