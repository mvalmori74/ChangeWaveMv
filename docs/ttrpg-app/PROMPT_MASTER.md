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
- Registrazione/login: email magic link + Google Sign-In. Nessun Apple Sign-In:
  senza build iOS, non serve.
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
- **Due livelli di motore STT** (conseguenza diretta del budget §4-bis):
  - **Tier 0 — on-device Android, costo zero**: riconoscimento vocale di sistema
    (`SpeechRecognizer`, con pacchetto lingua italiana scaricato per funzionare
    offline). Default di prodotto. Limiti reali: boost del vocabolario personalizzato
    assente o molto limitato, qualità inferiore sul parlato lungo, comportamento
    variabile tra produttori di device. Vanno misurati, non ipotizzati.
  - **Tier 1 — cloud, opt-in e contabilizzato**: usato quando l'utente lo attiva e il
    budget residuo lo consente. Guadagno atteso: accuratezza e **boost del glossario
    di campagna** (i nomi inventati sono esattamente ciò che il Tier 0 sbaglia).
  - La selezione del tier è una policy server-side, non una `if` sparsa nel client.
- Fallback a cascata: Tier 1 → Tier 0 → solo audio con banner "trascrizione non
  disponibile". Nessuno di questi stati deve rompere la chat.

**AC**: WER misurato su uno stesso set di 20 clip italiane di narrazione fantasy,
**per entrambi i tier** (Tier 1 con e senza glossario), documentato in
`docs/quality/stt-benchmark.md`. Nessun target inventato: si misura e si riporta il
numero reale. Se il delta Tier 1 − Tier 0 è marginale, **proponi di eliminare il
Tier 1** e liberare l'intero budget per il TTS.

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
- **Backend B — `tts-local` (on-device, costo zero)**: motore TTS di sistema Android,
  parametrizzato per voce/pitch/velocità e **poi passato per la catena DSP**. È la
  combinazione che rende possibile avere voci di personaggio **a costo marginale
  nullo e offline**, che è ciò che il budget di §4-bis richiede. Qualità: intelligibile
  ma sintetica. Da misurare, non da dare per buona.
- **Backend C — `tts-cloud` (opt-in, contabilizzato)**: TTS neurale di una cloud
  *commodity* (classe Azure/Google/Polly), non di un provider premium: vedi il calcolo
  in §4-bis, con 20 €/mese i provider di fascia alta sono fuori discussione. Streaming,
  cache aggressiva dell'audio renderizzato (stesso testo + stesso preset = nessuna
  seconda chiamata).
- **Backend D — `sts` (speech-to-speech, conserva la recitazione)**: **fuori budget,
  spostato in `docs/backlog.md`.** Era il risultato qualitativamente migliore ed è
  giusto sapere che lo si sta rinunciando per vincolo economico, non tecnico.
  Non lasciare codice morto o feature flag inerti per questo backend.

Requisiti trasversali:
- Selettore voce persistente per membro **e** override per singolo messaggio (il GM
  interpreta più PNG nello stesso turno).
- Il messaggio in chat porta: testo, audio originale, audio trasformato; il lettore
  sceglie cosa ascoltare (originale / voce PNG) e può leggere solo il testo.
- Nessuna clonazione della voce di persone reali senza consenso esplicito e
  registrato. Voci preset only in v1.0: è anche un vincolo legale, non solo etico.
- **Budget guard** (vedi §4-bis): contatore di secondi STT cloud e caratteri TTS
  cloud per tavolo e per mese, verificato **server-side** prima di ogni chiamata a
  pagamento, con soglia di allerta (80 %) e degrado automatico e silenzioso ai
  backend a costo zero al raggiungimento del tetto. Il degrado va **mostrato in app**
  con un'etichetta comprensibile, non subìto senza spiegazione.
