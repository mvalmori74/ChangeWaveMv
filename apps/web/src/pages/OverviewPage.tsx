import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Grid2 as Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import { useAgentCosts, useOverview } from '../api/hooks';

function StatCard({
  label,
  value,
  hint,
}: {
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h4">{value}</Typography>
        {hint && (
          <Typography variant="caption" color="text.secondary">
            {hint}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

const NO_DATA = '—';

export function OverviewPage() {
  const { data, isLoading, error } = useOverview();
  const costs = useAgentCosts();

  if (isLoading) return <CircularProgress />;
  if (error || !data) return <Alert severity="error">Could not load the dashboard.</Alert>;

  const percent = (value: number | null) =>
    value === null ? NO_DATA : `${Math.round(value * 100)}%`;

  return (
    <Stack spacing={3}>
      <Typography variant="h4">Overview</Typography>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard label="Opportunities analysed" value={String(data.opportunitiesAnalyzed)} />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            label="Approved"
            value={String(data.opportunitiesApproved)}
            hint={`${data.opportunitiesRejected} rejected or killed`}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            label="Average score"
            value={data.averageScore !== null ? data.averageScore.toFixed(1) : NO_DATA}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            label="Research spend"
            value={`$${data.totalLlmCostUsd.toFixed(4)}`}
            hint="LLM cost across all runs"
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            label="PRDs generated"
            value={String(data.prdsGenerated)}
            hint={`${data.promptsGenerated} build prompts`}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            label="Apps generated"
            value={String(data.appsGenerated)}
            hint={`${data.appsReleased} released`}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            label="Approval rate"
            value={percent(data.conversionRate)}
            hint="Approved / analysed"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatCard
            label="Revenue / ROI"
            value={data.revenueUsd === null ? NO_DATA : `$${data.revenueUsd.toFixed(2)}`}
            hint={
              data.revenueUsd === null
                ? 'No post-launch data collected yet'
                : `ROI ${percent(data.roi)}`
            }
          />
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Opportunities by status
          </Typography>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            {Object.entries(data.byStatus).length === 0 && (
              <Typography variant="body2" color="text.secondary">
                Nothing yet. Create a research project and run it.
              </Typography>
            )}
            {Object.entries(data.byStatus).map(([status, count]) => (
              <Chip key={status} label={`${status.replace(/_/g, ' ').toLowerCase()}: ${count}`} />
            ))}
          </Box>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Agent cost
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Where the token budget goes. Use it to move cheap tasks onto cheaper models.
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Agent</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Runs</TableCell>
                <TableCell align="right">Tokens</TableCell>
                <TableCell align="right">Cost</TableCell>
                <TableCell align="right">Avg duration</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(costs.data ?? []).map((row) => (
                <TableRow key={`${row.agentKey}-${row.status}`}>
                  <TableCell>{row.agentKey}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={row.status.toLowerCase()}
                      color={row.status === 'SUCCEEDED' ? 'success' : 'default'}
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell align="right">{row.executions}</TableCell>
                  <TableCell align="right">
                    <Tooltip title={`${row.promptTokens} in / ${row.completionTokens} out`}>
                      <span>{row.promptTokens + row.completionTokens}</span>
                    </Tooltip>
                  </TableCell>
                  <TableCell align="right">${row.costUsd.toFixed(4)}</TableCell>
                  <TableCell align="right">{(row.averageDurationMs / 1000).toFixed(1)}s</TableCell>
                </TableRow>
              ))}
              {(costs.data ?? []).length === 0 && (
                <TableRow>
                  <TableCell colSpan={6}>
                    <Typography variant="body2" color="text.secondary">
                      No agent has run yet.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </Stack>
  );
}
