> **Nota:** questo repository contiene più progetti. L'app Android **Ombra Parking**
> (geolocalizzazione + realtà aumentata per trovare parcheggio all'ombra) sta in
> [`OmbraParking/`](OmbraParking/README.md).

# Library REST API

API REST completa con interfaccia web grafica per gestire una libreria con libri e autori.

## ✨ Caratteristiche

- **API REST** completa con operazioni CRUD per autori e libri
- **Interfaccia Web** moderna e responsive
- **Relazioni** tra autori e libri con validazione
- **Database in-memory** (facilmente sostituibile con DB persistente)
- **Design moderno** con gradiente e animazioni

## 🚀 Installazione

```bash
npm install
```

## 📱 Avvio

```bash
# Modalità produzione
npm start

# Modalità sviluppo (con auto-reload)
npm run dev
```

L'applicazione sarà disponibile su `http://localhost:3000`

## 🌐 Interfaccia Web

Apri il browser e vai su **`http://localhost:3000`** per accedere all'interfaccia grafica dove puoi:

- ✅ Gestire autori (crea, modifica, elimina)
- ✅ Gestire libri (crea, modifica, elimina)
- ✅ Filtrare libri per autore
- ✅ Visualizzare tutte le informazioni in card moderne
- ✅ Ricevere notifiche per ogni operazione

## Endpoints

### Autori

#### GET /api/authors
Ottiene tutti gli autori.

**Risposta:**
```json
{
  "success": true,
  "count": 2,
  "data": [...]
}
```

#### GET /api/authors/:id
Ottiene un autore specifico per ID.

**Risposta:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "Nome Autore",
    "biography": "Biografia...",
    "birthDate": "1950-01-01",
    "nationality": "Italiana",
    "createdAt": "2025-11-05T..."
  }
}
```

#### POST /api/authors
Crea un nuovo autore.

**Body:**
```json
{
  "name": "Nome Autore",
  "biography": "Biografia dell'autore",
  "birthDate": "1950-01-01",
  "nationality": "Italiana"
}
```

**Risposta:**
```json
{
  "success": true,
  "message": "Autore creato con successo",
  "data": {...}
}
```

#### PUT /api/authors/:id
Aggiorna un autore esistente.

**Body:**
```json
{
  "name": "Nome Aggiornato",
  "biography": "Nuova biografia",
  "birthDate": "1950-01-01",
  "nationality": "Italiana"
}
```

#### DELETE /api/authors/:id
Elimina un autore. Non è possibile eliminare un autore se ci sono libri associati.

**Risposta:**
```json
{
  "success": true,
  "message": "Autore eliminato con successo"
}
```

### Libri

#### GET /api/books
Ottiene tutti i libri con i dati degli autori popolati.

**Risposta:**
```json
{
  "success": true,
  "count": 5,
  "data": [
    {
      "id": "uuid",
      "title": "Titolo Libro",
      "authorId": "uuid-autore",
      "isbn": "978-...",
      "publishedYear": 2020,
      "genre": "Fiction",
      "pages": 350,
      "description": "Descrizione...",
      "author": {
        "id": "uuid-autore",
        "name": "Nome Autore",
        ...
      }
    }
  ]
}
```

#### GET /api/books/:id
Ottiene un libro specifico per ID con i dati dell'autore popolati.

#### GET /api/books/author/:authorId
Ottiene tutti i libri di un autore specifico.

**Risposta:**
```json
{
  "success": true,
  "author": {...},
  "count": 3,
  "data": [...]
}
```

#### POST /api/books
Crea un nuovo libro.

**Body:**
```json
{
  "title": "Titolo del Libro",
  "authorId": "uuid-autore",
  "isbn": "978-1234567890",
  "publishedYear": 2023,
  "genre": "Fiction",
  "pages": 400,
  "description": "Descrizione del libro"
}
```

**Risposta:**
```json
{
  "success": true,
  "message": "Libro creato con successo",
  "data": {...}
}
```

#### PUT /api/books/:id
Aggiorna un libro esistente.

**Body:** (tutti i campi sono opzionali)
```json
{
  "title": "Nuovo Titolo",
  "authorId": "uuid-altro-autore",
  "isbn": "978-...",
  "publishedYear": 2024,
  "genre": "Non-fiction",
  "pages": 450,
  "description": "Nuova descrizione"
}
```

#### DELETE /api/books/:id
Elimina un libro.

**Risposta:**
```json
{
  "success": true,
  "message": "Libro eliminato con successo"
}
```

## Esempi di Utilizzo

### Creare un autore
```bash
curl -X POST http://localhost:3000/api/authors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Italo Calvino",
    "biography": "Scrittore italiano",
    "birthDate": "1923-10-15",
    "nationality": "Italiana"
  }'
```

### Creare un libro
```bash
curl -X POST http://localhost:3000/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Il barone rampante",
    "authorId": "uuid-calvino",
    "isbn": "978-8806132729",
    "publishedYear": 1957,
    "genre": "Fiction",
    "pages": 272,
    "description": "Un giovane nobile decide di vivere sugli alberi"
  }'
```

### Ottenere tutti i libri
```bash
curl http://localhost:3000/api/books
```

### Ottenere i libri di un autore
```bash
curl http://localhost:3000/api/books/author/uuid-autore
```

## Struttura del Progetto

```
.
├── package.json
├── README.md
├── .gitignore
└── src/
    ├── app.js                      # Entry point
    ├── database.js                 # Database in-memory
    ├── models/
    │   ├── Author.js               # Modello Autore
    │   └── Book.js                 # Modello Libro
    ├── controllers/
    │   ├── authorsController.js    # Controller autori
    │   └── booksController.js      # Controller libri
    └── routes/
        ├── authorsRoutes.js        # Route autori
        └── booksRoutes.js          # Route libri
```

## Tecnologie Utilizzate

- **Node.js** - Runtime JavaScript
- **Express** - Framework web
- **body-parser** - Middleware per parsing del body
- **uuid** - Generazione di ID unici

## Note

- Il database è in-memory, quindi i dati vengono persi al riavvio del server
- Per un ambiente di produzione, è consigliabile utilizzare un database persistente (MongoDB, PostgreSQL, etc.)
- Non è implementata l'autenticazione - per un uso in produzione aggiungere JWT o altre strategie di auth

## Validazioni

- Il nome dell'autore è obbligatorio
- Il titolo del libro è obbligatorio
- L'ID dell'autore deve esistere quando si crea un libro
- Non è possibile eliminare un autore con libri associati
