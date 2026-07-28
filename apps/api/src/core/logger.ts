import { pino, type Logger } from 'pino';

export type AppLogger = Logger;

/**
 * Structured JSON logging with redaction of anything that could carry a secret.
 * Agent prompts and raw outputs are only logged at debug level.
 */
export function createLogger(level: string): AppLogger {
  return pino({
    level,
    redact: {
      paths: [
        'req.headers.authorization',
        'req.headers.cookie',
        'password',
        '*.password',
        'apiKey',
        '*.apiKey',
        'OPENAI_API_KEY',
        'ANTHROPIC_API_KEY',
        'TAVILY_API_KEY',
        'JWT_SECRET',
      ],
      censor: '[redacted]',
    },
  });
}
