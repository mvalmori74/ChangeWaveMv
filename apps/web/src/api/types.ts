import type { DashboardOverview, OpportunityStatus } from '@aiaf/shared';

/** Shapes returned by the API, as consumed by the UI. */

export interface AuthUser {
  id: string;
  email: string;
  name: string;
  role: string;
}

export interface ResearchProject {
  id: string;
  name: string;
  sector: string;
  country: string;
  platform: string;
  language: string;
  timeframe: string;
  objective: string | null;
  status: string;
  budgetUsd: string;
  tokenBudget: number;
  createdAt: string;
  _count?: { opportunities: number; runs: number };
  runs?: ResearchRun[];
}

export interface AgentExecution {
  id: string;
  agentKey: string;
  status: string;
  model: string | null;
  promptTokens: number;
  completionTokens: number;
  costUsd: string;
  durationMs: number;
  error: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export interface ResearchRun {
  id: string;
  status: string;
  startedAt: string | null;
  finishedAt: string | null;
  error: string | null;
  totalCostUsd: string;
  totalTokens: number;
  executions?: AgentExecution[];
  _count?: { sources: number; opportunities: number };
}

export interface OpportunityScore {
  id: string;
  criterion: string;
  weight: number;
  rawScore: number;
  weightedScore: number;
  rationale: string;
  evidence: string[];
  confidence: number;
}

export interface OpportunitySummary {
  id: string;
  title: string;
  category: string;
  targetAudience: string;
  problem: string;
  finalScore: number | null;
  confidenceScore: number | null;
  classification: string | null;
  competitionScore: number | null;
  monetizationScore: number | null;
  status: OpportunityStatus;
  evidenceType: string;
  createdAt: string;
  project?: { name: string; country: string; platform: string };
}

export interface Source {
  id: string;
  url: string;
  title: string;
  publisher: string | null;
  sourceType: string;
  credibilityScore: number;
  accessedAt: string;
}

export interface Decision {
  id: string;
  type: string;
  aiRecommendation: string | null;
  aiRationale: string | null;
  aiConfidence: number | null;
  humanDecision: string | null;
  humanRationale: string | null;
  decidedAt: string | null;
  decidedBy?: { name: string } | null;
}

export interface OpportunityDetail extends OpportunitySummary {
  proposedSolution: string;
  mvpSummary: { features?: string[]; differentiators?: string[] } | null;
  monetization: Record<string, unknown> | null;
  feasibility: Record<string, unknown> | null;
  risks: string[];
  assumptions: string[];
  projectId: string;
  scores: OpportunityScore[];
  painPoints: PainPoint[];
  prds: { id: string; version: number; title: string; createdAt: string }[];
  prompts: { id: string; version: number; type: string; tokenEstimate: number; createdAt: string }[];
  codeProjects: { id: string; status: string; repository: string | null }[];
  decisions: Decision[];
  run: { id: string; status: string; sources: Source[] } | null;
  project: ResearchProject;
}

export interface PainPoint {
  id: string;
  problem: string;
  targetUser: string;
  frequency: string;
  severity: string;
  currentSolution: string | null;
  marketGap: string | null;
  evidenceType: string;
  confidence: number;
  sourceUrls: string[];
}

export interface Competitor {
  id: string;
  name: string;
  description: string | null;
  url: string | null;
  businessModel: string | null;
  pricing: string | null;
  rating: number | null;
  reviewCount: number | null;
  isDirect: boolean;
  strengths: string[];
  weaknesses: string[];
  features: string[];
  evidenceType: string;
  sourceUrls: string[];
}

export interface ReviewInsight {
  id: string;
  type: string;
  summary: string;
  quote: string | null;
  frequency: string | null;
  evidenceType: string;
  sourceUrls: string[];
}

export interface ResearchContext {
  competitors: Competitor[];
  reviewInsights: ReviewInsight[];
  painPoints: PainPoint[];
  market: {
    name: string;
    description: string;
    demandLevel: string | null;
    growthTrend: string | null;
    seasonality: string | null;
    saturationScore: number | null;
    sizeEstimate: { low: number; high: number; unit: string; basis: string } | null;
  } | null;
}

export interface AgentMeta {
  key: string;
  name: string;
  description: string;
  version: string;
  role: string;
  dependencies: string[];
  tools: string[];
  enabled: boolean;
  priority: number;
  modelTier: string;
  systemPrompt: string;
}

export interface AgentCost {
  agentKey: string;
  status: string;
  executions: number;
  costUsd: number;
  promptTokens: number;
  completionTokens: number;
  averageDurationMs: number;
}

export type { DashboardOverview };
