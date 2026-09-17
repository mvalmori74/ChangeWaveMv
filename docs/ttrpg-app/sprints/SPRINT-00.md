# Sprint 0 — Fondazioni e spike

**Durata**: 2 settimane (10 giorni lavorativi) · **Capacità**: 1 senior, ~70 % netto =
**7 giorni-uomo utili**, riferimento **20 story point**
**Obiettivo dello sprint**: non produrre funzionalità, ma **eliminare incertezza**.
A fine sprint si deve poter dire, con numeri alla mano, se il progetto come
specificato sta in piedi entro i vincoli di §13-D3 (20 €/mese) e §13-D6 (Android 2023+).

Riferimento normativo: `docs/ttrpg-app/PROMPT_MASTER.md`. Dove questo documento e il
master prompt divergono, vince il master prompt e va aperto un ADR.

---

## 0. Premessa di onestà: cosa non posso eseguire io

Tre dei cinque spike richiedono **telefoni fisici**, e io giro in un container senza
hardware Android, senza microfono e senza la tua voce. Emulatore non vale: il corredo
di voci TTS e il riconoscimento vocale offline dell'emulatore **non sono
rappresentativi** di un device reale, ed è esattamente ciò che gli spike devono
misurare.

Divisione del lavoro che propongo:

| Chi | Cosa |
|---|---|
| **Io, in autonomia** | Scaffolding del monorepo, modulo nativo Kotlin, **harness di misura** per ogni spike (app di prova che esegue i test e sputa un JSON/CSV di risultati), protocolli di misura, parsing e analisi dei dati che mi rimandi, ADR, tabella costi |
| **Tu, 2–3 ore in totale** | Registrare le 20 clip di riferimento, installare l'APK di prova sui telefoni del gruppo, premere "avvia test", rimandarmi i file di risultato |

Se questo split non ti va bene, dimmelo ora: l'alternativa è che gli spike diventino
"stime da letteratura", e a quel punto il piano poggia su assunzioni, non su misure.
Te lo direi comunque, ma preferisco dirlo prima.

---

## 1. Domande bloccanti (5)

| # | Domanda | Perché blocca | Se non rispondi |
|---|---|---|---|
| **Q1** | Chi apre l'account cloud e con quale metodo di pagamento? I tier gratuiti dei provider **richiedono comunque una carta registrata**. | Senza account non si esegue SPIKE-1 (STT cloud) né si misura nulla del Tier 1. | Lo sprint consegna solo il Tier 0 on-device e la decisione sul cloud slitta a S1. |
| **Q2** | Quali telefoni Android ha il gruppo (modello + versione Android) e chi può installarci un APK di prova? | Serve per SPIKE-3 (fasce di prestazioni) e SPIKE-4 (16 KB). Senza, il target di §3 non è verificabile. | Uso una fascia ipotetica e il rischio §7.4 resta aperto fino a S3. |
| **Q3** | **Registri tu le 20 clip di riferimento** per misurare il WER? Ti do traccia e protocollo; servono ~30 minuti del tuo tempo e la tua voce da GM, con nomi propri inventati. | È l'input del rischio n.1 del progetto. Senza clip reali, il verdetto su F3 è un'opinione. | SPIKE-1 non produce un numero e il punto di decisione dopo S0 (§12) non si può tenere. |
| **Q4** | Accetti un **VPS a ~5 €/mese** come backend, invece del piano gratuito di un servizio managed? Vedi §4 di questo documento: il gratuito **mette in pausa il progetto dopo una settimana di inattività**, con 10–30 s di risveglio al primo accesso. | Determina l'ADR-003 e la struttura di `packages/data-access` dallo sprint 1. | Procedo con il managed gratuito e metto la pausa fra i rischi accettati. |
| **Q5** | Confermi la **retention**: media 90 giorni (con `pin` del GM per le mappe), audio originale 30 giorni, trascrizione per sempre? | Determina il dimensionamento dello storage e quindi se si resta nel tier gratuito. | Applico questi valori come default e li rendo configurabili. |

Nessuna di queste blocca l'inizio dello sprint: Q1–Q3 servono **entro il giorno 4**,
Q4 entro il giorno 6, Q5 entro il giorno 8.

---

## 2. Story dello Sprint 0

Legenda: **SP** story point · *dip.* dipendenze · **AC** criteri di accettazione.

### Track A — Fondazioni (9 SP)

