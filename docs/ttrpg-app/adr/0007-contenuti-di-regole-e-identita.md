# ADR-007 — Contenuti di regole e identità del prodotto

- **Stato**: accettato
- **Data**: 2026-09-20
- **Contesto**: l'app serve a giocare a giochi di ruolo da tavolo. La tentazione di
  includere tabelle, incantesimi o mostri è forte e porta diritta a un problema legale.

## Decisione

**Nessun contenuto di regole nella v1.0.** L'app è uno strumento neutro: chat, dadi,
voce, allegati. Le regole restano nei manuali che il gruppo già possiede.

Nessun marchio, logotipo, nome di prodotto, testo o illustrazione di Wizards of the
Coast o Hasbro. Il nome del prodotto è provvisorio e la verifica di disponibilità del
marchio è rinviata a S7, prima di qualunque uso pubblico.

## Motivazione

1. **Non serve.** La notazione dei dadi non è brevettabile e non appartiene a nessuno:
   `4d6kh3` funziona senza citare alcun manuale. Il valore dell'app sta nella voce,
   nella chat e nei dadi, non in un archivio di incantesimi.
2. **Il materiale sotto licenza aperta copre meno di quanto sembri.** Esiste un
   sottoinsieme delle regole rilasciato con licenza libera, ma copre solo ciò che vi è
   effettivamente incluso — non i manuali completi, non i mostri iconici, non le
   ambientazioni — e impone una formula di attribuzione precisa, da verificare sul
   testo corrente della licenza.
3. **Il costo dell'errore è asimmetrico.** Non includere contenuti costa una funzione
   che nessuno ha chiesto; includerli male costa una diffida.

## Conseguenze

- Il glossario di campagna (F3) contiene i nomi **inventati dal GM**, non nomi presi
  da manuali: è una funzione dell'utente, non un archivio nostro.
- Se un domani si volesse includere materiale sotto licenza aperta, serve un nuovo ADR
  con il testo della licenza letto e l'attribuzione mostrata in app.
- `docs/licenses.md` traccia origine e licenza di ogni asset grafico e sonoro.
