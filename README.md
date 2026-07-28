# AI App Factory

A multi-agent platform that turns a sentence like *"find opportunities for new
Android apps in automotive maintenance"* into researched, scored, and
specified product opportunities — ending in a PRD and a build-ready prompt for a
code generation agent.

It is not a chatbot. It is a workflow system: specialised agents, persisted
state, a deterministic scoring engine, an evidence trail, cost control, and
human approval gates in front of every irreversible step.

```
USER → WEB UI → API → ORCHESTRATOR ─┬─ Trend Hunter
                                    ├─ Market Research
                                    ├─ Competitor Analysis
                                    ├─ Review Analysis
                                    ├─ Problem Discovery
                                    ├─ Opportunity Synthesis
                                    ├─ Monetization
                                    ├─ Technical Feasibility
                                    ├─ Opportunity Scoring
                                    ├─ PRD Generator        (on demand)
                                    └─ Prompt Engineer      (on demand)
```

## Quick start

### With Docker

```bash
cp .env.example .env         # set JWT_SECRET (32+ chars); keys are optional
docker compose up            # api :3000, web :8080, postgres :5432, redis :6379
```

Open <http://localhost:8080>, create the first account (it becomes `ADMIN`),
create a research project, and press **Start research run**.

### Without Docker

```bash
npm install                  # also generates the Prisma client and builds @aiaf/shared
cp .env.example .env         # point DATABASE_URL at a local PostgreSQL 16
npm run db:migrate           # apply migrations
npm run db:seed              # optional: an admin account and a demo project
npm run dev                  # api :3000 + web :5173
```

Other commands:

```bash
npm test                     # unit tests (no database or network needed)
npm run build                # build shared, api and web
npm run typecheck            # strict type check across all workspaces
npm run test:e2e             # Playwright smoke tests against a running stack
```

`npm test` needs no database, Redis or network. The HTTP integration suite runs
only when you point it at a disposable, migrated database:

```bash
DATABASE_TEST_URL=postgresql://aiaf:aiaf@localhost:5432/aiaf_test npm test
```

### Running with no API key

The default configuration (`LLM_PROVIDER=mock`, `SEARCH_PROVIDER=mock`) runs the
entire pipeline offline. That mode exists so the platform can be installed,
tested and demonstrated without spending anything — **its output is synthetic**.
Every offline record is tagged `HYPOTHESIS`, carries a
`[synthetic offline placeholder - not researched]` marker, and `GET /health`
reports `"synthetic": true`. To do real research, pick an LLM vendor and a
search vendor:

```env
# either
LLM_PROVIDER=openai
OPENAI_API_KEY=sk-…

# or
LLM_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-…

SEARCH_PROVIDER=tavily
TAVILY_API_KEY=tvly-…
```

### Choosing between OpenAI and Claude

Both are first-class: the agents, prompts, schemas and scoring are identical,
so the same research brief can be run through either and the results compared.
Nothing but `.env` changes.

`LLM_MODEL_FAST` / `_BALANCED` / `_DEEP` map the three task tiers agents declare
onto model ids. Left empty they resolve to the active provider's defaults
(`apps/api/src/providers/llm/model-router.ts`):

| Tier | `openai` | `anthropic` |
|---|---|---|
| `FAST` — mechanical extraction | `gpt-4o-mini` | `claude-haiku-4-5` |
| `BALANCED` — research and analysis | `gpt-4o` | `claude-sonnet-5` |
| `DEEP` — synthesis, scoring, PRD | `gpt-4o` | `claude-opus-5` |

Two behaviours are specific to the Claude backend:

- **Reasoning effort follows the tier**, not just the model, so a `DEEP` agent
  thinks harder than a `FAST` one even on the same model.
- **`ANTHROPIC_PROMPT_CACHE`** (default off) caches the agent system prompts. A
  cache write costs more than ordinary input tokens and a read costs a fraction,
  so it pays off when you re-research the same project inside the cache lifetime
  and costs slightly more when you do not. Cached tokens are priced correctly
  either way, so the run budget stays accurate.
- **`ANTHROPIC_FALLBACKS`** (default on) lets a request that safety classifiers
  decline be re-run server-side on a recommended model instead of failing the
  agent. Turn it off to see the refusal itself; the error then names the policy
  category and Anthropic's explanation.

Cost estimates come from a static table (`providers/llm/pricing.ts`) and are used
for budget enforcement, not billing. An unknown model id is priced
pessimistically, never free.

## Continuous integration

Every push to the feature branch runs
[`.github/workflows/ai-app-factory.yml`](.github/workflows/ai-app-factory.yml):
type check, build, unit tests, integration tests against a real PostgreSQL,
Playwright smoke tests, then both Docker images and a full `docker compose` stack
driven through a complete research run. The built frontend bundle and the Docker
images are published as artifacts on the workflow run page.

## What it does

1. **Research.** Trend, market, competitor and review agents gather evidence.
   Every URL they read is stored as a `Source`.
2. **Synthesis.** Problems are extracted, then turned into candidate products.
3. **Evaluation.** Monetization and technical feasibility are assessed per
   candidate; the scoring agent rates nine criteria with a rationale, evidence
   and confidence for each.
4. **Scoring.** The platform — not the model — computes the weighted final score
   and its classification (`KILL` → `EXCEPTIONAL`).
5. **Decision.** A human approves or rejects. The AI recommendation and the
   human decision are stored side by side.
6. **Delivery.** An approved opportunity gets a 23-section PRD and, from it, a
   sectioned build prompt. Both export as Markdown.

## Rules the platform enforces

These are the reason it is worth running rather than asking a model directly:

- **No invented data.** Agents may only cite URLs that were actually retrieved.
  With no search backend, nothing can be marked `VERIFIED`.
- **Evidence is typed.** Every claim is `VERIFIED`, `INFERENCE`, `ESTIMATE` or
  `HYPOTHESIS`, and the UI shows which.
- **No false precision.** Money and effort are ranges with a stated basis and a
  confidence, or `null`.
- **Scoring is deterministic.** The model supplies per-criterion scores; the
  weighting is plain code, so the same inputs always give the same result.
- **Budgets are hard.** Every model call is checked against the run's USD and
  token ceiling *before* it is made. There are no unbounded loops.
- **Humans decide.** No PRD without approval, no prompt without a PRD, no
  autonomous code generation at all in V1.

## Repository layout

```
packages/shared      Types, enums, DTO schemas and the scoring maths
apps/api             Fastify API, agents, providers, persistence
  prisma/            Schema and migrations
  src/agents/        Registry, execution engine, orchestrator, definitions/
  src/providers/     LLM, search and code-generation abstractions
  src/scoring/       Weighted, explainable scoring engine
  src/modules/       HTTP routes
apps/web             React + Vite + MUI dashboard
docs/                Architecture, database, agents, API and plan
```

## Documentation

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Components, request and run flow, design decisions |
| [docs/AGENTS.md](docs/AGENTS.md) | Every agent, its contract, and how to add one |
| [docs/DATABASE.md](docs/DATABASE.md) | Entities and relationships |
| [docs/API.md](docs/API.md) | Endpoint reference (live OpenAPI at `/docs`) |
| [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) | Milestones, status, what is deliberately not built yet |

## Configuration

All settings are environment variables, validated at boot — see
[.env.example](.env.example) for the annotated list. Secrets never go in the
repository.

## Not in V1

Deliberately out of scope, per the product spec: automatic Google Play
publishing, automatic deployment, marketing automation, payments, advanced
multi-tenancy, and autonomous code generation. The schema and the provider
interfaces leave room for each; see the implementation plan.

## Licence

Proprietary — all rights reserved.
