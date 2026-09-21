/**
 * API HTTP e WebSocket del tavolo (Sprint 1).
 *
 * Volutamente senza framework: a questa scala Fastify aggiungerebbe una dipendenza
 * da aggiornare su un PC domestico senza amministratore, in cambio di comodita' che
 * qui non servono (ADR-008 segue lo stesso criterio per Redis). Se l'API crescesse,
 * la decisione va rifatta.
 */
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { WebSocketServer, type WebSocket } from 'ws';
import { z } from 'zod';
import type { ArchivioPostgres } from './db/archivio.js';
import { Hub } from './realtime/hub.js';

const UUID = z.string().uuid();

const CorpoMessaggio = z.object({
  id: UUID,
  autoreId: UUID,
  tipo: z.enum(['testo', 'vocale', 'tiro', 'allegato', 'sistema']),
  corpo: z.unknown(),
  rispostaA: UUID.optional(),
});

const LIMITE_CORPO_BYTE = 64 * 1024;

async function leggiCorpo(req: IncomingMessage): Promise<unknown> {
  const pezzi: Buffer[] = [];
  let byte = 0;
  for await (const p of req) {
    byte += (p as Buffer).length;
    // Il tetto si applica mentre si legge, non dopo: altrimenti un corpo enorme
    // occuperebbe comunque la memoria del PC di casa prima di essere rifiutato.
    if (byte > LIMITE_CORPO_BYTE) throw new ErroreHttp(413, 'corpo troppo grande');
    pezzi.push(p as Buffer);
  }
  if (pezzi.length === 0) return undefined;
  try {
    return JSON.parse(Buffer.concat(pezzi).toString('utf8'));
  } catch {
    throw new ErroreHttp(400, 'JSON non valido');
  }
}

export class ErroreHttp extends Error {
  constructor(readonly codice: number, messaggio: string) {
    super(messaggio);
  }
}

export function classificaErrore(e: unknown): { codice: number; messaggio: string } {
  if (e instanceof ErroreHttp) return { codice: e.codice, messaggio: e.message };
  if (e instanceof z.ZodError) {
    const dettaglio = e.issues
      .map((i) => `${i.path.join('.') || 'corpo'}: ${i.message}`)
      .join('; ');
    return { codice: 400, messaggio: `richiesta non valida — ${dettaglio}` };
  }
  if (e instanceof RangeError) return { codice: 400, messaggio: e.message };
  // Una campagna inesistente e' una richiesta sbagliata, non un guasto.
  if (e instanceof Error && /Campagna inesistente/.test(e.message)) {
    return { codice: 404, messaggio: e.message };
  }
  return { codice: 500, messaggio: 'errore interno' };
}

export interface Applicazione {
  readonly server: Server;
  readonly hub: Hub;
  chiudi(): Promise<void>;
}

export function creaApplicazione(archivio: ArchivioPostgres, extra?: {
  gestisciAltro?: (req: IncomingMessage, res: ServerResponse) => boolean;
}): Applicazione {
  const hub = new Hub((c, d, l) => archivio.leggiDa(c, d, l));

  const server = createServer((req, res) => {
    void gestisci(req, res).catch((e: unknown) => {
      // Una richiesta malformata e' colpa di chi la manda, non del server: deve
      // tornare 4xx. Senza questa riga gli errori di validazione uscivano come 500,
      // cioe' il client leggeva "il server e' rotto" mentre il rotto era il suo
      // messaggio, e il log si riempiva di allarmi per niente.
      const { codice, messaggio } = classificaErrore(e);
      if (codice >= 500) console.error('errore non gestito:', e);
      if (!res.headersSent) {
        res.writeHead(codice, { 'content-type': 'application/json; charset=utf-8' });
      }
      res.end(JSON.stringify({ errore: messaggio }));
    });
  });

  async function gestisci(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const url = new URL(req.url ?? '/', 'http://interno');
    const invia = (codice: number, corpo: unknown) => {
      res.writeHead(codice, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(corpo));
    };

    if (extra?.gestisciAltro?.(req, res)) return;

    const messaggi = /^\/campagne\/([^/]+)\/messaggi$/.exec(url.pathname);

    if (messaggi && req.method === 'POST') {
      const campagnaId = UUID.parse(messaggi[1]);
      const dati = CorpoMessaggio.parse(await leggiCorpo(req));
      // `rispostaA` assente e `rispostaA: undefined` non sono la stessa cosa con
      // exactOptionalPropertyTypes: la chiave si aggiunge solo se c'e' davvero.
      const salvato = await archivio.aggiungi({
        id: dati.id,
        autoreId: dati.autoreId,
        tipo: dati.tipo,
        corpo: dati.corpo,
        campagnaId,
        ...(dati.rispostaA !== undefined ? { rispostaA: dati.rispostaA } : {}),
      });

      // Solo i messaggi davvero nuovi si pubblicano: un reinvio non deve far
      // comparire due volte la stessa riga agli altri giocatori.
      if (!salvato.eraGiaPresente) {
        hub.pubblica({ tipo: 'messaggio.creato', messaggio: salvato });
      }
      // 200 invece di 201 quando c'era gia': il client distingue senza interpretare.
      invia(salvato.eraGiaPresente ? 200 : 201, salvato);
      return;
    }

    if (messaggi && req.method === 'GET') {
      const campagnaId = UUID.parse(messaggi[1]);
      const dopo = z.coerce.number().int().min(0).default(0).parse(url.searchParams.get('dopo') ?? 0);
      const limite = z.coerce.number().int().min(1).max(200).default(50)
        .parse(url.searchParams.get('limite') ?? 50);
      invia(200, await archivio.leggiDa(campagnaId, dopo, limite));
      return;
    }

    invia(404, { errore: 'non trovato' });
  }

  // --- WebSocket ---
  const wss = new WebSocketServer({ noServer: true });

  server.on('upgrade', (req, socket, testa) => {
    const url = new URL(req.url ?? '/', 'http://interno');
    if (url.pathname !== '/realtime') {
      socket.destroy();
      return;
    }
    wss.handleUpgrade(req, socket, testa, (ws) => {
      void collega(ws, url);
    });
  });

  async function collega(ws: WebSocket, url: URL): Promise<void> {
    const campagna = UUID.safeParse(url.searchParams.get('campagna'));
    const dopo = z.coerce.number().int().min(0).default(0)
      .safeParse(url.searchParams.get('dopo') ?? 0);

    if (!campagna.success || !dopo.success) {
      ws.close(4000, 'parametri non validi');
      return;
    }

    try {
      const disiscrivi = await hub.sottoscrivi(campagna.data, dopo.data, (evento) => {
        if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(evento));
      });
      ws.on('close', disiscrivi);
      ws.on('error', disiscrivi);
    } catch {
      ws.close(4001, 'recupero della cronologia fallito');
    }
  }

  return {
    server,
    hub,
    async chiudi() {
      wss.close();
      for (const c of wss.clients) c.terminate();
      await new Promise<void>((r) => server.close(() => r()));
    },
  };
}
