# Ombra Parking per iPhone

Versione iOS dell'app, costruita sullo **stesso core Kotlin** dell'app Android: posizione del
sole, geometria delle ombre, previsione oraria, proiezione in realtà aumentata e lettura dei
dati OpenStreetMap sono letteralmente lo stesso codice, quello coperto dai 69 test di
`:core:jvmTest`. Qui sopra ci sta solo l'interfaccia SwiftUI e ciò che è davvero specifico
della piattaforma: fotocamera, sensori, mappa, notifiche.

## ⚠️ Stato: mai compilato

Questo codice **non è mai stato compilato**. È stato scritto senza accesso a un Mac, e né
Xcode né Kotlin/Native per iOS funzionano altrove. Il core condiviso è verificato dai test;
il livello Swift no. Aspettati di dover correggere qualche errore al primo build — soprattutto
nei nomi dei tipi Kotlin visti da Swift, che il compilatore genera e che qui sono stati
dedotti dalle convenzioni.

## Cosa serve

- un Mac con **Xcode 15+** (iOS 17 è il target minimo);
- **JDK 17+**, per compilare il core Kotlin;
- **XcodeGen**: `brew install xcodegen`.

## Compilare

```bash
cd ios
xcodegen generate          # crea OmbraParking.xcodeproj da project.yml
open OmbraParking.xcodeproj
```

Poi in Xcode: seleziona il tuo iPhone, imposta la tua squadra di sviluppo in *Signing &
Capabilities* e premi Run. Con un Apple ID gratuito l'app resta installata **7 giorni**, poi
va reinstallata; con un account sviluppatore a pagamento non scade.

Il progetto compila da sé il framework Kotlin: una fase di build lancia
`./gradlew :core:embedAndSignAppleFrameworkForXcode`, che produce `OmbraCore.framework` per
l'architettura in uso. La prima volta ci mette qualche minuto.

### Perché XcodeGen e non un progetto pronto

Un `.xcodeproj` è un elenco di identificatori generati, lungo migliaia di righe, che non si
può scrivere a mano in modo affidabile senza un Mac su cui aprirlo: un errore lì dentro si
manifesta come un progetto che non si apre affatto. `project.yml` invece è leggibile,
modificabile e genera un progetto corretto con un comando.

## Differenze rispetto ad Android

Non sono scelte estetiche: sono punti in cui la piattaforma offre qualcosa di meglio o di
diverso.

- **Notifica del sole in arrivo**: su iOS è una notifica locale programmata, consegnata al
  sistema. Scatta all'orario previsto senza le imprecisioni che su Android impone la modalità
  *Doze*.
- **Nord geografico**: CoreMotion offre il riferimento `xTrueNorthZVertical`, già allineato al
  nord vero. Su Android la declinazione magnetica va corretta a mano.
- **Mappa**: MapKit al posto di osmdroid. Le sagome d'ombra sono poligoni separati, quindi
  dove si sovrappongono il colore si somma leggermente; in AR il riempimento è unico come su
  Android.

## Da verificare per primo, su un telefono vero

1. **L'allineamento in AR.** La conversione dell'assetto di CoreMotion nella matrice attesa dal
   core è il punto più a rischio: se le ombre appaiono specchiate o ruotate di 90°, la causa è
   quasi certamente lì (`MotionTracker.enuMatrix`, in `Views/ARScreen.swift`), ed è una
   correzione di poche righe.
2. **I nomi dei tipi Kotlin in Swift.** `KotlinBridge.swift` assume che `kotlinx-datetime`
   arrivi come `Kotlinx_datetimeInstant`: se il compilatore genera nomi diversi, gli errori
   saranno tutti concentrati in quel file.
3. **La resa della mappa** con molte sagome: il limite attuale è 500 poligoni.
