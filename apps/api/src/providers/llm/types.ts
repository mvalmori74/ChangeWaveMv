import type { ModelTier } from '@aiaf/shared';

export interface LlmMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface LlmUsage {
  promptTokens: number;
  completionTokens: number;
  /** Computed by the provider from its own price table. */
  costUsd: number;
}

export interface LlmCompletionRequest {
  messages: LlmMessage[];
  /** Concrete model id. Resolved from a tier by the ModelRouter. */
  model: string;
  temperature?: number;
  maxTokens?: number;
  /**
   * When set the provider must return JSON matching this JSON Schema.
   * Providers that cannot enforce it natively fall back to prompt instructions
   * plus parse-and-retry; either way the caller validates with zod.
   */
  jsonSchema?: { name: string; schema: Record<string, unknown> };
  /** Aborts an in-flight call when a run is cancelled or times out. */
  signal?: AbortSignal;
}

export interface LlmCompletionResponse {
  content: string;
  model: string;
  usage: LlmUsage;
  finishReason: string;
}

/**
 * Every LLM backend implements this. Nothing above this layer knows about
 * OpenAI: swapping in another vendor means adding one file.
 */
export interface LlmProvider {
  readonly name: string;
  complete(request: LlmCompletionRequest): Promise<LlmCompletionResponse>;
  /** Rough token estimate used for pre-flight budget checks. */
  estimateTokens(text: string): number;
}

export interface ModelRouter {
  resolve(tier: ModelTier): string;
}
