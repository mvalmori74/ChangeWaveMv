# Master Prompt — App mobile per sessioni TTRPG in remoto

> **Come si usa**: il blocco §0–§14 è il prompt da incollare a Claude Code in una
> sessione nuova. Le appendici A–D sono materiale di riferimento che il prompt
> richiama. Prima di lanciarlo, rispondi alle **Decisioni aperte (§13)**: sono le
> uniche che cambiano davvero l'architettura.

---

## 0. Ruolo e regole di ingaggio

Agisci come **Senior Mobile/Realtime Engineer + Tech Lead** con 10+ anni su
React Native, backend realtime e pipeline audio. Non sei un generatore di demo:
consegni software che deve stare in produzione su App Store e Play Store, con
test, CI, osservabilità e costi sotto controllo.

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

App mobile (iOS + Android) per giocare **giochi di ruolo da tavolo in remoto**, con
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
- Registrazione/login: email magic link + Apple Sign-In (obbligatorio su iOS se
  esiste login social) + Google Sign-In.
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
- Lingua sorgente selezionabile per tavolo; **traduzione opzionale** (feature flag, vedi §13-D1).
- Fallback: se lo STT cloud è indisponibile o l'utente ha negato il consenso, il
  messaggio vocale resta puro audio con banner "trascrizione non disponibile".

**AC**: WER misurato su un set di 20 clip italiane di narrazione (con glossario
attivo) documentato in `docs/quality/stt-benchmark.md`. Nessun target inventato:
si misura e si riporta il numero reale.

### F4 — Voci e modulazione (orco, elfo, nano, umano, …)
Requisito centrale e la parte tecnicamente più delicata. Implementa **un'interfaccia,
tre backend**:

```
interface VoiceTransform {
  id: string; label: string; engine: 'dsp' | 'tts' | 'sts';
  render(input: AudioOrText, opts): Promise<AudioStream>;
}
```

- **Backend A — `dsp` (on-device, sempre disponibile)**: pitch shift + formant shift +
  EQ + saturazione/riverbero leggero via grafo audio nativo (AVAudioEngine su iOS,
  Oboe/SoundTouch su Android). Latenza < 40 ms, costo zero, funziona offline.
  Qualità "effetto scenico", non realismo. Preset: `orco` (pitch −5 st, formant −15%,
  growl), `nano` (pitch −3 st, boost 200–400 Hz), `elfo` (pitch +3 st, aria, riverbero
  ampio), `goblin` (pitch +7 st, compressione aggressiva), `umano` (bypass).
- **Backend B — `tts` (cloud, dal testo trascritto)**: una voce di sintesi per preset,
  streaming. È la resa più pulita e riusa la trascrizione già necessaria per F3.
  Perde la recitazione dell'attore umano.
- **Backend C — `sts` (speech-to-speech, cloud, dietro feature flag)**: conversione
  timbrica che **conserva prosodia e recitazione**. Miglior risultato, costo e latenza
  più alti. Non nel percorso critico della v1.0.

Requisiti trasversali:
- Selettore voce persistente per membro **e** override per singolo messaggio (il GM
  interpreta più PNG nello stesso turno).
- Il messaggio in chat porta: testo, audio originale, audio trasformato; il lettore
  sceglie cosa ascoltare (originale / voce PNG) e può leggere solo il testo.
- Nessuna clonazione della voce di persone reali senza consenso esplicito e
  registrato. Voci preset only in v1.0: è anche un vincolo legale, non solo etico.
- **Budget guard**: contatore secondi STT e caratteri TTS per utente/mese, con soglia
  di blocco e degrado automatico al backend `dsp`.

**AC**: A/B ascoltabile dei tre backend sui 4 preset, con latenza p50/p95 e costo per
minuto misurati e tabellati in `docs/quality/voice-benchmark.md`.

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
- **Rispetta `prefers-reduced-motion`**: alternativa non animata obbligatoria (vincolo
  di accessibilità, non un nice-to-have).
- Tiri privati del GM (visibili solo a lui, ma comunque loggati e verificabili).
- Test statistico: chi-quadro su 10^6 tiri per ogni tipo di dado, in CI.

**AC**: animazione a 60 fps su device di riferimento basso (vedi §13-D6); se non
raggiungibile, degradazione documentata a 30 fps o a sprite pre-renderizzati.

