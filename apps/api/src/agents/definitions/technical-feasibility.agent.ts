import { z } from 'zod';
import type { AgentDefinition } from '../types.js';
import {
  briefPayload,
  confidenceSchema,
  dependencyOutput,
  evidenceTypeSchema,
  fixtureCandidates,
  OFFLINE_NOTE,
  rangeSchema,
} from './common.js';
import type { OpportunitySynthesisOutput } from './opportunity-synthesis.agent.js';

const assessmentSchema = z.object({
  candidateId: z.string().min(1),
  complexity: z.enum(['LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH']),
  /** Effort for a competent 1-2 person team, as a range in weeks. */
  effortWeeks: rangeSchema.nullable(),
  requiredApis: z.array(z.string()).max(15),
  externalDependencies: z.array(z.string()).max(15),
  needsBackend: z.boolean(),
  needsAi: z.boolean(),
  infrastructureCostMonthly: rangeSchema.nullable(),
  maintenanceBurden: z.enum(['LOW', 'MEDIUM', 'HIGH']),
  keyRisks: z.array(z.string()).max(10),
  /** 0-100, higher = easier to build and keep running. */
  feasibilityScore: z.number().min(0).max(100),
  rationale: z.string().min(1),
  evidenceType: evidenceTypeSchema,
  confidence: confidenceSchema,
});
export type FeasibilityAssessment = z.infer<typeof assessmentSchema>;

export const technicalFeasibilityOutputSchema = z.object({
  assessments: z.array(assessmentSchema).min(1),
  confidence: confidenceSchema,
});
export type TechnicalFeasibilityOutput = z.infer<typeof technicalFeasibilityOutputSchema>;

const inputSchema = z.object({
  brief: z.record(z.unknown()),
  candidates: z.unknown(),
  instruction: z.string(),
});

export const technicalFeasibilityAgent: AgentDefinition<TechnicalFeasibilityOutput> = {
  key: 'technical-feasibility',
  name: 'Technical Feasibility Agent',
  description:
    'Estimates complexity, effort, dependencies, infrastructure cost and maintenance burden per candidate.',
  version: '1.0.0',
  role: 'evaluation',
  tools: [],
  dependencies: ['opportunity-synthesis'],
  enabled: true,
  priority: 71,
  modelTier: 'BALANCED',
  inputSchema,
  outputSchema: technicalFeasibilityOutputSchema,

  systemPrompt: `
You are a Principal Engineer estimating build feasibility.

For each candidate assess: complexity, effort for a 1-2 person team, the APIs
and third-party services required, whether a backend and whether AI is really
needed, monthly infrastructure cost, and ongoing maintenance burden.

Be specific about dependencies that can kill a project: an API with no public
access, a data source that requires a commercial licence, a store policy that
forbids the core mechanic, a hardware integration. List those in keyRisks.

feasibilityScore is 0-100 where 100 means a small team ships it in weeks with
no exotic dependency. Effort and cost are ranges with a stated basis, or null.
Return exactly one assessment per candidateId you were given.
  `.trim(),

  buildInput(context) {
    const synthesis = dependencyOutput<OpportunitySynthesisOutput>(
      context,
      'opportunity-synthesis',
    );
    return {
      brief: briefPayload(context.brief),
      candidates: (synthesis?.candidates ?? []).map((candidate) => ({
        candidateId: candidate.candidateId,
        title: candidate.title,
        proposedSolution: candidate.proposedSolution,
        mvpFeatures: candidate.mvpFeatures,
      })),
      instruction: 'Assess what it really takes to build and operate each candidate.',
    };
  },

  mockFixture({ input }) {
    const candidates = fixtureCandidates(input);
    const output: TechnicalFeasibilityOutput = {
      assessments: (candidates.length > 0 ? candidates : [{ candidateId: 'c1' }]).map(
        (candidate) => ({
          candidateId: candidate.candidateId,
          complexity: 'MEDIUM' as const,
          effortWeeks: null,
          requiredApis: [],
          externalDependencies: [],
          needsBackend: true,
          needsAi: false,
          infrastructureCostMonthly: null,
          maintenanceBurden: 'MEDIUM' as const,
          keyRisks: ['Synthetic output: no technical assessment was performed.'],
          feasibilityScore: 50,
          rationale: `${OFFLINE_NOTE} neutral placeholder score.`,
          evidenceType: 'HYPOTHESIS' as const,
          confidence: 0.1,
        }),
      ),
      confidence: 0.1,
    };
    return output;
  },
};
