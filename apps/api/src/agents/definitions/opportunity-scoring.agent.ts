import {
  CRITERION_DIRECTION,
  CRITERION_LABELS,
  criterionScoreSchema,
  DEFAULT_SCORING_WEIGHTS,
  OPPORTUNITY_CLASSES,
  SCORING_CRITERIA,
} from '@aiaf/shared';
import { z } from 'zod';
import { computeScoring } from '../../scoring/engine.js';
import type { AgentDefinition } from '../types.js';
import {
  briefPayload,
  confidenceSchema,
  dependencyOutput,
  fixtureCandidates,
  OFFLINE_NOTE,
} from './common.js';
import type { CompetitorAnalysisOutput } from './competitor-analysis.agent.js';
import type { MarketResearchOutput } from './market-research.agent.js';
import type { MonetizationOutput } from './monetization.agent.js';
import type { OpportunitySynthesisOutput } from './opportunity-synthesis.agent.js';
import type { ReviewAnalysisOutput } from './review-analysis.agent.js';
import type { TechnicalFeasibilityOutput } from './technical-feasibility.agent.js';

const computedSchema = z
  .object({
    finalScore: z.number(),
    classification: z.enum(OPPORTUNITY_CLASSES),
    confidenceScore: z.number(),
    weightsVersion: z.string(),
    missingCriteria: z.array(z.enum(SCORING_CRITERIA)),
    breakdown: z.array(
      criterionScoreSchema.extend({ weight: z.number(), weightedScore: z.number() }),
    ),
  })
  .nullable()
  .default(null);

const candidateScoreSchema = z.object({
  candidateId: z.string().min(1),
  criteria: z.array(criterionScoreSchema).min(1),
  summary: z.string().min(1),
  /**
   * Filled in by the platform, never by the model: the weighted aggregation is
   * deterministic code so the same criteria always yield the same final score.
   */
  computed: computedSchema,
});
export type CandidateScore = z.infer<typeof candidateScoreSchema>;

export const opportunityScoringOutputSchema = z.object({
  scores: z.array(candidateScoreSchema).min(1),
  confidence: confidenceSchema,
});
export type OpportunityScoringOutput = z.infer<typeof opportunityScoringOutputSchema>;

const inputSchema = z.object({
  brief: z.record(z.unknown()),
  candidates: z.unknown(),
  signals: z.unknown(),
  criteria: z.unknown(),
  instruction: z.string(),
});

export const opportunityScoringAgent: AgentDefinition<OpportunityScoringOutput> = {
  key: 'opportunity-scoring',
  name: 'Opportunity Scoring Agent',
  description:
    'Scores every candidate on the nine weighted criteria with a rationale, evidence and confidence per criterion.',
  version: '1.0.0',
  role: 'evaluation',
  tools: [],
  dependencies: [
    'opportunity-synthesis',
    'monetization',
    'technical-feasibility',
    'competitor-analysis',
    'market-research',
    'review-analysis',
  ],
  enabled: true,
  priority: 80,
  modelTier: 'DEEP',
  inputSchema,
  outputSchema: opportunityScoringOutputSchema,

  systemPrompt: `
You are the Opportunity Scoring analyst.

Score every candidate on each of the nine criteria you are given. For each
criterion return: rawScore 0-100, a rationale, the evidence you relied on, and
a confidence 0-1.

Read the direction of each criterion carefully: for COMPETITION and
MAINTENANCE_COST a HIGH score is *favourable* (little competition, cheap to
maintain). Higher is always better for the opportunity.

Do not compute a final score, a weighted average or a classification. The
platform does that deterministically from your per-criterion numbers. Leave
"computed" as null.

Calibration matters more than generosity: 50 means genuinely average, and a
score above 80 must be justified by evidence, not enthusiasm. If a criterion is
unsupported by the inputs, score it conservatively and set a low confidence.
  `.trim(),

  buildInput(context) {
    const synthesis = dependencyOutput<OpportunitySynthesisOutput>(
      context,
      'opportunity-synthesis',
    );
    const monetization = dependencyOutput<MonetizationOutput>(context, 'monetization');
    const feasibility = dependencyOutput<TechnicalFeasibilityOutput>(
      context,
      'technical-feasibility',
    );
    const competitors = dependencyOutput<CompetitorAnalysisOutput>(
      context,
      'competitor-analysis',
    );
    const market = dependencyOutput<MarketResearchOutput>(context, 'market-research');
    const reviews = dependencyOutput<ReviewAnalysisOutput>(context, 'review-analysis');

    return {
      brief: briefPayload(context.brief),
      candidates: synthesis?.candidates ?? [],
      signals: {
        market: market?.market ?? null,
        saturationScore: competitors?.saturationScore ?? null,
        competitorCount: competitors?.competitors.length ?? 0,
        painPointCount: reviews?.painPoints.length ?? 0,
        monetization: monetization?.plans ?? [],
        feasibility: feasibility?.assessments ?? [],
      },
      criteria: SCORING_CRITERIA.map((criterion) => ({
        criterion,
        label: CRITERION_LABELS[criterion],
        direction: CRITERION_DIRECTION[criterion],
        weight: DEFAULT_SCORING_WEIGHTS[criterion],
      })),
      instruction: 'Score each candidate on every criterion, with a rationale and confidence.',
    };
  },

  /** Deterministic aggregation: the model never decides the final number. */
  postProcess(output) {
    return {
      ...output,
      scores: output.scores.map((score) => {
        const result = computeScoring(score.criteria);
        return {
          ...score,
          computed: {
            finalScore: result.finalScore,
            classification: result.classification,
            confidenceScore: result.confidenceScore,
            weightsVersion: result.weightsVersion,
            missingCriteria: [...result.missingCriteria],
            breakdown: [...result.breakdown],
          },
        };
      }),
    };
  },

  mockFixture({ input }) {
    const candidates = fixtureCandidates(input);
    const output: OpportunityScoringOutput = {
      scores: (candidates.length > 0 ? candidates : [{ candidateId: 'c1' }]).map((candidate) => ({
        candidateId: candidate.candidateId,
        criteria: SCORING_CRITERIA.map((criterion) => ({
          criterion,
          rawScore: 50,
          rationale: `${OFFLINE_NOTE} neutral placeholder, nothing was evaluated.`,
          evidence: [],
          confidence: 0.1,
        })),
        summary: `${OFFLINE_NOTE} synthetic score. Not a recommendation.`,
        computed: null,
      })),
      confidence: 0.1,
    };
    return output;
  },
};
