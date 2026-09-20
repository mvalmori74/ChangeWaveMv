"""Test della logica di scelta del modello, senza bisogno di audio ne' di GPU."""
from dimensiona import Prova, scegli


def p(modello, rtf, errore=None):
    return Prova(modello, 30.0, 30.0 * rtf, rtf, errore)


def test_sceglie_il_piu_grande_sotto_la_soglia_ottima():
    scelto, _ = scegli([p("tiny", 0.05), p("base", 0.10), p("small", 0.20), p("medium", 0.60)])
    assert scelto == "small"


def test_ripiega_su_accettabile_quando_nessuno_e_ottimo():
    scelto, motivo = scegli([p("tiny", 0.50), p("base", 0.80), p("small", 1.40)])
    assert scelto == "base" and "coda" in motivo


def test_avvisa_quando_il_pc_non_ce_la_fa():
    scelto, motivo = scegli([p("tiny", 1.80), p("base", 3.00)])
    assert scelto == "tiny" and "ATTENZIONE" in motivo


def test_ignora_le_prove_fallite():
    scelto, _ = scegli([p("tiny", 0.10), p("base", 0.0, "modello non scaricato")])
    assert scelto == "tiny"


def test_nessuna_prova_valida():
    scelto, motivo = scegli([p("tiny", 0.0, "errore"), p("base", 0.0, "errore")])
    assert scelto is None and "Nessun modello" in motivo


if __name__ == "__main__":
    n = 0
    for nome, f in sorted(globals().items()):
        if nome.startswith("test_") and callable(f):
            f(); n += 1; print(f"  ok  {nome}")
    print(f"\n{n} test superati")
