import { describe, expect, it } from 'vitest';
import { z } from 'zod';
import { ConflictError, NotFoundError, ValidationError } from '../core/errors.js';
import { BUILT_IN_AGENTS, RESEARCH_PIPELINE_ROOTS } from './definitions/index.js';
import { AgentRegistry } from './registry.js';
import type { AgentDefinition } from './types.js';

function makeAgent(
  key: string,
  dependencies: string[] = [],
  overrides: Partial<AgentDefinition> = {},
): AgentDefinition {
  return {
    key,
    name: key,
    description: key,
    version: '1.0.0',
    role: 'test',
    systemPrompt: 'test',
    tools: [],
    dependencies,
    enabled: true,
    priority: 100,
    modelTier: 'FAST',
    inputSchema: z.object({}),
    outputSchema: z.object({ ok: z.boolean() }),
    buildInput: () => ({}),
    ...overrides,
  } as AgentDefinition;
}

describe('AgentRegistry', () => {
  it('registers and describes an agent with derived JSON schemas', () => {
    const registry = new AgentRegistry();
    registry.register(makeAgent('alpha'));

    const meta = registry.describe('alpha');
    expect(meta.key).toBe('alpha');
    expect(meta.outputSchema).toHaveProperty('properties.ok');
  });

  it('rejects a duplicate key', () => {
    const registry = new AgentRegistry();
    registry.register(makeAgent('alpha'));
    expect(() => registry.register(makeAgent('alpha'))).toThrow(ConflictError);
  });

  it('rejects a key that is not kebab-case', () => {
    const registry = new AgentRegistry();
    expect(() => registry.register(makeAgent('Alpha_One'))).toThrow(ValidationError);
  });

  it('throws for an unknown agent', () => {
    expect(() => new AgentRegistry().get('nope')).toThrow(NotFoundError);
  });

  describe('resolveExecutionOrder', () => {
    it('orders agents so dependencies always run first', () => {
      const registry = new AgentRegistry();
      registry.registerAll([
        makeAgent('c', ['b']),
        makeAgent('a'),
        makeAgent('b', ['a']),
      ]);

      expect(registry.resolveExecutionOrder().map((agent) => agent.key)).toEqual(['a', 'b', 'c']);
    });

    it('pulls in transitive dependencies of the requested agent only', () => {
      const registry = new AgentRegistry();
      registry.registerAll([
        makeAgent('a'),
        makeAgent('b', ['a']),
        makeAgent('unrelated'),
      ]);

      expect(registry.resolveExecutionOrder(['b']).map((agent) => agent.key)).toEqual(['a', 'b']);
    });

    it('breaks ties by priority then key, deterministically', () => {
      const registry = new AgentRegistry();
      registry.registerAll([
        makeAgent('z', [], { priority: 1 }),
        makeAgent('a', [], { priority: 5 }),
        makeAgent('m', [], { priority: 5 }),
      ]);

      expect(registry.resolveExecutionOrder().map((agent) => agent.key)).toEqual(['z', 'a', 'm']);
    });

    it('drops a disabled agent and everything that depends on it', () => {
      const registry = new AgentRegistry();
      registry.registerAll([
        makeAgent('a'),
        makeAgent('b', ['a'], { enabled: false }),
        makeAgent('c', ['b']),
        makeAgent('d'),
      ]);

      expect(registry.resolveExecutionOrder().map((agent) => agent.key)).toEqual(['a', 'd']);
    });

    it('rejects a dependency cycle instead of hanging', () => {
      const registry = new AgentRegistry();
      registry.registerAll([makeAgent('a', ['b']), makeAgent('b', ['a'])]);

      expect(() => registry.resolveExecutionOrder()).toThrow(/Circular agent dependency/);
    });

    it('rejects a dependency on an agent that does not exist', () => {
      const registry = new AgentRegistry();
      registry.register(makeAgent('a', ['ghost']));
      expect(() => registry.resolveExecutionOrder()).toThrow(NotFoundError);
    });
  });

  describe('built-in agents', () => {
    const registry = new AgentRegistry();
    registry.registerAll(BUILT_IN_AGENTS);

    it('forms a valid graph', () => {
      expect(() => registry.resolveExecutionOrder()).not.toThrow();
    });

    it('resolves the research pipeline without the on-demand delivery agents', () => {
      const order = registry
        .resolveExecutionOrder([...RESEARCH_PIPELINE_ROOTS])
        .map((agent) => agent.key);

      expect(order[0]).toBe('trend-hunter');
      expect(order.at(-1)).toBe('opportunity-scoring');
      expect(order).not.toContain('prd-generator');
      expect(order).not.toContain('prompt-engineer');
    });

    it('gives every agent a mock fixture so the platform runs offline', () => {
      for (const agent of BUILT_IN_AGENTS) {
        expect(agent.mockFixture, `${agent.key} has no offline fixture`).toBeTypeOf('function');
      }
    });

    it('produces fixture output that satisfies the agent own schema', () => {
      for (const agent of BUILT_IN_AGENTS) {
        const fixture = agent.mockFixture!({
          input: { brief: { sector: 'test', country: 'Italy', platform: 'ANDROID' } },
          model: 'test',
        });
        const result = agent.outputSchema.safeParse(fixture);
        expect(result.success, `${agent.key}: ${JSON.stringify(result)}`).toBe(true);
      }
    });
  });
});
