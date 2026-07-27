import { describe, expect, it } from 'vitest';
import { loadConfig } from './env.js';

const base = {
  DATABASE_URL: 'postgresql://user:pass@localhost:5432/db',
  JWT_SECRET: 'x'.repeat(32),
};

describe('loadConfig', () => {
  it('applies defaults', () => {
    const config = loadConfig({ ...base } as NodeJS.ProcessEnv);
    expect(config.API_PORT).toBe(3000);
    expect(config.LLM_PROVIDER).toBe('mock');
    expect(config.isProduction).toBe(false);
  });

  it('rejects a short JWT secret', () => {
    expect(() => loadConfig({ ...base, JWT_SECRET: 'short' } as NodeJS.ProcessEnv)).toThrow(
      /JWT_SECRET/,
    );
  });

  it('rejects a missing database URL', () => {
    expect(() => loadConfig({ JWT_SECRET: base.JWT_SECRET } as NodeJS.ProcessEnv)).toThrow(
      /DATABASE_URL/,
    );
  });

  it('rejects openai without a key, rather than failing mid-run', () => {
    expect(() =>
      loadConfig({ ...base, LLM_PROVIDER: 'openai' } as NodeJS.ProcessEnv),
    ).toThrow(/OPENAI_API_KEY/);
  });

  it('rejects tavily without a key', () => {
    expect(() =>
      loadConfig({ ...base, SEARCH_PROVIDER: 'tavily' } as NodeJS.ProcessEnv),
    ).toThrow(/TAVILY_API_KEY/);
  });

  it('parses the CORS origin list', () => {
    const config = loadConfig({
      ...base,
      CORS_ORIGINS: 'http://a.test, http://b.test ,',
    } as NodeJS.ProcessEnv);
    expect(config.corsOrigins).toEqual(['http://a.test', 'http://b.test']);
  });

  it('coerces numeric settings', () => {
    const config = loadConfig({
      ...base,
      API_PORT: '8080',
      DEFAULT_RUN_BUDGET_USD: '12.5',
    } as NodeJS.ProcessEnv);
    expect(config.API_PORT).toBe(8080);
    expect(config.DEFAULT_RUN_BUDGET_USD).toBe(12.5);
  });

  it('rejects an out-of-range port', () => {
    expect(() => loadConfig({ ...base, API_PORT: '70000' } as NodeJS.ProcessEnv)).toThrow();
  });

  // Docker Compose renders every unset variable as an empty string, so this is
  // the configuration the stack actually boots with by default.
  describe('empty optional variables', () => {
    const emptyEnv = {
      ...base,
      BOOTSTRAP_ADMIN_EMAIL: '',
      BOOTSTRAP_ADMIN_PASSWORD: '',
      REDIS_URL: '',
      OPENAI_API_KEY: '',
      OPENAI_BASE_URL: '',
      TAVILY_API_KEY: '',
    } as NodeJS.ProcessEnv;

    it('treats an empty string as unset', () => {
      const config = loadConfig(emptyEnv);
      expect(config.BOOTSTRAP_ADMIN_EMAIL).toBeUndefined();
      expect(config.BOOTSTRAP_ADMIN_PASSWORD).toBeUndefined();
      expect(config.REDIS_URL).toBeUndefined();
      expect(config.OPENAI_BASE_URL).toBeUndefined();
    });

    it('still rejects a genuinely malformed value', () => {
      expect(() =>
        loadConfig({ ...emptyEnv, BOOTSTRAP_ADMIN_EMAIL: 'not-an-email' } as NodeJS.ProcessEnv),
      ).toThrow(/BOOTSTRAP_ADMIN_EMAIL/);
    });

    it('still requires a key when the provider needs one', () => {
      expect(() =>
        loadConfig({ ...emptyEnv, LLM_PROVIDER: 'openai' } as NodeJS.ProcessEnv),
      ).toThrow(/OPENAI_API_KEY/);
    });
  });
});
