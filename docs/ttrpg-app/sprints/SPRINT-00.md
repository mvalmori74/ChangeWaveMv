# Sprint 0 — Fondazioni e spike

**Durata**: 2 settimane (10 giorni lavorativi) · **Capacità**: 1 senior, ~70 % netto =
**7 giorni-uomo utili**, riferimento **20 story point**
**Obiettivo**: non produrre funzionalità, ma **eliminare incertezza**. A fine sprint si
deve poter dire, con numeri alla mano, se il progetto come specificato sta in piedi su
un server di casa (§13-D9) con retention infinita (§13-D10) e telefoni Android dal
2023 (§13-D6).

Riferimento normativo: `docs/ttrpg-app/PROMPT_MASTER.md`. Dove questo documento e il
master prompt divergono, vince il master prompt e va aperto un ADR.

> **Revisione del 20/09/2026.** Le decisioni D9 (server in casa) e D10 (retention
> infinita) hanno riscritto questo sprint. La tabella dei listini cloud della versione
> precedente non serve più; al suo posto c'è §4, che fa il conto di quanto costa
> davvero l'alternativa scelta. Anticipata a questo sprint la prova di ripristino da
> backup, che prima stava in S7: con un disco solo in casa, è il rischio peggiore del
> progetto e non può aspettare quattro mesi.

---

## 0. Premessa di onestà: cosa non posso eseguire io

Quattro spike su cinque richiedono **hardware che non ho**: i telefoni del gruppo, il
tuo PC di casa, la tua linea internet e la tua voce. Giro in un container, senza
microfono e senza accesso alla tua rete. L'emulatore Android non vale: il corredo di
voci e il riconoscimento offline non sono rappresentativi, ed è esattamente ciò che va
misurato.

| Chi | Cosa |
|---|---|
| **Io, in autonomia** | Scaffolding del monorepo, modulo nativo Kotlin, `docker compose` completo, **harness di misura** (programmi di prova che eseguono i test e producono un CSV), protocolli, analisi dei dati che mi rimandi, ADR |
| **Tu, ~4 ore in totale** | Registrare le 20 clip, far girare il compose sul PC di casa, eseguire la verifica CGNAT, installare l'APK sui telefoni, misurare il consumo elettrico con un misuratore da presa, rimandarmi i file di risultato |

Se questo split non ti va, l'alternativa è che gli spike diventino stime da
letteratura e il piano poggi su assunzioni invece che su misure. Te lo direi comunque,
preferisco dirlo prima.

---

## 1. Domande bloccanti (5)

| # | Domanda | Perché blocca | Entro |
|---|---|---|---|
| **Q1** | **CHIUSA senza risposta, per decisione dell'utente (20/09).** Le caratteristiche del PC non vengono raccolte a mano: **il server le rileva da solo all'avvio** (`apps/server/src/diagnosi.ts`) e propone modello e concorrenza di conseguenza. Il dimensionamento definitivo resta della misura, che ha sempre la precedenza. | non blocca più nulla | — |
| **Q2** | **CHIUSA come non necessaria.** Con il tunnel in uscita la presenza di CGNAT è ininfluente: conta solo se il giro completo funziona, e il server lo riporta nella pagina di stato. | non blocca più nulla | — |
| **Q3** | **Registri tu le 20 clip di riferimento?** Ti do traccia e protocollo: ~30 minuti, la tua voce da GM, con nomi propri inventati. | È l'input del rischio n.1. Senza clip reali, il verdetto su F3 è un'opinione. | giorno 4 |
| **Q4** | Quali telefoni Android ha il gruppo (modello + versione) e chi può installarci un APK di prova? | SPIKE-3 e SPIKE-4. Senza, i target di §3 non sono verificabili. | giorno 4 |
| **Q5** | **CHIUSA: backup rinviato per decisione dell'utente (20/09).** Il servizio è nel compose sotto profilo `backup`, spento. Nessun secondo disco necessario ora. | non blocca più nulla | — |

**Aggiornamento del 20/09**: Q1 e Q2 sono state chiuse spostando il problema dentro il
software invece di girarlo all'utente. Il server si autodiagnostica all'avvio e
riporta macchina, profilo di trascrizione proposto ed esposizione nella pagina di
stato; `scripts/raccolta-ambiente.ps1` resta disponibile ma non serve più a nessuno
step obbligatorio.

