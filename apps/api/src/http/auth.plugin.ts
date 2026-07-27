import fastifyJwt from '@fastify/jwt';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import fp from 'fastify-plugin';
import type { AppConfig } from '../config/env.js';
import { UnauthorizedError } from '../core/errors.js';

export interface AuthenticatedUser {
  id: string;
  email: string;
  role: string;
}

export interface JwtPayload {
  sub: string;
  email: string;
  role: string;
}

declare module 'fastify' {
  interface FastifyInstance {
    authenticate(request: FastifyRequest): Promise<void>;
  }
  interface FastifyRequest {
    currentUser: AuthenticatedUser;
  }
}

/** JWT verification exposed as a route-level `preHandler`. */
export const authPlugin = fp(async (app: FastifyInstance, options: { config: AppConfig }) => {
  await app.register(fastifyJwt, {
    secret: options.config.JWT_SECRET,
    sign: { expiresIn: options.config.JWT_EXPIRES_IN },
  });

  // Declared up front so every request object has the same shape; the value is
  // only meaningful after `authenticate` has run.
  app.decorateRequest('currentUser', null as unknown as AuthenticatedUser);

  app.decorate('authenticate', async (request: FastifyRequest) => {
    try {
      const payload = (await request.jwtVerify()) as JwtPayload;
      request.currentUser = { id: payload.sub, email: payload.email, role: payload.role };
    } catch {
      throw new UnauthorizedError('Invalid or expired token');
    }
  });
});
