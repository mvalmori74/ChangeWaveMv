# ADR-003 — Esposizione del server di casa

- **Stato**: proposto, in attesa di SPIKE-0
- **Data**: 2026-09-20
- **Contesto**: §13-D9. Il backend gira su un PC domestico e deve essere raggiungibile
  dai telefoni dei giocatori quando sono fuori casa, senza costi ricorrenti.

## Opzioni

**A — Inoltro delle porte sul router + DNS dinamico.** Semplice da capire. Richiede un
indirizzo IP pubblico: la linea è WindTre, e l'operatore usa normalmente CGNAT sulle
connessioni residenziali, il che renderebbe questa strada **impraticabile**. Espone
inoltre una porta verso la rete domestica, dove stanno anche gli altri computer di
casa, e richiede di gestire a mano il certificato TLS — che non è opzionale, perché
Android blocca il traffico in chiaro.

**B — Tunnel in uscita.** Il server apre una connessione verso un servizio esterno e
riceve da lì il traffico. Nessuna porta aperta sul router.

**C — Rete privata virtuale fra tutti i device.** Traffico cifrato e nessuna
esposizione pubblica, ma richiede un client attivo su ogni telefono e una gestione
degli accessi per ogni giocatore: attrito sproporzionato per un gruppo di amici che
vuole solo aprire un'app.

## Decisione

**B, tunnel in uscita.** Risolve tre problemi con una sola scelta: funziona sotto
CGNAT, fornisce un certificato TLS valido senza gestirne il rinnovo, e riduce
drasticamente la superficie di attacco verso la rete di casa.

Il DNS dinamico resta come indirizzo stabile e piano di riserva, non come meccanismo
principale.

## Conseguenze

- Dipendenza da un servizio di terzi, seppure nel suo piano gratuito: è un punto di
  fallimento fuori dal nostro controllo. Accettato, perché l'alternativa A potrebbe
  essere tecnicamente impossibile.
- **Da verificare in SPIKE-0**: i termini d'uso del piano gratuito riguardo al
  transito di file voluminosi. Riguarda i video allegati (F6). Se il vincolo esiste,
  va documentato e si valuta se servire i media per altra via.
- Il profilo `tunnel` nel compose è separato, così lo sviluppo in rete locale non
  dipende dal tunnel.

## Da rivalutare se

L'operatore fornisce un indirizzo IP pubblico, oppure il servizio di tunnel cambia le
condizioni del piano gratuito.
