import { z } from 'zod';
import type { AgentDefinition } from '../types.js';
import {
  briefPayload,
  candidateSchema,
  confidenceSchema,
  dependencyOutput,
  OFFLINE_NOTE,
  offlineMeta,
} from './common.js';
import type { CompetitorAnalysisOutput } from './competitor-analysis.agent.js';
import type { ProblemDiscoveryOutput } from './problem-discovery.agent.js';

export const opportunitySynthesisOutputSchema = z.object({
  candidates: z.array(candidateSchema).min(1).max(20),
  confidence: confidenceSchema,
});
export type OpportunitySynthesisOutput = z.infer<typeof opportunitySynthesisOutputSchema>;

const inputSchema = z.object({
  brief: z.record(z.unknown()),
  problems: z.unknown(),
  whitespace: z.unknown(),
  instruction: z.string(),
});

/**
 * Turns problems into candidate products.
 *
 * This is the hinge of the pipeline: everything upstream is research, and
 * everything downstream (monetization, feasibility, scoring, PRD) is keyed on
 * the `candidateId` values produced here.
 */
export const opportunitySynthesisAgent: AgentDefinition<OpportunitySynthesisOutput> = {
  key: 'opportunity-synthesis',
  name: 'Opportunity Synthesis Agent',
  description:
    'Converts discovered problems into distinct candidate app opportunities with an MVP outline.',
  version: '1.0.0',
  role: 'synthesis',
  tools: [],
  dependencies: ['problem-discovery'],
  enabled: true,
  priority: 60,
  modelTier: 'DEEP',
  inputSchema,
  outputSchema: opportunitySynthesisOutputSchema,

  systemPrompt: `
You are a Product Strategist.

Turn each compelling problem into a candidate opportunity: one product idea,
one primary audience, one clear reason it wins. Merge problems that a single
product would solve together; split a problem that needs two different products.

Rules:
- candidateId must be short, stable and unique within your answer: c1, c2, c3...
- mvpFeatures is the smallest set that makes the product useful, not a wishlist.
  If it has more than 7 entries it is not an MVP.
- differentiators must be things the listed competitors demonstrably do not do.
- Carry the evidenceType of the underlying problem. A product built on a
  HYPOTHESIS problem is itself a HYPOTHESIS, however good the idea sounds.
- Do not score the opportunity. Scoring is another agent's job.
  `.trim(),

  buildInput(context) {
    const problems = dependencyOutput<ProblemDiscoveryOutput>(context, 'problem-discovery');
    const competitors = dependencyOutput<CompetitorAnalysisOutput>(
      context,
      'competitor-analysis',
    );
    return {
      brief: briefPayload(context.brief),
      problems: problems?.problems ?? [],
      whitespace: competitors?.whitespace ?? [],
      instruction:
        'Produce distinct, non-overlapping candidate opportunities with a minimal MVP each.',
    };
  },

  mockFixture({ input }) {
    const meta = offlineMeta();
    const sector =
      (input as { brief?: { sector?: string } } | null)?.brief?.sector ?? 'the sector';
    const output: OpportunitySynthesisOutput = {
      candidates: [
        {
          candidateId: 'c1',
          title: `${OFFLINE_NOTE} placeholder opportunity for ${sector}`,
          category: sector,
          targetAudience: 'Unknown until a live research run is executed.',
          problem: 'Placeholder problem produced without research.',
          proposedSolution: 'Placeholder solution produced without research.',
          mvpFeatures: ['Placeholder feature'],
          differentiators: [],
          risks: ['This candidate is synthetic and must not inform a real decision.'],
          assumptions: ['Everything here is an assumption: no evidence was gathered.'],
          ...meta,
        },
      ],
      confidence: 0.1,
    };
    return output;
  },
};
