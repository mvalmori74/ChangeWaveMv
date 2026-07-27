-- CreateSchema
CREATE SCHEMA IF NOT EXISTS "public";

-- CreateEnum
CREATE TYPE "UserRole" AS ENUM ('ADMIN', 'ANALYST', 'VIEWER');

-- CreateEnum
CREATE TYPE "Platform" AS ENUM ('ANDROID', 'IOS', 'WEB', 'DESKTOP', 'CROSS_PLATFORM');

-- CreateEnum
CREATE TYPE "ResearchProjectStatus" AS ENUM ('DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "ResearchRunStatus" AS ENUM ('PENDING', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "AgentExecutionStatus" AS ENUM ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED', 'BUDGET_EXCEEDED');

-- CreateEnum
CREATE TYPE "SourceType" AS ENUM ('WEB', 'APP_STORE', 'REVIEW', 'REPORT', 'FORUM', 'NEWS', 'SOCIAL', 'INTERNAL', 'OTHER');

-- CreateEnum
CREATE TYPE "EvidenceType" AS ENUM ('VERIFIED', 'INFERENCE', 'ESTIMATE', 'HYPOTHESIS');

-- CreateEnum
CREATE TYPE "ReviewInsightType" AS ENUM ('PAIN_POINT', 'FEATURE_REQUEST', 'BUG', 'COMPLAINT', 'PRICING', 'UX', 'ADS', 'PRAISE');

-- CreateEnum
CREATE TYPE "OpportunityStatus" AS ENUM ('DISCOVERED', 'ANALYZING', 'SCORED', 'APPROVED', 'REJECTED', 'PRD_GENERATED', 'CODE_PROMPT_GENERATED', 'DEVELOPMENT', 'RELEASED', 'MONITORING', 'SCALED', 'KILLED');

-- CreateEnum
CREATE TYPE "CodeGenerationStatus" AS ENUM ('CREATED', 'PROMPT_READY', 'SENT_TO_CODEX', 'GENERATING', 'BUILDING', 'TESTING', 'FAILED', 'COMPLETED');

-- CreateEnum
CREATE TYPE "PromptType" AS ENUM ('CODEX_PROJECT', 'CODEX_FEATURE', 'QA_PLAN');

-- CreateEnum
CREATE TYPE "DecisionType" AS ENUM ('APPROVE', 'REJECT', 'KEEP', 'IMPROVE', 'SCALE', 'KILL');

-- CreateEnum
CREATE TYPE "ActorType" AS ENUM ('USER', 'AGENT', 'SYSTEM');

-- CreateEnum
CREATE TYPE "AppStatus" AS ENUM ('PLANNED', 'IN_DEVELOPMENT', 'IN_REVIEW', 'RELEASED', 'RETIRED');

-- CreateEnum
CREATE TYPE "ModelTier" AS ENUM ('FAST', 'BALANCED', 'DEEP');

