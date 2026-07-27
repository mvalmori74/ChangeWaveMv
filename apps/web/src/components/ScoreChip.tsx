import { Chip, Tooltip } from '@mui/material';
import { classifyScore, EVIDENCE_TYPE_LABELS, type EvidenceType } from '@aiaf/shared';

const CLASS_COLOR: Record<string, 'default' | 'error' | 'warning' | 'info' | 'success'> = {
  KILL: 'error',
  LOW_POTENTIAL: 'warning',
  PROMISING: 'info',
  HIGH_POTENTIAL: 'success',
  EXCEPTIONAL: 'success',
};

export function ScoreChip({
  score,
  classification,
}: {
  score: number | null;
  classification?: string | null;
}) {
  if (score === null) {
    return <Chip size="small" label="not scored" variant="outlined" />;
  }
  const klass = classification ?? classifyScore(score);
  return (
    <Chip
      size="small"
      color={CLASS_COLOR[klass] ?? 'default'}
      label={`${score.toFixed(0)} · ${klass.replace(/_/g, ' ').toLowerCase()}`}
    />
  );
}

/** Confidence is shown next to every score: a number without it is misleading. */
export function ConfidenceChip({ confidence }: { confidence: number | null }) {
  if (confidence === null) return <Chip size="small" label="confidence n/a" variant="outlined" />;
  const percent = Math.round(confidence * 100);
  return (
    <Tooltip title="How much evidential support the score has">
      <Chip
        size="small"
        variant="outlined"
        color={percent >= 60 ? 'success' : percent >= 30 ? 'warning' : 'error'}
        label={`confidence ${percent}%`}
      />
    </Tooltip>
  );
}

/** Makes the verified / inferred / estimated / guessed distinction visible. */
export function EvidenceChip({ evidenceType }: { evidenceType: string }) {
  const label = EVIDENCE_TYPE_LABELS[evidenceType as EvidenceType] ?? evidenceType;
  const color =
    evidenceType === 'VERIFIED'
      ? 'success'
      : evidenceType === 'INFERENCE'
        ? 'info'
        : evidenceType === 'ESTIMATE'
          ? 'warning'
          : 'error';
  return (
    <Tooltip title={label}>
      <Chip size="small" variant="outlined" color={color} label={evidenceType.toLowerCase()} />
    </Tooltip>
  );
}
