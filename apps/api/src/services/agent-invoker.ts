import type { PrismaClient } from '@prisma/client';
import type { Orchestrator } from '../agents/orchestrator.js';
import { AgentExecutionError, NotFoundError } from '../core/errors.js';

/**
 * Runs a single agent outside the research pipeline (PRD, prompt engineering).
 *
 * It goes through the same orchestrator as a full run so that budget limits,
 * retries, cost accounting and execution telemetry behave identically — the
 * only difference is that the graph has one node and the subject is injected
 * into the initial blackboard state.
 */
export class AgentInvoker {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly orchestrator: Orchestrator,
  ) {}

  async invoke<T>(options: {
    projectId: string;
    agentKey: string;
    initialState: Record<string, unknown>;
  }): Promise<{ output: T; runId: string }> {
    const project = await this.prisma.researchProject.findUnique({
      where: { id: options.projectId },
    });
    if (!project) throw new NotFoundError('ResearchProject', options.projectId);

    const run = await this.prisma.researchRun.create({
      data: {
        projectId: project.id,
        status: 'PENDING',
        budgetUsd: project.budgetUsd,
        tokenBudget: project.tokenBudget,
      },
    });

    const result = await this.orchestrator.execute({
      runId: run.id,
      brief: {
        projectId: project.id,
        sector: project.sector,
        country: project.country,
        platform: project.platform,
        language: project.language,
        timeframe: project.timeframe,
        objective: project.objective ?? undefined,
      },
      budgetUsd: Number(project.budgetUsd),
      tokenBudget: project.tokenBudget,
      agentKeys: [options.agentKey],
      initialState: options.initialState,
    });

    const output = result.state[options.agentKey] as T | undefined;
    if (output === undefined) {
      const failure = result.failed.find((entry) => entry.agentKey === options.agentKey);
      throw new AgentExecutionError(
        failure?.error ?? `Agent '${options.agentKey}' produced no output`,
        options.agentKey,
      );
    }

    return { output, runId: run.id };
  }
}
