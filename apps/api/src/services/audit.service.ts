import type { Prisma, PrismaClient } from '@prisma/client';
import { toErrorMessage } from '../core/errors.js';
import type { AppLogger } from '../core/logger.js';

export interface AuditEntry {
  actorId?: string | null;
  actorType?: 'USER' | 'AGENT' | 'SYSTEM';
  action: string;
  entityType: string;
  entityId?: string | null;
  before?: unknown;
  after?: unknown;
  metadata?: Record<string, unknown>;
}

/**
 * Append-only trail of who changed what.
 *
 * Human decisions (approve, reject, kill) and agent-driven state changes are
 * both recorded, which is what makes the "AI recommendation vs human decision"
 * distinction auditable after the fact.
 */
export class AuditService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: AppLogger,
  ) {}

  async record(entry: AuditEntry): Promise<void> {
    try {
      await this.prisma.auditLog.create({
        data: {
          actorId: entry.actorId ?? null,
          actorType: entry.actorType ?? 'USER',
          action: entry.action,
          entityType: entry.entityType,
          entityId: entry.entityId ?? null,
          before: (entry.before ?? null) as Prisma.InputJsonValue,
          after: (entry.after ?? null) as Prisma.InputJsonValue,
          metadata: (entry.metadata ?? null) as Prisma.InputJsonValue,
        },
      });
    } catch (error) {
      // Never fail the caller's operation because the trail could not be
      // written; surface it loudly in the logs instead.
      this.logger.error(
        { action: entry.action, entityType: entry.entityType, error: toErrorMessage(error) },
        'failed to write audit log',
      );
    }
  }

  async list(entityType: string, entityId: string, limit = 50) {
    return this.prisma.auditLog.findMany({
      where: { entityType, entityId },
      orderBy: { createdAt: 'desc' },
      take: limit,
    });
  }
}
