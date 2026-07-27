import { describe, expect, it } from 'vitest';
import {
  classifyScore,
  computeScore,
  DEFAULT_SCORING_WEIGHTS,
  SCORING_CRITERIA,
  scoringWeightsSchema,
  type CriterionScore,
} from './scoring.js';

function fullScoreSet(rawScore: number, confidence = 0.8): CriterionScore[] {
  return SCORING_CRITERIA.map((criterion) => ({
    criterion,
    rawScore,
    rationale: `fixed ${rawScore}`,
    evidence: [],
    confidence,
  }));
}

describe('scoring weights', () => {
  it('the default weight table sums to 1', () => {
    expect(scoringWeightsSchema.safeParse(DEFAULT_SCORING_WEIGHTS).success).toBe(true);
  });

  it('rejects a weight table that does not sum to 1', () => {
    const broken = { ...DEFAULT_SCORING_WEIGHTS, MARKET_DEMAND: 0.5 };
    expect(scoringWeightsSchema.safeParse(broken).success).toBe(false);
  });
});

describe('computeScore', () => {
  it('returns the uniform score when every criterion is identical', () => {
    const result = computeScore(fullScoreSet(70));
    expect(result.finalScore).toBeCloseTo(70, 5);
    expect(result.classification).toBe('PROMISING');
    expect(result.missingCriteria).toHaveLength(0);
  });

  it('weights criteria according to the table', () => {
    const scores = fullScoreSet(0).map((s) =>
      s.criterion === 'MARKET_DEMAND' ? { ...s, rawScore: 100 } : s,
    );
    // MARKET_DEMAND is worth 20% of the total.
    expect(computeScore(scores).finalScore).toBeCloseTo(20, 5);
  });

  it('renormalises instead of treating missing criteria as zero', () => {
    const partial = fullScoreSet(80).filter((s) => s.criterion !== 'ASO_POTENTIAL');
    const result = computeScore(partial);
    expect(result.finalScore).toBeCloseTo(80, 5);
    expect(result.missingCriteria).toEqual(['ASO_POTENTIAL']);
  });

  it('is empty-safe', () => {
    const result = computeScore([]);
    expect(result.finalScore).toBe(0);
    expect(result.classification).toBe('KILL');
    expect(result.breakdown).toHaveLength(0);
  });

  it('clamps out-of-range raw scores', () => {
    const result = computeScore(fullScoreSet(180));
    expect(result.finalScore).toBeCloseTo(100, 5);
  });

  it('keeps the last score when a criterion is provided twice', () => {
    const scores = [...fullScoreSet(10), { ...fullScoreSet(90)[0]! }];
    const result = computeScore(scores);
    const marketDemand = result.breakdown.find((b) => b.criterion === 'MARKET_DEMAND');
    expect(marketDemand?.rawScore).toBe(90);
  });

  it('reports a weighted confidence', () => {
    const scores = fullScoreSet(50, 0.4);
    expect(computeScore(scores).confidenceScore).toBeCloseTo(0.4, 5);
  });
});

describe('classifyScore', () => {
  it.each([
    [0, 'KILL'],
    [39, 'KILL'],
    [40, 'LOW_POTENTIAL'],
    [59.9, 'LOW_POTENTIAL'],
    [60, 'PROMISING'],
    [74, 'PROMISING'],
    [75, 'HIGH_POTENTIAL'],
    [89, 'HIGH_POTENTIAL'],
    [90, 'EXCEPTIONAL'],
    [100, 'EXCEPTIONAL'],
  ])('classifies %s as %s', (score, expected) => {
    expect(classifyScore(score)).toBe(expected);
  });

  it('clamps values outside 0-100', () => {
    expect(classifyScore(-10)).toBe('KILL');
    expect(classifyScore(1000)).toBe('EXCEPTIONAL');
    expect(classifyScore(Number.NaN)).toBe('KILL');
  });
});
