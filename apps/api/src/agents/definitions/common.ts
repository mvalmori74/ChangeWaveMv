import { EVIDENCE_TYPES } from '@aiaf/shared';
import { z } from 'zod';
import type { AgentContext, ResearchBrief } from '../types.js';

/** Building blocks reused across agent output contracts. */

export const evidenceTypeSchema = z.enum(EVIDENCE_TYPES);

export const confidenceSchema = z.number().min(0).max(1);

export const rangeSchema = z.object({
  low: z.number(),
  high: z.number(),
  unit: z.string().min(1),
  confidence: confidenceSchema,
  basis: z.string().min(1),
});
export type Range = z.infer<typeof rangeSchema>;

/** Attached to any statement an agent makes. */
export const evidenceMetaSchema = z.object({
  evidenceType: evidenceTypeSchema,
  confidence: confidenceSchema,
  sourceUrls: z.array(z.string()).default([]),
});

export const claimSchema = evidenceMetaSchema.extend({
  statement: z.string().min(1),
});
export type AgentClaim = z.infer<typeof claimSchema>;

/**
 * A candidate opportunity. Produced once by opportunity-synthesis and then
 * enriched by monetization, feasibility and scoring, which correlate on
 * `candidateId` — so those agents stay independent of each other.
 */
export const candidateSchema = z.object({
  candidateId: z.string().min(1),
  title: z.string().min(1).max(160),
  category: z.string().min(1).max(80),
  targetAudience: z.string().min(1),
  problem: z.string().min(1),
  proposedSolution: z.string().min(1),
  mvpFeatures: z.array(z.string()).default([]),
  differentiators: z.array(z.string()).default([]),
  risks: z.array(z.string()).default([]),
  assumptions: z.array(z.string()).default([]),
  evidenceType: evidenceTypeSchema,
  confidence: confidenceSchema,
  sourceUrls: z.array(z.string()).default([]),
});
export type Candidate = z.infer<typeof candidateSchema>;

/** The brief block every agent receives as the head of its input payload. */
export function briefPayload(brief: ResearchBrief): Record<string, unknown> {
  return {
    sector: brief.sector,
    country: brief.country,
    platform: brief.platform,
    language: brief.language,
    timeframe: brief.timeframe,
    objective: brief.objective ?? null,
  };
}

/** Reads a dependency's output, or an empty object when it produced nothing. */
export function dependencyOutput<T>(context: AgentContext, agentKey: string): T | undefined {
  return context.blackboard.get<T>(agentKey);
}

/**
 * Fixtures must never look researched. Everything a fixture states is an
 * unverified placeholder, so it is tagged HYPOTHESIS with low confidence and no
 * sources, and its text says so.
 */
export const OFFLINE_NOTE = '[synthetic offline placeholder - not researched]';

export function offlineMeta(): { evidenceType: 'HYPOTHESIS'; confidence: number; sourceUrls: [] } {
  return { evidenceType: 'HYPOTHESIS', confidence: 0.1, sourceUrls: [] };
}

/** Reads the brief back out of a fixture's input payload. */
export function fixtureBrief(input: unknown): {
  sector: string;
  country: string;
  platform: string;
} {
  const brief = (input as { brief?: Record<string, unknown> } | null)?.brief ?? {};
  return {
    sector: typeof brief['sector'] === 'string' ? brief['sector'] : 'unspecified sector',
    country: typeof brief['country'] === 'string' ? brief['country'] : 'unspecified country',
    platform: typeof brief['platform'] === 'string' ? brief['platform'] : 'ANDROID',
  };
}

/** Reads candidates back out of a fixture's input payload. */
export function fixtureCandidates(input: unknown): Candidate[] {
  const candidates = (input as { candidates?: unknown } | null)?.candidates;
  const parsed = z.array(candidateSchema).safeParse(candidates);
  return parsed.success ? parsed.data : [];
}
