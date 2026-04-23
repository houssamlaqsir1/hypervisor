import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  /** sockjs-client expects Node's `global` — without this, the bundle can throw at load time → blank page. */
  define: {
    global: 'globalThis',
  },
  server: {
    host: true,
    port: 5173,
    /** Allow opening dev server via LAN URLs (Network: http://192.168.x.x:5173). */
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Do not proxy /ws: SockJS long-polling through Vite spams ECONNABORTED.
      // The browser connects directly to Spring (see src/api/client.ts WS_URL).
    },
  },
})
