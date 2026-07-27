import { SCORING_CRITERIA, type CriterionScore } from '@aiaf/shared';
import { afterEach, describe, expect, it } from 'vitest';
import {
  computeScoring,
  denormaliseScores,
  getActiveWeights,
  setActiveWeights,
} from './engine.js';

function scores(overrides: Partial<Record<string, number>> = {}): CriterionScore[] {
  return SCORING_CRITERIA.map((criterion) => ({
    criterion,
    rawScore: overrides[criterion] ?? 60,
    rationale: 'because',
    evidence: [],
    confidence: 0.5,
  }));
}

const defaults = getActiveWeights();
afterEach(() => setActiveWeights(defaults));

describe('scoring engine', () => {
  it('aggregates through the shared deterministic function', () => {
    expect(computeScoring(scores()).finalScore).toBeCloseTo(60, 5);
  });

  it('uses the configured weight set', () => {
    setActiveWeights({
      MARKET_DEMAND: 1,
      COMPETITION: 0,
      USER_PAIN: 0,
      MONETIZATION: 0,
      RETENTION_POTENTIAL: 0,
      TECHNICAL_FEASIBILITY: 0,
      ASO_POTENTIAL: 0,
      MAINTENANCE_COST: 0,
      STRATEGIC_FIT: 0,
    });

    expect(computeScoring(scores({ MARKET_DEMAND: 90 })).finalScore).toBeCloseTo(90, 5);
  });

  it('denormalises the five headline scores kept on the opportunity row', () => {
    const result = computeScoring(
      scores({ MARKET_DEMAND: 80, COMPETITION: 70, MONETIZATION: 65, TECHNICAL_FEASIBILITY: 55, RETENTION_POTENTIAL: 45 }),
    );

    expect(denormaliseScores(result)).toMatchObject({
      marketScore: 80,
      competitionScore: 70,
      monetizationScore: 65,
      technicalScore: 55,
      retentionScore: 45,
    });
  });

  it('reports null for a criterion the agent did not score', () => {
    const partial = scores().filter((score) => score.criterion !== 'MONETIZATION');
    expect(denormaliseScores(computeScoring(partial)).monetizationScore).toBeNull();
  });
});
