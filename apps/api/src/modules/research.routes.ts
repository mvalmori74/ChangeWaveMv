import { createResearchProjectSchema } from '@aiaf/shared';
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { Container } from '../core/container.js';
import { NotFoundError } from '../core/errors.js';

const idParams = z.object({ id: z.string().min(1) });

export async function registerResearchRoutes(
  app: FastifyInstance,
  container: Container,
): Promise<void> {
  const { research } = container.services;

  app.post(
    '/api/research/projects',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['research'], summary: 'Create a research project.' },
    },
    async (request, reply) => {
      const body = createResearchProjectSchema.parse(request.body);
      const project = await research.createProject(request.currentUser.id, body);
      return reply.status(201).send(project);
    },
  );

  app.get(
    '/api/research/projects',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['research'], summary: 'List the caller research projects.' },
    },
    async (request) => research.listProjects(request.currentUser.id),
  );

  app.get(
    '/api/research/projects/:id',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['research'], summary: 'Project detail with its runs.' },
    },
    async (request) => {
      const { id } = idParams.parse(request.params);
      return research.getProject(id, request.currentUser.id);
    },
  );

  app.post(
    '/api/research/projects/:id/runs',
    {
      preHandler: [app.authenticate],
      schema: {
        tags: ['research'],
        summary: 'Queue a research run. Returns immediately; poll the run for progress.',
      },
    },
    async (request, reply) => {
      const { id } = idParams.parse(request.params);
      const run = await research.startRun(id, request.currentUser.id);
      return reply.status(202).send(run);
    },
  );

  app.get(
    '/api/research/runs/:id',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['research'], summary: 'Run status with per-agent executions.' },
    },
    async (request) => {
      const { id } = idParams.parse(request.params);
      const run = await research.getRun(id);
      // A run is only reachable through a project the caller owns; anything
      // else is answered as missing rather than forbidden.
      if (run.project.ownerId !== request.currentUser.id) {
        throw new NotFoundError('ResearchRun', id);
      }
      return run;
    },
  );
}
