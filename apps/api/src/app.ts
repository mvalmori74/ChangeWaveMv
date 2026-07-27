import cors from '@fastify/cors';
import swagger from '@fastify/swagger';
import swaggerUi from '@fastify/swagger-ui';
import Fastify, { type FastifyBaseLogger, type FastifyInstance } from 'fastify';
import type { AppConfig } from './config/env.js';
import type { Container } from './core/container.js';
import { authPlugin } from './http/auth.plugin.js';
import { registerErrorHandler } from './http/errors.js';
import { registerAgentRoutes } from './modules/agent.routes.js';
import { registerAnalyticsRoutes } from './modules/analytics.routes.js';
import { registerAuthRoutes } from './modules/auth.routes.js';
import { registerDeliveryRoutes } from './modules/delivery.routes.js';
import { registerOpportunityRoutes } from './modules/opportunity.routes.js';
import { registerResearchRoutes } from './modules/research.routes.js';

export async function buildApp(
  config: AppConfig,
  container: Container,
): Promise<FastifyInstance> {
  const app = Fastify({
    // Widened to Fastify's own logger type so the instance keeps the default
    // generic parameters and route modules can take a plain FastifyInstance.
    loggerInstance: container.logger as FastifyBaseLogger,
    disableRequestLogging: config.NODE_ENV === 'test',
    trustProxy: true,
    bodyLimit: 1_048_576,
  });

  registerErrorHandler(app);

  await app.register(cors, {
    origin: config.corsOrigins.length > 0 ? config.corsOrigins : false,
    credentials: true,
  });
  await app.register(authPlugin, { config });

  // The signer only exists once @fastify/jwt is registered.
  container.setTokenSigner((payload) => app.jwt.sign(payload));

  await app.register(swagger, {
    openapi: {
      info: {
        title: 'AI App Factory API',
        description:
          'Multi-agent platform for discovering, scoring and specifying app opportunities.',
        version: '1.0.0',
      },
      components: {
        securitySchemes: {
          bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' },
        },
      },
      security: [{ bearerAuth: [] }],
    },
  });
  await app.register(swaggerUi, { routePrefix: '/docs' });

  app.get(
    '/health',
    { schema: { tags: ['system'], summary: 'Liveness and provider configuration.' } },
    async () => ({
      status: 'ok',
      environment: config.NODE_ENV,
      providers: {
        llm: container.llm.name,
        search: container.search.name,
        codegen: container.codegen.name,
        queue: container.queue.name,
      },
      // Surfaced so nobody mistakes an offline demo run for real research.
      synthetic: container.llm.name === 'mock' || container.search.synthetic,
    }),
  );

  app.get(
    '/health/ready',
    { schema: { tags: ['system'], summary: 'Readiness: verifies the database connection.' } },
    async (_request, reply) => {
      try {
        await container.prisma.$queryRaw`SELECT 1`;
        return { status: 'ready' };
      } catch {
        return reply.status(503).send({ status: 'unavailable', database: 'unreachable' });
      }
    },
  );

  await registerAuthRoutes(app, container);
  await registerResearchRoutes(app, container);
  await registerOpportunityRoutes(app, container);
  await registerDeliveryRoutes(app, container);
  await registerAgentRoutes(app, container);
  await registerAnalyticsRoutes(app, container);

  return app;
}
