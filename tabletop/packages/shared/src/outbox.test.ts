import { describe, expect, it } from 'vitest';
import { idMessaggio } from './ids.js';
import {
  ATTESA_MASSIMA_MS, TENTATIVI_PRIMA_DI_ARRENDERSI, attesaPerTentativo, daInviare,
  riepilogo, riprendiDopoRiavvio, segnaConsegnato, segnaFallito, segnaInVolo, sveglia,
  type VoceOutbox,
} from './outbox.js';

const id = (n: number) =>
  idMessaggio(`00000000-0000-4000-8000-${String(n).padStart(12, '0')}`);

const voce = (n: number, p: Partial<VoceOutbox> = {}): VoceOutbox => ({
  id: id(n), stato: 'in_coda', tentativi: 0, nonPrimaDi: 0, ...p,
});

describe('attesa fra i tentativi', () => {
  it('raddoppia a ogni tentativo', () => {
    expect(attesaPerTentativo(1)).toBe(1_000);
    expect(attesaPerTentativo(2)).toBe(2_000);
    expect(attesaPerTentativo(3)).toBe(4_000);
  });

  it('non supera mai il tetto', () => {
    // Senza tetto, dopo una notte a server spento l'attesa sarebbe di ore e il
    // messaggio non ripartirebbe nemmeno al ritorno del server.
    for (const t of [10, 20, 50, 100]) {
      expect(attesaPerTentativo(t)).toBe(ATTESA_MASSIMA_MS);
    }
  });
});

describe('selezione di cosa inviare', () => {
  it('prende solo le voci in coda e mature', () => {
    const coda = [
      voce(1, { nonPrimaDi: 0 }),
      voce(2, { nonPrimaDi: 5_000 }),
      voce(3, { stato: 'in_volo' }),
      voce(4, { stato: 'consegnato' }),
      voce(5, { stato: 'fallito' }),
    ];
    expect(daInviare(coda, 1_000).map((v) => v.id)).toEqual([id(1)]);
  });

  it('conserva l ordine di inserimento', () => {
    const coda = [voce(1), voce(2), voce(3)];
    expect(daInviare(coda, 10).map((v) => v.id)).toEqual([id(1), id(2), id(3)]);
  });
});

describe('esiti', () => {
  it('un fallimento di rete rimette in coda con attesa crescente', () => {
    const dopo = segnaFallito(segnaInVolo(voce(1)), 'rete assente', 10_000);
    expect(dopo.stato).toBe('in_coda');
    expect(dopo.tentativi).toBe(1);
    expect(dopo.nonPrimaDi).toBe(11_000);
  });

  it('un rifiuto del server non viene ritentato', () => {
    // Riprovare un messaggio malformato o di un utente espulso non migliora mai.
    const dopo = segnaFallito(segnaInVolo(voce(1)), 'non sei piu nel tavolo', 0, true);
    expect(dopo.stato).toBe('fallito');
    expect(dopo.tentativi).toBe(1);
  });

  it('dopo troppi tentativi si arrende invece di riprovare per sempre', () => {
    let v = voce(1);
    for (let i = 0; i < TENTATIVI_PRIMA_DI_ARRENDERSI; i++) {
      v = segnaFallito(segnaInVolo(v), 'rete assente', 0);
    }
    expect(v.stato).toBe('fallito');
    expect(v.tentativi).toBe(TENTATIVI_PRIMA_DI_ARRENDERSI);
  });

  it('la consegna cancella l errore precedente', () => {
    const v = segnaConsegnato(segnaFallito(voce(1), 'rete assente', 0));
    expect(v.stato).toBe('consegnato');
    expect(v.ultimoErrore).toBeUndefined();
  });
});

describe('ritorno del server', () => {
  it('sveglia le voci in attesa senza toccare le altre', () => {
    const coda = [
      voce(1, { nonPrimaDi: 999_999 }),
      voce(2, { stato: 'fallito', nonPrimaDi: 5 }),
      voce(3, { stato: 'consegnato', nonPrimaDi: 5 }),
    ];
    const dopo = sveglia(coda, 1_000);
    expect(dopo[0]?.nonPrimaDi).toBe(1_000);
    expect(dopo[1]?.nonPrimaDi).toBe(5); // chi si e' arreso lo rimanda l'utente
    expect(dopo[2]?.stato).toBe('consegnato');
  });
});

describe('riavvio dell app', () => {
  it('rimette in coda le voci rimaste in volo', () => {
    // Sicuro solo perche' l'invio e' idempotente sull'id: se erano gia' arrivate,
    // il server risponde che le aveva gia' e nessuno vede un doppione.
    const coda = [voce(1, { stato: 'in_volo' }), voce(2, { stato: 'consegnato' })];
    const dopo = riprendiDopoRiavvio(coda, 500);
    expect(dopo[0]?.stato).toBe('in_coda');
    expect(dopo[0]?.nonPrimaDi).toBe(500);
    expect(dopo[1]?.stato).toBe('consegnato');
  });

  it('non perde nulla: ogni voce sopravvive al riavvio', () => {
    const coda = [
      voce(1, { stato: 'in_volo' }), voce(2, { stato: 'in_coda' }),
      voce(3, { stato: 'fallito' }), voce(4, { stato: 'consegnato' }),
    ];
    expect(riprendiDopoRiavvio(coda, 0)).toHaveLength(coda.length);
  });
});

describe('scenario completo: rete che va e viene', () => {
  it('un messaggio scritto offline arriva quando il server torna', () => {
    let v = voce(1);
    let t = 0;

    // Tre tentativi a vuoto: server spento.
    for (let i = 0; i < 3; i++) {
      expect(daInviare([v], t)).toHaveLength(1);
      v = segnaFallito(segnaInVolo(v), 'server irraggiungibile', t);
      t += 100; // l'utente riprova prima della scadenza: non deve partire
      expect(daInviare([v], t)).toHaveLength(0);
      t = v.nonPrimaDi;
    }
    expect(v.stato).toBe('in_coda');
    expect(v.tentativi).toBe(3);

    // Il server torna: sveglia immediata, niente attesa residua.
    const svegliata = sveglia([v], t)[0]!;
    expect(daInviare([svegliata], t)).toHaveLength(1);

    const consegnata = segnaConsegnato(segnaInVolo(svegliata));
    expect(riepilogo([consegnata])).toEqual({ inAttesa: 0, falliti: 0, tuttoConsegnato: true });
  });
});
