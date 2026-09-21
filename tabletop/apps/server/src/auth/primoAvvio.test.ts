import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import pg from 'pg';
import { applicaSchema } from '../db/archivio.js';
import { primoAvvio } from './primoAvvio.js';

const URL_DB = process.env['DATABASE_URL_TEST'];
const descrivi = URL_DB ? describe : describe.skip;

descrivi('primo avvio', () => {
  let pool: pg.Pool;

  beforeAll(async () => {
    const iniziale = new pg.Pool({ connectionString: URL_DB, max: 1 });
    await iniziale.query('DROP SCHEMA IF EXISTS prova_primo_avvio CASCADE');
    await iniziale.query('CREATE SCHEMA prova_primo_avvio');
    await iniziale.end();
    pool = new pg.Pool({
      connectionString: URL_DB, max: 5, options: '-c search_path=prova_primo_avvio',
    });
    await applicaSchema(pool);
  });
  afterAll(async () => { await pool.end(); });

  beforeEach(async () => {
    await pool.query('TRUNCATE messaggi, membri_campagna, inviti, sessioni, campagne, utenti CASCADE');
  });

  it('su database vuoto crea tavolo e invito, e stampa il codice', async () => {
    const log = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    await primoAvvio(pool, 'Prova');

    const stampato = log.mock.calls.map((c) => String(c[0])).join('\n');
    expect(stampato).toMatch(/PRIMO AVVIO/);
    expect(stampato).toMatch(/[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}/);
    log.mockRestore();

    const inviti = await pool.query('SELECT ruolo FROM inviti');
    expect(inviti.rowCount).toBe(1);
    expect((inviti.rows[0] as { ruolo: string }).ruolo).toBe('gm');
  });

  it('non stampa mai il codice in chiaro nel database', async () => {
    const log = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    await primoAvvio(pool, 'Prova');
    const stampato = log.mock.calls.map((c) => String(c[0])).join('\n');
    const codice = /[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}/.exec(stampato)![0];
    log.mockRestore();

    // Nel database c'è solo l'impronta: chi legge il database non trova inviti usabili.
    const { rows } = await pool.query('SELECT impronta FROM inviti');
    const salvato = (rows[0] as { impronta: string }).impronta;
    expect(salvato).not.toContain(codice.replace(/-/g, ''));
    expect(salvato).toMatch(/^[0-9a-f]{64}$/);
  });

  it('non fa nulla se ci sono gia utenti', async () => {
    const log = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    await primoAvvio(pool, 'Prima');
    await primoAvvio(pool, 'Seconda');
    log.mockRestore();

    const campagne = await pool.query('SELECT count(*)::int AS n FROM campagne');
    expect((campagne.rows[0] as { n: number }).n).toBe(1);
  });
});
