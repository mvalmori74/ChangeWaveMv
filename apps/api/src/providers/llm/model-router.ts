import type { ModelTier } from '@aiaf/shared';
import type { AppConfig } from '../../config/env.js';
import type { ModelRouter } from './types.js';

/**
 * Maps an agent's declared task tier to a concrete model id.
 *
 * Cost control rule from the spec: cheap models for mechanical tasks, strong
 * models for synthesis and scoring. Agents declare a tier, never a model id, so
 * the whole fleet can be re-pointed from configuration.
 */
export class ConfigModelRouter implements ModelRouter {
  constructor(private readonly config: AppConfig) {}

  resolve(tier: ModelTier): string {
    switch (tier) {
      case 'FAST':
        return this.config.LLM_MODEL_FAST;
      case 'DEEP':
        return this.config.LLM_MODEL_DEEP;
      case 'BALANCED':
      default:
        return this.config.LLM_MODEL_BALANCED;
    }
  }
}
