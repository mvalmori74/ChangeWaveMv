# Implementation plan and status

## Milestones

| # | Milestone | Status |
|---|---|---|
| 1 | Monorepo, tooling, shared package | Done |
| 2 | Prisma schema + initial migration (20 entities) | Done |
| 3 | Backend core: config, DI, logging, errors, auth, OpenAPI | Done |
| 4 | Agent registry, execution engine, orchestrator | Done |
| 5 | LLM provider layer, model routing, cost control | Done |
| 6 | Search provider abstraction | Done |
| 7 | Trend Hunter | Done |
| 8 | Market Research | Done |
| 9 | Competitor Analysis (+ Review Analysis, Problem Discovery, Synthesis, Monetization, Feasibility) | Done |
| 10 | Opportunity Scoring + scoring engine | Done |
| 11 | Frontend dashboard | Done |
| 12 | Opportunity detail | Done |
| 13 | PRD Generator | Done |
| 14 | Prompt Engineer | Done |
| 15 | Markdown export | Done |
| 16 | Tests: unit, integration, browser | Done |
| 17 | Docker and Docker Compose | Done — built and exercised in CI |
| 18 | Continuous integration | Done — `.github/workflows/ai-app-factory.yml` |
| 19 | Second LLM backend (Anthropic) | Done — selectable with `LLM_PROVIDER` |

## V1 acceptance checklist

| Requirement | Where |
|---|---|
| Complete repository | this repo |
| Working backend | `apps/api` |
| Working frontend | `apps/web` |
| PostgreSQL database | `docker-compose.yml`, `apps/api/prisma` |
| Docker Compose | `docker-compose.yml` |
| Migration | `apps/api/prisma/migrations/20260727000000_init` |
| Agent registry | `apps/api/src/agents/registry.ts` |
| At least 4 working agents | 11 implemented |
| Dashboard | `apps/web/src/pages/OverviewPage.tsx` |
| Scoring engine | `packages/shared/src/scoring.ts`, `apps/api/src/scoring/engine.ts` |
| PRD generator | `apps/api/src/agents/definitions/prd-generator.agent.ts` |
| Prompt engineer | `apps/api/src/agents/definitions/prompt-engineer.agent.ts` |
| Automated tests | 125 unit + 8 integration + 3 browser, all run in CI |
| README | `README.md` |
| Architecture documentation | `docs/ARCHITECTURE.md` |
| API documentation | `docs/API.md` and live OpenAPI at `/docs` |

## Ambiguities in the specification, and how they were resolved

1. **"Do not invent data" vs. offline operation.** The platform must be usable
   without API keys, but synthetic market data is exactly what it exists to
   prevent. Resolution: offline mode is fully supported, and everything it
   produces is marked `HYPOTHESIS`, prefixed
   `[synthetic offline placeholder - not researched]`, and flagged by
   `GET /health`. The offline search provider returns no results rather than
   plausible fake URLs.

2. **Real market data sources.** Google Play / App Store scraping and Google
   Trends carry terms-of-service and legal constraints that a product spec
   cannot wave away. Resolution: research goes through the `SearchProvider`
   interface, V1 ships a generic web-search adapter (Tavily) and the offline
   one. No scraper is included; adding a licensed data source is one file.

3. **"Codex integration".** There is no public API for an autonomous Codex run,
   and §16 puts full automation out of V1 scope. Resolution:
   `CodeGenerationProvider` exists with an export implementation — the operator
   downloads the prompt and runs it in their agent of choice. This also keeps
   the mandated human gate in front of code generation.

4. **Revenue, ROI, conversion and success rate on the dashboard.** These need
   post-launch data that V1 does not collect. Resolution: `App` and `AppMetric`
   exist in the schema, the endpoint returns `null` (not `0`) until data is
   there, and the UI renders "no post-launch data collected yet". Approval rate,
   which V1 *can* measure, is shown instead.

5. **LangGraph.** Allowed but not required by the spec. Resolution: a typed
   orchestrator with the same semantics (see `docs/ARCHITECTURE.md` §3).

6. **Opportunity generation.** The spec lists Problem Discovery and then agents
   that evaluate "each opportunity", without naming what turns a problem into
   one. Resolution: an explicit `opportunity-synthesis` agent, so each agent
   keeps a single responsibility and the downstream agents correlate on
   `candidateId`.

## Known limitations

- The in-process queue is not durable. Without `REDIS_URL`, a restart mid-run
  leaves the run `PENDING`; it is visible in the UI and can be re-run.
- The Anthropic backend is covered by unit tests against an injected fake
  client, which pin the request shape, the refusal and truncation paths and the
  cache-aware cost. It has **not** been run against the live API — that needs a
  key. The OpenAI path has the same gap for the same reason; CI exercises the
  full pipeline on the offline provider.
- Cost figures are estimates from a static price table (`providers/llm/pricing.ts`),
  fine for budget enforcement, not for billing. Unknown models are priced
  pessimistically so they can never look free.
- Scoring weights are configurable in code (`setActiveWeights`) but not yet
  editable through the API.
- The API process is also the worker. Separating them is a compose change plus
  an entry point, not a redesign.
- Roles exist (`ADMIN`/`ANALYST`/`VIEWER`) but authorisation is currently
  ownership-based only; per-role route restrictions are not implemented.

## Next increments

1. **Post-launch loop** — App and AppMetric ingestion, the Post Launch Analytics
   and Decision agents, and the `KEEP` / `IMPROVE` / `SCALE` / `KILL`
   recommendation with its human gate. The schema and decision transitions are
   already in place.
2. **QA agent** — review the generated repository against the PRD's acceptance
   criteria before release.
3. **Scoring weights in the UI** — expose `setActiveWeights` with versioned
   weight sets; `OpportunityScore.weightsVersion` already records which set
   produced a score.
4. **Role-based authorisation** — enforce `VIEWER` read-only and restrict agent
   toggling to `ADMIN`.
5. **Separate worker process** — one container for HTTP, one for BullMQ.
6. **Additional research providers** — a licensed app-store data source would
   raise the evidence quality of the competitor and review agents more than any
   prompt change.
