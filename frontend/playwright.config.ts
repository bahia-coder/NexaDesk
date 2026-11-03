import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests against a running NexaDesk with the demonstration data (profile "demo"):
 * by default the Docker stack (`docker compose up -d`, http://localhost:8088). For the dev servers:
 * E2E_BASE_URL=http://localhost:4200.
 *
 * The installed Microsoft Edge is used (no browser download); E2E_CHANNEL= (empty) uses Playwright's own
 * Chromium instead, e.g. on a CI runner after `npx playwright install chromium`.
 */
const channel = process.env['E2E_CHANNEL'] ?? 'msedge';

export default defineConfig({
  testDir: './e2e',
  timeout: 45_000,
  expect: { timeout: 10_000 },
  // The scenarios share one database: run them one after the other.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env['CI'],
  reporter: [['list']],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:8088',
    channel: channel || undefined,
    locale: 'fr-FR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
