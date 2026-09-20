/**
 * Rilevazione delle caratteristiche della macchina. Separata da `diagnosi.ts`
 * perche' qui c'e' l'accesso al sistema, li' solo le decisioni.
 */
import { cpus, platform, totalmem } from 'node:os';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import type { Macchina } from './diagnosi.js';

const esegui = promisify(execFile);

async function rilevaGpu(): Promise<string | null> {
  try {
    const { stdout } = await esegui(
      'nvidia-smi',
      ['--query-gpu=name', '--format=csv,noheader'],
      { timeout: 5000 },
    );
    return stdout.trim().split('\n')[0]?.trim() || null;
  } catch {
    // Assenza di nvidia-smi non significa assenza di GPU: significa che non
    // sappiamo. La differenza conta, e il tipo la conserva.
    return null;
  }
}

export async function rilevaMacchina(): Promise<Macchina> {
  return {
    coreLogici: cpus().length,
    ramGb: Math.round((totalmem() / 1024 ** 3) * 10) / 10,
    gpu: await rilevaGpu(),
    piattaforma: platform(),
  };
}
