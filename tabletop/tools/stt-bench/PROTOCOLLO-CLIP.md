# Protocollo di registrazione delle clip di riferimento (SPIKE-1)

Serve a produrre l'unico input che non posso generare io: **la tua voce da GM, con i
nomi inventati della tua campagna**. Senza queste clip, il verdetto su F3 è
un'opinione invece di una misura.

Tempo richiesto: **circa 30 minuti**.

## Cosa registrare

**20 clip da 30–60 secondi**, di narrazione vera. Non leggere un testo in modo piatto:
il riconoscitore si comporta in modo molto diverso sulla lettura e sulla recitazione,
e a te serve il secondo caso.

Distribuzione delle condizioni — è la parte che conta più del contenuto:

| Quante | Condizione |
|---|---|
| 5 | stanza silenziosa, voce normale |
| 5 | con rumore di fondo domestico (TV accesa in un'altra stanza, lavastoviglie, traffico) |
| 5 | voce concitata, da scena di combattimento |
| 5 | voce bassa, "da tavolo", come quando si sussurra una descrizione |

## Contenuto

- Almeno **25 nomi propri inventati distinti** in totale: personaggi, luoghi, oggetti,
  divinità. **Ripetili in clip diverse**: serve a vedere se il motore sbaglia in modo
  coerente (recuperabile con il glossario) o casuale (non recuperabile).
- Frasi vere di narrazione, non elenchi di nomi.
- Dialoghi di PNG inclusi, se nel tuo stile: cambiano il timbro e mettono alla prova
  il riconoscitore.

## Come registrare

- **Con il telefono che userai davvero**, con l'app di registrazione di sistema, tenendolo
  come lo terresti giocando. Non con un microfono da studio: misureremmo una
  situazione che non esiste.
- Formato: quello predefinito del telefono va bene (`.m4a`, `.wav`, `.ogg`).
- Nomi dei file: `clip-01.m4a` … `clip-20.m4a`.

## Il riferimento (la parte noiosa, e indispensabile)

Per ogni clip scrivi **la trascrizione corretta**, parola per parola, di ciò che hai
detto davvero — comprese le esitazioni se le hai fatte. È il metro contro cui si
misura tutto: se il riferimento è approssimativo, i numeri non valgono niente.

Un file `clip-01.txt` accanto a ogni audio, stessa cartella.

## Il glossario

Copia `glossario.esempio.json` in `glossario.json` e mettici i nomi veri della tua
campagna. È l'ingrediente che il motore on-device del telefono non può usare e che
quello sul PC di casa può: **misurare quanto vale è lo scopo principale di questo spike.**

## Poi

```
pip install faster-whisper
python3 dimensiona.py clip-01.m4a      # sceglie il modello misurando sul tuo PC
python3 confronta.py ./clips            # esegue il confronto completo
```

Rimandami `out/dimensionamento.json` e `out/risultati.csv`.
