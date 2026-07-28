import { describe, expect, it } from 'vitest';
import type { AppConfig } from '../../config/env.js';
import { ConfigModelRouter } from './model-router.js';
import { getModelPrice, FALLBACK_PRICE } from './pricing.js';

const configWith = (overrides: Partial<AppConfig>): AppConfig =>
  ({ LLM_PROVIDER: 'openai', ...overrides }) as AppConfig;

describe('ConfigModelRouter', () => {
  it('uses the openai defaults, cheap tier on the cheap model', () => {
    const router = new ConfigModelRouter(configWith({ LLM_PROVIDER: 'openai' }));
    expect(router.resolve('FAST')).toBe('gpt-4o-mini');
    expect(router.resolve('BALANCED')).toBe('gpt-4o');
    expect(router.resolve('DEEP')).toBe('gpt-4o');
  });

  it('uses Claude ids when the provider is anthropic', () => {
    // Switching provider alone must produce a working configuration; sending
    // an OpenAI model id to Anthropic would 404 on the first agent.
    const router = new ConfigModelRouter(configWith({ LLM_PROVIDER: 'anthropic' }));
    expect(router.resolve('FAST')).toMatch(/^claude-/);
    expect(router.resolve('BALANCED')).toMatch(/^claude-/);
    expect(router.resolve('DEEP')).toMatch(/^claude-/);
  });

  it('prices every default model it can hand out', () => {
    // A default that is missing from the price table would be billed at the
    // pessimistic fallback and quietly distort every budget.
    for (const provider of ['openai', 'anthropic', 'mock'] as const) {
      const router = new ConfigModelRouter(configWith({ LLM_PROVIDER: provider }));
      for (const tier of ['FAST', 'BALANCED', 'DEEP'] as const) {
        expect(getModelPrice(router.resolve(tier))).not.toEqual(FALLBACK_PRICE);
      }
    }
  });

  it('lets an explicit model id override the default', () => {
    const router = new ConfigModelRouter(
      configWith({ LLM_PROVIDER: 'anthropic', LLM_MODEL_DEEP: 'claude-opus-4-8' }),
    );
    expect(router.resolve('DEEP')).toBe('claude-opus-4-8');
    expect(router.resolve('FAST')).toMatch(/^claude-/);
  });
});
