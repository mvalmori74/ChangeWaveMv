#!/usr/bin/env node
/**
 * Post-install preparation.
 *
 * Two artefacts are generated rather than committed, and everything else
 * depends on them:
 *
 *  - the Prisma client, which `@aiaf/api` imports types from;
 *  - `packages/shared/dist`, which both apps resolve `@aiaf/shared` to.
 *
 * Without them a fresh clone cannot even type check, so they are built here:
 * after `npm install`, `npm run typecheck`, `npm test` and `npm run build` all
 * work with no further setup. Docker images install with --ignore-scripts and
 * run both steps explicitly instead.
 */
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const envFile = resolve(repoRoot, '.env');

if (existsSync(envFile)) {
  try {
    process.loadEnvFile(envFile);
  } catch {
    // A malformed .env is the config validator's problem, not this script's.
  }
}

// `prisma generate` parses the datasource block and refuses to run when
// DATABASE_URL is unset. Generation touches no database, so a placeholder is
// enough to get a fresh clone working before the user has written a .env.
const env = {
  ...process.env,
  DATABASE_URL:
    process.env['DATABASE_URL'] ?? 'postgresql://placeholder:placeholder@localhost:5432/placeholder',
};

function run(label, command, args) {
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    stdio: 'inherit',
    env,
    shell: process.platform === 'win32',
  });
  if (result.status !== 0) {
    console.error(`\nPost-install step failed: ${label}`);
    process.exit(result.status ?? 1);
  }
}

run('prisma generate', 'npx', [
  'prisma',
  'generate',
  '--schema',
  'apps/api/prisma/schema.prisma',
]);
run('build @aiaf/shared', 'npm', ['run', 'build', '--workspace', '@aiaf/shared']);
