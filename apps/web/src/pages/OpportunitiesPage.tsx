import {
  Alert,
  Card,
  CardContent,
  CircularProgress,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { OPPORTUNITY_STATUSES, PLATFORMS } from '@aiaf/shared';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useOpportunities } from '../api/hooks';
import { ConfidenceChip, EvidenceChip, ScoreChip } from '../components/ScoreChip';

export function OpportunitiesPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [filters, setFilters] = useState({
    search: '',
    category: '',
    status: '',
    platform: '',
    minScore: '',
  });
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);

  const projectId = searchParams.get('projectId') ?? '';

  const params = useMemo(() => {
    const query = new URLSearchParams();
    query.set('page', String(page + 1));
    query.set('pageSize', String(pageSize));
    if (projectId) query.set('projectId', projectId);
    if (filters.search) query.set('search', filters.search);
    if (filters.category) query.set('category', filters.category);
    if (filters.status) query.set('status', filters.status);
    if (filters.platform) query.set('platform', filters.platform);
    if (filters.minScore) query.set('minScore', filters.minScore);
    return query;
  }, [filters, page, pageSize, projectId]);

  const { data, isLoading, error } = useOpportunities(params);

  return (
    <Stack spacing={3}>
      <Typography variant="h4">Market opportunities</Typography>

      <Card>
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <TextField
              label="Search"
              size="small"
              value={filters.search}
              onChange={(event) => {
                setPage(0);
                setFilters({ ...filters, search: event.target.value });
              }}
              sx={{ minWidth: 200 }}
            />
            <TextField
              label="Category"
              size="small"
              value={filters.category}
              onChange={(event) => {
                setPage(0);
                setFilters({ ...filters, category: event.target.value });
              }}
              sx={{ minWidth: 160 }}
            />
            <TextField
              select
              label="Status"
              size="small"
              value={filters.status}
              onChange={(event) => {
                setPage(0);
                setFilters({ ...filters, status: event.target.value });
              }}
              sx={{ minWidth: 180 }}
            >
              <MenuItem value="">Any</MenuItem>
              {OPPORTUNITY_STATUSES.map((status) => (
                <MenuItem key={status} value={status}>
                  {status.replace(/_/g, ' ').toLowerCase()}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Platform"
              size="small"
              value={filters.platform}
              onChange={(event) => {
                setPage(0);
                setFilters({ ...filters, platform: event.target.value });
              }}
              sx={{ minWidth: 160 }}
            >
              <MenuItem value="">Any</MenuItem>
              {PLATFORMS.map((platform) => (
                <MenuItem key={platform} value={platform}>
                  {platform}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Min score"
              size="small"
              type="number"
              value={filters.minScore}
              onChange={(event) => {
                setPage(0);
                setFilters({ ...filters, minScore: event.target.value });
              }}
              sx={{ width: 120 }}
              inputProps={{ min: 0, max: 100 }}
            />
          </Stack>
        </CardContent>
      </Card>

      {isLoading && <CircularProgress />}
      {error && <Alert severity="error">Could not load opportunities.</Alert>}

      {data && (
        <Card>
          <CardContent>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Title</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell>Score</TableCell>
                  <TableCell>Confidence</TableCell>
                  <TableCell>Evidence</TableCell>
                  <TableCell>Competition</TableCell>
                  <TableCell>Monetization</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.items.map((opportunity) => (
                  <TableRow
                    key={opportunity.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/opportunities/${opportunity.id}`)}
                  >
                    <TableCell sx={{ maxWidth: 320 }}>{opportunity.title}</TableCell>
                    <TableCell>{opportunity.category}</TableCell>
                    <TableCell>
                      <ScoreChip
                        score={opportunity.finalScore}
                        classification={opportunity.classification}
                      />
                    </TableCell>
                    <TableCell>
                      <ConfidenceChip confidence={opportunity.confidenceScore} />
                    </TableCell>
                    <TableCell>
                      <EvidenceChip evidenceType={opportunity.evidenceType} />
                    </TableCell>
                    <TableCell>
                      {opportunity.competitionScore !== null
                        ? opportunity.competitionScore.toFixed(0)
                        : '—'}
                    </TableCell>
                    <TableCell>
                      {opportunity.monetizationScore !== null
                        ? opportunity.monetizationScore.toFixed(0)
                        : '—'}
                    </TableCell>
                    <TableCell>{opportunity.status.replace(/_/g, ' ').toLowerCase()}</TableCell>
                  </TableRow>
                ))}
                {data.items.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={8}>
                      <Typography variant="body2" color="text.secondary">
                        No opportunity matches these filters.
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={data.total}
              page={page}
              onPageChange={(_event, next) => setPage(next)}
              rowsPerPage={pageSize}
              onRowsPerPageChange={(event) => {
                setPageSize(Number(event.target.value));
                setPage(0);
              }}
              rowsPerPageOptions={[10, 25, 50, 100]}
            />
          </CardContent>
        </Card>
      )}
    </Stack>
  );
}
