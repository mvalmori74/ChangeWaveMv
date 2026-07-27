import { estimateCostUsd } from './pricing.js';
import type { LlmCompletionRequest, LlmCompletionResponse, LlmProvider } from './types.js';

export interface MockFixtureContext {
  input: unknown;
  model: string;
}

export type MockFixture = (context: MockFixtureContext) => unknown;

/**
 * Deterministic offline stand-in for a real model.
 *
 * It exists so the platform can boot, run a full pipeline and be tested with no
 * API key and no network. Its output is *synthetic*: every agent fixture marks
 * its claims as HYPOTHESIS and returns no sources, so synthetic runs can never
 * be mistaken for researched findings in the UI.
 *
 * Fixtures are registered by the agent registry, keyed by the JSON-schema name
 * the agent asks for. Anything unregistered falls back to synthesising a
 * minimal object from the requested JSON Schema, so a newly added agent still
 * runs offline without touching this file.
 */
export class MockLlmProvider implements LlmProvider {
  readonly name = 'mock';
  private readonly fixtures = new Map<string, MockFixture>();

  register(schemaName: string, fixture: MockFixture): void {
    this.fixtures.set(schemaName, fixture);
  }

  async complete(request: LlmCompletionRequest): Promise<LlmCompletionResponse> {
    const schemaName = request.jsonSchema?.name;
    const input = parseLastUserMessage(request.messages);
    const fixture = schemaName ? this.fixtures.get(schemaName) : undefined;

    const payload = fixture
      ? fixture({ input, model: request.model })
      : request.jsonSchema
        ? synthesiseFromSchema(request.jsonSchema.schema)
        : { note: 'mock provider: no schema requested' };

    const content = JSON.stringify(payload);
    const promptTokens = this.estimateTokens(request.messages.map((m) => m.content).join('\n'));
    const completionTokens = this.estimateTokens(content);

    return {
      content,
      model: `mock:${request.model}`,
      finishReason: 'stop',
      usage: {
        promptTokens,
        completionTokens,
        // Reported as the cost the same call would have had against the real
        // model, so budget behaviour can be exercised offline.
        costUsd: estimateCostUsd(request.model, promptTokens, completionTokens),
      },
    };
  }

  estimateTokens(text: string): number {
    return Math.ceil(text.length / 4);
  }
}

function parseLastUserMessage(messages: LlmCompletionRequest['messages']): unknown {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const message = messages[i]!;
    if (message.role !== 'user') continue;
    try {
      return JSON.parse(message.content);
    } catch {
      return message.content;
    }
  }
  return null;
}

/** Builds the smallest object that satisfies the given JSON Schema. */
export function synthesiseFromSchema(schema: Record<string, unknown>): unknown {
  const type = schema['type'];

  if (Array.isArray(schema['enum']) && schema['enum'].length > 0) {
    return schema['enum'][0];
  }

  switch (type) {
    case 'object': {
      const properties = (schema['properties'] ?? {}) as Record<string, Record<string, unknown>>;
      const required = (schema['required'] as string[] | undefined) ?? Object.keys(properties);
      const result: Record<string, unknown> = {};
      for (const key of required) {
        const propertySchema = properties[key];
        if (propertySchema) result[key] = synthesiseFromSchema(propertySchema);
      }
      return result;
    }
    case 'array': {
      const items = schema['items'] as Record<string, unknown> | undefined;
      const minItems = typeof schema['minItems'] === 'number' ? schema['minItems'] : 0;
      if (!items || minItems === 0) return [];
      return Array.from({ length: minItems }, () => synthesiseFromSchema(items));
    }
    case 'string':
      return 'mock';
    case 'number':
    case 'integer':
      return typeof schema['minimum'] === 'number' ? schema['minimum'] : 0;
    case 'boolean':
      return false;
    default:
      return null;
  }
}
