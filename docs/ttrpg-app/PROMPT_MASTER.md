# Master Prompt — App mobile per sessioni TTRPG in remoto

> **Come si usa**: il blocco §0–§14 è il prompt da incollare a Claude Code in una
> sessione nuova. Le appendici A–D sono materiale di riferimento che il prompt
> richiama. Prima di lanciarlo, rispondi alle **Decisioni aperte (§13)**: sono le
> uniche che cambiano davvero l'architettura.

---

## 0. Ruolo e regole di ingaggio

Agisci come **Senior Mobile/Realtime Engineer + Tech Lead** con 10+ anni su
React Native, backend realtime e pipeline audio. Non sei un generatore di demo:
consegni software che deve funzionare in mano a persone reali per sessioni di 3–4 ore
consecutive, con test, CI, osservabilità e costi sotto controllo. La distribuzione è
privata (§13-D4): questo riduce gli adempimenti formali, **non** lo standard di
qualità tecnica.

Regole vincolanti per tutta la durata del lavoro:

1. **Zero ottimismo di facciata.** Se un requisito è tecnicamente fragile, costoso
   o non fattibile nei limiti dati, dillo subito con numeri (latenza in ms, costo
   per ora di sessione, MB, mAh) e proponi l'alternativa che regge.
2. **Niente API inventate.** Prima di usare una libreria o un servizio, verifica
   versione, API reale e pricing corrente. Se non puoi verificare, marca il punto
   come `ASSUNZIONE DA VERIFICARE` e non costruirci sopra silenziosamente.
3. **Niente mock spacciati per funzionanti.** Ogni stub è annotato
   `// STUB: <cosa manca, quale sprint lo chiude>`. Nessun `TODO` orfano a fine sprint.
4. **Decisioni tracciate.** Ogni scelta architetturale non banale produce un ADR in
   `docs/adr/NNNN-titolo.md` (contesto, opzioni valutate, decisione, conseguenze,
   costo di reversibilità).
5. **Lavoro a sprint.** Vedi §12. A fine sprint: build installabile, changelog,
   metriche, rischi aperti, e **stop per validazione**. Non iniziare lo sprint
   successivo senza il mio ok.
6. **Confini di scope.** Se ti accorgi che serve altro, non allargare: apri una voce
   in `docs/backlog.md` e chiedi.
7. **Segreti.** Nessuna chiave API nel bundle mobile, mai. Nessun segreto nei commit.
   Tutte le chiamate a provider STT/TTS passano dal backend.

---

## 1. Obiettivo del prodotto

App mobile **Android** (§13-D5) per giocare **giochi di ruolo da tavolo in remoto**, con
UX da app di messaggistica (paradigma WhatsApp: liste chat, thread, messaggi vocali,
allegati), specializzata sul tavolo da gioco.

Un tavolo = una *campagna* = una chat persistente con membri, ruoli (GM / giocatore),
cronologia, media e log dei tiri di dado.

**Non-goals v1.0** (esplicitamente fuori scope, vanno nel backlog):
- Battle map tattica / griglia con token e movimento.
- Gestione completa delle schede personaggio con automazione delle regole.
- Marketplace di avventure, monetizzazione, in-app purchase.
- Video-chiamata di gruppo.
- Client web/desktop.

---

## 2. Requisiti funzionali

Ogni requisito ha ID stabile: usalo nei commit (`feat(F3): ...`) e nei test.

### F1 — Account, tavoli, membri
- Registrazione/accesso: **codice di invito monouso, poi token di dispositivo**
  (ADR-009). Niente email, niente identità federate: entrambe richiederebbero un
  fornitore esterno, e risolvono un problema — identificare uno sconosciuto — che in
  una cerchia privata non esiste. Token conservati solo come impronta nel database;
  inviti con scadenza, revocabili e limitati in frequenza.
- Creazione tavolo, invito via link/QR con scadenza, ruoli `gm` | `player`,
  espulsione, abbandono, trasferimento GM.
- Profilo: nickname, avatar, fuso orario, lingua.

**AC**: un utente non membro non può leggere nulla del tavolo, verificato da test di
autorizzazione lato server (non solo lato UI).

### F2 — Chat realtime
- Messaggi testo, reply-to, edit (finestra 15 min, con marcatore), delete (tombstone),
  reazioni emoji, menzioni `@membro`, indicatore "sta scrivendo", ricevute di lettura.
- Ordinamento **deterministico e stabile**: sequenza monotona assegnata dal server,
  non il timestamp del client.
- **Offline-first**: coda outbox persistente, invio idempotente (client-generated
  `message_id` + idempotency key), riconciliazione al ritorno online, stati
  `pending | sent | delivered | failed` visibili.
- Paginazione cronologia a finestre, cache locale SQLite, cold start < 1,5 s a
  cronologia già sincronizzata.

**AC**: killando l'app durante l'invio con rete assente, al riavvio il messaggio
risulta ancora in coda e viene consegnato una volta sola (test E2E).

### F3 — Narrazione vocale → trascrizione in chat
Flusso: il GM (o qualunque membro) tiene premuto per parlare → l'audio viene
trascritto → **in chat compare il testo** insieme all'audio riproducibile.

- Registrazione push-to-talk, con waveform live e limite configurabile (default 120 s).
- STT asincrono: il messaggio appare immediatamente come `transcribing`, poi si
  popola. Nessun blocco della UI.
- **Editing della trascrizione** da parte dell'autore (lo STT sbaglia i nomi propri:
  è la norma, non l'eccezione).
- **Glossario di campagna**: nomi di PNG, luoghi e oggetti passati al motore STT come
  hint/boost per ridurre gli errori.
- Lingua italiana come default di tavolo. **Nessuna traduzione**: decisione §13-D1,
  solo trascrizione nella lingua parlata. La traduzione multilingua resta in
  `docs/backlog.md` e **non** va predisposta con astrazioni speculative.
- **Due livelli di motore STT, entrambi senza costo per minuto** (§4-bis):
  - **Tier 1 — riconoscimento eseguito sul server di casa. È il percorso
    principale.** Un modello di riconoscimento vocale eseguito in locale, scelto fra
    quelli con licenza libera e buona resa sull'italiano. Vantaggio decisivo:
    **accetta il condizionamento con il glossario di campagna**, cioè la mitigazione
    del rischio n.1 che il motore di sistema Android non permette. L'audio non lascia
    la rete di casa.
  - **Tier 0 — riconoscimento di sistema Android, sul telefono.** Ripiego quando il
    server non risponde, e unica via per trascrivere senza server. Qualità inferiore
    e nessun glossario possibile. Resta utile: è l'unico che funziona a server spento.
  - La selezione del tier è una policy server-side con ripiego automatico lato client
    quando il server è irraggiungibile, non una `if` sparsa nel codice.
  - **Dimensionamento del modello**: la scelta fra modelli più piccoli e più grandi è
    un compromesso fra accuratezza e tempo di elaborazione sull'hardware reale del
    PC. Va deciso **misurando su quel PC** (SPIKE-1), non scegliendo il modello più
    grande che esiste.
- **Coda di elaborazione sul server**: le trascrizioni sono lavori in coda, con
  limite di concorrenza tarato sull'hardware. Sei giocatori che mandano un vocale
  contemporaneamente non devono mettere in ginocchio il PC né far aspettare dieci
  minuti il primo della fila. Stato del lavoro visibile in chat.
- Fallback a cascata: Tier 1 → Tier 0 → solo audio con banner "trascrizione non
  disponibile". Nessuno di questi stati deve rompere la chat.

**AC**: WER misurato su uno stesso set di 20 clip italiane di narrazione fantasy,
**per entrambi i tier** e per il Tier 1 con e senza glossario, documentato in
`docs/quality/stt-benchmark.md`, insieme al **tempo di elaborazione sul PC reale**.
Nessun target inventato: si misura e si riporta il numero. Il confronto che decide il
progetto è **Tier 1 con glossario contro Tier 0**: se il guadagno sui nomi propri non
è netto, il self-hosting del riconoscimento non vale la complessità che aggiunge e va
riportato.

### F4 — Voci e modulazione (orco, elfo, nano, umano, …)
Requisito centrale e la parte tecnicamente più delicata. Implementa **un'interfaccia,
tre backend** (di cui due nella v1.0):

```
interface VoiceTransform {
  id: string; label: string; engine: 'dsp' | 'tts-local' | 'tts-cloud';
  render(input: AudioOrText, opts): Promise<AudioStream>;
}
```

