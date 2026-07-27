import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  LinearProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useProject, useRun, useStartRun } from '../api/hooks';

const ACTIVE_STATUSES = ['PENDING', 'RUNNING'];

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { data: project, isLoading, error } = useProject(id);
  const startRun = useStartRun();
  const [activeRunId, setActiveRunId] = useState<string | undefined>();

  const latestRunId = activeRunId ?? project?.runs?.[0]?.id;
  const { data: run } = useRun(latestRunId);

  if (isLoading) return <CircularProgress />;
  if (error || !project) return <Alert severity="error">Could not load this project.</Alert>;

  const running = run ? ACTIVE_STATUSES.includes(run.status) : false;

  return (
    <Stack spacing={3}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <Box>
          <Typography variant="h4">{project.name}</Typography>
          <Typography variant="body2" color="text.secondary">
            {project.sector} · {project.country} · {project.platform} · {project.timeframe}
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<PlayArrowIcon />}
          disabled={running || startRun.isPending}
          onClick={async () => {
            const created = await startRun.mutateAsync(project.id);
            setActiveRunId(created.id);
          }}
        >
          {running ? 'Run in progress' : 'Start research run'}
        </Button>
      </Box>

      {project.objective && (
        <Card>
          <CardContent>
            <Typography variant="overline" color="text.secondary">
              Objective
            </Typography>
            <Typography variant="body1">{project.objective}</Typography>
          </CardContent>
        </Card>
      )}

      {startRun.error && <Alert severity="error">{(startRun.error as Error).message}</Alert>}

      {run && (
        <Card>
          <CardContent>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
              <Typography variant="h6">Latest run</Typography>
              <Stack direction="row" spacing={1}>
                <Chip
                  size="small"
                  label={run.status.toLowerCase()}
                  color={
                    run.status === 'COMPLETED'
                      ? 'success'
                      : run.status === 'FAILED'
                        ? 'error'
                        : run.status === 'PARTIAL'
                          ? 'warning'
                          : 'default'
                  }
                />
                <Chip size="small" variant="outlined" label={`$${Number(run.totalCostUsd).toFixed(4)}`} />
                <Chip size="small" variant="outlined" label={`${run.totalTokens} tokens`} />
              </Stack>
            </Box>

            {running && <LinearProgress sx={{ mb: 2 }} />}
            {run.error && (
              <Alert severity="warning" sx={{ mb: 2 }}>
                {run.error}
              </Alert>
            )}

            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Agent</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Model</TableCell>
                  <TableCell align="right">Tokens</TableCell>
                  <TableCell align="right">Cost</TableCell>
                  <TableCell align="right">Duration</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(run.executions ?? []).map((execution) => (
                  <TableRow key={execution.id}>
                    <TableCell>{execution.agentKey}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        variant="outlined"
                        color={
                          execution.status === 'SUCCEEDED'
                            ? 'success'
                            : execution.status === 'FAILED'
                              ? 'error'
                              : 'default'
                        }
                        label={execution.status.toLowerCase()}
                      />
                    </TableCell>
                    <TableCell>{execution.model ?? '—'}</TableCell>
                    <TableCell align="right">
                      {execution.promptTokens + execution.completionTokens}
                    </TableCell>
                    <TableCell align="right">${Number(execution.costUsd).toFixed(4)}</TableCell>
                    <TableCell align="right">
                      {(execution.durationMs / 1000).toFixed(1)}s
                    </TableCell>
                  </TableRow>
                ))}
                {(run.executions ?? []).length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6}>
                      <Typography variant="body2" color="text.secondary">
                        Waiting for the first agent to start…
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>

            {run.status === 'COMPLETED' || run.status === 'PARTIAL' ? (
              <Button
                sx={{ mt: 2 }}
                component={RouterLink}
                to={`/opportunities?projectId=${project.id}`}
              >
                View the opportunities from this run
              </Button>
            ) : null}
          </CardContent>
        </Card>
      )}

      {!run && (
        <Alert severity="info">
          No run yet. Start one to discover, score and rank opportunities in this sector.
        </Alert>
      )}
    </Stack>
  );
}
