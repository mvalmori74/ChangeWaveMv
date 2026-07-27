import { z } from 'zod';
import type { AgentDefinition } from '../types.js';
import {
  briefPayload,
  confidenceSchema,
  dependencyOutput,
  evidenceTypeSchema,
  OFFLINE_NOTE,
  offlineMeta,
} from './common.js';
import type { CompetitorAnalysisOutput } from './competitor-analysis.agent.js';

const insightSchema = z.object({
  summary: z.string().min(1),
  /** Verbatim only when it appears in the evidence; otherwise null. */
  quote: z.string().nullable(),
  competitor: z.string().nullable(),
  frequency: z.enum(['LOW', 'MEDIUM', 'HIGH', 'UNKNOWN']),
  sentiment: z.number().min(-1).max(1).nullable(),
  evidenceType: evidenceTypeSchema,
  confidence: confidenceSchema,
  sourceUrls: z.array(z.string()).default([]),
});
export type ReviewInsightEntry = z.infer<typeof insightSchema>;

export const reviewAnalysisOutputSchema = z.object({
  painPoints: z.array(insightSchema).max(20),
  featureRequests: z.array(insightSchema).max(20),
  bugs: z.array(insightSchema).max(15),
  complaints: z.array(insightSchema).max(20),
  pricingSignals: z.array(insightSchema).max(10),
  uxSignals: z.array(insightSchema).max(10),
  adsSignals: z.array(insightSchema).max(10),
  opportunities: z.array(z.string()).max(15),
  confidence: confidenceSchema,
});
export type ReviewAnalysisOutput = z.infer<typeof reviewAnalysisOutputSchema>;

const inputSchema = z.object({
  brief: z.record(z.unknown()),
  competitors: z.unknown(),
  instruction: z.string(),
});

export const reviewAnalysisAgent: AgentDefinition<ReviewAnalysisOutput> = {
  key: 'review-analysis',
  name: 'Review Analysis Agent',
  description:
    'Mines user reviews and public feedback for pain points, feature requests, bugs, pricing, UX and ad complaints.',
  version: '1.0.0',
  role: 'research',
  tools: ['web_search'],
  dependencies: ['competitor-analysis'],
  enabled: true,
  priority: 40,
  modelTier: 'BALANCED',
  inputSchema,
  outputSchema: reviewAnalysisOutputSchema,

  systemPrompt: `
You are a User Feedback analyst.

Read the provided review and forum evidence and classify what users actually
complain about and ask for. Separate: pain points, feature requests, bugs,
general complaints, pricing signals, UX signals and advertising complaints.

Rules specific to this agent:
- A quote must be copied verbatim from the evidence. If you do not have a real
  quote, set quote to null. Never write a quote "in the style of" a user.
- frequency reflects how often the theme recurs *in the evidence you were
  given*, not how common you imagine it to be.
- "opportunities" is your synthesis: what a new product could do about all this.
  `.trim(),

  searchQueries(context) {
    const { sector, platform, country } = context.brief;
    const store = platform === 'IOS' ? 'App Store' : 'Google Play';
    const competitors = dependencyOutput<CompetitorAnalysisOutput>(
      context,
      'competitor-analysis',
    );
    const named = (competitors?.competitors ?? [])
      .filter((competitor) => !competitor.name.startsWith('['))
      .slice(0, 3)
      .map((competitor) => `${competitor.name} app reviews complaints`);
    return [
      `${sector} app ${store} reviews complaints ${country}`,
      `${sector} app users frustrated forum`,
      ...named,
    ];
  },

  buildInput(context) {
    const competitors = dependencyOutput<CompetitorAnalysisOutput>(
      context,
      'competitor-analysis',
    );
    return {
      brief: briefPayload(context.brief),
      competitors: (competitors?.competitors ?? []).map((competitor) => ({
        name: competitor.name,
        weaknesses: competitor.weaknesses,
      })),
      instruction: 'Classify what users complain about and what they ask for.',
    };
  },

  mockFixture() {
    const meta = offlineMeta();
    const placeholder = {
      summary: `${OFFLINE_NOTE} no reviews were read in offline mode`,
      quote: null,
      competitor: null,
      frequency: 'UNKNOWN' as const,
      sentiment: null,
      ...meta,
    };
    const output: ReviewAnalysisOutput = {
      painPoints: [placeholder],
      featureRequests: [],
      bugs: [],
      complaints: [],
      pricingSignals: [],
      uxSignals: [],
      adsSignals: [],
      opportunities: [],
      confidence: 0.1,
    };
    return output;
  },
};
