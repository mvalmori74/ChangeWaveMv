import { describe, expect, it } from 'vitest';
import { MockLlmProvider, synthesiseFromSchema } from './mock.provider.js';

describe('synthesiseFromSchema', () => {
  it('builds the required properties of an object', () => {
    expect(
      synthesiseFromSchema({
        type: 'object',
        properties: { a: { type: 'string' }, b: { type: 'number' }, c: { type: 'boolean' } },
        required: ['a', 'b'],
      }),
    ).toEqual({ a: 'mock', b: 0 });
  });

  it('honours minItems on arrays', () => {
    expect(
      synthesiseFromSchema({ type: 'array', items: { type: 'string' }, minItems: 2 }),
    ).toEqual(['mock', 'mock']);
  });

  it('picks the first enum value', () => {
    expect(synthesiseFromSchema({ type: 'string', enum: ['ALPHA', 'BETA'] })).toBe('ALPHA');
  });

  it('respects a numeric minimum', () => {
    expect(synthesiseFromSchema({ type: 'number', minimum: 5 })).toBe(5);
  });
});

describe('MockLlmProvider', () => {
  it('uses the registered fixture for the requested schema', async () => {
    const provider = new MockLlmProvider();
    provider.register('my_agent', ({ input }) => ({ echoed: input }));

    const response = await provider.complete({
      model: 'gpt-4o',
      messages: [{ role: 'user', content: '{"sector":"logistics"}' }],
      jsonSchema: { name: 'my_agent', schema: { type: 'object' } },
    });

    expect(JSON.parse(response.content)).toEqual({ echoed: { sector: 'logistics' } });
    expect(response.model).toBe('mock:gpt-4o');
  });

  it('falls back to schema synthesis for an unregistered agent', async () => {
    const provider = new MockLlmProvider();

    const response = await provider.complete({
      model: 'gpt-4o',
      messages: [{ role: 'user', content: '{}' }],
      jsonSchema: {
        name: 'unknown_agent',
        schema: { type: 'object', properties: { title: { type: 'string' } }, required: ['title'] },
      },
    });

    expect(JSON.parse(response.content)).toEqual({ title: 'mock' });
  });

  it('reports the cost the same call would have had against the real model', async () => {
    const provider = new MockLlmProvider();
    const response = await provider.complete({
      model: 'gpt-4o',
      messages: [{ role: 'user', content: 'x'.repeat(4000) }],
    });

    // Non-zero so budget behaviour can be exercised offline.
    expect(response.usage.costUsd).toBeGreaterThan(0);
    expect(response.usage.promptTokens).toBeGreaterThan(0);
  });

  it('passes the raw text through when the user message is not JSON', async () => {
    const provider = new MockLlmProvider();
    provider.register('a', ({ input }) => ({ input }));

    const response = await provider.complete({
      model: 'gpt-4o',
      messages: [{ role: 'user', content: 'plain text' }],
      jsonSchema: { name: 'a', schema: {} },
    });

    expect(JSON.parse(response.content)).toEqual({ input: 'plain text' });
  });
});
