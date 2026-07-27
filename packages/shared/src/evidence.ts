import { z } from 'zod';
import { EVIDENCE_TYPES, SOURCE_TYPES } from './enums.js';

/**
 * A source is anything the platform read to support a claim. Agents are
 * instructed to return the URL they actually used; a claim with no source can
 * still exist, but only as an INFERENCE / ESTIMATE / HYPOTHESIS.
 */
export const sourceSchema = z.object({
  url: z.string().url(),
  title: z.string().min(1),
  publisher: z.string().nullable().optional(),
  publishedAt: z.string().nullable().optional(),
  sourceType: z.enum(SOURCE_TYPES).default('WEB'),
  /** 0-1. How much weight the platform gives this source. */
  credibilityScore: z.number().min(0).max(1).default(0.5),
  snippet: z.string().nullable().optional(),
});
export type SourceInput = z.infer<typeof sourceSchema>;

/**
 * Every non-trivial statement produced by an agent is wrapped in a claim so the
 * UI can show what is fact, what is inferred and what is a guess.
 */
export const claimSchema = z.object({
  statement: z.string().min(1),
  evidenceType: z.enum(EVIDENCE_TYPES),
  confidence: z.number().min(0).max(1),
  /** Indices into the agent output `sources` array. */
  sourceUrls: z.array(z.string()).default([]),
});
export type Claim = z.infer<typeof claimSchema>;

/** A numeric estimate is never a single number: it is a range plus confidence. */
export const estimateRangeSchema = z.object({
  low: z.number(),
  high: z.number(),
  unit: z.string().min(1),
  confidence: z.number().min(0).max(1),
  basis: z.string().min(1),
});
export type EstimateRange = z.infer<typeof estimateRangeSchema>;

export const EVIDENCE_TYPE_LABELS: Record<(typeof EVIDENCE_TYPES)[number], string> = {
  VERIFIED: 'Verified (sourced)',
  INFERENCE: 'Inference (derived from sources)',
  ESTIMATE: 'Estimate (modelled)',
  HYPOTHESIS: 'Hypothesis (unverified)',
};
