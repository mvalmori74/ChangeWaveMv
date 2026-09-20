import { describe, expect, it } from 'vitest';
import { accettaCaricamenti, livelloDisco, messaggioPerIlGm } from './disco.js';

const GB = 1024 ** 3;

describe('sorveglianza del disco', () => {
  it('classifica le soglie', () => {
    expect(livelloDisco(10 * GB, 100 * GB)).toBe('ok');
    expect(livelloDisco(80 * GB, 100 * GB)).toBe('avviso');
    expect(livelloDisco(90 * GB, 100 * GB)).toBe('critico');
    expect(livelloDisco(99 * GB, 100 * GB)).toBe('esaurito');
  });

  it('rifiuta i caricamenti solo a disco esaurito', () => {
    expect(accettaCaricamenti('critico')).toBe(true);
    expect(accettaCaricamenti('esaurito')).toBe(false);
  });

  it('avvisa il GM da avviso in su, tace quando va tutto bene', () => {
    expect(messaggioPerIlGm('ok')).toBeNull();
    for (const l of ['avviso', 'critico', 'esaurito'] as const) {
      expect(messaggioPerIlGm(l)).toBeTruthy();
    }
  });

  it('rifiuta una dimensione totale assurda invece di dividere per zero', () => {
    expect(() => livelloDisco(1, 0)).toThrow(RangeError);
  });
});
