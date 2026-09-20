"""
Confronto delle configurazioni di trascrizione (SPIKE-1).

Esegue il riconoscimento sulle clip di riferimento in piu' configurazioni e produce
un CSV con WER complessivo e WER sui nomi propri per ciascuna.

Le configurazioni 1 e 2 girano qui (sul PC). La configurazione "device" va eseguita
con l'app di prova sul telefono e importata dal CSV che produce: qui viene solo
riletta e messa in tabella, perche' il motore di sistema Android non e' invocabile
da un PC.

Uso:
    python3 confronta.py ./clips [--modello small] [--device risultati-telefono.csv]
"""

from __future__ import annotations

import argparse
import csv
import json
import time
from pathlib import Path

from wer import valuta, verdetto

AUDIO = {".wav", ".m4a", ".mp3", ".ogg", ".opus", ".flac"}


def carica_clip(cartella: Path) -> list[tuple[Path, str]]:
    coppie = []
    for a in sorted(cartella.iterdir()):
        if a.suffix.lower() in AUDIO:
            rif = a.with_suffix(".txt")
            if not rif.exists():
                print(f"  ATTENZIONE: manca il riferimento per {a.name}, clip ignorata")
                continue
            coppie.append((a, rif.read_text(encoding="utf-8").strip()))
    return coppie


def trascrivi(modello, audio: Path, glossario: list[str] | None) -> tuple[str, float]:
    # Il glossario viene passato come contesto iniziale: e' il meccanismo che
    # orienta il riconoscitore verso i nomi della campagna. E' esattamente cio'
    # che il motore di sistema del telefono non permette di fare.
    prompt = ("Nomi propri: " + ", ".join(glossario)) if glossario else None
    t0 = time.perf_counter()
    segmenti, _ = modello.transcribe(
        str(audio), language="it", beam_size=5, initial_prompt=prompt
    )
    testo = " ".join(s.text for s in segmenti).strip()
    return testo, time.perf_counter() - t0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("cartella", type=Path)
    ap.add_argument("--modello", default="small")
    ap.add_argument("--glossario", type=Path, default=Path("glossario.json"))
    ap.add_argument("--device", type=Path, help="CSV prodotto dall'app di prova sul telefono")
    args = ap.parse_args()

    clip = carica_clip(args.cartella)
    if not clip:
        print("Nessuna clip con riferimento trovata. Vedi PROTOCOLLO-CLIP.md")
        return 1

    nomi: list[str] = []
    if args.glossario.exists():
        nomi = json.loads(args.glossario.read_text(encoding="utf-8")).get("nomi", [])
    else:
        print(f"ATTENZIONE: {args.glossario} assente: il confronto perde il suo scopo principale.")

    try:
        from faster_whisper import WhisperModel  # type: ignore
    except ImportError:
        print("faster-whisper non installato:  pip install faster-whisper")
        return 1

    modello = WhisperModel(args.modello, device="auto", compute_type="auto")
    del_device = {}
    if args.device and args.device.exists():
        with args.device.open(encoding="utf-8") as f:
            del_device = {r["clip"]: r["trascrizione"] for r in csv.DictReader(f)}

    Path("out").mkdir(exist_ok=True)
    righe = []
    tot = {"senza": [], "con": [], "device": []}

    for audio, riferimento in clip:
        print(f"  {audio.name} ...", flush=True)
        for etichetta, gloss in (("senza_glossario", None), ("con_glossario", nomi)):
            testo, secondi = trascrivi(modello, audio, gloss)
            e = valuta(riferimento, testo, nomi)
            righe.append({
                "clip": audio.name, "configurazione": f"server_{args.modello}_{etichetta}",
                "wer": round(e.wer, 4), "wer_nomi": round(e.wer_nomi, 4),
                "nomi_nel_riferimento": e.nomi_riferimento, "nomi_sbagliati": e.nomi_sbagliati,
                "secondi_elaborazione": round(secondi, 2), "trascrizione": testo,
            })
            tot["con" if gloss else "senza"].append(e)

        if audio.name in del_device:
            e = valuta(riferimento, del_device[audio.name], nomi)
            righe.append({
                "clip": audio.name, "configurazione": "device_android",
                "wer": round(e.wer, 4), "wer_nomi": round(e.wer_nomi, 4),
                "nomi_nel_riferimento": e.nomi_riferimento, "nomi_sbagliati": e.nomi_sbagliati,
                "secondi_elaborazione": "", "trascrizione": del_device[audio.name],
            })
            tot["device"].append(e)

    with (Path("out") / "risultati.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(righe[0].keys()))
        w.writeheader()
        w.writerows(righe)

    def aggrega(lista):
        if not lista:
            return None
        from wer import Esito
        return Esito(
            sum(e.parole_riferimento for e in lista), sum(e.sostituzioni for e in lista),
            sum(e.cancellazioni for e in lista), sum(e.inserimenti for e in lista),
            sum(e.nomi_riferimento for e in lista), sum(e.nomi_sbagliati for e in lista),
        )

    print("\n=== RIEPILOGO (aggregato su tutte le clip) ===")
    agg = {k: aggrega(v) for k, v in tot.items()}
    for k, e in agg.items():
        if e:
            print(f"  {k:<8} WER {e.wer:6.1%}   WER nomi propri {e.wer_nomi:6.1%}")

    if agg["senza"] and agg["con"]:
        print(f"\n=== VERDETTO ===\n  {verdetto(agg['senza'], agg['con'])}")
    print("\nSalvato in out/risultati.csv — rimandami questo file.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
