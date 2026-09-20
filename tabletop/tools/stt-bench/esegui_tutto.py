"""
SPIKE-1 con un comando solo.

    python3 esegui_tutto.py ./clips

Fa tutto in sequenza: verifica i prerequisiti, dimensiona il modello misurando su
questa macchina, esegue il confronto fra le configurazioni e scrive un rapporto
leggibile. Nessuna informazione sull'hardware va fornita a mano: viene rilevata.

Se manca qualcosa, lo dice e spiega come rimediare, invece di interrompersi con una
traccia di stack.
"""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
from pathlib import Path

QUI = Path(__file__).parent
AUDIO = {".wav", ".m4a", ".mp3", ".ogg", ".opus", ".flac"}


def intestazione(t: str) -> None:
    print(f"\n{'=' * 60}\n{t}\n{'=' * 60}")


def verifica_prerequisiti(cartella: Path) -> list[str]:
    """Tutti i problemi in una volta, non uno alla volta a ogni riesecuzione."""
    problemi: list[str] = []

    if sys.version_info < (3, 10):
        problemi.append(f"Serve Python 3.10 o superiore (qui c'e' {sys.version.split()[0]}).")

    try:
        import faster_whisper  # noqa: F401
    except ImportError:
        problemi.append("Manca faster-whisper. Rimedio:  pip install faster-whisper")

    if not shutil.which("ffmpeg"):
        problemi.append(
            "Manca ffmpeg, necessario per leggere i file audio del telefono.\n"
            "      Windows:  winget install ffmpeg      Linux:  apt install ffmpeg"
        )

    if not cartella.is_dir():
        problemi.append(f"Cartella non trovata: {cartella}")
        return problemi

    clip = [f for f in cartella.iterdir() if f.suffix.lower() in AUDIO]
    if not clip:
        problemi.append(f"Nessun file audio in {cartella}. Vedi PROTOCOLLO-CLIP.md")
    else:
        senza_riferimento = [f.name for f in clip if not f.with_suffix(".txt").exists()]
        if senza_riferimento:
            problemi.append(
                f"{len(senza_riferimento)} clip senza trascrizione di riferimento: "
                f"{', '.join(senza_riferimento[:5])}"
                f"{' ...' if len(senza_riferimento) > 5 else ''}\n"
                "      Senza il riferimento la misura non ha metro di paragone."
            )

    if not (QUI / "glossario.json").exists():
        problemi.append(
            "Manca glossario.json (copia glossario.esempio.json e mettici i tuoi nomi).\n"
            "      Senza glossario lo spike perde il suo scopo principale."
        )

    return problemi


def main() -> int:
    cartella = Path(sys.argv[1] if len(sys.argv) > 1 else "./clips").resolve()

    intestazione("1/3 — Prerequisiti")
    problemi = verifica_prerequisiti(cartella)
    if problemi:
        print("Manca qualcosa prima di poter misurare:\n")
        for p in problemi:
            print(f"  - {p}")
        print("\nRisolvi questi punti e rilancia lo stesso comando.")
        return 1
    clip = sorted(f for f in cartella.iterdir() if f.suffix.lower() in AUDIO)
    print(f"  ok — {len(clip)} clip con trascrizione di riferimento")

    intestazione("2/3 — Dimensionamento del modello su questa macchina")
    print("  Misura quanto ci mette ogni modello. Puo' richiedere qualche minuto\n"
          "  e il primo avvio scarica i modelli.\n")
    esito = subprocess.run(
        [sys.executable, str(QUI / "dimensiona.py"), str(clip[0])], cwd=QUI, check=False
    )
    if esito.returncode != 0:
        print("\nIl dimensionamento e' fallito. Non proseguo: il confronto userebbe\n"
              "un modello scelto a caso, e il risultato non varrebbe niente.")
        return 1

    scelto = "small"
    percorso = QUI / "out" / "dimensionamento.json"
    if percorso.exists():
        dati = json.loads(percorso.read_text(encoding="utf-8"))
        scelto = dati.get("scelto") or scelto

    intestazione(f"3/3 — Confronto delle configurazioni (modello: {scelto})")
    esito = subprocess.run(
        [sys.executable, str(QUI / "confronta.py"), str(cartella), "--modello", scelto],
        cwd=QUI, check=False,
    )
    if esito.returncode != 0:
        return 1

    intestazione("Fatto")
    print("  Rimandami questi due file:\n"
          f"    {QUI / 'out' / 'dimensionamento.json'}\n"
          f"    {QUI / 'out' / 'risultati.csv'}\n\n"
          "  Non contengono il tuo audio: solo le trascrizioni, i tempi e i numeri.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
