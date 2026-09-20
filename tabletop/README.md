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
cp .env.example .env     # compila: password, PERCORSO_BACKUP su un SECONDO disco
docker compose up -d
docker compose --profile tunnel up -d    # quando hai il token del tunnel
```

Stato: `http://localhost:8080/stato`

Il compose rifiuta di partire se manca la password o se `PERCORSO_BACKUP` non è
impostato. È voluto: un server senza backup non deve sembrare funzionante.

## Ripristino

```bash
cd infra && ./backup/ripristina.sh /percorso/backup/20260920-030000
```

**Da eseguire almeno una volta in Sprint 0**, su un'installazione vuota, annotando il
tempo nel runbook. Un backup mai ripristinato non è un backup.

## Due scelte che vale la pena conoscere

**L'ordinamento dei messaggi non usa il timestamp del client.** `packages/shared/ids.ts`
definisce `SeqCanale` come tipo distinto, assegnato solo dal server: orologi sfasati e
ritardi di rete non possono riordinare la cronologia. Il confronto è totale, quindi
due telefoni mostrano sempre lo stesso ordine.

**A disco pieno il tavolo continua a giocare.** `apps/server/src/disco.ts` rifiuta i
caricamenti e avvisa il GM, ma chat e dadi restano vivi. Con retention infinita lo
spazio è il vincolo da presidiare, e il modo sbagliato di presidiarlo è cadere.
