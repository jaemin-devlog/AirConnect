// Test-only proxy. Never reads env files; never targets production or :8080.
// The product app is now production-only. Override the fixed origin ONLY in this
// explicit verification server, and enforce same-origin networking as a second guard.
import { pathToFileURL } from 'node:url';
import path from 'node:path';
const root = path.resolve('C:/Users/정재민/Project/airconnect-admin');
const { createServer } = await import(pathToFileURL(path.join(root, 'node_modules/vite/dist/node/index.js')));
const server = await createServer({
  root, configFile: path.join(root, 'vite.config.ts'), envDir: false,
  plugins: [{
    name: 'isolated-report-transport', enforce: 'pre',
    transform(code, id) {
      if (id.split('?')[0].replaceAll('\\', '/') !== path.join(root, 'lib/admin/runtime.ts').replaceAll('\\', '/')) return;
      const expected = "export const API_ORIGIN = 'https://airconnect.cloud';";
      if (code.split(expected).length !== 2) throw new Error('Runtime contract changed; isolated verification must be updated before use.');
      return { code: code.replace(expected, "export const API_ORIGIN = 'http://127.0.0.1:15189';"), map: null };
    },
    transformIndexHtml(html) {
      return html.replace('<title>AirConnect · 운영 관리자</title>', '<title>격리 검증 전용 · 운영 연결 없음</title>');
    },
  }],
  server: { host: '127.0.0.1', port: 15189, strictPort: true,
    headers: {
      // Vite's local React refresh preamble is inline. Network access remains
      // restricted to this loopback origin; this header is never shipped to production.
      'Content-Security-Policy': "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self' ws://127.0.0.1:15189; object-src 'none'; form-action 'none'; frame-ancestors 'none'",
      'Cache-Control': 'no-store',
    },
    proxy: { '/api/v1': { target: 'http://127.0.0.1:18089', changeOrigin: true } } },
});
await server.listen();
server.printUrls();
console.log('ISOLATED REPORT VERIFICATION: synthetic MySQL fixtures only, no production API.');
