import { z } from 'zod';

/**
 * An optional setting that may legitimately arrive empty.
 *
 * Docker Compose renders an unset variable as an empty string
 * (`FOO: ${FOO:-}`), and so does a blank line in a .env, so `''` has to mean
 * "not configured" rather than "configured with an invalid value" — otherwise
 * the stack refuses to boot on its own default configuration.
 */
const optional = <T extends z.ZodTypeAny>(schema: T) =>
  z.preprocess((value) => (value === '' ? undefined : value), schema.optional());

/**
 * An on/off setting. `z.coerce.boolean()` is not usable here: it applies
 * JavaScript truthiness, so the string `'false'` would switch the flag *on*.
 */
const flag = (fallback: boolean) =>
  z.preprocess(
    (value) => (value === '' || value === undefined ? fallback : value),
    z.union([z.boolean(), z.enum(['true', 'false', '1', '0'])]).transform((value) =>
      typeof value === 'boolean' ? value : value === 'true' || value === '1',
    ),
  );

/**
 * Configuration is validated once at boot. A missing or malformed variable
 * fails the process immediately rather than surfacing as a confusing runtime
 * error halfway through a research run.
 */
const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  LOG_LEVEL: z
    .enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent'])
    .default('info'),
  API_PORT: z.coerce.number().int().min(1).max(65535).default(3000),
  API_HOST: z.string().default('0.0.0.0'),
  CORS_ORIGINS: z.string().default('http://localhost:5173'),

  DATABASE_URL: z.string().min(1),

  JWT_SECRET: z.string().min(32, 'JWT_SECRET must be at least 32 characters'),
  JWT_EXPIRES_IN: z.string().default('12h'),
  BOOTSTRAP_ADMIN_EMAIL: optional(z.string().email()),
  BOOTSTRAP_ADMIN_PASSWORD: optional(z.string().min(8)),

  REDIS_URL: optional(z.string().min(1)),

  LLM_PROVIDER: z.enum(['openai', 'anthropic', 'mock']).default('mock'),
  OPENAI_API_KEY: optional(z.string().min(1)),
  OPENAI_BASE_URL: optional(z.string().url()),
  ANTHROPIC_API_KEY: optional(z.string().min(1)),
  ANTHROPIC_BASE_URL: optional(z.string().url()),
  /**
   * Cache the agent system prompts. Off by default: a cache write costs a
   * premium over ordinary input tokens, so it only pays off when the same
   * project is researched repeatedly within the cache TTL.
   */
  ANTHROPIC_PROMPT_CACHE: flag(false),
  /** Let Anthropic re-run a declined request on its recommended fallback model. */
  ANTHROPIC_FALLBACKS: flag(true),
  /**
   * Model ids per task tier. Left unset they resolve to the defaults of the
   * active provider (`providers/llm/model-router.ts`), so switching
   * LLM_PROVIDER does not require rewriting three variables.
   */
  LLM_MODEL_FAST: optional(z.string().min(1)),
  LLM_MODEL_BALANCED: optional(z.string().min(1)),
  LLM_MODEL_DEEP: optional(z.string().min(1)),
  LLM_MAX_RETRIES: z.coerce.number().int().min(0).max(5).default(2),
  /** Upper bound on a single completion. On Claude this caps thinking too. */
  LLM_MAX_TOKENS: z.coerce.number().int().min(1024).max(200_000).default(16_000),

  SEARCH_PROVIDER: z.enum(['tavily', 'mock']).default('mock'),
  TAVILY_API_KEY: optional(z.string().min(1)),
  SEARCH_MAX_RESULTS: z.coerce.number().int().min(1).max(50).default(8),

  CODEGEN_PROVIDER: z.enum(['export', 'mock']).default('export'),

  DEFAULT_RUN_BUDGET_USD: z.coerce.number().positive().default(5),
  DEFAULT_RUN_TOKEN_BUDGET: z.coerce.number().int().positive().default(400_000),
  MAX_AGENT_RETRIES: z.coerce.number().int().min(0).max(5).default(1),
});

export type AppConfig = Readonly<z.infer<typeof envSchema>> & {
  readonly corsOrigins: string[];
  readonly isProduction: boolean;
};

export function loadConfig(source: NodeJS.ProcessEnv = process.env): AppConfig {
  const parsed = envSchema.safeParse(source);
  if (!parsed.success) {
    const details = parsed.error.issues
      .map((issue) => `  - ${issue.path.join('.')}: ${issue.message}`)
      .join('\n');
    throw new Error(`Invalid environment configuration:\n${details}`);
  }

  const config = parsed.data;

  // Fail fast on combinations that would only break once an agent runs.
  if (config.LLM_PROVIDER === 'openai' && !config.OPENAI_API_KEY) {
    throw new Error('LLM_PROVIDER=openai requires OPENAI_API_KEY to be set');
  }
  if (config.LLM_PROVIDER === 'anthropic' && !config.ANTHROPIC_API_KEY) {
    throw new Error('LLM_PROVIDER=anthropic requires ANTHROPIC_API_KEY to be set');
  }
  if (config.SEARCH_PROVIDER === 'tavily' && !config.TAVILY_API_KEY) {
    throw new Error('SEARCH_PROVIDER=tavily requires TAVILY_API_KEY to be set');
  }
  if (config.NODE_ENV === 'production' && config.LLM_PROVIDER === 'mock') {
    // Not fatal: a production deployment may legitimately run in dry-run mode,
    // but it must be a conscious choice, so it is logged loudly at boot.
    process.emitWarning(
      'LLM_PROVIDER=mock in production: agents will return synthetic, unsourced output.',
    );
  }

  return {
    ...config,
    corsOrigins: config.CORS_ORIGINS.split(',')
      .map((origin) => origin.trim())
      .filter(Boolean),
    isProduction: config.NODE_ENV === 'production',
  };
}
