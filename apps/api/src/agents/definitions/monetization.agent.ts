import { MONETIZATION_MODELS } from '@aiaf/shared';
import { z } from 'zod';
import type { AgentDefinition } from '../types.js';
import {
  briefPayload,
  confidenceSchema,
  dependencyOutput,
  evidenceTypeSchema,
  fixtureCandidates,
  OFFLINE_NOTE,
  rangeSchema,
} from './common.js';
import type { MarketResearchOutput } from './market-research.agent.js';
import type { OpportunitySynthesisOutput } from './opportunity-synthesis.agent.js';

const modelAssessmentSchema = z.object({
  model: z.enum(MONETIZATION_MODELS),
  fit: z.enum(['POOR', 'WEAK', 'MODERATE', 'STRONG']),
  rationale: z.string().min(1),
});

const monetizationPlanSchema = z.object({
  candidateId: z.string().min(1),
  assessed: z.array(modelAssessmentSchema).min(1),
  recommended: z.enum(MONETIZATION_MODELS),
  recommendedRationale: z.string().min(1),
  alternative: z.enum(MONETIZATION_MODELS).nullable(),
  /** ARPU as a range per user per month, never a point estimate. */
  arpuRange: rangeSchema.nullable(),
  conversionAssumption: z.string().min(1),
  risks: z.array(z.string()).max(10),
  evidenceType: evidenceTypeSchema,
  confidence: confidenceSchema,
});
export type MonetizationPlan = z.infer<typeof monetizationPlanSchema>;

export const monetizationOutputSchema = z.object({
  plans: z.array(monetizationPlanSchema).min(1),
  confidence: confidenceSchema,
});
export type MonetizationOutput = z.infer<typeof monetizationOutputSchema>;

const inputSchema = z.object({
  brief: z.record(z.unknown()),
  candidates: z.unknown(),
  market: z.unknown(),
  instruction: z.string(),
});

export const monetizationAgent: AgentDefinition<MonetizationOutput> = {
  key: 'monetization',
  name: 'Monetization Agent',
  description:
    'Evaluates every monetization model per candidate and recommends one with an ARPU range and risks.',
  version: '1.0.0',
  role: 'evaluation',
  tools: [],
  dependencies: ['opportunity-synthesis', 'market-research'],
  enabled: true,
  priority: 70,
  modelTier: 'BALANCED',
  inputSchema,
  outputSchema: monetizationOutputSchema,

  systemPrompt: `
You are a Monetization strategist.

For every candidate assess each model — advertising, rewarded video, in-app
purchase, premium, subscription, freemium, B2B licence, SaaS, marketplace fee —
then recommend one and name a fallback.

Money numbers are the single most dangerous output of this platform. Therefore:
- arpuRange is a range in a stated currency per user per month, with the
  reasoning in "basis". If you have no grounds for a range, return null.
- Never state a revenue figure as fact. It is an ESTIMATE at best.
- conversionAssumption must name the assumed free-to-paid rate you used, so a
  human can disagree with it.
- Include the risks that would break the model (ad rates, churn, platform fees,
  a free incumbent, B2B procurement cycles).

Return exactly one plan per candidate, keyed by the candidateId you were given.
  `.trim(),

  buildInput(context) {
    const synthesis = dependencyOutput<OpportunitySynthesisOutput>(
      context,
      'opportunity-synthesis',
    );
    const market = dependencyOutput<MarketResearchOutput>(context, 'market-research');
    return {
      brief: briefPayload(context.brief),
      candidates: synthesis?.candidates ?? [],
      market: {
        targetUsers: market?.targetUsers ?? [],
        demandLevel: market?.market.demandLevel ?? 'UNKNOWN',
      },
      instruction: 'Recommend a monetization model per candidate, with an honest ARPU range.',
    };
  },

  mockFixture({ input }) {
    const candidates = fixtureCandidates(input);
    const output: MonetizationOutput = {
      plans: (candidates.length > 0 ? candidates : [{ candidateId: 'c1' }]).map((candidate) => ({
        candidateId: candidate.candidateId,
        assessed: [
          {
            model: 'SUBSCRIPTION' as const,
            fit: 'MODERATE' as const,
            rationale: `${OFFLINE_NOTE} not assessed; offline placeholder.`,
          },
        ],
        recommended: 'SUBSCRIPTION' as const,
        recommendedRationale: `${OFFLINE_NOTE} no monetization analysis was performed.`,
        alternative: null,
        arpuRange: null,
        conversionAssumption: 'None. No conversion assumption can be made offline.',
        risks: ['Synthetic output: do not use for any financial decision.'],
        evidenceType: 'HYPOTHESIS' as const,
        confidence: 0.1,
      })),
      confidence: 0.1,
    };
    return output;
  },
};