È anche la soluzione tecnicamente migliore: resta corretta se il server viene spostato
su un altro computer, cosa che una raccolta manuale fatta una volta non fa.

**Resta aperta solo Q3: le clip, attese per il weekend.**

**Nota sull'upload, che si è chiarita da sola.** Su fibra — sia FTTH sia mista
rame — la banda in salita di una linea WindTre è di norma ampiamente sufficiente per
sei persone che si scambiano mappe e ritratti. Il rischio §7 "banda in salita" scende
da MEDIO a BASSO. Resta da misurare (lo script lo chiede) perché *ampiamente
sufficiente* va confermato con un numero, e perché i video allegati restano il caso
peggiore.

---

## 2. Story dello Sprint 0

Legenda: **SP** story point · *dip.* dipendenze · **AC** criteri di accettazione.

### Track A — App e catena di consegna (7 SP)

---
#### S0-01 · Monorepo e qualità di base · **2 SP** · dip. nessuna
Monorepo pnpm con `apps/mobile`, `apps/server`, `packages/shared`,
`packages/data-access`. TypeScript `strict`, ESLint + Prettier, Vitest, struttura
feature-sliced definita, convenzioni di commit.

**AC**
- `pnpm install && pnpm lint && pnpm typecheck && pnpm test` passa da clone pulito.
- Un import da `features/chat` verso `features/dice` **fallisce** il lint (regola di
  confine verificata da un test).
- `packages/shared` non dipende da React né da SDK di rete.

---
#### S0-02 · App Android che builda e gira su device reale · **3 SP** · dip. S0-01, Q4
Expo dev client, `minSdk 33`, schermata unica. Nessuna funzionalità.

**AC**
- APK installato su **almeno un telefono reale del gruppo**, avvio senza crash.
- Tempo di build documentato.
- Il classificatore di fascia (§4-ter) restituisce un valore su ogni telefono provato.

---
#### S0-03 · Firma, distribuzione e OTA · **2 SP** · dip. S0-02
Keystore generato e custodito in due posti distinti, canale di distribuzione
dell'APK, canale `preview` per gli OTA, `docs/runbooks/distribuzione.md` scritto
**per un giocatore, non per uno sviluppatore**.

**AC**
- **Prova end-to-end dell'OTA**: cambio una stringa → pubblico sul canale `preview` →
  la stringa cambia sul telefono **senza reinstallare l'APK**. Se questo non funziona,
  tutto il vantaggio di iterazione della cerchia privata svanisce, quindi è l'AC che
  conta davvero di questo story.

> **La CI slitta a S1.** Con le nuove decisioni il tempo di sprint è finito altrove, e
> fra "automatizzare la build" e "sapere se il server di casa è raggiungibile" la
> seconda vale di più adesso. Lo dico qui perché è uno scostamento dal piano
> precedente, non una dimenticanza.

---

### Track B — Server di casa (5 SP) — *nuovo, conseguenza di D9*

---
#### S0-04 · SPIKE-0 · Raggiungibilità del server di casa · **2 SP** · dip. Q2
**Il primo spike in ordine di esecuzione: se fallisce, l'architettura cambia.**

Lavoro: verifica CGNAT; tunnel in uscita configurato sul PC; un servizio banale
("pong") esposto e raggiunto **da fuori casa, su rete mobile**, con certificato TLS
valido; misura della latenza e verifica che i WebSocket passino dal tunnel.

**Metrica obbligatoria**
| Grandezza | Come |
|---|---|
| Esito CGNAT: sì/no | confronto IP router vs IP pubblico |
| Latenza di andata e ritorno da rete mobile, p50 e p95 | 100 richieste dal telefono, fuori dalla rete di casa |
| WebSocket: connessione stabile per 30 minuti senza cadute | test di tenuta |
| Banda in salita misurata della linea | test dal PC, **in orario serale**, che è quello di gioco e quello peggiore |
| Vincoli dei termini d'uso del tunnel sui file grandi | lettura e sintesi, non interpretazione a occhio |

**AC**: `docs/infra/raggiungibilita.md` con i numeri e un verdetto: architettura
confermata, oppure quali requisiti vanno rinegoziati. **La banda in salita è il dato
che deciderà i limiti sugli allegati (F6)**: se è stretta, i video vanno ridiscussi.

