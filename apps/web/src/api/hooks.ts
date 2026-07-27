import type { CreateResearchProjectRequest, DecisionRequest, Paginated } from '@aiaf/shared';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiRequest } from './client';
import type {
  AgentCost,
  AgentMeta,
  DashboardOverview,
  OpportunityDetail,
  OpportunitySummary,
  ResearchContext,
  ResearchProject,
  ResearchRun,
} from './types';

export const queryKeys = {
  overview: ['analytics', 'overview'] as const,
  pipeline: ['analytics', 'pipeline'] as const,
  agentCosts: ['analytics', 'agent-costs'] as const,
  projects: ['projects'] as const,
  project: (id: string) => ['projects', id] as const,
  run: (id: string) => ['runs', id] as const,
  opportunities: (query: string) => ['opportunities', query] as const,
  opportunity: (id: string) => ['opportunities', id] as const,
  opportunityResearch: (id: string) => ['opportunities', id, 'research'] as const,
  agents: ['agents'] as const,
};

export function useOverview() {
  return useQuery({
    queryKey: queryKeys.overview,
    queryFn: () => apiRequest<DashboardOverview>('/api/analytics/overview'),
  });
}

export function usePipeline() {
  return useQuery({
    queryKey: queryKeys.pipeline,
    queryFn: () => apiRequest<OpportunitySummary[]>('/api/analytics/pipeline'),
  });
}

export function useAgentCosts() {
  return useQuery({
    queryKey: queryKeys.agentCosts,
    queryFn: () => apiRequest<AgentCost[]>('/api/analytics/agent-costs'),
  });
}

export function useProjects() {
  return useQuery({
    queryKey: queryKeys.projects,
    queryFn: () => apiRequest<ResearchProject[]>('/api/research/projects'),
  });
}

export function useProject(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.project(id ?? ''),
    queryFn: () => apiRequest<ResearchProject>(`/api/research/projects/${id}`),
    enabled: Boolean(id),
  });
}

export function useCreateProject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateResearchProjectRequest) =>
      apiRequest<ResearchProject>('/api/research/projects', { method: 'POST', body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.projects }),
  });
}

export function useStartRun() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (projectId: string) =>
      apiRequest<ResearchRun>(`/api/research/projects/${projectId}/runs`, { method: 'POST' }),
    onSuccess: (_run, projectId) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.project(projectId) });
    },
  });
}

/** Polls while the run is active so the UI reflects agent-by-agent progress. */
export function useRun(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.run(id ?? ''),
    queryFn: () => apiRequest<ResearchRun>(`/api/research/runs/${id}`),
    enabled: Boolean(id),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'PENDING' || status === 'RUNNING' ? 2000 : false;
    },
  });
}

export function useOpportunities(params: URLSearchParams) {
  const query = params.toString();
  return useQuery({
    queryKey: queryKeys.opportunities(query),
    queryFn: () => apiRequest<Paginated<OpportunitySummary>>(`/api/opportunities?${query}`),
  });
}

export function useOpportunity(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.opportunity(id ?? ''),
    queryFn: () => apiRequest<OpportunityDetail>(`/api/opportunities/${id}`),
    enabled: Boolean(id),
  });
}

export function useOpportunityResearch(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.opportunityResearch(id ?? ''),
    queryFn: () => apiRequest<ResearchContext>(`/api/opportunities/${id}/research`),
    enabled: Boolean(id),
  });
}

export function useDecision(opportunityId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: DecisionRequest) =>
      apiRequest(`/api/opportunities/${opportunityId}/decisions`, { method: 'POST', body }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.opportunity(opportunityId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.overview });
      void queryClient.invalidateQueries({ queryKey: queryKeys.pipeline });
    },
  });
}

export function useGeneratePrd(opportunityId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiRequest<{ id: string }>(`/api/opportunities/${opportunityId}/prd`, { method: 'POST' }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.opportunity(opportunityId) }),
  });
}

export function useGeneratePrompt(opportunityId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiRequest<{ id: string }>(`/api/opportunities/${opportunityId}/codex-prompt`, {
        method: 'POST',
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.opportunity(opportunityId) }),
  });
}

export function useAgents() {
  return useQuery({
    queryKey: queryKeys.agents,
    queryFn: () => apiRequest<AgentMeta[]>('/api/agents'),
  });
}

export function useToggleAgent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      apiRequest<AgentMeta>(`/api/agents/${key}`, { method: 'PATCH', body: { enabled } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.agents }),
  });
}
