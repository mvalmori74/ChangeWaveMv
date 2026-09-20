# tabletop — server e client per sessioni di gioco di ruolo in remoto

Stato: **Sprint 0 in corso.** Qui c'è l'impalcatura e gli strumenti di misura, non
ancora il prodotto. Specifica completa in `../docs/ttrpg-app/PROMPT_MASTER.md`,
piano dello sprint in `../docs/ttrpg-app/sprints/SPRINT-00.md`.

## Cosa c'è, oggi

```
tabletop/
├── packages/shared/       dominio puro: id, ordinamento dei messaggi, preset voce
├── packages/data-access/  contratto verso la persistenza (nessuna implementazione)
├── apps/server/           scheletro del server + sorveglianza del disco
├── infra/                 docker compose, tunnel, backup e ripristino
└── tools/stt-bench/       misura del WER e dimensionamento del modello vocale
```

Non c'è ancora: app Android, chat, dadi, media. Sono S0-02 e gli sprint successivi.

## Verifica

```bash
pnpm install
pnpm check          # lint + typecheck + test
```

```bash
cd tools/stt-bench
python3 test_wer.py && python3 test_dimensiona.py
```

## Avvio del server di casa

```bash
cd infra
cp .env.example .env     # basta compilare POSTGRES_PASSWORD
docker compose up -d
```

Stato: `http://localhost:8080/stato` — riporta anche che macchina ha rilevato e quale
modello di trascrizione propone.

Servizi opzionali, attivabili quando servono:

```bash
docker compose --profile tunnel up -d    # accesso da fuori casa (serve TOKEN_TUNNEL)
docker compose --profile backup up -d    # backup automatico (serve PERCORSO_BACKUP)
```

Il compose rifiuta di partire solo se manca la password del database.

## Backup — disattivato per scelta

Il servizio di backup e lo script di ripristino esistono e funzionano, ma sono
**spenti di default** per decisione presa il 20/09/2026. Si accendono con il profilo
`backup` senza modificare nulla.

Finché resta spento, il disco del server è l'unica copia dei dati: vedi R2 in
`docs/risks.md`.

```bash
cd infra && ./backup/ripristina.sh /percorso/backup/20260920-030000
```

## Due scelte che vale la pena conoscere

**L'ordinamento dei messaggi non usa il timestamp del client.** `packages/shared/ids.ts`
definisce `SeqCanale` come tipo distinto, assegnato solo dal server: orologi sfasati e
ritardi di rete non possono riordinare la cronologia. Il confronto è totale, quindi
due telefoni mostrano sempre lo stesso ordine.

**A disco pieno il tavolo continua a giocare.** `apps/server/src/disco.ts` rifiuta i
caricamenti e avvisa il GM, ma chat e dadi restano vivi. Con retention infinita lo
spazio è il vincolo da presidiare, e il modo sbagliato di presidiarlo è cadere.
