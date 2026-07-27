import type { Prisma, PrismaClient } from '@prisma/client';
import type { AgentRegistry } from '../agents/registry.js';
import { toErrorMessage } from '../core/errors.js';
import type { AppLogger } from '../core/logger.js';

/**
 * Projects the in-code registry into the `agents` table.
 *
 * Code is the source of truth for what an agent *is*; the table exists so
 * executions can reference an agent row and so operators can see the fleet.
 * The one operator-owned field is `enabled`, which is therefore read back from
 * the database instead of being overwritten on boot.
 */
export async function syncAgentRegistry(
  prisma: PrismaClient,
  registry: AgentRegistry,
  logger: AppLogger,
): Promise<void> {
  for (const meta of registry.listMeta()) {
    try {
      const existing = await prisma.agent.findUnique({
        where: { key: meta.key },
        select: { enabled: true },
      });

      await prisma.agent.upsert({
        where: { key: meta.key },
        create: {
          key: meta.key,
          name: meta.name,
          description: meta.description,
          version: meta.version,
          role: meta.role,
          inputSchema: meta.inputSchema as Prisma.InputJsonValue,
          outputSchema: meta.outputSchema as Prisma.InputJsonValue,
          systemPrompt: meta.systemPrompt,
          tools: meta.tools,
          dependencies: meta.dependencies,
          enabled: meta.enabled,
          priority: meta.priority,
          modelTier: meta.modelTier,
        },
        update: {
          name: meta.name,
          description: meta.description,
          version: meta.version,
          role: meta.role,
          inputSchema: meta.inputSchema as Prisma.InputJsonValue,
          outputSchema: meta.outputSchema as Prisma.InputJsonValue,
          systemPrompt: meta.systemPrompt,
          tools: meta.tools,
          dependencies: meta.dependencies,
          priority: meta.priority,
          modelTier: meta.modelTier,
        },
      });

      // An agent an operator disabled stays disabled across restarts.
      if (existing && existing.enabled !== meta.enabled) {
        registry.setEnabled(meta.key, existing.enabled);
      }
    } catch (error) {
      logger.error(
        { agentKey: meta.key, error: toErrorMessage(error) },
        'failed to sync agent definition',
      );
    }
  }

  logger.info({ agents: registry.list().length }, 'agent registry synced');
}