---
#### S0-05 · Scheletro del server e pagina di stato · **2 SP** · dip. S0-04, Q1
`docker compose` con Postgres, API minima, reverse proxy, connettore del tunnel,
riavvio automatico. Pagina di stato leggibile dal telefono: server su/giù, spazio
disco, versione.

**AC**
- `docker compose up -d` da zero su quel PC, documentato passo passo.
- Sopravvive a un riavvio del PC **senza intervento manuale** (provato spegnendo e
  riaccendendo davvero, non solo riavviando i container).
- Consumo elettrico misurato a riposo e sotto carico, riportato in `docs/costs.md`.
- Postgres **non raggiungibile** dall'esterno della rete dei container (verificato).

---
#### S0-06 · Backup e prova di ripristino · **1 SP** · dip. S0-05, Q5
**Anticipato da S7.** Dump periodico del database e copia dei media su un secondo
disco, dentro il compose.

**AC**: un ripristino **eseguito davvero** su un'installazione vuota, con il tempo
impiegato annotato nel runbook. Un backup mai ripristinato non è un backup, e con
retention infinita e un disco solo questo è il rischio con la conseguenza peggiore
dell'intero progetto.

---

### Track C — Spike vocali (6 SP)

---
#### S0-07 · SPIKE-1 · Trascrizione: riconoscimento locale contro on-device · **4 SP** · dip. S0-05, Q1, Q3
**Rischio n.1 del progetto. Se fallisce, cambia il prodotto.**

Il self-hosting ha restituito la mitigazione che si era persa: un riconoscitore
eseguito in locale **accetta il condizionamento con il glossario di campagna**, che il
motore Android non permette. Questo spike serve a misurare quanto vale davvero quel
guadagno, e a quale prezzo in tempo di elaborazione.

Configurazioni da confrontare sulle stesse 20 clip:
1. Riconoscimento di sistema Android, offline (Tier 0).
2. Riconoscimento locale sul PC, modello piccolo, **senza** glossario.
3. Riconoscimento locale sul PC, modello piccolo, **con** glossario di campagna.
4. Riconoscimento locale sul PC, modello più grande, **con** glossario.

Protocollo del set di riferimento (lo preparo io, lo registri tu):
- 20 clip, 30–60 s, narrazione reale, non lettura piatta.
- Almeno **25 nomi propri inventati** distinti, ripetuti in clip diverse.
- 5 clip in stanza silenziosa, 5 con rumore domestico, 5 a voce concitata, 5 a voce
  bassa "da tavolo".
- Trascrizione corretta scritta a mano da te: è il riferimento contro cui si misura.

**Metrica obbligatoria**
| Grandezza | Come |
|---|---|
| **WER complessivo** per ciascuna delle 4 configurazioni | allineamento sul riferimento |
| **WER sui soli nomi propri** — *è la metrica che decide* | sottoinsieme etichettato |
| **Tempo di elaborazione per 30 s di audio** sul PC reale, p50 e p95 | strumentazione |
| RAM occupata dal modello | misura sul server |
| Tenuta con 6 richieste simultanee | test di carico |

**AC**: `docs/quality/stt-benchmark.md` completo + **verdetto esplicito**:
(1) il locale con glossario vince nettamente → si procede;
(2) il guadagno è marginale → il self-hosting del riconoscimento non vale la
complessità, si usa solo l'on-device e lo dico;
(3) nessuno regge sui nomi propri → si riapre la specifica di F3 e la decisione torna
a te (§12 del master prompt, punto di decisione dopo S0).

---
#### S0-08 · SPIKE-2 · Voci di personaggio · **2 SP** · dip. S0-05, Q1
Modulo Kotlin minimo con catena pitch/formant shift; sintesi neurale sul server. Per
ogni preset di Appendice A, tre rendering: DSP su voce umana, DSP su sintesi del
server, DSP su TTS di sistema.

**Metrica obbligatoria**
| Grandezza | Come |
|---|---|
| **Giudizio di ascolto** 1–5 su *intelligibilità* e *carattere* | ≥ 3 ascoltatori del gruppo, alla cieca sull'origine |
| Tempo di sintesi per 30 s di parlato sul PC | strumentazione |
| Dimensione dell'APK aggiunta dal modulo nativo | confronto build |
| Licenza del motore **e delle singole voci** | lettura, non assunzione |

