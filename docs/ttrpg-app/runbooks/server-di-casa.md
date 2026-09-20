# Runbook — server di casa

Destinatario: chi amministra il server, cioè il GM. Non richiede di essere sviluppatore.

## Prima accensione

1. Installa Docker sul PC.
2. `cd tabletop/infra && cp .env.example .env`
3. Compila `.env`:
   - `POSTGRES_PASSWORD`: lunga e casuale. Non riusare una password che hai altrove.
   - `PERCORSO_BACKUP`: un percorso su un **secondo disco fisico**. Una copia sullo
     stesso disco del server non protegge da niente.
   - `TOKEN_TUNNEL`: dal pannello del servizio di tunnel, quando ce l'hai.
4. `docker compose up -d`
5. Verifica: `curl http://localhost:8080/stato`

## Impostazione del BIOS da non dimenticare

Abilita **"riprendi all'arrivo dell'alimentazione"** (*Restore on AC Power Loss*,
*After Power Failure: Power On*, il nome cambia fra produttori). Senza, dopo il primo
temporale il server resta spento finché qualcuno non va a premere il pulsante — e te
ne accorgerai mezz'ora prima della sessione.

## Controlli periodici

| Quando | Cosa |
|---|---|
| Prima di ogni sessione | apri la pagina di stato dal telefono: server vivo, disco sotto l'80 %, ultimo backup recente |
| Una volta al mese | verifica che nella cartella dei backup ci siano file recenti e che crescano |
| Ogni sei mesi | **esegui un ripristino di prova** su un'installazione vuota |

## Aggiornamento

```bash
cd tabletop/infra
docker compose pull && docker compose up -d
```

Se qualcosa si rompe, torna indietro all'immagine precedente e riparti: i dati stanno
nei volumi, non nei container.

## Il server non risponde

In ordine, perché è l'ordine di probabilità:

1. Il PC è acceso? Docker è partito? `docker compose ps`
2. Il container del tunnel è vivo? `docker compose logs tunnel --tail 50`
3. Il disco è pieno? `docker compose exec api df -h /dati`
4. Il database è sano? `docker compose logs db --tail 50`

Finché il server è giù: la chat continua a funzionare offline sui telefoni e i
messaggi partiranno al ritorno. **I tiri di dado no**, ed è voluto: un tiro non
verificabile che sembra verificabile sarebbe peggio del non poter tirare.

## Se il disco si guasta

1. Sostituisci il disco, reinstalla Docker, riporta il repository.
2. `cp .env.example .env` e ricompila come alla prima accensione.
3. `./backup/ripristina.sh <cartella-del-backup-più-recente>`
4. Verifica la cronologia di una campagna dall'app prima di dichiarare risolto.

Il tempo che questa procedura richiede è quello che hai annotato durante la prova di
ripristino. Se non l'hai mai provata, non lo sai — ed è il momento peggiore per
scoprirlo.
