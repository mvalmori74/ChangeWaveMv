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
  'gpt-4o': { inputPerMillion: 2.5, outputPerMillion: 10 },
  'gpt-4o-mini': { inputPerMillion: 0.15, outputPerMillion: 0.6 },
  'gpt-4.1': { inputPerMillion: 2, outputPerMillion: 8 },
  'gpt-4.1-mini': { inputPerMillion: 0.4, outputPerMillion: 1.6 },
  'o3-mini': { inputPerMillion: 1.1, outputPerMillion: 4.4 },
};

export const FALLBACK_PRICE: ModelPrice = { inputPerMillion: 10, outputPerMillion: 30 };

export function getModelPrice(model: string): ModelPrice {
  return PRICES[model] ?? PRICES[model.replace(/-\d{4}-\d{2}-\d{2}$/, '')] ?? FALLBACK_PRICE;
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
  // Six decimals matches the Decimal(12,6) column used for storage.
  return Math.round(cost * 1_000_000) / 1_000_000;
}
