import { expect, test } from '@playwright/test';

const EMAIL = process.env['E2E_EMAIL'] ?? 'admin@example.com';
const PASSWORD = process.env['E2E_PASSWORD'] ?? 'changeme123';

async function signIn(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/');
  await page.getByLabel('Email').fill(EMAIL);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
}

test('signs in and shows the dashboard', async ({ page }) => {
  await signIn(page);
  await expect(page.getByText('Opportunities analysed')).toBeVisible();
  await expect(page.getByText('Research spend')).toBeVisible();
});

test('navigates the main sections', async ({ page }) => {
  await signIn(page);

  await page.getByRole('link', { name: 'Research' }).click();
  await expect(page.getByRole('heading', { name: 'Research projects' })).toBeVisible();

  await page.getByRole('link', { name: 'Opportunities' }).click();
  await expect(page.getByRole('heading', { name: 'Market opportunities' })).toBeVisible();

  await page.getByRole('link', { name: 'Pipeline' }).click();
  await expect(page.getByRole('heading', { name: 'Project pipeline' })).toBeVisible();

  await page.getByRole('link', { name: 'Agents' }).click();
  await expect(page.getByRole('heading', { name: 'Agent registry' })).toBeVisible();
  // Every built-in agent is listed by the registry, none hard-coded in the UI.
  await expect(page.getByText('trend-hunter', { exact: false }).first()).toBeVisible();
});

test('creates a research project', async ({ page }) => {
  await signIn(page);
  await page.getByRole('link', { name: 'Research' }).click();
  await page.getByRole('button', { name: 'New project' }).click();

  const suffix = Date.now();
  await page.getByLabel('Project name').fill(`E2E project ${suffix}`);
  await page.getByLabel('Sector').fill('fleet maintenance');
  await page.getByLabel('Country').fill('Italy');
  await page.getByRole('button', { name: 'Create' }).click();

  await expect(page.getByRole('heading', { name: `E2E project ${suffix}` })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Start research run' })).toBeVisible();
});
