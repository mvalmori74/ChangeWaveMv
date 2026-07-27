import { z } from 'zod';
import { AGENT_EXECUTION_STATUSES, MODEL_TIERS } from './enums.js';

/**
 * Serializable description of an agent. The registry stores these; the runtime
 * handler lives next to the definition inside the API package, so the web
 * client can list, inspect and enable/disable agents without importing code.
 */
export const agentDefinitionSchema = z.object({
  /** Stable machine key, e.g. "trend-hunter". Used in dependencies. */
  key: z.string().regex(/^[a-z0-9-]+$/),
  name: z.string().min(1),
  description: z.string().min(1),
  version: z.string().min(1),
  /** Short verticalised responsibility, e.g. "research", "synthesis". */
  role: z.string().min(1),
  inputSchema: z.record(z.unknown()),
  outputSchema: z.record(z.unknown()),
  systemPrompt: z.string().min(1),
  tools: z.array(z.string()).default([]),
  /** Keys of agents whose output this agent reads from the blackboard. */
  dependencies: z.array(z.string()).default([]),
  enabled: z.boolean().default(true),
  /** Lower runs earlier when the dependency graph allows either order. */
  priority: z.number().int().default(100),
  modelTier: z.enum(MODEL_TIERS).default('BALANCED'),
});
export type AgentDefinitionMeta = z.infer<typeof agentDefinitionSchema>;

export const agentExecutionSchema = z.object({
  id: z.string(),
  runId: z.string(),
  agentKey: z.string(),
  status: z.enum(AGENT_EXECUTION_STATUSES),
  model: z.string().nullable(),
  promptTokens: z.number().int(),
  completionTokens: z.number().int(),
  costUsd: z.number(),
  durationMs: z.number().int(),
  error: z.string().nullable(),
  startedAt: z.string(),
  finishedAt: z.string().nullable(),
});
export type AgentExecutionView = z.infer<typeof agentExecutionSchema>;

/** Which agents make up the V1 research pipeline, in dependency order. */
export const RESEARCH_PIPELINE_AGENTS = [
  'trend-hunter',
  'market-research',
  'competitor-analysis',
  'review-analysis',
  'problem-discovery',
  'monetization',
  'technical-feasibility',
  'opportunity-scoring',
] as const;
