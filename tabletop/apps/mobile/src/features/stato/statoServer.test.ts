import { describe, expect, it } from 'vitest';
import {
  funzioniDisponibili, MS_LENTO, spiegaAllUtente, valutaRaggiungibilita,
} from './statoServer.js';

describe('raggiungibilita del server di casa', () => {
  it('distingue lento da irraggiungibile', () => {
    expect(valutaRaggiungibilita('ok', 200)).toBe('raggiungibile');
    expect(valutaRaggiungibilita('ok', MS_LENTO + 1)).toBe('lento');
    expect(valutaRaggiungibilita('errore', 0)).toBe('irraggiungibile');
  });

  it('a server spento si puo scrivere ma non tirare i dadi', () => {
    const f = funzioniDisponibili('irraggiungibile');
    expect(f.scrivere).toBe(true);
    expect(f.tirareDadi).toBe(false);
    expect(f.allegare).toBe(false);
  });

  it('con server lento le funzioni restano attive', () => {
    expect(funzioniDisponibili('lento').tirareDadi).toBe(true);
  });

  it('nessun messaggio contiene gergo tecnico', () => {
    const gergo = ['timeout', 'socket', 'http', '500', 'null', 'undefined', 'exception'];
    for (const r of ['ignota', 'raggiungibile', 'lento', 'irraggiungibile'] as const) {
      const testo = spiegaAllUtente(r).toLowerCase();
      for (const g of gergo) expect(testo, `"${g}" in "${testo}"`).not.toContain(g);
      expect(testo.length).toBeGreaterThan(10);
    }
  });

  it('spiega perche i dadi non funzionano, invece di limitarsi a vietarli', () => {
    expect(spiegaAllUtente('irraggiungibile')).toContain('convalida');
  });
});