- **Cache dell'audio renderizzato** indicizzata su `hash(testo + preset + versione
  motore)`: a un tavolo che rigioca le stesse frasi non si paga due volte.

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
- Limiti espliciti e comunicati, **derivati dal budget di §4-bis e non scelti a
  gusto**: immagine ≤ 8 MB (ricompressa a lato lungo 2048 px), video ≤ 25 MB / 30 s.
  Se il tier di storage gratuito scelto è più capiente del previsto, alzali con un
  cambio di configurazione, non di codice.
- **Retention dei media**: scadenza automatica configurabile (default 90 giorni) con
  possibilità per il GM di **fissare (`pin`)** fino a N elementi come permanenti — le
  mappe e gli handout servono per tutta la campagna, i meme del giovedì no. Senza
  questo meccanismo lo storage cresce in modo monotono e il tetto di 20 €/mese salta
  nel giro di pochi mesi. È un requisito, non un'ottimizzazione.
- **Retention dell'audio**: l'audio originale dei messaggi vocali scade prima
  (default 30 giorni); la **trascrizione resta per sempre** perché è testo e non
  costa nulla. Comunicalo in app: è anche il modo in cui la cronologia di campagna
  resta consultabile a costo zero.
- Visualizzatore full-screen con pinch-zoom, salvataggio in galleria, player video.
- URL firmati a TTL breve; nessun bucket pubblico; storage **senza costi di egress**
  (vedi §4-bis): con i video, l'egress è la voce che sorprende.

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
Scope ridotto per la distribuzione privata, **non azzerato**: la voce di una persona
è un dato personale anche se il gruppo è di amici, e l'audio esce comunque verso un
provider terzo quando lo STT/TTS cloud è attivo.

Obbligatorio in v1.0:
- Schermata di consenso esplicito e revocabile all'elaborazione cloud della voce,
  con versione e timestamp salvati; se negato, l'app resta pienamente usabile in
  modalità solo-DSP e solo-audio (nessuna trascrizione).
- Retention configurata e dichiarata su audio originale, audio renderizzato e
  trascrizioni; cancellazione effettiva dello storage, non solo del record.
- Cancellazione account con rimozione dei dati, senza flusso self-service elaborato:
  è accettabile una procedura manuale documentata in `docs/runbooks/`.
- Verifica che i provider scelti offrano **opt-out dall'addestramento sui dati
  inviati** (criterio di selezione nello spike S0, non un dettaglio).
- **Nessuna E2EE**: il server deve elaborare l'audio per STT/TTS. Va **detto
  chiaramente in app**, non nascosto in una policy.

Deferito al backlog (necessario prima di una distribuzione pubblica): privacy policy
pubblica, age gate, export dati self-service, DPA formalizzati.

### F10 — Accessibilità
Dynamic type, contrasto AA, label per screen reader su ogni controllo, alternative
non animate, sottotitoli sempre disponibili sui vocali (F3 li produce già),
target touch ≥ 44 pt, navigazione da tastiera esterna.

---

## 3. Requisiti non funzionali (misurabili)

| Metrica | Target v1.0 | Come si misura |
|---|---|---|
| Cold start → chat usabile | < 1,5 s (device medio) | trace startup in CI perf |
| Latenza messaggio testo E2E | p95 < 400 ms (stessa regione) | timestamp server-client |
| Vocale: fine registrazione → trascrizione visibile | p95 < 4 s per 30 s di audio | telemetria |
| TTS locale: richiesta → primo byte audio | p95 < 300 ms | telemetria |
| TTS cloud: richiesta → primo byte audio | p95 < 800 ms | telemetria |
| Animazione dado | 60 fps in fascia `high`, 30 fps floor in `medium`, fallback in `low` (§4-ter) | profiler sul device più debole del gruppo |
| Crash-free sessions | ≥ 99,5 % | Sentry |
| **Costo totale mensile (fisso + variabile)** | **≤ 20 €** (§4-bis) | dashboard costi per feature, allarme a 16 € |
| Costo marginale con budget esaurito | **0 €** (degrado a Tier 0) | test del budget guard |
| Occupazione storage a regime | entro il tier gratuito scelto | job di retention + metrica |
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

**Backend**
- Node 22 + TypeScript, Fastify (REST) + WebSocket per il realtime, Postgres,
  Redis (presence, rate limit, pub/sub fan-out), object storage S3-compatibile.
- Alternativa accettata per accelerare la v1: **Supabase** (Postgres + Auth +
  Realtime + Storage + RLS), **a patto** che l'accesso passi da un layer
  `packages/data-access` così il vendor resta sostituibile. Le chiamate ai provider
  AI restano su funzioni server-side con chiavi mai esposte.
- Monorepo pnpm: `apps/mobile`, `apps/api`, `packages/shared` (tipi + schema Zod +
  **motore dadi condiviso**), `packages/data-access`.

**Provider AI** — scelti dopo lo spike dello Sprint 0, con questi criteri, in
quest'ordine di priorità: **costo unitario e ampiezza del tier gratuito** (§4-bis),
qualità sull'italiano, latenza streaming, opt-out dall'addestramento, data residency
UE. Nessun lock-in: interfacce `SttProvider` e `TtsProvider` con almeno due
implementazioni ciascuna, di cui **una on-device a costo zero** (che è sempre anche
il fallback).

**Infra/CI**
GitHub Actions (lint, typecheck, test, build), EAS Build, canali
`development | preview | production`, Sentry, feature flag server-driven, migrazioni
DB automatiche con rollback testato.

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

### §4-bis — Budget operativo: 20 €/mese (vincolo di progetto)

Decisione §13-D3: **tetto di 20 €/mese, tutto compreso**. Non è un obiettivo di
risparmio, è un vincolo architetturale: determina la scelta dei motori vocali (F3,
F4), i limiti sui media (F6) e la politica di retention. Trattalo come tratteresti
un vincolo di memoria su un embedded.

**Metodo di calcolo da rifare con prezzi verificati nello Sprint 0** (riporta fonte e
data; i prezzi cambiano e non devono essere copiati da qui):

```
scenario di riferimento (§13-D7): 1 tavolo, 6 giocatori, 2 sessioni/settimana × 3 h
  = ~24 h/mese di sessione
  di cui parlato effettivamente registrato come messaggio vocale: stimare ~25-30%
  = ~6-7 h/mese di audio  ≈ 400 min
  trascrizione: ~150 parole/min → ~60.000 parole/mese → ~380.000 caratteri
