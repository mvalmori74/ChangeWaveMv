import { decisionRequestSchema, opportunityFiltersSchema } from '@aiaf/shared';
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { Container } from '../core/container.js';

const idParams = z.object({ id: z.string().min(1) });

export async function registerOpportunityRoutes(
  app: FastifyInstance,
  container: Container,
): Promise<void> {
  const { opportunities, delivery } = container.services;

  app.get(
    '/api/opportunities',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['opportunities'], summary: 'Filtered, paginated opportunity list.' },
    },
    async (request) => {
      const filters = opportunityFiltersSchema.parse(request.query);
      return opportunities.list(request.currentUser.id, filters);
    },
  );

  app.get(
    '/api/opportunities/:id',
    {
      preHandler: [app.authenticate],
      schema: {
        tags: ['opportunities'],
        summary: 'Full detail: scores, sources, decisions and artefacts.',
      },
    },
    async (request) => {
      const { id } = idParams.parse(request.params);
      return opportunities.get(id, request.currentUser.id);
    },
  );

  app.get(
    '/api/opportunities/:id/research',
    {
      preHandler: [app.authenticate],
      schema: {
        tags: ['opportunities'],
        summary: 'Competitors, review insights and pain points behind this opportunity.',
      },
    },
    async (request) => {
      const { id } = idParams.parse(request.params);
      return opportunities.getResearchContext(id, request.currentUser.id);
    },
  );

  app.post(
    '/api/opportunities/:id/decisions',
    {
      preHandler: [app.authenticate],
      schema: {
        tags: ['opportunities'],
        summary: 'Record a human decision (approve, reject, kill, scale, keep, improve).',
      },
    },
    async (request) => {
      const { id } = idParams.parse(request.params);
      const body = decisionRequestSchema.parse(request.body);
      return opportunities.decide(id, request.currentUser.id, request.currentUser.id, body);
    },
  );

  app.post(
    '/api/opportunities/:id/prd',
    {
      preHandler: [app.authenticate],
      schema: {
        tags: ['delivery'],
        summary: 'Generate the PRD. Requires the opportunity to be approved.',
      },
    },
    async (request, reply) => {
      const { id } = idParams.parse(request.params);
      const prd = await delivery.generatePrd(id, request.currentUser.id, request.currentUser.id);
      return reply.status(201).send(prd);
    },
  );

  app.post(
    '/api/opportunities/:id/codex-prompt',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['delivery'], summary: 'Generate the Codex prompt from the latest PRD.' },
    },
    async (request, reply) => {
      const { id } = idParams.parse(request.params);
      const prompt = await delivery.generateCodexPrompt(
        id,
        request.currentUser.id,
        request.currentUser.id,
      );
      return reply.status(201).send(prompt);
    },
  );
}
