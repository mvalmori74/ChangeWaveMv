import { ProviderError } from '../../core/errors.js';
import type { SearchProvider, SearchQuery, SearchResponse, SearchResult } from './types.js';

interface TavilyApiResult {
  url?: string;
  title?: string;
  content?: string;
  score?: number;
  published_date?: string;
}

/**
 * Generic web research backend. Tavily is used because it returns citable URLs
 * with snippets; the platform depends only on the SearchProvider interface, so
 * this class is replaceable without touching any agent.
 */
export class TavilySearchProvider implements SearchProvider {
  readonly name = 'tavily';
  readonly synthetic = false;

  constructor(
    private readonly apiKey: string,
    private readonly defaultMaxResults: number,
    private readonly endpoint = 'https://api.tavily.com/search',
  ) {}

  async search(query: SearchQuery): Promise<SearchResponse> {
    let response: Response;
    try {
      response = await fetch(this.endpoint, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          api_key: this.apiKey,
          query: query.query,
          max_results: query.maxResults ?? this.defaultMaxResults,
          search_depth: 'advanced',
          include_answer: false,
          ...(query.recency && query.recency !== 'any' ? { time_range: query.recency } : {}),
        }),
        ...(query.signal ? { signal: query.signal } : {}),
      });
    } catch (error) {
      throw new ProviderError(
        `Search request failed: ${error instanceof Error ? error.message : 'unknown error'}`,
        this.name,
      );
    }

    if (!response.ok) {
      throw new ProviderError(
        `Search request failed with status ${response.status}`,
        this.name,
        { status: response.status },
      );
    }

    const body = (await response.json()) as { results?: TavilyApiResult[] };
    const results: SearchResult[] = (body.results ?? [])
      .filter((item): item is TavilyApiResult & { url: string } => Boolean(item.url))
      .map((item) => ({
        url: item.url,
        title: item.title ?? item.url,
        snippet: item.content ?? '',
        publisher: safeHostname(item.url),
        ...(item.published_date ? { publishedAt: item.published_date } : {}),
        sourceType: classifySource(item.url),
        score: typeof item.score === 'number' ? clamp01(item.score) : 0.5,
      }));

    return { provider: this.name, query: query.query, results, synthetic: false };
  }
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

function safeHostname(url: string): string | undefined {
  try {
    return new URL(url).hostname;
  } catch {
    return undefined;
  }
}

function classifySource(url: string): SearchResult['sourceType'] {
  const host = safeHostname(url) ?? '';
  if (host.includes('play.google.com') || host.includes('apps.apple.com')) return 'APP_STORE';
  if (host.includes('reddit.com') || host.includes('stackexchange.com')) return 'FORUM';
  if (host.includes('news') || host.includes('techcrunch')) return 'NEWS';
  return 'WEB';
}
