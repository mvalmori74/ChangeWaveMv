import { describe, expect, it } from 'vitest';
import {
  LUNGHEZZA_CODICE, TENTATIVI_MASSIMI, generaCodiceInvito, generaToken, impronta,
  improntaCorrisponde, normalizzaCodice, spiegaInvitoRifiutato, troppiTentativi,
  valutaInvito,
} from './credenziali.js';

describe('codici di invito', () => {
  it('hanno la lunghezza prevista e sono leggibili a gruppi', () => {
    const c = generaCodiceInvito();
    expect(normalizzaCodice(c)).toHaveLength(LUNGHEZZA_CODICE);
    expect(c).toMatch(/^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/);
  });

  it('non contengono caratteri che si confondono leggendo', () => {
    // 0/O, 1/I/L, 2/Z, 5/S, 8/B: un carattere frainteso brucia un invito monouso.
    for (let i = 0; i < 200; i++) {
      expect(generaCodiceInvito()).not.toMatch(/[0O1IL2Z5S8B]/);
    }
  });

  it('non si ripetono', () => {
    const visti = new Set(Array.from({ length: 500 }, () => generaCodiceInvito()));
    expect(visti.size).toBe(500);
  });

  it('usano tutto l alfabeto, senza concentrarsi su pochi simboli', () => {
    // Guardia contro un bias da modulo: senza lo scarto dei valori alti, i primi
    // simboli uscirebbero piu' spesso degli altri.
    const conteggio = new Map<string, number>();
    for (let i = 0; i < 2000; i++) {
      for (const ch of normalizzaCodice(generaCodiceInvito())) {
        conteggio.set(ch, (conteggio.get(ch) ?? 0) + 1);
      }
    }
    expect(conteggio.size).toBe(25);
    const valori = [...conteggio.values()];
    const atteso = (2000 * LUNGHEZZA_CODICE) / 25;
    // Nessun simbolo si discosta piu' del 25% dalla frequenza attesa.
    for (const v of valori) expect(Math.abs(v - atteso) / atteso).toBeLessThan(0.25);
  });

  it('accetta il codice comunque lo scriva l utente', () => {
    expect(normalizzaCodice('acde-fghj-kmnp')).toBe('ACDEFGHJKMNP');
    expect(normalizzaCodice(' ACDE FGHJ KMNP ')).toBe('ACDEFGHJKMNP');
    expect(normalizzaCodice('ACDEFGHJKMNP')).toBe('ACDEFGHJKMNP');
  });
});

describe('impronte', () => {
  it('non permettono di risalire al segreto', () => {
    const token = generaToken();
    const h = impronta(token);
    expect(h).not.toContain(token);
    expect(h).toMatch(/^[0-9a-f]{64}$/);
  });

  it('sono stabili e distinte', () => {
    expect(impronta('a')).toBe(impronta('a'));
    expect(impronta('a')).not.toBe(impronta('b'));
  });

  it('il confronto respinge lunghezze diverse e valori non validi', () => {
    expect(improntaCorrisponde(impronta('a'), impronta('a'))).toBe(true);
    expect(improntaCorrisponde(impronta('a'), impronta('b'))).toBe(false);
    expect(improntaCorrisponde('', '')).toBe(false);
    expect(improntaCorrisponde('zz', impronta('a'))).toBe(false);
  });

  it('i token sono lunghi e diversi ogni volta', () => {
    const t = Array.from({ length: 100 }, generaToken);
    expect(new Set(t).size).toBe(100);
    expect(t[0]!.length).toBeGreaterThanOrEqual(43);
  });
});

describe('validita di un invito', () => {
  const adesso = new Date('2026-09-21T12:00:00Z');
  const base = { scadeIl: new Date('2026-09-22T12:00:00Z'), usatoIl: null, revocatoIl: null };

  it('valido finche non scade', () => {
    expect(valutaInvito(base, adesso)).toBe('valido');
  });

  it('scaduto quando il tempo e passato', () => {
    expect(valutaInvito({ ...base, scadeIl: new Date('2026-09-20T12:00:00Z') }, adesso))
      .toBe('scaduto');
  });

  it('monouso', () => {
    expect(valutaInvito({ ...base, usatoIl: adesso }, adesso)).toBe('gia_usato');
  });

  it('la revoca vince su tutto', () => {
    expect(valutaInvito({ ...base, usatoIl: null, revocatoIl: adesso }, adesso)).toBe('revocato');
  });

  it('il messaggio all utente non rivela il motivo', () => {
    // Dire "scaduto" invece di "inesistente" confermerebbe che quel codice e'
    // esistito, e permetterebbe di esplorare lo spazio dei codici.
    const m = spiegaInvitoRifiutato().toLowerCase();
    for (const parola of ['scadut', 'usat', 'revocat', 'esist']) {
      expect(m).not.toContain(parola);
    }
  });
});

describe('limite ai tentativi', () => {
  it('scatta alla soglia', () => {
    expect(troppiTentativi(TENTATIVI_MASSIMI - 1)).toBe(false);
    expect(troppiTentativi(TENTATIVI_MASSIMI)).toBe(true);
  });
});
