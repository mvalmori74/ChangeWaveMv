import { describe, expect, it } from 'vitest';
import { estimateCostUsd, FALLBACK_PRICE, getModelPrice } from './pricing.js';

describe('pricing', () => {
  it('prices a known model', () => {
    // 1M in + 1M out on gpt-4o-mini = 0.15 + 0.60
    expect(estimateCostUsd('gpt-4o-mini', 1_000_000, 1_000_000)).toBeCloseTo(0.75, 6);
  });

  it('strips a dated model suffix before looking up the price', () => {
    expect(getModelPrice('gpt-4o-2024-08-06')).toEqual(getModelPrice('gpt-4o'));
  });

  it('falls back to a pessimistic price for an unknown model', () => {
    // An unmapped model must never look free, or budgets stop protecting us.
    expect(getModelPrice('some-new-model')).toEqual(FALLBACK_PRICE);
    expect(estimateCostUsd('some-new-model', 1000, 1000)).toBeGreaterThan(0);
  });

  it('rounds to the six decimals the cost column stores', () => {
    const cost = estimateCostUsd('gpt-4o-mini', 1, 1);
    expect(cost).toBe(Math.round(cost * 1_000_000) / 1_000_000);
  });

  it('is zero for zero usage', () => {
    expect(estimateCostUsd('gpt-4o', 0, 0)).toBe(0);
  });
});
