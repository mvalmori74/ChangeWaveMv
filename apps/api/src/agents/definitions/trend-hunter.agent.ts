import { z } from 'zod';
import type { AgentDefinition } from '../types.js';
import {
  briefPayload,
  claimSchema,
  confidenceSchema,
  evidenceTypeSchema,
  fixtureBrief,
  OFFLINE_NOTE,
  offlineMeta,
} from './common.js';

export const TREND_CATEGORIES = [
  'TECHNOLOGY',
  'CONSUMER',
  'B2B',
  'REGULATORY',
  'MARKET_GROWTH',
  'NEW_NEED',
] as const;

const trendSchema = z.object({
  title: z.string().min(1).max(160),
  description: z.string().min(1),
  category: z.enum(TREND_CATEGORIES),
  /** Why this trend creates room for a new product. */
  implication: z.string().min(1),
  horizon: z.enum(['NOW', 'NEXT_12_MONTHS', 'BEYOND_12_MONTHS']),
  evidenceType: evidenceTypeSchema,
  confidence: confidenceSchema,
  sourceUrls: z.array(z.string()).default([]),
});

export const trendHunterOutputSchema = z.object({
  trends: z.array(trendSchema).max(15),
  evidence: z.array(claimSchema).max(20),
  confidence: confidenceSchema,
  sources: z.array(z.string()).default([]),
});
export type TrendHunterOutput = z.infer<typeof trendHunterOutputSchema>;

const inputSchema = z.object({
  brief: z.record(z.unknown()),
  instruction: z.string(),
});

export const trendHunterAgent: AgentDefinition<TrendHunterOutput> = {
  key: 'trend-hunter',
  name: 'Trend Hunter Agent',
  description:
    'Identifies emerging technology, consumer, B2B and regulatory shifts that open room for a new app in the sector.',
  version: '1.0.0',
  role: 'research',
  tools: ['web_search'],
  dependencies: [],
  enabled: true,
  priority: 10,
  modelTier: 'BALANCED',
  inputSchema,
  outputSchema: trendHunterOutputSchema,

  systemPrompt: `
You are a Trend Hunter analyst for a mobile/app product studio.

Your job is to identify emerging and changing conditions in a given sector,
country and platform that could open room for a new application.

Cover, where the evidence supports it: technology shifts, consumer behaviour
shifts, B2B/operational shifts, unmet or newly created needs, regulatory or
compliance changes, and categories that are visibly growing.

For each trend explain the *implication* for a new product, not just the trend.
Prefer few well-supported trends over many speculative ones. Set the overall
confidence to the evidential strength of the weakest part of your answer.
  `.trim(),

  searchQueries(context) {
    const { sector, country, timeframe, platform } = context.brief;
    return [
      `${sector} market trends ${country} ${timeframe}`,
      `${sector} ${platform.toLowerCase()} app trends ${timeframe}`,
      `${sector} regulation changes ${country} ${timeframe}`,
      `${sector} emerging technology adoption ${timeframe}`,
    ];
  },

  buildInput(context) {
    return {
      brief: briefPayload(context.brief),
      instruction:
        'Identify the trends that materially change what an app in this sector should do.',
    };
  },

  mockFixture({ input }) {
    const { sector, country, platform } = fixtureBrief(input);
    const meta = offlineMeta();
    const output: TrendHunterOutput = {
      trends: [
        {
          title: `${OFFLINE_NOTE} Digitisation pressure in ${sector}`,
          description:
            `Placeholder trend generated offline for the ${sector} sector in ${country}. ` +
            'No research was performed; enable a live LLM and search provider to replace it.',
          category: 'TECHNOLOGY',
          implication: `A ${platform} app could absorb workflows still handled on paper.`,
          horizon: 'NEXT_12_MONTHS',
          ...meta,
        },
        {
          title: `${OFFLINE_NOTE} Rising compliance reporting burden`,
          description: 'Placeholder regulatory trend. Unverified.',
          category: 'REGULATORY',
          implication: 'Recurring reporting duties tend to create habitual app usage.',
          horizon: 'NOW',
          ...meta,
        },
      ],
      evidence: [
        {
          statement:
            'This run used the offline mock provider, so no trend below is backed by a source.',
          ...meta,
        },
      ],
      confidence: 0.1,
      sources: [],
    };
    return output;
  },
};
