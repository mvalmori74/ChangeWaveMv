import { Prisma, type PrismaClient } from '@prisma/client';
import type { RunRecorder } from '../agents/orchestrator.js';
import type { AppLogger } from '../core/logger.js';
import { toErrorMessage } from '../core/errors.js';

/**
 * Writes agent executions, sources and run progress to PostgreSQL.
 *
 * Recording is best-effort by design: a failure to write telemetry must never
 * abort a research run that is otherwise producing value, so write errors are
 * logged and swallowed.
 */
export class PrismaRunRecorder implements RunRecorder {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: AppLogger,
  ) {}

  async recordExecution(record: Parameters<RunRecorder['recordExecution']>[0]): Promise<void> {
    try {
      const agent = await this.prisma.agent.findUnique({
        where: { key: record.agentKey },
        select: { id: true },
      });
      await this.prisma.agentExecution.create({
        data: {
          runId: record.runId,
          agentKey: record.agentKey,
          agentId: agent?.id ?? null,
          status: record.status,
          input: record.input as Prisma.InputJsonValue,
          output: (record.output ?? Prisma.JsonNull) as Prisma.InputJsonValue,
          model: record.model ?? null,
          promptTokens: record.promptTokens,
          completionTokens: record.completionTokens,
          costUsd: record.costUsd,
          durationMs: record.durationMs,
          attempt: record.attempt,
          error: record.error ?? null,
          startedAt: record.startedAt,
          finishedAt: record.finishedAt,
        },
      });
    } catch (error) {
      this.logger.error(
        { runId: record.runId, agentKey: record.agentKey, error: toErrorMessage(error) },
        'failed to record agent execution',
      );
    }
  }

  async recordSources(
    runId: string,
    sources: Parameters<RunRecorder['recordSources']>[1],
  ): Promise<void> {
    if (sources.length === 0) return;
    try {
      await this.prisma.source.createMany({
        data: sources.map((source) => ({
          runId,
          url: source.url,
          title: source.title,
          snippet: source.snippet.slice(0, 2000),
          publisher: source.publisher ?? null,
          publishedAt: parseDate(source.publishedAt),
          sourceType: source.sourceType as never,
          credibilityScore: source.credibilityScore,
        })),
        // Agents share evidence, so the same URL turns up in several runs of
        // the same pipeline; the composite unique key absorbs that.
        skipDuplicates: true,
      });
    } catch (error) {
      this.logger.error({ runId, error: toErrorMessage(error) }, 'failed to record sources');
    }
  }

  async updateRun(
    runId: string,
    data: Parameters<RunRecorder['updateRun']>[1],
  ): Promise<void> {
    try {
      await this.prisma.researchRun.update({
        where: { id: runId },
        data: {
          ...(data.status ? { status: data.status } : {}),
          ...(data.state ? { state: data.state as Prisma.InputJsonValue } : {}),
          ...(data.totalCostUsd !== undefined ? { totalCostUsd: data.totalCostUsd } : {}),
          ...(data.totalTokens !== undefined ? { totalTokens: data.totalTokens } : {}),
          ...(data.error !== undefined ? { error: data.error } : {}),
          ...(data.startedAt ? { startedAt: data.startedAt } : {}),
          ...(data.finishedAt ? { finishedAt: data.finishedAt } : {}),
        },
      });
    } catch (error) {
      this.logger.error({ runId, error: toErrorMessage(error) }, 'failed to update run');
    }
  }
}

function parseDate(value: string | undefined): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}
