import { BudgetExceededError } from '../core/errors.js';

export interface BudgetSnapshot {
  spentUsd: number;
  usedTokens: number;
  budgetUsd: number;
  tokenBudget: number;
  remainingUsd: number;
  remainingTokens: number;
}

/**
 * Hard spend ceiling for a single research run.
 *
 * Two rules from the spec are enforced here: a run can never exceed its USD or
 * token budget, and there is no automatic escalation — once the ceiling is hit
 * the run stops and reports what it produced so far.
 */
export class BudgetTracker {
  private spentUsd = 0;
  private usedTokens = 0;

  constructor(
    private readonly budgetUsd: number,
    private readonly tokenBudget: number,
  ) {}

  /**
   * Pre-flight check run before every model call. `estimatedTokens` is a rough
   * prompt estimate; the check is intentionally conservative so an expensive
   * call is refused *before* it is billed rather than after.
   */
  assertCanSpend(estimatedTokens: number, estimatedCostUsd: number): void {
    if (this.usedTokens + estimatedTokens > this.tokenBudget) {
      throw new BudgetExceededError(
        `Token budget exhausted: ${this.usedTokens}/${this.tokenBudget} used, ` +
          `${estimatedTokens} more requested`,
        this.snapshot(),
      );
    }
    if (this.spentUsd + estimatedCostUsd > this.budgetUsd) {
      throw new BudgetExceededError(
        `Cost budget exhausted: $${this.spentUsd.toFixed(4)}/$${this.budgetUsd.toFixed(2)} used`,
        this.snapshot(),
      );
    }
  }

  record(tokens: number, costUsd: number): void {
    this.usedTokens += tokens;
    this.spentUsd += costUsd;
  }

  get exhausted(): boolean {
    return this.spentUsd >= this.budgetUsd || this.usedTokens >= this.tokenBudget;
  }

  snapshot(): BudgetSnapshot {
    return {
      spentUsd: Math.round(this.spentUsd * 1_000_000) / 1_000_000,
      usedTokens: this.usedTokens,
      budgetUsd: this.budgetUsd,
      tokenBudget: this.tokenBudget,
      remainingUsd: Math.max(0, this.budgetUsd - this.spentUsd),
      remainingTokens: Math.max(0, this.tokenBudget - this.usedTokens),
    };
  }
}
