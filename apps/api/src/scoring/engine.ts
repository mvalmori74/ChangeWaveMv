import {
  classifyScore,
  computeScore,
  DEFAULT_SCORING_WEIGHTS,
  SCORING_WEIGHTS_VERSION,
  type CriterionScore,
  type ScoringCriterion,
  type ScoringResult,
  type ScoringWeights,
} from '@aiaf/shared';

/**
 * The Scoring Engine.
 *
 * The maths lives in @aiaf/shared so the web client can re-explain a score
 * without a round trip; this module owns the platform-side concerns: the active
 * weight set and the mapping from criteria to the denormalised score columns on
 * the Opportunity row.
 */

let activeWeights: ScoringWeights = { ...DEFAULT_SCORING_WEIGHTS };

export function getActiveWeights(): ScoringWeights {
  return { ...activeWeights };
}

/** Weights are configurable; the version tag is stored with every score row. */
export function setActiveWeights(weights: ScoringWeights): void {
  activeWeights = { ...weights };
}

export function computeScoring(criteria: readonly CriterionScore[]): ScoringResult {
  return computeScore(criteria, activeWeights, SCORING_WEIGHTS_VERSION);
}

export interface DenormalisedScores {
  marketScore: number | null;
  competitionScore: number | null;
  monetizationScore: number | null;
  technicalScore: number | null;
  retentionScore: number | null;
  finalScore: number;
  confidenceScore: number;
  classification: string;
}

/**
 * The Opportunity row keeps five headline scores so the dashboard can sort and
 * filter without joining the per-criterion table.
 */
export function denormaliseScores(result: ScoringResult): DenormalisedScores {
  const raw = (criterion: ScoringCriterion): number | null =>
    result.breakdown.find((entry) => entry.criterion === criterion)?.rawScore ?? null;

  return {
    marketScore: raw('MARKET_DEMAND'),
    competitionScore: raw('COMPETITION'),
    monetizationScore: raw('MONETIZATION'),
    technicalScore: raw('TECHNICAL_FEASIBILITY'),
    retentionScore: raw('RETENTION_POTENTIAL'),
    finalScore: result.finalScore,
    confidenceScore: result.confidenceScore,
    classification: result.classification,
  };
}

export { classifyScore };
