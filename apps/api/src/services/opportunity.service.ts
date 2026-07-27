import type { DecisionRequest, OpportunityFilters, Paginated } from '@aiaf/shared';
import type { Opportunity, Prisma, PrismaClient } from '@prisma/client';
import { ConflictError, NotFoundError, ValidationError } from '../core/errors.js';
import type { AuditService } from './audit.service.js';

/** Which decisions are legal from which status. */
const DECISION_TRANSITIONS: Record<string, { from: string[]; to: string }> = {
  APPROVE: { from: ['DISCOVERED', 'ANALYZING', 'SCORED', 'REJECTED'], to: 'APPROVED' },
  REJECT: { from: ['DISCOVERED', 'ANALYZING', 'SCORED', 'APPROVED'], to: 'REJECTED' },
  KILL: {
    from: ['DISCOVERED', 'ANALYZING', 'SCORED', 'APPROVED', 'DEVELOPMENT', 'RELEASED', 'MONITORING'],
    to: 'KILLED',
  },
  SCALE: { from: ['RELEASED', 'MONITORING'], to: 'SCALED' },
  KEEP: { from: ['RELEASED', 'MONITORING'], to: 'MONITORING' },
  IMPROVE: { from: ['RELEASED', 'MONITORING'], to: 'DEVELOPMENT' },
};

