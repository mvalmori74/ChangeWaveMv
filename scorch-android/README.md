# ScorchWave 🔥

Gioco Android di artiglieria a turni in stile **Scorched Earth**: carri armati che si
bombardano a vicenda su un terreno distruttibile, con vento, gravità, negozio armi e IA.

Scritto in **Kotlin nativo**, motore di gioco custom su `SurfaceView` (nessuna libreria
di terze parti, nessun asset esterno: tutta la grafica è disegnata a runtime su `Canvas`).

## 🎮 Come si gioca

Ogni carro spara a turno regolando **angolo** e **potenza**. Il proiettile segue una
parabola influenzata da gravità e vento; ogni esplosione scava il terreno e la terra
sovrastante frana. Chi resta l'ultimo vivo vince il round.

**Comandi touch:**

| Comando | Dove | Azione |
|---|---|---|
| Trascinamento sul campo | — | Mira rapida: la direzione imposta l'angolo, la lunghezza del trascinamento la potenza |
| ◀ ▶ | in alto a sinistra | Inclinazione del cannone (tenendo premuto accelera) |
| ◀◀ ▶▶ | in alto a sinistra | Muove il carro: scala anche le pareti dei crateri, consumando carburante in proporzione alla salita |
| – + | in basso a sinistra | Potenza, con la sua barra |
| ARMA | in basso a destra | Apre l'arsenale e cambia munizione |
| FUOCO | in basso a destra | Spara |
| ☰ | in alto a destra | Pausa / classifica / audio / uscita |

I dati del carro di turno (vita, scudo, denaro) stanno in basso a sinistra, sotto la
potenza; round e vento in alto al centro.

Una traiettoria tratteggiata mostra l'inizio del tiro, e la scia del colpo precedente
resta visibile in trasparenza per correggere la mira.

## 💣 Arsenale

| Arma | Costo | Raggio | Danno | Effetto |
|---|---|---|---|---|
| Missile Baby | gratis, illimitato | 32 | 15 | Colpo base |
| Missile | 1.000 (x5) | 50 | 27 | Esplosione media |
| Baby Nuke | 3.500 (x3) | 78 | 42 | Ordigno medio |
| Nuke | 9.000 (x2) | 130 | 70 | Il colpo più grosso: non uccide da solo un carro intatto |
| Funky Bomb | 4.000 (x3) | 38 | 12 | All'impatto sparge 7 frammenti (raggio 36, danno 13) |
| MIRV | 6.000 (x2) | — | — | Si divide in 5 testate (raggio 43, danno 19) al culmine della parabola |
| Roller | 3.000 (x5) | 57 | 30 | Tocca terra e **rotola**, anche all'indietro: esplode quando tocca un carro |
| Digger | 2.500 (x5) | 64 | 35 | Si conficca nel terreno e scava **al massimo 70 unità**, poi esplode |
| Palla di Terra | 2.000 (x5) | 68 | — | Non fa danno: costruisce una collina di protezione |

I valori sono stati ridimensionati mantenendo le proporzioni fra le armi (raggi ×0,70 e
danni ×0,60 rispetto alla prima versione, che era troppo letale); il guadagno per punto
danno è salito a 100 $ perché il negozio resti allo stesso ritmo.

**Roller.** Rotola per inerzia: la pendenza lo accelera in discesa e lo frena in salita,
quindi contro una parete torna indietro invece di fermarsi lì. Esplode solo quando tocca
un carro, esce dal campo o si ferma per più di 0,8 s — non a ogni pianoro.

**Digger.** Fora il terreno facendo franare la terra sopra, come deve, ma solo per un breve
tratto: al massimo **45 unità di scavo** e **22 di scostamento** dal punto d'ingresso, con
la galleria che si assottiglia scendendo, poi esplode. Misurato su tre angoli di arrivo
diversi (35°, 60°, 80°): scava 43-44 unità restando entro 6 unità dall'ingresso, e la buca
finale è larga 128 unità su 1600 (in pratica il solo cratere dell'esplosione) e profonda 113.
Prima scavava 115-120 unità vagando fino a 69 di lato, per una buca larga 184 e profonda 210.

Equipaggiamento: **scudo** (assorbe i danni prima dello scafo), **kit riparazione**,
**carburante**. Si comprano nel negozio fra un round e l'altro con i soldi guadagnati
dai danni inflitti (60 $ per punto danno, 6.000 $ per carro distrutto, 8.000 $ per il
superstite del round).

Extra fedeli all'originale: reazioni a catena (un carro distrutto esplode e può
uccidere i vicini), danno da caduta quando il terreno sotto al carro viene scavato,
frane del terreno, vento variabile a ogni round, quattro palette ambientali.

