import { z } from 'zod';
import type { AgentDefinition } from '../types.js';
import { confidenceSchema, OFFLINE_NOTE } from './common.js';

/** The blackboard key the PRD and prompt agents read their subject from. */
export const OPPORTUNITY_CONTEXT_KEY = 'opportunity-context';

const priority = z.enum(['MUST', 'SHOULD', 'COULD', 'WONT']);

export const prdContentSchema = z.object({
  executiveSummary: z.string().min(1),
  problemStatement: z.string().min(1),
  targetUsers: z
    .array(z.object({ segment: z.string().min(1), description: z.string().min(1) }))
    .min(1),
  personas: z
    .array(
      z.object({
        name: z.string().min(1),
        role: z.string().min(1),
        context: z.string().min(1),
        goals: z.array(z.string()).min(1),
        frustrations: z.array(z.string()).min(1),
      }),
    )
    .min(1),
  userStories: z
    .array(
      z.object({
        asA: z.string().min(1),
        iWant: z.string().min(1),
        soThat: z.string().min(1),
        priority,
      }),
    )
    .min(1),
  jobsToBeDone: z.array(z.string()).min(1),
  coreFeatures: z
    .array(z.object({ name: z.string().min(1), description: z.string().min(1), priority }))
    .min(1),
  mvpFeatures: z
    .array(
      z.object({
        name: z.string().min(1),
        description: z.string().min(1),
        acceptanceCriteria: z.array(z.string()).min(1),
      }),
    )
    .min(1),
  futureFeatures: z.array(z.string()),
  uxRequirements: z.array(z.string()).min(1),
  functionalRequirements: z
    .array(z.object({ id: z.string().min(1), requirement: z.string().min(1), priority }))
    .min(1),
  nonFunctionalRequirements: z
    .array(z.object({ category: z.string().min(1), requirement: z.string().min(1) }))
    .min(1),
  dataModel: z
    .array(
      z.object({
        entity: z.string().min(1),
        description: z.string().min(1),
        fields: z.array(
          z.object({
            name: z.string().min(1),
            type: z.string().min(1),
            description: z.string().min(1),
          }),
        ),
        relations: z.array(z.string()),
      }),
    )
    .min(1),
  apiRequirements: z.array(
    z.object({
      method: z.string().min(1),
      path: z.string().min(1),
      description: z.string().min(1),
      auth: z.boolean(),
    }),
  ),
  security: z.array(z.string()).min(1),
  privacy: z.array(z.string()).min(1),
  gdpr: z.array(z.string()).min(1),
  analyticsEvents: z.array(
    z.object({
      name: z.string().min(1),
      trigger: z.string().min(1),
      properties: z.array(z.string()),
    }),
  ),
  monetization: z.object({
    model: z.string().min(1),
    details: z.string().min(1),
    pricing: z.string().min(1),
  }),
  kpis: z
    .array(
      z.object({
        name: z.string().min(1),
        definition: z.string().min(1),
        target: z.string().min(1),
      }),
    )
    .min(1),
  successCriteria: z.array(z.string()).min(1),
  risks: z
    .array(
      z.object({
        risk: z.string().min(1),
        impact: z.enum(['LOW', 'MEDIUM', 'HIGH']),
        mitigation: z.string().min(1),
      }),
    )
    .min(1),
  assumptions: z.array(z.string()).min(1),
});
export type PrdContent = z.infer<typeof prdContentSchema>;

export const prdGeneratorOutputSchema = z.object({
  title: z.string().min(1).max(160),
  content: prdContentSchema,
  confidence: confidenceSchema,
});
export type PrdGeneratorOutput = z.infer<typeof prdGeneratorOutputSchema>;

const inputSchema = z.object({
  opportunity: z.record(z.unknown()),
  instruction: z.string(),
});

