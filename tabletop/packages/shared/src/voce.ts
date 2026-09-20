/**
 * Preset di voce (F4, Appendice A del master prompt).
 *
 * I valori qui sono il PUNTO DI PARTENZA dello SPIKE-2, non la verita': vanno
 * sostituiti con quelli che reggono all'ascolto. Un preset sotto 3/5 di
 * intelligibilita' va scartato o ridisegnato.
 */
export interface ParametriDsp {
  /** Semitoni. Negativo = voce piu' grave. */
  readonly pitchSemitoni: number;
  /** Scostamento delle formanti in percentuale: da' la taglia del corpo, non l'altezza. */
  readonly formantiPct: number;
  readonly riverberoMix: number;
  readonly saturazione: number;
}

export interface PresetVoce {
  readonly id: string;
  readonly etichetta: string;
  readonly dsp: ParametriDsp;
  /** Verdetto dello SPIKE-2. 'da_misurare' finche' non e' stato ascoltato davvero. */
  readonly esitoSpike: 'da_misurare' | 'ok_costo_zero' | 'da_ridisegnare' | 'scartato';
}

export const PRESET_INIZIALI: readonly PresetVoce[] = [
  { id: 'umano', etichetta: 'Umano', esitoSpike: 'da_misurare',
    dsp: { pitchSemitoni: 0, formantiPct: 0, riverberoMix: 0, saturazione: 0 } },
  { id: 'nano', etichetta: 'Nano', esitoSpike: 'da_misurare',
    dsp: { pitchSemitoni: -3, formantiPct: -8, riverberoMix: 0.05, saturazione: 0.2 } },
  { id: 'orco', etichetta: 'Orco', esitoSpike: 'da_misurare',
    dsp: { pitchSemitoni: -5, formantiPct: -15, riverberoMix: 0.08, saturazione: 0.35 } },
  { id: 'elfo', etichetta: 'Elfo', esitoSpike: 'da_misurare',
    dsp: { pitchSemitoni: 3, formantiPct: 6, riverberoMix: 0.25, saturazione: 0 } },
  { id: 'goblin', etichetta: 'Goblin', esitoSpike: 'da_misurare',
    dsp: { pitchSemitoni: 7, formantiPct: 12, riverberoMix: 0.05, saturazione: 0.5 } },
] as const;

/** Limiti oltre i quali la voce diventa incomprensibile: il DSP non e' un giocattolo. */
export const LIMITI_DSP = { pitchSemitoni: 12, formantiPct: 30 } as const;

export function presetValido(p: PresetVoce): boolean {
  return (
    Math.abs(p.dsp.pitchSemitoni) <= LIMITI_DSP.pitchSemitoni &&
    Math.abs(p.dsp.formantiPct) <= LIMITI_DSP.formantiPct &&
    p.dsp.riverberoMix >= 0 && p.dsp.riverberoMix <= 1 &&
    p.dsp.saturazione >= 0 && p.dsp.saturazione <= 1
  );
}
