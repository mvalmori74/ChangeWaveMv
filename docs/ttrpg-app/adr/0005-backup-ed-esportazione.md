# ADR-005 — Backup ed esportazione della campagna

- **Stato**: strategia accettata, **attuazione rinviata per decisione dell'utente (20/09/2026)**
- **Data**: 2026-09-20
- **Contesto**: §13-D9 e D10. Server sul PC di casa, retention infinita, cancellazione
  manuale. Il disco di quel PC è **l'unica copia** di anni di gioco.

## Decisione

**Backup automatico attivo di default**, non opzionale e non affidato alla buona
volontà: dump periodico del database più copia incrementale dei media su un
**percorso su secondo disco**, configurato dentro il compose.

**Aggiornamento del 20/09/2026.** Su indicazione dell'utente il backup è **disattivato
per ora**: il servizio sta nel compose sotto il profilo `backup` e si attiva con un
comando, senza modifiche al codice. La scelta progettuale resta quella descritta qui;
cambia solo quando entra in funzione. Conseguenza registrata come rischio R2
accettato.

## La parte che conta davvero

**Il ripristino va provato, non solo configurato**, con il tempo impiegato annotato
nel runbook: così il giorno del guasto si sa quanto ci vorrà, invece di scoprirlo
allora. La prova era stata anticipata allo Sprint 0; con il backup disattivato torna
a S7, insieme all'attivazione del profilo.

Ogni backup porta un'impronta di verifica, e lo script di ripristino la controlla
**prima** di sovrascrivere qualcosa.

## Esportazione (F11)

Distinta dal backup, e serve a un'altra cosa: il backup protegge dai guasti,
l'esportazione serve a **chiudere un'avventura e archiviarla liberando spazio**.

Requisito: archivio autoconsistente con trascrizioni, cronologia, log dei tiri e
media, **più una versione leggibile senza l'app**. Per un gruppo che gioca da anni la
memoria della campagna vale più del software che la contiene, e non deve dipendere
dal fatto che questa app esista ancora fra cinque anni.

L'importazione dello stesso archivio è parte del requisito: un'esportazione
irreversibile è un vicolo cieco, non un archivio.

## Conseguenze

- Serve un secondo disco. È un costo una tantum, non ricorrente: rispetta §13-D3.
- Lo stato dell'ultimo backup è visibile al GM in app. Un backup fallito da tre
  settimane senza che nessuno se ne accorga è lo scenario da impedire.
