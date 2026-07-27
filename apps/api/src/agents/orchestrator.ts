import type { AgentExecutionStatus, ResearchRunStatus } from '@aiaf/shared';
import { BudgetExceededError, toErrorMessage } from '../core/errors.js';
import type { AppLogger } from '../core/logger.js';
import type { SearchProvider } from '../providers/search/types.js';
import { InMemoryBlackboard } from './blackboard.js';
import { BudgetTracker } from './budget.js';
import type { AgentRegistry } from './registry.js';
import type { AgentRunner } from './runner.js';
import type { AgentRunOutcome, ResearchBrief } from './types.js';

/**
 * Persistence port for a run. Keeping it an interface means the orchestrator is
 * unit-testable without a database and could later write to another store.
 */
export interface RunRecorder {
  recordExecution(record: {
    runId: string;
    agentKey: string;
    status: AgentExecutionStatus;
    input: unknown;
    output?: unknown;
    model?: string | undefined;
    promptTokens: number;
    completionTokens: number;
    costUsd: number;
    durationMs: number;
    attempt: number;
    error?: string | undefined;
    startedAt: Date;
    finishedAt: Date;
  }): Promise<void>;

  recordSources(
    runId: string,
    sources: {
      url: string;
      title: string;
      snippet: string;
      publisher?: string | undefined;
      publishedAt?: string | undefined;
      sourceType: string;
      credibilityScore: number;
    }[],
  ): Promise<void>;

  updateRun(
    runId: string,
    data: {
      status?: ResearchRunStatus;
      state?: Record<string, unknown>;
      totalCostUsd?: number;
      totalTokens?: number;
      error?: string | null;
      startedAt?: Date;
      finishedAt?: Date;
    },
  ): Promise<void>;
}

export interface OrchestratorRunOptions {
  runId: string;
  brief: ResearchBrief;
  budgetUsd: number;
  tokenBudget: number;
  /** Root agents to run; dependencies are resolved automatically. */
  agentKeys?: string[];
  initialState?: Record<string, unknown>;
  signal?: AbortSignal;
}

export interface OrchestratorRunResult {
  runId: string;
  status: ResearchRunStatus;
  state: Record<string, unknown>;
  executed: string[];
  skipped: string[];
  failed: { agentKey: string; error: string }[];
  totalCostUsd: number;
  totalTokens: number;
}

/**
 * Runs a research pipeline.
 *
 * It knows nothing about any particular agent: it asks the registry for a
 * topological order, executes each agent through the engine, and publishes the
 * result on the blackboard for downstream agents. Failure of one agent does not
 * abort the run — its dependants are skipped and the run finishes PARTIAL, so a
 * single bad answer never throws away the work already paid for.
 */
export class Orchestrator {
  private readonly recorder: RunRecorder;

  constructor(
    private readonly registry: AgentRegistry,
    private readonly runner: AgentRunner,
    private readonly search: SearchProvider,
    recorder: RunRecorder,
    private readonly logger: AppLogger,
  ) {
    // Bookkeeping is best-effort: losing a telemetry write must never throw
    // away agent output that has already been paid for.
    this.recorder = protectRecorder(recorder, logger);
  }

