import { loginRequestSchema, registerRequestSchema } from '@aiaf/shared';
import type { FastifyInstance } from 'fastify';
import type { Container } from '../core/container.js';

export async function registerAuthRoutes(
  app: FastifyInstance,
  container: Container,
): Promise<void> {
  app.post(
    '/api/auth/register',
    {
      schema: {
        tags: ['auth'],
        summary: 'Create an account. The first account created becomes ADMIN.',
      },
    },
    async (request, reply) => {
      const body = registerRequestSchema.parse(request.body);
      const result = await container.services.auth.register(body);
      return reply.status(201).send(result);
    },
  );

  app.post(
    '/api/auth/login',
    { schema: { tags: ['auth'], summary: 'Exchange credentials for a JWT.' } },
    async (request) => {
      const body = loginRequestSchema.parse(request.body);
      return container.services.auth.login(body);
    },
  );

  app.get(
    '/api/auth/me',
    { preHandler: [app.authenticate], schema: { tags: ['auth'], summary: 'Current user.' } },
    async (request) => request.currentUser,
  );
}
