/**
 * Scheletro del server (S0-05). Volutamente minimo: in Sprint 0 deve solo
 * dimostrare che il compose sale, sopravvive a un riavvio del PC ed e'
 * raggiungibile da fuori casa attraverso il tunnel.
 *
 * Nessuna logica di dominio qui dentro: arriva in S1.
 */
import { statfs } from 'node:fs/promises';
import pg from 'pg';
import { creaApplicazione } from './app.js';
import { ArchivioPostgres, applicaSchema } from './db/archivio.js';
import { livelloDisco, messaggioPerIlGm } from './disco.js';
import { profiloIniziale, spiegaEsposizione, valutaEsposizione } from './diagnosi.js';
import { rilevaMacchina } from './rilevaMacchina.js';

const PORTA = Number(process.env['PORTA'] ?? 8080);
const VERSIONE = process.env['VERSIONE'] ?? 'sviluppo';
const PERCORSO_DATI = process.env['PERCORSO_DATI'] ?? '/dati';
const TUNNEL_ATTIVO = process.env['TUNNEL_ATTIVO'] === '1';

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

// Autodiagnosi all'avvio: il server si misura da solo invece di farsi dire com'e'
// fatta la macchina. Calcolata una volta sola, non a ogni richiesta.
const macchina = await rilevaMacchina();
const profilo = profiloIniziale(macchina);
console.warn(
  `macchina: ${macchina.coreLogici} core, ${macchina.ramGb} GB` +
  `${macchina.gpu ? `, GPU ${macchina.gpu}` : ', nessuna GPU rilevata'}`,
);
console.warn(`profilo di trascrizione proposto: ${profilo.modelloIniziale} — ${profilo.motivo}`);

const pool = new pg.Pool({ connectionString: process.env['DATABASE_URL'] });
await applicaSchema(pool);
const archivio = new ArchivioPostgres(pool);

// Le rotte di diagnosi restano qui, davanti a quelle dell'applicazione: devono
// rispondere anche se il resto ha problemi, perche' sono il modo in cui il GM
// scopre che li ha.
const app = creaApplicazione(archivio, {
  gestisciAltro(req, res) {
    const invia = (codice: number, corpo: unknown) => {
      res.writeHead(codice, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(corpo));
    };

    if (req.url === '/salute') {
      invia(200, { stato: 'vivo', versione: VERSIONE });
      return true;
    }

    if (req.url === '/stato') {
      // Pagina di stato per il GM (master prompt §10): e' da qui che si accorgera'
      // di un problema, mezz'ora prima della sessione.
      void statoDisco().then((disco) =>
        invia(200, {
          versione: VERSIONE,
          avviatoDa: Math.round(process.uptime()),
          disco,
          macchina,
          profiloTrascrizione: profilo,
          esposizione: {
            esito: valutaEsposizione(TUNNEL_ATTIVO, null),
            spiegazione: spiegaEsposizione(valutaEsposizione(TUNNEL_ATTIVO, null)),
          },
          clientCollegati: app.hub.numeroIscritti,
          codaVocaleInAttesa: 0,
          ultimoBackup: null,
        }),
      );
      return true;
    }

    return false;
  },
});

app.server.listen(PORTA, () => {
  console.warn(`server in ascolto sulla porta ${PORTA} (versione ${VERSIONE})`);
});

for (const segnale of ['SIGTERM', 'SIGINT'] as const) {
  process.on(segnale, () => {
    console.warn(`ricevuto ${segnale}, chiusura ordinata`);
    void app.chiudi().then(() => pool.end()).then(() => process.exit(0));
  });
}
