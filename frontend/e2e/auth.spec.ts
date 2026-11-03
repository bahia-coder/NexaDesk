import { expect, test } from '@playwright/test';

import { demoAccounts, signIn } from './support';

test.describe('Connexion', () => {
  test('refuse un mauvais mot de passe sans dire lequel des deux est faux', async ({
    page,
    request,
  }) => {
    const { EMPLOYEE } = await demoAccounts(request);
    await page.goto('/login');
    await page.getByLabel('E-mail').fill(EMPLOYEE.email);
    await page.getByLabel('Mot de passe', { exact: true }).fill('pas-le-bon-mot-de-passe');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(page.getByRole('alert')).toContainText('E-mail ou mot de passe incorrect.');
    await expect(page).toHaveURL(/\/login/);
  });

  test('renvoie un visiteur vers la connexion, puis vers la page demandée', async ({
    page,
    request,
  }) => {
    const { EMPLOYEE } = await demoAccounts(request);
    await page.goto('/tickets');
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Ftickets/);
    await signIn(page, EMPLOYEE, /\/tickets$/);
    await expect(page.getByRole('heading', { name: 'Mes tickets' })).toBeVisible();
  });

  test('garde la session au rechargement, sans jeton lisible par la page', async ({
    page,
    request,
  }) => {
    const { EMPLOYEE } = await demoAccounts(request);
    await signIn(page, EMPLOYEE);
    await page.reload();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('button', { name: /^Compte de / })).toBeVisible();

    // The refresh token is an httpOnly cookie: present, but out of reach of the page's scripts.
    const cookies = await page.context().cookies();
    expect(cookies.find((c) => c.name === 'nexadesk_refresh')?.httpOnly).toBe(true);
    expect(await page.evaluate(() => document.cookie)).not.toContain('nexadesk_refresh');
    expect(
      await page.evaluate(() => JSON.stringify(localStorage) + JSON.stringify(sessionStorage)),
    ).not.toMatch(/eyJ/);
  });

  test('la déconnexion révoque la session', async ({ page, request }) => {
    const { EMPLOYEE } = await demoAccounts(request);
    await signIn(page, EMPLOYEE);
    await page.getByRole('button', { name: /^Compte de / }).click();
    await page.getByRole('menuitem', { name: 'Se déconnecter' }).click();
    await expect(page).toHaveURL(/\/login/);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });
});
