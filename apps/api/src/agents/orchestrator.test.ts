import { describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { BudgetExceededError } from '../core/errors.js';
import { ScriptedLlmProvider, StubSearchProvider, testBrief, testLogger } from '../test/fixtures.js';
import { Orchestrator, type RunRecorder } from './orchestrator.js';
import { AgentRegistry } from './registry.js';
import { AgentRunner } from './runner.js';
import type { AgentDefinition } from './types.js';

function recorderSpy(): RunRecorder & {
  executions: Parameters<RunRecorder['recordExecution']>[0][];
  updates: Parameters<RunRecorder['updateRun']>[1][];
  sources: Parameters<RunRecorder['recordSources']>[1];
} {
  const executions: Parameters<RunRecorder['recordExecution']>[0][] = [];
  const updates: Parameters<RunRecorder['updateRun']>[1][] = [];
  const sources: Parameters<RunRecorder['recordSources']>[1] = [];
  return {
    executions,
    updates,
    sources,
    async recordExecution(record) {
      executions.push(record);
    },
    async recordSources(_runId, batch) {
      sources.push(...batch);
    },
    async updateRun(_runId, data) {
      updates.push(data);
    },
  };
}

function agent(key: string, dependencies: string[] = []): AgentDefinition {
  return {
    key,
    name: key,
    description: key,
    version: '1.0.0',
    role: 'test',
    systemPrompt: key,
    tools: [],
    dependencies,
    enabled: true,
    priority: 100,
    modelTier: 'FAST',
    inputSchema: z.object({}),
    outputSchema: z.object({ value: z.string() }),
    buildInput: () => ({}),
  } as AgentDefinition;
}

function buildOrchestrator(
  agents: AgentDefinition[],
  responses: (string | Error)[],
  options: { search?: StubSearchProvider } = {},
) {
  const registry = new AgentRegistry();
  registry.registerAll(agents);
  const llm = new ScriptedLlmProvider(responses);
  const runner = new AgentRunner(llm, { resolve: () => 'test-model' }, {
    maxValidationRetries: 0,
    maxEvidencePerAgent: 5,
  });
  const recorder = recorderSpy();
  const search = options.search ?? new StubSearchProvider();
  return {
    orchestrator: new Orchestrator(registry, runner, search, recorder, testLogger),
    recorder,
    llm,
  };
}

const baseOptions = {
  runId: 'run-1',
  brief: testBrief,
  budgetUsd: 10,
  tokenBudget: 100_000,
};

describe('Orchestrator', () => {
  it('runs the graph in order and publishes each output on the blackboard', async () => {
    const { orchestrator, recorder } = buildOrchestrator(
      [agent('b', ['a']), agent('a')],
      ['{"value":"first"}', '{"value":"second"}'],
    );

    const result = await orchestrator.execute(baseOptions);

    expect(result.status).toBe('COMPLETED');
    expect(result.executed).toEqual(['a', 'b']);
    expect(result.state).toEqual({ a: { value: 'first' }, b: { value: 'second' } });
    expect(recorder.executions.map((execution) => execution.status)).toEqual([
      'SUCCEEDED',
      'SUCCEEDED',
    ]);
  });

  it('skips dependants of a failed agent and finishes PARTIAL', async () => {
    const { orchestrator, recorder } = buildOrchestrator(
      [agent('a'), agent('b', ['a']), agent('c')],
      ['not json', '{"value":"c ran"}'],
    );

    const result = await orchestrator.execute(baseOptions);

    expect(result.status).toBe('PARTIAL');
    expect(result.failed.map((entry) => entry.agentKey)).toEqual(['a']);
    expect(result.skipped).toEqual(['b']);
    // An unrelated branch still runs: one bad agent does not waste the run.
    expect(result.executed).toEqual(['c']);
    const skipped = recorder.executions.find((execution) => execution.agentKey === 'b');
    expect(skipped?.status).toBe('SKIPPED');
    expect(skipped?.error).toContain('Missing dependency output');
  });

  it('reports FAILED when nothing succeeded', async () => {
    const { orchestrator } = buildOrchestrator([agent('a')], ['not json']);
    const result = await orchestrator.execute(baseOptions);
    expect(result.status).toBe('FAILED');
  });

  it('stops the run once the budget is exhausted and marks the rest BUDGET_EXCEEDED', async () => {
    const { orchestrator, recorder } = buildOrchestrator(
      [agent('a'), agent('b'), agent('c')],
      ['{"value":"ok"}'],
    );

    // Enough for the first agent's pre-flight check, not for all three.
    const result = await orchestrator.execute({ ...baseOptions, tokenBudget: 500 });

    // Exactly where the budget runs out depends on prompt size; what matters
    // is that the run stops instead of overspending.
    expect(result.executed).toContain('a');
    expect(result.executed).not.toContain('c');
    expect(result.failed[0]?.error).toMatch(/budget/i);
    // Once the budget is gone the remaining agents are not attempted at all.
    const statuses = recorder.executions.map((execution) => execution.status);
    expect(statuses).toContain('BUDGET_EXCEEDED');
    expect(result.status).toBe('PARTIAL');
  });

  it('persists progress after every agent so a crash keeps paid-for work', async () => {
    const { orchestrator, recorder } = buildOrchestrator(
      [agent('a'), agent('b')],
      ['{"value":"1"}', '{"value":"2"}'],
    );

    await orchestrator.execute(baseOptions);

    const stateUpdates = recorder.updates.filter((update) => update.state !== undefined);
    expect(stateUpdates.length).toBeGreaterThanOrEqual(2);
    expect(recorder.updates[0]?.status).toBe('RUNNING');
    expect(recorder.updates.at(-1)?.status).toBe('COMPLETED');
  });

  it('records the sources an agent actually read', async () => {
    const search = new StubSearchProvider({
      results: [
        {
          url: 'https://example.test/report',
          title: 'Report',
          snippet: 'text',
          sourceType: 'REPORT',
          score: 0.8,
        },
      ],
    });
    const withQueries = { ...agent('a'), searchQueries: () => ['q'] };
    const { orchestrator, recorder } = buildOrchestrator([withQueries], ['{"value":"ok"}'], {
      search,
    });

    await orchestrator.execute(baseOptions);

    expect(recorder.sources).toHaveLength(1);
    expect(recorder.sources[0]?.url).toBe('https://example.test/report');
  });

  it('does not record synthetic search results as sources', async () => {
    const search = new StubSearchProvider({ results: [] }, true);
    const withQueries = { ...agent('a'), searchQueries: () => ['q'] };
    const { orchestrator, recorder } = buildOrchestrator([withQueries], ['{"value":"ok"}'], {
      search,
    });

    await orchestrator.execute(baseOptions);

    expect(recorder.sources).toHaveLength(0);
  });

  it('seeds the blackboard from initialState for single-agent invocations', async () => {
    const consumer: AgentDefinition = {
      ...agent('consumer'),
      buildInput: (context) => ({ seeded: context.blackboard.get('seed') }),
    };
    const { orchestrator, llm } = buildOrchestrator([consumer], ['{"value":"ok"}']);

    await orchestrator.execute({
      ...baseOptions,
      agentKeys: ['consumer'],
      initialState: { seed: { hello: 'world' } },
    });

    expect(llm.requests[0]?.messages[1]?.content).toContain('hello');
  });

  it('surfaces a budget stop as a BudgetExceededError from the runner', async () => {
    const { orchestrator } = buildOrchestrator([agent('a')], [new BudgetExceededError('nope')]);
    const result = await orchestrator.execute(baseOptions);
    expect(result.status).toBe('FAILED');
  });

  it('never lets a recorder failure abort the run', async () => {
    const registry = new AgentRegistry();
    registry.registerAll([agent('a')]);
    const runner = new AgentRunner(
      new ScriptedLlmProvider(['{"value":"ok"}']),
      { resolve: () => 'test-model' },
      { maxValidationRetries: 0, maxEvidencePerAgent: 5 },
    );
    const brokenRecorder: RunRecorder = {
      recordExecution: vi.fn().mockRejectedValue(new Error('db down')),
      recordSources: vi.fn().mockRejectedValue(new Error('db down')),
      updateRun: vi.fn().mockRejectedValue(new Error('db down')),
    };
    const orchestrator = new Orchestrator(
      registry,
      runner,
      new StubSearchProvider(),
      brokenRecorder,
      testLogger,
    );

    const result = await orchestrator.execute(baseOptions);

    expect(result.status).toBe('COMPLETED');
    expect(result.state).toEqual({ a: { value: 'ok' } });
  });
});