- **Backend A — `dsp` (on-device, costo zero, sempre disponibile)**: pitch shift +
  formant shift + EQ + saturazione/riverbero leggero su grafo audio nativo Android
  (Oboe/AAudio + libreria di time-stretch/pitch-shift, con licenza commerciale
  verificata — attenzione: alcune librerie di pitch shifting sono GPL o a doppia
  licenza, verificalo **prima** di integrarle e registralo in `docs/licenses.md`).
  Latenza < 40 ms, funziona offline. Qualità "effetto scenico", non realismo.
  Preset di partenza in Appendice A.
- **Backend B — `tts-server` (sintesi neurale sul PC di casa). È il percorso
  principale.** Un motore di sintesi vocale con licenza libera, eseguito in locale,
  con voci italiane e più voci distinte assegnabili ai preset; l'uscita passa poi per
  la catena DSP per caratterizzarla ulteriormente. Costo marginale nullo, nessun dato
  che esce di casa, qualità superiore al motore di sistema del telefono. **Verifica la
  licenza del motore e delle singole voci**: alcuni modelli vocali di ottima qualità
  hanno licenze che escludono usi che qui non ci riguardano, ma vanno comunque lette
  e registrate in `docs/licenses.md`.
- **Backend C — `tts-device` (motore TTS di sistema Android)**: ripiego quando il
  server non risponde, e unica via a server spento. Qualità inferiore, ma la
  differenza fra "voce brutta" e "nessuna voce" è tutta a favore della prima.
- **Backend D — `sts` (speech-to-speech, conserva la recitazione)**: resta in
  `docs/backlog.md`, ma **per ragioni diverse da prima**. Non è più il costo a
  escluderlo: esistono modelli di conversione vocale eseguibili in locale. A
  escluderlo ora sono la complessità e il carico sull'hardware domestico, che è lo
  stesso che deve già trascrivere e sintetizzare. Rivalutabile dopo la v1.0, quando
  si saprà quanto margine ha davvero quel PC. Non lasciare codice morto o feature
  flag inerti nel frattempo.

Requisiti trasversali:
- Selettore voce persistente per membro **e** override per singolo messaggio (il GM
  interpreta più PNG nello stesso turno).
- Il messaggio in chat porta: testo, audio originale, audio trasformato; il lettore
  sceglie cosa ascoltare (originale / voce PNG) e può leggere solo il testo.
- Nessuna clonazione della voce di persone reali senza consenso esplicito e
  registrato. Voci preset only in v1.0: è anche un vincolo legale, non solo etico.
- **Nessun budget guard economico**: non esistendo un costo per minuto, la
  contabilizzazione in euro non serve e non va implementata. Il limite da sorvegliare
  è la **capacità di calcolo del PC** (coda con limite di concorrenza, come in F3) e
  lo **spazio su disco** (§4-bis punto 3).
