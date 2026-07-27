# Agents

## 1. The contract

Every agent is an `AgentDefinition` (`apps/api/src/agents/types.ts`):

| Field | Purpose |
|---|---|
| `key` | Stable kebab-case id, used in `dependencies` and on the blackboard |
| `name`, `description`, `version`, `role` | Registry metadata, shown in the UI |
| `systemPrompt` | The agent's instructions; the global research rules are appended automatically |
| `inputSchema` / `outputSchema` | zod contracts; the JSON Schema sent to the model is derived from them |
| `dependencies` | Agent keys whose output must exist first |
| `enabled`, `priority` | Registry control; priority breaks ties in the topological sort |
| `modelTier` | `FAST` / `BALANCED` / `DEEP`, resolved to a model id by configuration |
| `searchQueries?` | Evidence gathering before prompting |
| `buildInput` | The payload the model reasons over |
| `postProcess?` | Deterministic post-processing of validated output |
| `mockFixture?` | Offline output, so the agent runs with no API key |

The agent contains no plumbing: prompting, retries, validation, cost accounting
and persistence are handled once by the execution engine.

## 2. Global rules

`apps/api/src/agents/prompt.ts` appends the same non-negotiables to every system
prompt: never invent facts or URLs, cite only supplied evidence, tag every claim
with an evidence type, use ranges with a stated basis instead of false
precision, and answer with JSON only. A new agent inherits all of it.

## 3. The pipeline

```
trend-hunter
    └─▶ market-research
            └─▶ competitor-analysis
                    └─▶ review-analysis
                            └─▶ problem-discovery ◀── (also trend + market)
                                    └─▶ opportunity-synthesis
                                            ├─▶ monetization ◀── (also market)
                                            ├─▶ technical-feasibility
                                            └─▶ opportunity-scoring ◀── (all of the above)
```

`prd-generator` and `prompt-engineer` are **not** part of a research run. They
have no dependencies and are invoked one at a time against a single approved
opportunity, receiving their subject through the initial blackboard state.

| Agent | Tier | Produces |
|---|---|---|
| `trend-hunter` | BALANCED | Technology, consumer, B2B and regulatory shifts, each with its implication for a product |
| `market-research` | BALANCED | Size range (or `null`), demand level, growth, seasonality, segments, key problems |
| `competitor-analysis` | BALANCED | Direct and indirect competitors, comparison matrix, saturation score, whitespace |
| `review-analysis` | BALANCED | Pain points, feature requests, bugs, complaints, pricing / UX / ads signals |
| `problem-discovery` | DEEP | Concrete problems: who, when, what it costs, what they do today, the gap |
| `opportunity-synthesis` | DEEP | Candidate products with an MVP outline, differentiators, risks, assumptions |
| `monetization` | BALANCED | Every model assessed, one recommended, ARPU range, conversion assumption, risks |
| `technical-feasibility` | BALANCED | Complexity, effort range, APIs, dependencies, infra cost, maintenance, 0-100 score |
| `opportunity-scoring` | DEEP | Nine criterion scores with rationale, evidence and confidence |
| `prd-generator` | DEEP | The 23-section PRD |
| `prompt-engineer` | DEEP | The 16-section build prompt |

## 4. Scoring criteria

| Criterion | Weight | 100 means |
|---|---|---|
| Market Demand | 20% | Strong, growing, evidenced demand |
| Competition | 15% | Almost no credible competitor (**favourable**) |
| User Pain | 15% | Frequent, severe, badly-served pain |
| Monetization | 15% | Clear willingness to pay |
| Retention Potential | 10% | Habitual use |
| Technical Feasibility | 10% | Small team, weeks, no exotic dependency |
| ASO Potential | 5% | Searchable keywords, weak incumbents |
| Maintenance Cost | 5% | Negligible ongoing cost (**favourable**) |
| Strategic Fit | 5% | Fits the operator's focus and assets |

Higher is always better for the opportunity, including the two inverted
criteria. Bands: `0-39 KILL`, `40-59 LOW_POTENTIAL`, `60-74 PROMISING`,
`75-89 HIGH_POTENTIAL`, `90-100 EXCEPTIONAL`.

## 5. Adding an agent

1. Create `apps/api/src/agents/definitions/my-agent.agent.ts`:

```ts
export const myAgentOutputSchema = z.object({ findings: z.array(z.string()) });

export const myAgent: AgentDefinition<z.infer<typeof myAgentOutputSchema>> = {
  key: 'my-agent',
  name: 'My Agent',
  description: 'What it is responsible for.',
  version: '1.0.0',
  role: 'research',
  tools: ['web_search'],
  dependencies: ['market-research'],   // ← the only wiring you write
  enabled: true,
  priority: 45,
  modelTier: 'FAST',
  inputSchema: z.object({ brief: z.record(z.unknown()) }),
  outputSchema: myAgentOutputSchema,
  searchQueries: (context) => [`${context.brief.sector} something`],
  buildInput: (context) => ({
    brief: briefPayload(context.brief),
    market: dependencyOutput<MarketResearchOutput>(context, 'market-research'),
  }),
  systemPrompt: '…',
  mockFixture: () => ({ findings: [] }),
};
```

2. Add it to `BUILT_IN_AGENTS` in `definitions/index.ts`.

That is the whole change. The registry derives its JSON schemas, the
orchestrator places it in the run order, the mock provider picks up its fixture,
the agent syncs into the `agents` table at boot, and the UI lists it.

To have it run inside a research run, either give an existing pipeline agent a
dependency on it, or add it to `RESEARCH_PIPELINE_ROOTS`.

A test in `registry.test.ts` asserts that every built-in agent has a fixture and
that the fixture satisfies the agent's own schema, so a new agent cannot quietly
break offline mode.

## 6. Execution telemetry

Every attempt writes an `AgentExecution`: input, output, model, prompt and
completion tokens, estimated cost, duration, attempt number, error and
timestamps. `GET /api/analytics/agent-costs` aggregates it per agent, which is
how you decide what to move to a cheaper tier.
