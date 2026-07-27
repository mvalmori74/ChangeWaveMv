/** Application error hierarchy. Everything thrown on purpose extends AppError. */
export class AppError extends Error {
  constructor(
    message: string,
    readonly statusCode: number,
    readonly code: string,
    readonly details?: unknown,
  ) {
    super(message);
    this.name = new.target.name;
    Error.captureStackTrace?.(this, new.target);
  }
}

export class ValidationError extends AppError {
  constructor(message: string, details?: unknown) {
    super(message, 400, 'VALIDATION_ERROR', details);
  }
}

export class UnauthorizedError extends AppError {
  constructor(message = 'Authentication required') {
    super(message, 401, 'UNAUTHORIZED');
  }
}

export class ForbiddenError extends AppError {
  constructor(message = 'Not allowed') {
    super(message, 403, 'FORBIDDEN');
  }
}

export class NotFoundError extends AppError {
  constructor(entity: string, id?: string) {
    super(id ? `${entity} '${id}' not found` : `${entity} not found`, 404, 'NOT_FOUND');
  }
}

export class ConflictError extends AppError {
  constructor(message: string, details?: unknown) {
    super(message, 409, 'CONFLICT', details);
  }
}

/** A run hit its USD or token ceiling. Never retried automatically. */
export class BudgetExceededError extends AppError {
  constructor(message: string, details?: unknown) {
    super(message, 402, 'BUDGET_EXCEEDED', details);
  }
}

/** An external provider (LLM, search) failed or returned unusable output. */
export class ProviderError extends AppError {
  constructor(
    message: string,
    readonly provider: string,
    details?: unknown,
  ) {
    super(message, 502, 'PROVIDER_ERROR', details);
  }
}

export class AgentExecutionError extends AppError {
  constructor(
    message: string,
    readonly agentKey: string,
    details?: unknown,
  ) {
    super(message, 500, 'AGENT_EXECUTION_ERROR', details);
  }
}

export function isAppError(error: unknown): error is AppError {
  return error instanceof AppError;
}

export function toErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === 'string') return error;
  return 'Unknown error';
}