### F6 — Allegati media
- Immagini (mappe, handout, ritratti) e video brevi, da camera o galleria.
- Compressione client-side prima dell'upload, **strip EXIF/GPS**, generazione
  thumbnail, upload resumibile con progress e retry, cancellazione.
- Limiti espliciti e comunicati: immagine ≤ 10 MB, video ≤ 100 MB / 60 s (tarabili).
- Visualizzatore full-screen con pinch-zoom, salvataggio in galleria, player video.
- URL firmati a TTL breve; nessun bucket pubblico.

### F7 — Notifiche push
- APNs/FCM per nuovo messaggio, menzione, inizio sessione, tiro del GM.
- Impostazioni per tavolo: tutti / solo menzioni / muto (con muto temporizzato).
- Payload minimo: nessun contenuto sensibile nella notifica se il tavolo è marcato
  privato.

### F8 — Moderazione, segnalazione, sicurezza sociale
Requisito **bloccante per la pubblicazione** (App Store Guideline 1.2 sui contenuti
generati dagli utenti): segnalazione messaggio/utente, blocco utente, rimozione
contenuti, coda di moderazione lato backend, EULA con tolleranza zero, procedura di
risposta documentata entro 24 h. Senza questo, la review rifiuta l'app.

### F9 — Privacy e conformità
- Consenso esplicito, granulare e revocabile all'elaborazione cloud della voce
  (in UE la voce è un dato personale; il trattamento va basato sul consenso e
  documentato). Registrare versione e timestamp del consenso.
- DPA con i provider STT/TTS, opt-out dall'addestramento dei modelli, retention
  configurata e dichiarata.
- Export e cancellazione account+dati (art. 15/17 GDPR), privacy policy, age gate.
- **Nessuna E2EE in v1.0**: il server deve elaborare l'audio per STT/TTS. Questo è un
  trade-off da **dichiarare esplicitamente all'utente**, non da nascondere.

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
| TTS: richiesta → primo byte audio | p95 < 800 ms | telemetria |
| Animazione dado | 60 fps target, 30 fps floor | profiler su device di riferimento |
| Crash-free sessions | ≥ 99,5 % | Sentry |
| Costo variabile per ora di sessione | budget da §13-D3 | dashboard costi per feature |
| Consumo batteria sessione 3 h | ≤ 25 % su device medio | misura manuale documentata |

---

## 4. Stack tecnologico

Prescritto, salvo ADR che motivi la deviazione:

**Mobile**
- React Native (ultima release stabile) + **Expo (dev client + EAS Build)**, TypeScript `strict`.
- Navigazione: `expo-router`. Stato: Zustand (client) + TanStack Query (server state).
- Persistenza locale: SQLite (`expo-sqlite`) con Drizzle ORM; migrazioni versionate.
- UI: design system proprio minimale (token di spacing/colore/tipografia) — niente
  libreria UI pesante. Animazioni: Reanimated 3 + Gesture Handler. Grafica dadi: GPU
  3D (three.js su contesto nativo) con fallback sprite.
- Audio: modulo nativo custom per il grafo DSP (Swift/Kotlin) esposto via JSI/Turbo
  Module. Registrazione/riproduzione con libreria audio Expo.

**Backend**
- Node 22 + TypeScript, Fastify (REST) + WebSocket per il realtime, Postgres,
  Redis (presence, rate limit, pub/sub fan-out), object storage S3-compatibile.
- Alternativa accettata per accelerare la v1: **Supabase** (Postgres + Auth +
  Realtime + Storage + RLS), **a patto** che l'accesso passi da un layer
  `packages/data-access` così il vendor resta sostituibile. Le chiamate ai provider
  AI restano su funzioni server-side con chiavi mai esposte.
- Monorepo pnpm: `apps/mobile`, `apps/api`, `packages/shared` (tipi + schema Zod +
  **motore dadi condiviso**), `packages/data-access`.

**Provider AI** — scelti dopo lo spike dello Sprint 0, con questi criteri: latenza
streaming, qualità sull'italiano, presenza di DPA/EU data residency, costo unitario,
esistenza di un fallback. Nessun lock-in: interfacce `SttProvider` / `TtsProvider` /
`VoiceConversionProvider` con almeno due implementazioni ciascuna.

