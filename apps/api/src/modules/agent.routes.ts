import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { Container } from '../core/container.js';

const keyParams = z.object({ key: z.string().min(1) });
const enabledBody = z.object({ enabled: z.boolean() });

/** Read and toggle the agent registry. */
export async function registerAgentRoutes(
  app: FastifyInstance,
  container: Container,
): Promise<void> {
  app.get(
    '/api/agents',
    {
      preHandler: [app.authenticate],
      schema: { tags: ['agents'], summary: 'List registered agents and their contracts.' },
    },
    async () => container.registry.listMeta(),
  );

  app.get(
    '/api/agents/execution-order',
    {
      preHandler: [app.authenticate],
      schema: {
        tags: ['agents'],
        summary: 'Resolved topological run order for the current registry.',
      },
    },
    async () => container.registry.resolveExecutionOrder().map((agent) => agent.key),
  );

  app.get(
    '/api/agents/:key',
    { preHandler: [app.authenticate], schema: { tags: ['agents'], summary: 'Agent detail.' } },
    async (request) => {
      const { key } = keyParams.parse(request.params);
      return container.registry.describe(key);
    },
  );

  app.patch(
    '/api/agents/:key',
    {
      preHandler: [app.authenticate],
      schema: {
        tags: ['agents'],
        summary: 'Enable or disable an agent for subsequent runs.',
      },
    },
    async (request) => {
      const { key } = keyParams.parse(request.params);
      const { enabled } = enabledBody.parse(request.body);
      container.registry.setEnabled(key, enabled);
      await container.prisma.agent.updateMany({ where: { key }, data: { enabled } });
      await container.services.audit.record({
        actorId: request.currentUser.id,
        action: enabled ? 'agent.enabled' : 'agent.disabled',
        entityType: 'Agent',
        entityId: key,
      });
      return container.registry.describe(key);
    },
  );
}
