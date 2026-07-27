import { z } from 'zod';
import {
  DECISION_TYPES,
  OPPORTUNITY_STATUSES,
  PLATFORMS,
  RESEARCH_PROJECT_STATUSES,
} from './enums.js';

/** Request / response contracts shared by the API and the web client. */

export const loginRequestSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8).max(200),
});
export type LoginRequest = z.infer<typeof loginRequestSchema>;

export const registerRequestSchema = loginRequestSchema.extend({
  name: z.string().min(1).max(120),
});
export type RegisterRequest = z.infer<typeof registerRequestSchema>;

export const authResponseSchema = z.object({
  token: z.string(),
  user: z.object({
    id: z.string(),
    email: z.string(),
    name: z.string(),
    role: z.string(),
  }),
});
export type AuthResponse = z.infer<typeof authResponseSchema>;

export const createResearchProjectSchema = z.object({
  name: z.string().min(3).max(160),
  sector: z.string().min(2).max(160),
  country: z.string().min(2).max(80),
  platform: z.enum(PLATFORMS),
  language: z.string().min(2).max(20).default('en'),
  timeframe: z.string().min(2).max(80).default('last 12 months'),
  objective: z.string().max(2000).optional(),
  /** Hard spend ceiling for a single run of this project, in USD. */
  budgetUsd: z.number().positive().max(1000).optional(),
  tokenBudget: z.number().int().positive().max(10_000_000).optional(),
});
export type CreateResearchProjectRequest = z.infer<typeof createResearchProjectSchema>;

export const opportunityFiltersSchema = z.object({
  projectId: z.string().optional(),
  category: z.string().optional(),
  country: z.string().optional(),
  platform: z.enum(PLATFORMS).optional(),
  status: z.enum(OPPORTUNITY_STATUSES).optional(),
  minScore: z.coerce.number().min(0).max(100).optional(),
  maxScore: z.coerce.number().min(0).max(100).optional(),
  search: z.string().max(200).optional(),
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(100).default(25),
  sortBy: z.enum(['finalScore', 'createdAt', 'title']).default('finalScore'),
  sortDir: z.enum(['asc', 'desc']).default('desc'),
});
export type OpportunityFilters = z.infer<typeof opportunityFiltersSchema>;

export const decisionRequestSchema = z.object({
  type: z.enum(DECISION_TYPES),
  rationale: z.string().min(1).max(4000),
});
export type DecisionRequest = z.infer<typeof decisionRequestSchema>;

export interface Paginated<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface DashboardOverview {
  opportunitiesAnalyzed: number;
  opportunitiesApproved: number;
  opportunitiesRejected: number;
  prdsGenerated: number;
  promptsGenerated: number;
  appsGenerated: number;
  appsReleased: number;
  /** Null when no post-launch data has been collected yet (V1 default). */
  revenueUsd: number | null;
  roi: number | null;
  conversionRate: number | null;
  successRate: number | null;
  totalLlmCostUsd: number;
  averageScore: number | null;
  byStatus: Record<string, number>;
}

export const researchProjectStatusSchema = z.enum(RESEARCH_PROJECT_STATUSES);
