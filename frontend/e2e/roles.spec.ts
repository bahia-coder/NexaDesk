import { expect, test } from '@playwright/test';

import { Role, demoAccounts, signIn } from './support';

/** What each role finds in the menu (production build: only pages that exist). */
const MENU: Record<Role, string[]> = {
  EMPLOYEE: [
    'Tableau de bord',
    'Mes tickets',
    'Mes équipements',
    'Base de connaissances',
    'Assistant IA',
  ],
  TECHNICIAN: [
    'Tableau de bord',
    'Tickets',
    'Mes interventions',
    'Équipements',
    'Mes statistiques',
    'Base de connaissances',
    'Assistant IA',
  ],
  ADMIN: [
    'Tableau de bord',
    'Tickets',
    'Mes interventions',
    'Équipements',
    'Rapports',
    'Base de connaissances',
    'Assistant IA',
    'Utilisateurs',
    'Paramètres',
    'Journal d’audit',
  ],
};

/** Pages a role must not open: the route does not match, the user lands on the dashboard. */
const FORBIDDEN: Record<Role, string[]> = {
  EMPLOYEE: ['/users', '/settings', '/audit', '/reports', '/interventions'],
  TECHNICIAN: ['/users', '/settings', '/audit'],
  ADMIN: [],
};

for (const role of ['EMPLOYEE', 'TECHNICIAN', 'ADMIN'] as Role[]) {
  test(`${role} : menu du rôle et pages interdites`, async ({ page, request }) => {
    const accounts = await demoAccounts(request);
    await signIn(page, accounts[role]);

    const menu = page.getByRole('navigation', { name: 'Navigation principale' });
    await expect(menu.getByRole('link')).toHaveText(MENU[role]);

    for (const path of FORBIDDEN[role]) {
      await page.goto(path);
      await expect(page, `${role} ne doit pas ouvrir ${path}`).toHaveURL(/\/dashboard$/);
    }
  });
}

test('l’API refuse aussi ce que l’interface cache', async ({ request }) => {
  const { EMPLOYEE } = await demoAccounts(request);
  const login = await request.post('/api/auth/login', {
    data: { email: EMPLOYEE.email, password: EMPLOYEE.password },
  });
  const { accessToken } = (await login.json()) as { accessToken: string };
  const headers = { Authorization: `Bearer ${accessToken}` };
  expect((await request.get('/api/users', { headers })).status()).toBe(403);
  expect((await request.get('/api/audit', { headers })).status()).toBe(403);
  expect((await request.get('/api/reports/summary', { headers })).status()).toBe(403);
});
