import type Anthropic from '@anthropic-ai/sdk';
import { describe, expect, it } from 'vitest';
import { ProviderError } from '../../core/errors.js';
import {
  AnthropicProvider,
  splitSystemPrompt,
  type AnthropicMessagesApi,
  type AnthropicProviderOptions,
} from './anthropic.provider.js';
import { CACHE_READ_MULTIPLIER, CACHE_WRITE_MULTIPLIER, getModelPrice } from './pricing.js';

type CreateBody = Anthropic.Beta.MessageCreateParamsNonStreaming;

/**
 * Records what the provider sends and returns a canned reply, so the request
 * shaping can be asserted without a network call or an API key.
 */
function fakeClient(reply: Partial<Anthropic.Beta.BetaMessage> = {}): {
  client: AnthropicMessagesApi;
  calls: CreateBody[];
} {
  const calls: CreateBody[] = [];
  const client: AnthropicMessagesApi = {
    beta: {
      messages: {
        async create(body) {
          calls.push(body);
          return {
            id: 'msg_test',
            type: 'message',
            role: 'assistant',
            model: 'claude-sonnet-5',
            content: [{ type: 'text', text: '{"ok":true}', citations: null }],
            stop_reason: 'end_turn',
            stop_details: null,
            stop_sequence: null,
            usage: { input_tokens: 100, output_tokens: 50 },
            ...reply,
          } as Anthropic.Beta.BetaMessage;
        },
      },
    },
  };
  return { client, calls };
}

const OPTIONS: AnthropicProviderOptions = {
  apiKey: 'test-key',
  maxRetries: 0,
  serverSideFallbacks: false,
  promptCaching: false,
};

function providerWith(
  reply?: Partial<Anthropic.Beta.BetaMessage>,
  options: Partial<AnthropicProviderOptions> = {},
) {
  const { client, calls } = fakeClient(reply);
  return { provider: new AnthropicProvider({ ...OPTIONS, ...options }, client), calls };
}

const MESSAGES = [
  { role: 'system' as const, content: 'You are an analyst.' },
  { role: 'user' as const, content: 'Find opportunities.' },
];

describe('splitSystemPrompt', () => {
  it('lifts system messages out and joins them', () => {
    const { system, messages } = splitSystemPrompt([
      { role: 'system', content: 'first' },
      { role: 'system', content: 'second' },
      { role: 'user', content: 'go' },
    ]);
    expect(system).toBe('first\n\nsecond');
    expect(messages).toEqual([{ role: 'user', content: 'go' }]);
  });

  it('drops leading assistant turns so the conversation opens with a user turn', () => {
    const { messages } = splitSystemPrompt([
      { role: 'assistant', content: 'stray' },
      { role: 'user', content: 'go' },
    ]);
    expect(messages).toEqual([{ role: 'user', content: 'go' }]);
  });

  it('keeps consecutive user turns as separate messages', () => {
    // The runner sends the input and the evidence as two user turns; the API
    // merges them, so no reshaping is needed here.
    const { messages } = splitSystemPrompt([
      { role: 'user', content: 'input' },
      { role: 'user', content: 'evidence' },
    ]);
    expect(messages).toHaveLength(2);
  });
});

