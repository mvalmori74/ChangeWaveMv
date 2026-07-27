import type { AgentDefinitionMeta, ModelTier, Platform } from '@aiaf/shared';
import type { ZodType, ZodTypeDef } from 'zod';
import type { AppLogger } from '../core/logger.js';
import type { MockFixture } from '../providers/llm/mock.provider.js';
import type { SearchProvider, SearchResult } from '../providers/search/types.js';
import type { BudgetTracker } from './budget.js';

/** The brief a research run is executed against. */
export interface ResearchBrief {
  projectId: string;
  sector: string;
  country: string;
  platform: Platform;
  language: string;
  timeframe: string;
  objective?: string | undefined;
}

/**
 * Run-scoped shared state. Agents never call each other: they read the output
 * of their declared dependencies from here, which is what keeps the agent set
 * extensible without touching the orchestrator.
 */
export interface Blackboard {
  get<T = unknown>(agentKey: string): T | undefined;
  set(agentKey: string, value: unknown): void;
  snapshot(): Record<string, unknown>;
}

export interface EvidenceBundle {
  results: SearchResult[];
  /** True when the search backend is offline/synthetic: nothing may be cited. */
  synthetic: boolean;
}

export interface AgentContext {
  runId: string;
  brief: ResearchBrief;
  blackboard: Blackboard;
  search: SearchProvider;
  budget: BudgetTracker;
  logger: AppLogger;
  signal?: AbortSignal | undefined;
}

/**
 * An agent is a declarative unit: metadata for the registry, a validated output
 * contract, the payload it wants the model to reason over, and an optional
 * offline fixture. Everything else (prompting, retries, cost accounting,
 * persistence) is handled once by the execution engine.
 */
export interface AgentDefinition<TOutput = unknown> {
  key: string;
  name: string;
  description: string;
  version: string;
  role: string;
  systemPrompt: string;
  tools: string[];
  /** Agent keys whose output must exist before this one runs. */
  dependencies: string[];
  enabled: boolean;
  priority: number;
  modelTier: ModelTier;

  // The third type argument is the schema's *input* type, which differs from
  // its output whenever a field uses .default() or a transform.
  inputSchema: ZodType<unknown, ZodTypeDef, unknown>;
  outputSchema: ZodType<TOutput, ZodTypeDef, unknown>;

  /** Search queries used to gather citable evidence before prompting. */
  searchQueries?(context: AgentContext): string[];

  /** Payload handed to the model, usually brief + dependency outputs. */
  buildInput(context: AgentContext): Record<string, unknown>;

  /** Deterministic offline output, used when LLM_PROVIDER=mock. */
  mockFixture?: MockFixture;

  /** Optional hook to normalise or enrich the validated model output. */
  postProcess?(output: TOutput, context: AgentContext): Promise<TOutput> | TOutput;
}

export interface AgentRunOutcome<TOutput = unknown> {
  agentKey: string;
  output: TOutput;
  model: string;
  promptTokens: number;
  completionTokens: number;
  costUsd: number;
  durationMs: number;
  attempts: number;
  evidence: EvidenceBundle;
}

/** Serialisable view of a definition, for the registry table and the API. */
export function toAgentMeta(
  definition: AgentDefinition,
  inputJsonSchema: Record<string, unknown>,
  outputJsonSchema: Record<string, unknown>,
): AgentDefinitionMeta {
  return {
    key: definition.key,
    name: definition.name,
    description: definition.description,
    version: definition.version,
    role: definition.role,
    inputSchema: inputJsonSchema,
    outputSchema: outputJsonSchema,
    systemPrompt: definition.systemPrompt,
    tools: definition.tools,
    dependencies: definition.dependencies,
    enabled: definition.enabled,
    priority: definition.priority,
    modelTier: definition.modelTier,
  };
}
