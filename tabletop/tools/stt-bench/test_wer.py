"""Test della misura di WER. Si eseguono con:  python3 -m pytest -q   oppure  python3 test_wer.py"""
from wer import Esito, allinea, normalizza, valuta, verdetto

GLOSSARIO = ["Zarthuk", "Velmoria", "Grimhold"]


def test_normalizzazione():
    assert normalizza("L'elfo, Zarthuk!  ") == ["l'elfo", "zarthuk"]
    assert normalizza("L’elfo") == ["l'elfo"]  # apostrofo tipografico uniformato
    assert normalizza("   ") == []


def test_trascrizione_perfetta():
    e = valuta("il nano entra a Grimhold", "il nano entra a Grimhold", GLOSSARIO)
    assert e.wer == 0.0 and e.wer_nomi == 0.0


def test_conta_le_tre_operazioni():
    e = valuta("a b c d", "a x c d e")  # b->x sostituzione, +e inserimento
    assert (e.sostituzioni, e.cancellazioni, e.inserimenti) == (1, 0, 1)
    assert e.wer == 0.5


def test_nome_proprio_sbagliato_pesa_sulla_metrica_dedicata():
    # WER complessivo basso, ma il nome proprio e' sbagliato: e' il caso che conta.
    e = valuta(
        "il vecchio Zarthuk apre il portone di Grimhold",
        "il vecchio sarto apre il portone di grim old",
        GLOSSARIO,
    )
    assert e.nomi_riferimento == 2
    assert e.nomi_sbagliati == 2
    assert e.wer_nomi == 1.0
    assert e.wer < e.wer_nomi  # il WER complessivo nasconde il problema


def test_riferimento_vuoto_non_divide_per_zero():
    e = valuta("", "qualcosa")
    assert e.wer == 0.0 and e.wer_nomi == 0.0


def test_glossario_assente_azzera_la_metrica_nomi():
    e = valuta("il nano entra", "il nano esce")
    assert e.nomi_riferimento == 0 and e.wer_nomi == 0.0


def test_allineamento_e_coerente_col_costo():
    passi = allinea(normalizza("a b c"), normalizza("a c"))
    assert sum(1 for p in passi if p.operazione == "cancellazione") == 1


def test_verdetto_copre_i_tre_esiti():
    buono = Esito(100, 5, 0, 0, 10, 1)      # wer_nomi 0.10
    scarso = Esito(100, 5, 0, 0, 10, 3)     # wer_nomi 0.30
    pessimo = Esito(100, 5, 0, 0, 10, 8)    # wer_nomi 0.80
    senza = Esito(100, 5, 0, 0, 10, 6)      # wer_nomi 0.60
    assert "PROCEDERE" in verdetto(senza, buono)
    assert "BOZZA" in verdetto(senza, scarso)
    assert "FERMARSI" in verdetto(senza, pessimo)


if __name__ == "__main__":
    fatti = 0
    for nome, f in sorted(globals().items()):
        if nome.startswith("test_") and callable(f):
            f()
            fatti += 1
            print(f"  ok  {nome}")
    print(f"\n{fatti} test superati")
