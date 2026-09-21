/**
 * Inviti, sessioni e appartenenza ai tavoli su Postgres (ADR-009).
 */
import { randomUUID } from 'node:crypto';
import type { Pool } from 'pg';
import {
  FINESTRA_TENTATIVI_MS, generaCodiceInvito, generaToken, impronta, normalizzaCodice,
  troppiTentativi, valutaInvito,
} from './credenziali.js';

export type Ruolo = 'gm' | 'giocatore';

export interface Utente {
  readonly id: string;
  readonly soprannome: string;
}

export interface InvitoCreato {
  /** Il codice in chiaro esiste solo qui, nel momento in cui lo si crea. */
  readonly codice: string;
  readonly scadeIl: Date;
}

export type EsitoAccesso =
  | { readonly esito: 'ok'; readonly utente: Utente; readonly token: string; readonly campagnaId: string }
  | { readonly esito: 'rifiutato'; readonly motivoInterno: string }
  | { readonly esito: 'troppi_tentativi' };

export const DURATA_INVITO_MS = 7 * 24 * 60 * 60 * 1000;

export class Accessi {
  constructor(private readonly pool: Pool) {}

  async creaInvito(
    campagnaId: string, ruolo: Ruolo, creatoDa: string, durataMs = DURATA_INVITO_MS,
  ): Promise<InvitoCreato> {
    const codice = generaCodiceInvito();
    const scadeIl = new Date(Date.now() + durataMs);
    await this.pool.query(
      `INSERT INTO inviti (impronta, campagna_id, ruolo, creato_da, scade_il)
       VALUES ($1, $2, $3, $4, $5)`,
      [impronta(normalizzaCodice(codice)), campagnaId, ruolo, creatoDa, scadeIl],
    );
    return { codice, scadeIl };
  }

  async revocaInvitiDi(campagnaId: string): Promise<number> {
    const { rowCount } = await this.pool.query(
      `UPDATE inviti SET revocato_il = now()
        WHERE campagna_id = $1 AND usato_il IS NULL AND revocato_il IS NULL`,
      [campagnaId],
    );
    return rowCount ?? 0;
  }

  /**
   * Riscatta un invito: crea l'utente, lo iscrive al tavolo e apre una sessione.
   *
   * Tutto in una transazione con l'invito bloccato: due persone che usano lo stesso
   * codice nello stesso istante non devono entrare entrambe con un invito monouso.
   */
  async riscatta(codice: string, soprannome: string, origine: string): Promise<EsitoAccesso> {
    if (troppiTentativi(await this.tentativiRecenti(origine))) {
      return { esito: 'troppi_tentativi' };
    }

    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const { rows } = await client.query(
        'SELECT * FROM inviti WHERE impronta = $1 FOR UPDATE',
        [impronta(normalizzaCodice(codice))],
      );
      const riga = rows[0] as {
        campagna_id: string; ruolo: Ruolo; scade_il: Date;
        usato_il: Date | null; revocato_il: Date | null;
      } | undefined;

      if (riga === undefined) {
        await client.query('COMMIT');
        await this.registraTentativo(origine, false);
        return { esito: 'rifiutato', motivoInterno: 'codice inesistente' };
      }

      const stato = valutaInvito(
        { scadeIl: riga.scade_il, usatoIl: riga.usato_il, revocatoIl: riga.revocato_il },
        new Date(),
      );
      if (stato !== 'valido') {
        await client.query('COMMIT');
        await this.registraTentativo(origine, false);
        return { esito: 'rifiutato', motivoInterno: stato };
      }

      const utenteId = randomUUID();
      await client.query('INSERT INTO utenti (id, soprannome) VALUES ($1, $2)',
        [utenteId, soprannome.trim()]);
      await client.query(
        'INSERT INTO membri_campagna (campagna_id, utente_id, ruolo) VALUES ($1, $2, $3)',
        [riga.campagna_id, utenteId, riga.ruolo]);
      await client.query(
        'UPDATE inviti SET usato_da = $1, usato_il = now() WHERE impronta = $2',
        [utenteId, impronta(normalizzaCodice(codice))]);

      const token = generaToken();
      await client.query(
        'INSERT INTO sessioni (impronta, utente_id, descrizione) VALUES ($1, $2, $3)',
        [impronta(token), utenteId, 'telefono']);

      await client.query('COMMIT');
      await this.registraTentativo(origine, true);
      return {
        esito: 'ok',
        utente: { id: utenteId, soprannome: soprannome.trim() },
        token,
        campagnaId: riga.campagna_id,
      };
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }

  /** Riconosce il portatore di un token. `null` se non valido o revocato. */
  async autentica(token: string | null): Promise<Utente | null> {
    if (token === null || token === '') return null;
    const { rows } = await this.pool.query(
      `SELECT u.id, u.soprannome FROM sessioni s
         JOIN utenti u ON u.id = s.utente_id
        WHERE s.impronta = $1 AND s.revocata_il IS NULL`,
      [impronta(token)],
    );
    const r = rows[0] as Utente | undefined;
    if (r === undefined) return null;
    // Aggiornamento opportunistico: se fallisce, l'autenticazione resta valida.
    void this.pool
      .query('UPDATE sessioni SET ultimo_uso = now() WHERE impronta = $1', [impronta(token)])
      .catch(() => undefined);
    return r;
  }

  async revocaSessione(token: string): Promise<void> {
    await this.pool.query(
      'UPDATE sessioni SET revocata_il = now() WHERE impronta = $1', [impronta(token)]);
  }

  /** Ruolo dell'utente nel tavolo, oppure `null` se non ne fa parte. */
  async ruoloNella(campagnaId: string, utenteId: string): Promise<Ruolo | null> {
    const { rows } = await this.pool.query(
      'SELECT ruolo FROM membri_campagna WHERE campagna_id = $1 AND utente_id = $2',
      [campagnaId, utenteId],
    );
    return (rows[0] as { ruolo: Ruolo } | undefined)?.ruolo ?? null;
  }

  /**
   * Espelle un membro e **revoca tutte le sue sessioni**: senza questo, chi viene
   * espulso conserva un token valido e continua a leggere il tavolo dalla diretta.
   */
  async espelli(campagnaId: string, utenteId: string): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query(
        'DELETE FROM membri_campagna WHERE campagna_id = $1 AND utente_id = $2',
        [campagnaId, utenteId]);
      await client.query(
        'UPDATE sessioni SET revocata_il = now() WHERE utente_id = $1 AND revocata_il IS NULL',
        [utenteId]);
      await client.query('COMMIT');
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }

  private async tentativiRecenti(origine: string): Promise<number> {
    const { rows } = await this.pool.query(
      `SELECT count(*)::int AS n FROM tentativi_accesso
        WHERE origine = $1 AND riuscito = false AND quando > now() - ($2 || ' milliseconds')::interval`,
      [origine, FINESTRA_TENTATIVI_MS],
    );
    return (rows[0] as { n: number }).n;
  }

  private async registraTentativo(origine: string, riuscito: boolean): Promise<void> {
    await this.pool.query(
      'INSERT INTO tentativi_accesso (origine, riuscito) VALUES ($1, $2)',
      [origine, riuscito]);
  }
}
