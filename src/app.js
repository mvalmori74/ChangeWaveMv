const express = require('express');
const bodyParser = require('body-parser');
const authorsRoutes = require('./routes/authorsRoutes');
const booksRoutes = require('./routes/booksRoutes');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));

// Logging middleware
app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} - ${req.method} ${req.path}`);
  next();
});

// Routes
app.get('/', (req, res) => {
  res.json({
    message: 'Benvenuto nell\'API della Libreria',
    version: '1.0.0',
    endpoints: {
      authors: '/api/authors',
      books: '/api/books'
    }
  });
});

app.use('/api/authors', authorsRoutes);
app.use('/api/books', booksRoutes);

// 404 Handler
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: 'Endpoint non trovato'
  });
});

// Error Handler
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({
    success: false,
    message: 'Errore del server',
    error: process.env.NODE_ENV === 'development' ? err.message : undefined
  });
});

// Avvia il server
app.listen(PORT, () => {
  console.log(`Server in esecuzione sulla porta ${PORT}`);
  console.log(`API disponibile su http://localhost:${PORT}`);
});

module.exports = app;
