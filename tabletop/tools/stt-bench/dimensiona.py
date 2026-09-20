"""
Dimensionamento del modello di riconoscimento vocale (SPIKE-1, risposta a Q1).

Invece di chiedere le caratteristiche del PC e scegliere a tavolino, misura.
Prova i modelli candidati su una clip reale, cronometra, e propone il piu' grande
che sta dentro il budget di attesa accettabile per un messaggio vocale.

Uso:
    pip install faster-whisper
    python3 dimensiona.py clip-di-prova.wav

Produce out/dimensionamento.json, da rimandare insieme agli altri risultati.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path

# Fattore di tempo reale: secondi di elaborazione per secondo di audio.
# RTF 0.5 = un vocale di 30 s e' trascritto in 15 s.
#
# Soglia scelta: per un messaggio vocale asincrono in chat, un'attesa fino a circa
# un terzo della durata del vocale passa inosservata; oltre il doppio la funzione
# cambia natura e diventa una coda. Sono soglie di prodotto, dichiarate qui e
# discutibili, non costanti di natura.
RTF_OTTIMO = 0.35
RTF_ACCETTABILE = 1.0

CANDIDATI = ["tiny", "base", "small", "medium", "large-v3"]


@dataclass
class Prova:
    modello: str
    secondi_audio: float
    secondi_elaborazione: float
    rtf: float
    errore: str | None = None


def hardware() -> dict[str, object]:
    info: dict[str, object] = {
        "piattaforma": sys.platform,
        "cpu_logiche": os.cpu_count(),
    }
    try:
        import psutil  # type: ignore

        info["ram_gb"] = round(psutil.virtual_memory().total / 1024**3, 1)
    except Exception:
        info["ram_gb"] = None

    gpu = None
    if shutil.which("nvidia-smi"):
        try:
            out = subprocess.run(
                ["nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader"],
                capture_output=True, text=True, timeout=10, check=False,
            ).stdout.strip()
            gpu = out.splitlines()[0] if out else None
        except Exception:
            gpu = None
    info["gpu"] = gpu
    return info


def scegli(prove: list[Prova]) -> tuple[str | None, str]:
    """Logica di scelta, separata dall'I/O per poterla provare senza audio."""
    valide = [p for p in prove if p.errore is None]
    if not valide:
        return None, "Nessun modello ha completato la prova: controlla l'installazione."

    ottimi = [p for p in valide if p.rtf <= RTF_OTTIMO]
    if ottimi:
        scelto = max(ottimi, key=lambda p: CANDIDATI.index(p.modello))
        return scelto.modello, (
            f"'{scelto.modello}' resta sotto {RTF_OTTIMO} di fattore tempo reale "
            f"(RTF {scelto.rtf:.2f}): l'attesa e' trascurabile."
        )

    accettabili = [p for p in valide if p.rtf <= RTF_ACCETTABILE]
    if accettabili:
        scelto = max(accettabili, key=lambda p: CANDIDATI.index(p.modello))
        return scelto.modello, (
            f"'{scelto.modello}' con RTF {scelto.rtf:.2f}: un vocale di 30 s richiede "
            f"circa {scelto.rtf * 30:.0f} s. Utilizzabile, ma la coda va mostrata in chat."
        )

    migliore = min(valide, key=lambda p: p.rtf)
    return migliore.modello, (
        f"ATTENZIONE: anche il modello piu' leggero ha RTF {migliore.rtf:.2f} "
        f"(~{migliore.rtf * 30:.0f} s per un vocale di 30 s). Su questo PC la "
        f"trascrizione automatica e' al limite: valutare il motore on-device del "
        f"telefono come percorso principale, o una macchina piu' capace."
    )


def prova_modello(nome: str, audio: Path) -> Prova:
    try:
        from faster_whisper import WhisperModel  # type: ignore
    except ImportError:
        return Prova(nome, 0, 0, 0, "faster-whisper non installato (pip install faster-whisper)")

    try:
        modello = WhisperModel(nome, device="auto", compute_type="auto")
        inizio = time.perf_counter()
        segmenti, info = modello.transcribe(str(audio), language="it", beam_size=1)
        testo = " ".join(s.text for s in segmenti)
        durata_elab = time.perf_counter() - inizio
        durata_audio = float(getattr(info, "duration", 0.0)) or 1.0
        Path("out").mkdir(exist_ok=True)
        (Path("out") / f"trascrizione-{nome}.txt").write_text(testo.strip(), encoding="utf-8")
        return Prova(nome, round(durata_audio, 2), round(durata_elab, 2),
                     round(durata_elab / durata_audio, 3))
    except Exception as e:  # noqa: BLE001 - qui vogliamo davvero catturare tutto
        return Prova(nome, 0, 0, 0, f"{type(e).__name__}: {e}")


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    audio = Path(sys.argv[1])
    if not audio.exists():
        print(f"File non trovato: {audio}", file=sys.stderr)
        return 1

    hw = hardware()
    print("=== HARDWARE ===")
    for k, v in hw.items():
        print(f"  {k}: {v}")
    if not hw.get("gpu"):
        print("  NOTA: nessuna GPU rilevata. I modelli grandi saranno lenti su CPU.")
    print("\n=== PROVE ===")

    prove: list[Prova] = []
    for nome in CANDIDATI:
        print(f"  {nome} ... ", end="", flush=True)
        p = prova_modello(nome, audio)
        prove.append(p)
        print(p.errore if p.errore else f"RTF {p.rtf:.2f} ({p.secondi_elaborazione:.1f}s)")
        # Inutile provare modelli piu' grandi se il precedente e' gia' fuori scala.
        if p.errore is None and p.rtf > RTF_ACCETTABILE * 2:
            print("  (interrotto: i modelli successivi sarebbero solo piu' lenti)")
            break

    scelto, motivo = scegli(prove)
    print(f"\n=== PROPOSTA ===\n  modello: {scelto}\n  {motivo}")

    Path("out").mkdir(exist_ok=True)
    Path("out/dimensionamento.json").write_text(
        json.dumps({"hardware": hw, "prove": [asdict(p) for p in prove],
                    "scelto": scelto, "motivo": motivo}, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    print("\nSalvato in out/dimensionamento.json — rimandami questo file.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
