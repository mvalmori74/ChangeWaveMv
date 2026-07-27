import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { Container } from '../core/container.js';

const idParams = z.object({ id: z.string().min(1) });

const codegenBody = z.object({
  repository: z.string().max(300).optional(),
  branch: z.string().max(200).optional(),
});

/** Markdown export routes: the V1 hand-off format for PRDs and prompts. */
export async function registerDeliveryRoutes(
  app: FastifyInstance,
  container: Container,
): Promise<void> {
  const { delivery } = container.services;

  app.get(
    '/api/prds/:id',
    { preHandler: [app.authenticate], schema: { tags: ['delivery'], summary: 'Fetch a PRD.' } },
    async (request) => {
      const { id } = idParams.parse(request.params);
      return delivery.getPrd(id, request.currentUser.id);
    },
  );

  app.get(
    '/api/prds/:id/export',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['delivery'], summary: 'Download the PRD as Markdown.' },
    },
    async (request, reply) => {
      const { id } = idParams.parse(request.params);
      const prd = await delivery.getPrd(id, request.currentUser.id);
      return reply
        .header('content-type', 'text/markdown; charset=utf-8')
        .header('content-disposition', `attachment; filename="${slugify(prd.title)}-prd.md"`)
        .send(prd.markdown);
    },
  );

  app.get(
    '/api/prompts/:id',
    { preHandler: [app.authenticate], schema: { tags: ['delivery'], summary: 'Fetch a prompt.' } },
    async (request) => {
      const { id } = idParams.parse(request.params);
      return delivery.getPrompt(id, request.currentUser.id);
    },
  );

  app.get(
    '/api/prompts/:id/export',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['delivery'], summary: 'Download the Codex prompt as Markdown.' },
    },
    async (request, reply) => {
      const { id } = idParams.parse(request.params);
      const prompt = await delivery.getPrompt(id, request.currentUser.id);
      return reply
        .header('content-type', 'text/markdown; charset=utf-8')
        .header(
          'content-disposition',
          `attachment; filename="${slugify(prompt.opportunity.title)}-codex-prompt.md"`,
        )
        .send(prompt.markdown);
    },
  );

  app.post(
    '/api/prompts/:id/code-generation',
    {
      preHandler: [app.authenticate],
      schema: {
        tags: ['delivery'],
        summary: 'Register a code generation project for this prompt.',
      },
    },
    async (request, reply) => {
      const { id } = idParams.parse(request.params);
      const body = codegenBody.parse(request.body ?? {});
      const result = await delivery.createCodeGenerationProject(
        id,
        request.currentUser.id,
        request.currentUser.id,
        body,
      );
      return reply.status(201).send(result);
    },
  );
}

function slugify(value: string): string {
  return (
    value
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 60) || 'document'
  );
}
