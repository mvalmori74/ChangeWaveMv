const Book = require('../models/Book');
const database = require('../database');

// GET tutti i libri
const getAllBooks = (req, res) => {
  // Popola i dati dell'autore per ogni libro
  const booksWithAuthors = database.books.map(book => {
    const author = database.authors.find(a => a.id === book.authorId);
    return {
      ...book,
      author: author || null
    };
  });

  res.json({
    success: true,
    count: booksWithAuthors.length,
    data: booksWithAuthors
  });
};

// GET libro per ID
const getBookById = (req, res) => {
  const book = database.books.find(b => b.id === req.params.id);

  if (!book) {
    return res.status(404).json({
      success: false,
      message: 'Libro non trovato'
    });
  }

  // Popola i dati dell'autore
  const author = database.authors.find(a => a.id === book.authorId);
  const bookWithAuthor = {
    ...book,
    author: author || null
  };

  res.json({
    success: true,
    data: bookWithAuthor
  });
};

// GET libri per autore
const getBooksByAuthor = (req, res) => {
  const books = database.books.filter(b => b.authorId === req.params.authorId);
  const author = database.authors.find(a => a.id === req.params.authorId);

  res.json({
    success: true,
    author: author || null,
    count: books.length,
    data: books
  });
};

// POST crea nuovo libro
const createBook = (req, res) => {
  try {
    const { title, authorId, isbn, publishedYear, genre, pages, description } = req.body;

    // Verifica che l'autore esista
    const authorExists = database.authors.find(a => a.id === authorId);
    if (!authorExists) {
      return res.status(400).json({
        success: false,
        message: 'Autore non trovato'
      });
    }

    const book = new Book(title, authorId, isbn, publishedYear, genre, pages, description);
    book.validate();
    database.books.push(book);

    res.status(201).json({
      success: true,
      message: 'Libro creato con successo',
      data: book
    });
  } catch (error) {
    res.status(400).json({
      success: false,
      message: error.message
    });
  }
};

// PUT aggiorna libro
const updateBook = (req, res) => {
  const index = database.books.findIndex(b => b.id === req.params.id);

  if (index === -1) {
    return res.status(404).json({
      success: false,
      message: 'Libro non trovato'
    });
  }

  try {
    const { title, authorId, isbn, publishedYear, genre, pages, description } = req.body;

    // Se si sta cambiando autore, verifica che il nuovo autore esista
    if (authorId && authorId !== database.books[index].authorId) {
      const authorExists = database.authors.find(a => a.id === authorId);
      if (!authorExists) {
        return res.status(400).json({
          success: false,
          message: 'Autore non trovato'
        });
      }
    }

    const updatedBook = {
      ...database.books[index],
      title: title || database.books[index].title,
      authorId: authorId || database.books[index].authorId,
      isbn: isbn !== undefined ? isbn : database.books[index].isbn,
      publishedYear: publishedYear !== undefined ? publishedYear : database.books[index].publishedYear,
      genre: genre !== undefined ? genre : database.books[index].genre,
      pages: pages !== undefined ? pages : database.books[index].pages,
      description: description !== undefined ? description : database.books[index].description
    };

    database.books[index] = updatedBook;

    res.json({
      success: true,
      message: 'Libro aggiornato con successo',
      data: updatedBook
    });
  } catch (error) {
    res.status(400).json({
      success: false,
      message: error.message
    });
  }
};

// DELETE elimina libro
const deleteBook = (req, res) => {
  const index = database.books.findIndex(b => b.id === req.params.id);

  if (index === -1) {
    return res.status(404).json({
      success: false,
      message: 'Libro non trovato'
    });
  }

  database.books.splice(index, 1);

  res.json({
    success: true,
    message: 'Libro eliminato con successo'
  });
};

module.exports = {
  getAllBooks,
  getBookById,
  getBooksByAuthor,
  createBook,
  updateBook,
  deleteBook
};
