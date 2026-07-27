import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Chip,
  CircularProgress,
  Stack,
  Switch,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { useAgents, useToggleAgent } from '../api/hooks';

export function AgentsPage() {
  const { data, isLoading, error } = useAgents();
  const toggle = useToggleAgent();

  if (isLoading) return <CircularProgress />;
  if (error || !data) return <Alert severity="error">Could not load the agent registry.</Alert>;

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Agent registry</Typography>
        <Typography variant="body2" color="text.secondary">
          Agents are data, not code paths. The run order below is derived from each agent's declared
          dependencies; disabling one also skips everything that depends on it.
        </Typography>
      </Box>

      {toggle.error && <Alert severity="error">{(toggle.error as Error).message}</Alert>}

      {data.map((agent) => (
        <Accordion key={agent.key}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%' }}>
              <Switch
                checked={agent.enabled}
                onClick={(event) => event.stopPropagation()}
                onChange={(event) =>
                  toggle.mutate({ key: agent.key, enabled: event.target.checked })
                }
                inputProps={{ 'aria-label': `Toggle ${agent.name}` }}
              />
              <Box sx={{ flexGrow: 1 }}>
                <Typography variant="subtitle1">{agent.name}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {agent.key} · v{agent.version} · {agent.role}
                </Typography>
              </Box>
              <Chip size="small" variant="outlined" label={agent.modelTier} />
            </Box>
          </AccordionSummary>
          <AccordionDetails>
            <Typography paragraph>{agent.description}</Typography>
            <Typography variant="overline" color="text.secondary">
              Dependencies
            </Typography>
            <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 2 }}>
              {agent.dependencies.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  None — this agent can start a run.
                </Typography>
              )}
              {agent.dependencies.map((dependency) => (
                <Chip key={dependency} size="small" label={dependency} />
              ))}
            </Box>
            <Typography variant="overline" color="text.secondary">
              System prompt
            </Typography>
            <Box
              component="pre"
              sx={{
                whiteSpace: 'pre-wrap',
                bgcolor: 'grey.100',
                p: 1.5,
                borderRadius: 1,
                fontSize: 13,
                overflowX: 'auto',
              }}
            >
              {agent.systemPrompt}
            </Box>
          </AccordionDetails>
        </Accordion>
      ))}
    </Stack>
  );
}
