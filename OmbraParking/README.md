# Ombra Parking

App Android che, a partire dalla tua posizione, calcola **dove ci sarà ombra a una certa ora**
e te lo mostra sia dall'alto su mappa sia in **realtà aumentata**, inquadrando la strada con
la fotocamera. Serve a rispondere a una domanda molto concreta: *dove parcheggio l'auto se
torno alle 15 e non voglio trovarla bollente?*

## Come funziona

1. **Posizione** — il telefono dà latitudine e longitudine (Fused Location Provider).
2. **Ostacoli** — da OpenStreetMap, via Overpass API, l'app scarica edifici, alberi e muri
   entro 300 m. L'altezza arriva dal tag `height`, altrimenti dal numero di piani
   (`building:levels` × 3,1 m), altrimenti da una stima prudente basata sul tipo di edificio.
3. **Sole** — la posizione del sole (azimut ed elevazione) è calcolata con l'algoritmo
   NOAA Solar Calculator per l'istante scelto con lo slider.
4. **Ombre** — ogni ostacolo è un prisma: la sua ombra è la somma di Minkowski dell'impronta
   con il segmento che va dall'ombra della base a quella della sommità. Per sapere se un punto
   preciso è in ombra si lancia invece un raggio verso il sole e si guarda se incontra un
   volume: è esatto e costa poco.
5. **Previsione** — campionando la giornata ogni 10 minuti esce la fascia oraria "sole/ombra"
   che vedi nella barra colorata: *ombra fino alle 17:20, ancora 2 h 10 min*.

## Guardare un'altra zona

Non serve essere sul posto. Cerchi una via o una piazza nella barra in alto e l'app scarica
edifici e alberi di *quella* zona, ci sposta la mappa e ricalcola tutto lì: è il modo per
decidere prima di partire dove conviene parcheggiare quando arrivi. Lo stesso succede
toccando un punto lontano sulla mappa — se cade fuori dall'area già scaricata, i dati vengono
presi intorno a quel punto.

Mentre esplori, il GPS aggiorna solo il puntino blu: i dati non seguono i tuoi passi, o la
zona che hai appena scelto scapperebbe via. Una riga in alto ricorda dove stai guardando e
riporta alla tua posizione con un tocco.

La vista in realtà aumentata, in quel caso, dice che non ha niente da mostrare invece di
sovrapporre ombre finte: può parlare solo di dove ti trovi davvero.

## Il posto auto

Quando parcheggi salvi il punto con un tocco: l'app lo ricorda fra un avvio e l'altro e lo
segna con una **P** verde sulla mappa e con un segnaposto in AR, con la distanza. La riga in
basso dice a che distanza e in che direzione è l'auto, quando l'hai lasciata e — la cosa che
serve davvero — **quando il sole arriverà a colpirla**: *"All'ombra, il sole arriva alle 16:20
(fra 1 h 10 min)"*.

Quello stato è calcolato sull'ora vera, non su quella dello slider: muovere la barra oraria
esplora gli scenari, ma l'auto resta parcheggiata nel mondo reale. Se ti allontani oltre la
zona di cui sono stati scaricati gli edifici, l'app dice che non può sapere se è al sole
invece di tirare a indovinare.

### La notifica

Quando l'auto è all'ombra e il sole arriverà più tardi, l'app fissa un avviso per **un
quarto d'ora prima** e te lo ricorda nella riga in basso. La notifica arriva anche ad app
chiusa: il calcolo è già stato fatto al momento del parcheggio, quindi il lavoro
programmato non ha bisogno né di rete né di GPS e prima di suonare controlla che l'auto sia
ancora dov'era — se l'hai spostata o hai cancellato il punto, tace.

Il permesso delle notifiche viene chiesto quando salvi il primo parcheggio, non all'avvio:
prima non ci sarebbe niente da notificare. Se lo neghi, il resto dell'app funziona uguale.

Due limiti dichiarati: l'avviso usa WorkManager e non una sveglia esatta (che Android
concede col contagocce), quindi in *Doze* può arrivare qualche minuto tardi — il preavviso
di quindici minuti serve anche ad assorbire quello scarto. E l'avviso vale per l'arrivo di
sole della giornata in corso: per i giorni successivi va riaperta l'app.

## Le due viste

**Mappa** (OpenStreetMap via osmdroid): vista dall'alto con le ombre dell'ora scelta. Tocca un
punto qualsiasi per spostarci l'analisi, trascina la barra oraria per vedere le ombre muoversi.

**Realtà aumentata**: la fotocamera inquadra la strada e l'app ci appoggia sopra le zone in
ombra, il sole e la sua traiettoria del giorno. Il mirino al centro dice cosa sarà quel pezzo
di asfalto all'ora scelta.