export class OpportunityService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly audit: AuditService,
  ) {}

  async list(ownerId: string, filters: OpportunityFilters): Promise<Paginated<Opportunity>> {
    const where: Prisma.OpportunityWhereInput = {
      project: {
        ownerId,
        ...(filters.country ? { country: filters.country } : {}),
        ...(filters.platform ? { platform: filters.platform } : {}),
      },
      ...(filters.projectId ? { projectId: filters.projectId } : {}),
      ...(filters.category ? { category: { contains: filters.category, mode: 'insensitive' } } : {}),
      ...(filters.status ? { status: filters.status } : {}),
      ...(filters.minScore !== undefined || filters.maxScore !== undefined
        ? {
            finalScore: {
              ...(filters.minScore !== undefined ? { gte: filters.minScore } : {}),
              ...(filters.maxScore !== undefined ? { lte: filters.maxScore } : {}),
            },
          }
        : {}),
      ...(filters.search
        ? {
            OR: [
              { title: { contains: filters.search, mode: 'insensitive' as const } },
              { problem: { contains: filters.search, mode: 'insensitive' as const } },
            ],
          }
        : {}),
    };

    const [items, total] = await Promise.all([
      this.prisma.opportunity.findMany({
        where,
        orderBy: { [filters.sortBy]: filters.sortDir },
        skip: (filters.page - 1) * filters.pageSize,
        take: filters.pageSize,
        include: { project: { select: { name: true, country: true, platform: true } } },
      }),
      this.prisma.opportunity.count({ where }),
    ]);

    return { items, total, page: filters.page, pageSize: filters.pageSize };
  }

  async get(opportunityId: string, ownerId: string) {
    const opportunity = await this.prisma.opportunity.findFirst({
      where: { id: opportunityId, project: { ownerId } },
      include: {
        project: true,
        scores: { orderBy: { weight: 'desc' } },
        painPoints: true,
        prds: { orderBy: { version: 'desc' } },
        prompts: { orderBy: { version: 'desc' } },
        codeProjects: { orderBy: { createdAt: 'desc' } },
        decisions: { orderBy: { createdAt: 'desc' }, include: { decidedBy: { select: { name: true } } } },
        run: {
          select: {
            id: true,
            status: true,
            sources: { orderBy: { credibilityScore: 'desc' }, take: 50 },
          },
        },
      },
    });
    if (!opportunity) throw new NotFoundError('Opportunity', opportunityId);
    return opportunity;
  }

  /** Everything the detail page needs about the run that produced this one. */
  async getResearchContext(opportunityId: string, ownerId: string) {
    const opportunity = await this.prisma.opportunity.findFirst({
      where: { id: opportunityId, project: { ownerId } },
      select: { projectId: true },
    });
    if (!opportunity) throw new NotFoundError('Opportunity', opportunityId);

    const [competitors, reviewInsights, painPoints, market] = await Promise.all([
      this.prisma.competitor.findMany({ where: { projectId: opportunity.projectId } }),
      this.prisma.reviewInsight.findMany({
        where: { projectId: opportunity.projectId },
        take: 200,
      }),
      this.prisma.painPoint.findMany({ where: { projectId: opportunity.projectId } }),
      this.prisma.market.findFirst({
        where: { projectId: opportunity.projectId },
        orderBy: { createdAt: 'desc' },
      }),
    ]);

    return { competitors, reviewInsights, painPoints, market };
  }

  /**
   * Records a human decision.
   *
   * The AI recommendation already stored on the opportunity is never
   * overwritten: both sides are kept so the trail shows what the platform
   * suggested and what the human actually chose.
   */
  async decide(
    opportunityId: string,
    ownerId: string,
    userId: string,
    request: DecisionRequest,
  ) {
    const opportunity = await this.prisma.opportunity.findFirst({
      where: { id: opportunityId, project: { ownerId } },
    });
    if (!opportunity) throw new NotFoundError('Opportunity', opportunityId);

    const transition = DECISION_TRANSITIONS[request.type];
    if (!transition) throw new ValidationError(`Unsupported decision '${request.type}'`);
    if (!transition.from.includes(opportunity.status)) {
      throw new ConflictError(
        `Cannot ${request.type} an opportunity in status ${opportunity.status}`,
        { allowedFrom: transition.from },
      );
    }

    const [updated] = await this.prisma.$transaction([
      this.prisma.opportunity.update({
        where: { id: opportunityId },
        data: { status: transition.to as Prisma.OpportunityUpdateInput['status'] },
      }),
      this.prisma.decision.create({
        data: {
          opportunityId,
          type: request.type,
          aiRecommendation: opportunity.classification,
          aiRationale: opportunity.classification
            ? `Scored ${opportunity.finalScore ?? 'n/a'} (${opportunity.classification}) by the scoring engine`
            : null,
          aiConfidence: opportunity.confidenceScore,
          humanDecision: request.type,
          humanRationale: request.rationale,
          decidedById: userId,
          decidedAt: new Date(),
        },
      }),
    ]);

    await this.audit.record({
      actorId: userId,
      action: `opportunity.${request.type.toLowerCase()}`,
      entityType: 'Opportunity',
      entityId: opportunityId,
      before: { status: opportunity.status },
      after: { status: transition.to },
      metadata: { rationale: request.rationale },
    });

    return updated;
  }

  /** Used by the PRD and prompt agents as their subject. */
  async buildAgentContext(opportunityId: string, ownerId: string): Promise<Record<string, unknown>> {
    const opportunity = await this.get(opportunityId, ownerId);
    return {
      title: opportunity.title,
      category: opportunity.category,
      targetAudience: opportunity.targetAudience,
      problem: opportunity.problem,
      proposedSolution: opportunity.proposedSolution,
      mvpSummary: opportunity.mvpSummary,
      monetization: opportunity.monetization,
      feasibility: opportunity.feasibility,
      risks: opportunity.risks,
      assumptions: opportunity.assumptions,
      evidenceType: opportunity.evidenceType,
      finalScore: opportunity.finalScore,
      classification: opportunity.classification,
      confidenceScore: opportunity.confidenceScore,
      scores: opportunity.scores.map((score) => ({
        criterion: score.criterion,
        rawScore: score.rawScore,
        rationale: score.rationale,
      })),
      market: {
        sector: opportunity.project.sector,
        country: opportunity.project.country,
        platform: opportunity.project.platform,
        language: opportunity.project.language,
      },
      painPoints: opportunity.painPoints.map((painPoint) => ({
        problem: painPoint.problem,
        targetUser: painPoint.targetUser,
        severity: painPoint.severity,
      })),
    };
  }

  async setStatus(opportunityId: string, status: string): Promise<void> {
    await this.prisma.opportunity.update({
      where: { id: opportunityId },
      data: { status: status as Prisma.OpportunityUpdateInput['status'] },
    });
  }
}
