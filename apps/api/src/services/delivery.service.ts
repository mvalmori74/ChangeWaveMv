import type { PrismaClient } from '@prisma/client';
import type { Prisma } from '@prisma/client';
import {
  OPPORTUNITY_CONTEXT_KEY,
  prdContentSchema,
  type PrdGeneratorOutput,
} from '../agents/definitions/prd-generator.agent.js';
import {
  PRD_CONTEXT_KEY,
  promptEngineerOutputSchema,
  type PromptEngineerOutput,
} from '../agents/definitions/prompt-engineer.agent.js';
import { ConflictError, NotFoundError } from '../core/errors.js';
import {
  estimatePromptTokens,
  renderCodexPromptMarkdown,
} from '../export/codex-prompt-markdown.js';
import { renderPrdMarkdown } from '../export/prd-markdown.js';
import type { CodeGenerationProvider } from '../providers/codegen/types.js';
import type { AgentInvoker } from './agent-invoker.js';
import type { AuditService } from './audit.service.js';
import type { OpportunityService } from './opportunity.service.js';

/**
 * Delivery artefacts: PRD, Codex prompt and the code generation project.
 *
 * The human-in-the-loop gate lives here: a PRD can only be generated for an
 * approved opportunity, and a code generation project can only be created from
 * an existing prompt.
 */
export class DeliveryService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly opportunities: OpportunityService,
    private readonly invoker: AgentInvoker,
    private readonly codegen: CodeGenerationProvider,
    private readonly audit: AuditService,
    private readonly syntheticProvider: boolean,
  ) {}

  async generatePrd(opportunityId: string, ownerId: string, userId: string) {
    const opportunity = await this.opportunities.get(opportunityId, ownerId);
    if (!['APPROVED', 'PRD_GENERATED', 'CODE_PROMPT_GENERATED'].includes(opportunity.status)) {
      throw new ConflictError(
        'A PRD can only be generated for an approved opportunity. Approve it first.',
        { status: opportunity.status },
      );
    }

    const context = await this.opportunities.buildAgentContext(opportunityId, ownerId);
    const { output } = await this.invoker.invoke<PrdGeneratorOutput>({
      projectId: opportunity.projectId,
      agentKey: 'prd-generator',
      initialState: { [OPPORTUNITY_CONTEXT_KEY]: context },
    });

    const content = prdContentSchema.parse(output.content);
    const version = (await this.prisma.pRD.count({ where: { opportunityId } })) + 1;
    const markdown = renderPrdMarkdown(output.title, content, {
      opportunityTitle: opportunity.title,
      finalScore: opportunity.finalScore,
      classification: opportunity.classification,
      confidence: opportunity.confidenceScore,
      generatedAt: new Date(),
      synthetic: this.syntheticProvider,
    });

    const prd = await this.prisma.pRD.create({
      data: {
        opportunityId,
        version,
        title: output.title,
        content: content as unknown as Prisma.InputJsonValue,
        markdown,
        createdById: userId,
      },
    });

    if (opportunity.status === 'APPROVED') {
      await this.opportunities.setStatus(opportunityId, 'PRD_GENERATED');
    }

    await this.audit.record({
      actorId: userId,
      action: 'prd.generated',
      entityType: 'PRD',
      entityId: prd.id,
      metadata: { opportunityId, version },
    });

    return prd;
  }

  async getPrd(prdId: string, ownerId: string) {
    const prd = await this.prisma.pRD.findFirst({
      where: { id: prdId, opportunity: { project: { ownerId } } },
      include: { opportunity: { select: { title: true } } },
    });
    if (!prd) throw new NotFoundError('PRD', prdId);
    return prd;
  }

  async generateCodexPrompt(opportunityId: string, ownerId: string, userId: string) {
    const opportunity = await this.opportunities.get(opportunityId, ownerId);
    const prd = opportunity.prds[0];
    if (!prd) {
      throw new ConflictError('Generate the PRD before the Codex prompt.', {
        opportunityId,
      });
    }

    const context = await this.opportunities.buildAgentContext(opportunityId, ownerId);
    const { output } = await this.invoker.invoke<PromptEngineerOutput>({
      projectId: opportunity.projectId,
      agentKey: 'prompt-engineer',
      initialState: {
        [OPPORTUNITY_CONTEXT_KEY]: context,
        [PRD_CONTEXT_KEY]: prd.content as Record<string, unknown>,
      },
    });

    const parsed = promptEngineerOutputSchema.parse(output);
    const markdown = renderCodexPromptMarkdown(parsed, {
      generatedAt: new Date(),
      opportunityTitle: opportunity.title,
      synthetic: this.syntheticProvider,
    });
    const version =
      (await this.prisma.prompt.count({ where: { opportunityId, type: 'CODEX_PROJECT' } })) + 1;

    const prompt = await this.prisma.prompt.create({
      data: {
        opportunityId,
        prdId: prd.id,
        type: 'CODEX_PROJECT',
        version,
        sections: parsed.sections as unknown as Prisma.InputJsonValue,
        markdown,
        tokenEstimate: estimatePromptTokens(markdown),
      },
    });

    if (opportunity.status === 'PRD_GENERATED') {
      await this.opportunities.setStatus(opportunityId, 'CODE_PROMPT_GENERATED');
    }

    await this.audit.record({
      actorId: userId,
      action: 'prompt.generated',
      entityType: 'Prompt',
      entityId: prompt.id,
      metadata: { opportunityId, version },
    });

    return prompt;
  }

  async getPrompt(promptId: string, ownerId: string) {
    const prompt = await this.prisma.prompt.findFirst({
      where: { id: promptId, opportunity: { project: { ownerId } } },
      include: { opportunity: { select: { title: true } } },
    });
    if (!prompt) throw new NotFoundError('Prompt', promptId);
    return prompt;
  }

  /**
   * Registers the code generation project. In V1 this hands the prompt to a
   * human — see ExportCodeGenerationProvider — rather than dispatching a build.
   */
  async createCodeGenerationProject(
    promptId: string,
    ownerId: string,
    userId: string,
    options: { repository?: string; branch?: string },
  ) {
    const prompt = await this.prisma.prompt.findFirst({
      where: { id: promptId, opportunity: { project: { ownerId } } },
      include: { opportunity: true },
    });
    if (!prompt) throw new NotFoundError('Prompt', promptId);

    const handle = await this.codegen.submit({
      opportunityId: prompt.opportunityId,
      projectName: prompt.opportunity.title,
      prompt: prompt.markdown,
      ...(options.repository ? { repository: options.repository } : {}),
      ...(options.branch ? { branch: options.branch } : {}),
    });

    const project = await this.prisma.codeGenerationProject.create({
      data: {
        opportunityId: prompt.opportunityId,
        prdId: prompt.prdId,
        promptId: prompt.id,
        repository: options.repository ?? null,
        branch: options.branch ?? null,
        status: handle.status,
        prompt: prompt.markdown,
        externalRef: handle.externalRef,
        generatedAt: new Date(),
      },
    });

    await this.audit.record({
      actorId: userId,
      action: 'code_generation.created',
      entityType: 'CodeGenerationProject',
      entityId: project.id,
      metadata: { promptId, provider: this.codegen.name },
    });

    return { project, instructions: handle.instructions };
  }
}
