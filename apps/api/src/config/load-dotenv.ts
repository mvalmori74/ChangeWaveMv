import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * Loads a local .env for development, using Node's built-in loader so the
 * project needs no dotenv dependency.
 *
 * Containers and CI inject real environment variables, so a missing file is not
 * an error. Existing variables always win: an explicit export must never be
 * silently overridden by a stale file.
 */
export function loadDotEnv(candidates: string[] = ['.env', '../../.env']): void {
  for (const candidate of candidates) {
    const path = resolve(process.cwd(), candidate);
    if (!existsSync(path)) continue;
    try {
      process.loadEnvFile(path);
    } catch {
      // A malformed .env should not take the process down before the config
      // validator has had a chance to report what is actually missing.
    }
    return;
  }
}
