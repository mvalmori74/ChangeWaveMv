import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    // Integration tests that need a live database opt in explicitly via
    // DATABASE_TEST_URL; the default suite runs with no external services.
    setupFiles: ['src/test/setup.ts'],
    pool: 'threads',
  },
});
