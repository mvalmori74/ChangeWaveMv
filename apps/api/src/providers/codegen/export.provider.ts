import type {
  CodeGenerationHandle,
  CodeGenerationProvider,
  CodeGenerationRequest,
} from './types.js';

/**
 * V1 code-generation backend: prepares the prompt and hands it to a human.
 *
 * Nothing is dispatched anywhere. The operator copies or downloads the prompt
 * and runs it in their coding agent of choice, which keeps the mandated
 * human-in-the-loop gate in front of every line of generated code.
 */
export class ExportCodeGenerationProvider implements CodeGenerationProvider {
  readonly name = 'export';

  async submit(request: CodeGenerationRequest): Promise<CodeGenerationHandle> {
    return {
      externalRef: null,
      status: 'PROMPT_READY',
      instructions:
        `Prompt ready for "${request.projectName}". Download or copy it from the ` +
        'opportunity detail page and run it in your coding agent, then record the ' +
        'repository and branch on this project.',
    };
  }

  async getStatus(externalRef: string): Promise<CodeGenerationHandle> {
    return {
      externalRef,
      status: 'PROMPT_READY',
      instructions: 'Status is tracked manually while running in export mode.',
    };
  }
}
