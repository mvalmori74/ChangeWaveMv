import AddIcon from '@mui/icons-material/Add';
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
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { PLATFORMS } from '@aiaf/shared';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCreateProject, useProjects } from '../api/hooks';

export function ProjectsPage() {
  const { data, isLoading, error } = useProjects();
  const createProject = useCreateProject();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    name: '',
    sector: '',
    country: '',
    platform: 'ANDROID',
    language: 'en',
    timeframe: 'last 12 months',
    objective: '',
  });

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const project = await createProject.mutateAsync({
      name: form.name,
      sector: form.sector,
      country: form.country,
      platform: form.platform as (typeof PLATFORMS)[number],
      language: form.language,
      timeframe: form.timeframe,
      ...(form.objective ? { objective: form.objective } : {}),
    });
    setOpen(false);
    navigate(`/projects/${project.id}`);
  };

  return (
    <Stack spacing={3}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h4">Research projects</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
          New project
        </Button>
      </Box>

      {isLoading && <CircularProgress />}
      {error && <Alert severity="error">Could not load projects.</Alert>}

      {data && (
        <Card>
          <CardContent>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Sector</TableCell>
                  <TableCell>Country</TableCell>
                  <TableCell>Platform</TableCell>
                  <TableCell align="right">Opportunities</TableCell>
                  <TableCell align="right">Runs</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.map((project) => (
                  <TableRow
                    key={project.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/projects/${project.id}`)}
                  >
                    <TableCell>{project.name}</TableCell>
                    <TableCell>{project.sector}</TableCell>
                    <TableCell>{project.country}</TableCell>
                    <TableCell>{project.platform}</TableCell>
                    <TableCell align="right">{project._count?.opportunities ?? 0}</TableCell>
                    <TableCell align="right">{project._count?.runs ?? 0}</TableCell>
                    <TableCell>
                      <Chip size="small" label={project.status.toLowerCase()} />
                    </TableCell>
                  </TableRow>
                ))}
                {data.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7}>
                      <Typography variant="body2" color="text.secondary">
                        No projects yet. Create one to start a research run.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <form onSubmit={submit}>
          <DialogTitle>New research project</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              <TextField
                label="Project name"
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                required
                fullWidth
              />
              <TextField
                label="Sector"
                placeholder="e.g. automotive maintenance"
                value={form.sector}
                onChange={(event) => setForm({ ...form, sector: event.target.value })}
                required
                fullWidth
              />
              <TextField
                label="Country"
                value={form.country}
                onChange={(event) => setForm({ ...form, country: event.target.value })}
                required
                fullWidth
              />
              <TextField
                select
                label="Platform"
                value={form.platform}
                onChange={(event) => setForm({ ...form, platform: event.target.value })}
                fullWidth
              >
                {PLATFORMS.map((platform) => (
                  <MenuItem key={platform} value={platform}>
                    {platform}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label="Timeframe"
                value={form.timeframe}
                onChange={(event) => setForm({ ...form, timeframe: event.target.value })}
                fullWidth
              />
              <TextField
                label="Objective (optional)"
                value={form.objective}
                onChange={(event) => setForm({ ...form, objective: event.target.value })}
                multiline
                minRows={2}
                fullWidth
              />
              {createProject.error && (
                <Alert severity="error">{(createProject.error as Error).message}</Alert>
              )}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createProject.isPending}>
              Create
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Stack>
  );
}