**AC**: `docs/quality/voice-benchmark.md` con i file audio e una riga di verdetto per
preset. Un preset sotto 3/5 in intelligibilità va scartato o ridisegnato: una voce
scenica che non si capisce danneggia il gioco invece di arricchirlo.

---

### Track D — Piattaforma e licenze (2 SP)

---
#### S0-09 · SPIKE-4 · Trappole della piattaforma nativa · **1 SP** · dip. S0-02, Q4
1. **Allineamento a 16 KB** delle librerie native: build con il modulo audio e avvio
   su un device Android aggiornato.
2. **Foreground service audio**: una registrazione di 90 s sopravvive allo schermo
   spento e al cambio di app?

**Metrica obbligatoria**: due esiti **pass/fail**, con il log dell'errore e la
correzione applicata in caso di fail. Nessuna zona grigia.

---
#### S0-10 · Licenze · **1 SP** · dip. nessuna
Libreria di pitch shifting (**attenzione: diverse librerie di time-stretch diffuse
sono GPL o a doppia licenza commerciale a pagamento** — bloccante se scoperto a S5),
modelli di riconoscimento e sintesi vocale, voci, asset.

**AC**: `docs/licenses.md` con una riga per dipendenza non banale: nome, licenza,
compatibilità con l'uso previsto, cosa cambierebbe in caso di distribuzione pubblica.

---

### Riepilogo capacità

| Track | SP |
|---|---|
| A — App e catena di consegna | 7 |
| B — Server di casa | 5 |
| C — Spike vocali | 6 |
| D — Piattaforma e licenze | 2 |
| **Totale** | **20** |

**Margine zero, e due cose rinviate consapevolmente**: la CI a S1, lo spike sui dadi
3D all'inizio di S2. Entrambi motivati sopra. Se SPIKE-1 dà esito negativo, taglio
S0-09 e S0-10 e uso il tempo per esplorare le alternative su F3: un requisito
centrale che non regge vale più di due verifiche che possono slittare di due
settimane.

---

## 3. ADR proposti

| ADR | Titolo | Opzioni |
|---|---|---|
| **ADR-001** | Motore di riconoscimento vocale e dimensionamento del modello | Solo sistema Android · Solo locale sul server · **Ibrido: locale con glossario, on-device come ripiego a server spento** (proposto). Il dimensionamento esce da SPIKE-1 |
| **ADR-002** | Motore di sintesi vocale e catena delle voci | Sintesi neurale sul server + DSP (proposto) · TTS di sistema + DSP · ibrido con cache su hash |
| **ADR-003** | Esposizione del server di casa | Inoltro di porte + DNS dinamico · **Tunnel in uscita** (proposto) · VPN fra i device. Decide SPIKE-0, ed è vincolato dall'esito CGNAT |
| **ADR-004** | Architettura del modulo audio Kotlin | Libreria di pitch shifting: quale e con quale licenza · elaborazione in tempo reale vs rendering differito · confine dell'interfaccia |
| **ADR-005** | Strategia di backup e formato di esportazione (F11) | Dump + copia incrementale su secondo disco (proposto) · formato dell'archivio di campagna · leggibilità senza l'app |
| **ADR-006** | Rendering 3D dei dadi | Motore 3D su contesto nativo · sprite pre-renderizzati · **ibrido a fasce** (proposto). Decide lo spike a inizio S2 |
| **ADR-007** | Contenuti di regole e identità del prodotto | **Nessun contenuto di regole** (proposto) · solo contenuto sotto licenza aperta con attribuzione · rinvio a dopo la v1 |
| **ADR-008** | Presenza o meno di Redis | Nessun Redis, tutto su Postgres (proposto: un componente in meno da mantenere in casa) · Redis per presence e pub/sub |

---

## 4. Quanto costa davvero il self-hosting

Il vincolo è "nessun costo ricorrente" (§13-D9). Va onorato alla lettera — nessun
abbonamento, nessun servizio a consumo — ma va anche detto cosa resta fuori dal
conteggio, perché **"nessun costo ricorrente" e "gratis" non sono la stessa cosa**.

### 4.1 Energia elettrica

```
consumo_mensile_kWh = potenza_media_W × 24 × 30 / 1000
costo_mensile       = consumo_mensile_kWh × prezzo_kWh_della_tua_bolletta
```

