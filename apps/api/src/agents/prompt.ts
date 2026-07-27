/**
 * Rules appended to every agent system prompt.
 *
 * These encode the platform's non-negotiables: no invented data, explicit
 * evidence typing, ranges instead of false precision. They live in one place so
 * a new agent inherits them automatically.
 */
export const GLOBAL_AGENT_RULES = `
GROUND RULES (apply to every field you produce):
1. Never invent facts, numbers, company names, URLs, ratings or review counts.
   If you do not have it, omit the field or return an empty array.
2. Only cite a URL that appears in the EVIDENCE section of the user message.
   Never construct, guess or complete a URL.
3. Tag every substantive statement with an evidenceType:
   - VERIFIED: directly supported by a provided source.
   - INFERENCE: logically derived from provided sources; say from what.
   - ESTIMATE: a modelled number; must be a range with its basis stated.
   - HYPOTHESIS: unverified reasoning with no supporting source.
4. When the EVIDENCE section is empty, nothing can be VERIFIED. Use HYPOTHESIS
   and lower your confidence accordingly. Do not compensate by making things up.
5. Express every quantity as a range (low/high) with a unit, a confidence in
   0..1, and the basis for the range. Never present an estimate as a fact.
6. Confidence is 0..1 and must reflect real evidential support, not writing
   fluency. Weak evidence means low confidence.
7. Answer with JSON only, matching the requested schema exactly. No markdown,
   no commentary, no trailing text.
`.trim();

export interface EvidenceLine {
  url: string;
  title: string;
  snippet: string;
  publisher?: string | undefined;
  publishedAt?: string | undefined;
}

/** Renders the evidence block, or an explicit "no evidence" marker. */
export function renderEvidence(lines: readonly EvidenceLine[], synthetic: boolean): string {
  if (synthetic) {
    return [
      'EVIDENCE: none. The research backend is running offline (no live search).',
      'Nothing may be marked VERIFIED and no URL may be cited in this run.',
    ].join('\n');
  }
  if (lines.length === 0) {
    return 'EVIDENCE: none found for this query. Nothing may be marked VERIFIED.';
  }
  const rendered = lines
    .map((line, index) => {
      const meta = [line.publisher, line.publishedAt].filter(Boolean).join(', ');
      return [
        `[${index + 1}] ${line.title}${meta ? ` (${meta})` : ''}`,
        `    url: ${line.url}`,
        `    excerpt: ${truncate(line.snippet, 700)}`,
      ].join('\n');
    })
    .join('\n');
  return `EVIDENCE (the only URLs you may cite):\n${rendered}`;
}

export function truncate(text: string, max: number): string {
  if (text.length <= max) return text;
  return `${text.slice(0, max - 1)}…`;
}
