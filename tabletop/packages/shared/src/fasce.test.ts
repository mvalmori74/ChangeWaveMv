import { describe, expect, it } from 'vitest';
import { API_MINIMA, classificaFascia, effettiPerFascia } from './fasce.js';

const base = { livelloApi: 34 };

describe('classificazione della fascia', () => {
  it('rifiuta un device sotto il livello di API minimo', () => {
    expect(() => classificaFascia({ ...base, livelloApi: 32, ramGb: 16, coreCpu: 8 }))
      .toThrow(RangeError);
    expect(API_MINIMA).toBe(33);
  });

  it('una misura reale ha la precedenza sugli indizi hardware', () => {
    // Hardware da fascia alta, ma i fps misurati dicono altro: vince la misura.
    const e = classificaFascia({ ...base, ramGb: 12, coreCpu: 8, fpsMisurati: 31 });
    expect(e.fascia).toBe('media');
    expect(e.daMisura).toBe(true);
  });

  it('non classifica per anno: un entry-level recente resta in fascia bassa', () => {
    const recenteMaScarso = classificaFascia({ ...base, ramGb: 3, coreCpu: 4, annoModello: 2024 });
    const vecchioMaPotente = classificaFascia({ ...base, ramGb: 12, coreCpu: 8, annoModello: 2021 });
    expect(recenteMaScarso.fascia).toBe('bassa');
    expect(vecchioMaPotente.fascia).toBe('alta');
  });

  it('colloca la fascia media', () => {
    expect(classificaFascia({ ...base, ramGb: 6, coreCpu: 8 }).fascia).toBe('media');
  });
});

describe('effetti per fascia', () => {
  it('degrada progressivamente', () => {
    expect(effettiPerFascia('alta', false).ombreDinamiche).toBe(true);
    expect(effettiPerFascia('media', false).ombreDinamiche).toBe(false);
    expect(effettiPerFascia('media', false).tridimensionale).toBe(true);
    expect(effettiPerFascia('bassa', false).tridimensionale).toBe(false);
  });

  it('la preferenza di animazione ridotta vince su qualunque fascia', () => {
    for (const f of ['alta', 'media', 'bassa'] as const) {
      const e = effettiPerFascia(f, true);
      expect(e.tridimensionale).toBe(false);
      expect(e.durataMs).toBe(0);
    }
  });
});
