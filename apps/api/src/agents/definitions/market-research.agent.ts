import { z } from 'zod';
import type { AgentDefinition } from '../types.js';
import {
  briefPayload,
  claimSchema,
  confidenceSchema,
  dependencyOutput,
  fixtureBrief,
  OFFLINE_NOTE,
  offlineMeta,
  rangeSchema,
} from './common.js';
import type { TrendHunterOutput } from './trend-hunter.agent.js';

const audienceSchema = z.object({
  segment: z.string().min(1),
  description: z.string().min(1),
  sizeEstimate: rangeSchema.nullable().optional(),
  buyingPower: z.enum(['LOW', 'MEDIUM', 'HIGH', 'UNKNOWN']).default('UNKNOWN'),
});

export const marketResearchOutputSchema = z.object({
  market: z.object({
    name: z.string().min(1),
    description: z.string().min(1),
    /** Addressable size. Always a range with its basis, never a single number. */
    sizeEstimate: rangeSchema.nullable(),
    demandLevel: z.enum(['LOW', 'MEDIUM', 'HIGH', 'UNKNOWN']),
    growthTrend: z.string().min(1),
    seasonality: z.string().min(1),
  }),
  targetUsers: z.array(audienceSchema).max(8),
  keyProblems: z.array(z.string()).max(15),
  claims: z.array(claimSchema).max(20),
  confidence: confidenceSchema,
});
export type MarketResearchOutput = z.infer<typeof marketResearchOutputSchema>;

const inputSchema = z.object({
  brief: z.record(z.unknown()),
  trends: z.unknown(),
  instruction: z.string(),
});

export const marketResearchAgent: AgentDefinition<MarketResearchOutput> = {
  key: 'market-research',
  name: 'Market Research Agent',
  description:
    'Sizes the addressable market, characterises demand, growth, seasonality and the target segments.',
  version: '1.0.0',
  role: 'research',
  tools: ['web_search'],
  dependencies: ['trend-hunter'],
  enabled: true,
  priority: 20,
  modelTier: 'BALANCED',
  inputSchema,
  outputSchema: marketResearchOutputSchema,

  systemPrompt: `
You are a Market Research analyst.

Characterise the market implied by the brief: its size, demand level, growth
direction, seasonality, who the users are and what they struggle with.

Market sizing is the part most often faked. Do not fake it. If you cannot ground
a number in the evidence, return sizeEstimate as null rather than a plausible
invention. When you do give a range, state in "basis" exactly how you derived
it (which figure, from which source, multiplied by what).

Use the trends provided as context, not as facts to repeat.
  `.trim(),

  searchQueries(context) {
    const { sector, country, timeframe } = context.brief;
    return [
      `${sector} market size ${country} ${timeframe}`,
      `${sector} number of businesses OR professionals ${country} statistics`,
      `${sector} industry growth rate ${timeframe}`,
      `${sector} customer problems survey ${country}`,
    ];
  },

  buildInput(context) {
    const trends = dependencyOutput<TrendHunterOutput>(context, 'trend-hunter');
    return {
      brief: briefPayload(context.brief),
      trends: trends?.trends ?? [],
      instruction:
        'Quantify demand where the evidence allows, and describe the target segments precisely.',
    };
  },

  mockFixture({ input }) {
    const { sector, country } = fixtureBrief(input);
    const meta = offlineMeta();
    const output: MarketResearchOutput = {
      market: {
        name: `${OFFLINE_NOTE} ${sector} apps - ${country}`,
        description: 'Placeholder market description produced offline. Not researched.',
        sizeEstimate: null,
        demandLevel: 'UNKNOWN',
        growthTrend: 'Unknown offline; no growth data was retrieved.',
        seasonality: 'Unknown offline.',
      },
      targetUsers: [
        {
          segment: `${sector} practitioners`,
          description: 'Placeholder segment. Replace with researched data.',
          sizeEstimate: null,
          buyingPower: 'UNKNOWN',
        },
      ],
      keyProblems: [`${OFFLINE_NOTE} problems not researched in offline mode`],
      claims: [
        {
          statement: 'No market data was retrieved: this run used the offline mock provider.',
          ...meta,
        },
      ],
      confidence: 0.1,
    };
    return output;
  },
};
