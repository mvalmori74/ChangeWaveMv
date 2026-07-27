import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end smoke tests.
 *
 * They expect an API on :3000 with a seeded account. Start the stack with
 * `docker compose up` (or `npm run dev` plus a local Postgres) and then run
 * `npm run test:e2e`.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    // Use a preinstalled Chromium when the environment provides one (CI images
    // often pin a build that does not match Playwright's expected revision).
    ...(process.env['CHROMIUM_PATH']
      ? { launchOptions: { executablePath: process.env['CHROMIUM_PATH'] } }
      : {}),
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: process.env['E2E_BASE_URL']
    ? undefined
    : {
        command: 'npm run dev',
        url: 'http://localhost:5173',
        reuseExistingServer: true,
        timeout: 60_000,
      },
});
