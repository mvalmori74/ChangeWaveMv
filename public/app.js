// API Base URL
const API_URL = '/api';

// Stato applicazione
let authors = [];
let books = [];
let editingAuthorId = null;
let editingBookId = null;

// Inizializzazione
document.addEventListener('DOMContentLoaded', () => {
    loadAuthors();
    loadBooks();
    setupEventListeners();
});

// Setup Event Listeners
function setupEventListeners() {
    document.getElementById('author-form').addEventListener('submit', handleAuthorSubmit);
    document.getElementById('book-form').addEventListener('submit', handleBookSubmit);
}

// Gestione Tab
function showTab(tabName) {
    // Rimuovi active da tutti i tab
    document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));

    // Aggiungi active al tab selezionato
    event.target.classList.add('active');
    document.getElementById(tabName + '-tab').classList.add('active');

    // Ricarica i dati
    if (tabName === 'authors') {
        loadAuthors();
    } else if (tabName === 'books') {
        loadBooks();
    }
}

// ========== AUTORI ==========

// Carica tutti gli autori
async function loadAuthors() {
    try {
        const response = await fetch(`${API_URL}/authors`);
        const data = await response.json();

        if (data.success) {
            authors = data.data;
            renderAuthors();
            populateAuthorSelects();
        }
    } catch (error) {
        showNotification('Errore nel caricamento degli autori', 'error');
        console.error(error);
    }
}

// Renderizza lista autori
function renderAuthors() {
    const container = document.getElementById('authors-list');

    if (authors.length === 0) {
        container.innerHTML = '<div class="empty-state">Nessun autore presente. Aggiungi il primo autore!</div>';
        return;
    }

    container.innerHTML = authors.map(author => `
        <div class="card">
            <h3>${author.name}</h3>
            <div class="card-info">
                ${author.nationality ? `<p><strong>Nazionalità:</strong> ${author.nationality}</p>` : ''}
                ${author.birthDate ? `<p><strong>Nato il:</strong> ${formatDate(author.birthDate)}</p>` : ''}
                ${author.biography ? `<p><strong>Biografia:</strong> ${author.biography}</p>` : ''}
            </div>
            <div class="card-actions">
                <button class="btn btn-edit" onclick="editAuthor('${author.id}')">Modifica</button>
                <button class="btn btn-danger" onclick="deleteAuthor('${author.id}')">Elimina</button>
            </div>
        </div>
    `).join('');
}

