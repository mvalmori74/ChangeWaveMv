-- Schema iniziale (Sprint 1, F1 e F2).
--
-- Due scelte che meritano di essere spiegate qui, dove chi legge il codice le trova.

CREATE TABLE IF NOT EXISTS campagne (
  id           uuid PRIMARY KEY,
  nome         text NOT NULL CHECK (length(trim(nome)) > 0),
  creata_il    timestamptz NOT NULL DEFAULT now(),
  -- Contatore della sequenza di canale. Sta QUI, sulla riga della campagna, e non
  -- in una sequenza di Postgres, per due motivi:
  --  1) le sequenze non sono prive di buchi: un rollback consuma un numero e lo
  --     perde. Un buco nella cronologia renderebbe impossibile per il client
  --     distinguere "messaggio mancante" da "numero mai esistito", e la
  --     risincronizzazione da last_seq si baserebbe su una bugia.
  --  2) aggiornare questa riga prende un lock sulla singola campagna, quindi due
  --     tavoli diversi non si ostacolano mai a vicenda.
  ultimo_seq   bigint NOT NULL DEFAULT 0 CHECK (ultimo_seq >= 0)
);

CREATE TABLE IF NOT EXISTS utenti (
  id         uuid PRIMARY KEY,
  soprannome text NOT NULL CHECK (length(trim(soprannome)) > 0),
  creato_il  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS membri_campagna (
  campagna_id uuid NOT NULL REFERENCES campagne(id) ON DELETE CASCADE,
  utente_id   uuid NOT NULL REFERENCES utenti(id)   ON DELETE CASCADE,
  ruolo       text NOT NULL CHECK (ruolo IN ('gm', 'giocatore')),
  entrato_il  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (campagna_id, utente_id)
);

CREATE TABLE IF NOT EXISTS messaggi (
  -- Identificatore generato dal CLIENT: e' anche la chiave di idempotenza.
  -- Un reinvio dopo un timeout di rete non crea un doppione (F2, outbox).
  id            uuid PRIMARY KEY,
  campagna_id   uuid   NOT NULL REFERENCES campagne(id) ON DELETE CASCADE,
  autore_id     uuid   NOT NULL REFERENCES utenti(id),
  seq           bigint NOT NULL CHECK (seq > 0),
  tipo          text   NOT NULL CHECK (tipo IN ('testo', 'vocale', 'tiro', 'allegato', 'sistema')),
  corpo         jsonb  NOT NULL,
  risposta_a    uuid   REFERENCES messaggi(id),
  creato_il     timestamptz NOT NULL DEFAULT now(),
  modificato_il timestamptz,
  -- Cancellazione logica: la riga resta, cosi' i client che risincronizzano
  -- da last_seq vedono che il messaggio e' stato tolto invece di trovare un buco.
  cancellato_il timestamptz,
  cancellato_da uuid REFERENCES utenti(id),
  UNIQUE (campagna_id, seq)
);

-- La cronologia si legge sempre per campagna e per sequenza crescente.
CREATE INDEX IF NOT EXISTS idx_messaggi_cronologia ON messaggi (campagna_id, seq);
