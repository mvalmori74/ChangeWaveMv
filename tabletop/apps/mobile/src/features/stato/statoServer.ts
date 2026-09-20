/**
 * Stato del server di casa (master prompt §4-bis punto 2).
 *
 * Il server sta nel salotto di qualcuno: puo' essere spento, la linea puo' essere
 * giu'. L'app deve dirlo in modo comprensibile, mai con una rotella che gira per
 * sempre. Questo modulo e' logica pura: la vista ci si appoggia.
 */
export type Raggiungibilita = 'ignota' | 'raggiungibile' | 'irraggiungibile' | 'lento';

export interface RispostaStato {
  readonly versione: string;
  readonly disco: { readonly livello: string; readonly avviso: string | null };
  readonly codaVocaleInAttesa: number;
  readonly ultimoBackup: { readonly quando: string; readonly esito: string } | null;
}

/** Oltre questa soglia la connessione c'e' ma non e' utilizzabile per giocare. */
export const MS_LENTO = 2000;

export function valutaRaggiungibilita(
  esito: 'ok' | 'errore',
  millisecondi: number,
): Raggiungibilita {
  if (esito === 'errore') return 'irraggiungibile';
  return millisecondi > MS_LENTO ? 'lento' : 'raggiungibile';
}

/**
 * Messaggio per l'utente. Mai gergo tecnico: chi legge vuole sapere se stasera si
 * gioca, non quale strato di rete ha ceduto.
 */
export function spiegaAllUtente(r: Raggiungibilita): string {
  switch (r) {
    case 'ignota': return 'Verifica del collegamento in corso.';
    case 'raggiungibile': return 'Server del tavolo raggiungibile.';
    case 'lento': return 'Il server risponde molto lentamente: la serata potrebbe risentirne.';
    case 'irraggiungibile':
      return 'Il server del tavolo non risponde. Puoi scrivere lo stesso: i messaggi ' +
             'partiranno appena torna. I tiri di dado invece no, perche’ li convalida il server.';
  }
}

/** Cosa resta utilizzabile quando il server non c'e'. Usato per disabilitare i comandi. */
export function funzioniDisponibili(r: Raggiungibilita): {
  scrivere: boolean; tirareDadi: boolean; allegare: boolean;
} {
  const vivo = r === 'raggiungibile' || r === 'lento';
  return { scrivere: true, tirareDadi: vivo, allegare: vivo };
}