**Infra/CI**
GitHub Actions (lint, typecheck, test, build), EAS Build + Submit, canali
`development | preview | production`, OTA update per il solo layer JS, Sentry,
feature flag server-driven, migrazioni DB automatiche con rollback testato.

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

1. **Qualità STT sull'italiano fantasy**: i nomi propri inventati sono il caso peggiore
   per qualunque ASR. Mitigazione: glossario di campagna + editing della trascrizione
   (F3). Non promettere accuratezza che non si misura.
2. **Latenza e costo della voce cloud**: modulazione realistica in tempo reale costa e
   introduce latenza. Mitigazione: DSP on-device come default, cloud opzionale,
   budget guard.
3. **Animazione 3D su Android low-end**: rischio di jank e drain. Mitigazione: budget
   di performance fissato nello Sprint 0 e fallback sprite pre-renderizzato.
4. **Autonomia batteria** in sessioni di 3–4 ore con audio e schermo attivi.
5. **Review degli store**: UGC senza moderazione (F8) e permessi microfono/camera con
   giustificazione debole sono cause classiche di rifiuto.
6. **IP**: vedi §8. Il nome e l'iconografia sono un rischio legale, non grafico.
7. **Lock-in sui provider AI**: mitigato da interfaccia + secondo provider pronto.
8. **Consenso GDPR sulla voce**: se sbagliato, blocca la distribuzione in UE.

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
1. Build installabile (TestFlight / Play internal testing o APK firmato).
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
| **S0** | Fondazioni e spike | Monorepo, CI, EAS, design system minimo, schema DB, ADR stack, **spike misurati**: STT it, TTS/STS (latenza+costo), DSP nativo, 3D dadi su device low-end; review legale §8 | Decisioni provider chiuse con numeri; scheletro app che builda su entrambe le piattaforme |
| **S1** | Auth + tavoli + chat | F1, F2 (testo, realtime, outbox offline, ordinamento, cronologia) | Chat di gruppo usabile per davvero |
| **S2** | Media + push | F6, F7, visualizzatore, compressione, notifiche | Tavolo funzionante come app di messaggistica |
| **S3** | Motore dadi | F5 parser + RNG verificabile + messaggio strutturato + test statistici | Tiri corretti, provabili, non falsificabili |
| **S4** | Scenografia dadi | F5 animazione 3D, haptics, SFX, fallback ridotto/sprite, budget performance | L'effetto "wow" senza jank |
| **S5** | Voce → testo | F3 completo: registrazione, pipeline asincrona, glossario, editing, fallback | Il GM narra, il tavolo legge |
| **S6** | Voci e modulazione | F4: DSP on-device + TTS cloud, preset orco/nano/elfo/umano, player, budget guard | Il tavolo ascolta i PNG |
| **S7** | Conformità e hardening | F8, F9, F10, sicurezza §9, osservabilità §10, tuning costi e batteria | App presentabile alla review |
| **S8** | Beta e 1.0 | Beta chiusa con un tavolo reale (sessione da 3 h), bug bash, performance, store listing, submit | **v1.0 in store** |
| *S9+* | Post-1.0 | Voce live in tempo reale (room WebRTC + agent server-side), backend `sts`, iniziativa/turni, schede personaggio | roadmap successiva |

**Stima onesta**: ~9 sprint = **18 settimane / ~4,5 mesi calendario** per un singolo
senior a tempo pieno, esclusi i tempi di review degli store e di eventuale
consulenza legale. Con un secondo sviluppatore si parallelizzano S3–S4 (dadi) e
S5–S6 (voce), ma non si scende sotto ~3 mesi: la pipeline audio e la conformità non
si comprimono.

---

## 13. Decisioni aperte (rispondi prima di iniziare)

Per ognuna è indicato il **default** che adotterò se non rispondi.

- **D1 — "trascrizione tradotta": trascrizione o traduzione?**
  Default: **solo trascrizione** nella lingua parlata; traduzione multilingua dietro
  feature flag, non nel percorso critico.
- **D2 — Voce live o messaggi vocali asincroni?**
  Default: **asincrono push-to-talk** in v1 (coerente con il paradigma WhatsApp,
  costi e complessità molto inferiori); room live in S9+.