**Uscire dai crateri.** Finire sul fondo di una buca non è una condanna: il carro
risale la parete consumando carburante in proporzione alla pendenza (circa 19 unità
per un cratere da missile baby, 74 per quello di una nuke, su un serbatoio da 100).
Il carburante si ricarica di 25 a ogni turno e si compra al negozio, quindi da una
buca profonda si esce in due o tre turni. Restano invalicabili solo le pareti
naturali davvero a picco (oltre 105 unità di dislivello nel raggio di manovra): in
quel caso la HUD avvisa con «PARETE TROPPO ALTA» e si può sempre spianare il terreno
con una palla di terra. Anche l'IA, se finisce in una conca, risale verso il bordo
più basso prima di sparare.

## 🔊 Audio

Il gioco ha effetti sonori **sintetizzati a runtime**: nessun file audio nell'APK. Al primo
avvio i suoni vengono generati in codice (oscillatori, rumore filtrato, inviluppi) e salvati
come WAV nella cache (~180 KB in tutto), poi riprodotti con `SoundPool`.

| Suono | Come è fatto |
|---|---|
| Cannone | corpo che scende da ~220 a ~60 Hz più uno schiocco; volume e tono seguono la potenza |
| Fischio del proiettile | anello tonale a 970 Hz, in loop, la cui velocità di riproduzione segue la caduta |
| Esplosioni | rumore che si scurisce più un corpo grave: ~870 Hz per un missile, ~220 Hz per una nuke |
| Frana / palla di terra | granuli di rumore filtrati a due poli, sordi (~220 Hz) |
| Carro distrutto | boato con risonanza metallica |
| Tonfo di caduta | grave breve, intensità proporzionale all'altezza |
| Inizio turno, acquisto, tocchi | note brevi e clic discreto |

L'audio si spegne dal menu (casella **Audio**, scelta ricordata) o durante la partita dal
menu di pausa.

## 📶 Partita a due via Bluetooth

Dal menu, **GIOCA IN DUE (BLUETOOTH)**: un telefono preme *Ospita* (diventa visibile per
3 minuti), l'altro preme *Cerca avversari* e lo sceglie dall'elenco — i dispositivi già
accoppiati compaiono subito. Chi ospita comanda il carro rosso e decide le impostazioni
della partita (round, vento, denaro); chi si unisce comanda il blu.

Il collegamento è **Bluetooth classico (RFCOMM)**, senza internet né server.

**Come resta sincronizzata la partita.** Sulla rete viaggia pochissimo: la mossa completa
del giocatore di turno (posizione finale, carburante, angolo, potenza, arma), gli acquisti
fatti al negozio e, a fine turno, una fotografia dello stato inviata da chi ospita. Tutto
il resto è ricalcolato in locale, perché la simulazione è **deterministica**: il motore usa
tre generatori casuali separati, e quelli che contano dipendono solo da seme, round e
numero di turno — mentre le particelle e gli effetti grafici hanno un generatore libero che
non può spostare di un millimetro l'esito di un tiro. La fotografia dell'host è quindi solo
una rete di sicurezza contro le minime differenze in virgola mobile fra chip diversi, e
viene applicata solo a turno concluso per non tagliare l'animazione.

Se il collegamento cade la partita si ferma con un avviso; la mossa dell'avversario che
arriva mentre l'animazione precedente è ancora in corso viene messa in coda, non persa.

**Permessi:** su Android 12+ servono *Dispositivi nelle vicinanze* (`BLUETOOTH_CONNECT`,
`BLUETOOTH_SCAN`), su Android 7-11 il permesso di posizione, richiesto solo per la ricerca
dei dispositivi come impone il sistema.

## 🤖 IA

Tre livelli (`Recluta`, `Veterano`, `Cyborg`). L'IA risolve la balistica in forma
chiusa per un ventaglio di tempi di volo, **simula ogni candidato contro il terreno**
per scartare i tiri che sbattono contro una collina o che sono autogol, sceglie il
bersaglio più conveniente (debole e vicino) e infine sporca la soluzione con un errore
proporzionale alla difficoltà. Compra anche le proprie armi fra un round e l'altro.

## 🏗️ Come compilare

Serve Android Studio (Giraffe o successivo) oppure una JDK 17 + Android SDK 34.

```bash
cd scorch-android
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew installDebug           # installa sul dispositivo collegato
```

Da Android Studio: `File → Open…` e seleziona la cartella `scorch-android`.

- `minSdk` 24 (Android 7.0) · `targetSdk`/`compileSdk` 34
- Orientamento orizzontale, schermo intero
- Dipendenze: solo `androidx.core-ktx` e `androidx.appcompat`

## 🧱 Architettura

