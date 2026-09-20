/**
 * Identificatori e ordinamento.
 *
 * Master prompt F2: l'ordinamento dei messaggi e' deterministico e stabile, dato da
 * una sequenza monotona assegnata dal SERVER. Il timestamp del client non ordina
 * nulla: orologi sfasati, fuso orario, ritardi di rete. Qui stanno i tipi che
 * rendono l'errore impossibile da scrivere per distrazione.
 */

/** Sequenza monotona per canale, assegnata dal server. Mai dal client. */
export type SeqCanale = number & { readonly __brand: 'SeqCanale' };

/** Identificatore generato dal client, usato anche come chiave di idempotenza. */
export type IdMessaggio = string & { readonly __brand: 'IdMessaggio' };

export type IdCampagna = string & { readonly __brand: 'IdCampagna' };
export type IdUtente = string & { readonly __brand: 'IdUtente' };

export function seqCanale(n: number): SeqCanale {
  if (!Number.isInteger(n) || n < 0) {
    throw new RangeError(`Sequenza di canale non valida: ${n}`);
  }
  return n as SeqCanale;
}

const RE_ID = /^[0-9a-z]{8}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{12}$/i;

export function idMessaggio(s: string): IdMessaggio {
  if (!RE_ID.test(s)) throw new TypeError(`Id messaggio non valido: ${s}`);
  return s as IdMessaggio;
}

/**
 * Ordina per sequenza di server. Confronto totale e stabile: a parita' di sequenza
 * (che non dovrebbe accadere) si ricade sull'id, cosi' l'ordine resta deterministico
 * su tutti i device invece di dipendere dall'algoritmo di sort della piattaforma.
 */
export function confrontaPerSeq(
  a: { seq: SeqCanale; id: IdMessaggio },
  b: { seq: SeqCanale; id: IdMessaggio },
): number {
  if (a.seq !== b.seq) return a.seq - b.seq;
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}
