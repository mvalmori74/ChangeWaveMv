import { zodToJsonSchema } from 'zod-to-json-schema';
import { ZodError } from 'zod';
import { AgentExecutionError, BudgetExceededError, toErrorMessage } from '../core/errors.js';
import type { LlmMessage, LlmProvider, ModelRouter } from '../providers/llm/types.js';
import type { SearchResult } from '../providers/search/types.js';
import { GLOBAL_AGENT_RULES, renderEvidence } from './prompt.js';
import type {
  AgentContext,
  AgentDefinition,
  AgentRunOutcome,
  EvidenceBundle,
} from './types.js';

export interface AgentRunnerOptions {
  /** Extra attempts when the model returns unparseable or invalid JSON. */
  maxValidationRetries: number;
  /** Upper bound on evidence lines injected into a prompt. */
  maxEvidencePerAgent: number;
}

export const DEFAULT_RUNNER_OPTIONS: AgentRunnerOptions = {
  maxValidationRetries: 1,
  maxEvidencePerAgent: 12,
};

/**
 * The Agent Execution Engine.
 *
 * One code path runs every agent: gather evidence, prompt the model under the
 * global research rules, validate the answer against the agent's own schema,
 * retry once with the validation errors fed back, and account for cost. Agents
 * contain no plumbing of their own.
 */
export class AgentRunner {
  constructor(
    private readonly llm: LlmProvider,
    private readonly modelRouter: ModelRouter,
    private readonly options: AgentRunnerOptions = DEFAULT_RUNNER_OPTIONS,
  ) {}

  async run<TOutput>(
    definition: AgentDefinition<TOutput>,
    context: AgentContext,
  ): Promise<AgentRunOutcome<TOutput>> {
    const startedAt = Date.now();
    const evidence = await this.gatherEvidence(definition, context);
    const input = definition.buildInput(context);

    const model = this.modelRouter.resolve(definition.modelTier);
    const jsonSchema = {
      name: schemaNameFor(definition.key),
      schema: zodToJsonSchema(definition.outputSchema, { $refStrategy: 'none' }) as Record<
        string,
        unknown
      >,
    };

    const messages: LlmMessage[] = [
      { role: 'system', content: `${definition.systemPrompt}\n\n${GLOBAL_AGENT_RULES}` },
      {
        role: 'user',
        content: JSON.stringify(input),
      },
      {
        role: 'user',
        content: renderEvidence(
          evidence.results.map((result) => ({
            url: result.url,
            title: result.title,
            snippet: result.snippet,
            publisher: result.publisher,
            publishedAt: result.publishedAt,
          })),
          evidence.synthetic,
        ),
      },
    ];

    let promptTokens = 0;
    let completionTokens = 0;
    let costUsd = 0;
    let lastError: unknown;
    const maxAttempts = this.options.maxValidationRetries + 1;

    for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
      const estimatedPromptTokens = this.llm.estimateTokens(
        messages.map((message) => message.content).join('\n'),
      );
      // Pre-flight: refuse the call rather than discover the overrun after
      // it has already been billed.
      context.budget.assertCanSpend(estimatedPromptTokens, 0);

      const response = await this.llm.complete({
        messages,
        model,
        // Passed alongside the resolved model so a provider that can vary
        // reasoning depth on its own (Anthropic's `effort`) sees the agent's
        // intent, not just the model id it was mapped to.
        tier: definition.modelTier,
        temperature: 0.2,
        jsonSchema,
        signal: context.signal,
      });

      promptTokens += response.usage.promptTokens;
      completionTokens += response.usage.completionTokens;
      costUsd += response.usage.costUsd;
      context.budget.record(
        response.usage.promptTokens + response.usage.completionTokens,
        response.usage.costUsd,
      );

      try {
        const parsed = parseJsonResponse(response.content);
        const validated = definition.outputSchema.parse(parsed);
        const output = definition.postProcess
          ? await definition.postProcess(validated, context)
          : validated;

        return {
          agentKey: definition.key,
          output,
          model: response.model,
          promptTokens,
          completionTokens,
          costUsd,
          durationMs: Date.now() - startedAt,
          attempts: attempt,
          evidence,
        };
      } catch (error) {
        // A budget stop is final: retrying would spend money we do not have.
        if (error instanceof BudgetExceededError) throw error;
        lastError = error;
        if (attempt === maxAttempts) break;
        context.logger.warn(
          { agentKey: definition.key, attempt, error: toErrorMessage(error) },
          'agent output failed validation, retrying with feedback',
        );
        messages.push({ role: 'assistant', content: response.content });
        messages.push({
          role: 'user',
          content:
            'Your previous answer did not satisfy the schema. Fix exactly these ' +
            `problems and return the corrected JSON only:\n${describeValidationError(error)}`,
        });
      }
    }

    throw new AgentExecutionError(
      `Agent '${definition.key}' produced invalid output after ${maxAttempts} attempt(s): ` +
        toErrorMessage(lastError),
      definition.key,
      { costUsd, promptTokens, completionTokens },
    );
  }

  private async gatherEvidence(
    definition: AgentDefinition,
    context: AgentContext,
  ): Promise<EvidenceBundle> {
    const queries = definition.searchQueries?.(context) ?? [];
    if (queries.length === 0) {
      return { results: [], synthetic: context.search.synthetic };
    }

    const responses = await Promise.allSettled(
      queries.map((query) =>
        context.search.search({
          query,
          country: context.brief.country,
          language: context.brief.language,
          ...(context.signal ? { signal: context.signal } : {}),
        }),
      ),
    );

    const byUrl = new Map<string, SearchResult>();
    for (const [index, response] of responses.entries()) {
      if (response.status === 'rejected') {
        // A failed lookup degrades evidence, it does not fail the agent: the
        // agent will simply have less to cite and must lower its confidence.
        context.logger.warn(
          {
            agentKey: definition.key,
            query: queries[index],
            error: toErrorMessage(response.reason),
          },
          'search query failed; continuing with reduced evidence',
        );
        continue;
      }
      for (const result of response.value.results) {
        const existing = byUrl.get(result.url);
        if (!existing || existing.score < result.score) byUrl.set(result.url, result);
      }
    }

    const results = [...byUrl.values()]
      .sort((a, b) => b.score - a.score)
      .slice(0, this.options.maxEvidencePerAgent);

    return { results, synthetic: context.search.synthetic };
  }
}

export function schemaNameFor(agentKey: string): string {
  return agentKey.replace(/-/g, '_');
}

/**
 * Models occasionally wrap JSON in prose or a code fence even when told not to.
 * Recover the object rather than burning a retry on a formatting slip.
 */
export function parseJsonResponse(content: string): unknown {
  const trimmed = content.trim();
  try {
    return JSON.parse(trimmed);
  } catch {
    const fenced = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/);
    if (fenced?.[1]) return JSON.parse(fenced[1].trim());
    const start = trimmed.indexOf('{');
    const end = trimmed.lastIndexOf('}');
    if (start !== -1 && end > start) return JSON.parse(trimmed.slice(start, end + 1));
    throw new Error('Response was not valid JSON');
  }
}

function describeValidationError(error: unknown): string {
  if (error instanceof ZodError) {
    return error.issues
      .slice(0, 20)
      .map((issue) => `- ${issue.path.join('.') || '(root)'}: ${issue.message}`)
      .join('\n');
  }
  return `- ${toErrorMessage(error)}`;
}
