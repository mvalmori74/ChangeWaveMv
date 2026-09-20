/**
 * Prova che le regole di confine architetturale scattino davvero
 * (criterio di accettazione di S0-01).
 *
 * Una regola di lint che nessuno verifica e' una regola che un giorno verra'
 * disattivata "temporaneamente" senza che nessuno se ne accorga. Qui la si mette
 * alla prova come si mette alla prova il codice.
 */
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

const qui = path.dirname(fileURLToPath(import.meta.url));
const radice = path.resolve(qui, '../../..');
const eslint = new ESLint({ cwd: radice });

async function erroriSu(codice: string, percorsoRelativo: string): Promise<string[]> {
  const [esito] = await eslint.lintText(codice, {
    filePath: path.join(radice, percorsoRelativo),
    warnIgnored: false,
  });
  return (esito?.messages ?? []).map((m) => m.message);
}

describe('confini fra feature', () => {
  it('vieta a una feature di importare da un altra feature', async () => {
    const errori = await erroriSu(
      `import { Dado } from '../../dadi/ui/Dado';\nexport const x = Dado;\n`,
      'apps/mobile/src/features/chat/ui/Cattivo.ts',
    );
    expect(errori.join('\n')).toMatch(/Import fra feature vietato/);
  });

  it('vieta anche la forma con alias, non solo quella relativa', async () => {
    const errori = await erroriSu(
      `import { Dado } from '@/features/dadi/ui/Dado';\nexport const x = Dado;\n`,
      'apps/mobile/src/features/chat/ui/Cattivo.ts',
    );
    expect(errori.join('\n')).toMatch(/Import fra feature vietato/);
  });

  it('consente un import interno alla stessa feature', async () => {
    // '../modello/x' non esce dalla feature: deve restare lecito, altrimenti la
    // regola sarebbe cosi' severa da spingere ad aggirarla.
    const errori = await erroriSu(
      `import { x } from '../modello/cose';\nexport const y = x;\n`,
      'apps/mobile/src/features/chat/ui/Buono.ts',
    );
    expect(errori.join('\n')).not.toMatch(/Import fra feature vietato/);
  });

  it('consente a una feature di importare dal dominio condiviso', async () => {
    const errori = await erroriSu(
      `import type { Messaggio } from '@tabletop/shared';\nexport type M = Messaggio;\n`,
      'apps/mobile/src/features/chat/ui/Buono.ts',
    );
    expect(errori.join('\n')).not.toMatch(/Import fra feature vietato/);
  });
});

describe('purezza del dominio condiviso', () => {
  it('vieta React dentro packages/shared', async () => {
    const errori = await erroriSu(
      `import { useState } from 'react';\nexport const x = useState;\n`,
      'packages/shared/src/cattivo.ts',
    );
    expect(errori.join('\n')).toMatch(/dominio puro: niente React/);
  });

  it('vieta gli SDK di infrastruttura dentro packages/shared', async () => {
    const errori = await erroriSu(
      `import pg from 'pg';\nexport const x = pg;\n`,
      'packages/shared/src/cattivo.ts',
    );
    expect(errori.join('\n')).toMatch(/niente SDK di infrastruttura/);
  });

  it('non applica il divieto fuori da packages/shared', async () => {
    const errori = await erroriSu(
      `import pg from 'pg';\nexport const x = pg;\n`,
      'apps/server/src/consentito.ts',
    );
    expect(errori.join('\n')).not.toMatch(/niente SDK di infrastruttura/);
  });
});
