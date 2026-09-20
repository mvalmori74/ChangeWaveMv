import type { IdCampagna, IdMessaggio, IdUtente, SeqCanale } from './ids.js';

/** Stato di consegna visibile in chat (F2). */
export type StatoConsegna = 'in_coda' | 'inviato' | 'consegnato' | 'fallito';

export type TipoMessaggio = 'testo' | 'vocale' | 'tiro' | 'allegato' | 'sistema';

export interface MessaggioBase {
  readonly id: IdMessaggio;
  readonly campagna: IdCampagna;
  readonly autore: IdUtente;
  /** Assente finche' il server non lo assegna: un messaggio in coda non ha posizione. */
  readonly seq?: SeqCanale;
  readonly creatoIl: string;
  readonly modificatoIl?: string;
  readonly cancellatoIl?: string;
  readonly rispostaA?: IdMessaggio;
}

export interface MessaggioTesto extends MessaggioBase {
  readonly tipo: 'testo';
  readonly testo: string;
}

/** Stato della pipeline vocale (F3). La trascrizione arriva dopo, non blocca la UI. */
export type StatoTrascrizione =
  | 'in_attesa'
  | 'in_corso'
  | 'pronta'
  | 'corretta_a_mano'
  | 'non_disponibile';

export interface MessaggioVocale extends MessaggioBase {
  readonly tipo: 'vocale';
  readonly uriAudio: string;
  readonly durataMs: number;
  readonly statoTrascrizione: StatoTrascrizione;
  readonly trascrizione?: string;
  /** Quale motore ha prodotto la trascrizione: serve per il benchmark e per l'utente. */
  readonly motore?: 'server' | 'device';
  readonly presetVoce?: string;
  readonly uriAudioTrasformato?: string;
}

export type Messaggio = MessaggioTesto | MessaggioVocale;

/** Un messaggio e' visibile in cronologia solo se il server gli ha dato una posizione. */
export function haPosizione(
  m: Messaggio,
): m is Messaggio & { seq: SeqCanale } {
  return m.seq !== undefined;
}

export function eCancellato(m: Messaggio): boolean {
  return m.cancellatoIl !== undefined;
}
