import type { PrdContent } from '../agents/definitions/prd-generator.agent.js';

/**
 * Renders the structured PRD to Markdown.
 *
 * The section order is fixed by the spec, and rendering is deterministic: the
 * exported document is a pure function of the stored content, so re-exporting
 * an old PRD always produces the same file.
 */
export function renderPrdMarkdown(
  title: string,
  content: PrdContent,
  meta: {
    opportunityTitle: string;
    finalScore: number | null;
    classification: string | null;
    confidence: number | null;
    generatedAt: Date;
    synthetic: boolean;
  },
): string {
  const lines: string[] = [];
  const push = (...values: string[]) => lines.push(...values);

  push(`# ${title}`, '');
  push(
    '| | |',
    '|---|---|',
    `| Opportunity | ${meta.opportunityTitle} |`,
    `| Score | ${meta.finalScore ?? 'not scored'}${meta.classification ? ` (${meta.classification})` : ''} |`,
    `| Confidence | ${meta.confidence !== null ? formatPercent(meta.confidence) : 'unknown'} |`,
    `| Generated | ${meta.generatedAt.toISOString()} |`,
    '',
  );

  if (meta.synthetic) {
    push(
      '> **Warning:** this document was generated with the offline mock provider.',
      '> Its content is synthetic and unresearched. Regenerate with a live LLM',
      '> provider before making any decision on it.',
      '',
    );
  }

  push('## 1. Executive Summary', '', content.executiveSummary, '');
  push('## 2. Problem Statement', '', content.problemStatement, '');

  push('## 3. Target Users', '');
  for (const user of content.targetUsers) push(`- **${user.segment}** — ${user.description}`);
  push('');

  push('## 4. User Personas', '');
  for (const persona of content.personas) {
    push(`### ${persona.name} (${persona.role})`, '', persona.context, '');
    push('**Goals**', '');
    for (const goal of persona.goals) push(`- ${goal}`);
    push('', '**Frustrations**', '');
    for (const frustration of persona.frustrations) push(`- ${frustration}`);
    push('');
  }

  push('## 5. User Stories', '');
  for (const story of content.userStories) {
    push(`- \`${story.priority}\` As a ${story.asA}, I want ${story.iWant}, so that ${story.soThat}.`);
  }
  push('');

  push('## 6. Jobs To Be Done', '');
  for (const job of content.jobsToBeDone) push(`- ${job}`);
  push('');

  push('## 7. Core Features', '');
  push('| Feature | Priority | Description |', '|---|---|---|');
  for (const feature of content.coreFeatures) {
    push(`| ${feature.name} | ${feature.priority} | ${escapeCell(feature.description)} |`);
  }
  push('');

  push('## 8. MVP Features', '');
  for (const feature of content.mvpFeatures) {
    push(`### ${feature.name}`, '', feature.description, '', '**Acceptance criteria**', '');
    for (const criterion of feature.acceptanceCriteria) push(`- [ ] ${criterion}`);
    push('');
  }

  push('## 9. Future Features', '');
  if (content.futureFeatures.length === 0) push('_None recorded._');
  for (const feature of content.futureFeatures) push(`- ${feature}`);
  push('');

  push('## 10. UX Requirements', '');
  for (const requirement of content.uxRequirements) push(`- ${requirement}`);
  push('');

  push('## 11. Functional Requirements', '');
  push('| ID | Priority | Requirement |', '|---|---|---|');
  for (const requirement of content.functionalRequirements) {
    push(`| ${requirement.id} | ${requirement.priority} | ${escapeCell(requirement.requirement)} |`);
  }
  push('');

  push('## 12. Non Functional Requirements', '');
  push('| Category | Requirement |', '|---|---|');
  for (const requirement of content.nonFunctionalRequirements) {
    push(`| ${requirement.category} | ${escapeCell(requirement.requirement)} |`);
  }
  push('');

  push('## 13. Data Model', '');
  for (const entity of content.dataModel) {
    push(`### ${entity.entity}`, '', entity.description, '');
    push('| Field | Type | Description |', '|---|---|---|');
    for (const field of entity.fields) {
      push(`| ${field.name} | \`${field.type}\` | ${escapeCell(field.description)} |`);
    }
    if (entity.relations.length > 0) {
      push('', '**Relations**', '');
      for (const relation of entity.relations) push(`- ${relation}`);
    }
    push('');
  }

  push('## 14. API Requirements', '');
  if (content.apiRequirements.length === 0) {
    push('_No server API required._', '');
  } else {
    push('| Method | Path | Auth | Description |', '|---|---|---|---|');
    for (const endpoint of content.apiRequirements) {
      push(
        `| ${endpoint.method} | \`${endpoint.path}\` | ${endpoint.auth ? 'yes' : 'no'} | ${escapeCell(endpoint.description)} |`,
      );
    }
    push('');
  }

  push('## 15. Security', '');
  for (const item of content.security) push(`- ${item}`);
  push('');

  push('## 16. Privacy', '');
  for (const item of content.privacy) push(`- ${item}`);
  push('');

  push('## 17. GDPR Considerations', '');
  for (const item of content.gdpr) push(`- ${item}`);
  push('');

  push('## 18. Analytics Events', '');
  if (content.analyticsEvents.length === 0) {
    push('_None defined._', '');
  } else {
    push('| Event | Trigger | Properties |', '|---|---|---|');
    for (const event of content.analyticsEvents) {
      push(`| \`${event.name}\` | ${escapeCell(event.trigger)} | ${event.properties.join(', ') || '—'} |`);
    }
    push('');
  }

  push('## 19. Monetization', '');
  push(`- **Model:** ${content.monetization.model}`);
  push(`- **Details:** ${content.monetization.details}`);
  push(`- **Pricing:** ${content.monetization.pricing}`);
  push('');

  push('## 20. KPIs', '');
  push('| KPI | Definition | Target |', '|---|---|---|');
  for (const kpi of content.kpis) {
    push(`| ${kpi.name} | ${escapeCell(kpi.definition)} | ${kpi.target} |`);
  }
  push('');

  push('## 21. Success Criteria', '');
  for (const criterion of content.successCriteria) push(`- ${criterion}`);
  push('');

  push('## 22. Risks', '');
  push('| Risk | Impact | Mitigation |', '|---|---|---|');
  for (const risk of content.risks) {
    push(`| ${escapeCell(risk.risk)} | ${risk.impact} | ${escapeCell(risk.mitigation)} |`);
  }
  push('');

  push('## 23. Assumptions', '');
  for (const assumption of content.assumptions) push(`- ${assumption}`);
  push('');

  return lines.join('\n');
}

function escapeCell(value: string): string {
  return value.replace(/\|/g, '\\|').replace(/\n/g, ' ');
}

function formatPercent(value: number): string {
  return `${Math.round(value * 100)}%`;
}
