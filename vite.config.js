import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        // Determine target based on Codespaces environment variables
        target: (() => {
          const codespaceNameEnv = process.env.CODESPACE_NAME;
          const portForwardingDomain = process.env.GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN;
          
          if (codespaceNameEnv && portForwardingDomain) {
            const backendUrl = `https://${codespaceNameEnv}-8080.${portForwardingDomain}`;
            console.log(`[Vite Proxy] Codespaces detected: Using ${backendUrl}`);
            return backendUrl;
          }
          
          console.log('[Vite Proxy] Local environment detected: Using http://localhost:8080');
          return 'http://localhost:8080';
        })(),
        changeOrigin: true,
        secure: false, // Accept self-signed certs in dev
      },
    },
  },
})