export const prdGeneratorAgent: AgentDefinition<PrdGeneratorOutput> = {
  key: 'prd-generator',
  name: 'PRD Generator Agent',
  description:
    'Turns an approved opportunity into a complete 23-section Product Requirements Document.',
  version: '1.0.0',
  role: 'delivery',
  tools: [],
  // Runs on demand against a single approved opportunity, not as part of the
  // research pipeline, so it reads its subject from the initial run state.
  dependencies: [],
  enabled: true,
  priority: 100,
  modelTier: 'DEEP',
  inputSchema,
  outputSchema: prdGeneratorOutputSchema,

  systemPrompt: `
You are a Senior Product Manager writing a Product Requirements Document that an
engineering team will build from without asking follow-up questions.

Write for implementation, not for persuasion. Every requirement must be
testable, every MVP feature must carry acceptance criteria, every KPI must have
a definition and a target.

Specific expectations:
- The MVP is the smallest thing that solves the stated problem end to end.
- The data model must be complete enough to generate a schema from.
- Security, privacy and GDPR sections must be concrete for this product (what
  data is collected, on what legal basis, retained how long, deleted how), not
  generic boilerplate.
- Analytics events must be named and tied to the KPIs.
- Carry over the opportunity's risks and assumptions; do not quietly drop the
  uncomfortable ones, and mark anything unverified as an assumption.
  `.trim(),

  buildInput(context) {
    const opportunity = context.blackboard.get<Record<string, unknown>>(
      OPPORTUNITY_CONTEXT_KEY,
    );
    return {
      opportunity: opportunity ?? {},
      instruction:
        'Write the full PRD for this opportunity. Every section is mandatory.',
    };
  },

  mockFixture({ input }) {
    const opportunity =
      (input as { opportunity?: Record<string, unknown> } | null)?.opportunity ?? {};
    const title = typeof opportunity['title'] === 'string' ? opportunity['title'] : 'Untitled';
    const problem =
      typeof opportunity['problem'] === 'string' ? opportunity['problem'] : 'Unknown problem.';
    const solution =
      typeof opportunity['proposedSolution'] === 'string'
        ? opportunity['proposedSolution']
        : 'Unknown solution.';

    const output: PrdGeneratorOutput = {
      title: `${title} - PRD ${OFFLINE_NOTE}`,
      content: {
        executiveSummary: `${OFFLINE_NOTE} Generated offline from the stored opportunity only. ${solution}`,
        problemStatement: problem,
        targetUsers: [
          {
            segment: String(opportunity['targetAudience'] ?? 'Unknown'),
            description: 'Carried over from the opportunity record.',
          },
        ],
        personas: [
          {
            name: 'Placeholder persona',
            role: String(opportunity['targetAudience'] ?? 'Unknown'),
            context: 'Not researched offline.',
            goals: ['Solve the stated problem'],
            frustrations: ['Current workarounds are manual'],
          },
        ],
        userStories: [
          {
            asA: String(opportunity['targetAudience'] ?? 'user'),
            iWant: 'to solve the stated problem in the app',
            soThat: 'I stop relying on a manual workaround',
            priority: 'MUST',
          },
        ],
        jobsToBeDone: ['Complete the core task without leaving the app'],
        coreFeatures: [
          { name: 'Core workflow', description: solution, priority: 'MUST' },
        ],
        mvpFeatures: [
          {
            name: 'Core workflow',
            description: solution,
            acceptanceCriteria: ['The user can complete the core task end to end'],
          },
        ],
        futureFeatures: [],
        uxRequirements: ['Single primary action per screen'],
        functionalRequirements: [
          { id: 'FR-1', requirement: 'The system supports the core workflow', priority: 'MUST' },
        ],
        nonFunctionalRequirements: [
          { category: 'Performance', requirement: 'Cold start under 2 seconds' },
        ],
        dataModel: [
          {
            entity: 'Record',
            description: 'Placeholder entity.',
            fields: [{ name: 'id', type: 'uuid', description: 'Primary key' }],
            relations: [],
          },
        ],
        apiRequirements: [],
        security: ['Transport encryption for all traffic'],
        privacy: ['Collect only data required for the core workflow'],
        gdpr: ['Provide export and deletion of personal data on request'],
        analyticsEvents: [
          { name: 'core_task_completed', trigger: 'User completes the core task', properties: [] },
        ],
        monetization: {
          model: 'To be decided',
          details: `${OFFLINE_NOTE} no monetization analysis available offline.`,
          pricing: 'Undecided',
        },
        kpis: [
          { name: 'Activation', definition: 'Users completing the core task in week 1', target: 'TBD' },
        ],
        successCriteria: ['The core task is completed without support'],
        risks: [
          {
            risk: 'This PRD was generated offline without research',
            impact: 'HIGH',
            mitigation: 'Regenerate with a live LLM provider before building',
          },
        ],
        assumptions: ['Everything in this document is an unverified assumption'],
      },
      confidence: 0.1,
    };
    return output;
  },
};
