import OpenAI from 'openai';
import { ProviderError } from '../../core/errors.js';
import { estimateCostUsd } from './pricing.js';
import type { LlmCompletionRequest, LlmCompletionResponse, LlmProvider } from './types.js';

export interface OpenAiProviderOptions {
  apiKey: string;
  baseURL?: string;
  maxRetries: number;
}

export class OpenAiProvider implements LlmProvider {
  readonly name = 'openai';
  private readonly client: OpenAI;

  constructor(options: OpenAiProviderOptions) {
    this.client = new OpenAI({
      apiKey: options.apiKey,
      ...(options.baseURL ? { baseURL: options.baseURL } : {}),
      maxRetries: options.maxRetries,
    });
  }

  async complete(request: LlmCompletionRequest): Promise<LlmCompletionResponse> {
    try {
      const response = await this.client.chat.completions.create(
        {
          model: request.model,
          messages: request.messages,
          temperature: request.temperature ?? 0.2,
          ...(request.maxTokens ? { max_tokens: request.maxTokens } : {}),
          ...(request.jsonSchema
            ? {
                response_format: {
                  type: 'json_schema' as const,
                  json_schema: {
                    name: request.jsonSchema.name,
                    schema: request.jsonSchema.schema,
                    strict: false,
                  },
                },
              }
            : {}),
        },
        request.signal ? { signal: request.signal } : {},
      );

      const choice = response.choices[0];
      if (!choice?.message.content) {
        throw new ProviderError('OpenAI returned an empty completion', this.name, {
          finishReason: choice?.finish_reason,
        });
      }

      const promptTokens = response.usage?.prompt_tokens ?? 0;
      const completionTokens = response.usage?.completion_tokens ?? 0;

      return {
        content: choice.message.content,
        model: response.model,
        finishReason: choice.finish_reason ?? 'stop',
        usage: {
          promptTokens,
          completionTokens,
          costUsd: estimateCostUsd(response.model, promptTokens, completionTokens),
        },
      };
    } catch (error) {
      if (error instanceof ProviderError) throw error;
      throw new ProviderError(
        `OpenAI request failed: ${error instanceof Error ? error.message : 'unknown error'}`,
        this.name,
      );
    }
  }

  /** ~4 characters per token is accurate enough for pre-flight budget checks. */
  estimateTokens(text: string): number {
    return Math.ceil(text.length / 4);
  }
}