- **Cache dell'audio renderizzato** indicizzata su `hash(testo + preset + versione
  motore)`: qui non risparmia denaro, risparmia tempo di CPU e attesa dei giocatori,
  che a questo punto è la risorsa scarsa.

**AC**: A/B ascoltabile dei backend A, B e C sui 4 preset, con latenza p50/p95 e
**costo per minuto di narrazione** misurati e tabellati in
`docs/quality/voice-benchmark.md`. Conclusione esplicita richiesta: quali preset
reggono a costo zero (A+B) e per quali il cloud vale davvero la spesa.

### F5 — Dadi
- Notazione completa: `NdX`, modificatori (`+3`, `-1`), keep/drop (`4d6kh3`, `2d20kl1`),
  vantaggio/svantaggio come alias espliciti, reroll (`r1`), exploding (`!`), gruppi
  multipli (`2d6+1d8+4`), tiro con etichetta (`2d20kh1+5 attacco spada lunga`).
  Parser con grammatica formale, non regex ad accumulo.
- **RNG autoritativo server-side, verificabile** (provably fair): per sessione il
  server pubblica `commit = SHA256(serverSeed)`; ogni tiro usa
  `HMAC-SHA256(serverSeed, clientSeed || nonce)` con **rejection sampling** per
  eliminare il bias modulo; a fine sessione il `serverSeed` viene rivelato e l'app
  permette di **riverificare ogni tiro**. Nessun tiro deciso dal client.
- Risultato in chat come messaggio strutturato: espressione, singoli dadi, totale,
  critico/fallimento critico evidenziati, autore, timestamp, link alla verifica.
- **Animazione**: il risultato è già deciso dal server; l'animazione è *scenografica e
  converge sulla faccia predeterminata*. Non derivare mai il risultato dalla fisica
  (non deterministica tra device). Implementazione: dadi 3D (d4/d6/d8/d10/d12/d20/d100)
  con GPU, ombre, materiali, impatto con haptics + SFX, durata 1,2–1,8 s, `skip` al tap.
- **Tre livelli di resa** agganciati alla fascia di prestazioni di §4-ter
  (`high` / `medium` / `low`), con override manuale dell'utente.
- **Rispetta la preferenza di sistema per l'animazione ridotta**: alternativa non
  animata obbligatoria (vincolo di accessibilità, non un nice-to-have). È una cosa
  diversa dalla fascia `low`: qui l'animazione si salta per scelta dell'utente, lì
  per limiti del device.
- Tiri privati del GM (visibili solo a lui, ma comunque loggati e verificabili).
- Test statistico: chi-quadro su 10^6 tiri per ogni tipo di dado, in CI.

**AC**: 60 fps in fascia `high` e almeno 30 fps stabili in `medium`, misurati sui
device reali del gruppo (§4-ter); dove non raggiungibile, la fascia declassa
automaticamente e il fatto compare in telemetria.

### F6 — Allegati media
- Immagini (mappe, handout, ritratti) e video brevi, da camera o galleria.
- **Selezione tramite photo picker di sistema** (§4-ter): con la baseline API 33
  questo evita di chiedere qualunque permesso sui media. Il permesso fotocamera resta
  necessario solo per lo scatto diretto, e va chiesto nel momento in cui serve.
- Compressione client-side prima dell'upload, **strip EXIF/GPS**, generazione
  thumbnail, upload resumibile con progress e retry, cancellazione.
- Limiti espliciti e comunicati, ora **tarati sul disco di casa e sulla banda in
  salita della linea domestica**, non su un listino: immagine ≤ 8 MB (ricompressa a
  lato lungo 2048 px), video ≤ 50 MB / 60 s, tutti configurabili senza toccare il
  codice. Il collo di bottiglia da verificare **non è il disco: è la banda in
  upload** della connessione di casa, che su molte linee domestiche è una frazione
  di quella in discesa. Sei giocatori che scaricano una mappa da 8 MB durante la
  sessione passano tutti da lì.
- **Nessuna scadenza automatica** (§13-D10): i contenuti restano finché il GM non
  cancella la campagna o fa pulizia a mano. Conseguenze da implementare:
  - **Cancellazione manuale che libera davvero lo spazio**: eliminare una campagna o
    un allegato deve rimuovere i file dal disco, non solo la riga dal database. Un
    test lo verifica contando i byte prima e dopo.
  - **Vista di occupazione per campagna** (§4-bis punto 3), perché il GM possa
    decidere cosa archiviare: senza un dato, la pulizia manuale non avviene mai.
  - **Deduplicazione dei file identici** su hash del contenuto: la stessa mappa
    rimandata tre volte occupa lo spazio di una. È poca fatica e con retention
    infinita rende molto.
- Visualizzatore full-screen con pinch-zoom, salvataggio in galleria, player video.
- URL firmati a TTL breve, nessuna directory servita in elenco: i media stanno su un
  disco di casa, ma restano raggiungibili da internet attraverso il tunnel.

### F7 — Notifiche push
- FCM per nuovo messaggio, menzione, inizio sessione, tiro del GM. Nessun APNs:
  niente iOS (§13-D5).
- **Permesso runtime per le notifiche obbligatorio** su questa baseline (§4-ter):
  chiedilo quando l'utente entra nel primo tavolo, spiegando a cosa serve. Se negato,
  l'app resta usabile e lo stato è visibile nelle impostazioni, con un percorso per
  concederlo dopo.
- Impostazioni per tavolo: tutti / solo menzioni / muto (con muto temporizzato).
- Payload minimo: nessun contenuto sensibile nella notifica se il tavolo è marcato
  privato.

### F8 — Sicurezza sociale (livello "cerchia privata")
La distribuzione è a invito, in un gruppo di persone che si conoscono (vedi §13-D4),
quindi **non** serve l'apparato di moderazione UGC richiesto dalla review degli store.
Resta il minimo indispensabile, che è comunque lavoro reale:
- Il GM può rimuovere qualunque messaggio o allegato del proprio tavolo (tombstone,
  con traccia in audit log).
- Espulsione membro con revoca immediata dell'accesso a cronologia e media.
- Inviti a scadenza e revocabili, non indovinabili.

**Deferito a `docs/backlog.md`** (da riaprire *prima* di qualunque pubblicazione
pubblica, perché l'App Store Guideline 1.2 li rende bloccanti): segnalazione
messaggio/utente, blocco fra utenti, coda di moderazione lato backend, EULA con
tolleranza zero, procedura di risposta entro 24 h. Non implementarli ora, ma
**progetta lo schema dati in modo che si possano aggiungere senza migrazione
distruttiva** (`messages` con `deleted_at` e `deleted_by`, tabella `reports` prevista
nel modello anche se inutilizzata).

### F9 — Privacy e conformità
**Il self-hosting (§13-D9) semplifica molto questo requisito**: nessun dato viene
trasferito a terzi, l'audio non lascia la rete di casa, non ci sono provider da
vincolare né opt-out dall'addestramento da verificare. Cade l'intero apparato di
conformità verso fornitori esterni.

Resta, e non è formalità:
- **Trasparenza su dove finiscono le registrazioni.** I giocatori devono sapere, in
  modo esplicito e in app, che audio, trascrizioni e allegati risiedono **sul
  computer di casa del GM**, che il GM vi ha accesso diretto e che la conservazione è
  a tempo indeterminato finché lui non cancella (§13-D10). È un'informazione che
  cambia come le persone si comportano al microfono: va data prima, non sepolta.
- **Cancellazione effettiva su richiesta**: un giocatore che chiede la rimozione dei
  propri messaggi deve ottenerla, file su disco compresi. Procedura manuale
  documentata in `docs/runbooks/`, non serve un flusso self-service.
- **Nessuna E2EE**: il server elabora l'audio per trascrizione e sintesi. Con il
  server in casa del GM il modello di fiducia è comunque diverso e più comprensibile
  di quello cloud — ma va detto, non lasciato intuire.

Deferito al backlog (necessario solo in caso di distribuzione pubblica): privacy
policy pubblica, age gate, export dati self-service.

### F10 — Accessibilità
Dynamic type, contrasto AA, label per screen reader su ogni controllo, alternative
non animate, sottotitoli sempre disponibili sui vocali (F3 li produce già),
target touch ≥ 44 pt, navigazione da tastiera esterna.

### F11 — Backup, esportazione e archiviazione della campagna
**Requisito nuovo e non negoziabile, conseguenza diretta di §13-D9 e §13-D10.** Con il
server in casa e la retention infinita, il disco di quel PC è **l'unica copia** di
anni di gioco. Un guasto del disco, un ransomware o un errore di manutenzione
cancellano tutto. La tua richiesta è di gestire archiviazione e cancellazione a mano:
perché sia una scelta e non un incidente in attesa, servono gli strumenti per farlo.

- **Backup automatico locale, attivo di default.** Dump periodico del database più
  copia incrementale dei media su un **secondo disco o percorso distinto** (una
  seconda copia sullo stesso disco non è un backup). Configurato nel compose, non
  lasciato all'iniziativa. Nessun servizio remoto, nessun costo.
- **Verifica del ripristino, non solo del salvataggio.** Un backup mai ripristinato
  non è un backup: il runbook contiene una procedura di ripristino **provata almeno
  una volta**, e lo Sprint 0 la include fra i criteri di completamento. È la cosa che
  tutti rimandano e che si paga una volta sola, male.
- **Esportazione di una campagna in un archivio autoconsistente**: un unico file con
  trascrizioni, cronologia, log dei tiri e media, più una versione leggibile senza
  l'app (HTML o Markdown con i media accanto). È ciò che ti permette di chiudere
  un'avventura, archiviarla e liberare spazio **senza perderla**. È anche la memoria
  della campagna, che per un gruppo che gioca da anni vale più del software.
- **Importazione dello stesso archivio**, così che l'esportazione sia reversibile e
  non un vicolo cieco.
- **Cancellazione di una campagna** con doppia conferma, riepilogo di cosa verrà
  eliminato e spazio che verrà liberato, e **proposta automatica di esportare prima**.
- Stato dell'ultimo backup visibile al GM in app: data, esito, dimensione. Un backup
  fallito da tre settimane e nessuno se n'è accorto è lo scenario da impedire.

---

## 3. Requisiti non funzionali (misurabili)

| Metrica | Target v1.0 | Come si misura |
|---|---|---|
| Cold start → chat usabile | < 1,5 s (device medio) | trace startup in CI perf |
| Latenza messaggio testo E2E | p95 < 400 ms, telefono ↔ server di casa attraverso il tunnel | timestamp server-client |
| Vocale: fine registrazione → trascrizione visibile | **da fissare dopo SPIKE-1**, misurata sul PC reale: non promettere un numero prima di averlo misurato | telemetria server |
| Sintesi vocale sul server: richiesta → primo byte audio | **da fissare dopo SPIKE-2** | telemetria server |
| TTS di sistema (ripiego): richiesta → primo byte | p95 < 300 ms | telemetria |
| Coda vocale con 6 richieste simultanee | nessuna richiesta oltre 3× il tempo della singola | test di carico |
| Animazione dado | 60 fps in fascia `high`, 30 fps floor in `medium`, fallback in `low` (§4-ter) | profiler sul device più debole del gruppo |
| Crash-free sessions | ≥ 99,5 % | raccolta crash sul server di casa |
| **Costo ricorrente in abbonamenti** | **0 €** (§13-D9) | verifica documentale |
| Consumo elettrico del server | misurato e riportato, non stimato | misuratore da presa (§4-bis) |
| Spazio su disco | avviso all'80 %, comportamento definito al 100 % | metrica + test a disco saturo |
| Ripristino da backup | **provato almeno una volta**, tempo di ripristino documentato | esercitazione (F11) |
| Consumo batteria sessione 3 h | ≤ 25 % sul device più debole del gruppo | misura manuale documentata |

**Device di riferimento** (§13-D6, dettaglio in §4-ter): Android 13 / API 33 come
minimo, device usciti dal 2023. I target sopra si misurano **sul telefono più debole
effettivamente in uso nel gruppo**, non su un modello ipotetico e non sull'emulatore.

---

## 4. Stack tecnologico

Prescritto, salvo ADR che motivi la deviazione:

**Mobile — solo Android** (decisione §13-D5)
- React Native (ultima release stabile) + **Expo (dev client + EAS Build)**, TypeScript `strict`.
- Navigazione: `expo-router`. Stato: Zustand (client) + TanStack Query (server state).
- Persistenza locale: SQLite (`expo-sqlite`) con Drizzle ORM; migrazioni versionate.
- UI: design system proprio minimale (token di spacing/colore/tipografia) — niente
  libreria UI pesante. Animazioni: Reanimated 3 + Gesture Handler. Grafica dadi: GPU
  3D (three.js su contesto nativo) con fallback sprite.
- Audio: **un solo modulo nativo, in Kotlin** (Oboe/AAudio per la catena DSP, motore
  TTS e `SpeechRecognizer` di sistema), esposto via Turbo Module. Nessun codice Swift,
  nessun doppio mantenimento.
- **Conseguenze dell'Android-only da sfruttare e da subire, entrambe**:
  - *Da sfruttare*: STT e TTS di sistema disponibili e gratuiti (F3 Tier 0, F4
    backend B); nessuna quota annuale di programma sviluppatori; installazione via
    APK senza intermediari; build eseguibili anche in locale senza consumare crediti
    cloud.
  - *Da subire*: la frammentazione. Il motore STT/TTS di sistema **non è lo stesso
    su tutti i device** (produttore, versione Android, pacchetti lingua installati).
    Serve una **matrice di device di test** dichiarata in `docs/devices.md` con i
    device reali del gruppo di gioco, e un rilevamento a runtime delle capacità
    (lingua italiana disponibile? riconoscimento offline supportato? quali voci TTS?)
    con degrado esplicito quando mancano. Non assumere nulla dal tuo emulatore.
  - Il porting su iOS resta possibile in futuro **solo se** il modulo nativo espone
    un'interfaccia platform-agnostic: definiscila bene ora, costa poco; non scrivere
    però alcuna implementazione iOS.

**Backend — self-hosted in casa** (decisione §13-D9)
- Gira interamente su un PC domestico dell'utente, raggiungibile da internet tramite
  DNS dinamico. Nessun servizio cloud a pagamento, nessun costo ricorrente di
  abbonamento.
- Node 22 + TypeScript, Fastify (REST) + WebSocket per il realtime, **Postgres**,
  media su **filesystem locale** (niente object storage a pagamento), Redis solo se
  serve davvero — su un tavolo da 6 persone probabilmente non serve, e un componente
  in meno da mantenere in casa vale più di un'ottimizzazione teorica. Motivalo in ADR.
- **Confezionamento obbligatorio: un unico `docker compose up -d`.** Il destinatario
  di questo software è una persona che la sera vuole giocare, non amministrare un
  server. Tutto dentro il compose: database, API, motori vocali, reverse proxy,
  connettore del tunnel, job di backup. Aggiornamento con un solo comando, rollback
  documentato.
- Riavvio automatico dei container, ripartenza dopo un'interruzione di corrente
  (impostazione del BIOS "riprendi all'arrivo dell'alimentazione": mettila nel
  runbook, è il genere di dettaglio che si scopre dopo il primo temporale).
- Rotazione dei log e tetto alla loro dimensione: un disco pieno di log è il modo più
  stupido di perdere una serata.

**Esposizione su internet — usa un tunnel in uscita, non l'inoltro delle porte**
- Requisito: **nessuna porta in ingresso aperta sul router di casa.** Il server
  stabilisce una connessione in uscita verso un servizio di tunnel e riceve da lì il
  traffico.
- Tre problemi risolti in un colpo solo: (1) funziona anche se l'operatore usa
  **CGNAT** e non fornisce un indirizzo IP pubblico — caso tutt'altro che raro sulle
  connessioni domestiche italiane, e che renderebbe **impossibile** l'inoltro delle
  porte; (2) certificato TLS valido senza gestirne il rinnovo a mano, e Android
  blocca il traffico in chiaro di default, quindi il TLS non è opzionale;
  (3) nessuna porta esposta significa una superficie di attacco verso la rete di casa
  enormemente ridotta.
- **Verifica prima di tutto il resto se la connessione è sotto CGNAT**: se lo è, la
  soluzione a inoltro di porte è morta in partenza e il tunnel non è una preferenza
  ma l'unica strada.
- Controlla i termini d'uso del servizio di tunnel scelto riguardo al **traffico di
  file grandi**: alcuni piani gratuiti limitano il transito di contenuti non-HTML
  voluminosi. Riguarda i video allegati (F6), non il resto. Se il vincolo esiste,
  documentalo e valuta di servire i media per via diversa.
- Il DNS dinamico resta utile come indirizzo stabile e come piano B, ma **non** come
  meccanismo principale di esposizione.

**Sicurezza di un servizio ospitato in casa** — il server sta nella rete dove ci sono
anche il NAS, la stampante e i computer di famiglia. Requisiti non negoziabili:
autenticazione robusta su ogni endpoint; Postgres **mai** esposto fuori dalla rete dei
container; container senza accesso alla rete dell'host; nessuna credenziale di
default; limitazione della frequenza delle richieste; aggiornamenti delle immagini
documentati nel runbook. Se l'API viene compromessa, la posta in gioco non è "un
tavolo di gioco": è la rete domestica.

**Motori vocali** — eseguiti in locale, scelti dopo gli spike dello Sprint 0 con
questi criteri, in quest'ordine: **licenza compatibile** con l'uso previsto, qualità
sull'italiano, **tempo di elaborazione sull'hardware reale del PC**, possibilità di
condizionare il riconoscimento con un glossario (F3), consumo di RAM. Nessun
lock-in: interfacce `SttProvider` e `TtsProvider` con due implementazioni ciascuna —
quella eseguita sul server e quella di sistema Android, che è sempre anche il
ripiego a server spento.

**Infra/CI**
GitHub Actions (lint, typecheck, test, build), EAS Build, canali
`development | preview | production`, feature flag serviti dal server di casa,
migrazioni DB automatiche con rollback testato e **provato su una copia del database
reale** prima di ogni rilascio: qui non c'è un fornitore che ripristina uno snapshot
al posto tuo.

**Osservabilità senza servizi esterni**: con il self-hosting, crash reporting e
metriche non possono appoggiarsi a un servizio a pagamento. Raccogli i crash e i log
strutturati **sul server di casa**, con rotazione e un limite di spazio, ed esponi
una pagina di stato minimale (server su/giù, spazio disco, code di elaborazione
vocale, ultimo backup) raggiungibile dal GM. Meno ricca di un servizio dedicato,
sufficiente per sei persone, e coerente con il vincolo di non avere costi ricorrenti.

**Distribuzione privata** (decisione §13-D4). Verifica i termini correnti dei
programmi sviluppatore nello Sprint 0 e riporta i costi reali; questo è il quadro da
cui partire:
- **Android**: build APK/AAB firmato con EAS, installazione diretta via link
  (sideload). Nessun account a pagamento necessario. Percorso più semplice, usalo
  come piattaforma di iterazione quotidiana.
- **iOS: fuori scope** (§13-D5). La quota annuale del programma sviluppatori Apple
  sarebbe da sola una frazione consistente del budget mensile di §4-bis, prima di
  qualunque riga di codice. Non scrivere, non configurare, non testare nulla per iOS.
- **Vincolo architetturale da non dimenticare**: il modulo audio nativo (F4) rende
  impossibile usare il client Expo generico. Serve una **dev/preview build custom**
  su ogni device di gioco, e ogni modifica al codice nativo richiede una nuova build
  installata a mano.
- **Keystore di firma**: generalo una volta, custodiscilo e documenta dove sta
  (`docs/runbooks/`). Perderlo significa che nessun device può più ricevere
  aggiornamenti in-place dell'APK. È il singolo punto di fallimento più banale e più
  fastidioso di tutta la distribuzione privata.
- **Installazione**: prevedi una pagina o un canale interno con l'APK corrente, il
  changelog e le istruzioni per l'installazione da origine sconosciuta. I giocatori
  non sono sviluppatori.
- **Vantaggio della cerchia privata da sfruttare**: l'aggiornamento del solo layer JS
  via **OTA update** (canale `preview`) consente iterazioni in giornata con il
  gruppo, senza reinstallazioni. Progetta di conseguenza: tieni il più possibile in
  JS e il modulo nativo sottile e stabile.

### §4-bis — Esercizio self-hosted: nessun costo ricorrente, altri costi sì

Decisione §13-D3/D9: **nessun abbonamento, nessun servizio a consumo.** Tutto gira sul
PC di casa. Questo elimina il vincolo economico che governava la versione precedente
di questo documento, e ne introduce altri tre che vanno guardati in faccia.

**1) "Nessun costo ricorrente" non significa "gratis". Significa che la bolletta
sostituisce la fattura.**
Fai il conto con i numeri reali del PC che userai, non con questi:

```
consumo_mensile_kWh = potenza_media_W × 24 × 30 / 1000
costo_mensile       = consumo_mensile_kWh × prezzo_kWh_della_tua_bolletta
```

Ordini di grandezza da verificare misurando (con un misuratore da presa, non a
occhio):
- Un PC desktop acceso 24/7 assorbe tipicamente diverse decine di watt a riposo. Su
  base mensile può arrivare a **costare quanto o più dell'abbonamento cloud che si
  voleva evitare**. Questo va detto chiaramente, non scoperto in bolletta.
- Un mini-PC a basso consumo sta un ordine di grandezza sotto ed è la scelta
  razionale per un servizio sempre acceso.
- **Se il PC è già acceso 24/7 per altri motivi, il costo marginale è vicino a zero**
  ed è il caso in cui questa scelta conviene senza discussione.

Non devi cambiare decisione: devi sapere quanto costa. Riporta il calcolo in
`docs/costs.md` con la potenza misurata e il prezzo dell'energia reale.

**2) Il costo vero non è in euro: è disponibilità e rischio di perdita dei dati.**
Un PC di casa non è un datacenter. Va progettato sapendo che:
- **Se il server è spento o la linea è giù, l'app non funziona per nessuno.** Con
  sessioni programmate due volte a settimana è probabilmente accettabile, ma deve
  essere **visibile**: l'app mostra uno stato di connessione al server chiaro e
  comprensibile ("il server del tavolo non risponde"), mai un errore generico o una
  rotella che gira per sempre.
- La chat è offline-first (F2), quindi scrivere funziona comunque e i messaggi
  partono al ritorno del server. **I dadi no**: F5 richiede un tiro autoritativo lato
  server, e questo è voluto. Server irraggiungibile = niente tiri. **Non introdurre un
  ripiego locale**: un tiro non verificabile che sembra un tiro verificabile è peggio
  del non poter tirare. Mostra il motivo e basta.
- **Il disco di quel PC è l'unica copia della campagna.** Un guasto cancella anni di
  gioco. Vedi F11.

**3) Il vincolo da sorvegliare cambia unità di misura: da euro a gigabyte.**
Con la retention infinita (§13-D10) lo spazio cresce in modo monotono e non torna mai
indietro. Il "budget guard" della versione precedente **non sparisce, cambia oggetto**:
- Monitoraggio dello spazio su disco con soglie di avviso (80 % e 90 %), mostrate **al
  GM dentro l'app**, non solo in un log che nessuno legge.
- Statistiche per campagna: quanto occupa, ripartito fra media, audio e database, così
  che il GM possa decidere cosa archiviare.
- Comportamento definito a disco pieno: l'app deve **rifiutare gli upload con un
  messaggio chiaro** e continuare a far funzionare chat e dadi. Un disco pieno non
  deve corrompere il database né far cadere il servizio. Test di accettazione
  obbligatorio, con disco saturato artificialmente.

**4) Conseguenza positiva, ed è grossa: i motori vocali diventano locali e gratuiti.**
Senza un costo per minuto da contenere, la strategia vocale migliora invece di
peggiorare. Vedi F3 e F4: riconoscimento e sintesi vocale **eseguiti sul PC di casa**,
a costo marginale nullo, con due vantaggi che il cloud non dava:
- **Il glossario di campagna torna possibile.** Era la mitigazione principale del
  rischio n.1 del progetto, ed era stata persa scegliendo il motore on-device di
  Android, che non accetta vocabolari personalizzati. Un riconoscitore eseguito in
  locale si può **condizionare con i nomi propri della campagna**. Questo è il
  guadagno tecnico più importante dell'intera decisione di self-hosting.
- **L'audio non esce mai dalla rete di casa.** F9 si semplifica drasticamente: niente
  trasferimento a terzi, niente opt-out dall'addestramento, niente accordi sul
  trattamento dei dati. Resta solo da dire chiaramente ai giocatori dove finiscono le
  loro registrazioni: sul computer del GM.

Il prezzo di questo vantaggio è la **latenza**, che dipende dall'hardware del PC e va
misurata (SPIKE-1 e SPIKE-2 dello Sprint 0), non assunta.

### §4-ter — Baseline device: cosa significa "dal 2023 in poi" in pratica

Decisione §13-D6. L'anno di uscita non è una condizione verificabile: va tradotto in
due criteri distinti, perché **fissa il piano software ma non quello hardware**.

**1) Piano software — questo sì che è garantito.** Un device uscito nel 2023 è nato
con Android 13 (API 33) o superiore, e nel 2026 ha verosimilmente ricevuto
aggiornamenti oltre. Fissa quindi `minSdk = 33`, `targetSdk` all'ultimo stabile.
Verifica ognuno dei punti seguenti in S0 prima di farci affidamento:
- **Permessi media granulari** (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`) al posto del
  vecchio permesso di storage. Meglio ancora: usare il **photo picker di sistema**,
  che per F6 consente di **non chiedere affatto un permesso sui media**. Fallo: è
  meno codice, meno attrito per i giocatori e meno superficie di privacy.
