# Dungeon d20 — roguelike a turni con regole D&D 5e per Android

Roguelike classico (dungeon generato proceduralmente, morte permanente, turni discreti)
il cui motore di gioco implementa il regolamento **D&D 5e / SRD 5.1**: tiri di d20 con
vantaggio/svantaggio, classe armatura, competenza, tiri salvezza, critici, condizioni,
tiri salvezza contro morte, progressione per PX.

```
    ########
    #......#  #######
    #......#  #.....#
    #...<..####!....#          <  scala di salita      >  scala di discesa
   #................#          !  pozione             [  armatura
   #..)....####.[...#          )  arma                ^  trappola scoperta
   #...!...#  #.....#          +  porta chiusa        '  porta aperta
   #.......#  #.....#          @  personaggio         g,o,s,...  mostri
   #.##+####  ####.##
            ...
   #.............@......#
```

> Schermata reale prodotta dal runner testuale incluso
> (`./gradlew :core:run --args="--demo 120 --class guerriero --seed 7777"`).

---

## 1. Stato del progetto: cosa è verificato e cosa no

Dichiarazione onesta, prima di tutto il resto.

| Componente | Stato | Verifica eseguita |
|---|---|---|
| `:core` — motore di regole, dungeon, IA, salvataggi, audio | **completo e funzionante** | 96 test JUnit 5 verdi, più 60 partite complete giocate da un bot |
| Runner testuale JVM (`:core:run`) | **completo e funzionante** | partite reali eseguite end-to-end |
| `:app` — interfaccia Android (Jetpack Compose) | **compila e produce un APK installabile** | build automatica su GitHub Actions; **mai eseguita su un dispositivo** |

### Scarica l'APK

L'ambiente di sviluppo non ha Android SDK e la sua policy di rete blocca
`dl.google.com`, quindi la compilazione del modulo `:app` è delegata ai runner di
GitHub Actions. Ad ogni push sul ramo di sviluppo la pipeline esegue i test del
motore, costruisce l'APK di debug e aggiorna una release con link diretto:

**Link diretto (sempre aggiornato all'ultima build):**
https://github.com/mvalmori74/ChangeWaveMv/releases/download/dungeon-apk-latest/dungeon-d20-debug.apk

Pagina della release: https://github.com/mvalmori74/ChangeWaveMv/releases/tag/dungeon-apk-latest

Installazione: scarica il file sul telefono e consenti l'installazione da origini
sconosciute. È una build di **debug**, firmata con la chiave di debug di Android:
va bene per provare il gioco, non è pubblicabile sul Play Store (servirebbe una
build di release firmata con una chiave propria, vedi Sprint 4).

Lo stesso APK è disponibile come artifact di ogni esecuzione del workflow
`Dungeon APK`, con ritenzione di 30 giorni.

### Cosa resta non verificato

L'APK **compila e si impacchetta**, ma non è ancora stato installato né aperto su
un dispositivo o un emulatore: non ci sono test strumentati, quindi eventuali
errori di runtime (crash all'avvio, layout illeggibili su schermi piccoli,
tocchi che non registrano) non sarebbero ancora emersi. La compilazione esclude
gli errori di sintassi e di API, non quelli di comportamento.

Primo collaudo consigliato: installare l'APK, creare un guerriero, scendere di un
livello, salvare uscendo dall'app e riprendere la partita.

## 2. Architettura

```
dungeon-rogue/
├── core/                        Kotlin/JVM puro — nessuna dipendenza Android
│   └── com.changewave.dungeon/
│       ├── rules/               Dice, Abilities, Conditions, Combat   (regole SRD)
│       ├── model/               Actor, PlayerCharacter, Monster, Bestiary, Items, Spells
│       ├── dungeon/             DungeonGenerator (BSP), FieldOfView, Pathfinding, Tile
│       ├── game/                GameEngine, GameState, MonsterAi, MessageLog, SaveGame
│       └── cli/                 TerminalGame (runner testuale)
└── app/                         Android, Jetpack Compose
    └── com.changewave.dungeon.android/
        ├── vm/GameViewModel     snapshot immutabile dello stato per la UI
        ├── ui/                  Canvas della mappa, HUD, log, D-pad, dialoghi
        └── data/SaveStore       salvataggio atomico su file privato
```

Scelta portante: **il motore non sa nulla della UI**. `GameEngine.execute(Command)` è
l'unico punto di ingresso, lo stato è serializzabile per intero, e la UI riceve uno
snapshot immutabile. Conseguenze pratiche:

- tutto il gioco è testabile in JUnit, senza emulatore;
- la stessa logica gira su terminale, su Android e (senza modifiche) su un eventuale
  backend o su desktop;
- il salvataggio include lo stato del generatore casuale: **una partita ricaricata prosegue
  con esattamente la stessa sequenza di dadi** (verificato da test).

### Modello del tempo

Punti azione, non turni fissi: ogni tick tutte le creature guadagnano energia pari alla
propria velocità (piedi/turno del manuale) e agire costa 30. Un lupo crudele (50 ft) agisce
1,67 volte per ogni azione del personaggio (30 ft), uno zombi (20 ft) 0,67. Questo rende
significative le velocità del bestiario senza turni "saltati".

---

## 3. Regole D&D 5e implementate

Implementate e coperte da test:

- **d20**: tiro, vantaggio/svantaggio (con annullamento reciproco), critico naturale 20
  (raddoppia i **dadi**, non il modificatore), fallimento naturale 1.
- **Caratteristiche**: sei punteggi, modificatore `floor((punteggio-10)/2)`, array standard
  o 4d6 scarta il minore, incrementi ai livelli 4 e 8.
- **Competenza**: `2 + floor((livello-1)/4)`, applicata ad attacchi, TS di classe, CD incantesimi.
- **Classe armatura**: armatura + DES (con limite per armature medie/pesanti) + scudo + bonus magici.
- **Attacchi**: bonus = caratteristica + competenza; armi finesse (migliore tra FOR e DES),
  armi a distanza (DES), armi a due mani (niente scudo).
- **Tiri salvezza**: competenza di classe, fallimento automatico su FOR/DES se paralizzati o privi di sensi.
- **Condizioni**: avvelenato, prono, accecato, spaventato, paralizzato, stordito, trattenuto,
  privo di sensi, benedetto, accelerato — con effetti su vantaggio/svantaggio e critici automatici.
- **Danni**: 12 tipi, immunità e resistenze del bestiario (lo scheletro dimezza il perforante, ecc.).
- **Morte**: a 0 PF si cade privi di sensi; tiri contro morte 3 successi/3 fallimenti; colpo
  in mischia su corpo privo di sensi = critico automatico = 2 fallimenti; 20 naturale = 1 PF.
- **Classi**: Guerriero (Recuperare Energie), Ladro (Attacco Furtivo, `ceil(liv/2)d6`),
  Chierico e Mago (incantatori completi con slot di 1° e 2° livello da tabella PHB).
- **Incantesimi**: dardo di fuoco, fiamma sacra, dardo incantato (colpisce sempre, +1 dardo
  per slot superiore), mani brucianti (area, TS dimezza), cura ferite, benedizione;
  trucchetti che raddoppiano i dadi al 5° livello.
- **Progressione**: tabella PX del PHB fino al livello 10, PF = dado vita + modificatore COS.
- **Bestiario SRD**: 16 creature da GS 1/8 a GS 6 con le statistiche originali
  (ratto gigante, coboldo, goblin, scheletro, zombi, orco, hobgoblin, ragno gigante, ghoul,
  lupo crudele, bugbear, ogre, orsogufo, wight, troll e il boss finale).

Consapevolmente **fuori perimetro** (semplificazioni da roguelike, documentate):
reazioni e attacchi di opportunità, azioni bonus come categoria, copertura, concentrazione,
riposo lungo, incantesimi oltre il 2° livello, multiclasse, sottoclassi, componenti materiali,
peso trasportato e fame.

---

## 4. Colonna sonora procedurale

Non ci sono file audio nel progetto: la musica e' **sintetizzata in tempo reale**
da `:core` e diventa piu' macabra scendendo. Scelta dettata da tre vincoli
concreti: nessuna licenza da gestire, APK invariato (dieci tracce registrate
sarebbero decine di MB) e transizione *continua* con la profondita' invece di
stacchi fra brani.

Catena di segnale, tutta in `AmbientMusicEngine`:

```
bordone (4 osc. scordati) ┐
pad melodico (6 voci)     ├─> somma ─> passa-basso ─> +eco ─> saturazione ─> volume
campana inarmonica        │                           (linea di ritardo
battito cardiaco          │                            con riaccoppiamento)
letto di rumore filtrato  ┘
```

### Primo livello: tema d'avventura

Il livello 1 non e' cavernoso: e' ancora "in superficie" e suona come una partenza,
non come una tomba. Materiale melodico originale, generato da regole:

- modo **misolidio** (maggiore con settima abbassata), giro armonico I - IV - v - IV;
- arpeggio percussivo tipo marimba con **accenti in controtempo** — la sincope e'
  cio' che da' il carattere caraibico a una sequenza altrimenti neutra;
- basso camminante in semiminime su fondamentale, quinta e terza, shaker sui
  contrattempi, 96 bpm;
- melodia costruita su una cellula ritmica di due battute, ripetuta con variazioni
  di altezza vincolate alla scala e agli accordi: e' la regola minima che
  distingue una melodia da una sequenza casuale.

Dal livello 2 comanda la progressione cavernosa descritta sotto. Il passaggio fra
i due stili avviene miscelandoli durante la dissolvenza, quindi le voci in
decadimento non vengono troncate.

### Dal secondo livello in giu': la discesa

Cosa cambia scendendo (`MusicDirector`, tutto monotono nella profondita'):

| Parametro | Livello 2 | Livello 10 |
|---|---|---|
| Scala | eoliana (minore naturale) | costruita sul tritono |
| Fondamentale | 55,0 Hz | 41,2 Hz |
| Intervallo del bordone | quinta giusta | tritono |
| Battito cardiaco | assente (entra al 3°) | 74 bpm |
| Campana a rintocchi | assente (entra al 5°) | presente |
| Letto di rumore | 5% | 38% |
| Riaccoppiamento dell'eco | 0,30 | 0,72 |
| Cluster di seconde minori | 5% delle note | 60% delle note |
| Indice di macabrita' | 6% | 100% |

Il cambio di livello non e' uno stacco: i parametri migrano con una dissolvenza
di 4 secondi, verificata da test.

### Usare un proprio brano

Dalle impostazioni si puo' scegliere **un file audio del telefono** come colonna
sonora di un livello qualsiasi (per default il primo). Il file resta dove sta:
l'app lo legge tramite il selettore di sistema con permesso persistente, non lo
copia al proprio interno e non lo distribuisce. Se il file viene spostato,
cancellato o non e' riproducibile, il gioco lo segnala e torna alla musica
generata invece di restare muto.

Questa e' anche la via corretta per usare musica di cui si possiedono i diritti:
nulla di protetto entra nel repository o nell'APK.

**Volume regolabile** dall'interfaccia: menu principale → *Impostazioni*, oppure
in partita dalla scheda del personaggio. Interruttore di attivazione e cursore
0-100% (con curva percettiva, non lineare), salvati in `SharedPreferences` e
ripristinati al riavvio. A 0% il segnale e' silenzio assoluto, non un suono
attenuato.

### Ascoltarla senza Android

```bash
./gradlew :core:runMusicExport -PskipAndroid=true \
  --args="--out ./musica --seconds 40 --depths 1,5,10 --descent"
```

Genera file WAV con lo stesso codice che gira sul telefono; `--descent` produce
una traccia che scende dal livello 1 al 10 per sentire la transizione.

### Un difetto trovato misurando, non ascoltando

La prima versione metteva l'**83% dell'energia sotto gli 80 Hz**: corretta sulla
carta (un dungeon deve suonare grave), inudibile in pratica su un altoparlante di
telefono, che sotto i 200 Hz non riproduce nulla. L'analisi spettrale dei WAV
generati l'ha reso evidente. Dopo la ritaratura — bordone spostato su ottava e
quinta superiori, pad in registro 300-1500 Hz, rumore portato nei medi, battito
alleggerito — la banda 200-2500 Hz raccoglie il 44-79% dell'energia a tutte le
profondita', mentre il sub-basso resta come peso per chi ascolta in cuffia.
Un test di regressione impone ora una soglia minima su quella banda.

## 5. Come si costruisce ed esegue

### Motore e test (funziona ovunque, senza Android SDK)

```bash
cd dungeon-rogue
./gradlew :core:test -PskipAndroid=true          # 77 test
./gradlew :core:run  -PskipAndroid=true --console=plain --args="--class ladro"
./gradlew :core:run  -PskipAndroid=true --console=plain --args="--demo 300 --seed 42"
```

Comandi del runner: `y k u / h . l / b j n` direzioni, `.` attendi, `>` scendi, `,` raccogli,
`i` zaino, `q` bevi pozione, `f` incantesimo, `s` Recuperare Energie, `x` esci.

### APK Android

Il modo più rapido è scaricarlo dalla release (vedi sezione 1). Per costruirlo in
locale servono Android SDK e accesso a Google Maven:

```bash
cd dungeon-rogue
./gradlew :app:assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew installDebug              # su dispositivo/emulatore collegato
```

Requisiti: Android Studio Ladybug o successivo, JDK 17, `compileSdk 35`, `minSdk 24`
(Android 7.0 — copre oltre il 95% dei dispositivi attivi).

Nota sulla struttura della build: le versioni dei plugin stanno nel blocco
`buildscript` del progetto root e i moduli li applicano per id. Il plugin Kotlin
per Android carica per riflessione classi dell'Android Gradle Plugin, quindi i due
devono stare sullo stesso classloader; dichiararli in moduli diversi con la DSL
`plugins {}` li separa e la build fallisce. AGP entra nel classpath solo quando
`:app` fa parte della build, così il motore resta compilabile anche senza SDK.

---

## 6. Lavorare dal PC o da Claude Code sul web

Il ramo su GitHub è l'unico punto di verità: si può lavorare indifferentemente in
locale o dal cloud, a patto di rispettare una regola sola — **un lato alla volta
sullo stesso ramo**. L'ambiente cloud è effimero: ciò che non è committato e
pushato non esiste.

### Prima volta sul PC

```bash
git clone https://github.com/mvalmori74/ChangeWaveMv.git
cd ChangeWaveMv
git checkout claude/dungeon-rogue-dnd-android-09lksd
cd dungeon-rogue
./gradlew :core:test -PskipAndroid=true     # verifica che tutto giri
```

Per l'app Android apri in Android Studio la cartella `dungeon-rogue` (non la
radice del repository): JDK 17 e Android SDK vengono gestiti dall'IDE. Per il solo
motore basta un JDK 17, senza SDK: il modulo `:app` viene escluso da solo.

### Ogni volta che si cambia lato

| Quando | Sul PC | Qui (sessione cloud) |
|---|---|---|
| Prima di iniziare | `git pull --rebase origin <ramo>` | dimmi "ho lavorato dal PC": faccio `git pull` prima di toccare qualsiasi cosa |
| Prima di smettere | `git commit` + `git push` | committo e pusho io a fine lavoro |

Se si lavora davvero in parallelo, meglio due rami separati e un merge, invece di
alternarsi sullo stesso: evita rebase e push forzati.

### Cosa non entra in git (già escluso)

`local.properties` (percorso dell'Android SDK, diverso su ogni macchina),
`.gradle/`, `build/`, `.idea/`, `*.iml`, `*.apk`. Il file `.gitattributes`
normalizza i fine-riga a LF, con l'eccezione dei `.bat`: senza di esso, alternando
Windows e Linux ogni file toccato comparirebbe come modificato per intero.

### Cosa conviene fare dove

| Attività | Dove |
|---|---|
| Modifiche al motore, test, bilanciamento | indifferente |
| Build e firma dell'APK | cloud (pipeline) o PC con SDK |
| **Installare e provare l'app sul telefono, leggere `logcat`** | **solo PC** (qui il container non raggiunge il telefono) |
| Debug passo-passo dell'app in esecuzione | solo PC, con Android Studio |

## 7. Verifica eseguita

`./gradlew :core:test` — **96 test, tutti verdi**. Non solo unitari:

- **Proprietà del generatore** (30 livelli per esecuzione): connettività totale verificata a
  flood fill, scale sempre raggiungibili, nessun mostro/oggetto dentro un muro o sovrapposto,
  densità di pavimento entro limiti, riproducibilità a parità di seed.
- **Distribuzioni casuali**: uniformità del d20 su 200.000 tiri (deviazione < 5% per faccia),
  media con vantaggio 13,825 e con svantaggio 7,175 (valori teorici) entro ±0,15.
- **Soak test**: 60 partite complete giocate da un bot, con invarianti controllate ogni 100
  turni (niente cadaveri sulla mappa, PF nei limiti, nessuna sovrapposizione).
- **Salvataggi**: round-trip completo, e prosecuzione identica dopo il ricaricamento.
- **Audio** (19 test): nessuna saturazione a nessuna profondità, RMS nell'intervallo
  utile, silenzio assoluto a volume zero, indipendenza dalla dimensione del blocco
  (nessun clic fra i buffer), nessun salto oltre soglia fra campioni consecutivi,
  transizione di profondità graduale e soglia minima di energia nella banda
  riprodotta dagli altoparlanti dei telefoni. Il tema del primo livello è
  verificato come **ritmico** misurando il contrasto di autocorrelazione
  dell'inviluppo al periodo della semiminima: 5-8 per un brano suonato contro
  ~0,8 per un bordone continuo. Il valore assoluto dell'autocorrelazione non
  distingue i due casi — un bordone lento è correlato a qualunque ritardo — e
  una prima versione del test, basata sul fattore di cresta, era troppo vicina
  al valore misurato per essere affidabile.
- **Prestazioni** (JVM desktop): turno completo **0,07 ms**, generazione di un livello
  **0,1 ms**. Anche con un fattore 10 su un telefono di fascia bassa si resta due ordini di
  grandezza sotto il budget di 16 ms per frame.

Due bug reali sono stati trovati proprio da questi test e corretti:

1. `Pathfinding.findPath` trattava le porte chiuse come muri: giocatore e mostri restavano
   bloccati in regioni isolate benché il dungeon fosse generato connesso (il flood fill di
   connettività, che le considerava attraversabili, non lo rilevava).
2. Curva di difficoltà sbagliata: il budget incontri cresceva del 22% a livello, molto più in
   fretta dei PX necessari a salire di livello. Nessun personaggio superava la profondità 3.

### Bilanciamento misurato (bot, 60 partite)

| Metrica | Prima della taratura | Dopo |
|---|---|---|
| Morti su 60 partite | 41 | 60 |
| Morti al livello 1 | 19 | 9 |
| Profondità massima raggiunta | 3 | 7 |
| Profondità media | 1,6 | 2,5 |

Il bot non ritira mai e non ottimizza: è una sonda di regressione, non un giocatore. Il dato
utile è il **profilo**: la morte al primo livello non è più l'esito normale e i livelli
profondi sono raggiungibili. La taratura fine va fatta con playtest umano (Sprint 3).

---

## 8. Pianificazione a sprint (2 settimane, sviluppatore senior)

Stima in giorni-uomo di uno sviluppatore senior che parta da zero, per parametrare il lavoro.

### Sprint 1 — Motore di regole e dungeon — **COMPLETATO E VERIFICATO** (~9 gg/uomo)

| Attività | gg |
|---|---|
| RNG deterministico serializzabile, dadi, notazione `2d6+3`, vantaggio/svantaggio | 0,5 |
| Caratteristiche, competenza, CD, progressione PX e livelli | 0,5 |
| Condizioni e loro effetti meccanici | 0,5 |
| Combattimento: attacchi, critici, TS, resistenze, tiri contro morte | 1,5 |
| Bestiario SRD (16 creature), oggetti, incantesimi, classi giocabili | 1,5 |
| Generatore BSP, porte, scale, popolamento a budget PX, trappole, bottino | 1,5 |
| Campo visivo (shadowcasting), A* a 8 direzioni, IA dei mostri per profilo | 1,0 |
| Motore a punti azione, comandi, log, salvataggio JSON versionato | 1,0 |
| Suite di test (77), soak test, taratura del bilanciamento | 1,0 |

### Sprint 2 — App Android — **CODICE SCRITTO, DA COMPILARE E COLLAUDARE** (~8 gg/uomo, di cui ~6 già svolti)

| Attività | gg | Stato |
|---|---|---|
| Progetto Gradle Android, manifest, tema, icona adattiva | 0,5 | fatto |
| ViewModel con snapshot immutabile e comandi fuori dal main thread | 1,0 | fatto |
| Renderer della mappa su Canvas con viewport e memoria della mappa | 1,5 | fatto |
| HUD, log colorato, croce direzionale, barra azioni contestuale | 1,5 | fatto |
| Creazione personaggio, menu, schermata di fine partita | 1,0 | fatto |
| Zaino, incantesimi, selezione bersaglio | 0,5 | fatto |
| Persistenza atomica e ripresa della partita | 0,5 | fatto |
| Pipeline di build automatica dell'APK su GitHub Actions | 0,5 | fatto |
| Compilazione e correzione degli errori di build | 0,5 | fatto |
| **Collaudo su dispositivo reale e correzione dei difetti di runtime** | **1,0** | **da fare** |

### Sprint 3 — Giocabilità e rifinitura (~9 gg/uomo, di cui ~3,5 svolti)

| Attività | gg | Stato |
|---|---|---|
| Sintetizzatore procedurale e progressione musicale per profondità | 1,5 | fatto |
| Tema d'avventura originale per il primo livello (strato ritmico-melodico) | 0,5 | fatto |
| Brano personalizzato dai file del telefono, per livello | 0,5 | fatto (da collaudare) |
| Schermata impostazioni con volume regolabile e persistenza | 0,5 | fatto |
| Riproduzione in streaming su Android (AudioTrack) e gestione del ciclo di vita | 0,5 | fatto (da collaudare) |
| Playtest e taratura della difficoltà con giocatori reali | 2,0 | da fare |
| Tutorial dei primi tre livelli | 1,0 | da fare |
| Animazioni di attacco e danno, feedback aptico | 1,0 | da fare |
| Effetti sonori delle azioni (colpi, pozioni, trappole) | 1,0 | da fare |
| Accessibilità: dimensione glifi, daltonismo, TalkBack | 1,0 | da fare |
| Rotazione e tablet, test strumentati Compose, profilazione | 1,0 | da fare |

### Sprint 4 — Contenuti e pubblicazione (~9 gg/uomo, da fare)

Ampliamento del bestiario e delle stanze speciali (santuari, negozi, vault); oggetti magici
con proprietà attive; due classi aggiuntive; incantesimi di 3° livello; firma dell'APK,
scheda Play Store, privacy policy, canale di test interno; telemetria anonima di
bilanciamento (profondità di morte, cause) per chiudere il ciclo sulla difficoltà.

**Totale progetto: ~35 giorni-uomo senior. Consegnato ora: ~19,5 giorni-uomo**, di cui
11 verificati da test automatici, 8,5 verificati fino alla compilazione e al
confezionamento dell'APK, e il collaudo sul dispositivo ancora da fare.

### Rischi residui (in ordine di probabilità)

1. **Difetti di runtime dell'app** — probabilità media, impatto medio: l'APK compila ma
   non è mai stato eseguito. I candidati tipici sono il primo avvio, la rotazione e il
   ripristino del salvataggio.
2. **Leggibilità dei glifi su schermi piccoli** — probabilità media: potrebbe servire
   passare da glifi ASCII a tile grafiche (2-3 gg aggiuntivi).
3. **Difficoltà ancora sbilanciata sui livelli 5-10** — probabilità media: il bot non arriva
   abbastanza in fondo da misurarla; serve playtest umano.
4. **Prestazioni su dispositivi molto vecchi** — probabilità bassa: margine attuale ampio.
5. **Resa della musica sull'altoparlante reale** — probabilità media: il bilanciamento
   spettrale è verificato numericamente, ma il timbro finale dipende dal trasduttore;
   potrebbe servire un ritocco dopo il primo ascolto su telefono.

---

## 9. Licenza dei contenuti

Le meccaniche e le statistiche delle creature derivano dal **System Reference Document 5.1**
di Wizards of the Coast LLC, disponibile sotto licenza
[Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/legalcode).
Vedi `NOTICE.md`. Il codice di questo progetto è materiale originale.
