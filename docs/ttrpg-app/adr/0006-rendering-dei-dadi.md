# ADR-006 — Rendering dell'animazione dei dadi

- **Stato**: proposto, decide lo spike a inizio S2
- **Data**: 2026-09-20
- **Contesto**: F5 chiede un effetto grafico che accompagni il tiro, su telefoni
  Android dal 2023 la cui potenza grafica però **non è garantita dall'anno** (§4-ter).

## Il vincolo che viene prima di tutto

Il risultato del tiro è deciso dal server (F5, RNG verificabile). **L'animazione
converge su un risultato già noto, non lo produce.** Una simulazione fisica che
determini la faccia sarebbe non deterministica fra device e, soprattutto,
manipolabile dal client: distruggerebbe la proprietà che rende il tiro credibile.

Questo esclude in partenza qualunque soluzione in cui il numero esca dalla fisica.

## Opzioni

**A — Motore 3D su contesto nativo.** Dadi veri con materiali e ombre. Resa migliore,
costo in prestazioni e batteria, e una dipendenza grafica in più da mantenere.

**B — Sprite pre-renderizzati.** Sequenze di immagini generate in anticipo, una per
faccia finale. Leggerissimo e identico ovunque, ma ogni tipo di dado richiede il suo
corredo di immagini, e il peso dell'APK cresce.

**C — Ibrido a fasce.** 3D completo in fascia alta, 3D semplificato in fascia media,
sprite in fascia bassa, con la classificazione di §4-ter.

## Decisione proposta

**C**, con una precisazione che vale più della scelta: la fascia è già implementata e
testata in `packages/shared/src/fasce.ts`, e **una misura reale ha la precedenza sugli
indizi hardware**. L'utente può forzare la fascia in entrambe le direzioni: chi ha un
telefono potente e vuole risparmiare batteria in una sessione di quattro ore ha
ragione quanto chi vuole l'effetto pieno.

La preferenza di sistema per l'animazione ridotta è **una cosa diversa dalla fascia
bassa** e vince sempre: lì si salta per scelta dell'utente, qui per limiti del device.

## Conseguenze

- Va mantenuto anche il corredo di sprite, non solo il 3D: è lavoro in più, ed è il
  prezzo per non tagliare fuori nessuno del gruppo.
- Serve un budget di prestazioni fissato **sui telefoni veri del gruppo**, non su un
  modello ipotetico.
