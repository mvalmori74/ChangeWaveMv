"""
Misura dell'errore di trascrizione (SPIKE-1, master prompt F3).

Due metriche, non una:
  - WER complessivo: quanto sbaglia in generale.
  - WER sui nomi propri: quanto sbaglia SUI NOMI INVENTATI della campagna.

La seconda e' quella che decide il progetto. Un motore con WER complessivo del 12%
che pero' sbaglia sistematicamente "Zarthuk" e "Velmoria" rende la funzione inutile
per un gioco di ruolo, perche' l'unica cosa che il giocatore non puo' ricostruire da
solo e' proprio il nome proprio.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from typing import Iterable, Literal, Sequence

Operazione = Literal["ok", "sostituzione", "cancellazione", "inserimento"]

# Teniamo l'apostrofo dentro la parola: in italiano "l'elfo" e' una scelta di
# tokenizzazione, non un refuso. Spezzarlo gonfierebbe il conteggio dei token e
# renderebbe i WER non confrontabili con la letteratura.
_PUNTEGGIATURA = re.compile(r"[^\w\s'’]", re.UNICODE)
_SPAZI = re.compile(r"\s+")


def normalizza(testo: str) -> list[str]:
    """Minuscole, niente punteggiatura, spazi compattati, apostrofi uniformati."""
    t = unicodedata.normalize("NFC", testo).lower().replace("’", "'")
    t = _PUNTEGGIATURA.sub(" ", t)
    return [p for p in _SPAZI.sub(" ", t).strip().split(" ") if p]


@dataclass(frozen=True)
class Passo:
    operazione: Operazione
    riferimento: str | None
    ipotesi: str | None


def allinea(riferimento: Sequence[str], ipotesi: Sequence[str]) -> list[Passo]:
    """Allineamento di Levenshtein a livello di parola, con ricostruzione del percorso."""
    n, m = len(riferimento), len(ipotesi)
    costo = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1):
        costo[i][0] = i
    for j in range(m + 1):
        costo[0][j] = j
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            if riferimento[i - 1] == ipotesi[j - 1]:
                costo[i][j] = costo[i - 1][j - 1]
            else:
                costo[i][j] = 1 + min(
                    costo[i - 1][j - 1],  # sostituzione
                    costo[i - 1][j],      # cancellazione
                    costo[i][j - 1],      # inserimento
                )

    passi: list[Passo] = []
    i, j = n, m
    while i > 0 or j > 0:
        if i > 0 and j > 0 and riferimento[i - 1] == ipotesi[j - 1] and costo[i][j] == costo[i - 1][j - 1]:
            passi.append(Passo("ok", riferimento[i - 1], ipotesi[j - 1]))
            i, j = i - 1, j - 1
        elif i > 0 and j > 0 and costo[i][j] == costo[i - 1][j - 1] + 1:
            passi.append(Passo("sostituzione", riferimento[i - 1], ipotesi[j - 1]))
            i, j = i - 1, j - 1
        elif i > 0 and costo[i][j] == costo[i - 1][j] + 1:
            passi.append(Passo("cancellazione", riferimento[i - 1], None))
            i -= 1
        else:
            passi.append(Passo("inserimento", None, ipotesi[j - 1]))
            j -= 1
    passi.reverse()
    return passi


@dataclass(frozen=True)
class Esito:
    parole_riferimento: int
    sostituzioni: int
    cancellazioni: int
    inserimenti: int
    nomi_riferimento: int
    nomi_sbagliati: int

    @property
    def wer(self) -> float:
        if self.parole_riferimento == 0:
            return 0.0
        return (self.sostituzioni + self.cancellazioni + self.inserimenti) / self.parole_riferimento

    @property
    def wer_nomi(self) -> float:
        """
        Errore sui soli nomi propri.

        Conta sostituzioni e cancellazioni delle parole del riferimento marcate come
        nomi propri. Gli inserimenti restano fuori di proposito: non sono
        attribuibili a una parola del riferimento, quindi includerli renderebbe la
        metrica arbitraria. E' una scelta, ed e' dichiarata qui invece che nascosta.
        """
        if self.nomi_riferimento == 0:
            return 0.0
        return self.nomi_sbagliati / self.nomi_riferimento


def valuta(riferimento: str, ipotesi: str, glossario: Iterable[str] = ()) -> Esito:
    rif = normalizza(riferimento)
    ipo = normalizza(ipotesi)
    nomi = {p for g in glossario for p in normalizza(g)}

    passi = allinea(rif, ipo)
    s = sum(1 for p in passi if p.operazione == "sostituzione")
    c = sum(1 for p in passi if p.operazione == "cancellazione")
    ins = sum(1 for p in passi if p.operazione == "inserimento")

    nomi_rif = sum(1 for p in rif if p in nomi)
    nomi_err = sum(
        1
        for p in passi
        if p.riferimento in nomi and p.operazione in ("sostituzione", "cancellazione")
    )

    return Esito(len(rif), s, c, ins, nomi_rif, nomi_err)


def verdetto(senza_glossario: Esito, con_glossario: Esito) -> str:
    """
    Traduce i numeri nella decisione che serve a fine SPIKE-1.
    Le soglie sono un punto di partenza dichiarato, non una verita' di letteratura.
    """
    if con_glossario.wer_nomi <= 0.15:
        guadagno = senza_glossario.wer_nomi - con_glossario.wer_nomi
        if guadagno >= 0.10:
            return "PROCEDERE: il glossario porta un guadagno netto sui nomi propri."
        return "PROCEDERE, ma il glossario incide poco: valutare se vale la complessita'."
    if con_glossario.wer_nomi <= 0.35:
        return "TRASCRIZIONE COME BOZZA: usabile solo con correzione a mano ben progettata."
    return "FERMARSI E RIPORTARE: sui nomi propri non regge. Riaprire la specifica di F3."
