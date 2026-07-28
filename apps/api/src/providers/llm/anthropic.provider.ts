import Anthropic from '@anthropic-ai/sdk';
import type { ModelTier } from '@aiaf/shared';
import { ProviderError } from '../../core/errors.js';
import { estimateCacheAwareCostUsd } from './pricing.js';
import type { LlmCompletionRequest, LlmCompletionResponse, LlmProvider } from './types.js';

/**
 * How a task tier maps onto Claude's `effort` parameter.
 *
 * This is the one place the Anthropic provider does something the OpenAI one
 * cannot: effort varies reasoning depth *independently of the model*, so a
 * cheap tier is not forced onto a weaker model. The research agents run at
 * `high`; the synthesis and scoring agents, which is where a wrong answer
 * propagates furthest, run at `xhigh`.
 */
const EFFORT_BY_TIER: Record<ModelTier, 'low' | 'medium' | 'high' | 'xhigh' | 'max'> = {
  FAST: 'low',
  BALANCED: 'high',
  DEEP: 'xhigh',
};

/**
 * Agent answers are JSON objects of a few thousand tokens at most, but
 * `max_tokens` caps thinking *and* response text together on current models —
 * so this leaves room for the model to reason before it answers.
 */
const DEFAULT_MAX_TOKENS = 16_000;

/** The beta that enables `fallbacks: 'default'`. */
const SERVER_SIDE_FALLBACK_BETA = 'server-side-fallback-2026-07-01';

export interface AnthropicProviderOptions {
  apiKey: string;
  baseURL?: string;
  maxRetries: number;
  /** Ceiling for a single completion; caps thinking and answer together. */
  maxTokens?: number;
  /**
   * Re-run a request that safety classifiers decline on Anthropic's
   * recommended fallback model, server-side, in the same call.
   */
  serverSideFallbacks: boolean;
  /**
   * Mark the system prompt cacheable. Off by default: a cache write costs a
   * premium and each agent's prompt is only re-read on a validation retry or a
   * repeat run within the cache TTL, so this pays off for frequent runs of the
   * same project and costs a little extra for occasional ones.
   */
  promptCaching: boolean;
}

/** The slice of the SDK this provider uses, so tests can substitute a double. */
export interface AnthropicMessagesApi {
  beta: {
    messages: {
      create(body: Anthropic.Beta.MessageCreateParamsNonStreaming): Promise<Anthropic.Beta.BetaMessage>;
    };
  };
}

export class AnthropicProvider implements LlmProvider {
  readonly name = 'anthropic';
  private readonly client: AnthropicMessagesApi;

  constructor(
    private readonly options: AnthropicProviderOptions,
    client?: AnthropicMessagesApi,
  ) {
    this.client =
      client ??
      new Anthropic({
        apiKey: options.apiKey,
        ...(options.baseURL ? { baseURL: options.baseURL } : {}),
        maxRetries: options.maxRetries,
      });
  }

