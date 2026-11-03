import { APIRequestContext, Page, expect } from '@playwright/test';

export type Role = 'EMPLOYEE' | 'TECHNICIAN' | 'ADMIN';

export interface DemoAccount {
  role: Role;
  label: string;
  email: string;
  password: string;
}

/**
 * The demonstration accounts, as the API publishes them (profile "demo" only): no credential is written in the
 * tests, and they keep working if the demo passwords change.
 */
export async function demoAccounts(request: APIRequestContext): Promise<Record<Role, DemoAccount>> {
  const response = await request.get('/api/auth/demo-accounts');
  expect(response.ok(), 'the API must run with the "demo" profile').toBeTruthy();
  const accounts = (await response.json()) as DemoAccount[];
  expect(accounts.length, 'the API must run with the "demo" profile').toBeGreaterThan(0);
  return Object.fromEntries(accounts.map((a) => [a.role, a])) as Record<Role, DemoAccount>;
}

/** Signs in through the form, like a user, and waits for the page the user lands on. */
export async function signIn(
  page: Page,
  account: DemoAccount,
  expectedPath = /\/dashboard$/,
): Promise<void> {
  if (!page.url().includes('/login')) {
    await page.goto('/login');
  }
  await page.getByLabel('E-mail').fill(account.email);
  await page.getByLabel('Mot de passe', { exact: true }).fill(account.password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(expectedPath);
}

/** A short unique suffix, so each run creates its own tickets in the shared demo database. */
export function runId(): string {
  return Date.now().toString(36).slice(-6);
}
