import type { AgentDefinitionMeta } from '@aiaf/shared';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { ConflictError, NotFoundError, ValidationError } from '../core/errors.js';
import { toAgentMeta, type AgentDefinition } from './types.js';

/**
 * The Agent Registry.
 *
 * Agents are registered as data and resolved into an execution order from their
 * declared `dependencies`. Adding an agent means adding a definition file and
 * registering it — the orchestrator has no knowledge of any specific agent.
 */
export class AgentRegistry {
  private readonly definitions = new Map<string, AgentDefinition>();

  register(definition: AgentDefinition): void {
    if (this.definitions.has(definition.key)) {
      throw new ConflictError(`Agent '${definition.key}' is already registered`);
    }
    if (!/^[a-z0-9-]+$/.test(definition.key)) {
      throw new ValidationError(
        `Agent key '${definition.key}' must be lower-case kebab-case`,
      );
    }
    this.definitions.set(definition.key, definition);
  }

  registerAll(definitions: readonly AgentDefinition[]): void {
    for (const definition of definitions) this.register(definition);
  }

  has(key: string): boolean {
    return this.definitions.has(key);
  }

  get(key: string): AgentDefinition {
    const definition = this.definitions.get(key);
    if (!definition) throw new NotFoundError('Agent', key);
    return definition;
  }

  list(): AgentDefinition[] {
    return [...this.definitions.values()].sort((a, b) => a.priority - b.priority);
  }

  listMeta(): AgentDefinitionMeta[] {
    return this.list().map((definition) => this.describe(definition.key));
  }

  describe(key: string): AgentDefinitionMeta {
    const definition = this.get(key);
    return toAgentMeta(
      definition,
      zodToJsonSchema(definition.inputSchema, { $refStrategy: 'none' }) as Record<string, unknown>,
      zodToJsonSchema(definition.outputSchema, { $refStrategy: 'none' }) as Record<string, unknown>,
    );
  }

  setEnabled(key: string, enabled: boolean): AgentDefinition {
    const definition = this.get(key);
    definition.enabled = enabled;
    return definition;
  }

  /**
   * Topological order for the requested agents.
   *
   * Dependencies are pulled in transitively, disabled agents are dropped along
   * with everything that depends on them, and cycles are rejected loudly rather
   * than deadlocking a run. Ties are broken by `priority` then key so the order
   * is deterministic across runs.
   */
  resolveExecutionOrder(requestedKeys?: readonly string[]): AgentDefinition[] {
    const roots = requestedKeys ?? this.list().map((definition) => definition.key);

    const wanted = new Set<string>();
    const collect = (key: string, trail: string[]): void => {
      if (trail.includes(key)) {
        throw new ValidationError(
          `Circular agent dependency: ${[...trail, key].join(' -> ')}`,
        );
      }
      if (wanted.has(key)) return;
      const definition = this.get(key);
      wanted.add(key);
      for (const dependency of definition.dependencies) {
        collect(dependency, [...trail, key]);
      }
    };
    for (const key of roots) collect(key, []);

    const enabled = [...wanted].filter((key) => this.get(key).enabled);
    const enabledSet = new Set(enabled);
    // An agent whose dependency was disabled cannot run either; drop it
    // transitively instead of feeding it a hole in the blackboard.
    let changed = true;
    while (changed) {
      changed = false;
      for (const key of [...enabledSet]) {
        const unmet = this.get(key).dependencies.filter((dep) => !enabledSet.has(dep));
        if (unmet.length > 0) {
          enabledSet.delete(key);
          changed = true;
        }
      }
    }

    const ordered: AgentDefinition[] = [];
    const done = new Set<string>();
    const remaining = [...enabledSet]
      .map((key) => this.get(key))
      .sort((a, b) => a.priority - b.priority || a.key.localeCompare(b.key));

    while (remaining.length > 0) {
      const index = remaining.findIndex((definition) =>
        definition.dependencies.every((dependency) => done.has(dependency)),
      );
      if (index === -1) {
        throw new ValidationError(
          `Unresolvable agent dependency graph for: ${remaining.map((d) => d.key).join(', ')}`,
        );
      }
      const [next] = remaining.splice(index, 1);
      ordered.push(next!);
      done.add(next!.key);
    }

    return ordered;
  }

  clear(): void {
    this.definitions.clear();
  }
}
