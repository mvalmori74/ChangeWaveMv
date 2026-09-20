# Tracce per le 20 clip

**Cos'è una clip**: una registrazione audio di 30–60 secondi. Un file, fatto col
registratore vocale del telefono. Venti file in tutto.

Queste sono **tracce da cui improvvisare, non copioni da leggere.** La differenza è
sostanziale: un riconoscitore vocale si comporta in modo molto diverso su una lettura
e su una narrazione recitata, e a noi serve misurare il secondo caso, perché è quello
che farai giocando.

Per ogni traccia hai la situazione e i **nomi che devono comparire**. Il resto lo dici
come viene. Se sbagli una parola, se ti interrompi, se ricominci una frase: **lascia
tutto**. Quelle imperfezioni sono esattamente ciò che il riconoscitore incontrerà
davvero.

> **Se hai già una campagna in corso, sostituisci questi nomi con i tuoi.** Sono i
> nomi che dovrà indovinare per davvero. Ricordati poi di metterli in `glossario.json`.

---

## Come si registra

1. Registratore vocale del telefono, **quello che userai giocando**, tenuto come lo
   terresti al tavolo. Non un microfono buono: misureremmo una situazione che non esiste.
2. Un file per traccia: `clip-01`, `clip-02`, … `clip-20`. Il formato che esce di
   default va bene.
3. Subito dopo, riascolta e scrivi in `clip-NN.txt` **quello che hai detto davvero**,
   non quello che c'è scritto nella traccia. È la parte noiosa ed è quella che dà
   valore a tutto il resto: se il testo di riferimento è approssimativo, i numeri
   che ne escono non valgono niente.
4. Audio e `.txt` nella stessa cartella.

---

## Gruppo A — stanza silenziosa, voce normale (clip 01–05)

**01.** Descrivi l'arrivo del gruppo alle porte della città di **Grimhold** al
tramonto. Cita le guardie, l'odore, il rumore. Compaiono: *Grimhold*, *Velmoria*.

**02.** Il gruppo entra nella taverna. Presenta l'oste, **Torvald**, e una figura
sospetta in fondo alla sala, **Muryn**. Compaiono: *Torvald*, *Muryn*, *Grimhold*.

**03.** Descrivi una stanza del tesoro: cosa vedono, cosa sentono, cosa li mette a
disagio. Compaiono: *Ashkalar*, *Bhaalgor*.

**04.** Riassumi ai giocatori cos'è successo nella sessione precedente. Compaiono:
*Zarthuk*, *Velmoria*, *Kaerth*.

**05.** Un PNG dà un incarico al gruppo, con ricompensa e scadenza. Compaiono:
*Eldrath*, *Sylvaris*, *Grimhold*.

## Gruppo B — con rumore di fondo (clip 06–10)

Accendi la TV in un'altra stanza, o registra mentre la lavastoviglie lavora, o con la
finestra aperta sul traffico. **Non alzare la voce** per compensare: parla normale.

**06.** Il gruppo attraversa un mercato affollato. Compaiono: *Grimhold*, *Muryn*.

**07.** Descrivi una trappola che scatta e cosa succede nei tre secondi successivi.
Compaiono: *Ashkalar*, *Kaerth*.

**08.** Un mercante contratta il prezzo di un oggetto magico. Compaiono: *Torvald*,
*Eldrath*.

**09.** Descrivi il paesaggio durante un viaggio di tre giorni. Compaiono:
*Velmoria*, *Sylvaris*.

**10.** Un messaggero porta cattive notizie. Compaiono: *Zarthuk*, *Bhaalgor*,
*Grimhold*.

## Gruppo C — voce concitata, da combattimento (clip 11–15)

Come parli davvero quando la scena si accende: più veloce, più forte, frasi spezzate.

**11.** L'imboscata: descrivi il primo assalto. Compaiono: *Bhaalgor*, *Kaerth*.

**12.** Il nemico usa un attacco devastante e un personaggio cade. Compaiono:
*Zarthuk*, *Ashkalar*.

**13.** Descrivi una fuga a rotta di collo per i vicoli. Compaiono: *Grimhold*, *Muryn*.

**14.** Il drago si alza in volo. Urla e ordini nel gruppo. Compaiono: *Ashkalar*,
*Velmoria*.

**15.** Il colpo finale e il silenzio che segue. Compaiono: *Bhaalgor*, *Eldrath*.

## Gruppo D — voce bassa, "da tavolo" (clip 16–20)

Come quando ti avvicini e abbassi la voce per creare tensione. **Non sussurrare
teatralmente**: parla piano e vicino al telefono, come faresti davvero.

**16.** Descrivi cosa sente un personaggio dietro la porta, mentre gli altri non
sentono. Compaiono: *Muryn*, *Zarthuk*.

**17.** Una profezia letta da una pergamena antica. Compaiono: *Sylvaris*, *Bhaalgor*,
*Eldrath*.

**18.** Un segreto rivelato in disparte a un solo giocatore. Compaiono: *Torvald*,
*Kaerth*.

**19.** Descrivi un incubo ricorrente del personaggio. Compaiono: *Ashkalar*,
*Velmoria*.

**20.** L'ultima scena della sessione, quella che li lascia col fiato sospeso fino
alla prossima. Compaiono: *Zarthuk*, *Grimhold*, *Bhaalgor*.

---

## Verifica prima di mandarmele

- [ ] 20 file audio + 20 file `.txt` con lo stesso nome
- [ ] ogni clip fra 30 e 60 secondi
- [ ] i `.txt` riportano quello che hai detto davvero, esitazioni comprese
- [ ] i dieci nomi compaiono ciascuno in **almeno due clip diverse** — serve a
      distinguere un errore sistematico (recuperabile col glossario) da uno casuale
      (non recuperabile)
- [ ] `glossario.json` compilato con i nomi che hai usato davvero

Poi:

```
python3 dimensiona.py clips/clip-01.m4a
python3 confronta.py ./clips
```
