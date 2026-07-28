import type { ModelTier } from '@aiaf/shared';
import type { AppConfig } from '../../config/env.js';
import type { ModelRouter } from './types.js';

/**
 * Sensible model per tier for each backend.
 *
 * Cost control rule from the spec: cheap models for mechanical tasks, strong
 * models for synthesis and scoring. These are only the defaults — `LLM_MODEL_*`
 * overrides any of them — but they mean that switching `LLM_PROVIDER` alone
 * produces a working configuration instead of sending OpenAI model ids to
 * Anthropic.
 *
 * `mock` keeps the OpenAI ids so the offline provider's cost simulation stays
 * comparable to a real OpenAI run.
 */
const DEFAULTS: Record<AppConfig['LLM_PROVIDER'], Record<ModelTier, string>> = {
  openai: {
    FAST: 'gpt-4o-mini',
    BALANCED: 'gpt-4o',
    DEEP: 'gpt-4o',
  },
  anthropic: {
    FAST: 'claude-haiku-4-5-20251001',
    BALANCED: 'claude-sonnet-5',
    DEEP: 'claude-opus-5',
  },
  mock: {
    FAST: 'gpt-4o-mini',
    BALANCED: 'gpt-4o',
    DEEP: 'gpt-4o',
  },
};

/**
 * Maps an agent's declared task tier to a concrete model id. Agents declare a
 * tier, never a model id, so the whole fleet can be re-pointed from
 * configuration.
 */
export class ConfigModelRouter implements ModelRouter {
  constructor(private readonly config: AppConfig) {}

  resolve(tier: ModelTier): string {
    const defaults = DEFAULTS[this.config.LLM_PROVIDER];
    switch (tier) {
      case 'FAST':
        return this.config.LLM_MODEL_FAST ?? defaults.FAST;
      case 'DEEP':
        return this.config.LLM_MODEL_DEEP ?? defaults.DEEP;
      case 'BALANCED':
      default:
        return this.config.LLM_MODEL_BALANCED ?? defaults.BALANCED;
    }
  }
}

export { DEFAULTS as DEFAULT_MODELS_BY_PROVIDER };
