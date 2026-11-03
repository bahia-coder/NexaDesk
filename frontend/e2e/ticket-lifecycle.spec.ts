import { Page, expect, test } from '@playwright/test';

import { demoAccounts, runId, signIn } from './support';

/** The ticket's status, as shown in its header. */
function status(page: Page) {
  return page.locator('nx-status-badge').first();
}

test('un ticket, de sa création à sa fermeture, entre l’employée et le technicien', async ({
  browser,
  request,
}) => {
  const accounts = await demoAccounts(request);
  // Two people, two browsers: each has its own session.
  const employee = await (await browser.newContext()).newPage();
  const technician = await (await browser.newContext()).newPage();
  const title = `VPN : erreur 809 en télétravail (${runId()})`;

  // 1. The employee describes the problem and files it under the technician's specialty (the AI suggestion is
  // only a help: its choice depends on the wording, so the test picks the category itself).
  await signIn(employee, accounts.EMPLOYEE);
  await employee.goto('/tickets/new');
  await employee.getByRole('button', { name: /^Incident/ }).click();
  await employee.getByLabel('Titre').fill(title);
  await employee
    .getByLabel('Description')
    .fill(
      'Depuis ce matin, le VPN affiche l’erreur 809 et je n’accède plus aux dossiers partagés.',
    );
  await expect(employee.getByRole('button', { name: 'Appliquer' })).toBeVisible();
  await employee.getByLabel('Catégorie *').selectOption({ label: 'Réseau & VPN' });
  await employee.getByRole('button', { name: 'Continuer' }).click();
  await employee.getByRole('button', { name: 'Créer le ticket' }).click();
  await expect(employee).toHaveURL(/\/tickets\/\d+$/);
  await expect(employee.getByRole('heading', { name: title })).toBeVisible();
  await expect(status(employee)).toHaveText('Nouveau');
  const ticketUrl = new URL(employee.url()).pathname;
  const bell = employee.locator('nx-notification-bell button');
  const unread = async () =>
    Number((await bell.getAttribute('aria-label'))?.match(/\d+/)?.[0] ?? 0);
  // The count is read once the page is settled (it is fetched when the live channel connects).
  await employee.waitForLoadState('networkidle');
  const unreadBefore = await unread();

  // 2. The technician of the specialty takes it, works on it and resolves it.
  await signIn(technician, accounts.TECHNICIAN);
  await technician.goto(ticketUrl);
  await technician.getByRole('button', { name: 'Prendre en charge' }).click();
  await expect(status(technician)).toHaveText('Assigné');
  await technician.getByRole('button', { name: 'Démarrer' }).click();
  await expect(status(technician)).toHaveText('En cours');
  await technician.getByRole('button', { name: 'Résoudre' }).click();
  await technician
    .getByRole('dialog')
    .getByLabel('Solution appliquée')
    .fill('Certificat de la passerelle VPN renouvelé ; connexion vérifiée avec l’utilisatrice.');
  await technician.getByRole('dialog').getByRole('button', { name: 'Valider' }).click();
  await expect(status(technician)).toHaveText('Résolu');

  // 3. Without reloading, the employee's page follows (WebSocket): status, solution and notifications.
  await expect(status(employee)).toHaveText('Résolu');
  await expect(employee.getByText('Certificat de la passerelle VPN renouvelé')).toBeVisible();
  // At least "pris en charge" and "résolu" (the API may add others, such as the start of the work).
  await expect.poll(unread).toBeGreaterThanOrEqual(unreadBefore + 2);

  // 4. She confirms: the ticket is closed and can be rated.
  await employee.getByRole('button', { name: 'Confirmer la résolution' }).click();
  await employee.getByRole('dialog').getByRole('button', { name: 'Oui, c’est résolu' }).click();
  await expect(status(employee)).toHaveText('Fermé');
  await expect(employee.getByText('Votre satisfaction')).toBeVisible();

  // The technician no longer has any action on a closed ticket.
  await technician.reload();
  await expect(status(technician)).toHaveText('Fermé');
  await expect(technician.getByRole('button', { name: 'Résoudre' })).toHaveCount(0);
});
