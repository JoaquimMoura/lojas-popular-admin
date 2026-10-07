// Emula o proxy reverso de produção (Traefik) para a validação LOCAL:
//   /api e /uploads -> backend;  qualquer outra rota -> build estático do frontend (fallback SPA).
// Node puro, sem dependências. Configuração por variáveis de ambiente:
//   LP_FRONT_DIST    pasta do build do frontend (obrigatória; também aceita como 1º argumento)
//   LP_BACKEND_PORT  porta do backend            (padrão 18090)
//   LP_PROXY_PORT    porta deste proxy           (padrão 4180)
// Cabeçalhos (Authorization, Idempotency-Key, Content-Type com boundary) e o corpo (JSON ou multipart)
// são repassados sem alteração, em streaming.
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');

const DIST = path.resolve(process.env.LP_FRONT_DIST || process.argv[2] || '');
const BACK = parseInt(process.env.LP_BACKEND_PORT || '18090', 10);
const PORT = parseInt(process.env.LP_PROXY_PORT || '4180', 10);

const TIPOS = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css',
  '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg', '.webp': 'image/webp', '.ico': 'image/x-icon', '.woff2': 'font/woff2',
  '.woff': 'font/woff', '.map': 'application/json',
};

function paraBackend(req, res) {
  const p = http.request(
    { host: '127.0.0.1', port: BACK, path: req.url, method: req.method, headers: req.headers },
    (r) => {
      res.writeHead(r.statusCode, r.headers);
      r.pipe(res);
    });
  p.on('error', (e) => {
    if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Backend indisponível: ' + e.message);
  });
  req.pipe(p);
}

function estatico(req, res) {
  let rel;
  try { rel = decodeURIComponent(req.url.split('?')[0]); } catch (e) { rel = '/'; }
  let f = path.join(DIST, rel);
  const dentro = f === DIST || f.startsWith(DIST + path.sep);
  if (!dentro || !fs.existsSync(f) || fs.statSync(f).isDirectory()) f = path.join(DIST, 'index.html');
  if (!fs.existsSync(f)) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    return res.end('Build do frontend não encontrado em ' + DIST);
  }
  res.writeHead(200, { 'Content-Type': TIPOS[path.extname(f)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
}

function tratar(req, res) {
  if (req.url.startsWith('/api') || req.url.startsWith('/uploads')) return paraBackend(req, res);
  return estatico(req, res);
}

// Escuta em 127.0.0.1 e, se disponível, em ::1: "localhost" resolve para o IPv6 primeiro no Windows e,
// sem este segundo listener, cada conexão perde centenas de milissegundos na tentativa recusada.
http.createServer(tratar).listen(PORT, '127.0.0.1', () => {
  console.log('proxy em http://localhost:' + PORT + ' -> backend :' + BACK + ' | frontend: ' + DIST);
});
const v6 = http.createServer(tratar);
v6.on('error', () => { /* IPv6 indisponível: segue só com IPv4 */ });
v6.listen(PORT, '::1');
