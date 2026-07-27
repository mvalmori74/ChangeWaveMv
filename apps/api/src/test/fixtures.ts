import { pino } from 'pino';
import { z } from 'zod';
import type { AgentContext, AgentDefinition, ResearchBrief } from '../agents/types.js';
import { BudgetTracker } from '../agents/budget.js';
import { InMemoryBlackboard } from '../agents/blackboard.js';
import type { LlmCompletionRequest, LlmCompletionResponse, LlmProvider } from '../providers/llm/types.js';
import type { SearchProvider, SearchQuery, SearchResponse } from '../providers/search/types.js';

/** Silent logger: tests assert on behaviour, not on log output. */
export const testLogger = pino({ level: 'silent' });

export const testBrief: ResearchBrief = {
  projectId: 'project-1',
  sector: 'industrial maintenance',
  country: 'Italy',
  platform: 'ANDROID',
  language: 'en',
  timeframe: 'last 12 months',
};

/** LLM double that replays a scripted list of responses. */
export class ScriptedLlmProvider implements LlmProvider {
  readonly name = 'scripted';
  readonly requests: LlmCompletionRequest[] = [];
  private index = 0;

  constructor(private readonly responses: (string | Error)[]) {}

  async complete(request: LlmCompletionRequest): Promise<LlmCompletionResponse> {
    this.requests.push(request);
    const next = this.responses[Math.min(this.index, this.responses.length - 1)];
    this.index += 1;
    if (next instanceof Error) throw next;
    return {
      content: next ?? '{}',
      model: request.model,
      finishReason: 'stop',
      usage: { promptTokens: 100, completionTokens: 50, costUsd: 0.001 },
    };
  }

  estimateTokens(text: string): number {
    return Math.ceil(text.length / 4);
  }
}

export class StubSearchProvider implements SearchProvider {
  readonly name = 'stub';
  readonly queries: string[] = [];

  constructor(
    private readonly response: Partial<SearchResponse> = {},
    readonly synthetic = false,
    private readonly failure?: Error,
  ) {}

  async search(query: SearchQuery): Promise<SearchResponse> {
    this.queries.push(query.query);
    if (this.failure) throw this.failure;
    return {
      provider: this.name,
      query: query.query,
      results: this.response.results ?? [],
      synthetic: this.synthetic,
    };
  }
}

export function makeContext(overrides: Partial<AgentContext> = {}): AgentContext {
  return {
    runId: 'run-1',
    brief: testBrief,
    blackboard: new InMemoryBlackboard(),
    search: new StubSearchProvider(),
    budget: new BudgetTracker(10, 100_000),
    logger: testLogger,
    ...overrides,
  };
}

export const echoAgent: AgentDefinition<{ value: string }> = {
  key: 'echo',
  name: 'Echo',
  description: 'Test agent',
  version: '1.0.0',
  role: 'test',
  systemPrompt: 'echo',
  tools: [],
  dependencies: [],
  enabled: true,
  priority: 10,
  modelTier: 'FAST',
  inputSchema: z.object({}),
  outputSchema: z.object({ value: z.string() }),
  buildInput: () => ({ ping: true }),
};
