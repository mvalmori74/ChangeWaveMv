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

## Quale servizio di tunnel — e cosa costa davvero

*Aggiunto il 26/09/2026. La stesura originale sceglieva "un tunnel in uscita" senza
dire quale, e il compose intanto usava Cloudflare: una decisione presa di fatto e mai
scritta. Peggio, nascondeva un requisito con un costo.*

### Cloudflare Tunnel — scelto

Dal luglio 2026 il servizio è gratuito senza limiti di banda né di numero di tunnel.
**Ma richiede un dominio proprio**, con i nameserver gestiti da Cloudflare: il
servizio non fornisce sottodomini.

**Costo reale: ~10 €/anno di dominio** in generale — ma **nel nostro caso zero**:
l'utente possiede già un secondo dominio con il solo redirect, senza posta, che è il
candidato ideale. §13-D3 resta rispettato alla lettera, nessun costo ricorrente nuovo.
*(Risolto il 26/09/2026; la versione precedente di questa sezione segnalava la
deviazione come aperta.)*

**Perché il secondo dominio e non quello della posta**: sul piano gratuito Cloudflare
diventa autoritativo per l'intera zona DNS, quindi anche MX, SPF, DKIM e DMARC
passerebbero da lì. La configurazione parziale, che lascerebbe il DNS all'attuale
gestore, è riservata al piano Business a pagamento. Spostare il dominio della posta è
fattibile con attenzione, ma è un rischio che non serve prendere quando esiste un
dominio senza nulla da perdere. Il redirect che si interrompe si ricrea con una regola
gratuita.

### Tailscale — valutato

Gratuito fino a **6 utenti** con dispositivi illimitati (raddoppiato da 3 ad aprile
2026). Il tavolo di riferimento è esattamente 6: ci sta, **al limite esatto**. Il
settimo giocatore farebbe saltare il piano.

### ZeroTier — valutato

Gratuito fino a **10 dispositivi** per rete (ridotto da 25 nel 2024). Sei telefoni più
il server fanno sette: ci sta con margine.

### Perché Cloudflare nonostante il costo

Tailscale e ZeroTier sono gratuiti ma **spostano il costo sui giocatori**: ognuno deve
installare una rete privata virtuale, crearsi un account e tenerla attiva. Per un'app
che si usa due sere a settimana, quell'attrito è ciò che fa smettere le persone. Il
proprietario del progetto lo sopporterebbe; l'amico che voleva solo tirare un dado no.

Dieci euro l'anno sono il costo più basso di tutto il progetto — meno di quanto
consuma il PC acceso in un mese (§4-bis).

### Prima di spendere: i quick tunnel

Cloudflare offre anche tunnel estemporanei senza account né dominio, con un indirizzo
casuale generato al volo. **Inutilizzabili a regime**, perché l'indirizzo cambia a
ogni riavvio mentre l'APK lo porta compilato dentro.

Servono però a una cosa precisa: **verificare che il meccanismo funzioni su questa
linea prima di comprare il dominio**. Se il tunnel non passasse, si è risparmiato il
dominio; se passa, si compra sapendo.

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
