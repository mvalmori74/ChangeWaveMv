import type { CreateResearchProjectRequest } from '@aiaf/shared';
import type { Prisma, PrismaClient } from '@prisma/client';
import { RESEARCH_PIPELINE_ROOTS } from '../agents/definitions/index.js';
import type { Orchestrator } from '../agents/orchestrator.js';
import type { ResearchBrief } from '../agents/types.js';
import type { CompetitorAnalysisOutput } from '../agents/definitions/competitor-analysis.agent.js';
import type { MarketResearchOutput } from '../agents/definitions/market-research.agent.js';
import type { MonetizationOutput } from '../agents/definitions/monetization.agent.js';
import type { OpportunityScoringOutput } from '../agents/definitions/opportunity-scoring.agent.js';
import type { OpportunitySynthesisOutput } from '../agents/definitions/opportunity-synthesis.agent.js';
import type { ProblemDiscoveryOutput } from '../agents/definitions/problem-discovery.agent.js';
import type { ReviewAnalysisOutput } from '../agents/definitions/review-analysis.agent.js';
import { NotFoundError, toErrorMessage } from '../core/errors.js';
import type { AppLogger } from '../core/logger.js';
import type { JobQueue } from '../queue/types.js';
import { denormaliseScores } from '../scoring/engine.js';
import type { AuditService } from './audit.service.js';

export const RESEARCH_RUN_JOB = 'research-run';

export interface ResearchRunJobPayload {
  runId: string;
}

/**
 * Owns the research lifecycle: projects, runs, and the translation of a run's
 * blackboard into persisted domain entities (markets, competitors, insights,
 * pain points, opportunities and their score breakdowns).
 */
