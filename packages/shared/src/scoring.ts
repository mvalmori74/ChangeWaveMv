import { z } from 'zod';

/**
 * Scoring is deliberately *not* left to the model. Agents supply a raw 0-100
 * score per criterion together with a rationale and evidence; the weighting,
 * aggregation and classification below are plain deterministic code so the same
 * inputs always produce the same final score and the maths can be audited.
 */

export const SCORING_CRITERIA = [
  'MARKET_DEMAND',
  'COMPETITION',
  'USER_PAIN',
  'MONETIZATION',
  'RETENTION_POTENTIAL',
  'TECHNICAL_FEASIBILITY',
  'ASO_POTENTIAL',
  'MAINTENANCE_COST',
  'STRATEGIC_FIT',
] as const;
export type ScoringCriterion = (typeof SCORING_CRITERIA)[number];

export const CRITERION_LABELS: Record<ScoringCriterion, string> = {
  MARKET_DEMAND: 'Market Demand',
  COMPETITION: 'Competition',
  USER_PAIN: 'User Pain',
  MONETIZATION: 'Monetization',
  RETENTION_POTENTIAL: 'Retention Potential',
  TECHNICAL_FEASIBILITY: 'Technical Feasibility',
  ASO_POTENTIAL: 'ASO Potential',
  MAINTENANCE_COST: 'Maintenance Cost',
  STRATEGIC_FIT: 'Strategic Fit',
};

/**
 * What "100" means for each criterion. Two criteria are inverted on purpose:
 * for COMPETITION and MAINTENANCE_COST a *high* raw score means a favourable
 * situation (little competition / cheap to maintain), so agents are prompted
 * accordingly and the label below is what the UI shows.
 */
export const CRITERION_DIRECTION: Record<ScoringCriterion, string> = {
  MARKET_DEMAND: '100 = very strong, growing, evidenced demand',
  COMPETITION: '100 = almost no credible competitor (favourable)',
  USER_PAIN: '100 = frequent, severe, badly-served pain',
  MONETIZATION: '100 = clear willingness to pay, high ARPU headroom',
  RETENTION_POTENTIAL: '100 = daily/weekly habitual use, high stickiness',
  TECHNICAL_FEASIBILITY: '100 = buildable by a small team in weeks, no exotic deps',
  ASO_POTENTIAL: '100 = searchable keywords with volume and weak incumbents',
  MAINTENANCE_COST: '100 = negligible ongoing cost (favourable)',
  STRATEGIC_FIT: '100 = perfect fit with the operator stated focus and assets',
};

export const DEFAULT_SCORING_WEIGHTS: Record<ScoringCriterion, number> = {
  MARKET_DEMAND: 0.2,
  COMPETITION: 0.15,
  USER_PAIN: 0.15,
  MONETIZATION: 0.15,
  RETENTION_POTENTIAL: 0.1,
  TECHNICAL_FEASIBILITY: 0.1,
  ASO_POTENTIAL: 0.05,
  MAINTENANCE_COST: 0.05,
  STRATEGIC_FIT: 0.05,
};

export const SCORING_WEIGHTS_VERSION = 'v1';

export const scoringWeightsSchema = z
  .record(z.enum(SCORING_CRITERIA), z.number().min(0).max(1))
  .refine(
    (weights) => {
      const total = Object.values(weights).reduce((sum, w) => sum + (w ?? 0), 0);
      return Math.abs(total - 1) < 1e-6;
    },
    { message: 'Scoring weights must sum to exactly 1' },
  );
export type ScoringWeights = Record<ScoringCriterion, number>;

export const criterionScoreSchema = z.object({
  criterion: z.enum(SCORING_CRITERIA),
  /** 0-100, higher is always better for the opportunity. */
  rawScore: z.number().min(0).max(100),
  rationale: z.string().min(1),
  /** Short evidence strings; ideally URLs already registered as sources. */
  evidence: z.array(z.string()).default([]),
  confidence: z.number().min(0).max(1),
});
export type CriterionScore = z.infer<typeof criterionScoreSchema>;

export const OPPORTUNITY_CLASSES = [
  'KILL',
  'LOW_POTENTIAL',
  'PROMISING',
  'HIGH_POTENTIAL',
  'EXCEPTIONAL',
] as const;
export type OpportunityClass = (typeof OPPORTUNITY_CLASSES)[number];

