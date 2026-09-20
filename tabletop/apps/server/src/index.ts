/**
 * Scheletro del server (S0-05). Volutamente minimo: in Sprint 0 deve solo
 * dimostrare che il compose sale, sopravvive a un riavvio del PC ed e'
 * raggiungibile da fuori casa attraverso il tunnel.
 *
 * Nessuna logica di dominio qui dentro: arriva in S1.
 */
import { createServer } from 'node:http';
import { statfs } from 'node:fs/promises';
import { livelloDisco, messaggioPerIlGm } from './disco.js';

const PORTA = Number(process.env['PORTA'] ?? 8080);
const VERSIONE = process.env['VERSIONE'] ?? 'sviluppo';
const PERCORSO_DATI = process.env['PERCORSO_DATI'] ?? '/dati';

async function statoDisco() {
  try {
    const s = await statfs(PERCORSO_DATI);
    const totale = s.blocks * s.bsize;
    const libero = s.bavail * s.bsize;
    const livello = livelloDisco(totale - libero, totale);
    return { totale, libero, livello, avviso: messaggioPerIlGm(livello) };
  } catch {
    return { totale: 0, libero: 0, livello: 'sconosciuto', avviso: null };
  }
}

const server = createServer((req, res) => {
  const invia = (codice: number, corpo: unknown) => {
    res.writeHead(codice, { 'content-type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(corpo));
  };

  if (req.url === '/salute') {
    invia(200, { stato: 'vivo', versione: VERSIONE });
    return;
  }

  if (req.url === '/stato') {
    // Pagina di stato per il GM (master prompt §10): e' da qui che si accorgera'
    // di un problema, mezz'ora prima della sessione.
    void statoDisco().then((disco) =>
      invia(200, {
        versione: VERSIONE,
        avviatoDa: Math.round(process.uptime()),
        disco,
        codaVocaleInAttesa: 0,
        ultimoBackup: null,
      }),
    );
    return;
  }

  invia(404, { errore: 'non trovato' });
});

server.listen(PORTA, () => {
  console.warn(`server in ascolto sulla porta ${PORTA} (versione ${VERSIONE})`);
});

for (const segnale of ['SIGTERM', 'SIGINT'] as const) {
  process.on(segnale, () => {
    console.warn(`ricevuto ${segnale}, chiusura ordinata`);
    server.close(() => process.exit(0));
  });
}