  async execute(options: OrchestratorRunOptions): Promise<OrchestratorRunResult> {
    const blackboard = new InMemoryBlackboard(options.initialState ?? {});
    const budget = new BudgetTracker(options.budgetUsd, options.tokenBudget);
    const order = this.registry.resolveExecutionOrder(options.agentKeys);
    const logger = this.logger.child({ runId: options.runId });

    const executed: string[] = [];
    const skipped: string[] = [];
    const failed: { agentKey: string; error: string }[] = [];
    let budgetStopped = false;

    await this.recorder.updateRun(options.runId, {
      status: 'RUNNING',
      startedAt: new Date(),
      error: null,
    });

    logger.info(
      { agents: order.map((definition) => definition.key) },
      'starting research run',
    );

    for (const definition of order) {
      const unmet = definition.dependencies.filter(
        (dependency) => blackboard.get(dependency) === undefined,
      );
      if (unmet.length > 0 || budgetStopped) {
        skipped.push(definition.key);
        await this.recordSkip(
          options.runId,
          definition.key,
          budgetStopped
            ? 'Run budget exhausted before this agent could start'
            : `Missing dependency output: ${unmet.join(', ')}`,
          budgetStopped ? 'BUDGET_EXCEEDED' : 'SKIPPED',
        );
        continue;
      }

      const context = {
        runId: options.runId,
        brief: options.brief,
        blackboard,
        search: this.search,
        budget,
        logger: logger.child({ agentKey: definition.key }),
        signal: options.signal,
      };
      const startedAt = new Date();

      try {
        const outcome: AgentRunOutcome = await this.runner.run(definition, context);
        blackboard.set(definition.key, outcome.output);
        executed.push(definition.key);

        await this.recorder.recordExecution({
          runId: options.runId,
          agentKey: definition.key,
          status: 'SUCCEEDED',
          input: definition.buildInput(context),
          output: outcome.output,
          model: outcome.model,
          promptTokens: outcome.promptTokens,
          completionTokens: outcome.completionTokens,
          costUsd: outcome.costUsd,
          durationMs: outcome.durationMs,
          attempt: outcome.attempts,
          startedAt,
          finishedAt: new Date(),
        });

        if (!outcome.evidence.synthetic && outcome.evidence.results.length > 0) {
          await this.recorder.recordSources(
            options.runId,
            outcome.evidence.results.map((result) => ({
              url: result.url,
              title: result.title,
              snippet: result.snippet,
              publisher: result.publisher,
              publishedAt: result.publishedAt,
              sourceType: result.sourceType,
              credibilityScore: result.score,
            })),
          );
        }

        // Persist after every agent: a run that dies halfway still keeps the
        // outputs already paid for.
        await this.recorder.updateRun(options.runId, {
          state: blackboard.snapshot(),
          totalCostUsd: budget.snapshot().spentUsd,
          totalTokens: budget.snapshot().usedTokens,
        });
      } catch (error) {
        const message = toErrorMessage(error);
        const isBudget = error instanceof BudgetExceededError;
        if (isBudget) budgetStopped = true;

        failed.push({ agentKey: definition.key, error: message });
        logger.error(
          { agentKey: definition.key, error: message, budgetStop: isBudget },
          'agent failed',
        );

        await this.recorder.recordExecution({
          runId: options.runId,
          agentKey: definition.key,
          status: isBudget ? 'BUDGET_EXCEEDED' : 'FAILED',
          input: definition.buildInput(context),
          promptTokens: 0,
          completionTokens: 0,
          costUsd: 0,
          durationMs: Date.now() - startedAt.getTime(),
          attempt: 1,
          error: message,
          startedAt,
          finishedAt: new Date(),
        });
      }
    }

    const status: ResearchRunStatus =
      executed.length === 0 ? 'FAILED' : failed.length > 0 || skipped.length > 0 ? 'PARTIAL' : 'COMPLETED';
    const snapshot = budget.snapshot();

    await this.recorder.updateRun(options.runId, {
      status,
      state: blackboard.snapshot(),
      totalCostUsd: snapshot.spentUsd,
      totalTokens: snapshot.usedTokens,
      finishedAt: new Date(),
      error: failed.length > 0 ? failed.map((f) => `${f.agentKey}: ${f.error}`).join('; ') : null,
    });

    logger.info(
      { status, executed, skipped, failed: failed.length, cost: snapshot.spentUsd },
      'research run finished',
    );

    return {
      runId: options.runId,
      status,
      state: blackboard.snapshot(),
      executed,
      skipped,
      failed,
      totalCostUsd: snapshot.spentUsd,
      totalTokens: snapshot.usedTokens,
    };
  }

  private async recordSkip(
    runId: string,
    agentKey: string,
    reason: string,
    status: AgentExecutionStatus,
  ): Promise<void> {
    const now = new Date();
    await this.recorder.recordExecution({
      runId,
      agentKey,
      status,
      input: {},
      promptTokens: 0,
      completionTokens: 0,
      costUsd: 0,
      durationMs: 0,
      attempt: 0,
      error: reason,
      startedAt: now,
      finishedAt: now,
    });
  }
}

/**
 * Wraps a recorder so a persistence failure is logged instead of propagating.
 *
 * Telemetry is not the deliverable: a run that has already spent money on agent
 * calls must be allowed to finish and return its results even if the database
 * is momentarily unreachable.
 */
function protectRecorder(recorder: RunRecorder, logger: AppLogger): RunRecorder {
  const guard = async (operation: string, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
    } catch (error) {
      logger.error({ operation, error: toErrorMessage(error) }, 'run bookkeeping failed');
    }
  };

  return {
    recordExecution: (record) => guard('recordExecution', () => recorder.recordExecution(record)),
    recordSources: (runId, sources) =>
      guard('recordSources', () => recorder.recordSources(runId, sources)),
    updateRun: (runId, data) => guard('updateRun', () => recorder.updateRun(runId, data)),
  };
}
