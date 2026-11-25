// Serves dist-artifact/ the way a hosted artifact runs: the fragment wrapped in a document skeleton, under a strict
// Content-Security-Policy (inline scripts only, no eval; WebAssembly only with "wasm"), so the fallbacks get exercised.
// Usage: node test/artifact-server.cjs <port> [strict|wasm]
const http = require('http'), fs = require('fs'), path = require('path');
const [port = '8796', mode = 'strict'] = process.argv.slice(2), root = path.join(__dirname, '../dist-artifact');
const csp = ["default-src 'none'", `script-src 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net https://unpkg.com${mode === 'wasm' ? " 'wasm-unsafe-eval'" : ''}`,
  "style-src 'unsafe-inline' https://fonts.googleapis.com", 'font-src https://fonts.gstatic.com', "img-src 'self' data: blob:", "connect-src 'self'", "worker-src 'self' blob:", "media-src 'self' blob: data:"].join('; ');
const skeleton = body => `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"><style>:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0;font:14px system-ui,sans-serif}img{max-width:100%}[hidden]{display:none!important}</style></head><body>${body}</body></html>`;
const types = { '.json': 'application/json', '.wasm': 'application/wasm', '.html': 'text/html; charset=utf-8' };
http.createServer((req, res) => {
  let p = decodeURIComponent(req.url.split('?')[0]); if (p === '/' ) p = '/index.html';
  const f = path.join(root, path.normalize(p)); if (!f.startsWith(root) || !fs.existsSync(f)) { res.writeHead(404); return res.end(); }
  res.setHeader('Content-Security-Policy', csp); res.setHeader('Content-Type', types[path.extname(f)] || 'application/octet-stream');
  res.end(p === '/index.html' ? skeleton(fs.readFileSync(f, 'utf8')) : fs.readFileSync(f));
}).listen(+port, () => console.log(`artifact (${mode}) on ${port}`));
