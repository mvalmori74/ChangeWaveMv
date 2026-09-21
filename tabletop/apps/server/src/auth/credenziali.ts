/**
 * Generazione e verifica di codici di invito e token di dispositivo (ADR-009).
 *
 * Logica pura, senza database: cosi' le proprieta' che contano — entropia, forma del
 * codice, impossibilita' di risalire dal database alle credenziali — si verificano
 * senza infrastruttura.
 */
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

/**
 * Alfabeto senza caratteri ambigui: niente 0/O, 1/I/L, 2/Z, 5/S, 8/B.
 * Il codice viene dettato a voce o ricopiato da un messaggio, e un carattere
 * frainteso significa un tentativo bruciato e una persona irritata.
 */
const ALFABETO = 'ACDEFGHJKMNPQRTUVWXY34679';

/** 12 caratteri su 25 simboli danno circa 55 bit: non indovinabile. */
export const LUNGHEZZA_CODICE = 12;

export function generaCodiceInvito(): string {
  // randomBytes e non Math.random: la seconda e' prevedibile e qui sarebbe un buco.
  const byte = randomBytes(LUNGHEZZA_CODICE * 4);
  let codice = '';
  const limite = 256 - (256 % ALFABETO.length);
  for (let i = 0; i < byte.length && codice.length < LUNGHEZZA_CODICE; i++) {
    const b = byte[i]!;
    if (b >= limite) continue; // scarta i valori che introdurrebbero bias
    codice += ALFABETO[b % ALFABETO.length];
  }
  // A gruppi di quattro: piu' leggibile da dettare e da ricopiare.
  return (codice.match(/.{1,4}/g) ?? []).join('-');
}

/** Tollerante su come l'utente lo digita: minuscole, spazi, trattini mancanti. */
export function normalizzaCodice(codice: string): string {
  return codice.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

export function generaToken(): string {
  return randomBytes(32).toString('base64url');
}

/**
 * Impronta per la conservazione. Un hash veloce basta: il segreto ha gia' molta
 * entropia, non c'e' nulla da indovinare con un dizionario, e un algoritmo lento
 * servirebbe solo a rallentare ogni richiesta.
 */
export function impronta(segreto: string): string {
  return createHash('sha256').update(segreto).digest('hex');
}

/** Confronto a tempo costante, per non far trapelare informazioni dai tempi. */
export function improntaCorrisponde(a: string, b: string): boolean {
  if (a.length !== b.length || a.length === 0) return false;
  try {
    return timingSafeEqual(Buffer.from(a, 'hex'), Buffer.from(b, 'hex'));
  } catch {
    return false;
  }
}

export type EsitoInvito = 'valido' | 'scaduto' | 'gia_usato' | 'revocato';

export function valutaInvito(
  invito: { scadeIl: Date; usatoIl: Date | null; revocatoIl: Date | null },
  adesso: Date,
): EsitoInvito {
  if (invito.revocatoIl !== null) return 'revocato';
  if (invito.usatoIl !== null) return 'gia_usato';
  if (invito.scadeIl.getTime() <= adesso.getTime()) return 'scaduto';
  return 'valido';
}

/**
 * Messaggio per l'utente. **Non distingue i motivi**: rispondere "scaduto" invece di
 * "inesistente" direbbe a un attaccante che quel codice e' esistito, permettendogli
 * di esplorare lo spazio dei codici. Il motivo preciso resta nei log, dove serve al GM.
 */
export function spiegaInvitoRifiutato(): string {
  return 'Codice non valido. Chiedi al GM di generarne uno nuovo.';
}

export const TENTATIVI_MASSIMI = 10;
export const FINESTRA_TENTATIVI_MS = 15 * 60 * 1000;

export function troppiTentativi(tentativiRecenti: number): boolean {
  return tentativiRecenti >= TENTATIVI_MASSIMI;
}
