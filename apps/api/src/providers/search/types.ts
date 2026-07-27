import type { SourceType } from '@aiaf/shared';

export interface SearchQuery {
  query: string;
  maxResults?: number;
  /** ISO country code or plain country name, passed through when supported. */
  country?: string;
  language?: string;
  /** Restrict to a time window, e.g. "year", "month". */
  recency?: 'day' | 'week' | 'month' | 'year' | 'any';
  signal?: AbortSignal;
}

export interface SearchResult {
  url: string;
  title: string;
  snippet: string;
  publisher?: string;
  publishedAt?: string;
  sourceType: SourceType;
  /** 0-1 provider relevance score, normalised. */
  score: number;
}

export interface SearchResponse {
  provider: string;
  query: string;
  results: SearchResult[];
  /** True when the results are synthetic and must not be cited as evidence. */
  synthetic: boolean;
}

/**
 * Research backends implement this. No agent talks to a search vendor directly,
 * so swapping Tavily for Bing/SerpAPI/an internal index is a one-file change.
 */
export interface SearchProvider {
  readonly name: string;
  readonly synthetic: boolean;
  search(query: SearchQuery): Promise<SearchResponse>;
}
