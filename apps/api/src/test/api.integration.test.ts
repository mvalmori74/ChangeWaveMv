import { PrismaClient } from '@prisma/client';
import type { FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { buildApp } from '../app.js';
import { loadConfig } from '../config/env.js';
import { createContainer, type Container } from '../core/container.js';
import { syncAgentRegistry } from '../persistence/agent-sync.js';
import { InMemoryQueue } from '../queue/in-memory.queue.js';
import { testLogger } from './fixtures.js';

/**
 * End-to-end HTTP test over a real database.
 *
 * It runs only when DATABASE_TEST_URL points at a migrated, disposable
 * database, so the default `npm test` needs no external service:
 *
 *   createdb aiaf_test
 *   DATABASE_URL=postgresql://…/aiaf_test npx prisma migrate deploy
 *   DATABASE_TEST_URL=postgresql://…/aiaf_test npm test
 */
const databaseUrl = process.env['DATABASE_TEST_URL'];
const suite = databaseUrl ? describe : describe.skip;

suite('API integration', () => {
  let app: FastifyInstance;
  let container: Container;
  let queue: InMemoryQueue;
  let prisma: PrismaClient;
  let token: string;

  const email = `test-${Date.now()}@example.com`;

  beforeAll(async () => {
    const config = loadConfig({
      NODE_ENV: 'test',
      LOG_LEVEL: 'silent',
      DATABASE_URL: databaseUrl,
      JWT_SECRET: 'integration-test-secret-integration-test',
      LLM_PROVIDER: 'mock',
      SEARCH_PROVIDER: 'mock',
    } as NodeJS.ProcessEnv);

    prisma = new PrismaClient({ datasources: { db: { url: databaseUrl! } } });
    queue = new InMemoryQueue(testLogger);
    container = createContainer(config, { prisma, queue, logger: testLogger });
    app = await buildApp(config, container);
    await syncAgentRegistry(prisma, container.registry, testLogger);
    await app.ready();
  }, 60_000);

  afterAll(async () => {
    await app?.close();
    await prisma?.user.deleteMany({ where: { email } });
    await prisma?.$disconnect();
  });

  const auth = () => ({ authorization: `Bearer ${token}` });

  it('registers a user and returns a token', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/register',
      payload: { name: 'Test user', email, password: 'password123' },
    });

    expect(response.statusCode).toBe(201);
    token = response.json<{ token: string }>().token;
    expect(token).toBeTruthy();
  });

  it('refuses to register the same email twice', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/register',
      payload: { name: 'Test user', email, password: 'password123' },
    });
    expect(response.statusCode).toBe(409);
  });

  it('rejects a wrong password', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/auth/login',
      payload: { email, password: 'wrong-password' },
    });
    expect(response.statusCode).toBe(401);
  });

  it('requires authentication on protected routes', async () => {
    const response = await app.inject({ method: 'GET', url: '/api/opportunities' });
    expect(response.statusCode).toBe(401);
  });

  it('validates the request body', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/research/projects',
      headers: auth(),
      payload: { name: 'x' },
    });
    expect(response.statusCode).toBe(400);
    expect(response.json<{ error: { code: string } }>().error.code).toBe('VALIDATION_ERROR');
  });

  it('runs a full research pipeline and produces scored opportunities', async () => {
    const project = await app.inject({
      method: 'POST',
      url: '/api/research/projects',
      headers: auth(),
      payload: {
        name: 'Integration project',
        sector: 'industrial maintenance',
        country: 'Italy',
        platform: 'ANDROID',
      },
    });
    expect(project.statusCode).toBe(201);
    const projectId = project.json<{ id: string }>().id;

    const run = await app.inject({
      method: 'POST',
      url: `/api/research/projects/${projectId}/runs`,
      headers: auth(),
    });
    expect(run.statusCode).toBe(202);

    // The in-process queue lets the test wait for the job deterministically.
    await queue.drain();

    const runId = run.json<{ id: string }>().id;
    const finished = await app.inject({
      method: 'GET',
      url: `/api/research/runs/${runId}`,
      headers: auth(),
    });
    const body = finished.json<{ status: string; executions: { agentKey: string }[] }>();
    expect(body.status).toBe('COMPLETED');
    expect(body.executions.map((execution) => execution.agentKey)).toContain('opportunity-scoring');
    // Delivery agents are on-demand and must not run inside a research run.
    expect(body.executions.map((execution) => execution.agentKey)).not.toContain('prd-generator');

    const list = await app.inject({
      method: 'GET',
      url: `/api/opportunities?projectId=${projectId}`,
      headers: auth(),
    });
    const opportunities = list.json<{ items: { id: string; status: string; finalScore: number }[] }>();
    expect(opportunities.items.length).toBeGreaterThan(0);
    expect(opportunities.items[0]?.status).toBe('SCORED');
    expect(opportunities.items[0]?.finalScore).toBeGreaterThanOrEqual(0);

    const opportunityId = opportunities.items[0]!.id;

    // Human-in-the-loop gate: no PRD before approval.
    const tooEarly = await app.inject({
      method: 'POST',
      url: `/api/opportunities/${opportunityId}/prd`,
      headers: auth(),
    });
    expect(tooEarly.statusCode).toBe(409);

    const approval = await app.inject({
      method: 'POST',
      url: `/api/opportunities/${opportunityId}/decisions`,
      headers: auth(),
      payload: { type: 'APPROVE', rationale: 'Integration test' },
    });
    expect(approval.statusCode).toBe(200);
    expect(approval.json<{ status: string }>().status).toBe('APPROVED');

    const prd = await app.inject({
      method: 'POST',
      url: `/api/opportunities/${opportunityId}/prd`,
      headers: auth(),
    });
    expect(prd.statusCode).toBe(201);
    const prdId = prd.json<{ id: string }>().id;

    const prompt = await app.inject({
      method: 'POST',
      url: `/api/opportunities/${opportunityId}/codex-prompt`,
      headers: auth(),
    });
    expect(prompt.statusCode).toBe(201);
    const promptId = prompt.json<{ id: string }>().id;

    const prdExport = await app.inject({
      method: 'GET',
      url: `/api/prds/${prdId}/export`,
      headers: auth(),
    });
    expect(prdExport.headers['content-type']).toContain('text/markdown');
    expect(prdExport.body).toContain('## 1. Executive Summary');

    const promptExport = await app.inject({
      method: 'GET',
      url: `/api/prompts/${promptId}/export`,
      headers: auth(),
    });
    expect(promptExport.body).toContain('## ACCEPTANCE CRITERIA');
    expect(promptExport.body).toContain('EXECUTION RULES (mandatory)');

    const overview = await app.inject({
      method: 'GET',
      url: '/api/analytics/overview',
      headers: auth(),
    });
    const stats = overview.json<{ opportunitiesAnalyzed: number; prdsGenerated: number; revenueUsd: number | null }>();
    expect(stats.opportunitiesAnalyzed).toBeGreaterThan(0);
    expect(stats.prdsGenerated).toBe(1);
    // No post-launch data collected in V1: null, not a misleading zero.
    expect(stats.revenueUsd).toBeNull();
  }, 120_000);

  it('does not leak another user runs', async () => {
    const other = await app.inject({
      method: 'POST',
      url: '/api/auth/register',
      payload: { name: 'Other', email: `other-${Date.now()}@example.com`, password: 'password123' },
    });
    const otherToken = other.json<{ token: string }>().token;

    const runs = await prisma.researchRun.findFirst({ orderBy: { createdAt: 'desc' } });
    const response = await app.inject({
      method: 'GET',
      url: `/api/research/runs/${runs?.id}`,
      headers: { authorization: `Bearer ${otherToken}` },
    });
    expect(response.statusCode).toBe(404);
  });

  it('exposes the agent registry', async () => {
    const response = await app.inject({ method: 'GET', url: '/api/agents', headers: auth() });
    const agents = response.json<{ key: string }[]>();
    expect(agents.map((agent) => agent.key)).toContain('trend-hunter');
  });
});