- **Permesso runtime per le notifiche** (`POST_NOTIFICATIONS`): da API 33 è
  obbligatorio chiederlo. Per F7 significa progettare il *momento* della richiesta
  (dopo che l'utente è entrato in un tavolo, non al primo avvio, o verrà negato).
- **Riconoscimento vocale on-device**: l'API dedicata esiste da prima di API 33,
  quindi con questa baseline è **presente per certo**. Attenzione alla distinzione che
  conta: *API presente* ≠ *modello italiano scaricato sul device*. Il rilevamento a
  runtime e il fallback di F3 restano necessari.
- **Preferenza di lingua per-app** disponibile a livello di sistema: usala per l'i18n
  invece di reinventarla.
- **Tipi di foreground service**: le versioni di Android successive richiedono di
  dichiarare il tipo di servizio in primo piano. Se la registrazione audio deve
  sopravvivere allo schermo spento, questo ti riguarda: dichiaralo correttamente e
  testalo, non scoprirlo quando un vocale si tronca a metà.
- **Allineamento delle pagine di memoria a 16 KB**: le versioni recenti di Android
  girano su device con pagine da 16 KB e le librerie native devono essere compilate di
  conseguenza. Ti riguarda direttamente, perché hai un **modulo nativo audio** (F4) e
  librerie native di terze parti. **Verificalo in S0 con una build su un device
  aggiornato**: è il tipo di problema che si manifesta come crash all'avvio, non come
  warning di compilazione.

**2) Piano hardware — questo NON è garantito, ed è l'errore da non fare.** Un telefono
economico del 2023 è più lento di un telefono di fascia alta del 2021: l'anno fissa il
sistema operativo, non la GPU. Per l'animazione 3D dei dadi (F5) e per la batteria,
quindi, **non classificare per anno ma per capacità misurata a runtime**:

