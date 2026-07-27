import type { Blackboard } from './types.js';

/** In-memory blackboard, snapshotted to `research_runs.state` after each agent. */
export class InMemoryBlackboard implements Blackboard {
  private readonly values: Map<string, unknown>;

  constructor(initial: Record<string, unknown> = {}) {
    this.values = new Map(Object.entries(initial));
  }

  get<T = unknown>(agentKey: string): T | undefined {
    return this.values.get(agentKey) as T | undefined;
  }

  set(agentKey: string, value: unknown): void {
    this.values.set(agentKey, value);
  }

  snapshot(): Record<string, unknown> {
    return Object.fromEntries(this.values);
  }
}
