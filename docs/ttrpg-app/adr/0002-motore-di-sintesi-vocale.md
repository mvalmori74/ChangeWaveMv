# ADR-002 — Motore di sintesi vocale e catena delle voci

- **Stato**: **aperto — non decidibile senza SPIKE-2**; una parte è però già decisa
- **Data**: 2026-09-20

## Già deciso, perché non dipende dall'ascolto

La catena di elaborazione applicata all'uscita — pitch shift e spostamento delle
formanti — usa **Signalsmith Stretch (MIT)** su Oboe. Vedi ADR-004 e
`docs/licenses.md`: la scelta è vincolata dalla licenza, non dal gusto.

## Aperto

**A — Sintesi neurale sul server di casa.** Qualità superiore, costo marginale nullo,
nessun dato che esce. Consuma CPU sulla stessa macchina che deve già trascrivere.
Attenzione alla licenza: il candidato principale incorpora un componente
verosimilmente GPL. Girando **solo sul server**, senza essere distribuito, gli
obblighi della GPL non scattano — ma va confermato che sia GPL e **non AGPL**, che
invece scatterebbe anche sul solo uso via rete (azione L1 in `docs/licenses.md`).

**B — Motore di sintesi di sistema Android.** Costo zero, offline, funziona a server
spento. Qualità inferiore ma intelligibile, e variabile fra produttori di telefoni.

**C — Ibrido: A principale, B come ripiego.** Proposta di partenza.

## Cosa deve dire SPIKE-2

Giudizio di ascolto alla cieca su intelligibilità e carattere, per ogni preset e per
ogni catena; tempo di sintesi sul PC reale; licenza delle **singole voci italiane**,
che è spesso diversa da quella del motore.

Un preset sotto 3 su 5 di intelligibilità va scartato o ridisegnato: una voce scenica
che non si capisce danneggia il gioco invece di arricchirlo.
