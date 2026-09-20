# ADR-001 — Motore di riconoscimento vocale

- **Stato**: **aperto — non decidibile senza SPIKE-1**
- **Data**: 2026-09-20

Questo ADR resta deliberatamente aperto. Decidere ora significherebbe scegliere sulla
base di impressioni, ed è esattamente ciò che lo Sprint 0 esiste per evitare.

## Opzioni sul tavolo

**A — Solo motore di sistema Android.** Costo zero, nessun server coinvolto, funziona
a server spento. **Non accetta un vocabolario personalizzato**: sui nomi propri
inventati è il caso peggiore, ed è il rischio n.1 del progetto.

**B — Solo riconoscimento sul server di casa.** Accetta il condizionamento con il
glossario di campagna, qualità superiore, audio che non lascia la rete domestica.
Non funziona a server spento e consuma la CPU del PC.

**C — Ibrido: B come percorso principale, A come ripiego.** Più codice, ma nessuna
delle due debolezze resta scoperta.

## Cosa deve dire SPIKE-1 prima che si possa decidere

1. WER complessivo e **WER sui soli nomi propri** per le quattro configurazioni.
2. Il delta fra "server con glossario" e "sistema Android": se è marginale, B e C non
   valgono la complessità che aggiungono.
3. Tempo di elaborazione sul PC reale, da cui il dimensionamento del modello.

Candidati verificati come licenza (`docs/licenses.md`): whisper.cpp (MIT),
faster-whisper (MIT), Vosk (Apache 2.0). Nessun vincolo di licenza discrimina fra
loro: deciderà la misura.

**Proposta di partenza, da confermare o smentire con i numeri**: C.
