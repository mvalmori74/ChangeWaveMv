# Runbook — costruire e installare l'APK

Destinatario: tu, sul PC di casa. Android soltanto (§13-D5).

**Avvertenza onesta**: questa procedura non l'ho potuta eseguire — non ho un telefono
né un SDK Android in questo ambiente. È scritta dal comportamento documentato degli
strumenti e dalla configurazione reale del progetto. La prima esecuzione troverà
probabilmente un paio di attriti: annotali e li sistemiamo, sono il genere di cosa che
si scopre solo facendola.

Tempo: **la prima volta 45-90 minuti**, quasi tutti di scaricamento. Le volte
successive, pochi minuti.

---

## 0. Perché non basta l'app Expo dal Play Store

Il progetto ha un **modulo audio nativo** (`modules/voce-dsp`, in Kotlin). Il client
Expo generico contiene solo i moduli standard, quindi non può eseguirlo: serve una
build nostra. È una conseguenza diretta di F4 e non si aggira.

---

## 1. Cosa installare sul PC (una volta sola)

### 1.1 Java 17

Serve a Gradle, il costruttore di Android. La versione **17**: con la 21 o la 25
Gradle può lamentarsi.

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

Verifica in un **nuovo** terminale (le variabili d'ambiente non si aggiornano in
quelli già aperti):

```powershell
java -version      # deve dire 17.x
```

### 1.2 Android SDK

Due strade. Se non hai già Android Studio, la seconda è più leggera di diversi GB.

**a) Android Studio** — comodo se ti servirà anche l'emulatore:
```powershell
winget install Google.AndroidStudio
```
Al primo avvio, in *More Actions → SDK Manager*, spunta:
- **Android SDK Platform 36**
- **Android SDK Build-Tools**
- **Android SDK Platform-Tools** (contiene `adb`, che serve per installare)

**b) Solo strumenti da riga di comando** — più snello:
scarica *command line tools only* da https://developer.android.com/studio,
scompatta in `C:\Android\cmdline-tools\latest`, poi:

```powershell
C:\Android\cmdline-tools\latest\bin\sdkmanager.bat "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

### 1.3 Variabili d'ambiente

```powershell
setx ANDROID_HOME "$env:LOCALAPPDATA\Android\Sdk"     # o C:\Android se hai scelto (b)
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-17..."
```

Apri un terminale nuovo e verifica:

```powershell
adb version
```

Se `adb` non si trova, aggiungi `%ANDROID_HOME%\platform-tools` al `Path`.

---

## 2. Preparare il telefono

1. **Impostazioni → Informazioni sul telefono**, tocca **sette volte** *Numero build*.
   Compare il messaggio "sei uno sviluppatore".
2. **Impostazioni → Sistema → Opzioni sviluppatore** → attiva **Debug USB**.
3. Collega il telefono al PC col cavo. Sul telefono compare una richiesta di
   autorizzazione: accetta, spuntando *consenti sempre*.

```powershell
adb devices      # deve elencare il telefono come "device", non "unauthorized"
```

> **Requisito**: Android 13 o superiore. Sotto quella versione l'installazione viene
> rifiutata dal sistema, per scelta nostra (§13-D6, `minSdk 33`).

---

## 3. L'indirizzo del server

### Telefono e PC devono stare sulla stessa rete?

**No, e a regime non devono**: i giocatori non saranno a casa tua. Ma per la *prima*
prova conviene, e vale la pena capire perché.

| Caso | Telefono | Funziona? |
|---|---|---|
| **A — stessa rete Wi-Fi**, indirizzo locale | in casa, sul Wi-Fi | **sì**, ed è quello che useremo adesso |
| **B — reti diverse, con tunnel** | ovunque: rete mobile, casa d'altri | **sì**, ed è la configurazione definitiva |
| **C — reti diverse, senza tunnel** | fuori casa | **no**, e con questo operatore nemmeno aprendo porte sul router |

Il caso **C** non funziona perché il telefono, fuori dalla rete di casa, non ha modo
di raggiungere il tuo indirizzo privato. Con un operatore che usa CGNAT — quasi
certamente il tuo — **non esiste proprio un indirizzo pubblico da raggiungere**:
l'inoltro delle porte non avrebbe nulla su cui agire. È il motivo per cui ADR-003 ha
scelto il tunnel prima ancora di sapere quale fosse il tuo operatore.

Il caso **B** funziona perché è il server ad aprire la connessione **verso l'esterno**:
si collega al servizio di tunnel, che gli assegna un indirizzo pubblico in HTTPS; il
telefono contatta quell'indirizzo e il traffico scende fino al tuo PC. Nessuna porta
aperta sul router.

**Perché cominciamo dal caso A**: per isolare le variabili. Se la prima prova non
funziona, il problema è nell'app o nel server — non nel tunnel, non nel certificato,
non nel servizio esterno. Una cosa alla volta. Il caso A è un ponteggio per il
collaudo, non una configurazione d'uso.

Quando passerai al caso B, **anche il tuo telefono in casa passerà dal tunnel**. Il
traffico esce e rientra, costa qualche millisecondo in più, e si guadagna la cosa che
conta: **una sola build per tutti**, tu e i giocatori, invece di due configurazioni da
tenere allineate.

### Per questa prima prova (caso A)

Sul PC dove gira il server:

```powershell
ipconfig      # cerca "Indirizzo IPv4" della tua scheda di rete: es. 192.168.1.50
```

Verifica che il server risponda **dal telefono**: collega il telefono al Wi-Fi di
casa, apri il browser e vai a `http://192.168.1.50:8080/salute`.
Deve rispondere `{"stato":"vivo",...}`.

