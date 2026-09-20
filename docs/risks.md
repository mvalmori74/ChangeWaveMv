# Registro dei rischi

Aggiornato al 20/09/2026. Ordinato per conseguenza, non per probabilità: un rischio
poco probabile ma irreversibile pesa più di uno frequente e rimediabile.

| # | Rischio | Livello | Stato |
|---|---|---|---|
| R1 | Qualità della trascrizione sui nomi propri inventati | **ALTO** | aperto, misura SPIKE-1 |
| R2 | Perdita dei dati della campagna | **ALTO** | **accettato consapevolmente**, non mitigato |
| R3 | Raggiungibilità del server di casa | MEDIO | mitigato per progetto, verifica SPIKE-0 |
| R4 | Frammentazione Android su motori vocali di sistema | MEDIO | aperto |
| R5 | Capacità di calcolo del PC di casa | MEDIO | aperto, misura SPIKE-1/2 |
| R6 | Animazione 3D su telefoni deboli | MEDIO | mitigato per progetto |
| R7 | Autonomia batteria in sessioni lunghe | MEDIO | aperto |
| R8 | Librerie native e pagine di memoria da 16 KB | MEDIO | aperto, verifica SPIKE-4 |
| R9 | Licenze delle dipendenze | BASSO | **chiuso** il 20/09 |

---

## R1 — Trascrizione sui nomi propri inventati · ALTO

I nomi inventati sono il caso peggiore per qualunque riconoscitore, e sono l'unica
cosa che un giocatore **non può ricostruire dal contesto**. Se sbaglia "il" al posto
di "lo" nessuno se ne accorge; se sbaglia il nome del villaggio, la frase perde senso.

*Mitigazione*: il self-hosting ha restituito il glossario di campagna, che il motore
di sistema Android non permette. Editing della trascrizione sempre disponibile.

*Misura*: SPIKE-1, metrica dedicata "WER sui soli nomi propri".

*Se va male*: la funzione si ridefinisce come bozza da correggere, e si dice. Non si
nasconde dietro un WER complessivo lusinghiero.

## R2 — Perdita dei dati della campagna · ALTO

Server in casa, disco singolo, retention infinita, nessuna copia remota. È il rischio
con la conseguenza peggiore del progetto: a differenza di un bug, **non si ripara**.

*Mitigazione disponibile*: F11 e ADR-005. Backup su secondo disco, impronta di
verifica, esportazione della campagna in archivio leggibile anche senza l'app. Il
codice esiste ed è pronto.

*Stato al 20/09/2026*: **il backup è disattivato per decisione dell'utente.** Il
servizio è presente nel compose sotto il profilo `backup` e si accende con un
comando, senza modifiche. Finché resta spento, il disco del server è l'unica copia.

*Perché resta ALTO e non declassato*: il livello misura la conseguenza, non la
probabilità, e la conseguenza non è cambiata. Un rischio accettato resta un rischio;
la differenza è che ora è una scelta registrata invece di una svista.

*Riaprire quando*: arriva un secondo disco, oppure la prima campagna accumula
abbastanza sessioni da rendere la perdita dolorosa.

## R3 — Raggiungibilità del server di casa · MEDIO

Blackout, guasti di linea, PC spento, CGNAT dell'operatore. A server giù l'app non
funziona per nessuno.

*Mitigazione*: tunnel in uscita (ADR-003), riavvio automatico dei container e
ripartenza dopo mancanza di alimentazione, stato di connessione spiegato in modo
comprensibile. La chat resta scrivibile offline; **i dadi no, di proposito**.

*Declassato da ALTO*: il tunnel rende la questione CGNAT ininfluente.

## R4 — Frammentazione Android · MEDIO

Voci di sintesi e modello italiano offline dipendono da produttore e configurazione
del telefono, anche su Android recenti. Un preset che suona bene sul telefono di uno
può non esistere su quello di un altro.

*Mitigazione*: rilevamento delle capacità a runtime, degrado esplicito, matrice dei
device reali del gruppo. **Mai provare sull'emulatore**: ha un corredo vocale non
rappresentativo.

## R5 — Capacità di calcolo del PC di casa · MEDIO

Trascrizione e sintesi girano sulla stessa macchina, per sei persone, in
contemporanea.

*Mitigazione*: code con limite di concorrenza, dimensionamento del modello deciso
misurando (`tools/stt-bench/dimensiona.py`), stato del lavoro visibile in chat.

## R6 — Animazione 3D su telefoni deboli · MEDIO

L'anno del telefono fissa il sistema operativo, non la GPU.

*Mitigazione*: fasce di prestazioni già implementate e testate, con precedenza alla
misura reale sugli indizi hardware, e possibilità per l'utente di forzare la fascia.

## R7 — Autonomia batteria · MEDIO

Sessioni di tre o quattro ore con schermo acceso, audio e animazioni.

*Stato*: da misurare in SPIKE-3. Non rimandare alla beta.

## R8 — Pagine di memoria da 16 KB · MEDIO

Con un modulo audio nativo, un allineamento sbagliato si manifesta come **crash
all'avvio** sui device Android recenti, non come errore di compilazione.

*Verifica*: SPIKE-4, esito pass/fail su un device aggiornato.

## R9 — Licenze · BASSO · **chiuso il 20/09/2026**

La libreria di pitch shifting più diffusa è GPL v2 e avrebbe obbligato a distribuire
il sorgente dell'intera app. Adottata un'alternativa MIT. Vedi `docs/licenses.md`.

Restano cinque verifiche puntuali (azioni L1–L5), nessuna bloccante.
