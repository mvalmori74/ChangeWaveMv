import type { LlmProvider } from '../providers/llm/types.js';
import { MockLlmProvider } from '../providers/llm/mock.provider.js';
import { BUILT_IN_AGENTS } from './definitions/index.js';
import { AgentRegistry } from './registry.js';
import { schemaNameFor } from './runner.js';
import type { AgentDefinition } from './types.js';

/**
 * Builds the registry and, when running offline, wires each agent's fixture
 * into the mock provider under the same schema name the runner will request.
 */
export function buildAgentRegistry(
  llm: LlmProvider,
  definitions: readonly AgentDefinition[] = BUILT_IN_AGENTS,
): AgentRegistry {
  const registry = new AgentRegistry();
  registry.registerAll(definitions);

  if (llm instanceof MockLlmProvider) {
    for (const definition of definitions) {
      if (definition.mockFixture) {
        llm.register(schemaNameFor(definition.key), definition.mockFixture);
      }
    }
  }

  return registry;
}
