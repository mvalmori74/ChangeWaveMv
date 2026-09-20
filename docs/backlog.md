# Backlog

Cose deliberatamente **fuori** dalla v1.0. Ogni voce dice perché è fuori e cosa la
farebbe rientrare — altrimenti un backlog diventa un cimitero di buone intenzioni.

## Rinviate per scelta di prodotto

| Voce | Perché fuori | Rientra se |
|---|---|---|
| Traduzione multilingua della trascrizione | §13-D1: serve solo la trascrizione | il tavolo diventa multilingue. La pipeline di F3 è già a step espliciti, aggiungerne uno non è debito |
| Stanza vocale dal vivo | §13-D2: il paradigma è la messaggistica. Costo e complessità molto superiori | dopo la v1.0, se il gruppo la chiede davvero |
| Conversione vocale che conserva la recitazione | carico sul PC di casa, che deve già trascrivere e sintetizzare per sei persone | quando si saprà quanto margine ha quella macchina (dopo SPIKE-1/2) |
| Battle map tattica con token | non richiesta, ed è un prodotto a sé | mai, probabilmente: è un'altra app |
| Schede personaggio con automazione delle regole | vedi ADR-007, e sarebbe metà del lavoro totale | mai in questa forma |
| Client iOS | §13-D5: quota annuale del programma sviluppatori | se qualcuno del gruppo passa a iOS. L'interfaccia del modulo nativo è già platform-agnostic |
| Client web o desktop | non richiesto | — |

## Rinviate ma predisposte

| Voce | Stato | Nota |
|---|---|---|
| Moderazione UGC completa | schema dati **già predisposto** | obbligatoria prima di qualunque distribuzione pubblica (App Store 1.2). `messages` ha già `deleted_at`/`deleted_by`, la tabella `reports` è prevista anche se inutilizzata: si aggiunge senza migrazione distruttiva |
| Privacy policy pubblica, age gate, export self-service | non iniziati | necessari solo in caso di apertura al pubblico |

## Debito tecnico dichiarato

| Voce | Origine | Costo di rientro |
|---|---|---|
| Condizione di risoluzione per i tipi legacy di React Native | reperto R1 dello Sprint 0 | una riga in `tsconfig.json`, da **togliere consapevolmente** quando i tipi generati saranno corretti |
| CI ridotta, spostata a S1 | scelta di priorità in Sprint 0 | ~1 story point |
| Spike dadi 3D spostato a inizio S2 | idem | ~2 story point, già pianificati |
| Limitazione della frequenza delle richieste per processo e non distribuita | ADR-008 | irrilevante a sei giocatori, da riaprire solo con la moderazione |
