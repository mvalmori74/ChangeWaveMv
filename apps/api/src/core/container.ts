import type { PrismaClient } from '@prisma/client';
import { buildAgentRegistry } from '../agents/bootstrap.js';
import { Orchestrator } from '../agents/orchestrator.js';
import type { AgentRegistry } from '../agents/registry.js';
import { AgentRunner, DEFAULT_RUNNER_OPTIONS } from '../agents/runner.js';
import type { AppConfig } from '../config/env.js';
import { getPrismaClient } from '../db/prisma.js';
import { PrismaRunRecorder } from '../persistence/run-recorder.js';
import { ExportCodeGenerationProvider } from '../providers/codegen/export.provider.js';
import type { CodeGenerationProvider } from '../providers/codegen/types.js';
import { AnthropicProvider } from '../providers/llm/anthropic.provider.js';
import { ConfigModelRouter } from '../providers/llm/model-router.js';
import { MockLlmProvider } from '../providers/llm/mock.provider.js';
import { OpenAiProvider } from '../providers/llm/openai.provider.js';
import type { LlmProvider } from '../providers/llm/types.js';
import { MockSearchProvider } from '../providers/search/mock.provider.js';
import { TavilySearchProvider } from '../providers/search/tavily.provider.js';
import type { SearchProvider } from '../providers/search/types.js';
import { BullMqQueue } from '../queue/bullmq.queue.js';
import { InMemoryQueue } from '../queue/in-memory.queue.js';
import type { JobQueue } from '../queue/types.js';
import { AgentInvoker } from '../services/agent-invoker.js';
import { AnalyticsService } from '../services/analytics.service.js';
import { AuditService } from '../services/audit.service.js';
import { AuthService, type TokenSigner } from '../services/auth.service.js';
import { DeliveryService } from '../services/delivery.service.js';
import { OpportunityService } from '../services/opportunity.service.js';
import {
  RESEARCH_RUN_JOB,
  ResearchService,
  type ResearchRunJobPayload,
} from '../services/research.service.js';
import { createLogger, type AppLogger } from './logger.js';

export interface Container {
  config: AppConfig;
  logger: AppLogger;
  prisma: PrismaClient;
  llm: LlmProvider;
  search: SearchProvider;
  codegen: CodeGenerationProvider;
  registry: AgentRegistry;
  orchestrator: Orchestrator;
  queue: JobQueue;
  setTokenSigner(signer: TokenSigner): void;
  services: {
    audit: AuditService;
    auth: AuthService;
    research: ResearchService;
    opportunities: OpportunityService;
    delivery: DeliveryService;
    analytics: AnalyticsService;
  };
  shutdown(): Promise<void>;
}

export interface ContainerOverrides {
  prisma?: PrismaClient;
  llm?: LlmProvider;
  search?: SearchProvider;
  queue?: JobQueue;
  logger?: AppLogger;
  signToken?: TokenSigner;
}

/**
 * The one place that knows which LLM vendor is in play. `loadConfig` has
 * already refused any combination without the matching API key, so the
 * non-null assertions here cannot fire at runtime.
 */
export function createLlmProvider(config: AppConfig): LlmProvider {
  switch (config.LLM_PROVIDER) {
    case 'openai':
      return new OpenAiProvider({
        apiKey: config.OPENAI_API_KEY as string,
        ...(config.OPENAI_BASE_URL ? { baseURL: config.OPENAI_BASE_URL } : {}),
        maxRetries: config.LLM_MAX_RETRIES,
      });
    case 'anthropic':
      return new AnthropicProvider({
        apiKey: config.ANTHROPIC_API_KEY as string,
        ...(config.ANTHROPIC_BASE_URL ? { baseURL: config.ANTHROPIC_BASE_URL } : {}),
        maxRetries: config.LLM_MAX_RETRIES,
        maxTokens: config.LLM_MAX_TOKENS,
        serverSideFallbacks: config.ANTHROPIC_FALLBACKS,
        promptCaching: config.ANTHROPIC_PROMPT_CACHE,
      });
    default:
      return new MockLlmProvider();
  }
}

/**
 * Composition root. Every dependency is constructed here and injected
 * downwards, so nothing below this file reaches for a global or decides which
 * provider it is talking to.
 */
export function createContainer(
  config: AppConfig,
  overrides: ContainerOverrides = {},
): Container {
  const logger = overrides.logger ?? createLogger(config.LOG_LEVEL);
  const prisma = overrides.prisma ?? getPrismaClient(config.DATABASE_URL);

  const llm = overrides.llm ?? createLlmProvider(config);

  const search =
    overrides.search ??
    (config.SEARCH_PROVIDER === 'tavily'
      ? new TavilySearchProvider(config.TAVILY_API_KEY as string, config.SEARCH_MAX_RESULTS)
      : new MockSearchProvider());

  const codegen: CodeGenerationProvider = new ExportCodeGenerationProvider();

  const registry = buildAgentRegistry(llm);
  const runner = new AgentRunner(llm, new ConfigModelRouter(config), {
    ...DEFAULT_RUNNER_OPTIONS,
    maxValidationRetries: config.MAX_AGENT_RETRIES,
  });
  const recorder = new PrismaRunRecorder(prisma, logger);
  const orchestrator = new Orchestrator(registry, runner, search, recorder, logger);

  const queue =
    overrides.queue ??
    (config.REDIS_URL ? new BullMqQueue(config.REDIS_URL, logger) : new InMemoryQueue(logger));

  const audit = new AuditService(prisma, logger);
  const invoker = new AgentInvoker(prisma, orchestrator);
  const opportunities = new OpportunityService(prisma, audit);
  const research = new ResearchService(prisma, orchestrator, queue, audit, logger, {
    budgetUsd: config.DEFAULT_RUN_BUDGET_USD,
    tokenBudget: config.DEFAULT_RUN_TOKEN_BUDGET,
  });
  const delivery = new DeliveryService(
    prisma,
    opportunities,
    invoker,
    codegen,
    audit,
    llm.name === 'mock' || search.synthetic,
  );
  const analytics = new AnalyticsService(prisma);

  // The API process is also the worker in V1: one deployable, one place where
  // jobs are handled, whichever queue implementation is active.
  queue.register<ResearchRunJobPayload>(RESEARCH_RUN_JOB, async (payload) => {
    await research.executeRun(payload.runId);
  });

  // The JWT signer comes from the Fastify instance, which is built after the
  // container, so it is injected through a mutable slot rather than making the
  // whole container depend on the HTTP layer.
  let tokenSigner: TokenSigner | undefined = overrides.signToken;
  const auth = new AuthService(prisma, (payload) => {
    if (!tokenSigner) throw new Error('Token signer has not been configured yet');
    return tokenSigner(payload);
  });

  return {
    setTokenSigner(signer: TokenSigner) {
      tokenSigner = signer;
    },
    config,
    logger,
    prisma,
    llm,
    search,
    codegen,
    registry,
    orchestrator,
    queue,
    services: { audit, auth, research, opportunities, delivery, analytics },
    async shutdown() {
      await queue.close();
      await prisma.$disconnect();
    },
  };
}
