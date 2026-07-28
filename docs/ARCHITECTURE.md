# Architecture

## 1. Components

```
┌──────────────────────────────────────────────────────────────────────┐
│ WEB (React + Vite + MUI)                                             │
│  Overview · Research · Opportunities · Opportunity detail · Pipeline │
│  · Agent registry                                                    │
└───────────────────────────┬──────────────────────────────────────────┘
                            │ JSON over HTTP, bearer JWT
┌───────────────────────────▼──────────────────────────────────────────┐
│ API (Fastify)                                                        │
│  routes → services → agents/providers → Prisma                       │
│                                                                      │
│  ┌────────────────┐   ┌──────────────────┐   ┌────────────────────┐  │
│  │ Agent Registry │──▶│ Orchestrator     │──▶│ Execution Engine   │  │
│  │ (agents = data)│   │ (DAG + budget)   │   │ (prompt, validate, │  │
│  └────────────────┘   └────────┬─────────┘   │  retry, account)   │  │
│                                │             └─────────┬──────────┘  │
│  ┌─────────────────────────────▼──────────┐            │             │
│  │ Blackboard (run-scoped shared state)   │            │             │
│  └────────────────────────────────────────┘            │             │
│                                                        ▼             │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────────────────┐   │
│  │ LlmProvider  │  │ SearchProvider│  │ CodeGenerationProvider   │   │
│  │ openai       │  │ tavily│mock   │  │ export                   │   │
│  │ anthropic    │  │               │  │                          │   │
│  │ mock         │  │               │  │                          │   │
│  └──────────────┘  └───────────────┘  └──────────────────────────┘   │
│                                                                      │
│  Scoring Engine · Export renderers · Audit trail · Job queue          │
└───────────────┬──────────────────────────────┬───────────────────────┘
                │                              │
        ┌───────▼────────┐            ┌────────▼────────┐
        │ PostgreSQL     │            │ Redis (BullMQ)  │
        │ (Prisma)       │            │ optional        │
        └────────────────┘            └─────────────────┘
```

## 2. Run flow

```
POST /api/research/projects/:id/runs
  └─ ResearchRun row (PENDING) ─── enqueue ──▶ job queue
                                                 │
                            ┌────────────────────▼──────────────────────┐
                            │ ResearchService.executeRun                │
                            │  Orchestrator.execute(agentKeys: [leaf])  │
                            └────────────────────┬──────────────────────┘
                                                 │ for each agent, in DAG order
                    ┌────────────────────────────▼─────────────────────────────┐
                    │ 1. searchQueries()  → SearchProvider → evidence           │
                    │ 2. buildInput()     → brief + dependency outputs          │
                    │ 3. budget.assertCanSpend()   ← refuses before spending    │
                    │ 4. LlmProvider.complete(jsonSchema)                       │
                    │ 5. zod validate → on failure, retry with the errors       │
                    │ 6. postProcess() (e.g. deterministic scoring)             │
                    │ 7. blackboard.set(key, output) + persist execution        │
                    └────────────────────────────┬─────────────────────────────┘
                                                 │
                    persistRunOutputs: Market, Competitor, ReviewInsight,
                    PainPoint, Opportunity, OpportunityScore
```

A failed agent does not abort the run: its dependants are skipped, the run
finishes `PARTIAL`, and everything already produced is kept. Run state is
written to `research_runs.state` after each agent, so a crash never discards
work that has already been paid for.

## 3. Key decisions

### Agents are data, not code paths

An `AgentDefinition` is metadata + a zod output contract + a payload builder.
The orchestrator asks the registry for a topological order derived from each
agent's `dependencies` and executes whatever it gets back. Adding an agent means
adding a file and listing it in `definitions/index.ts` — no orchestrator change,
no switch statement, no new route.

Disabling an agent transitively drops everything that depends on it, so a
half-fed downstream agent can never run against a hole in the blackboard.

### Agents never call each other

They communicate only through the run-scoped blackboard, keyed by agent key.
That is what keeps the dependency graph honest and the agents independently
testable.

### Scoring is deterministic code

The scoring agent supplies `rawScore`, `rationale`, `evidence` and `confidence`
per criterion. Weighting, renormalisation for missing criteria, the final score
and the classification are pure functions in `packages/shared/src/scoring.ts`.
Consequences: identical inputs give identical scores, the arithmetic can be
audited, and the weight table can be changed without touching a prompt.

Criteria a run did not score are *excluded and the remaining weights
renormalised*, not counted as zero — an unanswered question is not evidence of a
bad opportunity — and the missing list is reported so the UI can flag a partial
score.

### Everything is behind a provider interface