---
#### S0-01 · Monorepo e qualità di base · **2 SP** · dip. nessuna
Monorepo pnpm con `apps/mobile`, `packages/shared`, `packages/data-access`.
TypeScript `strict`, ESLint + Prettier, Vitest, struttura feature-sliced vuota ma
definita, `CONTRIBUTING.md` con le convenzioni di commit.

**AC**
- `pnpm install && pnpm lint && pnpm typecheck && pnpm test` passa da clone pulito.
- Un import da `features/chat` verso `features/dice` **fallisce** il lint (regola di
  confine configurata e verificata da un test).
- `packages/shared` non ha alcuna dipendenza da React o da SDK di rete.

---
#### S0-02 · App Android che builda e gira su device reale · **3 SP** · dip. S0-01
Expo dev client, `minSdk 33`, `targetSdk` ultimo stabile, schermata unica con
"hello table". Nessuna funzionalità.

**AC**
- APK generato da CI e **installato su almeno un telefono reale del gruppo** (Q2).
- Avvio senza crash, log pulito.
- `minSdk` verificato: l'installazione su un device con API < 33 viene rifiutata dal
  sistema (verifica documentale, non serve il device).
- Tempo di build documentato, locale e in CI.

---
#### S0-03 · Firma, distribuzione e OTA · **2 SP** · dip. S0-02
Keystore generato e custodito, canale di distribuzione dell'APK per il gruppo,
canale `preview` per gli OTA, `docs/runbooks/distribuzione.md`.

**AC**
- Il runbook è eseguibile da una persona che non sia io: contiene i passi per
  installare da origine sconosciuta, scritti per un giocatore, non per uno
  sviluppatore.
- **Prova end-to-end dell'OTA**: modifica di una stringa → pubblicazione sul canale
  `preview` → la stringa cambia sul telefono **senza reinstallare l'APK**. Questa
  prova è il vero valore dello story: se l'OTA non funziona, tutto il vantaggio di
  iterazione della cerchia privata svanisce.
- Il keystore è salvato in due posti distinti e il runbook dice quali.

---
#### S0-04 · CI · **2 SP** · dip. S0-01, S0-02
GitHub Actions: lint, typecheck, test, build APK. Cache delle dipendenze.

**AC**
- Pipeline verde su PR in meno di 10 minuti (riporta il tempo reale).
- Il job di build produce un APK scaricabile come artifact.
- Un PR che rompe il typecheck viene bloccato (verificato con un PR di prova).

---

### Track B — Spike misurati (9 SP)

Ogni spike ha **una metrica obbligatoria**. Uno spike che finisce senza il suo numero
non è concluso, anche se il codice funziona.

---
#### S0-05 · SPIKE-1 · Qualità della trascrizione: Tier 0 vs Tier 1 · **3 SP** · dip. S0-02, Q1, Q3
**È il rischio n.1 del progetto (§7.1). Se fallisce, cambia il prodotto.**

Lavoro: harness Android che trascrive un set di clip con (a) riconoscimento vocale di
sistema offline, (b) riconoscimento di sistema online se disponibile, (c) STT cloud
senza adattamento, (d) STT cloud con boost del glossario di campagna; esporta un CSV
con trascrizione e tempi.

Protocollo del set di riferimento (lo preparo io, lo registri tu):
- 20 clip, 30–60 s ciascuna, parlato di narrazione reale, non lettura piatta.
- Devono contenere **almeno 25 nomi propri inventati** distinti (PNG, luoghi,
  oggetti), ripetuti in clip diverse.
- Condizioni miste: 5 clip in stanza silenziosa, 5 con rumore di fondo domestico,
  5 a voce concitata, 5 a voce bassa "da tavolo".
- Trascrizione corretta di riferimento scritta a mano da te (è il ground truth).

**Metrica obbligatoria**
| Grandezza | Come |
|---|---|
| **WER complessivo** per ciascuna delle 4 configurazioni | allineamento standard sul ground truth |
| **WER sui soli nomi propri** (metrica che conta davvero) | sottoinsieme etichettato |
| Latenza fine-registrazione → testo, p50 e p95 | strumentazione nell'harness |
| Costo per minuto effettivo | fatturazione reale del provider |

**AC**: tabella completa in `docs/quality/stt-benchmark.md` + **verdetto esplicito**
su una delle tre opzioni: (1) il Tier 0 basta, il cloud è un lusso; (2) serve il
cloud, e ci sta nel budget; (3) nessuno dei due regge sui nomi propri → si riapre la
specifica di F3 e si porta la decisione a te (§12, punto di decisione dopo S0).

