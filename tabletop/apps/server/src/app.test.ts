/**
 * Prova end-to-end: server HTTP vero, WebSocket vero, Postgres vero.
 *
 * Questi test attraversano tutto il percorso che attraversera' un messaggio
 * mandato da un telefono. Si saltano da soli senza DATABASE_URL_TEST.
 */
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import pg from 'pg';
import { WebSocket } from 'ws';
import type { AddressInfo } from 'node:net';
import { ArchivioPostgres, applicaSchema } from './db/archivio.js';
import { Accessi } from './auth/accessi.js';
import { generaToken, impronta } from './auth/credenziali.js';
import { creaApplicazione, type Applicazione } from './app.js';

const URL_DB = process.env['DATABASE_URL_TEST'];
const descrivi = URL_DB ? describe : describe.skip;

const uuid = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const CAMPAGNA = uuid(1);
const AUTORE = uuid(2);

/**
 * Ogni file di test lavora in uno schema Postgres suo.
 *
 * Vitest esegue i file in parallelo: senza isolamento due file che azzerano le
 * stesse tabelle si cancellano i dati a vicenda, e i test falliscono solo
 * nell'esecuzione completa — il tipo di guasto che fa perdere un pomeriggio perche'
 * ogni file, provato da solo, passa.
 */
async function poolIsolato(url: string, schema: string): Promise<pg.Pool> {
  const iniziale = new pg.Pool({ connectionString: url, max: 1 });
  await iniziale.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`);
  await iniziale.query(`CREATE SCHEMA ${schema}`);
  await iniziale.end();
  return new pg.Pool({
    connectionString: url,
    max: 10,
    options: `-c search_path=${schema}`,
  });
}

descrivi('API del tavolo, end-to-end', () => {
  let pool: pg.Pool;
  let app: Applicazione;
  let accessi: Accessi;
  let base: string;
  let token: string;

  beforeAll(async () => {
    pool = await poolIsolato(URL_DB!, 'prova_api');
    await applicaSchema(pool);
  });

  afterAll(async () => { await pool.end(); });

  beforeEach(async () => {
    await pool.query('TRUNCATE messaggi, membri_campagna, campagne, utenti CASCADE');
    await pool.query('INSERT INTO utenti (id, soprannome) VALUES ($1, $2)', [AUTORE, 'GM']);
    await pool.query('INSERT INTO campagne (id, nome) VALUES ($1, $2)', [CAMPAGNA, 'Prova']);
    accessi = new Accessi(pool);
    app = creaApplicazione(new ArchivioPostgres(pool), accessi);

    // Sessione del GM creata direttamente: il percorso dell'invito ha i suoi test.
    token = generaToken();
    await pool.query(
      'INSERT INTO sessioni (impronta, utente_id) VALUES ($1, $2)',
      [impronta(token), AUTORE],
    );
    await pool.query(
      'INSERT INTO membri_campagna (campagna_id, utente_id, ruolo) VALUES ($1, $2, $3)',
      [CAMPAGNA, AUTORE, 'gm'],
    );
    await new Promise<void>((r) => app.server.listen(0, '127.0.0.1', r));
    base = `http://127.0.0.1:${(app.server.address() as AddressInfo).port}`;
  });

  afterEach(async () => { await app.chiudi(); });

  const manda = (n: number, extra: Record<string, unknown> = {}) =>
    fetch(`${base}/campagne/${CAMPAGNA}/messaggi`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
      body: JSON.stringify({
        id: uuid(1000 + n), autoreId: AUTORE, tipo: 'testo',
        corpo: { testo: `messaggio ${n}` }, ...extra,
      }),
    });

  it('accetta un messaggio e gli assegna la sequenza', async () => {
    const r = await manda(1);
    expect(r.status).toBe(201);
    expect(await r.json()).toMatchObject({ seq: 1, eraGiaPresente: false });
  });

  it('un reinvio risponde 200 e non 201, e non crea un doppione', async () => {
    await manda(1);
    const r = await manda(1);
    expect(r.status).toBe(200);
    expect(await r.json()).toMatchObject({ seq: 1, eraGiaPresente: true });

    const cronologia = await (await fetch(`${base}/campagne/${CAMPAGNA}/messaggi`,
      { headers: { authorization: `Bearer ${token}` } })).json();
    expect(cronologia).toHaveLength(1);
  });

  it('rifiuta un corpo malformato con 400 e non con 500', async () => {
    const r = await fetch(`${base}/campagne/${CAMPAGNA}/messaggi`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
      body: '{non json',
    });
    expect(r.status).toBe(400);
  });

  it('rifiuta un tipo di messaggio sconosciuto', async () => {
    const r = await manda(1, { tipo: 'incantesimo' });
    expect(r.status).toBeGreaterThanOrEqual(400);
    expect(r.status).toBeLessThan(500);
  });

  it('la cronologia si legge a pagine, in ordine di sequenza', async () => {
    for (let i = 1; i <= 5; i++) await manda(i);
    const pagina = await (await fetch(`${base}/campagne/${CAMPAGNA}/messaggi?dopo=2&limite=2`,
      { headers: { authorization: `Bearer ${token}` } })).json() as { seq: number }[];
    expect(pagina.map((m) => m.seq)).toEqual([3, 4]);
  });

  it('rifiuta un limite di pagina fuori scala', async () => {
    const r = await fetch(`${base}/campagne/${CAMPAGNA}/messaggi?limite=99999`,
      { headers: { authorization: `Bearer ${token}` } });
    expect(r.status).toBeGreaterThanOrEqual(400);
  });

  describe('WebSocket', () => {
    const apriWs = (dopo: number) => {
      const porta = (app.server.address() as AddressInfo).port;
      return new WebSocket(`ws://127.0.0.1:${porta}/realtime?campagna=${CAMPAGNA}&dopo=${dopo}`,
        { headers: { authorization: `Bearer ${token}` } });
    };

    const raccogli = (ws: WebSocket, quanti: number, entroMs = 3000) =>
      new Promise<number[]>((risolvi, rifiuta) => {
        const seq: number[] = [];
        const scadenza = setTimeout(
          () => rifiuta(new Error(`ricevute ${seq.length} di ${quanti}: ${seq.join(',')}`)),
          entroMs,
        );
        ws.on('message', (d) => {
          seq.push((JSON.parse(String(d)) as { messaggio: { seq: number } }).messaggio.seq);
          if (seq.length === quanti) { clearTimeout(scadenza); risolvi(seq); }
        });
      });

    it('consegna in diretta i messaggi nuovi', async () => {
      const ws = apriWs(0);
      await new Promise((r) => ws.on('open', r));
      const attesi = raccogli(ws, 2);
      await manda(1);
      await manda(2);
      expect(await attesi).toEqual([1, 2]);
      ws.close();
    });

    it('al ricollegamento recupera solo cio che manca', async () => {
      for (let i = 1; i <= 4; i++) await manda(i);
      const ws = apriWs(2);              // il client era fermo al 2
      const recuperati = raccogli(ws, 2);
      expect(await recuperati).toEqual([3, 4]);
      ws.close();
    });

    it('recupero e diretta insieme: nessuna perdita, nessun doppione', async () => {
      for (let i = 1; i <= 3; i++) await manda(i);
      const ws = apriWs(0);
      const tutti = raccogli(ws, 5);
      await new Promise((r) => ws.on('open', r));
      await manda(4);                     // arrivano mentre il client si allinea
      await manda(5);
      const seq = await tutti;
      expect(seq).toEqual([1, 2, 3, 4, 5]);
      expect(new Set(seq).size).toBe(5);
      ws.close();
    });

    it('un reinvio non viene ritrasmesso agli altri giocatori', async () => {
      const ws = apriWs(0);
      await new Promise((r) => ws.on('open', r));
      const ricevuti: number[] = [];
      ws.on('message', (d) => {
        ricevuti.push((JSON.parse(String(d)) as { messaggio: { seq: number } }).messaggio.seq);
      });
      await manda(1);
      await manda(1); // reinvio dello stesso id
      await new Promise((r) => setTimeout(r, 200));
      expect(ricevuti).toEqual([1]);
      ws.close();
    });

    it('chiude la connessione se i parametri non sono validi', async () => {
      const porta = (app.server.address() as AddressInfo).port;
      const ws = new WebSocket(`ws://127.0.0.1:${porta}/realtime?campagna=non-un-uuid`,
        { headers: { authorization: `Bearer ${token}` } });
      const codice = await new Promise<number>((r) => ws.on('close', r));
      expect(codice).toBe(4000);
    });
  });
});

describe('classificazione degli errori', () => {
  it('distingue la colpa del client da quella del server', async () => {
    const { classificaErrore, ErroreHttp } = await import('./app.js');
    const { z } = await import('zod');

    expect(classificaErrore(new ErroreHttp(413, 'troppo grande')).codice).toBe(413);
    expect(classificaErrore(new RangeError('limite assurdo')).codice).toBe(400);
    expect(classificaErrore(new Error('Campagna inesistente: x')).codice).toBe(404);
    expect(classificaErrore(new Error('boom')).codice).toBe(500);

    const zodErr = z.object({ a: z.string() }).safeParse({ a: 1 });
    expect(zodErr.success).toBe(false);
    if (!zodErr.success) {
      const c = classificaErrore(zodErr.error);
      expect(c.codice).toBe(400);
      expect(c.messaggio).toMatch(/richiesta non valida/);
    }
  });

  it('non rivela dettagli interni in un errore 500', async () => {
    const { classificaErrore } = await import('./app.js');
    const dentro = new Error('connessione a postgres://utente:segreto@host fallita');
    expect(classificaErrore(dentro).messaggio).toBe('errore interno');
  });
});
