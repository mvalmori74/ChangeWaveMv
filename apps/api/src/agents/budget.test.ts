import { describe, expect, it } from 'vitest';
import { BudgetExceededError } from '../core/errors.js';
import { BudgetTracker } from './budget.js';

describe('BudgetTracker', () => {
  it('allows spend inside both ceilings', () => {
    const budget = new BudgetTracker(1, 1000);
    expect(() => budget.assertCanSpend(500, 0.5)).not.toThrow();
  });

  it('refuses a call that would exceed the token ceiling', () => {
    const budget = new BudgetTracker(10, 1000);
    budget.record(900, 0.1);
    expect(() => budget.assertCanSpend(200, 0)).toThrow(BudgetExceededError);
  });

  it('refuses a call that would exceed the cost ceiling', () => {
    const budget = new BudgetTracker(1, 1_000_000);
    budget.record(10, 0.95);
    expect(() => budget.assertCanSpend(10, 0.1)).toThrow(BudgetExceededError);
  });

  it('reports what is left', () => {
    const budget = new BudgetTracker(2, 1000);
    budget.record(400, 0.5);

    const snapshot = budget.snapshot();
    expect(snapshot.usedTokens).toBe(400);
    expect(snapshot.remainingTokens).toBe(600);
    expect(snapshot.spentUsd).toBeCloseTo(0.5, 6);
    expect(snapshot.remainingUsd).toBeCloseTo(1.5, 6);
  });

  it('never reports a negative remainder', () => {
    const budget = new BudgetTracker(1, 100);
    budget.record(500, 5);

    const snapshot = budget.snapshot();
    expect(snapshot.remainingTokens).toBe(0);
    expect(snapshot.remainingUsd).toBe(0);
    expect(budget.exhausted).toBe(true);
  });

  it('carries the snapshot in the error so the run can report where it stopped', () => {
    const budget = new BudgetTracker(1, 100);
    try {
      budget.assertCanSpend(500, 0);
      throw new Error('should have thrown');
    } catch (error) {
      expect(error).toBeInstanceOf(BudgetExceededError);
      expect((error as BudgetExceededError).details).toMatchObject({ tokenBudget: 100 });
    }
  });
});
