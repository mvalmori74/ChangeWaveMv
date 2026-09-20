/**
 * Coda di invio del client (F2, offline-first).
 *
 * Logica pura: nessun I/O, nessun timer, nessun accesso alla rete. Il chiamante le
 * passa lo stato e l'istante corrente, lei dice cosa fare. Cosi' si puo' provare
 * ogni scenario — rete che va e viene, app chiusa a meta' invio, server irraggiungibile
 * per tre ore — senza aspettare tre ore.
 */
import type { IdMessaggio } from './ids.js';

export type StatoInvio = 'in_coda' | 'in_volo' | 'consegnato' | 'fallito';

export interface VoceOutbox {
  readonly id: IdMessaggio;
  readonly stato: StatoInvio;
  readonly tentativi: number;
  /** Istante del prossimo tentativo utile, in millisecondi epoch. */
  readonly nonPrimaDi: number;
  readonly ultimoErrore?: string;
}

/**
 * Attesa fra un tentativo e il successivo: raddoppia ogni volta, con un tetto.
 *
 * Il tetto non e' un dettaglio: il server sta nel salotto di qualcuno e puo' restare
 * spento per giorni. Senza tetto, dopo una notte l'attesa sarebbe di ore e il
 * messaggio non ripartirebbe nemmeno quando il server torna.
 */
export const ATTESA_BASE_MS = 1_000;
export const ATTESA_MASSIMA_MS = 60_000;
export const TENTATIVI_PRIMA_DI_ARRENDERSI = 8;

export function attesaPerTentativo(tentativi: number): number {
  if (tentativi <= 0) return 0;
  return Math.min(ATTESA_BASE_MS * 2 ** (tentativi - 1), ATTESA_MASSIMA_MS);
}

/** Quali voci sono pronte per un tentativo adesso, in ordine di inserimento. */
export function daInviare(coda: readonly VoceOutbox[], adesso: number): readonly VoceOutbox[] {
  return coda.filter((v) => v.stato === 'in_coda' && v.nonPrimaDi <= adesso);
}

export function segnaInVolo(v: VoceOutbox): VoceOutbox {
  return { ...v, stato: 'in_volo' };
}

/**
 * Esito positivo. Vale anche quando il server risponde "lo avevo gia'": per il
 * mittente la differenza non esiste, il messaggio e' arrivato.
 */
export function segnaConsegnato(v: VoceOutbox): VoceOutbox {
  // L'errore precedente si toglie rimuovendo la chiave, non assegnandole
  // undefined: con exactOptionalPropertyTypes le due cose non coincidono, ed e'
  // proprio la distinzione che vogliamo (assente vs presente e vuoto).
  const { ultimoErrore: _scartato, ...resto } = v;
  return { ...resto, stato: 'consegnato' };
}

/**
 * Esito negativo. `definitivo` distingue i due casi che vanno trattati in modo
 * opposto: un rifiuto del server (messaggio malformato, utente espulso dal tavolo)
 * non migliora riprovando, mentre un guasto di rete migliora quasi sempre.
 */
export function segnaFallito(
  v: VoceOutbox,
  errore: string,
  adesso: number,
  definitivo = false,
): VoceOutbox {
  const tentativi = v.tentativi + 1;
  if (definitivo || tentativi >= TENTATIVI_PRIMA_DI_ARRENDERSI) {
    return { ...v, stato: 'fallito', tentativi, ultimoErrore: errore, nonPrimaDi: adesso };
  }
  return {
    ...v,
    stato: 'in_coda',
    tentativi,
    ultimoErrore: errore,
    nonPrimaDi: adesso + attesaPerTentativo(tentativi),
  };
}

/**
 * Il server e' tornato raggiungibile: si riprova subito, senza aspettare la fine
 * dell'attesa in corso. Le voci gia' arrese restano tali: quelle le rimanda
 * l'utente, con un gesto esplicito.
 */
export function sveglia(coda: readonly VoceOutbox[], adesso: number): readonly VoceOutbox[] {
  return coda.map((v) => (v.stato === 'in_coda' ? { ...v, nonPrimaDi: adesso } : v));
}

/**
 * Ripresa dopo la chiusura dell'app. Le voci rimaste "in volo" sono quelle il cui
 * esito non conosciamo: rimetterle in coda e' sicuro **perche' l'invio e'
 * idempotente sull'id del messaggio**. Se erano gia' arrivate, il server
 * risponde che le aveva gia' e nessuno vede un doppione.
 */
export function riprendiDopoRiavvio(
  coda: readonly VoceOutbox[],
  adesso: number,
): readonly VoceOutbox[] {
  return coda.map((v) =>
    v.stato === 'in_volo' ? { ...v, stato: 'in_coda' as const, nonPrimaDi: adesso } : v,
  );
}

/** Riepilogo per la barra di stato in chat. */
export function riepilogo(coda: readonly VoceOutbox[]): {
  inAttesa: number; falliti: number; tuttoConsegnato: boolean;
} {
  const inAttesa = coda.filter((v) => v.stato === 'in_coda' || v.stato === 'in_volo').length;
  const falliti = coda.filter((v) => v.stato === 'fallito').length;
  return { inAttesa, falliti, tuttoConsegnato: inAttesa === 0 && falliti === 0 };
}
