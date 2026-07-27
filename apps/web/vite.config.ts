import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    // The API base URL is configurable, but proxying /api in dev keeps the
    // browser on a single origin so nothing depends on CORS locally.
    proxy: {
      '/api': { target: process.env['VITE_API_BASE_URL'] ?? 'http://localhost:3000', changeOrigin: true },
      '/health': { target: process.env['VITE_API_BASE_URL'] ?? 'http://localhost:3000', changeOrigin: true },
    },
  },
  build: { outDir: 'dist', sourcemap: true },
});
