/**
 * USD price per 1M tokens. Vendor pricing changes over time, so this table is
 * a *best effort* used for budget enforcement and cost reporting, not billing.
 * Unknown models fall back to a deliberately pessimistic default so an
 * unmapped model can never silently look free.
 */
export interface ModelPrice {
  readonly inputPerMillion: number;
  readonly outputPerMillion: number;
}

const PRICES: Record<string, ModelPrice> = {
  // OpenAI
  'gpt-4o': { inputPerMillion: 2.5, outputPerMillion: 10 },
  'gpt-4o-mini': { inputPerMillion: 0.15, outputPerMillion: 0.6 },
  'gpt-4.1': { inputPerMillion: 2, outputPerMillion: 8 },
  'gpt-4.1-mini': { inputPerMillion: 0.4, outputPerMillion: 1.6 },
  'o3-mini': { inputPerMillion: 1.1, outputPerMillion: 4.4 },

  // Anthropic. Sonnet 5 carries a lower introductory rate for a limited
  // period; the standard rate is used here so a budget can never be
  // under-estimated when the promotion ends.
  'claude-fable-5': { inputPerMillion: 10, outputPerMillion: 50 },
  'claude-opus-5': { inputPerMillion: 5, outputPerMillion: 25 },
  'claude-opus-4-8': { inputPerMillion: 5, outputPerMillion: 25 },
  'claude-opus-4-7': { inputPerMillion: 5, outputPerMillion: 25 },
  'claude-sonnet-5': { inputPerMillion: 3, outputPerMillion: 15 },
  'claude-sonnet-4-6': { inputPerMillion: 3, outputPerMillion: 15 },
  'claude-haiku-4-5': { inputPerMillion: 1, outputPerMillion: 5 },
};

export const FALLBACK_PRICE: ModelPrice = { inputPerMillion: 10, outputPerMillion: 30 };

/**
 * Both vendors publish dated model ids — `gpt-4o-2024-08-06`,
 * `claude-haiku-4-5-20251001` — that price identically to the undated family.
 */
const DATE_SUFFIX = /-(?:\d{4}-\d{2}-\d{2}|\d{8})$/;

export function getModelPrice(model: string): ModelPrice {
  return PRICES[model] ?? PRICES[model.replace(DATE_SUFFIX, '')] ?? FALLBACK_PRICE;
}

export function estimateCostUsd(
  model: string,
  promptTokens: number,
  completionTokens: number,
): number {
  const price = getModelPrice(model);
  const cost =
    (promptTokens / 1_000_000) * price.inputPerMillion +
    (completionTokens / 1_000_000) * price.outputPerMillion;
  return roundToStoredPrecision(cost);
}

/** Multipliers applied to the base input price for cached tokens. */
export const CACHE_READ_MULTIPLIER = 0.1;
export const CACHE_WRITE_MULTIPLIER = 1.25;

export interface CacheAwareUsage {
  /** Tokens processed at full input price (neither read from nor written to cache). */
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens?: number;
  cacheWriteTokens?: number;
}

/**
 * Cost for providers that report cache activity separately.
 *
 * Cached reads bill at a fraction of the input price and cache writes at a
 * premium, so counting them as ordinary input tokens would misreport the spend
 * in both directions and make the run budget meaningless.
 */
export function estimateCacheAwareCostUsd(model: string, usage: CacheAwareUsage): number {
  const price = getModelPrice(model);
  const perInputToken = price.inputPerMillion / 1_000_000;
  const cost =
    usage.inputTokens * perInputToken +
    (usage.cacheReadTokens ?? 0) * perInputToken * CACHE_READ_MULTIPLIER +
    (usage.cacheWriteTokens ?? 0) * perInputToken * CACHE_WRITE_MULTIPLIER +
    (usage.outputTokens / 1_000_000) * price.outputPerMillion;
  return roundToStoredPrecision(cost);
}

/** Six decimals matches the Decimal(12,6) column used for storage. */
function roundToStoredPrecision(value: number): number {
  return Math.round(value * 1_000_000) / 1_000_000;
}