-- CreateTable
CREATE TABLE "users" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "passwordHash" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "role" "UserRole" NOT NULL DEFAULT 'ANALYST',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "agents" (
    "id" TEXT NOT NULL,
    "key" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "version" TEXT NOT NULL,
    "role" TEXT NOT NULL,
    "inputSchema" JSONB NOT NULL,
    "outputSchema" JSONB NOT NULL,
    "systemPrompt" TEXT NOT NULL,
    "tools" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "dependencies" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "priority" INTEGER NOT NULL DEFAULT 100,
    "modelTier" "ModelTier" NOT NULL DEFAULT 'BALANCED',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "agents_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "agent_executions" (
    "id" TEXT NOT NULL,
    "runId" TEXT NOT NULL,
    "agentKey" TEXT NOT NULL,
    "agentId" TEXT,
    "status" "AgentExecutionStatus" NOT NULL DEFAULT 'PENDING',
    "input" JSONB NOT NULL,
    "output" JSONB,
    "model" TEXT,
    "promptTokens" INTEGER NOT NULL DEFAULT 0,
    "completionTokens" INTEGER NOT NULL DEFAULT 0,
    "costUsd" DECIMAL(12,6) NOT NULL DEFAULT 0,
    "durationMs" INTEGER NOT NULL DEFAULT 0,
    "attempt" INTEGER NOT NULL DEFAULT 1,
    "error" TEXT,
    "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "finishedAt" TIMESTAMP(3),

    CONSTRAINT "agent_executions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "research_projects" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "sector" TEXT NOT NULL,
    "country" TEXT NOT NULL,
    "platform" "Platform" NOT NULL DEFAULT 'ANDROID',
    "language" TEXT NOT NULL DEFAULT 'en',
    "timeframe" TEXT NOT NULL DEFAULT 'last 12 months',
    "objective" TEXT,
    "status" "ResearchProjectStatus" NOT NULL DEFAULT 'DRAFT',
    "budgetUsd" DECIMAL(10,2) NOT NULL DEFAULT 5,
    "tokenBudget" INTEGER NOT NULL DEFAULT 400000,
    "ownerId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "research_projects_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "research_runs" (
    "id" TEXT NOT NULL,
    "projectId" TEXT NOT NULL,
    "status" "ResearchRunStatus" NOT NULL DEFAULT 'PENDING',
    "startedAt" TIMESTAMP(3),
    "finishedAt" TIMESTAMP(3),
    "error" TEXT,
    "totalCostUsd" DECIMAL(12,6) NOT NULL DEFAULT 0,
    "totalTokens" INTEGER NOT NULL DEFAULT 0,
    "budgetUsd" DECIMAL(10,2) NOT NULL,
    "tokenBudget" INTEGER NOT NULL,
    "state" JSONB NOT NULL DEFAULT '{}',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "research_runs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sources" (
    "id" TEXT NOT NULL,
    "runId" TEXT,
    "url" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "publisher" TEXT,
    "sourceType" "SourceType" NOT NULL DEFAULT 'WEB',
    "accessedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "publishedAt" TIMESTAMP(3),
    "credibilityScore" DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    "snippet" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "sources_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "claims" (
    "id" TEXT NOT NULL,
    "runId" TEXT NOT NULL,
    "agentKey" TEXT NOT NULL,
    "statement" TEXT NOT NULL,
    "evidenceType" "EvidenceType" NOT NULL,
    "confidence" DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "claims_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "markets" (
    "id" TEXT NOT NULL,
    "projectId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "sizeEstimate" JSONB,
    "growthTrend" TEXT,
    "seasonality" TEXT,
    "targetUsers" JSONB NOT NULL DEFAULT '[]',
    "demandLevel" TEXT,
    "saturationScore" DOUBLE PRECISION,
    "keyProblems" JSONB NOT NULL DEFAULT '[]',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "markets_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "competitors" (
    "id" TEXT NOT NULL,
    "projectId" TEXT NOT NULL,
    "marketId" TEXT,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "url" TEXT,
    "businessModel" TEXT,
    "pricing" TEXT,
    "rating" DOUBLE PRECISION,
    "reviewCount" INTEGER,
    "installsRange" TEXT,
    "platform" "Platform",
    "strengths" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "weaknesses" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "features" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "isDirect" BOOLEAN NOT NULL DEFAULT true,
    "evidenceType" "EvidenceType" NOT NULL DEFAULT 'INFERENCE',
    "sourceUrls" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "competitors_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "review_insights" (
    "id" TEXT NOT NULL,
    "projectId" TEXT NOT NULL,
    "competitorId" TEXT,
    "type" "ReviewInsightType" NOT NULL,
    "summary" TEXT NOT NULL,
    "quote" TEXT,
    "sentiment" DOUBLE PRECISION,
    "frequency" TEXT,
    "evidenceType" "EvidenceType" NOT NULL DEFAULT 'INFERENCE',
    "sourceUrls" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "review_insights_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "pain_points" (
    "id" TEXT NOT NULL,
    "projectId" TEXT NOT NULL,
    "problem" TEXT NOT NULL,
    "targetUser" TEXT NOT NULL,
    "frequency" TEXT NOT NULL,
    "severity" TEXT NOT NULL,
    "currentSolution" TEXT,
    "marketGap" TEXT,
    "evidenceType" "EvidenceType" NOT NULL DEFAULT 'INFERENCE',
    "confidence" DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    "sourceUrls" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "pain_points_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "opportunities" (
    "id" TEXT NOT NULL,
    "projectId" TEXT NOT NULL,
    "runId" TEXT,
    "title" TEXT NOT NULL,
    "category" TEXT NOT NULL,
    "targetAudience" TEXT NOT NULL,
    "problem" TEXT NOT NULL,
    "proposedSolution" TEXT NOT NULL,
    "marketScore" DOUBLE PRECISION,
    "competitionScore" DOUBLE PRECISION,
    "monetizationScore" DOUBLE PRECISION,
    "technicalScore" DOUBLE PRECISION,
    "retentionScore" DOUBLE PRECISION,
    "finalScore" DOUBLE PRECISION,
    "confidenceScore" DOUBLE PRECISION,
    "classification" TEXT,
    "monetization" JSONB,
    "feasibility" JSONB,
    "mvpSummary" JSONB,
    "risks" JSONB NOT NULL DEFAULT '[]',
    "assumptions" JSONB NOT NULL DEFAULT '[]',
    "evidenceType" "EvidenceType" NOT NULL DEFAULT 'INFERENCE',
    "status" "OpportunityStatus" NOT NULL DEFAULT 'DISCOVERED',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "opportunities_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "opportunity_scores" (
    "id" TEXT NOT NULL,
    "opportunityId" TEXT NOT NULL,
    "criterion" TEXT NOT NULL,
    "weight" DOUBLE PRECISION NOT NULL,
    "rawScore" DOUBLE PRECISION NOT NULL,
    "weightedScore" DOUBLE PRECISION NOT NULL,
    "rationale" TEXT NOT NULL,
    "evidence" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "confidence" DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    "weightsVersion" TEXT NOT NULL DEFAULT 'v1',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "opportunity_scores_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "prds" (
    "id" TEXT NOT NULL,
    "opportunityId" TEXT NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 1,
    "title" TEXT NOT NULL,
    "content" JSONB NOT NULL,
    "markdown" TEXT NOT NULL,
    "createdById" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "prds_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "prompts" (
    "id" TEXT NOT NULL,
    "opportunityId" TEXT NOT NULL,
    "prdId" TEXT,
    "type" "PromptType" NOT NULL DEFAULT 'CODEX_PROJECT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "sections" JSONB NOT NULL,
    "markdown" TEXT NOT NULL,
    "model" TEXT,
    "tokenEstimate" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "prompts_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "code_generation_projects" (
    "id" TEXT NOT NULL,
    "opportunityId" TEXT NOT NULL,
    "prdId" TEXT,
    "promptId" TEXT,
    "repository" TEXT,
    "branch" TEXT,
    "status" "CodeGenerationStatus" NOT NULL DEFAULT 'CREATED',
    "prompt" TEXT,
    "buildStatus" TEXT,
    "testStatus" TEXT,
    "externalRef" TEXT,
    "generatedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "code_generation_projects_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "apps" (
    "id" TEXT NOT NULL,
    "opportunityId" TEXT NOT NULL,
    "codeGenerationId" TEXT,
    "name" TEXT NOT NULL,
    "packageName" TEXT,
    "platform" "Platform" NOT NULL DEFAULT 'ANDROID',
    "status" "AppStatus" NOT NULL DEFAULT 'PLANNED',
    "storeUrl" TEXT,
    "releasedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "apps_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "app_metrics" (
    "id" TEXT NOT NULL,
    "appId" TEXT NOT NULL,
    "date" TIMESTAMP(3) NOT NULL,
    "installs" INTEGER NOT NULL DEFAULT 0,
    "dau" INTEGER NOT NULL DEFAULT 0,
    "mau" INTEGER NOT NULL DEFAULT 0,
    "retentionD1" DOUBLE PRECISION,
    "retentionD7" DOUBLE PRECISION,
    "retentionD30" DOUBLE PRECISION,
    "revenueUsd" DECIMAL(12,2) NOT NULL DEFAULT 0,
    "adRevenueUsd" DECIMAL(12,2) NOT NULL DEFAULT 0,
    "iapRevenueUsd" DECIMAL(12,2) NOT NULL DEFAULT 0,
    "costUsd" DECIMAL(12,2) NOT NULL DEFAULT 0,
    "rating" DOUBLE PRECISION,
    "crashRate" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "app_metrics_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "decisions" (
    "id" TEXT NOT NULL,
    "opportunityId" TEXT,
    "appId" TEXT,
    "type" "DecisionType" NOT NULL,
    "aiRecommendation" TEXT,
    "aiRationale" TEXT,
    "aiConfidence" DOUBLE PRECISION,
    "humanDecision" TEXT,
    "humanRationale" TEXT,
    "decidedById" TEXT,
    "decidedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "decisions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "audit_logs" (
    "id" TEXT NOT NULL,
    "actorId" TEXT,
    "actorType" "ActorType" NOT NULL DEFAULT 'USER',
    "action" TEXT NOT NULL,
    "entityType" TEXT NOT NULL,
    "entityId" TEXT,
    "before" JSONB,
    "after" JSONB,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "audit_logs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "_ClaimSources" (
    "A" TEXT NOT NULL,
    "B" TEXT NOT NULL,

    CONSTRAINT "_ClaimSources_AB_pkey" PRIMARY KEY ("A","B")
);

-- CreateTable
CREATE TABLE "_OpportunityToPainPoint" (
    "A" TEXT NOT NULL,
    "B" TEXT NOT NULL,

    CONSTRAINT "_OpportunityToPainPoint_AB_pkey" PRIMARY KEY ("A","B")
);

-- CreateIndex
CREATE UNIQUE INDEX "users_email_key" ON "users"("email");

-- CreateIndex
CREATE UNIQUE INDEX "agents_key_key" ON "agents"("key");

-- CreateIndex
CREATE INDEX "agent_executions_runId_idx" ON "agent_executions"("runId");

-- CreateIndex
CREATE INDEX "agent_executions_agentKey_idx" ON "agent_executions"("agentKey");

-- CreateIndex
CREATE INDEX "research_projects_ownerId_idx" ON "research_projects"("ownerId");

-- CreateIndex
CREATE INDEX "research_runs_projectId_idx" ON "research_runs"("projectId");

-- CreateIndex
CREATE INDEX "sources_runId_idx" ON "sources"("runId");

-- CreateIndex
CREATE UNIQUE INDEX "sources_runId_url_key" ON "sources"("runId", "url");

-- CreateIndex
CREATE INDEX "claims_runId_idx" ON "claims"("runId");

-- CreateIndex
CREATE INDEX "markets_projectId_idx" ON "markets"("projectId");

-- CreateIndex
CREATE INDEX "competitors_projectId_idx" ON "competitors"("projectId");

-- CreateIndex
CREATE INDEX "review_insights_projectId_idx" ON "review_insights"("projectId");

-- CreateIndex
CREATE INDEX "pain_points_projectId_idx" ON "pain_points"("projectId");

-- CreateIndex
CREATE INDEX "opportunities_projectId_idx" ON "opportunities"("projectId");

-- CreateIndex
CREATE INDEX "opportunities_status_idx" ON "opportunities"("status");

-- CreateIndex
CREATE INDEX "opportunities_finalScore_idx" ON "opportunities"("finalScore");

-- CreateIndex
CREATE INDEX "opportunity_scores_opportunityId_idx" ON "opportunity_scores"("opportunityId");

-- CreateIndex
CREATE UNIQUE INDEX "opportunity_scores_opportunityId_criterion_weightsVersion_key" ON "opportunity_scores"("opportunityId", "criterion", "weightsVersion");

-- CreateIndex
CREATE INDEX "prds_opportunityId_idx" ON "prds"("opportunityId");

-- CreateIndex
CREATE UNIQUE INDEX "prds_opportunityId_version_key" ON "prds"("opportunityId", "version");

-- CreateIndex
CREATE INDEX "prompts_opportunityId_idx" ON "prompts"("opportunityId");

-- CreateIndex
CREATE UNIQUE INDEX "prompts_opportunityId_type_version_key" ON "prompts"("opportunityId", "type", "version");

-- CreateIndex
CREATE INDEX "code_generation_projects_opportunityId_idx" ON "code_generation_projects"("opportunityId");

-- CreateIndex
CREATE INDEX "apps_opportunityId_idx" ON "apps"("opportunityId");

-- CreateIndex
CREATE INDEX "app_metrics_appId_idx" ON "app_metrics"("appId");

-- CreateIndex
CREATE UNIQUE INDEX "app_metrics_appId_date_key" ON "app_metrics"("appId", "date");

-- CreateIndex
CREATE INDEX "decisions_opportunityId_idx" ON "decisions"("opportunityId");

-- CreateIndex
CREATE INDEX "audit_logs_entityType_entityId_idx" ON "audit_logs"("entityType", "entityId");

-- CreateIndex
CREATE INDEX "audit_logs_createdAt_idx" ON "audit_logs"("createdAt");

-- CreateIndex
CREATE INDEX "_ClaimSources_B_index" ON "_ClaimSources"("B");

-- CreateIndex
CREATE INDEX "_OpportunityToPainPoint_B_index" ON "_OpportunityToPainPoint"("B");

-- AddForeignKey
ALTER TABLE "agent_executions" ADD CONSTRAINT "agent_executions_runId_fkey" FOREIGN KEY ("runId") REFERENCES "research_runs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "agent_executions" ADD CONSTRAINT "agent_executions_agentId_fkey" FOREIGN KEY ("agentId") REFERENCES "agents"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "research_projects" ADD CONSTRAINT "research_projects_ownerId_fkey" FOREIGN KEY ("ownerId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "research_runs" ADD CONSTRAINT "research_runs_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "research_projects"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "sources" ADD CONSTRAINT "sources_runId_fkey" FOREIGN KEY ("runId") REFERENCES "research_runs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "claims" ADD CONSTRAINT "claims_runId_fkey" FOREIGN KEY ("runId") REFERENCES "research_runs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "markets" ADD CONSTRAINT "markets_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "research_projects"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "competitors" ADD CONSTRAINT "competitors_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "research_projects"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "competitors" ADD CONSTRAINT "competitors_marketId_fkey" FOREIGN KEY ("marketId") REFERENCES "markets"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "review_insights" ADD CONSTRAINT "review_insights_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "research_projects"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "review_insights" ADD CONSTRAINT "review_insights_competitorId_fkey" FOREIGN KEY ("competitorId") REFERENCES "competitors"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "pain_points" ADD CONSTRAINT "pain_points_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "research_projects"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "opportunities" ADD CONSTRAINT "opportunities_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "research_projects"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "opportunities" ADD CONSTRAINT "opportunities_runId_fkey" FOREIGN KEY ("runId") REFERENCES "research_runs"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "opportunity_scores" ADD CONSTRAINT "opportunity_scores_opportunityId_fkey" FOREIGN KEY ("opportunityId") REFERENCES "opportunities"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prds" ADD CONSTRAINT "prds_opportunityId_fkey" FOREIGN KEY ("opportunityId") REFERENCES "opportunities"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prds" ADD CONSTRAINT "prds_createdById_fkey" FOREIGN KEY ("createdById") REFERENCES "users"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prompts" ADD CONSTRAINT "prompts_opportunityId_fkey" FOREIGN KEY ("opportunityId") REFERENCES "opportunities"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prompts" ADD CONSTRAINT "prompts_prdId_fkey" FOREIGN KEY ("prdId") REFERENCES "prds"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "code_generation_projects" ADD CONSTRAINT "code_generation_projects_opportunityId_fkey" FOREIGN KEY ("opportunityId") REFERENCES "opportunities"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "code_generation_projects" ADD CONSTRAINT "code_generation_projects_prdId_fkey" FOREIGN KEY ("prdId") REFERENCES "prds"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "code_generation_projects" ADD CONSTRAINT "code_generation_projects_promptId_fkey" FOREIGN KEY ("promptId") REFERENCES "prompts"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "apps" ADD CONSTRAINT "apps_opportunityId_fkey" FOREIGN KEY ("opportunityId") REFERENCES "opportunities"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "apps" ADD CONSTRAINT "apps_codeGenerationId_fkey" FOREIGN KEY ("codeGenerationId") REFERENCES "code_generation_projects"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "app_metrics" ADD CONSTRAINT "app_metrics_appId_fkey" FOREIGN KEY ("appId") REFERENCES "apps"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "decisions" ADD CONSTRAINT "decisions_opportunityId_fkey" FOREIGN KEY ("opportunityId") REFERENCES "opportunities"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "decisions" ADD CONSTRAINT "decisions_appId_fkey" FOREIGN KEY ("appId") REFERENCES "apps"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "decisions" ADD CONSTRAINT "decisions_decidedById_fkey" FOREIGN KEY ("decidedById") REFERENCES "users"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "audit_logs" ADD CONSTRAINT "audit_logs_actorId_fkey" FOREIGN KEY ("actorId") REFERENCES "users"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_ClaimSources" ADD CONSTRAINT "_ClaimSources_A_fkey" FOREIGN KEY ("A") REFERENCES "claims"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_ClaimSources" ADD CONSTRAINT "_ClaimSources_B_fkey" FOREIGN KEY ("B") REFERENCES "sources"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_OpportunityToPainPoint" ADD CONSTRAINT "_OpportunityToPainPoint_A_fkey" FOREIGN KEY ("A") REFERENCES "opportunities"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "_OpportunityToPainPoint" ADD CONSTRAINT "_OpportunityToPainPoint_B_fkey" FOREIGN KEY ("B") REFERENCES "pain_points"("id") ON DELETE CASCADE ON UPDATE CASCADE;

