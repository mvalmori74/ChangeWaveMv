import DownloadIcon from '@mui/icons-material/Download';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid2 as Grid,
  Link,
  List,
  ListItem,
  ListItemText,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { CRITERION_LABELS, type ScoringCriterion } from '@aiaf/shared';
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { downloadMarkdown } from '../api/client';
import {
  useDecision,
  useGeneratePrd,
  useGeneratePrompt,
  useOpportunity,
  useOpportunityResearch,
} from '../api/hooks';
import { ConfidenceChip, EvidenceChip, ScoreChip } from '../components/ScoreChip';

export function OpportunityDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const { data: opportunity, isLoading, error } = useOpportunity(id);
  const { data: research } = useOpportunityResearch(id);
  const decide = useDecision(id);
  const generatePrd = useGeneratePrd(id);
  const generatePrompt = useGeneratePrompt(id);

  const [decisionDialog, setDecisionDialog] = useState<'APPROVE' | 'REJECT' | null>(null);
  const [rationale, setRationale] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  if (isLoading) return <CircularProgress />;
  if (error || !opportunity) return <Alert severity="error">Could not load this opportunity.</Alert>;

  const latestPrd = opportunity.prds[0];
  const latestPrompt = opportunity.prompts[0];
  const canApprove = ['DISCOVERED', 'ANALYZING', 'SCORED', 'REJECTED'].includes(
    opportunity.status,
  );
  const canReject = ['DISCOVERED', 'ANALYZING', 'SCORED', 'APPROVED'].includes(opportunity.status);

  const run = async (action: () => Promise<unknown>) => {
    setActionError(null);
    try {
      await action();
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Action failed');
    }
  };

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">{opportunity.title}</Typography>
        <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap' }}>
          <ScoreChip score={opportunity.finalScore} classification={opportunity.classification} />
          <ConfidenceChip confidence={opportunity.confidenceScore} />
          <EvidenceChip evidenceType={opportunity.evidenceType} />
          <Chip size="small" label={opportunity.category} />
          <Chip size="small" variant="outlined" label={opportunity.status.replace(/_/g, ' ').toLowerCase()} />
        </Stack>
      </Box>

      {actionError && <Alert severity="error">{actionError}</Alert>}

      <Card>
        <CardContent>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
            <Button
              variant="contained"
              color="success"
              disabled={!canApprove || decide.isPending}
              onClick={() => {
                setRationale('');
                setDecisionDialog('APPROVE');
              }}
            >
              Approve
            </Button>
            <Button
              variant="outlined"
              color="error"
              disabled={!canReject || decide.isPending}
              onClick={() => {
                setRationale('');
                setDecisionDialog('REJECT');
              }}
            >
              Reject
            </Button>
            <Tooltip title="Requires an approved opportunity">
              <span>
                <Button
                  variant="contained"
                  disabled={generatePrd.isPending}
                  onClick={() => void run(() => generatePrd.mutateAsync())}
                >
                  {generatePrd.isPending ? 'Generating…' : 'Generate PRD'}
                </Button>
              </span>
            </Tooltip>
            <Tooltip title="Requires a PRD">
              <span>
                <Button
                  variant="contained"
                  disabled={generatePrompt.isPending || !latestPrd}
                  onClick={() => void run(() => generatePrompt.mutateAsync())}
                >
                  {generatePrompt.isPending ? 'Generating…' : 'Generate Codex prompt'}
                </Button>
              </span>
            </Tooltip>
            {latestPrd && (
              <Button
                startIcon={<DownloadIcon />}
                onClick={() =>
                  void downloadMarkdown(`/api/prds/${latestPrd.id}/export`, 'prd.md')
                }
              >
                PRD v{latestPrd.version} (Markdown)
              </Button>
            )}
            {latestPrompt && (
              <Button
                startIcon={<DownloadIcon />}
                onClick={() =>
                  void downloadMarkdown(
                    `/api/prompts/${latestPrompt.id}/export`,
                    'codex-prompt.md',
                  )
                }
              >
                Prompt v{latestPrompt.version} (Markdown)
              </Button>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Analysis
              </Typography>
              <Typography variant="overline" color="text.secondary">
                Target audience
              </Typography>
              <Typography paragraph>{opportunity.targetAudience}</Typography>
              <Typography variant="overline" color="text.secondary">
                Problem
              </Typography>
              <Typography paragraph>{opportunity.problem}</Typography>
              <Typography variant="overline" color="text.secondary">
                Proposed solution
              </Typography>
              <Typography paragraph>{opportunity.proposedSolution}</Typography>
              {opportunity.mvpSummary?.features?.length ? (
                <>
                  <Typography variant="overline" color="text.secondary">
                    MVP
                  </Typography>
                  <ul>
                    {opportunity.mvpSummary.features.map((feature) => (
                      <li key={feature}>
                        <Typography variant="body2">{feature}</Typography>
                      </li>
                    ))}
                  </ul>
                </>
              ) : null}
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Scoring breakdown
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                The final score is a weighted sum computed by the platform, not by the model.
              </Typography>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Criterion</TableCell>
                    <TableCell align="right">Raw</TableCell>
                    <TableCell align="right">Weight</TableCell>
                    <TableCell align="right">Weighted</TableCell>
                    <TableCell align="right">Conf.</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {opportunity.scores.map((score) => (
                    <TableRow key={score.id}>
                      <TableCell>
                        <Tooltip title={score.rationale}>
                          <span>
                            {CRITERION_LABELS[score.criterion as ScoringCriterion] ??
                              score.criterion}
                          </span>
                        </Tooltip>
                      </TableCell>
                      <TableCell align="right">{score.rawScore.toFixed(0)}</TableCell>
                      <TableCell align="right">{(score.weight * 100).toFixed(0)}%</TableCell>
                      <TableCell align="right">{score.weightedScore.toFixed(1)}</TableCell>
                      <TableCell align="right">{(score.confidence * 100).toFixed(0)}%</TableCell>
                    </TableRow>
                  ))}
                  {opportunity.scores.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5}>
                        <Typography variant="body2" color="text.secondary">
                          Not scored yet.
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Monetization
              </Typography>
              {opportunity.monetization ? (
                <JsonFacts data={opportunity.monetization} />
              ) : (
                <Typography variant="body2" color="text.secondary">
                  No monetization analysis on record.
                </Typography>
              )}
              <Divider sx={{ my: 2 }} />
              <Typography variant="h6" gutterBottom>
                Technical feasibility
              </Typography>
              {opportunity.feasibility ? (
                <JsonFacts data={opportunity.feasibility} />
              ) : (
                <Typography variant="body2" color="text.secondary">
                  No feasibility assessment on record.
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Risks and assumptions
              </Typography>
              <Typography variant="overline" color="text.secondary">
                Risks
              </Typography>
              <List dense>
                {(opportunity.risks ?? []).map((risk) => (
                  <ListItem key={risk} disableGutters>
                    <ListItemText primary={risk} />
                  </ListItem>
                ))}
                {(opportunity.risks ?? []).length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    None recorded.
                  </Typography>
                )}
              </List>
              <Typography variant="overline" color="text.secondary">
                Assumptions
              </Typography>
              <List dense>
                {(opportunity.assumptions ?? []).map((assumption) => (
                  <ListItem key={assumption} disableGutters>
                    <ListItemText primary={assumption} />
                  </ListItem>
                ))}
                {(opportunity.assumptions ?? []).length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    None recorded.
                  </Typography>
                )}
              </List>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Competitors
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Model</TableCell>
                <TableCell>Pricing</TableCell>
                <TableCell align="right">Rating</TableCell>
                <TableCell>Weaknesses</TableCell>
                <TableCell>Evidence</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(research?.competitors ?? []).map((competitor) => (
                <TableRow key={competitor.id}>
                  <TableCell>
                    {competitor.url ? (
                      <Link href={competitor.url} target="_blank" rel="noreferrer">
                        {competitor.name}
                      </Link>
                    ) : (
                      competitor.name
                    )}
                  </TableCell>
                  <TableCell>{competitor.isDirect ? 'direct' : 'indirect'}</TableCell>
                  <TableCell>{competitor.businessModel ?? '—'}</TableCell>
                  <TableCell>{competitor.pricing ?? '—'}</TableCell>
                  <TableCell align="right">
                    {competitor.rating !== null
                      ? `${competitor.rating} (${competitor.reviewCount ?? '?'})`
                      : '—'}
                  </TableCell>
                  <TableCell>{competitor.weaknesses.join('; ') || '—'}</TableCell>
                  <TableCell>
                    <EvidenceChip evidenceType={competitor.evidenceType} />
                  </TableCell>
                </TableRow>
              ))}
              {(research?.competitors ?? []).length === 0 && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Typography variant="body2" color="text.secondary">
                      No competitor recorded for this project.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Pain points
              </Typography>
              <List dense>
                {(research?.painPoints ?? []).map((painPoint) => (
                  <ListItem key={painPoint.id} disableGutters>
                    <ListItemText
                      primary={painPoint.problem}
                      secondary={`${painPoint.targetUser} · ${painPoint.severity.toLowerCase()} severity · ${painPoint.frequency.toLowerCase()}`}
                    />
                    <EvidenceChip evidenceType={painPoint.evidenceType} />
                  </ListItem>
                ))}
                {(research?.painPoints ?? []).length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    None recorded.
                  </Typography>
                )}
              </List>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Sources
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Every URL the research run actually read.
              </Typography>
              <List dense>
                {(opportunity.run?.sources ?? []).map((source) => (
                  <ListItem key={source.id} disableGutters>
                    <ListItemText
                      primary={
                        <Link href={source.url} target="_blank" rel="noreferrer">
                          {source.title}
                        </Link>
                      }
                      secondary={`${source.publisher ?? 'unknown publisher'} · credibility ${(source.credibilityScore * 100).toFixed(0)}%`}
                    />
                  </ListItem>
                ))}
                {(opportunity.run?.sources ?? []).length === 0 && (
                  <Alert severity="warning">
                    No source was recorded. This analysis is unverified — treat every claim as a
                    hypothesis.
                  </Alert>
                )}
              </List>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Decisions
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            The AI recommendation and the human decision are recorded separately.
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>When</TableCell>
                <TableCell>AI recommendation</TableCell>
                <TableCell>Human decision</TableCell>
                <TableCell>Rationale</TableCell>
                <TableCell>By</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {opportunity.decisions.map((decision) => (
                <TableRow key={decision.id}>
                  <TableCell>
                    {decision.decidedAt ? new Date(decision.decidedAt).toLocaleString() : '—'}
                  </TableCell>
                  <TableCell>
                    {decision.aiRecommendation ?? '—'}
                    {decision.aiConfidence !== null
                      ? ` (${Math.round(decision.aiConfidence * 100)}%)`
                      : ''}
                  </TableCell>
                  <TableCell>
                    <Chip size="small" label={decision.humanDecision ?? decision.type} />
                  </TableCell>
                  <TableCell>{decision.humanRationale ?? '—'}</TableCell>
                  <TableCell>{decision.decidedBy?.name ?? '—'}</TableCell>
                </TableRow>
              ))}
              {opportunity.decisions.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5}>
                    <Typography variant="body2" color="text.secondary">
                      No decision recorded yet.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog open={decisionDialog !== null} onClose={() => setDecisionDialog(null)} fullWidth>
        <DialogTitle>
          {decisionDialog === 'APPROVE' ? 'Approve opportunity' : 'Reject opportunity'}
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Your rationale is stored next to the AI recommendation, so the trail shows why the two
            agreed or differed.
          </Typography>
          <TextField
            label="Rationale"
            value={rationale}
            onChange={(event) => setRationale(event.target.value)}
            multiline
            minRows={3}
            fullWidth
            required
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDecisionDialog(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={rationale.trim().length === 0 || decide.isPending}
            onClick={async () => {
              const type = decisionDialog;
              setDecisionDialog(null);
              if (type) await run(() => decide.mutateAsync({ type, rationale }));
            }}
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

/** Renders an agent's JSON block as readable label/value rows. */
function JsonFacts({ data }: { data: Record<string, unknown> }) {
  const entries = Object.entries(data).filter(
    ([key]) => !['candidateId', 'evidenceType'].includes(key),
  );
  return (
    <List dense>
      {entries.map(([key, value]) => (
        <ListItem key={key} disableGutters>
          <ListItemText
            primary={key.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase())}
            secondary={formatValue(value)}
          />
        </ListItem>
      ))}
    </List>
  );
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (Array.isArray(value)) {
    return value.length === 0 ? '—' : value.map((entry) => formatValue(entry)).join('; ');
  }
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>;
    if ('low' in record && 'high' in record) {
      return `${record['low']}–${record['high']} ${record['unit'] ?? ''} (${record['basis'] ?? 'no basis given'})`;
    }
    return Object.entries(record)
      .map(([key, entry]) => `${key}: ${formatValue(entry)}`)
      .join(', ');
  }
  return String(value);
}