---
#### S0-06 · SPIKE-2 · Voci di personaggio: quanto si ottiene a costo zero · **3 SP** · dip. S0-02, Q1
Lavoro: modulo Kotlin minimo con catena pitch/formant shift su Oboe; harness che
produce, per ogni preset di Appendice A, tre rendering: DSP su voce umana registrata,
DSP su TTS di sistema, TTS cloud.

**Metrica obbligatoria**
| Grandezza | Come |
|---|---|
| **Giudizio di ascolto** su 2 scale 1–5: *intelligibilità* e *carattere* (quanto "suona da orco") | almeno 3 ascoltatori del gruppo, alla cieca sull'origine |
| Latenza di rendering p50/p95 per 30 s di audio | strumentazione |
| Costo per minuto di narrazione | listino verificato |
| Dimensione dell'APK aggiunta dal modulo nativo | confronto build |

**AC**: `docs/quality/voice-benchmark.md` con i file audio allegati e la riga di
verdetto per preset: *accettabile a costo zero* / *serve il cloud* / *da scartare*.
Se un preset non raggiunge 3/5 in intelligibilità, va scartato o ridisegnato: una voce
scenica che non si capisce è un danno al gioco, non un effetto.

---
#### S0-07 · SPIKE-3 · Dadi 3D, fasce di prestazioni e batteria · **2 SP** · dip. S0-02, Q2
Lavoro: scena di prova con un d20 in caduta, materiali e ombre attivabili; il
classificatore di fascia di §4-ter; loop di stress che anima N tiri consecutivi.

**Metrica obbligatoria**
| Grandezza | Come |
|---|---|
| fps medio e **frame time p95** con ombre e senza | strumentazione sul device |
| **% di batteria consumata per ora** di animazione continua | misura su device reale, schermo a luminosità fissa |
| Fascia assegnata dal classificatore per ogni telefono del gruppo | output dell'harness |
| Tempo di primo caricamento della scena 3D | strumentazione |

**AC**: `docs/quality/dice-perf.md` con una riga per telefono del gruppo e la mappa
fascia → effetti attivi. Se il telefono più debole non tiene 30 fps stabili in
`medium`, la soglia della fascia `low` si alza: è una decisione tecnica che prendo io
e documento, non una che ti giro.

---
#### S0-08 · SPIKE-4 · Trappole della piattaforma nativa · **1 SP** · dip. S0-02, Q2
Due verifiche binarie che costano poco ora e molto dopo:
1. **Allineamento a 16 KB** delle librerie native: build con il modulo audio e avvio
   su un device Android aggiornato.
2. **Foreground service audio**: una registrazione di 90 s sopravvive allo schermo
   spento e al cambio di app?

**Metrica obbligatoria**: due esiti **pass/fail** documentati, con il log dell'errore
in caso di fail e la correzione applicata. Nessuna zona grigia.

---

### Track C — Decisioni e conformità (2 SP)

---
#### S0-09 · Tabella costi e scelta dell'infrastruttura · **1 SP** · dip. Q1, Q4
Vedi §4 di questo documento: la ricerca di listino è **già fatta** e riportata sotto.
Resta da confermare i consumi reali misurati negli spike e chiudere l'ADR-003.

**AC**: `docs/costs.md` aggiornato con i consumi misurati, non stimati; ADR-003 chiuso.

---
#### S0-10 · Licenze · **1 SP** · dip. nessuna
Verifica della licenza della libreria di pitch shifting scelta (**attenzione: diverse
librerie di time-stretch diffuse sono GPL o a doppia licenza commerciale a pagamento**
— è un punto che blocca, se scoperto a S5), della licenza dei contenuti di regole che
si volessero includere, e degli asset grafici/sonori.

**AC**: `docs/licenses.md` con una riga per dipendenza non banale: nome, licenza,
compatibilità con distribuzione privata, e cosa cambierebbe in caso di pubblicazione.

---

### Riepilogo capacità

| Track | SP |
|---|---|
| A — Fondazioni | 9 |
| B — Spike | 9 |
| C — Decisioni | 2 |
| **Totale** | **20** |

Margine zero: è voluto, perché gli spike hanno esito incerto per definizione. Se
SPIKE-1 dà esito negativo, **taglio S0-04 (CI) e lo sposto in S1** e uso il tempo per
esplorare le alternative su F3. La CI di uno sprint può aspettare; un requisito
centrale che non regge, no.

