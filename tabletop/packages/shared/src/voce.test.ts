import { describe, expect, it } from 'vitest';
import { LIMITI_DSP, PRESET_INIZIALI, presetValido } from './voce.js';

describe('preset di voce', () => {
  it('tutti i preset iniziali stanno nei limiti di intelligibilita', () => {
    for (const p of PRESET_INIZIALI) {
      expect(presetValido(p), `preset fuori limiti: ${p.id}`).toBe(true);
    }
  });

  it('gli id sono univoci', () => {
    const ids = PRESET_INIZIALI.map((p) => p.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('nessun preset e dichiarato valido prima di essere ascoltato', () => {
    // Guardia contro l'ottimismo: finche' SPIKE-2 non gira, nessun esito e' "ok".
    for (const p of PRESET_INIZIALI) expect(p.esitoSpike).toBe('da_misurare');
  });

  it('respinge un preset oltre i limiti', () => {
    expect(presetValido({
      id: 'x', etichetta: 'X', esitoSpike: 'da_misurare',
      dsp: { pitchSemitoni: LIMITI_DSP.pitchSemitoni + 1, formantiPct: 0, riverberoMix: 0, saturazione: 0 },
    })).toBe(false);
  });
});
