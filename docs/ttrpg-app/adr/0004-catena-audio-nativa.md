# ADR-004 — Architettura del modulo audio nativo

- **Stato**: accettato per la parte licenze, aperto sui parametri
- **Data**: 2026-09-20
- **Contesto**: F4 richiede di alterare timbro e altezza della voce sul telefono, a
  costo marginale nullo e funzionante anche a server spento.

## Decisione 1 — Libreria di elaborazione

**Signalsmith Stretch, licenza MIT.**

Alternative scartate, con il motivo:

- **Rubber Band**: licenza GPL v2, verificata alla fonte. Dentro un APK distribuito
  anche solo a sei amici farebbe scattare l'obbligo di fornire il sorgente dell'intera
  app. La licenza commerciale alternativa è a pagamento, fuori dal vincolo §13-D3.
- **SoundTouch**: licenza non verificabile al momento della decisione, e comunque
  copyleft debole, che nel collegamento statico dentro un APK porta obblighi non
  banali. Non serve, avendo un'alternativa MIT.

Vedi `docs/licenses.md`. Questo chiude in Sprint 0 un rischio che il master prompt
segnalava come bloccante se scoperto a S5.

## Decisione 2 — Livello di accesso all'audio

**Oboe (Apache 2.0)**, che seleziona da solo il percorso a minore latenza disponibile
sul device invece di costringerci a gestire le differenze fra versioni di Android.

## Decisione 3 — Quando si elabora

**Rendering differito, non in tempo reale.** Il messaggio vocale viene registrato per
intero e poi trasformato, invece di alterare la voce mentre si parla.

Perché: il prodotto è una chat con messaggi vocali (§13-D2), non una conversazione
dal vivo. Il differito toglie ogni vincolo di latenza stretta, consente di rifare il
rendering con un preset diverso senza registrare di nuovo, e permette di mettere in
cache il risultato. L'elaborazione in tempo reale servirebbe solo con la stanza vocale
dal vivo, che è in roadmap S8+.

## Decisione 4 — Confine dell'interfaccia

Il modulo espone un'interfaccia **senza riferimenti ad Android**, benché esista solo
l'implementazione Kotlin. Costa poco ora e tiene aperta la porta a iOS, che oggi è
fuori scope (§13-D5). Nessuna implementazione iOS va scritta.

## Aperto

I parametri dei preset in `packages/shared/src/voce.ts` sono un punto di partenza
dichiarato, non una verità: tutti hanno esito `da_misurare`, e un test impedisce di
dichiararli validi prima che SPIKE-2 li abbia fatti ascoltare a qualcuno.
