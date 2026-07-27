import { z } from 'zod';
import type { AgentDefinition } from '../types.js';
import {
  briefPayload,
  claimSchema,
  confidenceSchema,
  dependencyOutput,
  evidenceTypeSchema,
  fixtureBrief,
  OFFLINE_NOTE,
  offlineMeta,
} from './common.js';
import type { MarketResearchOutput } from './market-research.agent.js';

const competitorSchema = z.object({
  name: z.string().min(1),
  description: z.string().min(1),
  url: z.string().nullable(),
  isDirect: z.boolean(),
  businessModel: z.string().nullable(),
  pricing: z.string().nullable(),
  /** Store rating. Null unless it was actually observed in the evidence. */
  rating: z.number().min(0).max(5).nullable(),
  reviewCount: z.number().int().min(0).nullable(),
  installsRange: z.string().nullable(),
  strengths: z.array(z.string()).max(8),
  weaknesses: z.array(z.string()).max(8),
  features: z.array(z.string()).max(15),
  evidenceType: evidenceTypeSchema,
  sourceUrls: z.array(z.string()).default([]),
});
export type CompetitorEntry = z.infer<typeof competitorSchema>;

export const competitorAnalysisOutputSchema = z.object({
  competitors: z.array(competitorSchema).max(20),
  matrix: z.object({
    /** Comparison dimensions, e.g. "offline mode", "price". */
    dimensions: z.array(z.string()).max(12),
    /** competitorName -> dimension -> short cell value. */
    rows: z.array(
      z.object({
        competitor: z.string().min(1),
        cells: z.record(z.string()),
      }),
    ),
  }),
  /** 0-100, higher means more saturated (i.e. worse for a newcomer). */
  saturationScore: z.number().min(0).max(100),
  saturationRationale: z.string().min(1),
  whitespace: z.array(z.string()).max(10),
  claims: z.array(claimSchema).max(20),
  confidence: confidenceSchema,
});
export type CompetitorAnalysisOutput = z.infer<typeof competitorAnalysisOutputSchema>;

const inputSchema = z.object({
  brief: z.record(z.unknown()),
  market: z.unknown(),
  instruction: z.string(),
});

export const competitorAnalysisAgent: AgentDefinition<CompetitorAnalysisOutput> = {
  key: 'competitor-analysis',
  name: 'Competitor Analysis Agent',
  description:
    'Maps direct and indirect competitors, builds a comparison matrix and scores market saturation.',
  version: '1.0.0',
  role: 'research',
  tools: ['web_search'],
  dependencies: ['market-research'],
  enabled: true,
  priority: 30,
  modelTier: 'BALANCED',
  inputSchema,
  outputSchema: competitorAnalysisOutputSchema,

  systemPrompt: `
You are a Competitive Intelligence analyst.

List the direct competitors (same job, same audience) and the indirect ones
(different product, same job — including spreadsheets, paper and WhatsApp).

Ratings, review counts and install ranges are the fields most often
hallucinated. Return null unless the number appears in the evidence. A
competitor with fewer fields but honest nulls is worth more than a complete
fabricated row.

saturationScore is 0-100 where 100 means a mature market with strong,
well-rated incumbents covering the whole job, and 0 means essentially nobody
serves this audience. Justify the number in saturationRationale.

whitespace lists the gaps no listed competitor covers well.
  `.trim(),

  searchQueries(context) {
    const { sector, country, platform } = context.brief;
    const store = platform === 'IOS' ? 'App Store' : 'Google Play';
    return [
      `best ${sector} apps ${store} ${country}`,
      `${sector} software competitors comparison`,
      `${sector} app alternatives pricing`,
      `${sector} management app reviews`,
    ];
  },

  buildInput(context) {
    const market = dependencyOutput<MarketResearchOutput>(context, 'market-research');
    return {
      brief: briefPayload(context.brief),
      market: market?.market ?? null,
      instruction:
        'Identify who already serves this audience, how well, and what they leave uncovered.',
    };
  },

  mockFixture({ input }) {
    const { sector } = fixtureBrief(input);
    const meta = offlineMeta();
    const output: CompetitorAnalysisOutput = {
      competitors: [
        {
          name: `${OFFLINE_NOTE} unnamed incumbent`,
          description:
            'Offline placeholder. Real competitor names are never invented: this run had ' +
            'no search backend, so no competitor could be identified.',
          url: null,
          isDirect: true,
          businessModel: null,
          pricing: null,
          rating: null,
          reviewCount: null,
          installsRange: null,
          strengths: [],
          weaknesses: [],
          features: [],
          evidenceType: 'HYPOTHESIS',
          sourceUrls: [],
        },
      ],
      matrix: { dimensions: [], rows: [] },
      saturationScore: 50,
      saturationRationale:
        'Neutral placeholder: saturation cannot be assessed without competitor evidence.',
      whitespace: [`${OFFLINE_NOTE} whitespace unknown for ${sector}`],
      claims: [
        { statement: 'No competitor was researched in this offline run.', ...meta },
      ],
      confidence: 0.1,
    };
    return output;
  },
};
