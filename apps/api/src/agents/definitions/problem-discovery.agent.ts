import { z } from 'zod';
import type { AgentDefinition } from '../types.js';
import {
  briefPayload,
  confidenceSchema,
  dependencyOutput,
  evidenceTypeSchema,
  OFFLINE_NOTE,
  offlineMeta,
} from './common.js';
import type { CompetitorAnalysisOutput } from './competitor-analysis.agent.js';
import type { MarketResearchOutput } from './market-research.agent.js';
import type { ReviewAnalysisOutput } from './review-analysis.agent.js';
import type { TrendHunterOutput } from './trend-hunter.agent.js';

const problemSchema = z.object({
  problem: z.string().min(1),
  targetUser: z.string().min(1),
  frequency: z.enum(['RARE', 'OCCASIONAL', 'WEEKLY', 'DAILY', 'UNKNOWN']),
  severity: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL', 'UNKNOWN']),
  /** What people do today, including "nothing" and "a spreadsheet". */
  currentSolution: z.string().min(1),
  marketGap: z.string().min(1),
  evidenceType: evidenceTypeSchema,
  confidence: confidenceSchema,
  sourceUrls: z.array(z.string()).default([]),
});
export type DiscoveredProblem = z.infer<typeof problemSchema>;

export const problemDiscoveryOutputSchema = z.object({
  problems: z.array(problemSchema).min(1).max(20),
  confidence: confidenceSchema,
});
export type ProblemDiscoveryOutput = z.infer<typeof problemDiscoveryOutputSchema>;

const inputSchema = z.object({
  brief: z.record(z.unknown()),
  trends: z.unknown(),
  market: z.unknown(),
  competitorWhitespace: z.unknown(),
  reviewSignals: z.unknown(),
  instruction: z.string(),
});

export const problemDiscoveryAgent: AgentDefinition<ProblemDiscoveryOutput> = {
  key: 'problem-discovery',
  name: 'Problem Discovery Agent',
  description:
    'Combines trends, market data, competitor gaps and review signals into concrete, solvable user problems.',
  version: '1.0.0',
  role: 'synthesis',
  tools: [],
  dependencies: ['trend-hunter', 'market-research', 'competitor-analysis', 'review-analysis'],
  enabled: true,
  priority: 50,
  modelTier: 'DEEP',
  inputSchema,
  outputSchema: problemDiscoveryOutputSchema,

  systemPrompt: `
You are a Problem Discovery analyst.

You receive the outputs of the trend, market, competitor and review agents. Your
job is to state the concrete problems a new app could solve — not themes, not
categories, not product ideas.

A good problem statement names who has it, in what situation, and what it costs
them. "Technicians waste 20 minutes per job re-entering the same data into two
systems" is a problem. "Poor digitisation" is not.

Only state a problem you can trace back to something in the input. Where the
inputs are weak or synthetic, say so through evidenceType and confidence rather
than filling the gap with invention. Do not propose solutions here.
  `.trim(),

  buildInput(context) {
    const trends = dependencyOutput<TrendHunterOutput>(context, 'trend-hunter');
    const market = dependencyOutput<MarketResearchOutput>(context, 'market-research');
    const competitors = dependencyOutput<CompetitorAnalysisOutput>(
      context,
      'competitor-analysis',
    );
    const reviews = dependencyOutput<ReviewAnalysisOutput>(context, 'review-analysis');

    return {
      brief: briefPayload(context.brief),
      trends: (trends?.trends ?? []).map((trend) => ({
        title: trend.title,
        implication: trend.implication,
        evidenceType: trend.evidenceType,
      })),
      market: {
        keyProblems: market?.keyProblems ?? [],
        targetUsers: market?.targetUsers ?? [],
      },
      competitorWhitespace: {
        whitespace: competitors?.whitespace ?? [],
        saturationScore: competitors?.saturationScore ?? null,
        weaknesses: (competitors?.competitors ?? []).flatMap((c) => c.weaknesses),
      },
      reviewSignals: {
        painPoints: (reviews?.painPoints ?? []).map((p) => p.summary),
        featureRequests: (reviews?.featureRequests ?? []).map((p) => p.summary),
        complaints: (reviews?.complaints ?? []).map((p) => p.summary),
      },
      instruction: 'State the concrete problems worth solving, ordered by how compelling they are.',
    };
  },

  mockFixture({ input }) {
    const meta = offlineMeta();
    const sector =
      (input as { brief?: { sector?: string } } | null)?.brief?.sector ?? 'the sector';
    const output: ProblemDiscoveryOutput = {
      problems: [
        {
          problem: `${OFFLINE_NOTE} No researched problem is available for ${sector}.`,
          targetUser: 'Unknown until a live research run is executed.',
          frequency: 'UNKNOWN',
          severity: 'UNKNOWN',
          currentSolution: 'Unknown offline.',
          marketGap: 'Unknown offline.',
          ...meta,
        },
      ],
      confidence: 0.1,
    };
    return output;
  },
};
