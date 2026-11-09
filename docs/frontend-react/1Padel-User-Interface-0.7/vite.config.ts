import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins:[react({jsxImportSource:'@padel/i18n'}), {
    name:'local-i18n-runtime', enforce:'post',
    configResolved(config) {
      // Keep one locale store in dev: never bundle a second copy inside the JSX adapter.
      config.optimizeDeps.include=(config.optimizeDeps.include||[]).filter(id=>!id.startsWith('@padel/i18n'));
      config.optimizeDeps.include.push('react/jsx-runtime','react/jsx-dev-runtime');
    }
  }],
  resolve:{alias:{'@padel/i18n':new URL('./src/i18n',import.meta.url).pathname}},
  optimizeDeps:{exclude:['@padel/i18n','@padel/i18n/jsx-runtime','@padel/i18n/jsx-dev-runtime']},
  base:'./',build:{outDir:'dist',emptyOutDir:true}
});
