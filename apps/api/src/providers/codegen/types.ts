import type { CodeGenerationStatus } from '@aiaf/shared';

export interface CodeGenerationRequest {
  opportunityId: string;
  projectName: string;
  prompt: string;
  repository?: string;
  branch?: string;
}

export interface CodeGenerationHandle {
  /** Provider-side identifier, when the backend has one. */
  externalRef: string | null;
  status: CodeGenerationStatus;
  /** Human-readable next step, shown in the UI. */
  instructions: string;
}

/**
 * Abstraction over "something that turns a prompt into a repository".
 *
 * V1 deliberately ships only the export implementation: the spec puts
 * autonomous Codex execution out of scope, and no human approval gate can be
 * enforced by a fire-and-forget integration. A future hosted-agent backend
 * implements this same interface.
 */
export interface CodeGenerationProvider {
  readonly name: string;
  submit(request: CodeGenerationRequest): Promise<CodeGenerationHandle>;
  getStatus(externalRef: string): Promise<CodeGenerationHandle>;
}
