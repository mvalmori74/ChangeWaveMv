/**
 * Classificazione della fascia di prestazioni del device (master prompt §4-ter).
 *
 * Il motivo per cui questo codice esiste: l'anno di uscita di un telefono fissa il
 * sistema operativo, NON la GPU. Un entry-level del 2023 rende meno di un top di
 * gamma del 2021. Classificare per anno sarebbe comodo e sbagliato.
 *
 * Funzione pura: la rilevazione delle capacita' sta nel client, qui c'e' solo la
 * decisione, cosi' e' verificabile senza un telefono in mano.
 */

export type Fascia = 'alta' | 'media' | 'bassa';

export interface CapacitaDevice {
  readonly ramGb: number;
  readonly coreCpu: number;
  readonly livelloApi: number;
  /** Anno del modello, se noto. Indizio debole: pesa poco di proposito. */
  readonly annoModello?: number;
  /** Esito di un micro-benchmark di rendering, se gia' eseguito: fps stimati. */
  readonly fpsMisurati?: number;
}

/** Sotto questo livello di API l'app non si installa nemmeno (§13-D6). */
export const API_MINIMA = 33;

export interface EsitoFascia {
  readonly fascia: Fascia;
  readonly motivo: string;
  /** true quando la classificazione si basa su una misura, non su indizi hardware. */
  readonly daMisura: boolean;
}

export function classificaFascia(c: CapacitaDevice): EsitoFascia {
  if (c.livelloApi < API_MINIMA) {
    throw new RangeError(
      `Livello API ${c.livelloApi} sotto il minimo ${API_MINIMA}: device non supportato`,
    );
  }

  // Una misura reale batte qualunque euristica sull'hardware dichiarato.
  if (c.fpsMisurati !== undefined) {
    if (c.fpsMisurati >= 55) return { fascia: 'alta', motivo: `${c.fpsMisurati} fps misurati`, daMisura: true };
    if (c.fpsMisurati >= 28) return { fascia: 'media', motivo: `${c.fpsMisurati} fps misurati`, daMisura: true };
    return { fascia: 'bassa', motivo: `${c.fpsMisurati} fps misurati: sotto la soglia utile`, daMisura: true };
  }

  if (c.ramGb >= 8 && c.coreCpu >= 8) {
    return { fascia: 'alta', motivo: `${c.ramGb} GB di RAM e ${c.coreCpu} core`, daMisura: false };
  }
  if (c.ramGb >= 4 && c.coreCpu >= 6) {
    return { fascia: 'media', motivo: `${c.ramGb} GB di RAM e ${c.coreCpu} core`, daMisura: false };
  }
  return {
    fascia: 'bassa',
    motivo: `${c.ramGb} GB di RAM e ${c.coreCpu} core: sotto la soglia per il 3D`,
    daMisura: false,
  };
}

export interface EffettiDadi {
  readonly tridimensionale: boolean;
  readonly ombreDinamiche: boolean;
  readonly durataMs: number;
}

export function effettiPerFascia(fascia: Fascia, animazioneRidotta: boolean): EffettiDadi {
  // La preferenza di sistema per l'animazione ridotta e' una cosa diversa dalla
  // fascia bassa: li' si salta per scelta dell'utente, qui per limiti del device.
  // Vince sempre la scelta dell'utente.
  if (animazioneRidotta) return { tridimensionale: false, ombreDinamiche: false, durataMs: 0 };

  switch (fascia) {
    case 'alta':   return { tridimensionale: true,  ombreDinamiche: true,  durataMs: 1600 };
    case 'media':  return { tridimensionale: true,  ombreDinamiche: false, durataMs: 1200 };
    case 'bassa':  return { tridimensionale: false, ombreDinamiche: false, durataMs: 800 };
  }
}
