/**
 * Enumerations shared by API and web client.
 * These mirror the Prisma enums one-to-one; the API has a compile-time test
 * (`prisma-enums.test.ts`) that fails if the two ever drift apart.
 */

export const OPPORTUNITY_STATUSES = [
  'DISCOVERED',
  'ANALYZING',
  'SCORED',
  'APPROVED',
  'REJECTED',
  'PRD_GENERATED',
  'CODE_PROMPT_GENERATED',
  'DEVELOPMENT',
  'RELEASED',
  'MONITORING',
  'SCALED',
  'KILLED',
] as const;
export type OpportunityStatus = (typeof OPPORTUNITY_STATUSES)[number];

/** Columns of the pipeline kanban, in display order. */
export const PIPELINE_COLUMNS: readonly OpportunityStatus[] = [
  'DISCOVERED',
  'ANALYZING',
  'SCORED',
  'APPROVED',
  'PRD_GENERATED',
  'CODE_PROMPT_GENERATED',
  'DEVELOPMENT',
  'RELEASED',
  'MONITORING',
];

export const RESEARCH_RUN_STATUSES = [
  'PENDING',
  'RUNNING',
  'COMPLETED',
  'PARTIAL',
  'FAILED',
  'CANCELLED',
] as const;
export type ResearchRunStatus = (typeof RESEARCH_RUN_STATUSES)[number];

export const RESEARCH_PROJECT_STATUSES = ['DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED'] as const;
export type ResearchProjectStatus = (typeof RESEARCH_PROJECT_STATUSES)[number];

export const AGENT_EXECUTION_STATUSES = [
  'PENDING',
  'RUNNING',
  'SUCCEEDED',
  'FAILED',
  'SKIPPED',
  'BUDGET_EXCEEDED',
] as const;
export type AgentExecutionStatus = (typeof AGENT_EXECUTION_STATUSES)[number];

export const CODEGEN_STATUSES = [
  'CREATED',
  'PROMPT_READY',
  'SENT_TO_CODEX',
  'GENERATING',
  'BUILDING',
  'TESTING',
  'FAILED',
  'COMPLETED',
] as const;
export type CodeGenerationStatus = (typeof CODEGEN_STATUSES)[number];

export const SOURCE_TYPES = [
  'WEB',
  'APP_STORE',
  'REVIEW',
  'REPORT',
  'FORUM',
  'NEWS',
  'SOCIAL',
  'INTERNAL',
  'OTHER',
] as const;
export type SourceType = (typeof SOURCE_TYPES)[number];

/**
 * How a statement was obtained. The platform must never blur these:
 * a model-generated guess is a HYPOTHESIS, not a fact.
 */
export const EVIDENCE_TYPES = ['VERIFIED', 'INFERENCE', 'ESTIMATE', 'HYPOTHESIS'] as const;
export type EvidenceType = (typeof EVIDENCE_TYPES)[number];

export const REVIEW_INSIGHT_TYPES = [
  'PAIN_POINT',
  'FEATURE_REQUEST',
  'BUG',
  'COMPLAINT',
  'PRICING',
  'UX',
  'ADS',
  'PRAISE',
] as const;
export type ReviewInsightType = (typeof REVIEW_INSIGHT_TYPES)[number];

export const MONETIZATION_MODELS = [
  'ADVERTISING',
  'REWARDED_VIDEO',
  'IN_APP_PURCHASE',
  'PREMIUM',
  'SUBSCRIPTION',
  'FREEMIUM',
  'B2B_LICENSE',
  'SAAS',
  'MARKETPLACE_FEE',
] as const;
export type MonetizationModel = (typeof MONETIZATION_MODELS)[number];

export const DECISION_TYPES = [
  'APPROVE',
  'REJECT',
  'KEEP',
  'IMPROVE',
  'SCALE',
  'KILL',
] as const;
export type DecisionType = (typeof DECISION_TYPES)[number];

export const USER_ROLES = ['ADMIN', 'ANALYST', 'VIEWER'] as const;
export type UserRole = (typeof USER_ROLES)[number];

export const PLATFORMS = ['ANDROID', 'IOS', 'WEB', 'DESKTOP', 'CROSS_PLATFORM'] as const;
export type Platform = (typeof PLATFORMS)[number];

/** Model tiers used by the router; concrete model ids come from configuration. */
export const MODEL_TIERS = ['FAST', 'BALANCED', 'DEEP'] as const;
export type ModelTier = (typeof MODEL_TIERS)[number];