// Gestione submit form autore
async function handleAuthorSubmit(e) {
    e.preventDefault();

    const authorData = {
        name: document.getElementById('author-name').value,
        nationality: document.getElementById('author-nationality').value,
        birthDate: document.getElementById('author-birthdate').value || null,
        biography: document.getElementById('author-biography').value
    };

    try {
        let response;
        if (editingAuthorId) {
            // Update
            response = await fetch(`${API_URL}/authors/${editingAuthorId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(authorData)
            });
        } else {
            // Create
            response = await fetch(`${API_URL}/authors`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(authorData)
            });
        }

        const data = await response.json();

        if (data.success) {
            showNotification(data.message, 'success');
            resetAuthorForm();
            loadAuthors();
        } else {
            showNotification(data.message, 'error');
        }
    } catch (error) {
        showNotification('Errore nel salvataggio dell\'autore', 'error');
        console.error(error);
    }
}

// Modifica autore
function editAuthor(id) {
    const author = authors.find(a => a.id === id);
    if (!author) return;

    editingAuthorId = id;
    document.getElementById('author-name').value = author.name;
    document.getElementById('author-nationality').value = author.nationality || '';
    document.getElementById('author-birthdate').value = author.birthDate || '';
    document.getElementById('author-biography').value = author.biography || '';

    // Scroll to form
    document.querySelector('#authors-tab .section').scrollIntoView({ behavior: 'smooth' });
}

// Elimina autore
async function deleteAuthor(id) {
    if (!confirm('Sei sicuro di voler eliminare questo autore?')) return;

    try {
        const response = await fetch(`${API_URL}/authors/${id}`, {
            method: 'DELETE'
        });

        const data = await response.json();

        if (data.success) {
            showNotification(data.message, 'success');
            loadAuthors();
        } else {
            showNotification(data.message, 'error');
        }
    } catch (error) {
        showNotification('Errore nell\'eliminazione dell\'autore', 'error');
        console.error(error);
    }
}

// Reset form autore
function resetAuthorForm() {
    document.getElementById('author-form').reset();
    editingAuthorId = null;
}

// ========== LIBRI ==========

// Carica tutti i libri
async function loadBooks() {
    try {
        const filterAuthorId = document.getElementById('filter-author').value;
        let url = `${API_URL}/books`;

        if (filterAuthorId) {
            url = `${API_URL}/books/author/${filterAuthorId}`;
        }

        const response = await fetch(url);
        const data = await response.json();

        if (data.success) {
            books = data.data;
            renderBooks();
        }
    } catch (error) {
        showNotification('Errore nel caricamento dei libri', 'error');
        console.error(error);
    }
}

// Renderizza lista libri
function renderBooks() {
    const container = document.getElementById('books-list');

    if (books.length === 0) {
        container.innerHTML = '<div class="empty-state">Nessun libro presente. Aggiungi il primo libro!</div>';
        return;
    }

    container.innerHTML = books.map(book => `
        <div class="card">
            <h3>${book.title}</h3>
            <div class="card-info">
                <p><strong>Autore:</strong> ${book.author ? book.author.name : 'Sconosciuto'}</p>
                ${book.isbn ? `<p><strong>ISBN:</strong> ${book.isbn}</p>` : ''}
                ${book.genre ? `<p><strong>Genere:</strong> ${book.genre}</p>` : ''}
                ${book.publishedYear ? `<p><strong>Anno:</strong> ${book.publishedYear}</p>` : ''}
                ${book.pages ? `<p><strong>Pagine:</strong> ${book.pages}</p>` : ''}
                ${book.description ? `<p><strong>Descrizione:</strong> ${book.description}</p>` : ''}
            </div>
            <div class="card-actions">
                <button class="btn btn-edit" onclick="editBook('${book.id}')">Modifica</button>
                <button class="btn btn-danger" onclick="deleteBook('${book.id}')">Elimina</button>
            </div>
        </div>
    `).join('');
}

// Gestione submit form libro
async function handleBookSubmit(e) {
    e.preventDefault();

    const bookData = {
        title: document.getElementById('book-title').value,
        authorId: document.getElementById('book-author').value,
        isbn: document.getElementById('book-isbn').value,
        genre: document.getElementById('book-genre').value,
        publishedYear: document.getElementById('book-year').value ? parseInt(document.getElementById('book-year').value) : null,
        pages: document.getElementById('book-pages').value ? parseInt(document.getElementById('book-pages').value) : null,
        description: document.getElementById('book-description').value
    };

    try {
        let response;
        if (editingBookId) {
            // Update
            response = await fetch(`${API_URL}/books/${editingBookId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(bookData)
            });
        } else {
            // Create
            response = await fetch(`${API_URL}/books`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(bookData)
            });
        }

        const data = await response.json();

        if (data.success) {
            showNotification(data.message, 'success');
            resetBookForm();
            loadBooks();
        } else {
            showNotification(data.message, 'error');
        }
    } catch (error) {
        showNotification('Errore nel salvataggio del libro', 'error');
        console.error(error);
    }
}

// Modifica libro
function editBook(id) {
    const book = books.find(b => b.id === id);
    if (!book) return;

    editingBookId = id;
    document.getElementById('book-title').value = book.title;
    document.getElementById('book-author').value = book.authorId;
    document.getElementById('book-isbn').value = book.isbn || '';
    document.getElementById('book-genre').value = book.genre || '';
    document.getElementById('book-year').value = book.publishedYear || '';
    document.getElementById('book-pages').value = book.pages || '';
    document.getElementById('book-description').value = book.description || '';

    // Scroll to form
    document.querySelector('#books-tab .section').scrollIntoView({ behavior: 'smooth' });
}

// Elimina libro
async function deleteBook(id) {
    if (!confirm('Sei sicuro di voler eliminare questo libro?')) return;

    try {
        const response = await fetch(`${API_URL}/books/${id}`, {
            method: 'DELETE'
        });

        const data = await response.json();

        if (data.success) {
            showNotification(data.message, 'success');
            loadBooks();
        } else {
            showNotification(data.message, 'error');
        }
    } catch (error) {
        showNotification('Errore nell\'eliminazione del libro', 'error');
        console.error(error);
    }
}

// Reset form libro
function resetBookForm() {
    document.getElementById('book-form').reset();
    editingBookId = null;
}

// ========== UTILITY ==========

// Popola i select degli autori
function populateAuthorSelects() {
    const bookAuthorSelect = document.getElementById('book-author');
    const filterAuthorSelect = document.getElementById('filter-author');

    const options = authors.map(author =>
        `<option value="${author.id}">${author.name}</option>`
    ).join('');

    bookAuthorSelect.innerHTML = '<option value="">Seleziona autore...</option>' + options;
    filterAuthorSelect.innerHTML = '<option value="">Tutti gli autori</option>' + options;
}

// Formatta data
function formatDate(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('it-IT');
}

// Mostra notifica
function showNotification(message, type = 'info') {
    const notification = document.getElementById('notification');
    notification.textContent = message;
    notification.className = `notification ${type}`;

    setTimeout(() => {
        notification.className = 'notification';
    }, 3000);
}
