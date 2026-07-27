import { describe, expect, it } from 'vitest';
import { prdGeneratorAgent, type PrdContent } from '../agents/definitions/prd-generator.agent.js';
import {
  promptEngineerAgent,
  PROMPT_SECTIONS,
  type PromptEngineerOutput,
} from '../agents/definitions/prompt-engineer.agent.js';
import { CODEX_EXECUTION_RULES, renderCodexPromptMarkdown } from './codex-prompt-markdown.js';
import { renderPrdMarkdown } from './prd-markdown.js';

const prdFixture = prdGeneratorAgent.mockFixture!({
  input: { opportunity: { title: 'Fleet checks', problem: 'P', proposedSolution: 'S' } },
  model: 'test',
}) as { title: string; content: PrdContent };

const promptFixture = promptEngineerAgent.mockFixture!({
  input: { opportunity: { title: 'Fleet checks' } },
  model: 'test',
}) as PromptEngineerOutput;

const meta = {
  opportunityTitle: 'Fleet checks',
  finalScore: 78,
  classification: 'HIGH_POTENTIAL',
  confidence: 0.62,
  generatedAt: new Date('2026-01-01T00:00:00.000Z'),
  synthetic: false,
};

describe('renderPrdMarkdown', () => {
  const markdown = renderPrdMarkdown('Fleet checks PRD', prdFixture.content, meta);

  it('emits all 23 numbered sections in order', () => {
    const headings = [...markdown.matchAll(/^## (\d+)\./gm)].map((match) => Number(match[1]));
    expect(headings).toEqual(Array.from({ length: 23 }, (_, index) => index + 1));
  });

  it('includes the score and confidence in the header table', () => {
    expect(markdown).toContain('78 (HIGH_POTENTIAL)');
    expect(markdown).toContain('62%');
  });

  it('is deterministic for the same input', () => {
    expect(renderPrdMarkdown('Fleet checks PRD', prdFixture.content, meta)).toBe(markdown);
  });

  it('warns loudly when the document came from the offline provider', () => {
    const synthetic = renderPrdMarkdown('X', prdFixture.content, { ...meta, synthetic: true });
    expect(synthetic).toContain('offline mock provider');
  });

  it('escapes pipes so a table cell cannot break the table', () => {
    const content: PrdContent = {
      ...prdFixture.content,
      coreFeatures: [{ name: 'A', description: 'has | a pipe', priority: 'MUST' }],
    };
    expect(renderPrdMarkdown('X', content, meta)).toContain('has \\| a pipe');
  });
});

describe('renderCodexPromptMarkdown', () => {
  const markdown = renderCodexPromptMarkdown(promptFixture, {
    generatedAt: new Date('2026-01-01T00:00:00.000Z'),
    opportunityTitle: 'Fleet checks',
    synthetic: false,
  });

  it('emits every required section', () => {
    for (const section of PROMPT_SECTIONS) {
      expect(markdown).toContain(`## ${section.replace(/_/g, ' ')}`);
    }
  });

  it('always appends the platform execution rules verbatim', () => {
    // These are not model output: every exported prompt must carry them.
    expect(markdown).toContain(CODEX_EXECUTION_RULES);
    expect(markdown).toContain('Never invent an API');
    expect(markdown).toContain('Leave no critical TODO');
  });

  it('includes the stack and repository layout', () => {
    expect(markdown).toContain('## STACK');
    expect(markdown).toContain('## REPOSITORY STRUCTURE');
  });
});