- Al primo avvio determina una **fascia di prestazioni** (`high` / `medium` / `low`)
  combinando RAM disponibile, numero e classe dei core, versione delle API grafiche e
  — se necessario — un micro-benchmark di rendering di durata trascurabile eseguito
  una sola volta e memorizzato.
- Mappa: `high` → animazione 3D completa con ombre e materiali; `medium` → animazione
  3D semplificata (niente ombre dinamiche, meno campioni); `low` → sprite
  pre-renderizzati o fallback non animato.
- L'utente deve poter **forzare la fascia** dalle impostazioni, in entrambe le
  direzioni. Chi ha un device potente e vuole risparmiare batteria durante una
  sessione di 4 ore ha ragione quanto chi vuole l'effetto pieno.
- La fascia scelta e le sue conseguenze vanno in telemetria: serve per capire, dopo
  la prima sessione reale, se la classificazione ha indovinato.

**Conseguenza sul piano.** Questa baseline **riduce** il rischio di frammentazione
(§7.2) ma non lo annulla: motori vocali e voci TTS di sistema restano diversi tra
produttori anche su Android recenti. Sul calendario non cambia nulla: si risparmiano
i rami di compatibilità legacy, si spende in device tiering. Considerala una
riduzione di rischio, non un anticipo di consegna.

---

## 5. Architettura richiesta

- **Feature-sliced**: `src/features/{chat,voice,dice,media,campaign,auth}` con
  `domain / data / ui` interni. Il dominio non importa React né SDK di rete.
- **Motore dadi in `packages/shared`**, puro, senza I/O, identico su client e server:
  il client lo usa per preview e validazione, il server per il tiro autoritativo.
- **Pipeline audio come catena di step espliciti**
  `capture → encode(opus) → upload → stt → (translate?) → tts|sts|dsp → publish`,
  ogni step idempotente, riprendibile e osservabile; job su coda con retry
  esponenziale e dead-letter queue.
- **Realtime**: un canale per tavolo, autorizzazione al subscribe, eventi tipizzati
  (`message.created`, `message.updated`, `transcript.ready`, `audio.ready`,
  `dice.rolled`, `presence.changed`), sequenza monotona per canale, replay da
  `last_seq` alla riconnessione.
- **Autorizzazione difesa in profondità**: RLS Postgres *e* check applicativi.
  Ogni endpoint ha un test che verifica il rifiuto per un membro non autorizzato.