export interface ScoreBand {
  readonly klass: OpportunityClass;
  readonly min: number;
  readonly max: number;
  readonly label: string;
}

export const SCORE_BANDS: readonly ScoreBand[] = [
  { klass: 'KILL', min: 0, max: 39, label: 'Kill' },
  { klass: 'LOW_POTENTIAL', min: 40, max: 59, label: 'Low potential' },
  { klass: 'PROMISING', min: 60, max: 74, label: 'Promising' },
  { klass: 'HIGH_POTENTIAL', min: 75, max: 89, label: 'High potential' },
  { klass: 'EXCEPTIONAL', min: 90, max: 100, label: 'Exceptional' },
];

export function classifyScore(finalScore: number): OpportunityClass {
  const clamped = clampScore(finalScore);
  // The bands are declared with the integer bounds from the spec (0-39, 40-59,
  // ...), but scores are continuous, so match on the lower bound walking down
  // the table. Matching min *and* max would drop 59.5 into no band at all.
  for (let i = SCORE_BANDS.length - 1; i >= 0; i -= 1) {
    const band = SCORE_BANDS[i]!;
    if (clamped >= band.min) return band.klass;
  }
  return 'KILL';
}

export function clampScore(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(100, Math.max(0, value));
}

export interface WeightedCriterionResult extends CriterionScore {
  readonly weight: number;
  readonly weightedScore: number;
}

export interface ScoringResult {
  readonly finalScore: number;
  readonly classification: OpportunityClass;
  /** Weighted mean of per-criterion confidence: how much to trust finalScore. */
  readonly confidenceScore: number;
  readonly breakdown: readonly WeightedCriterionResult[];
  readonly weightsVersion: string;
  /** Criteria the agent did not score; they are treated as absent, not as 0. */
  readonly missingCriteria: readonly ScoringCriterion[];
}

/**
 * Aggregate per-criterion scores into a final 0-100 score.
 *
 * Missing criteria are *excluded* and the remaining weights are renormalised,
 * rather than being silently counted as zero — an unanswered criterion is not
 * evidence of a bad opportunity. Missing criteria are reported so the UI can
 * warn the user that the score is partial.
 */
export function computeScore(
  scores: readonly CriterionScore[],
  weights: ScoringWeights = DEFAULT_SCORING_WEIGHTS,
  weightsVersion: string = SCORING_WEIGHTS_VERSION,
): ScoringResult {
  const byCriterion = new Map<ScoringCriterion, CriterionScore>();
  for (const score of scores) {
    // Last write wins: an agent retry should override its earlier answer.
    byCriterion.set(score.criterion, score);
  }

  const present = SCORING_CRITERIA.filter((c) => byCriterion.has(c));
  const missingCriteria = SCORING_CRITERIA.filter((c) => !byCriterion.has(c));
  const totalWeight = present.reduce((sum, c) => sum + (weights[c] ?? 0), 0);

  if (present.length === 0 || totalWeight <= 0) {
    return {
      finalScore: 0,
      classification: 'KILL',
      confidenceScore: 0,
      breakdown: [],
      weightsVersion,
      missingCriteria,
    };
  }

  // Contributions are summed at full precision and only the total is rounded.
  // Rounding each contribution first would make nine criteria of 80 add up to
  // 79.99, which looks like a bug to anyone checking the arithmetic by hand.
  let exactTotal = 0;
  const breakdown: WeightedCriterionResult[] = present.map((criterion) => {
    const score = byCriterion.get(criterion)!;
    const normalisedWeight = (weights[criterion] ?? 0) / totalWeight;
    const rawScore = clampScore(score.rawScore);
    const exactContribution = rawScore * normalisedWeight;
    exactTotal += exactContribution;
    return {
      ...score,
      rawScore,
      weight: weights[criterion] ?? 0,
      weightedScore: round2(exactContribution),
    };
  });

  const finalScore = round2(exactTotal);
  const confidenceScore = round2(
    breakdown.reduce(
      (sum, b) => sum + b.confidence * ((weights[b.criterion] ?? 0) / totalWeight),
      0,
    ),
  );

  return {
    finalScore,
    classification: classifyScore(finalScore),
    confidenceScore,
    breakdown,
    weightsVersion,
    missingCriteria,
  };
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}