export class ResearchService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly orchestrator: Orchestrator,
    private readonly queue: JobQueue,
    private readonly audit: AuditService,
    private readonly logger: AppLogger,
    private readonly defaults: { budgetUsd: number; tokenBudget: number },
  ) {}

  async createProject(ownerId: string, request: CreateResearchProjectRequest) {
    const project = await this.prisma.researchProject.create({
      data: {
        name: request.name,
        sector: request.sector,
        country: request.country,
        platform: request.platform,
        language: request.language,
        timeframe: request.timeframe,
        objective: request.objective ?? null,
        budgetUsd: request.budgetUsd ?? this.defaults.budgetUsd,
        tokenBudget: request.tokenBudget ?? this.defaults.tokenBudget,
        ownerId,
      },
    });

    await this.audit.record({
      actorId: ownerId,
      action: 'research_project.created',
      entityType: 'ResearchProject',
      entityId: project.id,
      after: { name: project.name, sector: project.sector },
    });

    return project;
  }

  async listProjects(ownerId: string) {
    return this.prisma.researchProject.findMany({
      where: { ownerId },
      orderBy: { createdAt: 'desc' },
      include: {
        _count: { select: { opportunities: true, runs: true } },
        runs: { orderBy: { createdAt: 'desc' }, take: 1 },
      },
    });
  }

  async getProject(projectId: string, ownerId: string) {
    const project = await this.prisma.researchProject.findFirst({
      where: { id: projectId, ownerId },
      include: {
        runs: { orderBy: { createdAt: 'desc' } },
        _count: { select: { opportunities: true } },
      },
    });
    if (!project) throw new NotFoundError('ResearchProject', projectId);
    return project;
  }

  /** Creates a queued run and returns immediately; execution is asynchronous. */
  async startRun(projectId: string, ownerId: string) {
    const project = await this.getProject(projectId, ownerId);

    const run = await this.prisma.researchRun.create({
      data: {
        projectId: project.id,
        status: 'PENDING',
        budgetUsd: project.budgetUsd,
        tokenBudget: project.tokenBudget,
      },
    });

    await this.prisma.researchProject.update({
      where: { id: project.id },
      data: { status: 'ACTIVE' },
    });

    await this.audit.record({
      actorId: ownerId,
      action: 'research_run.queued',
      entityType: 'ResearchRun',
      entityId: run.id,
      metadata: { projectId: project.id },
    });

    await this.queue.enqueue<ResearchRunJobPayload>(RESEARCH_RUN_JOB, { runId: run.id });
    return run;
  }

  async getRun(runId: string) {
    const run = await this.prisma.researchRun.findUnique({
      where: { id: runId },
      include: {
        executions: { orderBy: { startedAt: 'asc' } },
        project: true,
        _count: { select: { sources: true, opportunities: true } },
      },
    });
    if (!run) throw new NotFoundError('ResearchRun', runId);
    return run;
  }

  /** Job handler: executes the pipeline and persists everything it produced. */
  async executeRun(runId: string): Promise<void> {
    const run = await this.prisma.researchRun.findUnique({
      where: { id: runId },
      include: { project: true },
    });
    if (!run) throw new NotFoundError('ResearchRun', runId);

    const brief: ResearchBrief = {
      projectId: run.projectId,
      sector: run.project.sector,
      country: run.project.country,
      platform: run.project.platform,
      language: run.project.language,
      timeframe: run.project.timeframe,
      objective: run.project.objective ?? undefined,
    };

    const result = await this.orchestrator.execute({
      runId: run.id,
      brief,
      budgetUsd: Number(run.budgetUsd),
      tokenBudget: run.tokenBudget,
      // Only the research pipeline: naming its leaf pulls in every dependency
      // and leaves the on-demand delivery agents (PRD, prompt) out of the run.
      agentKeys: [...RESEARCH_PIPELINE_ROOTS],
    });

    try {
      await this.persistRunOutputs(run.id, run.projectId, result.state);
    } catch (error) {
      // The agents already ran and were paid for: a persistence failure is
      // reported but must not erase the run record itself.
      this.logger.error(
        { runId: run.id, error: toErrorMessage(error) },
        'failed to persist research outputs',
      );
      await this.prisma.researchRun.update({
        where: { id: run.id },
        data: { error: `Persistence failed: ${toErrorMessage(error)}` },
      });
    }
  }

  /**
   * Maps the blackboard onto relational entities. Each block is independent so
   * a missing agent output degrades the result instead of failing the write.
   */
  private async persistRunOutputs(
    runId: string,
    projectId: string,
    state: Record<string, unknown>,
  ): Promise<void> {
    const market = state['market-research'] as MarketResearchOutput | undefined;
    const competitors = state['competitor-analysis'] as CompetitorAnalysisOutput | undefined;
    const reviews = state['review-analysis'] as ReviewAnalysisOutput | undefined;
    const problems = state['problem-discovery'] as ProblemDiscoveryOutput | undefined;
    const synthesis = state['opportunity-synthesis'] as OpportunitySynthesisOutput | undefined;
    const monetization = state['monetization'] as MonetizationOutput | undefined;
    const feasibility = state['technical-feasibility'] as
      | { assessments: { candidateId: string; feasibilityScore: number }[] }
      | undefined;
    const scoring = state['opportunity-scoring'] as OpportunityScoringOutput | undefined;

    let marketId: string | null = null;
    if (market) {
      const created = await this.prisma.market.create({
        data: {
          projectId,
          name: market.market.name,
          description: market.market.description,
          sizeEstimate: (market.market.sizeEstimate ?? undefined) as Prisma.InputJsonValue,
          growthTrend: market.market.growthTrend,
          seasonality: market.market.seasonality,
          demandLevel: market.market.demandLevel,
          targetUsers: market.targetUsers as unknown as Prisma.InputJsonValue,
          keyProblems: market.keyProblems as unknown as Prisma.InputJsonValue,
          saturationScore: competitors?.saturationScore ?? null,
        },
      });
      marketId = created.id;
    }

    const competitorIdByName = new Map<string, string>();
    for (const competitor of competitors?.competitors ?? []) {
      const created = await this.prisma.competitor.create({
        data: {
          projectId,
          marketId,
          name: competitor.name,
          description: competitor.description,
          url: competitor.url,
          businessModel: competitor.businessModel,
          pricing: competitor.pricing,
          rating: competitor.rating,
          reviewCount: competitor.reviewCount,
          installsRange: competitor.installsRange,
          strengths: competitor.strengths,
          weaknesses: competitor.weaknesses,
          features: competitor.features,
          isDirect: competitor.isDirect,
          evidenceType: competitor.evidenceType,
          sourceUrls: competitor.sourceUrls,
        },
      });
      competitorIdByName.set(competitor.name, created.id);
    }

    if (reviews) {
      const groups = [
        ['PAIN_POINT', reviews.painPoints],
        ['FEATURE_REQUEST', reviews.featureRequests],
        ['BUG', reviews.bugs],
        ['COMPLAINT', reviews.complaints],
        ['PRICING', reviews.pricingSignals],
        ['UX', reviews.uxSignals],
        ['ADS', reviews.adsSignals],
      ] as const;

      for (const [type, insights] of groups) {
        for (const insight of insights) {
          await this.prisma.reviewInsight.create({
            data: {
              projectId,
              competitorId: insight.competitor
                ? (competitorIdByName.get(insight.competitor) ?? null)
                : null,
              type,
              summary: insight.summary,
              quote: insight.quote,
              sentiment: insight.sentiment,
              frequency: insight.frequency,
              evidenceType: insight.evidenceType,
              sourceUrls: insight.sourceUrls,
            },
          });
        }
      }
    }

    const painPointIds: string[] = [];
    for (const problem of problems?.problems ?? []) {
      const created = await this.prisma.painPoint.create({
        data: {
          projectId,
          problem: problem.problem,
          targetUser: problem.targetUser,
          frequency: problem.frequency,
          severity: problem.severity,
          currentSolution: problem.currentSolution,
          marketGap: problem.marketGap,
          evidenceType: problem.evidenceType,
          confidence: problem.confidence,
          sourceUrls: problem.sourceUrls,
        },
      });
      painPointIds.push(created.id);
    }

    if (!synthesis) return;

    const monetizationByCandidate = new Map(
      (monetization?.plans ?? []).map((plan) => [plan.candidateId, plan]),
    );
    const feasibilityByCandidate = new Map(
      (feasibility?.assessments ?? []).map((assessment) => [assessment.candidateId, assessment]),
    );
    const scoreByCandidate = new Map(
      (scoring?.scores ?? []).map((score) => [score.candidateId, score]),
    );

    for (const candidate of synthesis.candidates) {
      const score = scoreByCandidate.get(candidate.candidateId);
      const computed = score?.computed ?? null;
      const denormalised = computed
        ? denormaliseScores({
            finalScore: computed.finalScore,
            classification: computed.classification,
            confidenceScore: computed.confidenceScore,
            breakdown: computed.breakdown,
            weightsVersion: computed.weightsVersion,
            missingCriteria: computed.missingCriteria,
          })
        : null;

      const opportunity = await this.prisma.opportunity.create({
        data: {
          projectId,
          runId,
          title: candidate.title,
          category: candidate.category,
          targetAudience: candidate.targetAudience,
          problem: candidate.problem,
          proposedSolution: candidate.proposedSolution,
          mvpSummary: {
            features: candidate.mvpFeatures,
            differentiators: candidate.differentiators,
          } as Prisma.InputJsonValue,
          risks: candidate.risks as unknown as Prisma.InputJsonValue,
          assumptions: candidate.assumptions as unknown as Prisma.InputJsonValue,
          evidenceType: candidate.evidenceType,
          monetization: (monetizationByCandidate.get(candidate.candidateId) ??
            undefined) as Prisma.InputJsonValue,
          feasibility: (feasibilityByCandidate.get(candidate.candidateId) ??
            undefined) as Prisma.InputJsonValue,
          marketScore: denormalised?.marketScore ?? null,
          competitionScore: denormalised?.competitionScore ?? null,
          monetizationScore: denormalised?.monetizationScore ?? null,
          technicalScore: denormalised?.technicalScore ?? null,
          retentionScore: denormalised?.retentionScore ?? null,
          finalScore: denormalised?.finalScore ?? null,
          confidenceScore: denormalised?.confidenceScore ?? null,
          classification: denormalised?.classification ?? null,
          // An opportunity nobody scored is still DISCOVERED, not SCORED.
          status: denormalised ? 'SCORED' : 'DISCOVERED',
          painPoints: painPointIds.length > 0
            ? { connect: painPointIds.map((id) => ({ id })) }
            : undefined,
        },
      });

      for (const entry of computed?.breakdown ?? []) {
        await this.prisma.opportunityScore.create({
          data: {
            opportunityId: opportunity.id,
            criterion: entry.criterion,
            weight: entry.weight,
            rawScore: entry.rawScore,
            weightedScore: entry.weightedScore,
            rationale: entry.rationale,
            evidence: entry.evidence,
            confidence: entry.confidence,
            weightsVersion: computed?.weightsVersion ?? 'v1',
          },
        });
      }
    }
  }
}
