/**
 * Autodiagnosi del server di casa.
 *
 * Sostituisce la raccolta manuale delle caratteristiche del PC (domanda Q1 dello
 * Sprint 0): invece di chiedere a una persona che macchina abbia, la macchina se lo
 * chiede da sola all'avvio. E' anche piu' robusto, perche' resta corretto se il
 * server viene spostato su un altro computer.
 *
 * La parte decisionale e' pura e testabile; la rilevazione vive in `rilevaMacchina`.
 */

export interface Macchina {
  readonly coreLogici: number;
  readonly ramGb: number;
  /** Modello di GPU, quando rilevabile. null non significa "assente" ma "non rilevata". */
  readonly gpu: string | null;
  readonly piattaforma: string;
}

export type ClasseMacchina = 'potente' | 'adeguata' | 'modesta';

export interface ProfiloTrascrizione {
  readonly classe: ClasseMacchina;
  /** Modello proposto come punto di partenza: la misura reale puo' smentirlo. */
  readonly modelloIniziale: 'tiny' | 'base' | 'small' | 'medium' | 'large-v3';
  /** Quante trascrizioni far girare insieme senza mettere in ginocchio il PC. */
  readonly concorrenzaMassima: number;
  readonly motivo: string;
}

/**
 * Proposta iniziale, non verdetto. Serve perche' il sistema parta gia' configurato
 * in modo sensato; il dimensionamento definitivo esce dalla misura di SPIKE-1, che
 * ha sempre la precedenza (come per le fasce dei device in packages/shared).
 */
export function profiloIniziale(m: Macchina): ProfiloTrascrizione {
  if (m.gpu !== null) {
    return {
      classe: 'potente',
      modelloIniziale: 'medium',
      concorrenzaMassima: 3,
      motivo: `GPU rilevata (${m.gpu}): si parte da un modello grande e si misura.`,
    };
  }
  if (m.coreLogici >= 12 && m.ramGb >= 16) {
    return {
      classe: 'adeguata',
      modelloIniziale: 'small',
      concorrenzaMassima: 2,
      motivo: `${m.coreLogici} core logici e ${m.ramGb} GB di RAM, senza GPU rilevata.`,
    };
  }
  if (m.coreLogici >= 6 && m.ramGb >= 8) {
    return {
      classe: 'adeguata',
      modelloIniziale: 'base',
      concorrenzaMassima: 1,
      motivo: `${m.coreLogici} core logici e ${m.ramGb} GB di RAM: modello prudente, una trascrizione per volta.`,
    };
  }
  return {
    classe: 'modesta',
    modelloIniziale: 'tiny',
    concorrenzaMassima: 1,
    motivo:
      `${m.coreLogici} core logici e ${m.ramGb} GB di RAM: macchina modesta. ` +
      `Se la misura conferma, valutare il riconoscimento sul telefono come percorso principale.`,
  };
}

/** Il server ha abbastanza memoria per il modello proposto, tenendo margine al resto? */
export function memoriaSufficiente(m: Macchina, ramRichiestaGb: number): boolean {
  // Un quarto della RAM resta al sistema e a Postgres: un server che va in swap
  // durante una sessione e' peggio di un modello piu' piccolo.
  return m.ramGb * 0.75 >= ramRichiestaGb;
}

export type EsitoEsposizione = 'interna' | 'esposta' | 'sconosciuta';

/**
 * Il server capisce da solo se e' raggiungibile da fuori, invece di far verificare a
 * mano la presenza di CGNAT (domanda Q2 dello Sprint 0). Con il tunnel in uscita la
 * questione CGNAT e' comunque ininfluente: cio' che conta e' solo se il giro
 * completo funziona.
 */
export function valutaEsposizione(
  tunnelAttivo: boolean,
  giroCompletoRiuscito: boolean | null,
): EsitoEsposizione {
  if (!tunnelAttivo) return 'interna';
  if (giroCompletoRiuscito === null) return 'sconosciuta';
  return giroCompletoRiuscito ? 'esposta' : 'sconosciuta';
}

export function spiegaEsposizione(e: EsitoEsposizione): string {
  switch (e) {
    case 'interna':
      return 'Il server risponde solo dalla rete di casa. Per giocare da fuori serve il tunnel attivo.';
    case 'esposta':
      return 'Il server è raggiungibile da internet attraverso il tunnel.';
    case 'sconosciuta':
      return 'Il tunnel è avviato ma il giro completo non è ancora stato verificato.';
  }
}