Non usa ARCore: per ancorare dei poligoni al suolo bastano il sensore di rotazione e il campo
visivo della fotocamera, e così funziona anche sui telefoni non certificati ARCore. Due
correzioni fanno la differenza fra un overlay allineato e uno storto:

- la **declinazione magnetica** (i sensori puntano al nord magnetico, il sole si calcola sul
  nord geografico: in Italia sono 3-4°, altrove anche 15);
- il **campo visivo effettivo**, non quello nominale del sensore: `PreviewView` in
  `FILL_CENTER` ritaglia l'immagine, e ignorarlo allarga le ombre disegnate.

## Struttura

```
OmbraParking/
├── core/                        # Kotlin multipiattaforma: JVM (Android) + iOS → testabile
│   ├── geo/Geometry.kt          # Vec2/Vec3, piano tangente locale, poligoni, raycast
│   ├── sun/SolarPosition.kt     # algoritmo NOAA
│   ├── sun/SunTimes.kt          # alba, tramonto, mezzogiorno solare
│   ├── shadow/ShadowEngine.kt   # ombra di un punto + sagome da disegnare
│   ├── shadow/ShadeTimeline.kt  # previsione oraria su un punto
│   ├── ar/CameraProjector.kt    # proiezione prospettica con clipping
│   ├── alarm/SunWarning.kt      # quando far scattare l'avviso di sole in arrivo
│   ├── geo/DataCoverage.kt      # fin dove ci si può fidare dei dati scaricati
│   └── osm/                     # Overpass, ricerca dei luoghi, stima delle altezze
├── app/                         # Android: Compose, CameraX, osmdroid, sensori, WorkManager
│   ├── data/                    # posizione, orientamento, Overpass, luoghi, posto auto, notifica
│   └── ui/map, ui/ar, ui/components
└── ios/                         # iPhone: SwiftUI, AVFoundation, CoreMotion, MapKit
    └── vedi ios/README.md       # solo sul ramo claude/ios-ombra-parking
```

Tutta la matematica sta in `core`, un modulo **Kotlin Multiplatform** senza dipendenze da
Android: si esegue e si testa su una macchina qualsiasi, senza emulatori, ed è dove vive la
logica che conta. Gli stessi sorgenti compilano per la JVM (che serve all'app Android) e per
iOS: quando arriverà l'app per iPhone userà esattamente questo codice, già verificato, invece
di una seconda traduzione da tenere allineata.

## Compilare

Serve Android Studio (Ladybug o più recente) oppure JDK 17+ e l'Android SDK con API 35.

```bash
./gradlew :core:jvmTest     # test della logica (69 test, nessun emulatore)
./gradlew :app:assembleDebug
```

Se compili da riga di comando crea `local.properties` con il percorso dell'SDK:

```
sdk.dir=/percorso/di/Android/Sdk
```

## Test

Il modulo `core` è coperto da test che verificano le proprietà fisiche invece dei numeri a
memoria: al solstizio il sole a mezzogiorno sta a `90° - latitudine + 23,44°`, all'equinozio
sorge a est, nell'emisfero sud a mezzogiorno sta a nord, alba e tramonto a Roma cadono negli
orari pubblicati. Il test più utile confronta le due strade con cui l'app calcola l'ombra —
il raycast puntuale e la sagoma disegnata — su una griglia di oltre mille punti: se divergono,
significa che mappa e AR starebbero raccontando cose diverse.

## Limiti noti

- **I dati valgono quanto OpenStreetMap**: dove gli edifici non sono mappati non c'è ombra da
  calcolare. L'altezza è spesso stimata dai piani, quindi l'ombra può sbagliare di qualche metro.
- **Il terreno è considerato piano.** In una strada in pendenza le ombre si allungano o
  accorciano rispetto a quanto disegnato.
- **Niente nuvole**: l'app dice dove *non arriva il sole*, non se ci sarà il sole.
- L'orientamento in AR dipende dalla bussola: se il telefono segnala scarsa precisione,
  l'app lo dice e conviene calibrarla muovendolo a forma di otto.
- Overpass è un servizio pubblico gratuito: l'app fa una richiesta per zona e la tiene in
  cache, ma con la rete lenta il primo caricamento può richiedere qualche secondo.
- L'avviso di sole in arrivo passa da WorkManager: in *Doze* può scattare qualche minuto
  dopo l'orario previsto, e copre la giornata in corso (per quelle dopo, riapri l'app).

## Possibili sviluppi

- Ombra proiettata sui parcheggi mappati in OSM, con classifica dei posti più freschi.
- Modello del terreno (DTM) per le strade in pendenza.
- Cache su disco degli ostacoli per l'uso offline.
