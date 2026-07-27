import type { DashboardOverview } from '@aiaf/shared';
import type { PrismaClient } from '@prisma/client';

/**
 * Dashboard aggregates.
 *
 * Revenue, ROI and conversion depend on post-launch data that V1 does not
 * collect, so those fields return null rather than zero: "no data" and "zero
 * revenue" are different statements and the UI renders them differently.
 */
export class AnalyticsService {
  constructor(private readonly prisma: PrismaClient) {}

  async overview(ownerId: string): Promise<DashboardOverview> {
    const scope = { project: { ownerId } };

    const [
      opportunitiesAnalyzed,
      opportunitiesApproved,
      opportunitiesRejected,
      prdsGenerated,
      promptsGenerated,
      appsGenerated,
      appsReleased,
      statusGroups,
      scoreAggregate,
      costAggregate,
      revenueAggregate,
    ] = await Promise.all([
      this.prisma.opportunity.count({ where: scope }),
      this.prisma.opportunity.count({
        where: {
          ...scope,
          status: {
            in: ['APPROVED', 'PRD_GENERATED', 'CODE_PROMPT_GENERATED', 'DEVELOPMENT', 'RELEASED', 'MONITORING', 'SCALED'],
          },
        },
      }),
      this.prisma.opportunity.count({ where: { ...scope, status: { in: ['REJECTED', 'KILLED'] } } }),
      this.prisma.pRD.count({ where: { opportunity: scope } }),
      this.prisma.prompt.count({ where: { opportunity: scope } }),
      this.prisma.codeGenerationProject.count({ where: { opportunity: scope } }),
      this.prisma.app.count({ where: { opportunity: scope, status: 'RELEASED' } }),
      this.prisma.opportunity.groupBy({ by: ['status'], where: scope, _count: true }),
      this.prisma.opportunity.aggregate({ where: scope, _avg: { finalScore: true } }),
      this.prisma.researchRun.aggregate({
        where: { project: { ownerId } },
        _sum: { totalCostUsd: true },
      }),
      this.prisma.appMetric.aggregate({
        where: { app: { opportunity: scope } },
        _sum: { revenueUsd: true, costUsd: true },
        _count: true,
      }),
    ]);

    const hasPostLaunchData = revenueAggregate._count > 0;
    const revenueUsd = hasPostLaunchData ? Number(revenueAggregate._sum.revenueUsd ?? 0) : null;
    const appCostUsd = hasPostLaunchData ? Number(revenueAggregate._sum.costUsd ?? 0) : 0;
    const totalLlmCostUsd = Number(costAggregate._sum.totalCostUsd ?? 0);

    const investment = totalLlmCostUsd + appCostUsd;
    const roi =
      revenueUsd !== null && investment > 0
        ? Math.round(((revenueUsd - investment) / investment) * 10000) / 10000
        : null;

    return {
      opportunitiesAnalyzed,
      opportunitiesApproved,
      opportunitiesRejected,
      prdsGenerated,
      promptsGenerated,
      appsGenerated,
      appsReleased,
      revenueUsd,
      roi,
      // Share of analysed opportunities a human approved: the platform's own
      // funnel, which V1 *can* measure.
      conversionRate:
        opportunitiesAnalyzed > 0
          ? Math.round((opportunitiesApproved / opportunitiesAnalyzed) * 10000) / 10000
          : null,
      // Requires released apps to judge; null until then.
      successRate: appsReleased > 0 ? Math.round((appsReleased / Math.max(appsGenerated, 1)) * 10000) / 10000 : null,
      totalLlmCostUsd: Math.round(totalLlmCostUsd * 1_000_000) / 1_000_000,
      averageScore:
        scoreAggregate._avg.finalScore !== null
          ? Math.round(scoreAggregate._avg.finalScore * 100) / 100
          : null,
      byStatus: Object.fromEntries(
        statusGroups.map((group) => [group.status, group._count as unknown as number]),
      ),
    };
  }

  /** Kanban board: opportunities grouped by pipeline status. */
  async pipeline(ownerId: string) {
    return this.prisma.opportunity.findMany({
      where: { project: { ownerId } },
      select: {
        id: true,
        title: true,
        category: true,
        finalScore: true,
        confidenceScore: true,
        classification: true,
        status: true,
        updatedAt: true,
        project: { select: { name: true } },
      },
      orderBy: [{ finalScore: 'desc' }, { updatedAt: 'desc' }],
      take: 500,
    });
  }

  /** Per-agent cost and latency, used to tune model tiers. */
  async agentCosts(ownerId: string) {
    const grouped = await this.prisma.agentExecution.groupBy({
      by: ['agentKey', 'status'],
      where: { run: { project: { ownerId } } },
      _sum: { costUsd: true, promptTokens: true, completionTokens: true },
      _avg: { durationMs: true },
      _count: true,
    });

    return grouped.map((entry) => ({
      agentKey: entry.agentKey,
      status: entry.status,
      executions: entry._count as unknown as number,
      costUsd: Number(entry._sum.costUsd ?? 0),
      promptTokens: entry._sum.promptTokens ?? 0,
      completionTokens: entry._sum.completionTokens ?? 0,
      averageDurationMs: Math.round(entry._avg.durationMs ?? 0),
    }));
  }
}
