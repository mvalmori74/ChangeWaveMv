import {
  Alert,
  Box,
  Card,
  CardActionArea,
  CardContent,
  CircularProgress,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { PIPELINE_COLUMNS } from '@aiaf/shared';
import { useNavigate } from 'react-router-dom';
import { usePipeline } from '../api/hooks';
import { ScoreChip } from '../components/ScoreChip';

export function PipelinePage() {
  const { data, isLoading, error } = usePipeline();
  const navigate = useNavigate();

  if (isLoading) return <CircularProgress />;
  if (error || !data) return <Alert severity="error">Could not load the pipeline.</Alert>;

  return (
    <Stack spacing={3}>
      <Typography variant="h4">Project pipeline</Typography>
      <Box sx={{ display: 'flex', gap: 2, overflowX: 'auto', pb: 2 }}>
        {PIPELINE_COLUMNS.map((status) => {
          const items = data.filter((opportunity) => opportunity.status === status);
          return (
            <Paper key={status} sx={{ minWidth: 260, p: 1.5, bgcolor: 'grey.50' }} variant="outlined">
              <Typography variant="subtitle2" gutterBottom>
                {status.replace(/_/g, ' ').toLowerCase()} ({items.length})
              </Typography>
              <Stack spacing={1}>
                {items.map((opportunity) => (
                  <Card key={opportunity.id}>
                    <CardActionArea onClick={() => navigate(`/opportunities/${opportunity.id}`)}>
                      <CardContent sx={{ p: 1.5 }}>
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {opportunity.title}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" display="block">
                          {opportunity.category}
                        </Typography>
                        <Box sx={{ mt: 1 }}>
                          <ScoreChip
                            score={opportunity.finalScore}
                            classification={opportunity.classification}
                          />
                        </Box>
                      </CardContent>
                    </CardActionArea>
                  </Card>
                ))}
                {items.length === 0 && (
                  <Typography variant="caption" color="text.secondary">
                    Empty
                  </Typography>
                )}
              </Stack>
            </Paper>
          );
        })}
      </Box>
    </Stack>
  );
}
