# ADR-005 — Backup ed esportazione della campagna

- **Stato**: accettato per la strategia; formato dell'archivio da definire in S7
- **Data**: 2026-09-20
- **Contesto**: §13-D9 e D10. Server sul PC di casa, retention infinita, cancellazione
  manuale. Il disco di quel PC è **l'unica copia** di anni di gioco.

## Decisione

**Backup automatico attivo di default**, non opzionale e non affidato alla buona
volontà: dump periodico del database più copia incrementale dei media su un
**percorso su secondo disco**, configurato dentro il compose.

Il compose **rifiuta di partire** se il percorso di backup non è impostato. Un server
senza backup non deve sembrare funzionante.

## La parte che conta davvero

**Il ripristino va provato, non solo configurato.** Un backup mai ripristinato non è
un backup: è una cartella che cresce. Per questo la prova di ripristino è stata
**anticipata da S7 allo Sprint 0**, con il tempo impiegato annotato nel runbook — così
il giorno del guasto si sa quanto ci vorrà, invece di scoprirlo allora.

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
