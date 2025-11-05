const Author = require('../models/Author');
const database = require('../database');

// GET tutti gli autori
const getAllAuthors = (req, res) => {
  res.json({
    success: true,
    count: database.authors.length,
    data: database.authors
  });
};

// GET autore per ID
const getAuthorById = (req, res) => {
  const author = database.authors.find(a => a.id === req.params.id);

  if (!author) {
    return res.status(404).json({
      success: false,
      message: 'Autore non trovato'
    });
  }

  res.json({
    success: true,
    data: author
  });
};

// POST crea nuovo autore
const createAuthor = (req, res) => {
  try {
    const { name, biography, birthDate, nationality } = req.body;
    const author = new Author(name, biography, birthDate, nationality);

    author.validate();
    database.authors.push(author);

    res.status(201).json({
      success: true,
      message: 'Autore creato con successo',
      data: author
    });
  } catch (error) {
    res.status(400).json({
      success: false,
      message: error.message
    });
  }
};

// PUT aggiorna autore
const updateAuthor = (req, res) => {
  const index = database.authors.findIndex(a => a.id === req.params.id);

  if (index === -1) {
    return res.status(404).json({
      success: false,
      message: 'Autore non trovato'
    });
  }

  try {
    const { name, biography, birthDate, nationality } = req.body;
    const updatedAuthor = {
      ...database.authors[index],
      name: name || database.authors[index].name,
      biography: biography !== undefined ? biography : database.authors[index].biography,
      birthDate: birthDate !== undefined ? birthDate : database.authors[index].birthDate,
      nationality: nationality !== undefined ? nationality : database.authors[index].nationality
    };

    database.authors[index] = updatedAuthor;

    res.json({
      success: true,
      message: 'Autore aggiornato con successo',
      data: updatedAuthor
    });
  } catch (error) {
    res.status(400).json({
      success: false,
      message: error.message
    });
  }
};

// DELETE elimina autore
const deleteAuthor = (req, res) => {
  const index = database.authors.findIndex(a => a.id === req.params.id);

  if (index === -1) {
    return res.status(404).json({
      success: false,
      message: 'Autore non trovato'
    });
  }

  // Verifica se ci sono libri associati a questo autore
  const booksWithAuthor = database.books.filter(b => b.authorId === req.params.id);
  if (booksWithAuthor.length > 0) {
    return res.status(400).json({
      success: false,
      message: 'Impossibile eliminare l\'autore: ci sono libri associati'
    });
  }

  database.authors.splice(index, 1);

  res.json({
    success: true,
    message: 'Autore eliminato con successo'
  });
};

module.exports = {
  getAllAuthors,
  getAuthorById,
  createAuthor,
  updateAuthor,
  deleteAuthor
};
