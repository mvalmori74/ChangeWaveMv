import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import type { Pool, PoolClient } from 'pg';

/**
 * Archivio dei messaggi su Postgres (F2).
 *
 * Il nodo di tutto lo Sprint 1 sta in `aggiungi`: assegnare una sequenza di canale
 * che sia insieme monotona, priva di buchi e idempotente sull'id del client, anche
 * quando sei persone scrivono nello stesso istante.
 */

export interface MessaggioDaInserire {
  readonly id: string;
  readonly campagnaId: string;
  readonly autoreId: string;
  readonly tipo: 'testo' | 'vocale' | 'tiro' | 'allegato' | 'sistema';
  readonly corpo: unknown;
  readonly rispostaA?: string;
}

export interface MessaggioSalvato extends MessaggioDaInserire {
  readonly seq: number;
  readonly creatoIl: Date;
  readonly cancellatoIl: Date | null;
  /** true se il messaggio esisteva gia': un reinvio, non un nuovo messaggio. */
  readonly eraGiaPresente: boolean;
}

const QUI = path.dirname(fileURLToPath(import.meta.url));

export async function applicaSchema(pool: Pool): Promise<void> {
  const sql = await readFile(path.join(QUI, 'schema.sql'), 'utf8');
  await pool.query(sql);
}

function daRiga(r: Record<string, unknown>, eraGiaPresente: boolean): MessaggioSalvato {
  return {
    id: r['id'] as string,
    campagnaId: r['campagna_id'] as string,
    autoreId: r['autore_id'] as string,
    tipo: r['tipo'] as MessaggioSalvato['tipo'],
    corpo: r['corpo'],
    ...(r['risposta_a'] ? { rispostaA: r['risposta_a'] as string } : {}),
    seq: Number(r['seq']),
    creatoIl: r['creato_il'] as Date,
    cancellatoIl: (r['cancellato_il'] as Date | null) ?? null,
    eraGiaPresente,
  };
}

export class ArchivioPostgres {
  constructor(private readonly pool: Pool) {}

  /**
   * Inserisce un messaggio assegnandogli la sequenza di canale.
   *
   * L'ordine delle operazioni non e' negoziabile, ed e' il motivo per cui questo
   * metodo esiste invece di una semplice INSERT:
   *
   *   1. si blocca la riga della campagna (SELECT ... FOR UPDATE);
   *   2. SOLO DOPO si controlla se l'id esiste gia';
   *   3. se non esiste, si incrementa il contatore e si inserisce.
   *
   * Invertire 1 e 2 sembra piu' naturale e rompe l'idempotenza. Verificato
   * sperimentalmente su Postgres 16 con otto reinvii simultanei dello stesso
   * messaggio: con l'ordine invertito passano tutti il controllo, uno vince e
   * **cinque su otto tornano al client con una violazione di unicita'**. Il
   * messaggio e' stato consegnato, ma chi l'ha mandato vede "invio fallito" e
   * riprova all'infinito.
   *
   * Nota per chi legge: la sequenza NON si buca in nessuno dei due ordini, perche'
   * il rollback della transazione annulla anche l'incremento del contatore. Il
   * motivo per bloccare prima e' l'idempotenza, non i buchi.
   */
  async aggiungi(m: MessaggioDaInserire): Promise<MessaggioSalvato> {
    const client: PoolClient = await this.pool.connect();
    try {
      await client.query('BEGIN');

      // 1. Lock sulla singola campagna: due tavoli diversi non si ostacolano.
      const campagna = await client.query(
        'SELECT id FROM campagne WHERE id = $1 FOR UPDATE',
        [m.campagnaId],
      );
      if (campagna.rowCount === 0) {
        throw new Error(`Campagna inesistente: ${m.campagnaId}`);
      }

      // 2. Con il lock in mano: il messaggio esiste gia'?
      const esistente = await client.query('SELECT * FROM messaggi WHERE id = $1', [m.id]);
      const riga = esistente.rows[0];
      if (riga !== undefined) {
        await client.query('COMMIT');
        return daRiga(riga as Record<string, unknown>, true);
      }

      // 3. Solo ora si consuma un numero di sequenza.
      const { rows } = await client.query(
        'UPDATE campagne SET ultimo_seq = ultimo_seq + 1 WHERE id = $1 RETURNING ultimo_seq',
        [m.campagnaId],
      );
      const seq = Number((rows[0] as { ultimo_seq: string }).ultimo_seq);

      const inserito = await client.query(
        `INSERT INTO messaggi (id, campagna_id, autore_id, seq, tipo, corpo, risposta_a)
         VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING *`,
        [m.id, m.campagnaId, m.autoreId, seq, m.tipo, JSON.stringify(m.corpo), m.rispostaA ?? null],
      );
      await client.query('COMMIT');
      return daRiga(inserito.rows[0] as Record<string, unknown>, false);
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }

  /**
   * Cronologia a finestre, per la risincronizzazione da `dopoSeq` e per lo
   * scorrimento all'indietro. Include i messaggi cancellati, con la loro data di
   * cancellazione: il client deve poter mostrare "messaggio rimosso" invece di
   * trovare un salto nei numeri.
   */
  async leggiDa(campagnaId: string, dopoSeq: number, limite: number): Promise<MessaggioSalvato[]> {
    if (!Number.isInteger(limite) || limite <= 0 || limite > 500) {
      throw new RangeError(`Limite non valido: ${limite}`);
    }
    const { rows } = await this.pool.query(
      `SELECT * FROM messaggi
        WHERE campagna_id = $1 AND seq > $2
        ORDER BY seq ASC
        LIMIT $3`,
      [campagnaId, dopoSeq, limite],
    );
    return (rows as Record<string, unknown>[]).map((r) => daRiga(r, false));
  }

  async ultimoSeq(campagnaId: string): Promise<number> {
    const { rows } = await this.pool.query(
      'SELECT ultimo_seq FROM campagne WHERE id = $1',
      [campagnaId],
    );
    const r = rows[0] as { ultimo_seq: string } | undefined;
    if (r === undefined) throw new Error(`Campagna inesistente: ${campagnaId}`);
    return Number(r.ultimo_seq);
  }
}
