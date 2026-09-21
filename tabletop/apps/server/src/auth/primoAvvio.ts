import { randomUUID } from 'node:crypto';
import type { Pool } from 'pg';
import { Accessi } from './accessi.js';

/**
 * Primo avvio su un database vuoto.
 *
 * Senza questo il server sarebbe inaccessibile: per creare un invito serve essere
 * GM, e per essere GM serve un invito. Il primo anello della catena lo mette qui il
 * server stesso, stampando il codice **nei log**.
 *
 * Perché nei log e non in una pagina web: chi legge i log di quel container è chi ha
 * accesso alla macchina, cioè il proprietario. Una pagina di configurazione iniziale
 * raggiungibile dalla rete sarebbe, per qualche minuto, una porta aperta — e la
 * storia del software domestico è piena di installazioni rimaste su quella schermata
 * per anni.
 */
export async function primoAvvio(pool: Pool, nomeCampagna = 'La mia campagna'): Promise<void> {
  const { rows } = await pool.query('SELECT count(*)::int AS n FROM utenti');
  if ((rows[0] as { n: number }).n > 0) return;

  const gmId = randomUUID();
  const campagnaId = randomUUID();
  await pool.query('INSERT INTO utenti (id, soprannome) VALUES ($1, $2)', [gmId, 'GM']);
  await pool.query('INSERT INTO campagne (id, nome) VALUES ($1, $2)', [campagnaId, nomeCampagna]);
  await pool.query(
    'INSERT INTO membri_campagna (campagna_id, utente_id, ruolo) VALUES ($1, $2, $3)',
    [campagnaId, gmId, 'gm'],
  );

  // L'invito è per il ruolo di GM: il primo a usarlo diventa il master del tavolo.
  const { codice, scadeIl } = await new Accessi(pool).creaInvito(campagnaId, 'gm', gmId);

  console.warn(
    '\n' + '='.repeat(64) +
    `\n  PRIMO AVVIO — tavolo "${nomeCampagna}" creato.` +
    `\n\n  Codice di invito per il GM:   ${codice}` +
    `\n  Valido fino al:               ${scadeIl.toLocaleString('it-IT')}` +
    '\n\n  Inseriscilo nell\'app al primo accesso. È monouso.' +
    '\n  Se lo perdi: ferma il server, svuota il database e riavvia.' +
    '\n' + '='.repeat(64) + '\n',
  );
}
