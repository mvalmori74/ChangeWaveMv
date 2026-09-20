import type { IdCampagna, Messaggio, SeqCanale } from '@tabletop/shared';

/**
 * Confine verso la persistenza. Esiste perche' il resto del codice non sappia mai
 * COME i dati sono memorizzati: oggi Postgres su un PC di casa, domani altro.
 * Nessuna implementazione qui dentro: solo il contratto.
 */
export interface ArchivioMessaggi {
  /**
   * Inserisce un messaggio assegnandogli la sequenza di canale.
   * DEVE essere idempotente sull'id del messaggio: un reinvio dopo un timeout di
   * rete non crea un doppione (F2, outbox).
   */
  aggiungi(m: Messaggio): Promise<Messaggio & { seq: SeqCanale }>;

  /** Cronologia a finestre, ordinata per sequenza crescente. */
  leggiDa(
    campagna: IdCampagna,
    dopoSeq: SeqCanale | null,
    limite: number,
  ): Promise<readonly (Messaggio & { seq: SeqCanale })[]>;
}

export interface StatoSistema {
  readonly versione: string;
  readonly discoLiberoByte: number;
  readonly discoTotaleByte: number;
  readonly ultimoBackup: { readonly quando: string; readonly esito: 'ok' | 'fallito' } | null;
  readonly codaVocaleInAttesa: number;
}