---

## 3. ADR proposti (titolo + opzioni da valutare)

| ADR | Titolo | Opzioni |
|---|---|---|
| **ADR-001** | Motore STT: on-device, cloud o ibrido | Solo sistema Android · Solo cloud · Ibrido a due tier con policy server-side (proposto) |
| **ADR-002** | Motore TTS e catena delle voci | TTS di sistema + DSP · TTS cloud commodity · Ibrido con cache (proposto). Vedi §4: il calcolo dei costi **ha ribaltato l'ipotesi di partenza** |
| **ADR-003** | Backend e storage | Managed a piano gratuito · VPS minimale + Postgres (proposto, vedi Q4) · Ibrido: VPS per l'API, storage a oggetti separato con egress gratuito |
| **ADR-004** | Architettura del modulo audio Kotlin | Libreria di pitch shifting: quale, con quale licenza · Elaborazione in tempo reale vs rendering differito · Confine dell'interfaccia platform-agnostic |
| **ADR-005** | Rendering 3D dei dadi | Motore 3D su contesto nativo · Animazione 2D con sprite pre-renderizzati · Ibrido a fasce (proposto) |
| **ADR-006** | Contenuti di regole e identità del prodotto | Nessun contenuto di regole (proposto) · Solo contenuto sotto licenza aperta con attribuzione · Rinvio a dopo la v1 |
| **ADR-007** | Formato e retention dell'audio | Codec e bitrate di registrazione · Se conservare una copia a qualità più alta per il rendering delle voci (vedi Appendice A del master prompt) |

---

## 4. Tabella costi — listini verificati il **17 settembre 2026**

Scenario di riferimento (§13-D7): **1 tavolo, 6 giocatori, 2 sessioni/settimana × 3 h
= ~24 h/mese**, di cui ~25–30 % registrato come messaggi vocali → **~400 min/mese di
audio**, ~**380.000 caratteri** di trascrizione.

### 4.1 Trascrizione (Google Cloud Speech-to-Text)

| Modalità | Prezzo di listino | Costo a 400 min/mese |
|---|---|---|
| Dynamic Batch Recognition, Standard | $0,003 / min | **$1,20** |
| Recognition V2, Standard (0–500k min/mese) | $0,016 / min | $6,40 |
| V1 **con** data logging (primi 60 min/mese gratis) | $0,016 / min | $5,44 |
| V1 **senza** data logging (primi 60 min/mese gratis) | $0,024 / min | $8,16 |

> **Attenzione, e non è un dettaglio contabile.** Il listino applica uno sconto
> esplicito alla modalità *con data logging*, cioè quella in cui l'audio dei tuoi
> giocatori viene usato dal provider. F9 richiede l'opt-out dall'addestramento: la
> tariffa scontata **non è utilizzabile**. Da verificare in S0 se il prezzo della
> modalità batch a $0,003/min sia soggetto alla stessa condizione: se lo fosse, la
> voce STT del budget passa da $1,20 a $8,16/mese. È l'unica variabile che può
> spostare il conto in modo significativo.

### 4.2 Sintesi vocale (Google Cloud Text-to-Speech)

| Tipo di voce | Quota gratuita mensile | Prezzo oltre la quota |
|---|---|---|
| Standard | 4 M caratteri | $4 / 1M |
| WaveNet | 4 M caratteri | $4 / 1M |
| Neural2 | 1 M caratteri | $16 / 1M |
| Polyglot (Preview) | 1 M caratteri | $16 / 1M |
| Chirp 3: HD | 1 M caratteri | $30 / 1M |
| Studio | 1 M caratteri | $160 / 1M |
| Instant custom voice | — | $60 / 1M |

**Fabbisogno: ~380.000 caratteri/mese → dentro la quota gratuita di *qualunque* di
queste voci**, comprese le HD. Anche con un fattore 2,5× di riascolti (~950k
caratteri) si resta gratuiti su Standard/WaveNet e al limite su Chirp 3 HD.

> **Correzione a §4-bis del master prompt.** Avevo scritto che il TTS sarebbe stata la
> voce di costo dominante. Con i listini reali **è falso a questo volume**: il TTS
> costa **0 €**, e l'unica voce variabile è la trascrizione. La conclusione pratica si
> capovolge: **il budget guard va calibrato sullo STT**, e sul TTS serve soprattutto
> un tetto di sicurezza contro l'uso anomalo (un tavolo che risintetizza in massa la
> cronologia), non un risparmio quotidiano. Aggiorno il master prompt di conseguenza.

