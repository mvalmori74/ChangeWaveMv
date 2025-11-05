// Database in-memory per semplicità
// In produzione, si userebbe un database vero come MongoDB, PostgreSQL, etc.

const database = {
  authors: [],
  books: []
};

module.exports = database;