`LlmProvider`, `SearchProvider` and `CodeGenerationProvider` each have a real
implementation and an offline one. The offline search provider returns **no
results at all** rather than plausible fake URLs: a fabricated citation is worse
than a missing one, because the whole point of the evidence layer is that a URL
in the UI can be opened and checked.

`LlmProvider` has two real backends, OpenAI and Anthropic, selected by
`LLM_PROVIDER`. Adding the second one touched no agent, no prompt and no schema:
one file implementing the interface, one branch in the composition root, and
per-provider default model ids in `ConfigModelRouter`. Vendor differences stay
inside the provider — Anthropic takes the system prompt as a top-level field
rather than a message, rejects sampling parameters on current models, reports
cached tokens separately (priced separately, so the run budget stays accurate),
and can decline a request with a `refusal` stop reason, which the provider turns
into a typed `ProviderError` naming the policy category instead of letting an
empty body surface as a JSON parse failure two layers up.

The only capability the interface grew for it is `tier`: the task tier travels
alongside the resolved model id, so a provider that can vary reasoning depth
independently of the model can use it, and one that cannot ignores it.

### Cost control is enforced, not advisory

`BudgetTracker` holds the run's USD and token ceilings. The execution engine
performs a pre-flight check before every call, so an over-budget request is
refused *before* it is billed. Hitting a ceiling stops the run — there is no
automatic escalation and no retry of a budget failure. Agents declare a model
*tier* (`FAST`/`BALANCED`/`DEEP`), never a model id, so the whole fleet can be
re-pointed at cheaper models from configuration.

### Fastify over NestJS

The spec allowed either. Fastify with an explicit composition root
(`core/container.ts`) gives the same dependency injection and modularity with
less ceremony, faster boot, and no decorator metadata build step. Every
dependency is constructed in one file and injected downwards; nothing below it
reaches for a global or chooses its own provider.

### Queue abstraction with an in-process default

`JobQueue` has a BullMQ implementation (used when `REDIS_URL` is set) and an
in-process one (used otherwise). Requiring Redis just to try the platform would
be hostile; requiring durability in production is right. The in-process queue is
not durable — a restart leaves the run `PENDING`, visible in the UI, rather than
silently losing it.

### Custom orchestrator instead of LangGraph

The spec allowed "LangGraph or an equivalent modular architecture". A ~200-line
typed orchestrator gives exactly the semantics needed (topological execution,
blackboard, budget, partial failure) with full type inference over each agent's
output and no external state-machine dependency to keep in step.

## 4. Error handling

| Layer | Behaviour |
|---|---|
| Config | Validated at boot; a bad or missing variable fails the process immediately |
| HTTP | One error handler maps `AppError` subclasses to status codes; internals never leak |
| Agent output | zod-validated, one retry with the validation errors fed back, then a typed failure |
| Search | A failed query degrades evidence and lowers confidence; it does not fail the agent |
| Persistence of telemetry | Best-effort: logged, never fatal to a run |
| Budget | Terminal for the run; never retried |

## 5. Security and privacy

- Passwords hashed with bcrypt (cost 12); login compares against a dummy hash
  when the account does not exist, so timing does not reveal which emails exist.
- JWT bearer tokens, expiry configurable, secret required to be 32+ characters.
- Every query is scoped by owner; a resource belonging to someone else answers
  `404`, not `403`, so ids cannot be probed.
- Structured logs redact `authorization`, cookies, passwords and API keys.
- Secrets come from the environment only; `.env` is git-ignored and
  `.env.example` carries no real values.
- `AuditLog` records every state change with actor, action, before and after.

## 6. Testing strategy

| Kind | Location | Needs |
|---|---|---|
| Scoring maths | `packages/shared/src/scoring.test.ts` | nothing |
| Registry, engine, orchestrator, budget, providers, exporters, config | `apps/api/src/**/*.test.ts` | nothing |
| Full HTTP + pipeline | `apps/api/src/test/api.integration.test.ts` | `DATABASE_TEST_URL` |
| Browser smoke | `apps/web/e2e/smoke.spec.ts` | a running stack |

The default `npm test` runs with no database, no Redis and no network, which is
what makes it usable in CI and as a pre-commit gate.

### Continuous integration

`.github/workflows/ai-app-factory.yml` runs on every push to the feature branch:

- **verifica** — type check, build, unit tests, then the integration suite
  against a PostgreSQL service container and the Playwright tests against a
  running API and frontend. Publishes the web bundle as an artifact.
- **immagini** — builds both Docker images, brings the compose stack up with
  `--wait`, and drives a complete research run over HTTP. This is the only place
  the BullMQ/Redis queue path is exercised, since local development uses the
  in-process queue. The images are published as downloadable artifacts.
