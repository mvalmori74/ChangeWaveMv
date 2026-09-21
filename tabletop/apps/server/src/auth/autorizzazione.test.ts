/**
 * Autorizzazione (F1).
 *
 * Il master prompt lo chiede esplicitamente: "un utente non membro non può leggere
 * nulla del tavolo, verificato da test di autorizzazione lato server, non solo lato
 * UI". Nascondere un pulsante non è sicurezza: è arredamento.
 */
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import pg from 'pg';
import { WebSocket } from 'ws';
import type { AddressInfo } from 'node:net';
import { ArchivioPostgres, applicaSchema } from '../db/archivio.js';
import { creaApplicazione, type Applicazione } from '../app.js';
import { Accessi } from './accessi.js';
import { generaToken, impronta, normalizzaCodice } from './credenziali.js';

const URL_DB = process.env['DATABASE_URL_TEST'];
const descrivi = URL_DB ? describe : describe.skip;

async function poolIsolato(url: string, schema: string): Promise<pg.Pool> {
  const iniziale = new pg.Pool({ connectionString: url, max: 1 });
  await iniziale.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`);
  await iniziale.query(`CREATE SCHEMA ${schema}`);
  await iniziale.end();
  return new pg.Pool({ connectionString: url, max: 10, options: `-c search_path=${schema}` });
}

const uuid = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const TAVOLO = uuid(1);
const TAVOLO_ALTRUI = uuid(9);
const GM = uuid(2);
const ESTRANEO = uuid(3);

descrivi('autorizzazione', () => {
  let pool: pg.Pool;
  let app: Applicazione;
  let accessi: Accessi;
  let base: string;
  let porta: number;
  let tokenGm: string;
  let tokenEstraneo: string;

  beforeAll(async () => {
    pool = await poolIsolato(URL_DB!, 'prova_autorizzazione');
    await applicaSchema(pool);
  });
  afterAll(async () => { await pool.end(); });

  beforeEach(async () => {
    await pool.query(
      'TRUNCATE messaggi, membri_campagna, inviti, sessioni, tentativi_accesso, campagne, utenti CASCADE');
    for (const [id, nome] of [[GM, 'GM'], [ESTRANEO, 'Estraneo']]) {
      await pool.query('INSERT INTO utenti (id, soprannome) VALUES ($1, $2)', [id, nome]);
    }
    for (const [id, nome] of [[TAVOLO, 'Il mio tavolo'], [TAVOLO_ALTRUI, 'Tavolo altrui']]) {
      await pool.query('INSERT INTO campagne (id, nome) VALUES ($1, $2)', [id, nome]);
    }
    await pool.query(
      'INSERT INTO membri_campagna (campagna_id, utente_id, ruolo) VALUES ($1, $2, $3)',
      [TAVOLO, GM, 'gm']);
    // L'estraneo è un utente vero, con una sessione valida: semplicemente non fa
    // parte di questo tavolo. È il caso realistico, non un utente inventato.
    await pool.query(
      'INSERT INTO membri_campagna (campagna_id, utente_id, ruolo) VALUES ($1, $2, $3)',
      [TAVOLO_ALTRUI, ESTRANEO, 'gm']);

    tokenGm = generaToken(); tokenEstraneo = generaToken();
    for (const [t, u] of [[tokenGm, GM], [tokenEstraneo, ESTRANEO]]) {
      await pool.query('INSERT INTO sessioni (impronta, utente_id) VALUES ($1, $2)',
        [impronta(t!), u]);
    }

    accessi = new Accessi(pool);
    app = creaApplicazione(new ArchivioPostgres(pool), accessi);
    await new Promise<void>((r) => app.server.listen(0, '127.0.0.1', r));
    porta = (app.server.address() as AddressInfo).port;
    base = `http://127.0.0.1:${porta}`;
  });
  afterEach(async () => { await app.chiudi(); });

  const con = (token?: string) =>
    token ? { authorization: `Bearer ${token}` } : {};

  const scrivi = (token: string | undefined, autoreId = GM, n = 1) =>
    fetch(`${base}/campagne/${TAVOLO}/messaggi`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', ...con(token) },
      body: JSON.stringify({ id: uuid(500 + n), autoreId, tipo: 'testo', corpo: { testo: 'ciao' } }),
    });

  const leggi = (token?: string) =>
    fetch(`${base}/campagne/${TAVOLO}/messaggi`, { headers: con(token) });

  describe('senza credenziali', () => {
    it('non si legge', async () => expect((await leggi()).status).toBe(401));
    it('non si scrive', async () => expect((await scrivi(undefined)).status).toBe(401));

    it('un token inventato non vale', async () => {
      expect((await leggi(generaToken())).status).toBe(401);
    });

    it('il WebSocket viene chiuso', async () => {
      const ws = new WebSocket(`ws://127.0.0.1:${porta}/realtime?campagna=${TAVOLO}&dopo=0`);
      expect(await new Promise((r) => ws.on('close', r))).toBe(4401);
    });
  });

  describe('un membro di un altro tavolo', () => {
    it('non legge la cronologia', async () => {
      await scrivi(tokenGm);
      expect((await leggi(tokenEstraneo)).status).toBe(404);
    });

    it('non scrive', async () => {
      expect((await scrivi(tokenEstraneo, ESTRANEO)).status).toBe(404);
    });

    it('riceve 404 e non 403, per non sapere che il tavolo esiste', async () => {
      // Rispondere "non sei membro" confermerebbe l'esistenza della campagna a chi
      // prova identificatori a caso. Per un estraneo il tavolo non esiste.
      const r = await leggi(tokenEstraneo);
      expect(r.status).toBe(404);
      expect(JSON.stringify(await r.json())).not.toMatch(/membro|permess|ruolo/i);
    });

    it('non si collega alla diretta', async () => {
      const ws = new WebSocket(`ws://127.0.0.1:${porta}/realtime?campagna=${TAVOLO}&dopo=0`,
        { headers: con(tokenEstraneo) });
      expect(await new Promise((r) => ws.on('close', r))).toBe(4404);
    });

    it('non riceve i messaggi del tavolo nemmeno restando collegato al proprio', async () => {
      const ws = new WebSocket(`ws://127.0.0.1:${porta}/realtime?campagna=${TAVOLO_ALTRUI}&dopo=0`,
        { headers: con(tokenEstraneo) });
      await new Promise((r) => ws.on('open', r));
      const ricevuti: unknown[] = [];
      ws.on('message', (d) => ricevuti.push(d));
      await scrivi(tokenGm);
      await new Promise((r) => setTimeout(r, 250));
      expect(ricevuti).toHaveLength(0);
      ws.close();
    });
  });

  describe('identita', () => {
    it('un membro non puo scrivere a nome di un altro', async () => {
      // Senza questo controllo il GM potrebbe far dire cose a un giocatore.
      const r = await scrivi(tokenGm, ESTRANEO);
      expect(r.status).toBe(403);
    });
  });

  describe('ruoli', () => {
    it('solo il GM crea inviti', async () => {
      await pool.query(
        'INSERT INTO membri_campagna (campagna_id, utente_id, ruolo) VALUES ($1, $2, $3)',
        [TAVOLO, ESTRANEO, 'giocatore']);
      const r = await fetch(`${base}/campagne/${TAVOLO}/inviti`, {
        method: 'POST', headers: { 'content-type': 'application/json', ...con(tokenEstraneo) },
        body: '{}',
      });
      expect(r.status).toBe(403); // qui 403: sa già che il tavolo esiste
    });

    it('il GM crea un invito valido', async () => {
      const r = await fetch(`${base}/campagne/${TAVOLO}/inviti`, {
        method: 'POST', headers: { 'content-type': 'application/json', ...con(tokenGm) },
        body: '{"ruolo":"giocatore"}',
      });
      expect(r.status).toBe(201);
      const { codice } = await r.json() as { codice: string };
      expect(normalizzaCodice(codice)).toHaveLength(12);
    });
  });

  describe('espulsione', () => {
    it('chi viene espulso perde subito l accesso, non alla scadenza del token', async () => {
      await pool.query(
        'INSERT INTO membri_campagna (campagna_id, utente_id, ruolo) VALUES ($1, $2, $3)',
        [TAVOLO, ESTRANEO, 'giocatore']);
      expect((await leggi(tokenEstraneo)).status).toBe(200);

      await accessi.espelli(TAVOLO, ESTRANEO);

      // Non basta toglierlo dai membri: il token va revocato, altrimenti continua a
      // leggere finché non scade.
      expect((await leggi(tokenEstraneo)).status).toBe(401);
    });
  });

  describe('percorso completo dell invito', () => {
    it('un nuovo giocatore entra con il codice e poi scrive', async () => {
      const { codice } = await accessi.creaInvito(TAVOLO, 'giocatore', GM);

      const accesso = await fetch(`${base}/accedi`, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ codice, soprannome: 'Nuovo' }),
      });
      expect(accesso.status).toBe(201);
      const dati = await accesso.json() as { token: string; utente: { id: string } };

      const scritto = await fetch(`${base}/campagne/${TAVOLO}/messaggi`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', ...con(dati.token) },
        body: JSON.stringify({
          id: uuid(777), autoreId: dati.utente.id, tipo: 'testo', corpo: { testo: 'eccomi' },
        }),
      });
      expect(scritto.status).toBe(201);
    });

    it('lo stesso codice non vale due volte', async () => {
      const { codice } = await accessi.creaInvito(TAVOLO, 'giocatore', GM);
      const usa = () => fetch(`${base}/accedi`, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ codice, soprannome: 'Tizio' }),
      });
      expect((await usa()).status).toBe(201);
      expect((await usa()).status).toBe(401);
    });

    it('un codice revocato non vale piu', async () => {
      const { codice } = await accessi.creaInvito(TAVOLO, 'giocatore', GM);
      await accessi.revocaInvitiDi(TAVOLO);
      const r = await fetch(`${base}/accedi`, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ codice, soprannome: 'Tizio' }),
      });
      expect(r.status).toBe(401);
    });

    it('il rifiuto non rivela perche', async () => {
      const r = await fetch(`${base}/accedi`, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ codice: 'ACDE-FGHJ-KMNP', soprannome: 'Tizio' }),
      });
      const corpo = JSON.stringify(await r.json()).toLowerCase();
      for (const parola of ['scadut', 'usat', 'revocat', 'inesist']) {
        expect(corpo).not.toContain(parola);
      }
    });

    it('dopo troppi tentativi falliti si viene fermati', async () => {
      for (let i = 0; i < 10; i++) {
        await fetch(`${base}/accedi`, {
          method: 'POST', headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ codice: 'ACDE-FGHJ-KMNP', soprannome: 'Tizio' }),
        });
      }
      const r = await fetch(`${base}/accedi`, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ codice: 'ACDE-FGHJ-KMNQ', soprannome: 'Tizio' }),
      });
      expect(r.status).toBe(429);
    });
  });
});
