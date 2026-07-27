import { buildApp } from './app.js';
import { loadConfig } from './config/env.js';
import { loadDotEnv } from './config/load-dotenv.js';
import { createContainer } from './core/container.js';
import { toErrorMessage } from './core/errors.js';
import { syncAgentRegistry } from './persistence/agent-sync.js';

async function main(): Promise<void> {
  loadDotEnv();
  const config = loadConfig();
  const container = createContainer(config);
  const app = await buildApp(config, container);

  // The registry is the source of truth in code; the table is its projection,
  // so the two are reconciled at every boot.
  await syncAgentRegistry(container.prisma, container.registry, container.logger);

  if (config.BOOTSTRAP_ADMIN_EMAIL && config.BOOTSTRAP_ADMIN_PASSWORD) {
    await container.services.auth.ensureBootstrapAdmin(
      config.BOOTSTRAP_ADMIN_EMAIL,
      config.BOOTSTRAP_ADMIN_PASSWORD,
    );
  }

  await app.listen({ port: config.API_PORT, host: config.API_HOST });
  container.logger.info(
    {
      port: config.API_PORT,
      llm: container.llm.name,
      search: container.search.name,
      queue: container.queue.name,
    },
    'AI App Factory API started',
  );

  const shutdown = async (signal: string): Promise<void> => {
    container.logger.info({ signal }, 'shutting down');
    try {
      await app.close();
      await container.shutdown();
      process.exit(0);
    } catch (error) {
      container.logger.error({ error: toErrorMessage(error) }, 'shutdown failed');
      process.exit(1);
    }
  };

  process.on('SIGTERM', () => void shutdown('SIGTERM'));
  process.on('SIGINT', () => void shutdown('SIGINT'));
}

main().catch((error: unknown) => {
  // The logger may not exist yet if configuration failed, so this one goes to
  // stderr directly.
  console.error('Fatal startup error:', toErrorMessage(error));
  process.exit(1);
});
