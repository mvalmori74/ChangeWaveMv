const express = require('express');
const router = express.Router();
const booksController = require('../controllers/booksController');

// Route per i libri
router.get('/', booksController.getAllBooks);
router.get('/:id', booksController.getBookById);
router.get('/author/:authorId', booksController.getBooksByAuthor);
router.post('/', booksController.createBook);
router.put('/:id', booksController.updateBook);
router.delete('/:id', booksController.deleteBook);

module.exports = router;