describe('AnthropicProvider', () => {
  it('sends the system prompt as a top-level field, not a message', async () => {
    const { provider, calls } = providerWith();
    await provider.complete({ messages: MESSAGES, model: 'claude-sonnet-5' });

    expect(calls[0]!.system).toEqual([{ type: 'text', text: 'You are an analyst.' }]);
    expect(calls[0]!.messages).toEqual([{ role: 'user', content: 'Find opportunities.' }]);
  });

  it('never sends temperature, which current Claude models reject', async () => {
    const { provider, calls } = providerWith();
    await provider.complete({ messages: MESSAGES, model: 'claude-sonnet-5', temperature: 0.2 });

    expect(calls[0]).not.toHaveProperty('temperature');
    expect(calls[0]).not.toHaveProperty('top_p');
    // Adaptive thinking is the default and configuring it explicitly can 400.
    expect(calls[0]).not.toHaveProperty('thinking');
  });

  it('maps the task tier onto reasoning effort', async () => {
    for (const [tier, effort] of [
      ['FAST', 'low'],
      ['BALANCED', 'high'],
      ['DEEP', 'xhigh'],
    ] as const) {
      const { provider, calls } = providerWith();
      await provider.complete({ messages: MESSAGES, model: 'claude-sonnet-5', tier });
      expect(calls[0]!.output_config?.effort).toBe(effort);
    }
  });

  it('forwards the JSON schema as a structured output format', async () => {
    const schema = { type: 'object', properties: { ok: { type: 'boolean' } } };
    const { provider, calls } = providerWith();
    await provider.complete({
      messages: MESSAGES,
      model: 'claude-sonnet-5',
      jsonSchema: { name: 'agent_output', schema },
    });

    expect(calls[0]!.output_config?.format).toEqual({ type: 'json_schema', schema });
  });

  it('marks the system prompt cacheable only when caching is enabled', async () => {
    const off = providerWith();
    await off.provider.complete({ messages: MESSAGES, model: 'claude-sonnet-5' });
    expect(off.calls[0]!.system).toEqual([{ type: 'text', text: 'You are an analyst.' }]);

    const on = providerWith(undefined, { promptCaching: true });
    await on.provider.complete({ messages: MESSAGES, model: 'claude-sonnet-5' });
    expect(on.calls[0]!.system).toEqual([
      { type: 'text', text: 'You are an analyst.', cache_control: { type: 'ephemeral' } },
    ]);
  });

  it('requests server-side fallbacks only when configured', async () => {
    const off = providerWith();
    await off.provider.complete({ messages: MESSAGES, model: 'claude-sonnet-5' });
    expect(off.calls[0]).not.toHaveProperty('fallbacks');

    const on = providerWith(undefined, { serverSideFallbacks: true });
    await on.provider.complete({ messages: MESSAGES, model: 'claude-sonnet-5' });
    expect(on.calls[0]!.fallbacks).toBe('default');
    expect(on.calls[0]!.betas).toContain('server-side-fallback-2026-07-01');
  });

  it('honours the configured max_tokens ceiling', async () => {
    const { provider, calls } = providerWith(undefined, { maxTokens: 4096 });
    await provider.complete({ messages: MESSAGES, model: 'claude-sonnet-5' });
    expect(calls[0]!.max_tokens).toBe(4096);
  });

  it('turns a declined request into a ProviderError naming the category', async () => {
    // A refusal is a 200 with an empty body: without this check it would
    // surface three layers up as "the response was not valid JSON".
    const { provider } = providerWith({
      content: [],
      stop_reason: 'refusal',
      stop_details: {
        type: 'refusal',
        category: 'general_harms',
        explanation: 'The brief reads as a request for harmful content.',
        recommended_model: 'claude-sonnet-4-6',
        fallback_credit_token: null,
        fallback_has_prefill_claim: null,
      },
    });

    const error = await provider
      .complete({ messages: MESSAGES, model: 'claude-sonnet-5' })
      .catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ProviderError);
    // The operator needs the category, the reason and the way out, not a
    // downstream "the response was not valid JSON".
    expect((error as ProviderError).message).toMatch(/general_harms/);
    expect((error as ProviderError).message).toMatch(/reads as a request for harmful content/);
    expect((error as ProviderError).message).toMatch(/ANTHROPIC_FALLBACKS/);
  });

  it('rejects a truncated answer instead of returning half a JSON object', async () => {
    const { provider } = providerWith({
      content: [{ type: 'text', text: '{"partial":', citations: null }],
      stop_reason: 'max_tokens',
    });

    const error = await provider
      .complete({ messages: MESSAGES, model: 'claude-sonnet-5' })
      .catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ProviderError);
    expect((error as ProviderError).message).toMatch(/max_tokens/);
  });

  it('rejects an empty response rather than returning empty content', async () => {
    const { provider } = providerWith({ content: [] });
    await expect(
      provider.complete({ messages: MESSAGES, model: 'claude-sonnet-5' }),
    ).rejects.toThrow(/no text content/);
  });

  it('refuses a request with no user turn', async () => {
    const { provider } = providerWith();
    await expect(
      provider.complete({
        messages: [{ role: 'system', content: 'only a system prompt' }],
        model: 'claude-sonnet-5',
      }),
    ).rejects.toThrow(/at least one non-system message/);
  });

  it('counts cached tokens in the prompt total and prices them separately', async () => {
    const { provider } = providerWith({
      usage: {
        input_tokens: 1_000,
        output_tokens: 500,
        cache_read_input_tokens: 10_000,
        cache_creation_input_tokens: 2_000,
      } as Anthropic.Beta.BetaUsage,
    });

    const response = await provider.complete({ messages: MESSAGES, model: 'claude-sonnet-5' });

    // The budget must see every token the request consumed, cached or not.
    expect(response.usage.promptTokens).toBe(13_000);
    expect(response.usage.completionTokens).toBe(500);

    const price = getModelPrice('claude-sonnet-5');
    const perInput = price.inputPerMillion / 1_000_000;
    const expected =
      1_000 * perInput +
      10_000 * perInput * CACHE_READ_MULTIPLIER +
      2_000 * perInput * CACHE_WRITE_MULTIPLIER +
      (500 / 1_000_000) * price.outputPerMillion;
    expect(response.usage.costUsd).toBeCloseTo(expected, 6);
    // A cache read must be cheaper than the same tokens at full input price.
    expect(response.usage.costUsd).toBeLessThan(13_000 * perInput + 500 * perInput * 5);
  });

  it('reports the model the API actually served, not the one requested', async () => {
    // With server-side fallbacks a different model may answer; the cost table
    // has to be consulted for that one.
    const { provider } = providerWith({ model: 'claude-haiku-4-5-20251001' });
    const response = await provider.complete({ messages: MESSAGES, model: 'claude-opus-5' });
    expect(response.model).toBe('claude-haiku-4-5-20251001');
  });
});
