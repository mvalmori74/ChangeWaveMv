import { z } from 'zod';
import type { AgentDefinition } from '../types.js';
import { confidenceSchema, OFFLINE_NOTE } from './common.js';

/** The blackboard key holding the PRD the prompt is derived from. */
export const PRD_CONTEXT_KEY = 'prd-context';

/**
 * Section order of the generated Codex prompt. The renderer emits them in this
 * order, and ACCEPTANCE_CRITERIA / IMPLEMENTATION_PLAN / DELIVERABLES are
 * always followed by the platform's own non-negotiable rules block.
 */
export const PROMPT_SECTIONS = [
  'PROJECT_CONTEXT',
  'BUSINESS_OBJECTIVE',
  'TARGET_USERS',
  'MVP',
  'TECHNICAL_ARCHITECTURE',
  'DATA_MODEL',
  'API',
  'UI',
  'SECURITY',
  'PRIVACY',
  'MONETIZATION',
  'ANALYTICS',
  'TESTING',
  'ACCEPTANCE_CRITERIA',
  'IMPLEMENTATION_PLAN',
  'DELIVERABLES',
] as const;
export type PromptSection = (typeof PROMPT_SECTIONS)[number];

const sectionsShape = Object.fromEntries(
  PROMPT_SECTIONS.map((section) => [section, z.string().min(1)]),
) as Record<PromptSection, z.ZodString>;

export const promptEngineerOutputSchema = z.object({
  projectName: z.string().min(1).max(120),
  stack: z.object({
    platform: z.string().min(1),
    language: z.string().min(1),
    frameworks: z.array(z.string()),
    database: z.string().min(1),
    infrastructure: z.string().min(1),
  }),
  repositoryLayout: z.array(z.string()).min(1),
  sections: z.object(sectionsShape),
  confidence: confidenceSchema,
});
export type PromptEngineerOutput = z.infer<typeof promptEngineerOutputSchema>;

const inputSchema = z.object({
  opportunity: z.record(z.unknown()),
  prd: z.record(z.unknown()),
  sections: z.array(z.string()),
  instruction: z.string(),
});

export const promptEngineerAgent: AgentDefinition<PromptEngineerOutput> = {
  key: 'prompt-engineer',
  name: 'Prompt Engineer Agent',
  description:
    'Transforms a PRD into a sectioned, implementation-ready prompt for a code generation agent.',
  version: '1.0.0',
  role: 'delivery',
  tools: [],
  dependencies: [],
  enabled: true,
  priority: 110,
  modelTier: 'DEEP',
  inputSchema,
  outputSchema: promptEngineerOutputSchema,

  systemPrompt: `
You are a Prompt Engineer producing the brief that a code generation agent will
build an entire application from, unattended.

Write each requested section as detailed, unambiguous markdown. The reader has
your text and nothing else: no access to the PRD, no ability to ask questions.

Rules:
- Be concrete. Name the screens, the endpoints, the tables, the events.
- Never invent an external API, SDK or service. If the product needs one, state
  the capability required and say the implementer must verify availability.
- ACCEPTANCE_CRITERIA must be checkable statements, not aspirations.
- IMPLEMENTATION_PLAN must be ordered phases, each independently verifiable and
  each leaving the build green.
- TESTING must state what is unit tested, what is integration tested, and the
  minimum a reviewer should run.
- Do not restate the whole PRD: translate it into build instructions.
  `.trim(),

  buildInput(context) {
    const prd = context.blackboard.get<Record<string, unknown>>(PRD_CONTEXT_KEY);
    const opportunity = context.blackboard.get<Record<string, unknown>>('opportunity-context');
    return {
      opportunity: opportunity ?? {},
      prd: prd ?? {},
      sections: [...PROMPT_SECTIONS],
      instruction:
        'Write every listed section. The output is the sole input to an autonomous build.',
    };
  },

  mockFixture({ input }) {
    const opportunity =
      (input as { opportunity?: Record<string, unknown> } | null)?.opportunity ?? {};
    const title = typeof opportunity['title'] === 'string' ? opportunity['title'] : 'Untitled app';
    const sections = Object.fromEntries(
      PROMPT_SECTIONS.map((section) => [
        section,
        `${OFFLINE_NOTE} ${section.replace(/_/g, ' ').toLowerCase()} was not generated: ` +
          'this prompt was produced by the offline mock provider.',
      ]),
    ) as Record<PromptSection, string>;

    const output: PromptEngineerOutput = {
      projectName: title,
      stack: {
        platform: 'Android',
        language: 'Kotlin',
        frameworks: ['Jetpack Compose'],
        database: 'Room',
        infrastructure: 'None (offline placeholder)',
      },
      repositoryLayout: ['app/', 'README.md'],
      sections,
      confidence: 0.1,
    };
    return output;
  },
};
