/**
 * Distribuzione degli eventi in tempo reale, con recupero della cronologia perduta.
 *
 * Il problema che questo file risolve e' la trappola classica del realtime: quando un
 * client si ricollega dopo un'interruzione chiede "mandami tutto dopo il numero N".
 * Mentre il server gli rispedisce la cronologia, **arrivano messaggi nuovi**. Se li
 * si inoltra subito il client li riceve fuori ordine; se li si scarta li perde per
 * sempre.
 *
 * Qui recupero e diretta convivono: gli eventi che arrivano durante il recupero
 * finiscono in un tampone e vengono consegnati alla fine, scartando quelli gia'
 * inclusi nella cronologia. Il client riceve ogni sequenza **una volta sola e in
 * ordine**, senza dover sapere nulla di tutto questo.
 */
import type { MessaggioSalvato } from '../db/archivio.js';

export type Invio = (evento: EventoRealtime) => void;

export interface EventoRealtime {
  readonly tipo: 'messaggio.creato' | 'messaggio.aggiornato';
  readonly messaggio: MessaggioSalvato;
}

/** Quanti messaggi per pagina durante il recupero. */
export const PAGINA_RECUPERO = 200;

/**
 * Tetto al tampone. Un client cosi' lento da accumulare piu' di questo va scollegato:
 * continuare a bufferizzare per lui significherebbe consumare la memoria del PC di
 * casa finche' non cade tutto il tavolo.
 */
export const TAMPONE_MASSIMO = 1_000;

type LeggiCronologia = (
  campagnaId: string,
  dopoSeq: number,
  limite: number,
) => Promise<readonly MessaggioSalvato[]>;

interface Iscritto {
  readonly campagnaId: string;
  readonly invia: Invio;
  inRecupero: boolean;
  tampone: EventoRealtime[];
  /** Sequenza piu' alta gia' consegnata a questo client. */
  ultimaInviata: number;
  troppoLento: boolean;
}

export class Hub {
  private readonly iscritti = new Set<Iscritto>();

  constructor(private readonly leggiCronologia: LeggiCronologia) {}

  get numeroIscritti(): number {
    return this.iscritti.size;
  }

  async sottoscrivi(campagnaId: string, dopoSeq: number, invia: Invio): Promise<() => void> {
    const iscritto: Iscritto = {
      campagnaId, invia, inRecupero: true, tampone: [],
      ultimaInviata: dopoSeq, troppoLento: false,
    };
    this.iscritti.add(iscritto);

    try {
      await this.recupera(iscritto, campagnaId, dopoSeq);
    } catch (e) {
      this.iscritti.delete(iscritto);
      throw e;
    }

    return () => { this.iscritti.delete(iscritto); };
  }

  private async recupera(i: Iscritto, campagnaId: string, dopoSeq: number): Promise<void> {
    let cursore = dopoSeq;
    for (;;) {
      const pagina = await this.leggiCronologia(campagnaId, cursore, PAGINA_RECUPERO);
      if (pagina.length === 0) break;
      for (const m of pagina) {
        i.invia({ tipo: 'messaggio.creato', messaggio: m });
        i.ultimaInviata = Math.max(i.ultimaInviata, m.seq);
      }
      cursore = pagina[pagina.length - 1]!.seq;
      if (pagina.length < PAGINA_RECUPERO) break;
    }

    // Da qui la diretta. Il tampone si svuota scartando cio' che il recupero ha gia'
    // consegnato: e' il punto in cui le due vie si ricongiungono.
    i.inRecupero = false;
    const arretrati = i.tampone;
    i.tampone = [];
    for (const e of arretrati) {
      if (e.messaggio.seq > i.ultimaInviata) {
        i.invia(e);
        i.ultimaInviata = e.messaggio.seq;
      }
    }
  }

  pubblica(evento: EventoRealtime): void {
    for (const i of this.iscritti) {
      if (i.campagnaId !== evento.messaggio.campagnaId) continue;

      if (i.inRecupero) {
        if (i.tampone.length >= TAMPONE_MASSIMO) {
          i.troppoLento = true;
          continue;
        }
        i.tampone.push(evento);
        continue;
      }

      if (evento.messaggio.seq <= i.ultimaInviata) continue; // gia' consegnato
      i.invia(evento);
      i.ultimaInviata = evento.messaggio.seq;
    }
  }

  /** Iscritti che hanno saturato il tampone: le loro connessioni vanno chiuse. */
  iscrittiDaScollegare(): readonly Invio[] {
    return [...this.iscritti].filter((i) => i.troppoLento).map((i) => i.invia);
  }
}
