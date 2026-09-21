# Sprint 1 — Server di casa, autenticazione e chat

**Stato**: in corso. Questo documento si aggiorna mentre lo sprint procede.

Obiettivo: alla fine il gruppo deve poter usare l'app come chat, **da fuori casa**, e
cominciare a darti feedback. Tutto il resto degli sprint successivi consegna il
proprio risultato dentro un messaggio, quindi questa è la fondazione.

---

## Fatto e verificato

### Nucleo dei messaggi (F2)

- Schema Postgres con contatore di sequenza sulla riga della campagna.
- `ArchivioPostgres`: inserimento idempotente sull'id generato dal client, lettura a
  pagine, sequenze indipendenti per campagna.
- **11 test di integrazione su Postgres 16 reale**: 50 inserimenti simultanei danno
  sequenze uniche e senza buchi; 8 reinvii simultanei danno una riga sola.

### Distribuzione in tempo reale

- `Hub`: recupero della cronologia da `last_seq` e diretta che convivono senza perdite
  né duplicati, con tetto al tampone per non consumare la memoria del PC di casa.
- **12 test**, fra cui il caso che rompe le implementazioni ingenue: messaggi che
  arrivano *durante* il recupero.

### API

- `POST /campagne/:id/messaggi` — 201 se nuovo, **200 se era già presente**, così il
  client distingue senza dover interpretare.
- `GET /campagne/:id/messaggi?dopo=&limite=` — cronologia a pagine.
- `WS /realtime?campagna=&dopo=` — diretta con recupero.
- **13 test end-to-end** con server HTTP, WebSocket e Postgres veri.

### Outbox del client (F2, lato telefono)

Macchina a stati pura in `packages/shared`: attesa crescente con tetto, distinzione
fra guasto di rete e rifiuto del server, ripresa dopo la chiusura dell'app. 12 test.

---

### Accesso e autorizzazione (F1)

Decisione in ADR-009: **codice di invito monouso, poi token di dispositivo**. Niente
email né identità federate, che richiederebbero un fornitore esterno e risolvono un
problema — identificare uno sconosciuto — che in una cerchia privata non esiste.

- Codici a 12 caratteri su un alfabeto senza simboli confondibili, con scarto dei
  valori che introdurrebbero bias; token e codici conservati **solo come impronta**.
- Inviti monouso, con scadenza, revocabili; tentativi limitati in frequenza.
- **Primo avvio**: su database vuoto il server crea il tavolo e stampa nei log il
  codice per il primo GM. Senza, il server sarebbe inaccessibile: per creare un
  invito serve essere GM, e per essere GM serve un invito.
- **18 test di autorizzazione**, quelli che il master prompt chiede esplicitamente.

Due scelte di risposta che meritano una riga:

| Situazione | Risposta | Perché |
|---|---|---|
| Estraneo che chiede un tavolo | **404**, non 403 | dire "non sei membro" confermerebbe che il tavolo esiste a chi prova identificatori a caso |
| Membro che non è GM | **403** | sa già che il tavolo esiste: non c'è nulla da nascondergli |
| Codice di invito rifiutato | messaggio unico | distinguere "scaduto" da "inesistente" permetterebbe di esplorare lo spazio dei codici. Il motivo resta nei log |

**L'espulsione revoca anche le sessioni.** Toglierlo dai membri non basta: con un
token ancora valido continuerebbe a leggere la diretta finché non scade. C'è un test
che lo verifica.

## Reperti

### R5 — I test dai sorgenti non vedono i bug di confezionamento

`schema.sql` non veniva copiato in `dist/`: i test passavano perché girano dai
sorgenti, dove il file c'è. Nell'immagine Docker, che contiene solo `dist/`, il
server si fermava all'avvio con "file non trovato".

Sarebbe emerso sul PC di casa, al primo `docker compose up`.

*Rimedio*: script `copia-risorse.mjs` nel passo di build, `pnpm build` aggiunto a
`pnpm check`, Dockerfile che esegue la build invece del solo controllo dei tipi.

*Regola che ne discende*: **la verifica non è finita finché non si è eseguito
l'artefatto costruito**, non solo i test.

### R6 — Un errore di validazione usciva come 500

Una richiesta malformata tornava al client come "errore interno del server". Due
danni: il client legge che il server è rotto mentre il rotto è il suo messaggio, e il
log si riempie di allarmi per niente.

Trovato da un test scritto apposta per distinguere le due colpe. Ora c'è
`classificaErrore`, con un test che verifica anche che **un 500 non riveli dettagli
interni** (una stringa di connessione con la password dentro, per esempio).

### R7 — Test in parallelo sullo stesso database

I due file di test di integrazione azzeravano le stesse tabelle. Ciascuno, eseguito da
solo, passava; **insieme fallivano 13 test su 87**. È il guasto che fa perdere un
pomeriggio, perché la prima reazione è rieseguire il singolo file e vederlo verde.

*Rimedio*: ogni file di test lavora in uno schema Postgres suo, creato e distrutto dal
file stesso. Il parallelismo resta, l'interferenza no.

### R8 — Niente framework HTTP, per ora

Scelta coerente con ADR-008 (niente Redis): a questa scala un framework aggiungerebbe
una dipendenza da aggiornare su un PC domestico senza amministratore, in cambio di
comodità che qui non servono. Se l'API crescesse, la decisione va rifatta — è scritto
nel file.

---

## Da fare

- [x] ~~Autenticazione e tavoli (F1)~~ — inviti, ruoli, espulsione
- [x] ~~Autorizzazione verificata da test~~ — 18 test
- [ ] Persistenza locale sul telefono (SQLite) e coda outbox collegata alla rete
- [ ] Schermata di chat: elenco, invio, stati di consegna, indicatore di riconnessione
- [ ] Notifiche push di base
- [ ] CI (arretrata dallo Sprint 0)
