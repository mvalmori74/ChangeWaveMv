import { describe, expect, it, vi } from 'vitest';
import type { MessaggioSalvato } from '../db/archivio.js';
import { Hub, TAMPONE_MASSIMO, type EventoRealtime } from './hub.js';

const CAMP = 'campagna-1';

function msg(seq: number, campagnaId = CAMP): MessaggioSalvato {
  return {
    id: `id-${seq}`, campagnaId, autoreId: 'autore', tipo: 'testo',
    corpo: { testo: `m${seq}` }, seq, creatoIl: new Date(0),
    cancellatoIl: null, eraGiaPresente: false,
  };
}

/** Cronologia finta: qui serve controllare i tempi, non il database. */
function cronologia(messaggi: MessaggioSalvato[], ritardoMs = 0) {
  return async (campagnaId: string, dopoSeq: number, limite: number) => {
    if (ritardoMs > 0) await new Promise((r) => setTimeout(r, ritardoMs));
    return messaggi
      .filter((m) => m.campagnaId === campagnaId && m.seq > dopoSeq)
      .sort((a, b) => a.seq - b.seq)
      .slice(0, limite);
  };
}

const sequenzeRicevute = (invia: ReturnType<typeof vi.fn>) =>
  invia.mock.calls.map((c) => (c[0] as EventoRealtime).messaggio.seq);

describe('recupero della cronologia', () => {
  it('consegna esattamente cio che manca a partire da dopoSeq', async () => {
    const hub = new Hub(cronologia([msg(1), msg(2), msg(3), msg(4), msg(5)]));
    const invia = vi.fn();
    await hub.sottoscrivi(CAMP, 3, invia);
    expect(sequenzeRicevute(invia)).toEqual([4, 5]);
  });

  it('a un client nuovo consegna tutto dall inizio', async () => {
    const hub = new Hub(cronologia([msg(1), msg(2), msg(3)]));
    const invia = vi.fn();
    await hub.sottoscrivi(CAMP, 0, invia);
    expect(sequenzeRicevute(invia)).toEqual([1, 2, 3]);
  });

  it('a un client gia aggiornato non consegna nulla', async () => {
    const hub = new Hub(cronologia([msg(1), msg(2)]));
    const invia = vi.fn();
    await hub.sottoscrivi(CAMP, 2, invia);
    expect(invia).not.toHaveBeenCalled();
  });
});

describe('la trappola: messaggi che arrivano durante il recupero', () => {
  it('non li perde e non li duplica, e li consegna in ordine', async () => {
    // È il caso che rompe le implementazioni ingenue: il recupero è lento (rete,
    // disco, tanti messaggi) e intanto il tavolo continua a scrivere.
    const hub = new Hub(cronologia([msg(1), msg(2), msg(3)], 30));
    const invia = vi.fn();

    const iscrizione = hub.sottoscrivi(CAMP, 0, invia);
    // Mentre il recupero è in corso arrivano due messaggi nuovi.
    await new Promise((r) => setTimeout(r, 10));
    hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(4) });
    hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(5) });
    await iscrizione;

    const ricevute = sequenzeRicevute(invia);
    expect(ricevute).toEqual([1, 2, 3, 4, 5]);          // tutte, in ordine
    expect(new Set(ricevute).size).toBe(ricevute.length); // nessun doppione
  });

  it('scarta dal tampone cio che il recupero aveva gia consegnato', async () => {
    // Il messaggio 3 è sia nella cronologia sia pubblicato in diretta: il client
    // deve vederlo una volta sola.
    const hub = new Hub(cronologia([msg(1), msg(2), msg(3)], 30));
    const invia = vi.fn();

    const iscrizione = hub.sottoscrivi(CAMP, 0, invia);
    await new Promise((r) => setTimeout(r, 10));
    hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(3) });
    hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(4) });
    await iscrizione;

    expect(sequenzeRicevute(invia)).toEqual([1, 2, 3, 4]);
  });
});

describe('diretta', () => {
  it('consegna i nuovi messaggi agli iscritti', async () => {
    const hub = new Hub(cronologia([]));
    const invia = vi.fn();
    await hub.sottoscrivi(CAMP, 0, invia);
    hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(1) });
    expect(sequenzeRicevute(invia)).toEqual([1]);
  });

  it('non mescola le campagne', async () => {
    const hub = new Hub(cronologia([]));
    const invia = vi.fn();
    await hub.sottoscrivi(CAMP, 0, invia);
    hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(1, 'altra-campagna') });
    expect(invia).not.toHaveBeenCalled();
  });

  it('ignora un evento gia consegnato, se ripubblicato', async () => {
    const hub = new Hub(cronologia([]));
    const invia = vi.fn();
    await hub.sottoscrivi(CAMP, 0, invia);
    hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(1) });
    hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(1) });
    expect(sequenzeRicevute(invia)).toEqual([1]);
  });

  it('serve piu iscritti dello stesso tavolo', async () => {
    const hub = new Hub(cronologia([]));
    const a = vi.fn(); const b = vi.fn();
    await hub.sottoscrivi(CAMP, 0, a);
    await hub.sottoscrivi(CAMP, 0, b);
    hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(1) });
    expect(sequenzeRicevute(a)).toEqual([1]);
    expect(sequenzeRicevute(b)).toEqual([1]);
  });
});

describe('protezione della memoria del server', () => {
  it('marca da scollegare chi satura il tampone invece di crescere all infinito', async () => {
    const hub = new Hub(cronologia([msg(1)], 50));
    const invia = vi.fn();
    const iscrizione = hub.sottoscrivi(CAMP, 0, invia);

    await new Promise((r) => setTimeout(r, 5));
    for (let s = 2; s < TAMPONE_MASSIMO + 50; s++) {
      hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(s) });
    }
    expect(hub.iscrittiDaScollegare()).toHaveLength(1);
    await iscrizione;
  });
});

describe('ciclo di vita', () => {
  it('la disiscrizione ferma la consegna', async () => {
    const hub = new Hub(cronologia([]));
    const invia = vi.fn();
    const disiscrivi = await hub.sottoscrivi(CAMP, 0, invia);
    disiscrivi();
    hub.pubblica({ tipo: 'messaggio.creato', messaggio: msg(1) });
    expect(invia).not.toHaveBeenCalled();
    expect(hub.numeroIscritti).toBe(0);
  });

  it('un recupero fallito non lascia iscritti appesi', async () => {
    const hub = new Hub(async () => { throw new Error('database non raggiungibile'); });
    await expect(hub.sottoscrivi(CAMP, 0, vi.fn())).rejects.toThrow(/non raggiungibile/);
    expect(hub.numeroIscritti).toBe(0);
  });
});
