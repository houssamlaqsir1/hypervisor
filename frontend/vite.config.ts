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
    /**
     * Filesystem events do not cross a bind mount from a Windows host into
     * a Linux container, so a dev server running that way never learns a
     * file changed and keeps serving the module it transformed at startup.
     * Polling costs a little CPU and is only switched on when asked for.
     */
    watch: process.env.VITE_DEV_POLL ? { usePolling: true, interval: 300 } : undefined,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      /**
       * MediaMTX serves HLS on :8888 by default. Browsers block cross-origin
       * fetches for .m3u8 + .ts segments unless CORS is configured on MediaMTX.
       * In dev, proxy through Vite so /hls-media/... is same-origin as the app.
       */
      '/hls-media': {
        target: process.env.VITE_DEV_HLS_TARGET ?? 'http://127.0.0.1:8888',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/hls-media/, ''),
      },
      // Do not proxy /ws: SockJS long-polling through Vite spams ECONNABORTED.
      // The browser connects directly to Spring (see src/api/client.ts WS_URL).
    },
  },
})
