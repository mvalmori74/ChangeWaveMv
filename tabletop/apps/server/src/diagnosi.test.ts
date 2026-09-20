import { describe, expect, it } from 'vitest';
import {
  memoriaSufficiente, profiloIniziale, spiegaEsposizione, valutaEsposizione,
  type Macchina,
} from './diagnosi.js';

const pc = (p: Partial<Macchina>): Macchina => ({
  coreLogici: 8, ramGb: 16, gpu: null, piattaforma: 'linux', ...p,
});

describe('profilo iniziale di trascrizione', () => {
  it('con una GPU parte da un modello grande', () => {
    const p = profiloIniziale(pc({ gpu: 'NVIDIA RTX 4060' }));
    expect(p.classe).toBe('potente');
    expect(p.modelloIniziale).toBe('medium');
    expect(p.concorrenzaMassima).toBeGreaterThan(1);
  });

  it('senza GPU ma con molti core resta prudente', () => {
    expect(profiloIniziale(pc({ coreLogici: 16, ramGb: 32 })).modelloIniziale).toBe('small');
  });

  it('su una macchina modesta propone il modello piu leggero e lo dice', () => {
    const p = profiloIniziale(pc({ coreLogici: 4, ramGb: 8 }));
    expect(p.modelloIniziale).toBe('tiny');
    expect(p.motivo).toMatch(/telefono/); // suggerisce l'alternativa, non si limita a degradare
  });

  it('non propone mai piu di una trascrizione insieme senza GPU', () => {
    for (const core of [4, 8, 12, 16, 32]) {
      const p = profiloIniziale(pc({ coreLogici: core, ramGb: 32 }));
      expect(p.concorrenzaMassima).toBe(core >= 12 ? 2 : 1);
    }
  });
});

describe('memoria', () => {
  it('riserva un quarto della RAM a sistema e database', () => {
    // La soglia e' esattamente il 75%: un server che va in swap durante una
    // sessione e' peggio di un modello piu' piccolo.
    expect(memoriaSufficiente(pc({ ramGb: 8 }), 6)).toBe(true);   // 8 x 0,75 = 6, al limite
    expect(memoriaSufficiente(pc({ ramGb: 8 }), 6.1)).toBe(false); // appena oltre
    expect(memoriaSufficiente(pc({ ramGb: 8 }), 7)).toBe(false);
    expect(memoriaSufficiente(pc({ ramGb: 16 }), 6)).toBe(true);
  });
});

describe('esposizione', () => {
  it('senza tunnel il server e solo interno', () => {
    expect(valutaEsposizione(false, true)).toBe('interna');
  });

  it('il tunnel avviato non basta: conta il giro completo', () => {
    expect(valutaEsposizione(true, null)).toBe('sconosciuta');
    expect(valutaEsposizione(true, false)).toBe('sconosciuta');
    expect(valutaEsposizione(true, true)).toBe('esposta');
  });

  it('spiega ogni stato senza gergo', () => {
    for (const e of ['interna', 'esposta', 'sconosciuta'] as const) {
      const t = spiegaEsposizione(e).toLowerCase();
      expect(t.length).toBeGreaterThan(20);
      for (const g of ['cgnat', 'nat', 'porta', 'socket']) expect(t).not.toContain(g);
    }
  });
});