- **D3 — Budget mensile per API cloud (STT/TTS) e chi paga?**
  Default: tetto **20 €/mese totali in sviluppo**, il che impone DSP on-device come
  default di prodotto e cloud come opzione.
- **D4 — Distribuzione: store pubblici o cerchia privata?**
  Default: **store pubblici**, quindi F8/F9 sono obbligatori dallo Sprint 0.
- **D5 — Piattaforme: iOS + Android o una sola per la v1?**
  Default: **entrambe** (Expo lo rende sostenibile), con iOS come piattaforma di
  riferimento per le performance.
- **D6 — Device di riferimento minimo?**
  Default: iPhone 12 e un Android di fascia media del 2021 (Snapdragon 6xx /
  4 GB RAM). Tutti i target di §3 si riferiscono a questi.
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
3. Gli ADR proposti (solo titolo + opzioni da valutare) per: provider STT, provider
   TTS/STS, backend (Supabase vs custom), architettura audio nativa, rendering 3D dei
   dadi, strategia di licenza dei contenuti di regole.
4. La tabella dei costi ricorrenti stimati a 10 tavoli attivi e a 1 000 tavoli attivi,
   con le fonti di pricing verificate e la data di verifica.
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

Lo spike deve dire, con file audio alla mano, **quali preset reggono in DSP** e quali
richiedono il cloud per essere credibili. Non decidere a tavolino.

## Appendice B — Casi di test obbligatori sul motore dadi

`1d20`, `1d20+5`, `2d20kh1` (vantaggio), `2d20kl1` (svantaggio), `4d6kh3` ×6
(generazione statistiche), `8d6` (palla di fuoco), `1d100`, `2d6+1d8+4`,
`4d6r1` (reroll degli 1), `3d6!` (exploding), `1d20+5 attacco`, espressione vuota,
`0d6`, `1d0`, `1000000d6` (rifiuto con errore chiaro), overflow, notazione malformata,
input con unicode e spazi. Più: distribuzione chi-quadro, assenza di bias modulo,
riproducibilità della verifica del seed, e **impossibilità per il client di imporre
un risultato** (test di sicurezza dedicato).

## Appendice C — Prompt breve (se ti serve una versione compatta)

> Costruisci un'app mobile React Native/Expo (iOS+Android, TypeScript strict,
> monorepo) per giocare a giochi di ruolo da tavolo in remoto, con UX da app di
> messaggistica. Funzioni: (1) chat di gruppo realtime offline-first per campagna;
> (2) messaggi vocali trascritti automaticamente in chat, con glossario di campagna e
> trascrizione editabile; (3) riproduzione del messaggio con voci di personaggio
> selezionabili (orco, nano, elfo, umano) tramite modulazione DSP on-device come
> default e TTS cloud come opzione, dietro un'unica interfaccia `VoiceTransform`;
> (4) tiri di dado con notazione completa (`4d6kh3`, `2d20kh1`, modificatori, reroll,
> exploding), RNG autoritativo server-side verificabile via commit/reveal del seed, e
> animazione 3D che converge sul risultato già deciso dal server, con alternativa
> non animata; (5) allegati immagine e video con compressione, strip EXIF e URL
> firmati. Vincoli: chiavi AI solo server-side, RLS + check applicativi, consenso
> GDPR esplicito sull'elaborazione della voce, moderazione UGC per la review degli
> store, nessun marchio o contenuto Wizards of the Coast, i18n it/en,
> accessibilità AA. Lavora a sprint di 2 settimane: a ogni fine sprint build
> installabile, metriche misurate, ADR, scostamenti dal piano e stop per validazione.
> Prima di scrivere codice: domande bloccanti, piano Sprint 0 con spike misurati e
> tabella costi verificata.

## Appendice D — Cosa NON promettere al committente

- Modulazione vocale realistica in tempo reale a costo zero: o è DSP (economico,
  effetto scenico) o è cloud (credibile, con latenza e costo per minuto).
- Trascrizione accurata sui nomi propri fantasy senza glossario e senza correzione manuale.
- Animazione 3D fluida su qualunque Android senza un budget di performance e un fallback.
- Pubblicazione rapida sugli store con UGC e microfono: moderazione e privacy sono
  sprint di lavoro, non checkbox.
- Una v1 in "qualche settimana": il numero onesto è ~4,5 mesi per un senior.
