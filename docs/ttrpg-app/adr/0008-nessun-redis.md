# ADR-008 — Nessun Redis nella v1.0

- **Stato**: accettato
- **Data**: 2026-09-20
- **Contesto**: l'architettura di riferimento per una chat realtime prevede
  tipicamente Redis per presenza, limitazione della frequenza e distribuzione degli
  eventi. Qui il sistema gira sul PC di casa di una persona.

## Decisione

**Niente Redis.** Presenza, code e distribuzione degli eventi si appoggiano a Postgres
e alla memoria del processo.

## Motivazione

Il dimensionamento reale è **un tavolo, sei persone, due sere a settimana**. A questa
scala Postgres regge presenza e notifiche senza sforzo, e un processo singolo può
tenere in memoria lo stato delle connessioni aperte.

Il criterio decisivo non è la prestazione ma la **manutenzione**: ogni componente in
più è un container che può non ripartire dopo un blackout, una versione da aggiornare,
un log che riempie il disco, una cosa in più da capire alle nove di sera quando il
tavolo aspetta. Su un server domestico senza amministratore, **un componente in meno
vale più di un'ottimizzazione teorica**.

## Conseguenze

- Lo stato delle connessioni vive nel processo: al riavvio del server i client si
  riconnettono e rifanno la sincronizzazione da `last_seq`. È già previsto da F2.
- Se un giorno servissero più processi in parallelo, questa decisione va rifatta. Non
  succederà a sei giocatori.
- La limitazione della frequenza delle richieste è per processo, non distribuita.
  Sufficiente qui, insufficiente in uno scenario pubblico: da riaprire insieme alla
  moderazione, se mai si aprisse al pubblico.