Esempi di calcolo, da sostituire con la **misura reale** (S0-05):

| Macchina | Potenza a riposo | kWh/mese | A 0,25 €/kWh | A 0,35 €/kWh |
|---|---|---|---|---|
| PC desktop acceso 24/7 | ~60 W | ~43 | ~11 € | ~15 € |
| PC desktop con GPU dedicata attiva | ~100 W | ~72 | ~18 € | ~25 € |
| Mini-PC a basso consumo | ~10 W | ~7 | ~1,8 € | ~2,5 € |

**Il confronto che devi vedere, e che non ti farà piacere.** L'alternativa cloud che
hai scartato costava, con i listini verificati la settimana scorsa, **6–13 €/mese**.
Un PC desktop acceso 24/7 costa **11–15 €/mese di sola corrente**. Sul puro criterio
economico, **il self-hosting su un desktop non è più conveniente di ciò che
sostituisce**: sposta la voce dalla carta di credito alla bolletta, dove si vede meno.

Tre casi in cui invece conviene davvero, e sono probabilmente il tuo:
1. **Il PC è già acceso 24/7 per altri motivi** → il costo marginale è vicino a zero e
   la decisione è vinta senza discussione. *(È la Q1.)*
2. **È un mini-PC a basso consumo** → ~2 €/mese, un quinto dell'alternativa.
3. **Il criterio non è il costo ma il controllo**: nessun fornitore che cambia
   listino o condizioni, nessun dato di voce che esce di casa, nessun limite di
   storage imposto da altri. Sono ragioni solide. Sono solo ragioni diverse dal
   risparmio, e vale la pena sceglierle sapendolo.

### 4.2 Costi una tantum, non ricorrenti

Secondo disco per i backup (F11, obbligatorio); eventuale gruppo di continuità, se
nella tua zona la corrente va via spesso; eventuale mini-PC se il desktop si rivela
troppo esoso. Nessuno di questi è un abbonamento: rispettano il vincolo.

### 4.3 Quello che resta a zero

