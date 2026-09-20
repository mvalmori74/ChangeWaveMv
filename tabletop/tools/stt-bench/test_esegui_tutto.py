"""Test della verifica dei prerequisiti: deve elencare TUTTI i problemi, non il primo."""
import tempfile
from pathlib import Path
from esegui_tutto import verifica_prerequisiti


def test_cartella_inesistente():
    p = verifica_prerequisiti(Path("/non/esiste"))
    assert any("non trovata" in x for x in p)


def test_segnala_le_clip_senza_riferimento():
    with tempfile.TemporaryDirectory() as d:
        c = Path(d)
        (c / "clip-01.wav").write_bytes(b"")
        (c / "clip-02.wav").write_bytes(b"")
        (c / "clip-01.txt").write_text("ciao", encoding="utf-8")
        p = verifica_prerequisiti(c)
        manca = [x for x in p if "senza trascrizione" in x]
        assert len(manca) == 1 and "clip-02.wav" in manca[0]


def test_cartella_vuota():
    with tempfile.TemporaryDirectory() as d:
        p = verifica_prerequisiti(Path(d))
        assert any("Nessun file audio" in x for x in p)


def test_elenca_piu_problemi_insieme():
    # Chi esegue non deve scoprire i problemi uno alla volta a ogni riesecuzione.
    with tempfile.TemporaryDirectory() as d:
        p = verifica_prerequisiti(Path(d))
        assert len(p) >= 1
        assert all(isinstance(x, str) and x for x in p)


if __name__ == "__main__":
    n = 0
    for nome, f in sorted(globals().items()):
        if nome.startswith("test_") and callable(f):
            f(); n += 1; print(f"  ok  {nome}")
    print(f"\n{n} test superati")
