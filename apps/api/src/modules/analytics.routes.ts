import type { FastifyInstance } from 'fastify';
import type { Container } from '../core/container.js';

export async function registerAnalyticsRoutes(
  app: FastifyInstance,
  container: Container,
): Promise<void> {
  const { analytics } = container.services;

  app.get(
    '/api/analytics/overview',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['analytics'], summary: 'Dashboard headline numbers.' },
    },
    async (request) => analytics.overview(request.currentUser.id),
  );

  app.get(
    '/api/analytics/pipeline',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['analytics'], summary: 'Opportunities for the kanban board.' },
    },
    async (request) => analytics.pipeline(request.currentUser.id),
  );

  app.get(
    '/api/analytics/agent-costs',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['analytics'], summary: 'Token and cost usage per agent.' },
    },
    async (request) => analytics.agentCosts(request.currentUser.id),
  );
}
