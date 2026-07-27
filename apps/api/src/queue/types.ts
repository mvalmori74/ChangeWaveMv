export type JobHandler<T> = (payload: T) => Promise<void>;

/**
 * Minimal job queue contract.
 *
 * Research runs are long and must not block an HTTP request, but requiring
 * Redis to try the platform locally would be hostile — so the interface has two
 * implementations and the rest of the code never knows which one it has.
 */
export interface JobQueue {
  readonly name: string;
  enqueue<T>(jobName: string, payload: T): Promise<void>;
  register<T>(jobName: string, handler: JobHandler<T>): void;
  close(): Promise<void>;
}
