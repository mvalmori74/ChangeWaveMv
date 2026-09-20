/**
 * Test di integrazione su Postgres vero.
 *
 * Non su un finto database: il comportamento che stiamo verificando — lock di riga,
 * transazioni concorrenti, assenza di buchi nella sequenza — esiste solo su un
 * motore reale. Un finto passerebbe questi test dicendo nulla.
 *
 * Si saltano da soli se non c'e' un database: DATABASE_URL_TEST non impostata.
 */
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import pg from 'pg';
import { ArchivioPostgres, applicaSchema } from './archivio.js';

const URL = process.env['DATABASE_URL_TEST'];
const descrivi = URL ? describe : describe.skip;

const uuid = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const CAMPAGNA = uuid(1);
const AUTORE = uuid(2);

descrivi('archivio dei messaggi su Postgres', () => {
  let pool: pg.Pool;
  let archivio: ArchivioPostgres;

  beforeAll(async () => {
    pool = new pg.Pool({ connectionString: URL, max: 10 });
    await applicaSchema(pool);
  });

  afterAll(async () => { await pool.end(); });

  beforeEach(async () => {
    await pool.query('TRUNCATE messaggi, membri_campagna, campagne, utenti CASCADE');
    await pool.query('INSERT INTO utenti (id, soprannome) VALUES ($1, $2)', [AUTORE, 'GM']);
    await pool.query('INSERT INTO campagne (id, nome) VALUES ($1, $2)', [CAMPAGNA, 'Prova']);
    archivio = new ArchivioPostgres(pool);
  });

  const msg = (n: number) => ({
    id: uuid(1000 + n), campagnaId: CAMPAGNA, autoreId: AUTORE,
    tipo: 'testo' as const, corpo: { testo: `messaggio ${n}` },
  });

  it('assegna sequenze crescenti a partire da 1', async () => {
    const a = await archivio.aggiungi(msg(1));
    const b = await archivio.aggiungi(msg(2));
    expect(a.seq).toBe(1);
    expect(b.seq).toBe(2);
    expect(a.eraGiaPresente).toBe(false);
  });

  it('e idempotente: reinviare lo stesso messaggio non crea un doppione', async () => {
    const primo = await archivio.aggiungi(msg(1));
    const secondo = await archivio.aggiungi(msg(1));

    expect(secondo.seq).toBe(primo.seq);
    expect(secondo.eraGiaPresente).toBe(true);

    const { rows } = await pool.query('SELECT count(*)::int AS n FROM messaggi');
    expect((rows[0] as { n: number }).n).toBe(1);
  });

  it('un reinvio NON consuma un numero di sequenza', async () => {
    await archivio.aggiungi(msg(1));
    await archivio.aggiungi(msg(1)); // reinvio
    const dopo = await archivio.aggiungi(msg(2));

    // Se il reinvio avesse consumato un numero, qui avremmo 3 e la cronologia
    // avrebbe un buco al posto 2.
    expect(dopo.seq).toBe(2);
    expect(await archivio.ultimoSeq(CAMPAGNA)).toBe(2);
  });

  it('con 50 inserimenti simultanei le sequenze sono uniche, complete e senza buchi', async () => {
    const quanti = 50;
    const esiti = await Promise.all(
      Array.from({ length: quanti }, (_, i) => archivio.aggiungi(msg(i + 1))),
    );

    const sequenze = esiti.map((e) => e.seq).sort((a, b) => a - b);
    expect(new Set(sequenze).size).toBe(quanti);              // nessun doppione
    expect(sequenze).toEqual(Array.from({ length: quanti }, (_, i) => i + 1)); // nessun buco
  });

  it('reinvii simultanei dello stesso messaggio producono una riga sola e nessun buco', async () => {
    // Il caso che rompe l'implementazione ingenua: sei device che ritrasmettono
    // lo stesso messaggio quando la rete torna.
    const esiti = await Promise.all(Array.from({ length: 8 }, () => archivio.aggiungi(msg(1))));

    expect(new Set(esiti.map((e) => e.seq)).size).toBe(1);
    const { rows } = await pool.query('SELECT count(*)::int AS n FROM messaggi');
    expect((rows[0] as { n: number }).n).toBe(1);
    expect(await archivio.ultimoSeq(CAMPAGNA)).toBe(1);
  });

  it('la risincronizzazione da last_seq restituisce esattamente cio che manca', async () => {
    for (let i = 1; i <= 10; i++) await archivio.aggiungi(msg(i));

    const mancanti = await archivio.leggiDa(CAMPAGNA, 7, 100);
    expect(mancanti.map((m) => m.seq)).toEqual([8, 9, 10]);

    const daCapo = await archivio.leggiDa(CAMPAGNA, 0, 100);
    expect(daCapo).toHaveLength(10);
    expect(daCapo[0]?.seq).toBe(1);
  });

  it('la cronologia e ordinata per sequenza, non per ora di inserimento', async () => {
    for (let i = 1; i <= 20; i++) await archivio.aggiungi(msg(i));
    const pagina = await archivio.leggiDa(CAMPAGNA, 0, 100);
    const seq = pagina.map((m) => m.seq);
    expect(seq).toEqual([...seq].sort((a, b) => a - b));
  });

  it('i messaggi cancellati restano in cronologia, per non lasciare buchi', async () => {
    const m = await archivio.aggiungi(msg(1));
    await archivio.aggiungi(msg(2));
    await pool.query('UPDATE messaggi SET cancellato_il = now() WHERE id = $1', [m.id]);

    const pagina = await archivio.leggiDa(CAMPAGNA, 0, 100);
    expect(pagina).toHaveLength(2);
    expect(pagina[0]?.cancellatoIl).not.toBeNull();
  });

  it('rifiuta un limite di pagina assurdo invece di tentare di servirlo', async () => {
    await expect(archivio.leggiDa(CAMPAGNA, 0, 0)).rejects.toThrow(RangeError);
    await expect(archivio.leggiDa(CAMPAGNA, 0, 10_000)).rejects.toThrow(RangeError);
  });

  it('rifiuta un messaggio su una campagna inesistente', async () => {
    await expect(
      archivio.aggiungi({ ...msg(1), campagnaId: uuid(999) }),
    ).rejects.toThrow(/Campagna inesistente/);
  });

  it('campagne diverse hanno sequenze indipendenti', async () => {
    const altra = uuid(3);
    await pool.query('INSERT INTO campagne (id, nome) VALUES ($1, $2)', [altra, 'Altra']);

    await archivio.aggiungi(msg(1));
    const suAltra = await archivio.aggiungi({ ...msg(2), campagnaId: altra });

    expect(suAltra.seq).toBe(1); // riparte da 1: i tavoli non si influenzano
  });
});

/**
 * Nota sperimentale (20/09/2026), tenuta qui perche' e' il genere di cosa che si
 * riscopre a caro prezzo.
 *
 * L'ordine invertito — controllare l'esistenza PRIMA di bloccare la campagna — e'
 * stato provato su questo stesso Postgres con otto reinvii simultanei:
 *   - la sequenza NON si buca: il rollback annulla anche l'incremento del contatore;
 *   - ma cinque reinvii su otto tornano con una violazione di unicita' invece del
 *     messaggio gia' salvato.
 *
 * Per il giocatore la differenza e' tutta: il messaggio e' arrivato, ma il suo
 * telefono dice "invio fallito" e continua a riprovare. Il test
 * "reinvii simultanei ... producono una riga sola e nessun buco" qui sopra e' la
 * guardia contro quella regressione: se qualcuno riordinasse le operazioni,
 * `Promise.all` fallirebbe sulla prima violazione.
 */
