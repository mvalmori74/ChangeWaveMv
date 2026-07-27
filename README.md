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
npm install
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
reports `"synthetic": true`. To do real research:

```env
LLM_PROVIDER=openai
OPENAI_API_KEY=sk-…
SEARCH_PROVIDER=tavily
TAVILY_API_KEY=tvly-…
```

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