Se non risponde, prima di costruire l'app: il firewall di Windows blocca quasi
sempre le connessioni in ingresso di un programma nuovo. Consenti la porta 8080 in
rete **privata**.

> Il file `app.config.ts` abilita da solo il traffico non cifrato quando l'indirizzo
> comincia per `http://`, e lo spegne quando è `https://`. Android blocca il traffico
> in chiaro dalla versione 9: senza questa eccezione l'app in rete locale non
> parlerebbe con nulla. Nella build definitiva, che passa dal tunnel, l'eccezione si
> spegne da sé — non c'è un interruttore da ricordarsi.

---

## 4. La chiave di firma (una volta sola, e poi custodiscila)

Un APK non firmato non si installa. La chiave serve anche per **aggiornare l'app
senza disinstallarla**: se la perdi, i telefoni rifiuteranno ogni aggiornamento
futuro e l'unica via sarà disinstallare e reinstallare, perdendo i dati locali.

```powershell
cd tabletop\apps\mobile
keytool -genkeypair -v -keystore tavolo.keystore -alias tavolo `
        -keyalg RSA -keysize 2048 -validity 10000
```

Ti chiede una password: **annotala dove annoti le cose importanti**, insieme al file
`tavolo.keystore`. Due copie, in due posti diversi.

> `tavolo.keystore` non va nel repository: è già escluso.

---

## 5. Costruire

```powershell
cd tabletop
pnpm install

