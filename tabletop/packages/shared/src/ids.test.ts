import { describe, expect, it } from 'vitest';
import { confrontaPerSeq, idMessaggio, seqCanale } from './ids.js';

const id = (s: string) => idMessaggio(s);
const A = id('aaaaaaaa-1111-2222-3333-444444444444');
const B = id('bbbbbbbb-1111-2222-3333-444444444444');

describe('sequenza di canale', () => {
  it('rifiuta valori non interi o negativi', () => {
    expect(() => seqCanale(1.5)).toThrow(RangeError);
    expect(() => seqCanale(-1)).toThrow(RangeError);
    expect(seqCanale(0)).toBe(0);
  });
});

describe('identificatore messaggio', () => {
  it('rifiuta formati non validi', () => {
    expect(() => idMessaggio('pippo')).toThrow(TypeError);
  });
});

describe('ordinamento', () => {
  it('ordina per sequenza crescente', () => {
    const m = [
      { seq: seqCanale(3), id: A },
      { seq: seqCanale(1), id: B },
      { seq: seqCanale(2), id: A },
    ];
    expect([...m].sort(confrontaPerSeq).map((x) => x.seq)).toEqual([1, 2, 3]);
  });

  it('a parita di sequenza resta deterministico', () => {
    const x = { seq: seqCanale(7), id: A };
    const y = { seq: seqCanale(7), id: B };
    expect(confrontaPerSeq(x, y)).toBeLessThan(0);
    expect(confrontaPerSeq(y, x)).toBeGreaterThan(0);
    expect(confrontaPerSeq(x, x)).toBe(0);
  });

  it('e un ordinamento totale: stesso risultato da input mescolati diversi', () => {
    const base = [
      { seq: seqCanale(2), id: B },
      { seq: seqCanale(1), id: A },
      { seq: seqCanale(2), id: A },
    ];
    const atteso = [...base].sort(confrontaPerSeq).map((m) => `${m.seq}:${m.id}`);
    for (let i = 0; i < 20; i++) {
      const mescolato = [...base].sort(() => Math.random() - 0.5);
      expect(mescolato.sort(confrontaPerSeq).map((m) => `${m.seq}:${m.id}`)).toEqual(atteso);
    }
  });
});
