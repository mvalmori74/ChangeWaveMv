import type { AgentDefinition } from '../types.js';
import { competitorAnalysisAgent } from './competitor-analysis.agent.js';
import { marketResearchAgent } from './market-research.agent.js';
import { monetizationAgent } from './monetization.agent.js';
import { opportunityScoringAgent } from './opportunity-scoring.agent.js';
import { opportunitySynthesisAgent } from './opportunity-synthesis.agent.js';
import { prdGeneratorAgent } from './prd-generator.agent.js';
import { problemDiscoveryAgent } from './problem-discovery.agent.js';
import { promptEngineerAgent } from './prompt-engineer.agent.js';
import { reviewAnalysisAgent } from './review-analysis.agent.js';
import { technicalFeasibilityAgent } from './technical-feasibility.agent.js';
import { trendHunterAgent } from './trend-hunter.agent.js';

/**
 * Every agent shipped with the platform.
 *
 * Adding one means adding a file here and nothing else: the registry derives
 * schemas, the orchestrator derives the run order from `dependencies`, and the
 * mock provider picks up the fixture automatically.
 */
export const BUILT_IN_AGENTS: readonly AgentDefinition[] = [
  trendHunterAgent,
  marketResearchAgent,
  competitorAnalysisAgent,
  reviewAnalysisAgent,
  problemDiscoveryAgent,
  opportunitySynthesisAgent,
  monetizationAgent,
  technicalFeasibilityAgent,
  opportunityScoringAgent,
  prdGeneratorAgent,
  promptEngineerAgent,
] as AgentDefinition[];

/**
 * Leaf agents of the research pipeline. Naming only the leaves is enough: the
 * registry pulls in every dependency transitively, and the on-demand delivery
 * agents (PRD, prompt engineering) stay out of a research run.
 */
export const RESEARCH_PIPELINE_ROOTS = ['opportunity-scoring'] as const;

export { competitorAnalysisAgent } from './competitor-analysis.agent.js';
export { marketResearchAgent } from './market-research.agent.js';
export { monetizationAgent } from './monetization.agent.js';
export { opportunityScoringAgent } from './opportunity-scoring.agent.js';
export { opportunitySynthesisAgent } from './opportunity-synthesis.agent.js';
export { prdGeneratorAgent } from './prd-generator.agent.js';
export { problemDiscoveryAgent } from './problem-discovery.agent.js';
export { promptEngineerAgent } from './prompt-engineer.agent.js';
export { reviewAnalysisAgent } from './review-analysis.agent.js';
export { technicalFeasibilityAgent } from './technical-feasibility.agent.js';
export { trendHunterAgent } from './trend-hunter.agent.js';