Motori vocali (eseguiti in locale, modelli con licenza libera), storage (disco di
casa), tunnel (piano gratuito da verificare nei termini d'uso), notifiche push
(servizio gratuito del sistema operativo), certificato TLS (gratuito tramite il
tunnel), DNS dinamico (servizi gratuiti disponibili).

**Totale ricorrente in abbonamenti: 0 €.** Vincolo rispettato. Totale reale: la
bolletta, che dipende da Q1.

---

## 5. Cosa della richiesta iniziale non regge

In ordine di quanto costa scoprirlo tardi.

1. **La disponibilità del sistema ora dipende dal tuo PC, ed è la cosa che è cambiata
   di più.** Se il computer è spento, se salta la corrente, se il tuo operatore ha un
   guasto, **l'app non funziona per nessuno del gruppo**. La chat continua a
   funzionare offline e i messaggi partono dopo, ma **i dadi no**: il tiro è
   autoritativo lato server per costruzione, ed è giusto così. Non metterò un ripiego
   locale: un tiro non verificabile che sembra verificabile è peggio del non poter
   tirare. Conseguenza pratica: **prima di ogni sessione qualcuno deve sapere che il
   server è acceso**, e per questo esiste la pagina di stato.
2. **Con retention infinita e un disco solo, il backup non è un'opzione: è l'unica cosa
   che separa il gruppo dalla perdita della campagna.** Hai detto che archivi e
   cancelli a mano, ed è una scelta legittima; perché sia una scelta e non un
   incidente in attesa, F11 diventa requisito obbligatorio e la prova di ripristino
   entra in questo sprint invece che in S7. Un guasto del disco, a differenza di un
   bug, non si ripara la settimana dopo.
3. **"Trascrizione automatica" resta una bozza da correggere, ma la prognosi è
   migliorata.** Il glossario di campagna — la mitigazione principale del rischio n.1,
   che si era persa scegliendo il motore on-device — **torna possibile** grazie al
   self-hosting. È il guadagno tecnico più importante di tutta la decisione D9, e
   vale da solo più del risparmio. Quanto valga in punti di WER lo dirà SPIKE-1.
4. **"Modulare la mia voce come se fossi un orco" ha ancora due significati diversi.**
   Cambiare timbro *conservando la tua recitazione* è conversione vocale: ora non è
   più esclusa per costo, ma per carico sul PC, che deve già trascrivere e
   sintetizzare per sei persone. Rivalutabile dopo la v1, quando si saprà quanto
   margine ha quella macchina. Nel frattempo SPIKE-2 ti fa scegliere **ascoltando**.
5. **"Stile WhatsApp" nasconde la parte più costosa dell'app.** Una chat che regge
   offline, ordina in modo deterministico, non duplica gli invii e riconcilia al
   ritorno della rete è lo sprint S1 intero, e non si vede. Se in demo sembra "solo
   una chat", è perché è fatta bene.
6. **I dadi sono la funzione più facile da sbagliare in modo invisibile.** Un RNG
   scritto con la funzione random del linguaggio e l'operatore modulo produce una
   distribuzione distorta che **nessuno noterà mai**, e che rende il gioco
   subdolamente ingiusto. Per questo il motore ha un test di distribuzione in CI e un
   seed verificabile.
7. **Gli allegati video adesso hanno un collo di bottiglia nuovo: la banda in salita
   della tua linea**, non lo spazio su disco. Sei giocatori che scaricano una mappa
   passano tutti da lì, e sulle linee domestiche l'upload è spesso una frazione del
   download. SPIKE-0 la misura. Se è stretta, la scelta onesta è ridurre i limiti o
   togliere il video dalla v1.

---

## 6. Definition of Done dello sprint

- [ ] `pnpm install && pnpm lint && pnpm typecheck && pnpm test` verde da clone pulito
- [ ] APK installato e funzionante su **almeno due telefoni reali** del gruppo
- [ ] Aggiornamento OTA dimostrato su device, senza reinstallazione
- [ ] `docker compose up -d` riproducibile sul PC di casa, **sopravvive a un riavvio reale**
- [ ] Servizio raggiunto **da fuori casa su rete mobile**, con TLS valido e WebSocket stabili
- [ ] **Ripristino da backup eseguito davvero**, con tempo annotato nel runbook
- [ ] `docs/quality/stt-benchmark.md`: WER complessivo **e sui nomi propri**, per le 4 configurazioni, con i tempi sul PC reale
- [ ] `docs/quality/voice-benchmark.md`: file audio e verdetto per preset
- [ ] SPIKE-4: due esiti pass/fail documentati
- [ ] `docs/costs.md` con consumo elettrico **misurato**, non stimato
- [ ] `docs/infra/raggiungibilita.md` con esito CGNAT, latenze e banda in salita
- [ ] `docs/licenses.md`, `docs/devices.md`, `docs/risks.md`, `docs/backlog.md` popolati
- [ ] ADR-001 … ADR-008 aperti, con almeno 001–005 chiusi
- [ ] `sprint-00-consuntivo.md`: SP pianificati vs completati e **causa reale** degli scostamenti
- [ ] Le 3 domande per lo Sprint 1

---

## 7. Cosa NON si fa in questo sprint

Nessuna schermata di chat, nessuna autenticazione, nessun messaggio scambiato fra due
telefoni, nessun dado. Il codice degli spike è **usa e getta**: va marcato
`// SPIKE: da buttare` e cancellato in S1, non promosso a fondamenta. Se a fine sprint
mi trovo a voler "salvare" un harness perché tanto funziona, è il segnale che l'ho
scritto sbagliato.

---

## Fonti

- Listini cloud consultati il 17/09/2026 e ora **superati dalla decisione D9**, tenuti
  come termine di paragone in §4.1: [Google Cloud Text-to-Speech](https://cloud.google.com/text-to-speech/pricing) ·
  [Google Cloud Speech-to-Text](https://cloud.google.com/speech-to-text/pricing) ·
  [Cloudflare R2](https://developers.cloudflare.com/r2/pricing/) · [Supabase](https://supabase.com/pricing)
- Prezzo dell'energia: **la tua bolletta**, non una media nazionale.

---

## 8. Reperti dello sprint (aggiornato in corsa)

Annotazioni tecniche emerse lavorando, che valgono per tutti gli sprint successivi.

### R1 — React Native 0.87: i tipi generati rompono lo stile

**Sintomo**: qualunque stile, anche `{ backgroundColor: 'red' }`, viene rifiutato dal
controllo di tipo con messaggi fuorvianti (`Did you mean 'backgroundClip'?`).

**Causa**: RN 0.87 espone come tipi predefiniti quelli generati automaticamente dal
codice Flow (`types_generated/`), in cui `ViewStyle` e `TextStyle` non contengono le
proprietà attese. Non è un errore del nostro codice.

**Soluzione adottata**: RN pubblica anche i tipi scritti a mano, raggiungibili tramite
una condizione di risoluzione dedicata. In `apps/mobile/tsconfig.json`:

```json
"customConditions": ["react-native-legacy-deep-imports", "react-native"]
```

**Da rivalutare**: a ogni aggiornamento di SDK. Quando i tipi generati saranno
corretti, questa riga va tolta — e va tolta consapevolmente, non dimenticata lì.

### R2 — Convenzione: nessuno stile inline

Conseguenza pratica di R1, ma buona pratica a prescindere: gli stili si dichiarano in
`StyleSheet.create`, mai come oggetto scritto dentro il JSX. Per i temi chiaro e scuro
si costruiscono **due fogli completi una volta sola** (`src/design/stili.ts`), invece
di comporre colori a ogni render. Più veloce, e non urta i tipi.

### R3 — La regola di confine fra feature aveva un buco

La prima versione bloccava solo gli import con alias (`@/features/…`) e lasciava
passare quelli relativi (`../../dadi/ui/Dado`): esattamente la forma che un editor
genera da solo con il completamento automatico. Trovato dal test che verifica la
regola, non a occhio.

Conferma che vale la pena **testare le regole di lint architetturali come si testa il
codice**: una regola che nessuno verifica è una regola che un giorno sarà aggirata
senza che nessuno se ne accorga.

### R4 — Metro in monorepo richiede configurazione esplicita

Con pnpm, che usa collegamenti simbolici, Metro non risolve `@tabletop/shared` senza
`watchFolders`, `nodeModulesPaths` e `unstable_enableSymlinks`. Già in
`apps/mobile/metro.config.js`. Da verificare sul device: la risoluzione funziona in
fase di compilazione dei tipi, ma Metro è un risolutore diverso e va provato davvero.

---

## 9. Consuntivo parziale al 20/09/2026

| Story | SP | Stato |
|---|---|---|
| S0-01 Monorepo e confini | 2 | **completato** — AC della regola di confine verificato da test |
| S0-02 App Android | 3 | 2 — codice e configurazione pronti, manca l'APK su device |
| S0-03 Firma, distribuzione, OTA | 2 | 0 |
| S0-04 SPIKE-0 raggiungibilità | 2 | 0 |
| S0-05 Compose server | 2 | 1 — scritto e validato, mai eseguito sul PC reale |
| S0-06 Backup e ripristino | 1 | **rinviato a S7** per decisione dell'utente (20/09); codice pronto sotto profilo `backup` |
| S0-07 SPIKE-1 trascrizione | 4 | 2 — strumenti, protocollo e tracce pronti, zero dati |
| S0-08 SPIKE-2 voci | 2 | 1 — modulo nativo e interfaccia pronti, DSP da collegare |
| S0-09 SPIKE-4 trappole native | 1 | 0 |
| S0-10 Licenze | 1 | **completato** |
| | **20** | **~9,5** |

**Scostamento e sua causa reale**: metà dello sprint è fatta, e la metà mancante è
quasi interamente **lavoro che richiede hardware** — telefoni, il PC di casa, la linea
domestica, una voce umana. Non è un ritardo di esecuzione: è la divisione del lavoro
dichiarata in §0 fin dall'inizio.

Due scostamenti di priorità, presi consapevolmente:
- **CI spostata a S1** (~1 SP): fra automatizzare la build e sapere se il server è
  raggiungibile, adesso vale di più la seconda.
- **SPIKE-3 dadi spostato a inizio S2** (~2 SP): i rischi nuovi introdotti dal
  self-hosting sono più alti di quello sull'animazione, e lo sprint dei dadi è
  comunque S2.

**Un rischio chiuso in anticipo**: R9 licenze. La libreria di pitch shifting più
diffusa è GPL v2 e avrebbe obbligato a distribuire il sorgente dell'intera app; è
stata sostituita con un'alternativa MIT prima di scrivere una riga di DSP. Il master
prompt lo segnalava come bloccante se scoperto a S5.
