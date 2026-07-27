import type { FastifyInstance } from 'fastify';
import { ZodError } from 'zod';
import { isAppError } from '../core/errors.js';

/**
 * Single place where an exception becomes an HTTP response. Internal details
 * never reach the client: unexpected errors are logged and answered with a
 * generic 500.
 */
export function registerErrorHandler(app: FastifyInstance): void {
  app.setErrorHandler((error, request, reply) => {
    if (error instanceof ZodError) {
      return reply.status(400).send({
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Request validation failed',
          details: error.issues.map((issue) => ({
            path: issue.path.join('.'),
            message: issue.message,
          })),
        },
      });
    }

    if (isAppError(error)) {
      if (error.statusCode >= 500) {
        request.log.error({ err: error, code: error.code }, 'application error');
      }
      return reply.status(error.statusCode).send({
        error: { code: error.code, message: error.message, details: error.details },
      });
    }

    // Fastify's own errors (bad JSON, unsupported media type, …) carry a
    // statusCode and a code we can safely pass through.
    const fastifyError = error as { statusCode?: number; code?: string; message?: string };
    if (typeof fastifyError.statusCode === 'number' && fastifyError.statusCode < 500) {
      return reply.status(fastifyError.statusCode).send({
        error: {
          code: fastifyError.code ?? 'BAD_REQUEST',
          message: fastifyError.message ?? 'Bad request',
        },
      });
    }

    request.log.error({ err: error }, 'unhandled error');
    return reply.status(500).send({
      error: { code: 'INTERNAL_ERROR', message: 'Internal server error' },
    });
  });

  app.setNotFoundHandler((request, reply) => {
    reply.status(404).send({
      error: { code: 'NOT_FOUND', message: `Route ${request.method} ${request.url} not found` },
    });
  });
}
