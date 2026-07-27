# API reference

Interactive OpenAPI documentation is served at **`/docs`** by the running API
(<http://localhost:3000/docs>). This page is the summary.

Base URL: `http://localhost:3000`. All `/api/*` routes except the two auth
entry points require `Authorization: Bearer <jwt>`.

## Errors

Every failure uses the same envelope:

```json
{ "error": { "code": "CONFLICT", "message": "…", "details": { } } }
```

| Status | Code | Meaning |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Request body or query failed validation; `details` lists the fields |
| 401 | `UNAUTHORIZED` | Missing, invalid or expired token |
| 402 | `BUDGET_EXCEEDED` | The run hit its USD or token ceiling |
| 404 | `NOT_FOUND` | Missing — or belongs to another user |
| 409 | `CONFLICT` | Illegal state transition (e.g. a PRD before approval) |
| 500 | `AGENT_EXECUTION_ERROR` | An agent could not produce valid output |
| 502 | `PROVIDER_ERROR` | The LLM or search backend failed |

## System

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Liveness, active providers, and whether output is synthetic |
| `GET` | `/health/ready` | Readiness; `503` when the database is unreachable |
| `GET` | `/docs` | OpenAPI UI |

## Auth

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account. The first account becomes `ADMIN`. |
| `POST` | `/api/auth/login` | Exchange credentials for a JWT |
| `GET` | `/api/auth/me` | Current user |

```bash
curl -X POST localhost:3000/api/auth/register \
  -H 'content-type: application/json' \
  -d '{"name":"Ada","email":"ada@example.com","password":"password123"}'
```

## Research

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/research/projects` | Create a project (sector, country, platform, timeframe, budgets) |
| `GET` | `/api/research/projects` | List the caller's projects |
| `GET` | `/api/research/projects/:id` | Project with its runs |
| `POST` | `/api/research/projects/:id/runs` | Queue a run — returns `202` immediately |
| `GET` | `/api/research/runs/:id` | Run status, per-agent executions, cost and tokens |

A run is asynchronous. Poll the run endpoint: `PENDING` → `RUNNING` →
`COMPLETED` / `PARTIAL` / `FAILED`. `PARTIAL` means some agents failed or were
skipped and the rest of the output was kept.

## Opportunities

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/opportunities` | Filtered, paginated list |
| `GET` | `/api/opportunities/:id` | Detail: scores, sources, decisions, artefacts |
| `GET` | `/api/opportunities/:id/research` | Competitors, review insights, pain points, market |
| `POST` | `/api/opportunities/:id/decisions` | Record a human decision |
| `POST` | `/api/opportunities/:id/prd` | Generate the PRD (requires `APPROVED`) |
| `POST` | `/api/opportunities/:id/codex-prompt` | Generate the build prompt (requires a PRD) |

List query parameters: `projectId`, `category`, `country`, `platform`, `status`,
`minScore`, `maxScore`, `search`, `page`, `pageSize`, `sortBy`
(`finalScore` | `createdAt` | `title`), `sortDir`.

Decision body: `{ "type": "APPROVE" | "REJECT" | "KEEP" | "IMPROVE" | "SCALE" | "KILL", "rationale": "…" }`.
The rationale is mandatory and is stored next to the AI recommendation.

## Delivery

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/prds/:id` | PRD with structured content and Markdown |
| `GET` | `/api/prds/:id/export` | Download the PRD as Markdown |
| `GET` | `/api/prompts/:id` | Prompt with its sections |
| `GET` | `/api/prompts/:id/export` | Download the build prompt as Markdown |
| `POST` | `/api/prompts/:id/code-generation` | Register a code generation project `{ repository?, branch? }` |

## Agents

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/agents` | Registry: metadata, schemas, prompts, dependencies |
| `GET` | `/api/agents/execution-order` | Resolved topological run order |
| `GET` | `/api/agents/:key` | One agent |
| `PATCH` | `/api/agents/:key` | `{ "enabled": boolean }` — takes effect on the next run |

## Analytics

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/analytics/overview` | Dashboard totals, approval rate, spend |
| `GET` | `/api/analytics/pipeline` | Opportunities for the kanban board |
| `GET` | `/api/analytics/agent-costs` | Tokens, cost and duration per agent |

`revenueUsd`, `roi` and `successRate` are `null` until post-launch metrics
exist — the API does not report zero for "not measured".

## Worked example

```bash
TOKEN=$(curl -s -X POST localhost:3000/api/auth/login \
  -H 'content-type: application/json' \
  -d '{"email":"ada@example.com","password":"password123"}' | jq -r .token)

PROJECT=$(curl -s -X POST localhost:3000/api/research/projects \
  -H "authorization: Bearer $TOKEN" -H 'content-type: application/json' \
  -d '{"name":"Industrial technicians","sector":"industrial maintenance",
       "country":"Italy","platform":"ANDROID"}' | jq -r .id)

RUN=$(curl -s -X POST localhost:3000/api/research/projects/$PROJECT/runs \
  -H "authorization: Bearer $TOKEN" | jq -r .id)

curl -s localhost:3000/api/research/runs/$RUN -H "authorization: Bearer $TOKEN" | jq .status

OPP=$(curl -s "localhost:3000/api/opportunities?projectId=$PROJECT" \
  -H "authorization: Bearer $TOKEN" | jq -r '.items[0].id')

curl -s -X POST localhost:3000/api/opportunities/$OPP/decisions \
  -H "authorization: Bearer $TOKEN" -H 'content-type: application/json' \
  -d '{"type":"APPROVE","rationale":"Strong pain, weak incumbents."}'

PRD=$(curl -s -X POST localhost:3000/api/opportunities/$OPP/prd \
  -H "authorization: Bearer $TOKEN" | jq -r .id)

curl -s localhost:3000/api/prds/$PRD/export -H "authorization: Bearer $TOKEN" -o prd.md
```