### 4.3 Storage dei media (Cloudflare R2)

| Voce | Quota gratuita | Oltre la quota |
|---|---|---|
| Storage Standard | 10 GB/mese | $0,015 / GB |
| Operazioni Classe A | 1 M/mese | $4,50 / M |
| Operazioni Classe B | 10 M/mese | $0,36 / M |
| **Egress** | **gratuito, sempre, a qualunque volume** | — |

Con la retention di Q5, l'audio pesa poco (400 min/mese in codec compresso sono
decine di MB) e il vincolo reale sono i video. **10 GB gratuiti reggono il tavolo di
riferimento con ampio margine**, purché la retention sia implementata: senza, il
margine si esaurisce e la voce diventa a pagamento.

L'egress gratuito è il motivo per cui questa scelta batte le alternative: con 6
device che riscaricano mappe e video, il traffico in uscita sarebbe altrimenti la
sorpresa di fine mese.

### 4.4 Backend — ed è qui che si gioca davvero il budget

Piano gratuito di un servizio managed tipico (verificato su Supabase):
500 MB di database, 1 GB di file storage, 5 GB di egress, **massimo 2 progetti
attivi**, e soprattutto **sospensione del progetto dopo 1 settimana di inattività, con
10–30 secondi di risveglio alla prima richiesta**. Il passaggio al piano a pagamento
che rimuove la sospensione costa **$25/mese**: da solo **oltre l'intero budget di
20 €/mese**.

Due conseguenze concrete:
1. Il file storage di 1 GB del piano gratuito **non basta** per i media → conferma la
   scelta di uno storage a oggetti separato (§4.3).
2. La sospensione per inattività colpisce esattamente lo scenario d'uso: due sessioni
   a settimana vanno bene, ma una pausa estiva di due settimane fa sì che **il primo
   tiro della serata aspetti mezzo minuto**. Mitigabile con un ping periodico, che però
   è un trucco, non una soluzione.

**Raccomandazione (ADR-003)**: VPS minimale a ~5 €/mese con Postgres e API proprie.
Costa più tempo di setup e un po' di lavoro operativo continuo, ma toglie la
sospensione, i limiti di storage e il rischio che il piano gratuito cambi condizioni.
Da qui la domanda **Q4**.

### 4.5 Quadro complessivo mensile

| Voce | Ipotesi VPS (raccomandata) | Ipotesi managed gratuito |
|---|---|---|
| Backend | ~5 € | 0 € |
| Storage media | 0 € (entro 10 GB) | 0 € (entro 10 GB) |
| TTS | 0 € (entro quota) | 0 € |
| STT | ~1,10 € (batch) — fino a ~7,50 € se vale il vincolo del data logging | idem |
| Notifiche push, crash reporting | 0 € (piani gratuiti) | 0 € |
| **Totale** | **~6–13 €/mese** | **~1–8 €/mese**, con sospensioni |

**Il tetto di 20 €/mese regge in entrambi gli scenari**, con margine. Conversione
€/$ e listini vanno riverificati prima di ogni sprint: le fonti sono quelle citate in
fondo, con la data di consultazione.

**Dove si romperebbe**: non nel costo unitario delle AI, ma nel numero di tavoli. Il
modello regge 1 tavolo con larghezza; a 5 tavoli lo STT resta sotto controllo (~5 €)
ma le quote gratuite di TTS e storage iniziano a stringere. Se un giorno l'app uscisse
dalla cerchia privata, l'economia va rifatta da zero — e con un modello a costo
marginale non nullo per utente.

---

## 5. Cosa della richiesta iniziale non regge

In ordine di quanto costa scoprirlo tardi.

1. **"Vedere il testo della trascrizione" presuppone una trascrizione affidabile, e i
   nomi propri fantasy sono il caso peggiore per qualunque riconoscitore.** Il motore
   di sistema Android, che è la scelta a costo zero, **non accetta un glossario
   personalizzato**. La formulazione onesta della funzione non è "parlo e appare il
   testo giusto", ma "parlo, appare una bozza, la correggo in due tocchi se serve".
   L'editing della trascrizione non è un ripiego: è parte del flusso, e va progettato
   bene (correzione rapida, memoria dei nomi già corretti, riuso).
