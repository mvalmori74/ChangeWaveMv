const { v4: uuidv4 } = require('uuid');

class Book {
  constructor(title, authorId, isbn, publishedYear, genre, pages, description) {
    this.id = uuidv4();
    this.title = title;
    this.authorId = authorId;
    this.isbn = isbn || '';
    this.publishedYear = publishedYear || null;
    this.genre = genre || '';
    this.pages = pages || 0;
    this.description = description || '';
    this.createdAt = new Date().toISOString();
  }

  validate() {
    if (!this.title || this.title.trim() === '') {
      throw new Error('Il titolo del libro è obbligatorio');
    }
    if (!this.authorId) {
      throw new Error('L\'ID dell\'autore è obbligatorio');
    }
    return true;
  }
}

module.exports = Book;
