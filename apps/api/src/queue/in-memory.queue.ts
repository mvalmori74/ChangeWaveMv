import { toErrorMessage } from '../core/errors.js';
import type { AppLogger } from '../core/logger.js';
import type { JobHandler, JobQueue } from './types.js';

/**
 * In-process queue used when no Redis is configured.
 *
 * Jobs run sequentially on the API process. That is fine for a single-operator
 * V1 and keeps `npm run dev` dependency-free; it is not durable, so a restart
 * loses queued work — the run row stays PENDING and can be restarted from the
 * UI rather than silently disappearing.
 */
export class InMemoryQueue implements JobQueue {
  readonly name = 'in-memory';
  private readonly handlers = new Map<string, JobHandler<unknown>>();
  private chain: Promise<void> = Promise.resolve();
  private closed = false;

  constructor(private readonly logger: AppLogger) {}

  register<T>(jobName: string, handler: JobHandler<T>): void {
    this.handlers.set(jobName, handler as JobHandler<unknown>);
  }

  async enqueue<T>(jobName: string, payload: T): Promise<void> {
    if (this.closed) throw new Error('Queue is closed');
    const handler = this.handlers.get(jobName);
    if (!handler) throw new Error(`No handler registered for job '${jobName}'`);

    this.chain = this.chain.then(async () => {
      try {
        await handler(payload);
      } catch (error) {
        this.logger.error({ jobName, error: toErrorMessage(error) }, 'job failed');
      }
    });
  }

  /** Test helper: resolves once every queued job has settled. */
  async drain(): Promise<void> {
    await this.chain;
  }

  async close(): Promise<void> {
    this.closed = true;
    await this.chain;
  }
}
