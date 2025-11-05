const { v4: uuidv4 } = require('uuid');

class Author {
  constructor(name, biography, birthDate, nationality) {
    this.id = uuidv4();
    this.name = name;
    this.biography = biography || '';
    this.birthDate = birthDate || null;
    this.nationality = nationality || '';
    this.createdAt = new Date().toISOString();
  }

  validate() {
    if (!this.name || this.name.trim() === '') {
      throw new Error('Il nome dell\'autore è obbligatorio');
    }
    return true;
  }
}

module.exports = Author;
