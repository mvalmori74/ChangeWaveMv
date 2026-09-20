# Licenze delle dipendenze non banali

Verifiche del **20 settembre 2026**, alle fonti (file di licenza nei repository
ufficiali), non a memoria. Da rifare a ogni cambio di versione maggiore.

Contesto d'uso: distribuzione **privata a invito via APK** a circa sei persone, con
server eseguito sul PC di casa del GM (§13-D4, D9).

---

## La distinzione che decide tutto: APK contro server

Le licenze copyleft classiche (GPL, LGPL) fanno scattare i loro obblighi **sulla
distribuzione del binario**. Nel nostro caso i due lati del sistema stanno in
posizioni opposte:

| Dove gira | Cosa succede | Conseguenza |
|---|---|---|
| **Dentro l'APK** installato sui telefoni dei giocatori | è **distribuzione** a tutti gli effetti, anche se a sei amici e senza denaro | una dipendenza GPL obbligherebbe a rendere disponibile il codice sorgente dell'intera app ai destinatari |
| **Sul server di casa**, in un container | **non è distribuzione**: il software gira su una macchina dell'utente, nessun binario cambia mani | gli obblighi della GPL non scattano |

Eccezione da sorvegliare: le licenze pensate per il software di rete (tipo AGPL)
fanno scattare gli obblighi **anche sul solo uso via rete**. Per ogni componente
lato server va quindi verificato non solo *se* è copyleft, ma *quale* copyleft.

**Regola operativa del progetto**: nell'APK entrano solo licenze permissive. Lato
server si può essere più elastici, dopo aver escluso l'AGPL.

---

## Catena audio nell'APK — la parte vincolata

| Componente | Licenza | Verificata | Esito |
|---|---|---|---|
| **Oboe** (audio a bassa latenza Android) | Apache 2.0 | sì, file LICENSE | **adottabile** |
| **Signalsmith Stretch** (pitch e time shift) | MIT | sì, file LICENSE.txt | **adottabile — candidato principale** |
| **Rubber Band Library** (pitch e time shift) | **GPL v2** | sì, file COPYING | **ESCLUSA** dall'APK: obbligherebbe a distribuire il sorgente dell'app a tutti i giocatori. Esiste una licenza commerciale a pagamento, fuori dal vincolo di costo zero |
| **SoundTouch** (pitch e time shift) | storicamente LGPL v2.1, con licenza commerciale alternativa | **no**: sorgente non raggiungibile al momento della verifica | **da verificare prima di adottarla.** Anche fosse LGPL, il collegamento statico dentro un APK porta obblighi non banali: preferire Signalsmith |

> **Conclusione sulla catena DSP**: si adotta **Signalsmith Stretch (MIT)**. È il
> rischio "libreria di pitch shifting con licenza incompatibile" che il master prompt
> segnalava come bloccante se scoperto a S5. **Chiuso ora, a costo zero.**

## Riconoscimento vocale sul server — nessun problema

| Componente | Licenza | Verificata | Esito |
|---|---|---|---|
| **whisper.cpp** | MIT | sì, file LICENSE | adottabile |
| **faster-whisper** | MIT | sì, file LICENSE | adottabile — usato già da `tools/stt-bench` |
| **Vosk** | Apache 2.0 | sì, file COPYING | adottabile, alternativa più leggera |

I **pesi dei modelli** hanno una licenza propria, distinta da quella del codice che li
esegue: vanno verificati separatamente quando il modello sarà scelto, dopo SPIKE-1.

## Sintesi vocale sul server — qui c'è un tema

| Componente | Licenza | Verificata | Esito |
|---|---|---|---|
| **Piper** | **quasi certamente GPL** | parzialmente | il repository ufficiale si chiama letteralmente `piper1-gpl` e il README dichiara di **incorporare espeak-ng** per la conversione in fonemi; espeak-ng è storicamente GPL v3. Non sono riuscito a leggere il file di licenza: **verificare prima di adottarlo** |

**Perché è probabilmente accettabile lo stesso**: Piper girerebbe **sul server di
casa**, dentro un container, e non verrebbe distribuito a nessuno. Per una licenza
GPL questo non fa scattare alcun obbligo. Due condizioni da confermare:

1. Che sia GPL e **non AGPL**: l'AGPL farebbe scattare gli obblighi anche sul solo
   uso via rete, ed è esattamente la nostra configurazione.
2. Che nessuna parte di Piper finisca **dentro l'APK**. Se un domani si volesse la
   sintesi anche sul telefono, la valutazione andrebbe rifatta da capo.

**Voci di sintesi**: ogni voce ha una licenza propria, spesso diversa da quella del
motore, e alcune voci di buona qualità hanno vincoli sull'uso. Da verificare
singolarmente quando le voci italiane saranno scelte, in SPIKE-2.

## Contenuti di gioco

Nessun contenuto di regole incluso (ADR-007). Nessun marchio, testo o illustrazione di
Wizards of the Coast o Hasbro. Se un domani si volesse includere materiale coperto da
licenza aperta, l'attribuzione richiesta va verificata sul testo della licenza
corrente e riportata in app.

---

## Azioni aperte

| # | Cosa | Quando |
|---|---|---|
| L1 | Leggere il file di licenza di Piper e stabilire se è GPL o AGPL | prima di SPIKE-2 |
| L2 | Verificare la licenza di SoundTouch, se mai la si considerasse | solo se Signalsmith non bastasse |
| L3 | Licenza dei pesi del modello di riconoscimento scelto | dopo SPIKE-1 |
| L4 | Licenza delle singole voci italiane di sintesi | durante SPIKE-2 |
| L5 | Licenza degli asset grafici e sonori (suono dei dadi, icone) | prima di S3 |

## Fonti consultate il 20/09/2026

- Oboe — https://github.com/google/oboe/blob/main/LICENSE
- Signalsmith Stretch — https://github.com/Signalsmith-Audio/signalsmith-stretch/blob/main/LICENSE.txt
- Rubber Band — https://github.com/breakfastquay/rubberband/blob/default/COPYING
- whisper.cpp — https://github.com/ggerganov/whisper.cpp/blob/master/LICENSE
- faster-whisper — https://github.com/SYSTRAN/faster-whisper/blob/master/LICENSE
- Vosk — https://github.com/alphacep/vosk-api/blob/master/COPYING
- Piper — https://github.com/OHF-Voice/piper1-gpl (nome del repository e README)
