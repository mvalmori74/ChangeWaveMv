# Database

PostgreSQL 16 via Prisma. Schema: `apps/api/prisma/schema.prisma`.
Initial migration: `apps/api/prisma/migrations/20260727000000_init`.

```bash
npm run db:migrate       # apply migrations (prisma migrate deploy)
npm run db:generate      # regenerate the client
npm run db:seed          # admin account + demo project
npm run db:reset         # drop, re-migrate, re-seed (destructive)
```

## Entity map

```
User ─┬─ ResearchProject ─┬─ ResearchRun ─┬─ AgentExecution ─▶ Agent
      │                   │               ├─ Source ──╌╌ Claim
      │                   │               └─ Opportunity
      │                   ├─ Market ─── Competitor ─── ReviewInsight
      │                   ├─ PainPoint
      │                   └─ Opportunity ─┬─ OpportunityScore
      │                                   ├─ PRD ─── Prompt ─── CodeGenerationProject ─── App ─── AppMetric
      │                                   └─ Decision
      └─ AuditLog
```

## Tables

| Model | Table | Purpose |
|---|---|---|
| `User` | `users` | Accounts. The first one registered becomes `ADMIN`. |
| `Agent` | `agents` | Registry projection. Code is the source of truth; `enabled` is operator-owned and survives restarts. |
| `AgentExecution` | `agent_executions` | One row per attempt: input, output, model, tokens, cost, duration, error. |
| `ResearchProject` | `research_projects` | The brief: sector, country, platform, timeframe, budget ceilings. |
| `ResearchRun` | `research_runs` | One execution of the pipeline. `state` is the blackboard snapshot; `totalCostUsd` / `totalTokens` the spend. |
| `Source` | `sources` | A URL an agent actually read. Unique per `(runId, url)`. |
| `Claim` | `claims` | A statement plus its evidence type, confidence and supporting sources. |
| `Market` | `markets` | Size range, demand level, growth, seasonality, segments. |
| `Competitor` | `competitors` | Direct and indirect competitors. Rating/review count are nullable on purpose. |
| `ReviewInsight` | `review_insights` | Classified user feedback (pain point, feature request, bug, pricing, UX, ads…). |
| `PainPoint` | `pain_points` | Problem, target user, frequency, severity, current solution, market gap. |
| `Opportunity` | `opportunities` | The central entity: problem, solution, the five headline scores, status. |
| `OpportunityScore` | `opportunity_scores` | One row per criterion — this is what makes a score explainable. |
| `PRD` | `prds` | Structured content plus the rendered Markdown; versioned per opportunity. |
| `Prompt` | `prompts` | Build prompt sections plus rendered Markdown; versioned per opportunity and type. |
| `CodeGenerationProject` | `code_generation_projects` | Repository, branch, status, build and test status. |
| `App` | `apps` | A released application. |
| `AppMetric` | `app_metrics` | Daily installs, DAU/MAU, retention, revenue, cost, rating, crash rate. |
| `Decision` | `decisions` | AI recommendation **and** human decision, side by side, never merged. |
| `AuditLog` | `audit_logs` | Append-only trail: actor, action, entity, before, after. |

## Enums

`UserRole`, `Platform`, `ResearchProjectStatus`, `ResearchRunStatus`,
`AgentExecutionStatus`, `SourceType`, `EvidenceType`, `ReviewInsightType`,
`OpportunityStatus`, `CodeGenerationStatus`, `PromptType`, `DecisionType`,
`ActorType`, `AppStatus`, `ModelTier`.

They are mirrored in `packages/shared/src/enums.ts` so the web client can use
the same values without importing Prisma.

## Conventions

- **Money** uses `Decimal`: `Decimal(12,6)` for LLM cost (fractions of a cent
  matter when counting agent calls), `Decimal(12,2)` for revenue.
- **Nullable means unknown.** A competitor with `rating = null` was found
  without a rating; it does not mean zero. The same distinction drives the
  dashboard, where revenue is `null` until post-launch data exists.
- **`evidenceType` travels with the data**, from the agent output through to the
  table, so the UI can label a stored claim as verified, inferred, estimated or
  hypothetical.
- **Cascades** follow ownership: deleting a project deletes its runs,
  opportunities and research artefacts. Optional references (`agentId`,
  `marketId`, `decidedById`) are `SetNull` so history survives.
- **Indexes** cover the query patterns the dashboard actually uses:
  `opportunities(status)`, `opportunities(finalScore)`, plus foreign keys.

## Opportunity status flow

```
DISCOVERED → ANALYZING → SCORED ──approve──▶ APPROVED
                            │                   │
                            └────reject────▶ REJECTED
                                                ▼
                                          PRD_GENERATED
                                                ▼
                                     CODE_PROMPT_GENERATED
                                                ▼
                              DEVELOPMENT → RELEASED → MONITORING
                                                     ├──▶ SCALED
                                                     └──▶ KILLED
```

Transitions are enforced in `OpportunityService`: a decision that does not match
the current status is refused with `409`.
