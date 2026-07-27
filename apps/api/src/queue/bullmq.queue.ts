import { Queue, Worker, type Job } from 'bullmq';
import { toErrorMessage } from '../core/errors.js';
import type { AppLogger } from '../core/logger.js';
import type { JobHandler, JobQueue } from './types.js';

const QUEUE_NAME = 'aiaf-jobs';

/** Durable queue used when REDIS_URL is configured. */
export class BullMqQueue implements JobQueue {
  readonly name = 'bullmq';
  private readonly queue: Queue;
  private readonly handlers = new Map<string, JobHandler<unknown>>();
  private worker: Worker | undefined;

  constructor(
    private readonly redisUrl: string,
    private readonly logger: AppLogger,
  ) {
    this.queue = new Queue(QUEUE_NAME, { connection: { url: this.redisUrl } });
  }

  register<T>(jobName: string, handler: JobHandler<T>): void {
    this.handlers.set(jobName, handler as JobHandler<unknown>);
    this.ensureWorker();
  }

  async enqueue<T>(jobName: string, payload: T): Promise<void> {
    await this.queue.add(jobName, payload as object, {
      removeOnComplete: 100,
      removeOnFail: 500,
      // Research runs are expensive and not idempotent: one automatic retry at
      // most, and never an unbounded backoff loop.
      attempts: 1,
    });
  }

  private ensureWorker(): void {
    if (this.worker) return;
    this.worker = new Worker(
      QUEUE_NAME,
      async (job: Job) => {
        const handler = this.handlers.get(job.name);
        if (!handler) {
          this.logger.warn({ jobName: job.name }, 'received job with no registered handler');
          return;
        }
        await handler(job.data);
      },
      { connection: { url: this.redisUrl }, concurrency: 2 },
    );

    this.worker.on('failed', (job, error) => {
      this.logger.error(
        { jobName: job?.name, error: toErrorMessage(error) },
        'job failed',
      );
    });
  }

  async close(): Promise<void> {
    await this.worker?.close();
    await this.queue.close();
  }
}
