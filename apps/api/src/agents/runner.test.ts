import { describe, expect, it } from 'vitest';
import { BudgetTracker } from './budget.js';
import { AgentExecutionError, BudgetExceededError } from '../core/errors.js';
import {
  echoAgent,
  makeContext,
  ScriptedLlmProvider,
  StubSearchProvider,
} from '../test/fixtures.js';
import { AgentRunner, parseJsonResponse } from './runner.js';

const router = { resolve: () => 'test-model' };

describe('parseJsonResponse', () => {
  it('parses plain JSON', () => {
    expect(parseJsonResponse('{"a":1}')).toEqual({ a: 1 });
  });

  it('recovers JSON from a code fence', () => {
    expect(parseJsonResponse('```json\n{"a":1}\n```')).toEqual({ a: 1 });
  });

  it('recovers JSON wrapped in prose', () => {
    expect(parseJsonResponse('Sure! {"a":1} hope that helps')).toEqual({ a: 1 });
  });

  it('throws on unparseable content', () => {
    expect(() => parseJsonResponse('not json at all')).toThrow();
  });
});

describe('AgentRunner', () => {
  it('returns validated output and accounts for usage', async () => {
    const llm = new ScriptedLlmProvider(['{"value":"hello"}']);
    const runner = new AgentRunner(llm, router);
    const context = makeContext();

    const outcome = await runner.run(echoAgent, context);

    expect(outcome.output).toEqual({ value: 'hello' });
    expect(outcome.attempts).toBe(1);
    expect(outcome.promptTokens).toBe(100);
    expect(outcome.costUsd).toBeCloseTo(0.001, 6);
    expect(context.budget.snapshot().usedTokens).toBe(150);
  });

  it('retries once with the validation errors fed back', async () => {
    const llm = new ScriptedLlmProvider(['{"value":42}', '{"value":"corrected"}']);
    const runner = new AgentRunner(llm, router, {
      maxValidationRetries: 1,
      maxEvidencePerAgent: 5,
    });

    const outcome = await runner.run(echoAgent, makeContext());

    expect(outcome.output).toEqual({ value: 'corrected' });
    expect(outcome.attempts).toBe(2);
    // Both calls are billed, which is why retries are capped.
    expect(outcome.promptTokens).toBe(200);
    const lastMessage = llm.requests[1]?.messages.at(-1);
    expect(lastMessage?.content).toContain('did not satisfy the schema');
  });

  it('gives up after exhausting its retries', async () => {
    const llm = new ScriptedLlmProvider(['{"value":42}', '{"value":43}']);
    const runner = new AgentRunner(llm, router, {
      maxValidationRetries: 1,
      maxEvidencePerAgent: 5,
    });

    await expect(runner.run(echoAgent, makeContext())).rejects.toThrow(AgentExecutionError);
  });

  it('refuses the call when the token budget cannot cover it', async () => {
    const llm = new ScriptedLlmProvider(['{"value":"hello"}']);
    const runner = new AgentRunner(llm, router);
    const context = makeContext({ budget: new BudgetTracker(10, 5) });

    await expect(runner.run(echoAgent, context)).rejects.toThrow(BudgetExceededError);
    // Refused *before* the provider was called.
    expect(llm.requests).toHaveLength(0);
  });

  it('passes the agent JSON schema to the provider', async () => {
    const llm = new ScriptedLlmProvider(['{"value":"hello"}']);
    await new AgentRunner(llm, router).run(echoAgent, makeContext());

    expect(llm.requests[0]?.jsonSchema?.name).toBe('echo');
    expect(llm.requests[0]?.jsonSchema?.schema).toHaveProperty('properties.value');
  });

  it('continues with reduced evidence when a search query fails', async () => {
    const search = new StubSearchProvider({}, false, new Error('search down'));
    const agent = { ...echoAgent, searchQueries: () => ['a', 'b'] };
    const llm = new ScriptedLlmProvider(['{"value":"hello"}']);

    const outcome = await new AgentRunner(llm, router).run(agent, makeContext({ search }));

    expect(outcome.output).toEqual({ value: 'hello' });
    expect(outcome.evidence.results).toHaveLength(0);
    expect(search.queries).toEqual(['a', 'b']);
  });

  it('deduplicates evidence by URL, keeping the best-scoring hit', async () => {
    const search = new StubSearchProvider({
      results: [
        { url: 'https://x.test/a', title: 'A low', snippet: '', sourceType: 'WEB', score: 0.2 },
        { url: 'https://x.test/a', title: 'A high', snippet: '', sourceType: 'WEB', score: 0.9 },
        { url: 'https://x.test/b', title: 'B', snippet: '', sourceType: 'WEB', score: 0.5 },
      ],
    });
    const agent = { ...echoAgent, searchQueries: () => ['q'] };
    const llm = new ScriptedLlmProvider(['{"value":"hello"}']);

    const outcome = await new AgentRunner(llm, router).run(agent, makeContext({ search }));

    expect(outcome.evidence.results.map((result) => result.title)).toEqual(['A high', 'B']);
  });

  it('tells the model it may cite nothing when the search backend is offline', async () => {
    const search = new StubSearchProvider({}, true);
    const agent = { ...echoAgent, searchQueries: () => ['q'] };
    const llm = new ScriptedLlmProvider(['{"value":"hello"}']);

    await new AgentRunner(llm, router).run(agent, makeContext({ search }));

    const evidenceMessage = llm.requests[0]?.messages.at(-1)?.content ?? '';
    expect(evidenceMessage).toContain('running offline');
    expect(evidenceMessage).toContain('no URL may be cited');
  });

  it('applies postProcess to the validated output', async () => {
    const agent = {
      ...echoAgent,
      postProcess: (output: { value: string }) => ({ value: output.value.toUpperCase() }),
    };
    const llm = new ScriptedLlmProvider(['{"value":"hello"}']);

    const outcome = await new AgentRunner(llm, router).run(agent, makeContext());

    expect(outcome.output).toEqual({ value: 'HELLO' });
  });
});
