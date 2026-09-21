# ADR-009 — Accesso senza servizi esterni

- **Stato**: accettato
- **Data**: 2026-09-21
- **Contesto**: F1 chiedeva "email magic link + Google Sign-In". Quella scelta è stata
  fatta quando il backend era in cloud. Con §13-D9 (server sul PC di casa) e §13-D3
  (nessun costo ricorrente) non regge più.

## Perché la scelta precedente non funziona qui

- **Magic link via email**: serve un servizio di invio posta. Mandare email da un
  indirizzo IP domestico significa finire nello spam nel 90% dei casi, quando non
  bloccati dall'operatore. Un servizio di invio ha un piano gratuito limitato e
  comunque è un fornitore esterno da configurare e sorvegliare.
- **Google Sign-In**: gratuito, ma richiede un progetto cloud, credenziali OAuth,
  un dominio verificato e la manutenzione di tutto questo. Per sei amici è un
  apparato sproporzionato, e reintroduce la dipendenza da terzi che D9 voleva togliere.

Entrambe risolvono un problema che qui non esiste: **stabilire l'identità di uno
sconosciuto**. Nella cerchia privata l'identità è già nota — sono persone che il GM
conosce e a cui manda un invito.

## Decisione

**Codice di invito monouso, poi token di dispositivo a lunga durata.**

1. Il GM genera un invito per il suo tavolo: un codice ad alta entropia, con scadenza.
2. Il giocatore lo inserisce una volta, sceglie un soprannome, e riceve un token che
   il telefono conserva nell'archivio protetto del sistema.
3. Da quel momento l'app è autenticata. Nessuna password da ricordare, nessuna email,
   nessun fornitore.

Requisiti di sicurezza, non opzionali:
- I token si conservano **solo come impronta** (hash) nel database: chi legge il
  database non ottiene credenziali utilizzabili.
- I codici di invito sono **monouso, con scadenza e revocabili**, e abbastanza lunghi
  da non essere indovinabili.
- Tentativi di accesso limitati in frequenza: un codice corto e un attaccante paziente
  sono la combinazione che rende inutile tutto il resto.

## Conseguenze

- **Perdere il telefono significa perdere l'accesso**: il GM emette un nuovo invito.
  Per sei persone è una procedura accettabile; per un'app pubblica non lo sarebbe.
- Nessun recupero password, perché non ci sono password.
- Se un giorno l'app uscisse dalla cerchia privata, questa decisione va rifatta da
  capo insieme a moderazione e privacy policy: sono tutte figlie dello stesso
  contesto.
- Il master prompt (F1) va aggiornato: la formulazione attuale descrive un mondo che
  non è più il nostro.
