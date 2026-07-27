import type { SearchProvider, SearchQuery, SearchResponse } from './types.js';

/**
 * Offline search backend.
 *
 * It returns *no results at all* rather than plausible-looking fake URLs. A
 * fabricated citation is worse than no citation: the whole point of the source
 * layer is that a URL shown in the UI can be opened and checked. Agents running
 * against this provider therefore have nothing to cite and must downgrade their
 * output to HYPOTHESIS, which is exactly the intended offline behaviour.
 */
export class MockSearchProvider implements SearchProvider {
  readonly name = 'mock';
  readonly synthetic = true;

  async search(query: SearchQuery): Promise<SearchResponse> {
    return {
      provider: this.name,
      query: query.query,
      results: [],
      synthetic: true,
    };
  }
}
