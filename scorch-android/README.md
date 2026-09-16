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

| Comando | Azione |
|---|---|
| Trascinamento sul campo | Mira rapida: la direzione imposta l'angolo, la lunghezza del trascinamento la potenza |
| ◀ ▶ | Angolo fine (tenendo premuto accelera) |
| – + | Potenza fine |
| ◀◀ ▶▶ | Muove il carro: scala anche le pareti dei crateri, consumando carburante in proporzione alla salita |
| ARMA | Apre l'arsenale e cambia munizione |
| FUOCO | Spara |
| ☰ | Pausa / classifica / uscita |

Una traiettoria tratteggiata mostra l'inizio del tiro, e la scia del colpo precedente
resta visibile in trasparenza per correggere la mira.

## 💣 Arsenale

| Arma | Costo | Effetto |
|---|---|---|
| Missile Baby | gratis, illimitato | Colpo base, raggio 46 |
| Missile | 1.000 (x5) | Esplosione media, danno 45 |
| Baby Nuke | 3.500 (x3) | Raggio 112, danno 70 |
| Nuke | 9.000 (x2) | Raggio 185, danno 115 |
| Funky Bomb | 4.000 (x3) | All'impatto sparge 7 frammenti |
| MIRV | 6.000 (x2) | Si divide in 5 testate al culmine della parabola |
| Roller | 3.000 (x5) | Tocca terra e rotola in discesa fino all'avvallamento |
| Digger | 2.500 (x5) | Si conficca nel terreno scavando un tunnel, poi esplode |
| Palla di Terra | 2.000 (x5) | Non fa danno: costruisce una collina di protezione |

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
  vento): 12 round, 246 turni, nessun blocco, nessun NaN, 65% di tiri a segno
- tutte e 9 le armi sparate e verificate su terreno e carri (esplosione, frammenti,
  divisione MIRV, rotolamento, scavo, deposito di terra)

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