**Modello dati minimo** (da raffinare nell'ADR): `users`, `campaigns`,
`campaign_members(role)`, `messages(id, campaign_id, seq, author_id, kind, body,
reply_to, edited_at, deleted_at)`, `attachments`, `voice_messages(audio_uri,
transcript, transcript_edited, lang, voice_preset_id, rendered_audio_uri, status)`,
`dice_rolls(expression, parsed_json, results_json, total, server_seed_commit,
client_seed, nonce, private)`, `dice_sessions(seed_commit, seed_revealed_at)`,
`voice_presets`, `consents`, `reports`, `usage_counters`.

---

## 6. Qualità e Definition of Done

**DoD di ogni story**: codice + test + typecheck + lint puliti; test unitari sul
dominio; test di integrazione su API/DB; E2E (Maestro o Detox) sui flussi critici;
telemetria ed eventi di errore aggiunti; stringhe i18n (it + en, nessun literal in UI);
stati loading/empty/error/offline gestiti; accessibilità verificata; documentazione
aggiornata; **nessun regresso di performance** sulle metriche §3.

**Copertura**: 100 % su parser e RNG dei dadi (incluso test di distribuzione), ≥ 80 %
sul dominio, E2E sui percorsi: login → crea tavolo → invita → messaggio → vocale con
trascrizione → tiro → allegato.

**Sicurezza**: dependency audit in CI, secret scanning, rate limiting su tutti gli
endpoint pubblici, validazione input con Zod su ogni confine, upload con verifica del
MIME reale (magic bytes, non l'estensione).

---

## 7. Rischi da affrontare, non da scoprire dopo

Apri `docs/risks.md` e mantienilo aggiornato. Partenza obbligatoria:

1. **[ALTO] Qualità dello STT on-device sull'italiano fantasy.** È il rischio numero
   uno del progetto dopo le ultime decisioni: i nomi propri inventati sono il caso
   peggiore per qualunque ASR, e il motore di sistema Android **non accetta un
   glossario personalizzato** come farebbe una cloud. Se il Tier 0 produce
   trascrizioni da riscrivere a mano ogni volta, la funzione perde senso.
   Mitigazione: editing della trascrizione sempre disponibile (F3), Tier 1 cloud
   opt-in entro budget, e **misurazione nello Sprint 0 prima di costruirci sopra**.
   Se lo spike dà esito negativo, l'alternativa onesta è dichiarare la trascrizione
   "assistita" (bozza da correggere) invece che automatica.
2. **[MEDIO-ALTO, ridotto da §13-D6] Frammentazione Android su STT e TTS di
   sistema** (§4, §4-ter): la baseline API 33 elimina i rami legacy, ma **non**
   uniforma i motori vocali: voci TTS disponibili e presenza del modello italiano
   offline continuano a dipendere da produttore e configurazione del device.
   Mitigazione: rilevamento capacità a runtime, degrado esplicito, matrice device
   reale in `docs/devices.md`, test sui device effettivi del gruppo — mai
   sull'emulatore, che ha un corredo vocale non rappresentativo.
2-bis. **[MEDIO] Librerie native e pagine di memoria da 16 KB** (§4-ter): con un
   modulo audio nativo, un allineamento sbagliato si manifesta come crash all'avvio
   sui device Android più recenti, non come errore di build. Verifica in S0 su un
   device aggiornato, non in emulatore.
3. **[ALTO] Perdita dei dati della campagna** (§4-bis, F11): server in casa, disco
   singolo, retention infinita, nessuna copia remota. Un guasto o un errore cancella
   anni di gioco. È il rischio con la conseguenza peggiore di tutto il progetto,
   perché a differenza di un bug non si ripara. Mitigazione: F11 per intero, con la
   **prova di ripristino** già nello Sprint 0.
3-bis. **[ALTO] Raggiungibilità del server di casa**: CGNAT dell'operatore (che
   renderebbe impossibile l'inoltro delle porte), indirizzo IP che cambia,
   interruzioni di corrente e di linea, PC spento. Mitigazione: tunnel in uscita
   invece di porte aperte, riavvio automatico, stato di connessione chiaro in app.
   **Da verificare per primo nello Sprint 0**: se la linea è sotto CGNAT, alcune
   soluzioni sono escluse in partenza.
3-ter. **[MEDIO] Capacità di calcolo del PC di casa**: trascrizione e sintesi vocale
   girano sullo stesso computer, contemporaneamente, per sei persone. Mitigazione:
   code con limite di concorrenza, dimensionamento del modello deciso misurando
   (SPIKE-1/2), stato del lavoro visibile in chat.
3-quater. **[MEDIO] Banda in salita della linea domestica** (F6): sei device che
   scaricano media passano dall'upload di casa, che è tipicamente la frazione più
   stretta. Mitigazione: limiti sulle dimensioni, compressione, misura reale in S0.
4. **[MEDIO] Animazione 3D su Android di fascia media/bassa**: jank e consumo.
   Mitigazione: budget di performance fissato nello Sprint 0 sui device reali e
   fallback sprite pre-renderizzato.
5. **[MEDIO] Autonomia batteria** in sessioni di 3–4 ore con audio, schermo attivo e
   animazioni. Su Android di fascia media è un problema concreto: misuralo in S0 e
   riportalo, non rimandarlo alla beta.
6. **[BASSO] IP**: vedi §8. Il nome e l'iconografia sono un rischio legale, non grafico.
7. **[BASSO] Lock-in sui provider AI**: mitigato dall'interfaccia e dal fatto che il
   fallback on-device è sempre presente.
8. **[BASSO, ma non nullo] Consenso sull'elaborazione della voce** (F9): con
   distribuzione privata il rischio regolatorio è limitato, ma l'audio di persone
   reali esce comunque verso terzi quando il Tier 1 è attivo. Il consenso e il
   funzionamento completo in caso di rifiuto restano requisiti.

---

## 8. Vincoli legali e di IP (non negoziabili)

- **Non** usare marchi, logotipi, nomi di prodotto o testi di Wizards of the Coast /
  Hasbro. L'app non è "un'app di D&D": è uno strumento generico per giochi di ruolo,
  con branding proprio.
- Il **System Reference Document** rilasciato da WotC sotto licenza Creative Commons
  (SRD 5.1 e SRD 5.2) è utilizzabile **solo** rispettando l'attribuzione richiesta
  dalla licenza, e copre solo il contenuto effettivamente incluso nell'SRD — non il
  manuale completo, non i mostri iconici, non le ambientazioni. **Verifica il testo
  della licenza corrente prima di includere qualsiasi contenuto di regole** e
  registra l'esito in un ADR. In dubbio: non includere contenuto di regole; l'app
  funziona benissimo come strumento neutro (i dadi non sono brevettabili).
- Nessuno scraping di D&D Beyond o fonti equivalenti.
- Voci di sintesi: usare solo voci licenziate dal provider per uso commerciale.
  Nessuna imitazione di attori o personaggi riconoscibili.
- Asset grafici e sonori: solo licenze commerciali compatibili, con file
  `docs/licenses.md` che traccia origine e licenza di ogni asset.

---

## 9. Sicurezza — requisiti espliciti

Chiavi AI solo server-side; token di sessione a vita breve con refresh rotante;
storage locale cifrato per token (Keychain/Keystore); URL media firmati con TTL ≤ 10
min; rate limit per utente e per tavolo su messaggi, tiri, upload e chiamate AI;
audit log di azioni amministrative (espulsioni, cancellazioni); protezione contro
enumerazione degli inviti; nessun log di contenuto audio o testo dei messaggi negli
strumenti di osservabilità.

---

## 10. Osservabilità

Tutto autoprodotto sul server di casa, senza servizi a pagamento (§4 Infra/CI).
Raccolta dei crash dell'app con release health; eventi di prodotto (sessione aperta,
vocale inviato, trascrizione corretta a mano, voce usata, tiro effettuato); log
strutturati correlati da `trace_id` lungo tutta la pipeline audio, con rotazione e
tetto di spazio.

**Pagina di stato per il GM**, che è l'unico amministratore che questo sistema avrà:
server raggiungibile, spazio su disco e proiezione di riempimento, lunghezza delle
code vocali e tempo medio di elaborazione, esito e data dell'ultimo backup, versione
in esecuzione. Deve essere leggibile dal telefono, perché è da lì che il GM se ne
accorgerà, mezz'ora prima della sessione.

---

## 11. Consegna per sprint

A fine di **ogni** sprint produci, senza che io debba chiederlo:
1. **APK firmato installabile**, con changelog, pubblicato sul canale interno del
   gruppo; più l'eventuale OTA update sul canale `preview` se lo sprint ha toccato
   solo il layer JS.
2. `docs/sprints/sprint-NN.md`: fatto / non fatto e perché / metriche misurate /
   decisioni / rischi nuovi / debito tecnico creato con costo stimato di rientro.
3. ADR delle decisioni prese.
4. **Confronto onesto con il piano**: story point pianificati vs completati, e la
   causa reale degli scostamenti.
5. Demo script: i passi esatti per verificare a mano le nuove funzioni.
6. Le 3 domande a cui ti serve risposta per lo sprint successivo.

---

## 12. Piano di sprint (2 settimane, 1 senior full-time)

Assunzioni di capacità dichiarate: 10 giorni lavorativi per sprint, ~70 % di capacità
netta = **7 giorni-uomo utili**, 20 story point per sprint come riferimento. Se uno
sprint sfora, **non comprimere la qualità**: riporta lo scostamento e rinegozia lo scope.

| Sprint | Tema | Contenuto | Esito atteso |
|---|---|---|---|
| **S0** | Fondazioni e spike | Monorepo, CI, EAS, design system minimo, schema DB, ADR stack, keystore, APK installato sui **device reali del gruppo**, `minSdk 33` + classificatore di fascia (§4-ter), verifica dell'allineamento a 16 KB delle librerie native, tabella costi §4-bis con prezzi verificati, **spike misurati**: STT Tier 0 vs Tier 1 su clip fantasy italiane, TTS di sistema + DSP vs TTS cloud, catena DSP Kotlin, 3D dadi e batteria sul device più debole; verifica licenze §8 e della libreria di pitch shifting | Decisioni provider chiuse con numeri; **verdetto esplicito: il Tier 0 basta o no** |
| **S1** | Server di casa + auth + chat | Compose completo sul PC reale, tunnel raggiungibile da rete esterna, backup automatico attivo (F11 parte 1), F1, F2 (testo, realtime, outbox offline, ordinamento, cronologia) + push base "nuovo messaggio" | Il gruppo può già usarla come chat, **da fuori casa**, e darti feedback da qui in avanti |
| **S2** | Motore dadi | F5 parser + RNG verificabile + messaggio strutturato + test statistici e di sicurezza | Tiri corretti, provabili, non falsificabili |
| **S3** | Scenografia dadi | F5 animazione 3D, haptics, SFX, fallback ridotto/sprite, budget performance | L'effetto "wow" senza jank |
| **S4** | Voce → testo | F3 completo: registrazione, pipeline asincrona con coda sul server, riconoscimento locale condizionato dal glossario di campagna, editing, ripiego on-device | Il GM narra, il tavolo legge |
| **S5** | Voci e modulazione | F4: modulo nativo DSP + sintesi neurale sul server con code, preset orco/nano/elfo/umano, player a doppia traccia, ripiego a TTS di sistema | Il tavolo ascolta i PNG |
| **S6** | Media e rifiniture | F6 allegati, F7 notifiche complete, F8 minimo, F10 accessibilità | App completa sulle funzioni richieste |
| **S7** | Hardening e v1.0 privata | F9 trasparenza, **F11 backup ed esportazione con prova di ripristino**, sicurezza §9 con focus sull'esposizione da casa, pagina di stato, tuning batteria, **sessione di gioco reale da 3 h come test di accettazione**, bug bash, runbook di distribuzione e di esercizio del server | **v1.0 in mano al gruppo** |
| *S8+* | Post-1.0 | Voce live in tempo reale (room WebRTC + agent server-side), backend `sts`, iniziativa/turni, schede personaggio; **e, solo se si vuole pubblicare**: moderazione UGC completa, privacy policy, age gate, store listing (stimare 2 sprint) | roadmap successiva |

**Ordine scelto e perché**: dadi e voce (S2–S5) vengono prima dei media perché sono
le funzioni distintive e quelle con rischio tecnico alto — un rischio va colpito
presto, non rimandato. Gli allegati (S6) sono tecnologia nota e a basso rischio:
stanno bene in coda. La chat (S1) resta prima di tutto perché ogni altra funzione
consegna il proprio risultato *dentro* un messaggio.

**Punto di decisione dopo S0 — da rispettare.** Se lo spike dice che lo STT
on-device non regge sulla narrazione fantasy italiana e il cloud non sta nel budget,
**fermati e riporta**: le opzioni saranno (a) alzare il tetto di spesa, (b)
ridefinire la trascrizione come bozza da correggere a mano, (c) limitare la
trascrizione ai messaggi brevi. Non scegliere da solo e non proseguire come se il
problema non esistesse.

**Effetto di §13-D6 sul piano**: nessuno sul calendario. La baseline 2023+ toglie i
rami di compatibilità legacy e li sostituisce con il device tiering di §4-ter: è uno
scambio quasi alla pari in costo, ma **riduce il rischio**, che è il motivo per cui è
una buona decisione. Diffida di chi te la vende come un anticipo di consegna.

**Effetto del self-hosting (§13-D9/D10) sul piano.** Due movimenti opposti, che quasi
si annullano:
- *Tolgono lavoro*: niente integrazione con provider cloud, niente budget guard né
  contatori di spesa, niente job di retention, F9 dimezzato.
- *Aggiungono lavoro*: confezionamento in compose, tunnel ed esposizione sicura,
  esecuzione e dimensionamento dei motori vocali in locale con code e limiti di
  concorrenza, osservabilità autoprodotta, e soprattutto **F11 per intero**, che
  prima non esisteva.

Saldo: **circa mezzo sprint in più**, da 16 a **~17 settimane**. Il cambiamento vero
non è nel calendario ma nel **profilo di rischio**, che si sposta dal costo alla
disponibilità e alla perdita dei dati — e questi ultimi, a differenza di uno
sforamento di budget, non si recuperano.

**Stima onesta**: ~8,5 sprint = **~17 settimane / ~4 mesi calendario** per un singolo
senior a tempo pieno. L'Android-only fa risparmiare attrito reale (un solo modulo
nativo, una sola catena di firma e distribuzione, nessuna review), ma lo restituisce
in parte sotto forma di test sulla frammentazione dei motori vocali di sistema: il
saldo netto è circa **mezzo sprint**, non due. Rispetto alla versione originale
"store pubblici, due piattaforme" siamo passati da ~18 a ~16 settimane; il grosso del
lavoro — pipeline audio, realtime, animazione — non si è mosso, perché non dipendeva
da quelle decisioni. Con un secondo sviluppatore si parallelizzano S2–S3 (dadi) e
S4–S5 (voce), arrivando realisticamente a ~3 mesi.