  async complete(request: LlmCompletionRequest): Promise<LlmCompletionResponse> {
    const { system, messages } = splitSystemPrompt(request.messages);
    if (messages.length === 0) {
      throw new ProviderError('Anthropic requires at least one non-system message', this.name);
    }

    let response: Anthropic.Beta.BetaMessage;
    try {
      response = await this.client.beta.messages.create({
        model: request.model,
        max_tokens: request.maxTokens ?? this.options.maxTokens ?? DEFAULT_MAX_TOKENS,
        // `temperature` is deliberately not forwarded: current Claude models
        // reject sampling parameters with a 400. Determinism comes from the
        // schema plus the low `effort` tier, not from temperature.
        ...(system.length > 0
          ? {
              system: [
                {
                  type: 'text' as const,
                  text: system,
                  ...(this.options.promptCaching
                    ? { cache_control: { type: 'ephemeral' as const } }
                    : {}),
                },
              ],
            }
          : {}),
        messages,
        output_config: {
          effort: EFFORT_BY_TIER[request.tier ?? 'BALANCED'],
          ...(request.jsonSchema ? { format: { type: 'json_schema' as const, schema: request.jsonSchema.schema } } : {}),
        },
        // No `thinking` parameter: current models think adaptively by default,
        // which is what the agents want. Configuring it explicitly is either a
        // no-op or, on some models, a 400.
        ...(this.options.serverSideFallbacks
          ? { fallbacks: 'default' as const, betas: [SERVER_SIDE_FALLBACK_BETA] }
          : {}),
      });
    } catch (error) {
      throw this.toProviderError(error);
    }

    // A declined request is a successful HTTP response with an empty body, so
    // this has to be checked before reading content — otherwise it surfaces as
    // a confusing "response was not valid JSON" three layers up.
    if (response.stop_reason === 'refusal') {
      const category = response.stop_details?.category ?? 'unspecified';
      const explanation = response.stop_details?.explanation;
      const recommended = response.stop_details?.recommended_model;
      throw new ProviderError(
        [
          `Anthropic declined this request (policy category: ${category}).`,
          explanation,
          recommended
            ? `A retry on ${recommended} may succeed; set ANTHROPIC_FALLBACKS=true to have that happen automatically.`
            : 'Rephrase the research brief.',
        ]
          .filter(Boolean)
          .join(' '),
        this.name,
        { stopReason: response.stop_reason, category },
      );
    }

    if (response.stop_reason === 'max_tokens') {
      throw new ProviderError(
        'Anthropic hit max_tokens before finishing; the JSON answer is truncated. ' +
          'Raise LLM_MAX_TOKENS or lower the agent model tier.',
        this.name,
        { stopReason: response.stop_reason },
      );
    }

    const text = response.content
      .filter((block): block is Anthropic.Beta.BetaTextBlock => block.type === 'text')
      .map((block) => block.text)
      .join('');

    if (text.length === 0) {
      throw new ProviderError('Anthropic returned no text content', this.name, {
        stopReason: response.stop_reason,
      });
    }

    const cacheReadTokens = response.usage.cache_read_input_tokens ?? 0;
    const cacheWriteTokens = response.usage.cache_creation_input_tokens ?? 0;

    return {
      content: text,
      model: response.model,
      finishReason: response.stop_reason ?? 'end_turn',
      usage: {
        // Reported so the run's token budget counts everything the request
        // actually consumed, cached or not.
        promptTokens: response.usage.input_tokens + cacheReadTokens + cacheWriteTokens,
        completionTokens: response.usage.output_tokens,
        costUsd: estimateCacheAwareCostUsd(response.model, {
          inputTokens: response.usage.input_tokens,
          outputTokens: response.usage.output_tokens,
          cacheReadTokens,
          cacheWriteTokens,
        }),
      },
    };
  }

  /** ~4 characters per token is accurate enough for pre-flight budget checks. */
  estimateTokens(text: string): number {
    return Math.ceil(text.length / 4);
  }

  private toProviderError(error: unknown): ProviderError {
    if (error instanceof Anthropic.RateLimitError) {
      return new ProviderError('Anthropic rate limit reached', this.name, { status: 429 });
    }
    if (error instanceof Anthropic.AuthenticationError) {
      return new ProviderError('Anthropic rejected the API key', this.name, { status: 401 });
    }
    if (error instanceof Anthropic.APIError) {
      return new ProviderError(`Anthropic request failed: ${error.message}`, this.name, {
        status: error.status,
      });
    }
    return new ProviderError(
      `Anthropic request failed: ${error instanceof Error ? error.message : 'unknown error'}`,
      this.name,
    );
  }
}

/**
 * Claude takes the system prompt as a top-level field rather than a message,
 * so the runner's leading system message is lifted out here. Consecutive
 * same-role messages are accepted by the API and merged into one turn, so the
 * runner's two user messages (input, then evidence) need no reshaping.
 */
export function splitSystemPrompt(messages: LlmCompletionRequest['messages']): {
  system: string;
  messages: Anthropic.Beta.BetaMessageParam[];
} {
  const systemParts: string[] = [];
  const rest: Anthropic.Beta.BetaMessageParam[] = [];

  for (const message of messages) {
    if (message.role === 'system') {
      systemParts.push(message.content);
      continue;
    }
    rest.push({ role: message.role, content: message.content });
  }

  // A conversation must open with a user turn; the runner always satisfies
  // this, but a malformed caller would otherwise get an opaque 400.
  while (rest.length > 0 && rest[0]!.role !== 'user') rest.shift();

  return { system: systemParts.join('\n\n'), messages: rest };
}