2. **"Modulare la mia voce come se fossi un orco" ha due significati tecnici diversi, e
   quello che probabilmente hai in mente è il più costoso.** Cambiare il timbro
   *conservando la tua recitazione* è conversione vocale: fuori dai 20 €/mese. Quello
   che rientra nel budget è l'effetto scenico (la tua voce lavorata con pitch e
   formanti, riconoscibilmente tua ma "da orco") oppure una voce sintetica che legge
   il testo (pulita, ma non sei più tu a recitare). Sono esperienze diverse: SPIKE-2
   serve a farti scegliere **ascoltando**, non leggendo una tabella.
3. **"Stile WhatsApp" nasconde la parte più costosa dell'app.** Una chat che regge
   offline, ordina i messaggi in modo deterministico, non duplica gli invii e
   riconcilia al ritorno della rete è lavoro serio: è lo sprint S1 intero, e non si
   vede. Se in demo sembra "solo una chat", è perché è fatta bene.
4. **I dadi sono la funzione più semplice da far funzionare e la più facile da sbagliare
   in modo invisibile.** Un RNG scritto con la funzione random del linguaggio e
   l'operatore modulo produce una distribuzione distorta che **nessuno noterà mai**, e
   che rende il gioco subdolamente ingiusto. Per questo il motore ha un test di
   distribuzione in CI e un seed verificabile. Non è teatro: è l'unica cosa che
   distingue un simulatore di dadi corretto da uno che sembra corretto.
5. **"Allegare video" è il requisito più costoso in proporzione a quanto lo userete.**
   È anche l'unico che può far saltare il budget da solo. Per questo i limiti sono
   stretti (25 MB / 30 s) e la retention è obbligatoria. Se il gruppo condivide
   soprattutto mappe e ritratti, valuta di **togliere del tutto il video dalla v1**: si
   libera mezzo sprint e un rischio di costo. Dimmelo e lo sposto nel backlog.
6. **Nessuno di questi punti riguarda la tecnologia AI**, che è la parte che di solito
   preoccupa. Le funzioni vocali sono, con i numeri di §4, la parte **meno** rischiosa
   sul piano economico. Il rischio vero è la qualità della trascrizione sui nomi
   inventati (punto 1) e la fatica invisibile della chat offline (punto 3).

---

## 6. Definition of Done dello sprint

- [ ] `pnpm install && pnpm lint && pnpm typecheck && pnpm test` verde da clone pulito
- [ ] APK installato e funzionante su **almeno due telefoni reali** del gruppo
- [ ] Aggiornamento OTA dimostrato su device, senza reinstallazione
- [ ] `docs/quality/stt-benchmark.md` con WER complessivo **e sui nomi propri**
- [ ] `docs/quality/voice-benchmark.md` con i file audio e un verdetto per preset
- [ ] `docs/quality/dice-perf.md` con fps, frame time p95 e batteria per telefono
- [ ] SPIKE-4: due esiti pass/fail documentati
- [ ] `docs/costs.md` con consumi **misurati**, non stimati
- [ ] `docs/licenses.md`, `docs/devices.md`, `docs/risks.md`, `docs/backlog.md` popolati
- [ ] ADR-001 … ADR-007 aperti, con almeno 001–005 chiusi
- [ ] `docs/sprints/sprint-00-consuntivo.md`: SP pianificati vs completati e **causa
      reale** degli scostamenti
- [ ] Le 3 domande per lo Sprint 1

---

## 7. Cosa NON si fa in questo sprint

Nessuna schermata di chat, nessuna autenticazione, nessun database di produzione,
nessun messaggio inviato fra due telefoni. Il codice degli spike è codice **usa e
getta**: va marcato `// SPIKE: da buttare` e cancellato in S1, non promosso a
fondamenta. Se a fine sprint mi trovo a voler "salvare" l'harness perché "tanto
funziona", è il segnale che ho sbagliato a scriverlo.

---

## Fonti dei listini

Consultate il **17 settembre 2026**. Vanno riverificate a ogni sprint.

- Google Cloud Text-to-Speech — pricing: https://cloud.google.com/text-to-speech/pricing
- Google Cloud Speech-to-Text — pricing: https://cloud.google.com/speech-to-text/pricing
- Cloudflare R2 — pricing e tier gratuito: https://developers.cloudflare.com/r2/pricing/
- Supabase — piani e limiti del piano gratuito: https://supabase.com/pricing