**Primo momento in cui il gruppo può giocarci davvero**: fine S3 (~8 settimane) con
chat + dadi completi, narrando a voce con i messaggi vocali non ancora trascritti.
Da lì ogni sprint aggiunge valore su un'app già in uso — è il modo giusto di
sfruttare la cerchia privata.

---

## 13. Decisioni aperte (rispondi prima di iniziare)

Per ognuna è indicato il **default** che adotterò se non rispondi.

- **D1 — "trascrizione tradotta": trascrizione o traduzione?** → **DECISO:
  trascrizione**, nella lingua parlata. Nessuna traduzione, nessun feature flag,
  nessuna astrazione predisposta: la traduzione sta in `docs/backlog.md` e se
  servirà si aggiungerà come step della pipeline (F3 è già a step espliciti, quindi
  non è debito).
- **D2 — Voce live o messaggi vocali asincroni?**
  Default: **asincrono push-to-talk** in v1 (coerente con il paradigma WhatsApp,
  costi e complessità molto inferiori); room live in S9+.
- **D3 — Budget mensile per servizi cloud?** → **SUPERATA da D9: nessun costo
  ricorrente, nessun servizio a consumo.** Il tetto di 20 €/mese non è più il
  vincolo di progetto; al suo posto valgono i vincoli di §4-bis (energia elettrica,
  disponibilità, spazio su disco, capacità di calcolo). Il budget guard economico è
  **cancellato**, non rinviato: non scrivere contatori di spesa.
- **D4 — Distribuzione: store pubblici o cerchia privata?** → **DECISO: cerchia
  privata.** Nessuna pubblicazione sugli store in v1.0. Conseguenze già recepite in
  F8, F9, §4 e §12. Corollario vincolante: **progetta lo schema dati e i confini dei
  moduli in modo che l'apertura al pubblico resti possibile** senza riscritture
  (§F8), ma non pagare *ora* il costo di quella conformità.
- **D9 — Dove gira il server?** → **DECISO: PC di casa dell'utente, esposto tramite
  DNS dinamico, nessun servizio cloud a pagamento.** Conseguenze in §4 (backend,
  tunnel, sicurezza), §4-bis, F3, F4, F9, F11 e nel registro dei rischi.
  **Raccomandazione tecnica che accompagna la decisione**: esporre con un **tunnel in
  uscita** anziché aprendo porte sul router. Risolve insieme il caso CGNAT, il
  certificato TLS e la superficie di attacco verso la rete domestica. Il DNS dinamico
  resta come indirizzo stabile, non come meccanismo di esposizione.
- **D10 — Retention** → **DECISO: infinita.** Nessuna scadenza automatica; il GM
  cancella o archivia a mano a fine avventura. Conseguenze: F11 (backup, esportazione,
  cancellazione che libera spazio davvero) diventa requisito obbligatorio, e il
  presidio si sposta dallo scadere del tempo al **monitoraggio dello spazio su disco**
  (§4-bis punto 3).
- **D5 — Piattaforme** → **DECISO: solo Android.** Nessun codice, configurazione o
  test iOS. Modulo nativo unico in Kotlin. Coerente con D3: la sola quota annuale
  Apple avrebbe eroso una frazione rilevante dei 20 €/mese.
  **Requisito operativo aperto**: serve l'elenco reale dei device Android del gruppo
  di gioco (modello e versione Android) da mettere in `docs/devices.md`. Senza quello
  non si può fissare il target di performance né verificare i motori vocali.
- **D6 — Device Android di riferimento minimo?** → **DECISO: device usciti dal 2023
  in poi.** Tradotto in criteri verificabili in §4-ter, perché "anno di uscita" non è
  interrogabile a runtime: quello che il codice può leggere è il livello di API, il
  SoC, la RAM e le capacità GPU.
  **Resta da raccogliere** (non blocca S0, serve entro S1): l'elenco reale dei device
  del gruppo in `docs/devices.md`, per sapere su cosa si testa davvero e quale fascia
  di prestazioni è effettivamente rappresentata.
- **D7 — Dimensione del tavolo e durata sessione tipica** (serve per dimensionare
  fan-out e costi).
  Default: 6 membri, 3 ore, 2 sessioni/settimana per tavolo.
- **D8 — Nome e branding dell'app?**
  Default: nome provvisorio di lavoro, decisione rinviata a S7 con verifica di
  disponibilità del marchio prima del submit.

---

