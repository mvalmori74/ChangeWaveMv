import { PrismaClient } from '@prisma/client';

export type Database = PrismaClient;

let client: PrismaClient | undefined;

/**
 * A single PrismaClient per process. `tsx watch` reloads the module graph on
 * every save, so the instance is cached on globalThis to avoid exhausting the
 * connection pool during development.
 */
export function getPrismaClient(databaseUrl?: string): PrismaClient {
  const globalRef = globalThis as typeof globalThis & { __aiafPrisma?: PrismaClient };
  if (globalRef.__aiafPrisma) return globalRef.__aiafPrisma;
  if (client) return client;

  client = new PrismaClient({
    ...(databaseUrl ? { datasources: { db: { url: databaseUrl } } } : {}),
    log: [{ emit: 'stdout', level: 'warn' }, { emit: 'stdout', level: 'error' }],
  });

  if (process.env.NODE_ENV !== 'production') {
    globalRef.__aiafPrisma = client;
  }
  return client;
}

export async function disconnectPrisma(): Promise<void> {
  const globalRef = globalThis as typeof globalThis & { __aiafPrisma?: PrismaClient };
  await client?.$disconnect();
  client = undefined;
  globalRef.__aiafPrisma = undefined;
}
