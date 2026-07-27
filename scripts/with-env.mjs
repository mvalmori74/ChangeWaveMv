#!/usr/bin/env node
/**
 * Runs a command with the repository .env loaded.
 *
 * The Prisma CLI only looks for a .env next to the schema or in the current
 * working directory, but this is a monorepo with a single .env at the root.
 * Rather than asking everyone to keep two copies in sync, the db:* scripts go
 * through here. Real environment variables always win, and a missing file is
 * fine — containers and CI inject the variables directly.
 *
 * Usage: node scripts/with-env.mjs <command> [args...]
 */
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const envFile = resolve(repoRoot, '.env');

if (existsSync(envFile)) {
  try {
    process.loadEnvFile(envFile);
  } catch (error) {
    console.warn(`Could not read ${envFile}: ${error.message}`);
  }
}

const [command, ...args] = process.argv.slice(2);
if (!command) {
  console.error('Usage: node scripts/with-env.mjs <command> [args...]');
  process.exit(1);
}

const child = spawn(command, args, { stdio: 'inherit', shell: process.platform === 'win32' });
child.on('exit', (code, signal) => process.exit(signal ? 1 : (code ?? 0)));
child.on('error', (error) => {
  console.error(`Failed to run ${command}: ${error.message}`);
  process.exit(1);
});