## 14. Primo passo che ti chiedo

**Non scrivere codice applicativo adesso.** Consegna, in quest'ordine:

1. Le domande bloccanti che restano dopo aver letto §13 (massimo 5).
2. Il piano dello **Sprint 0** in story con story point, dipendenze e criteri di
   accettazione, inclusi gli spike con la metrica esatta che ciascuno deve produrre.
3. Gli ADR proposti (solo titolo + opzioni da valutare) per: STT Tier 0 vs Tier 1 e
   scelta del provider cloud, TTS di sistema vs TTS cloud, backend (managed a tier
   gratuito vs VPS) e object storage senza egress, architettura del modulo audio
   Kotlin con la libreria di pitch shifting e la sua licenza, rendering 3D dei dadi,
   strategia di licenza dei contenuti di regole.
4. La tabella dei costi ricorrenti per lo scenario reale — **1 tavolo, 6 giocatori,
   2 sessioni/settimana** — separando costi fissi (hosting, quota sviluppatore Apple
   se D5 la richiede) e variabili (secondi STT, caratteri TTS, storage, egress), con
   fonti di pricing verificate e data di verifica. Aggiungi una seconda colonna a
   5 tavoli, come margine di crescita realistico per una cerchia privata.
5. La lista di ciò che, nella mia richiesta iniziale, secondo te **non regge** e cosa
   proponi al suo posto.

Poi fermati e aspetta il mio ok.

---

# Appendici

## Appendice A — Preset voce, punto di partenza per lo spike

| Preset | DSP (pitch / formant / colore) | Nota |
|---|---|---|
| Umano | bypass | riferimento |
| Nano | −3 st / −8 % / boost 200–400 Hz, leggera saturazione | voce di petto |
| Orco | −5 st / −15 % / growl + compressione, HP filter a 80 Hz | rischio intelligibilità: verificare |
| Elfo | +3 st / +6 % / riverbero ampio, air shelf 8 kHz | facile da esagerare |
| Goblin | +7 st / +12 % / compressione aggressiva, bitcrush leggero | usare con parsimonia |

Lo spike deve produrre, con file audio alla mano, **tre righe di verdetto per ogni
preset**: resa applicando il DSP alla voce umana registrata; resa applicando il DSP
al TTS di sistema; resa del TTS cloud. E la risposta alla domanda che conta: *quali
preset sono accettabili a costo zero?* Non decidere a tavolino.

Nota su un dettaglio che si scopre tardi: il pitch shifting di una voce umana **già
compressa** (l'audio del messaggio vocale è codificato per stare leggero) accumula
artefatti. Valuta se conservare l'audio a qualità più alta per il solo tempo
necessario al rendering, e misura l'impatto su storage e budget (§4-bis).

## Appendice B — Casi di test obbligatori sul motore dadi

`1d20`, `1d20+5`, `2d20kh1` (vantaggio), `2d20kl1` (svantaggio), `4d6kh3` ×6
(generazione statistiche), `8d6` (palla di fuoco), `1d100`, `2d6+1d8+4`,
`4d6r1` (reroll degli 1), `3d6!` (exploding), `1d20+5 attacco`, espressione vuota,
`0d6`, `1d0`, `1000000d6` (rifiuto con errore chiaro), overflow, notazione malformata,
input con unicode e spazi. Più: distribuzione chi-quadro, assenza di bias modulo,
riproducibilità della verifica del seed, e **impossibilità per il client di imporre
un risultato** (test di sicurezza dedicato).

## Appendice C — Prompt breve (se ti serve una versione compatta)

> Costruisci un'app mobile **Android** in React Native/Expo (TypeScript strict,
> monorepo, un solo modulo nativo in Kotlin, `minSdk 33` — device dal 2023 in poi) per
> giocare a giochi di ruolo da tavolo in remoto, con UX da app di messaggistica.
>
> **Vincolo di esercizio: nessun costo ricorrente.** Il backend gira su un PC di casa,
> confezionato in un unico `docker compose`, esposto con un **tunnel in uscita** e non
> aprendo porte sul router (funziona anche sotto CGNAT, TLS incluso, superficie di
> attacco verso la rete domestica ridotta). Riconoscimento e sintesi vocale sono
> **eseguiti su quel PC**: nessun servizio cloud, l'audio non lascia la rete di casa,
> e il glossario di campagna torna possibile. **Retention infinita**: niente scadenze
> automatiche, ma backup automatico su un secondo disco, esportazione e importazione
> di una campagna in archivio autoconsistente, cancellazione che libera davvero i byte
> e sorveglianza dello spazio su disco sono requisiti, non extra.
>
> Funzioni: (1) chat di gruppo realtime offline-first per campagna, ordinamento
> deterministico lato server e outbox persistente;
> (2) messaggi vocali trascritti automaticamente in chat (solo trascrizione italiana,
> nessuna traduzione): riconoscimento eseguito sul server e condizionato con il
> glossario di campagna, motore di sistema Android come ripiego a server spento,
> trascrizione sempre correggibile a mano;
> (3) riproduzione con voci di personaggio selezionabili (orco, nano, elfo, umano)
> tramite catena DSP nativa (pitch e formant shift su Oboe) applicata alla voce
> registrata o alla sintesi neurale eseguita sul server, con il TTS di sistema come
> ripiego, dietro un'unica interfaccia `VoiceTransform`;
> (4) tiri di dado con notazione completa (`4d6kh3`, `2d20kh1`, modificatori, reroll,
> exploding), RNG autoritativo server-side verificabile via commit/reveal del seed,
> animazione 3D che converge sul risultato già deciso dal server, resa su tre fasce di
> prestazioni classificate a runtime (l'anno del device fissa il sistema operativo,
> non la GPU) e alternativa non animata sempre disponibile;
> (5) allegati immagine e video con compressione, strip EXIF, URL firmati,
> deduplicazione su hash del contenuto e limiti tarati sulla banda in salita della
> linea domestica.
>
> Distribuzione **privata a invito via APK**: nessuna pubblicazione sugli store,
> quindi niente apparato di moderazione UGC, ma schema dati predisposto per
> aggiungerlo senza migrazione distruttiva. Vincoli: code con limite di concorrenza
> sui motori vocali, comportamento definito a disco pieno, autorizzazione difesa in
> profondità, trasparenza esplicita in app sul fatto che registrazioni e allegati
> risiedono sul computer del GM, rilevamento a runtime delle capacità vocali del
> device (la frammentazione Android resta un rischio), nessun marchio o contenuto
> Wizards of the Coast, i18n it/en, accessibilità AA.
>
> Lavora a sprint di 2 settimane: a ogni fine sprint build installabile, metriche
> misurate, ADR, scostamenti dal piano e stop per validazione.
> Prima di scrivere codice: domande bloccanti, piano Sprint 0 con spike misurati e
> verifica della raggiungibilità della linea di casa.

## Appendice D — Cosa NON promettere al committente

- Modulazione vocale realistica: si ottiene un effetto scenico convincente (DSP) e
  una sintesi neurale dignitosa, non il timbro di un doppiatore. La conversione
  speech-to-speech, che avrebbe conservato la recitazione, resta fuori dalla v1 — ora
  per il carico sul PC di casa, non per il costo.
- Trascrizione accurata sui nomi propri fantasy col solo motore on-device: il motore
  di sistema non accetta glossari personalizzati. La correzione a mano è parte del
  flusso, non un ripiego.
- Che l'app suoni e si comporti allo stesso modo su tutti gli Android: i motori
  vocali di sistema cambiano per produttore e configurazione, anche a parità di
  versione recente.
- Che "telefoni recenti" significhi "telefoni potenti": un entry-level del 2023 rende
  meno di un top di gamma del 2021. La baseline fissa le API, non le prestazioni.
- Animazione 3D fluida su qualunque Android senza un budget di performance e un fallback.
- Che distribuzione privata, Android-only e self-hosting rendano il progetto
  "piccolo": nel saldo complessivo si resta a ~17 settimane. Il costo sta nella
  pipeline audio, nel realtime e nell'animazione, che non dipendono da quelle scelte.
- Che "nessun costo ricorrente" significhi gratis: un PC acceso 24/7 ha una bolletta,
  e va calcolata sul consumo misurato (§4-bis).
- Che il server di casa sia sempre raggiungibile: spegnimenti, blackout, guasti di
  linea e CGNAT esistono, e a server giù l'app non funziona per nessuno.
- Che la retention infinita sia senza conseguenze: sposta il problema dal tempo allo
  spazio, e rende il backup l'unica cosa che separa il gruppo dalla perdita della
  campagna.
- Una v1 in "qualche settimana": il numero onesto è ~4 mesi per un senior; primo
  build davvero giocabile (chat + dadi) a ~8 settimane.
