import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';
import type { Plugin } from 'vite';

function previewHistoryFallback(): Plugin {
  return {
    name: 'preview-history-fallback',
    configureServer(server) {
      server.middlewares.use((request, _response, next) => {
        if (request.url?.startsWith('/preview')) request.url = '/web/';
        next();
      });
    },
  };
}

export default defineConfig({
  base: '/web/',
  plugins: [previewHistoryFallback(), react()],
  build: {
    outDir: '../target/generated-resources/frontend/static/web',
    emptyOutDir: true,
    sourcemap: true,
  },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/login': 'http://127.0.0.1:8080',
      '/logout': 'http://127.0.0.1:8080',
      '/oauth2': 'http://127.0.0.1:8080',
      '/login/oauth2': 'http://127.0.0.1:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
    globals: true,
    css: true,
  },
});