```
app/src/main/java/com/changewave/scorch/
├── MenuActivity.kt          schermata di configurazione partita
├── GameActivity.kt          host del gioco, dialog di round/negozio/pausa
├── game/
│   ├── GameWorld.kt         macchina a stati dei turni, fisica, esplosioni, punteggi
│   ├── Terrain.kt           height-map distruttibile (crateri, frane, terra aggiunta)
│   ├── Tank.kt              carro: vita, scudo, carburante, inventario, disegno
│   ├── Projectile.kt        proiettili (volo / rotolamento / scavo) ed effetti
│   ├── Weapons.kt           catalogo armi ed equipaggiamento
│   ├── AiBrain.kt           risolutore balistico e mira dell'IA
│   ├── AiShopper.kt         acquisti automatici dell'IA
│   └── GameSettings.kt      impostazioni partita (Parcelable)
├── BluetoothLobbyActivity.kt  ricerca dispositivi, permessi e handshake
├── audio/
│   ├── SfxSynth.kt          sintesi dei suoni e scrittura dei WAV in cache
│   └── Sfx.kt               riproduzione con SoundPool, fischio in loop, mute
├── net/
│   ├── Protocol.kt          pacchetti binari (mossa, acquisti, snapshot)
│   ├── BluetoothLink.kt     socket RFCOMM, thread di lettura, riconnessione
│   └── NetSession.kt        tiene vivo il collegamento fra le schermate
└── ui/
    ├── GameView.kt          SurfaceView + game loop su thread dedicato
    ├── Hud.kt               controlli touch e pannelli disegnati su Canvas
    └── ShopDialog.kt        negozio fra un round e l'altro
```

Il mondo è largo **1600 unità** e viene scalato alla larghezza del display: la fisica
è quindi identica su qualsiasi dispositivo, mentre l'altezza in unità segue il formato
dello schermo. La HUD è invece disegnata in pixel schermo, sopra al mondo.

Il loop gira a ~60 fps su un thread dedicato; le collisioni usano sotto-passi
proporzionali alla velocità del proiettile per evitare il tunneling attraverso il
terreno.

## ✅ Stato della verifica

Il codice del motore è stato compilato (Kotlin 1.9.24, zero errori e zero warning) e
fatto girare **headless** con stub delle API grafiche:

- 6 partite complete IA-contro-IA (2/3/4 giocatori, tutte le difficoltà, con e senza
  vento): 12 round, 237 turni, nessun blocco, nessun NaN, 67% di tiri a segno
- tutte e 9 le armi sparate e verificate su terreno e carri (esplosione, frammenti,
  divisione MIRV, rotolamento, scavo, deposito di terra)
- uscita dai crateri su terreno piano, con il costo in carburante misurato
- correzioni misurate una per una: il roller su un pendio atterra a 773 e **torna indietro**
  fino a 317; in piano rotola fino al carro e scoppia a 18 unità da lui (29 di danno) invece
  di fermarsi a mezza strada; il digger scava 43-44 unità sul limite di 45 restando entro
  6 unità dal punto d'ingresso (buca larga 128 e profonda 113, invece di 184 e 210) su tre
  angoli di arrivo diversi; le armi risultano ridotte con la stessa scala (raggi
  0,69-0,70 · danni 0,60-0,61); il cartello del turno compare 12 volte in un minuto di
  partita e resta visibile al massimo 0,92 s, e dopo un riallineamento fra dispositivi
  viene azzerato (prima poteva restare impresso sullo schermo)
- **audio**: i 10 suoni vengono generati e misurati (durata, picco, assenza di scatti ai
  bordi, WAV valido, anello del fischio che si richiude con salto zero) e se ne verifica il
  contenuto in frequenza: il fischio a ~1.0 kHz, il boato della nuke a ~220 Hz e più grave
  di quello del missile (~870 Hz), il cannone che scende da 218 a 111 Hz. La misura ha
  scovato un errore vero: il fischio era stato generato a 44 Hz invece di 970
- **partita Bluetooth**: due istanze del gioco che comunicano solo con i pacchetti veri
  del protocollo giocano 176 turni e 9 round restando identiche (terreno, vita, carburante,
  inventari). La prova è stata ripetuta disattivando le fotografie di sincronizzazione: con
  **zero** correzioni scambiate i due mondi restano comunque allineati, quindi la
  determinicità regge da sola

L'APK di debug viene costruito automaticamente da GitHub Actions
(`.github/workflows/scorchwave-android.yml`) a ogni push che tocca `scorch-android/`:
build Gradle + lint su `ubuntu-latest`. APK di ~2,9 MB.

**Download diretto (sempre l'ultima build):**

```
https://github.com/mvalmori74/ChangeWaveMv/releases/download/scorchwave-latest/ScorchWave-debug.apk
```

Ogni build aggiorna la release `scorchwave-latest`, quindi il link non cambia mai.
Per una release stabile con un tag proprio: *Actions → Build ScorchWave APK → Run
workflow*, indicando il tag (es. `scorchwave-v1.0`). L'APK è di debug, non firmato per
il Play Store: sul telefono va consentita l'installazione da origini sconosciute.