cd apps\mobile
$env:URL_SERVER = "http://192.168.1.50:8080"     # il TUO indirizzo, dal punto 3
npx expo prebuild --platform android --clean
```

`prebuild` genera la cartella `android/`. Ci mette qualche minuto la prima volta.

Poi configura la firma. Apri `android\gradle.properties` e aggiungi in fondo:

```properties
TAVOLO_STORE_FILE=../../tavolo.keystore
TAVOLO_STORE_PASSWORD=la-password-che-hai-scelto
TAVOLO_KEY_ALIAS=tavolo
TAVOLO_KEY_PASSWORD=la-password-che-hai-scelto
```

In `android\app\build.gradle`, dentro il blocco `android { ... }`, sostituisci il
blocco `signingConfigs` esistente aggiungendo la configurazione `release`:

```gradle
signingConfigs {
    debug { /* lascia com'è */ }
    release {
        storeFile file(TAVOLO_STORE_FILE)
        storePassword TAVOLO_STORE_PASSWORD
        keyAlias TAVOLO_KEY_ALIAS
        keyPassword TAVOLO_KEY_PASSWORD
    }
}
buildTypes {
    release {
        signingConfig signingConfigs.release      // <- di norma punta a .debug
        // il resto lascialo com'è
    }
}
```

> **Attenzione**: `prebuild --clean` **rigenera** la cartella `android/` e cancella
> queste modifiche. Dopo la prima volta, usa `prebuild` senza `--clean`, oppure
> ri-applica questo passo. È il motivo per cui questo runbook esiste.

Infine:

```powershell
cd android
.\gradlew assembleRelease
```

La prima volta scarica Gradle e le dipendenze Android: **parecchi minuti e qualche
GB**. Alla fine trovi l'APK in:

```
tabletop\apps\mobile\android\app\build\outputs\apk\release\app-release.apk
```

---

## 6. Installare

Col telefono collegato:

```powershell
adb install -r app-release.apk
```

`-r` significa *sostituisci se già presente*, ed è ciò che userai per gli
aggiornamenti.

**Per gli altri giocatori**, che non collegheranno il telefono al tuo PC: mandagli il
file (WhatsApp, chiavetta, cartella condivisa). Al primo tentativo Android chiederà
di consentire l'installazione da quell'app di provenienza — è normale per le app fuori
dal Play Store, e va concesso una volta sola.

---

## 7. Primo accesso

1. Avvia il server sul PC, se non gira già:
   ```powershell
   cd tabletop\infra
   docker compose up -d
   docker compose logs api
   ```
2. Nei log cerca il riquadro **PRIMO AVVIO** con il codice di invito. Compare solo
   su database vuoto.
3. Apri l'app sul telefono. Nella schermata di stato devi vedere
   **"Server del tavolo raggiungibile"** e la fascia di prestazioni assegnata al
   telefono.
4. Il codice di invito è monouso: il primo che lo usa diventa il GM.

---

## 8. Cosa guardare in questa prima prova

È il collaudo di S0-02, S0-03 e SPIKE-4 insieme. Annota:

- [ ] L'app si avvia senza chiudersi da sola (verifica implicita
      dell'allineamento a 16 KB delle librerie native — se fosse sbagliato,
      **si chiuderebbe subito all'avvio**)
- [ ] La schermata dice "raggiungibile", non "non risponde"
- [ ] Che **fascia** assegna al telefono: alta, media o bassa
- [ ] Modello del telefono e versione di Android, per `docs/devices.md`
- [ ] Quanto è durata la build, per sapere cosa aspettarsi la prossima volta

Mandami questi dati e chiudo tre story dello Sprint 0.

---

## 8-bis. Passare al tunnel (quando la prima prova è andata bene)

Solo dopo che il caso A funziona. Due passi:

1. Sul PC, con il token del servizio di tunnel in `.env`:
   ```powershell
   cd tabletop\infra
   docker compose --profile tunnel up -d
   docker compose logs tunnel      # deve mostrare la connessione stabilita
   ```
2. Ricostruisci l'app con l'indirizzo pubblico:
   ```powershell
   cd tabletop\apps\mobile
   $env:URL_SERVER = "https://tavolo.tuodominio"
   npx expo prebuild --platform android          # senza --clean: vedi punto 5
   cd android; .\gradlew assembleRelease
   ```

Il traffico in chiaro si spegne da solo, perché l'indirizzo comincia per `https://`.

Questa è la build da distribuire ai giocatori. Provala **prima** dalla rete mobile del
tuo telefono, con il Wi-Fi spento: è l'unico modo di verificare davvero che il tunnel
funzioni, perché restando sul Wi-Fi di casa non sapresti se stai passando da lì o
dalla rete locale.

## 9. Se qualcosa non va

| Sintomo | Causa quasi certa |
|---|---|
| `adb devices` dice `unauthorized` | la richiesta sul telefono non è stata accettata: scollega, ricollega, guarda lo schermo del telefono |
| `SDK location not found` | `ANDROID_HOME` non impostata, o terminale aperto prima di impostarla |
| Gradle si lamenta della versione di Java | hai la 21 o la 25 al posto della 17: controlla `JAVA_HOME` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | il telefono ha già una versione firmata con un'altra chiave: disinstalla prima |
| `INSTALL_FAILED_OLDER_SDK` | il telefono ha Android 12 o precedente: fuori baseline |
| L'app dice "il server non risponde" (caso A) | firewall di Windows, oppure `URL_SERVER` sbagliato al momento della build, oppure **telefono su rete mobile invece che sul Wi-Fi di casa**: nel caso A devono stare sulla stessa rete |
| L'app dice "il server non risponde" (caso B) | container del tunnel non avviato (`docker compose --profile tunnel up -d`), oppure token mancante in `.env` |
| L'app si chiude appena si apre | probabile allineamento a 16 KB: mandami `adb logcat -d > log.txt` |

Per qualunque cosa non elencata: `adb logcat -d > log.txt` con il telefono collegato,
subito dopo il problema, e mandami il file.

---

## 10. Alternativa: build nella nuvola

Esiste il servizio di build di Expo (EAS), che evita di installare SDK e Java: si
lancia `eas build -p android --profile preview` e si scarica l'APK.

Non l'ho messo come strada principale per due motivi coerenti con le decisioni già
prese: richiede un account su un servizio esterno, e il piano gratuito ha un numero
limitato di build al mese — che è poco per la fase in cui si itera spesso. La build
locale, dopo l'installazione iniziale, è illimitata e non dipende da nessuno.

Se l'installazione degli strumenti si rivelasse più faticosa del previsto, è un
ripiego ragionevole per sbloccare la prima prova.