costo_STT_mese   = 400 min × prezzo_al_minuto
costo_TTS_mese   = 380.000 char × prezzo_per_carattere × quota_messaggi_riascoltati
costo_storage    = GB accumulati (al netto della retention F6) × prezzo_GB
costo_egress     = GB scaricati dai 6 device × prezzo_GB   ← la voce dimenticata
costo_fisso      = hosting + DB + eventuale tier a pagamento
```

**Conclusioni già ricavabili, da confermare con i numeri reali:**
1. Il TTS è la voce dominante, non lo STT. Un provider TTS *premium* costa
   tipicamente un ordine di grandezza più di una cloud *commodity*: con questo tetto
   **i provider premium sono esclusi**, e anche una commodity va usata con cache e
   tetto. Da qui la scelta di F4 backend B (TTS di sistema + DSP) come **default**.
2. **Verifica i tier gratuiti permanenti** dei provider cloud (STT e TTS hanno spesso
   quote mensili gratuite): allo scenario di 1 tavolo è plausibile che il fabbisogno
   ci rientri quasi tutto. Se è così, il budget diventa riserva e non spesa corrente —
   ma **non progettare assumendolo**: il budget guard deve funzionare comunque.
3. **Storage: scegli un provider object-storage con egress gratuito.** Con i video
   allegati, il traffico in uscita verso 6 device può superare il costo dello storage
   stesso. Questo singolo punto vale più di molte micro-ottimizzazioni.
4. **Niente servizi con sospensione per inattività** su ciò che serve durante una
   sessione, o il primo tiro della serata aspetta il cold start. Verifica il
   comportamento dei tier gratuiti su questo punto specifico e documentalo.
5. Preferisci il tier gratuito di un managed service a un VPS a pagamento **solo se**
   i limiti reggono lo scenario; altrimenti un VPS minimale è più prevedibile. La
   scelta va in un ADR con i numeri, non a intuito.

**Requisiti implementativi del budget guard:**
- Contatori persistiti server-side (`usage_counters`), per tavolo e per mese, con
  reset a calendario e **verifica prima della chiamata** a pagamento, non dopo.
- Prezzi unitari in configurazione, non hard-coded: quando il provider cambia
  listino, si aggiorna un valore.
- Dashboard con spesa corrente, proiezione a fine mese e ripartizione per feature.
- Allarme all'80 % del tetto, degrado automatico al 100 %.
- **Test automatico del degrado**: con budget simulato esaurito, l'app deve restare
  pienamente funzionale a costo zero. È un test di accettazione, non una prova manuale.

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
3. **[MEDIO] Tetto di 20 €/mese** (§4-bis): un errore di stima sui prezzi unitari, o
   una retention non implementata, fa saltare il budget in silenzio. Mitigazione:
   budget guard server-side con verifica *prima* della chiamata, degrado automatico a
   costo zero, allarme all'80 %, retention F6 come requisito e non come cleanup
   rinviato.
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

Crash e performance (Sentry) con release health; eventi di prodotto (sessione aperta,
vocale inviato, trascrizione corretta a mano, voce usata, tiro effettuato) anonimizzati;
**dashboard costi per feature**: secondi STT, caratteri TTS, GB storage, GB egress,
con allarme a soglia; log strutturati correlati da `trace_id` lungo tutta la pipeline
audio.

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
| **S1** | Auth + tavoli + chat | F1, F2 (testo, realtime, outbox offline, ordinamento, cronologia) + push base "nuovo messaggio" | Il gruppo può già usarla come chat e darti feedback da qui in avanti |
| **S2** | Motore dadi | F5 parser + RNG verificabile + messaggio strutturato + test statistici e di sicurezza | Tiri corretti, provabili, non falsificabili |
| **S3** | Scenografia dadi | F5 animazione 3D, haptics, SFX, fallback ridotto/sprite, budget performance | L'effetto "wow" senza jank |
| **S4** | Voce → testo | F3 completo: registrazione, pipeline asincrona, glossario di campagna, editing, fallback | Il GM narra, il tavolo legge |
| **S5** | Voci e modulazione | F4: modulo nativo DSP + TTS cloud, preset orco/nano/elfo/umano, player a doppia traccia, budget guard | Il tavolo ascolta i PNG |
| **S6** | Media e rifiniture | F6 allegati, F7 notifiche complete, F8 minimo, F10 accessibilità | App completa sulle funzioni richieste |
| **S7** | Hardening e v1.0 privata | F9 consenso e retention, sicurezza §9, osservabilità §10, tuning costi e batteria, **sessione di gioco reale da 3 h come test di accettazione**, bug bash, runbook di distribuzione | **v1.0 in mano al gruppo** |
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

**Stima onesta**: 8 sprint = **16 settimane / ~4 mesi calendario** per un singolo
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
- **D3 — Budget mensile per API cloud (STT/TTS) e chi paga?** → **DECISO: 20 €/mese
  tutto compreso** (infrastruttura + AI + storage). Conseguenze in §4-bis, F3, F4, F6.
  Sintesi: motori on-device a costo zero come default di prodotto, cloud come
  opzione contabilizzata, retention dei media obbligatoria, provider premium esclusi.
- **D4 — Distribuzione: store pubblici o cerchia privata?** → **DECISO: cerchia
  privata.** Nessuna pubblicazione sugli store in v1.0. Conseguenze già recepite in
  F8, F9, §4 e §12. Corollario vincolante: **progetta lo schema dati e i confini dei
  moduli in modo che l'apertura al pubblico resti possibile** senza riscritture
  (§F8), ma non pagare *ora* il costo di quella conformità.
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
> monorepo, un solo modulo nativo in Kotlin, `minSdk 33` — device dal 2023 in poi)
> per giocare a giochi di ruolo da tavolo in remoto, con UX da app di messaggistica. **Vincolo economico vincolante: costo
> operativo totale ≤ 20 €/mese**, che impone motori vocali on-device come default e
> cloud solo come opzione contabilizzata con degrado automatico a costo zero.
> Funzioni: (1) chat di gruppo realtime offline-first per campagna, ordinamento
> deterministico lato server e outbox persistente;
> (2) messaggi vocali trascritti automaticamente in chat (solo trascrizione italiana,
> nessuna traduzione): STT di sistema Android come default gratuito, STT cloud opt-in
> con glossario di campagna entro budget, trascrizione sempre correggibile a mano;
> (3) riproduzione con voci di personaggio selezionabili (orco, nano, elfo, umano)
> tramite catena DSP nativa (pitch e formant shift su Oboe) applicata alla voce
> registrata o al TTS di sistema, più TTS cloud commodity come opzione, dietro
> un'unica interfaccia `VoiceTransform`;
> (4) tiri di dado con notazione completa (`4d6kh3`, `2d20kh1`, modificatori, reroll,
> exploding), RNG autoritativo server-side verificabile via commit/reveal del seed, e
> animazione 3D che converge sul risultato già deciso dal server, resa su tre fasce
> di prestazioni classificate a runtime (l'anno del device fissa il sistema operativo,
> non la GPU) e alternativa non animata sempre disponibile; (5) allegati immagine e video con compressione, strip EXIF, URL
> firmati, retention automatica e object storage senza costi di egress.
> Distribuzione **privata a invito via APK**: nessuna pubblicazione sugli store,
> quindi niente apparato di moderazione UGC, ma schema dati predisposto per
> aggiungerlo senza migrazione distruttiva. Vincoli: chiavi AI solo server-side,
> budget guard server-side verificato prima di ogni chiamata a pagamento, RLS +
> check applicativi, consenso esplicito e revocabile sull'elaborazione cloud della
> voce con fallback offline pienamente funzionante, rilevamento a runtime delle
> capacità vocali del device (la frammentazione Android e' un rischio alto), nessun
> marchio o contenuto Wizards of the Coast, i18n it/en, accessibilità AA. Lavora a
> sprint di 2 settimane: a ogni fine sprint build installabile, metriche misurate,
> ADR, scostamenti dal piano e stop per validazione.
> Prima di scrivere codice: domande bloccanti, piano Sprint 0 con spike misurati e
> tabella costi verificata.

## Appendice D — Cosa NON promettere al committente

- Modulazione vocale realistica a costo zero: con 20 €/mese si ottiene un effetto
  scenico convincente (DSP), non il timbro di un doppiatore. La conversione
  speech-to-speech, che avrebbe conservato la recitazione, è esclusa per budget.
- Trascrizione accurata sui nomi propri fantasy col solo motore on-device: il motore
  di sistema non accetta glossari personalizzati. La correzione a mano è parte del
  flusso, non un ripiego.
- Che l'app suoni e si comporti allo stesso modo su tutti gli Android: i motori
  vocali di sistema cambiano per produttore e configurazione, anche a parità di
  versione recente.
- Che "telefoni recenti" significhi "telefoni potenti": un entry-level del 2023 rende
  meno di un top di gamma del 2021. La baseline fissa le API, non le prestazioni.
- Animazione 3D fluida su qualunque Android senza un budget di performance e un fallback.
- Che distribuzione privata e Android-only rendano il progetto "piccolo": insieme
  tagliano circa uno sprint e mezzo su otto. Il costo sta nella pipeline audio, nel
  realtime e nell'animazione, che non dipendono da quelle scelte.
- Una v1 in "qualche settimana": il numero onesto è ~4 mesi per un senior; primo
  build davvero giocabile (chat + dadi) a ~8 settimane.
