import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {once} from 'node:events';
import {createDevServer} from '../dev-server.mjs';

async function listen(server) { server.listen(0,'127.0.0.1'); await once(server,'listening'); return server.address().port; }
async function close(server) { await new Promise(resolve => server.close(resolve)); }

test('development server serves only the static allowlist with security headers',async () => {
  const server = createDevServer(); const port = await listen(server);
  try {
    const response = await fetch(`http://127.0.0.1:${port}/`); assert.equal(response.status,200); assert.match(await response.text(),/Master Coffee/); assert.match(response.headers.get('content-security-policy'),/script-src 'self'/); assert.equal(response.headers.get('x-content-type-options'),'nosniff');
    const module = await fetch(`http://127.0.0.1:${port}/app.mjs`,{method:'HEAD'}); assert.equal(module.status,200); assert.match(module.headers.get('content-type'),/application\/javascript/); assert.equal(await module.text(),'');
    for (const route of ['/dev-server.mjs','/tests/lib.test.mjs','/.env','/%2e%2e/backend/go.mod','/assets/%2f..%2f..%2f.env']) assert.equal((await fetch(`http://127.0.0.1:${port}${route}`)).status,404,route);
    assert.equal((await fetch(`http://127.0.0.1:${port}/`,{method:'POST'})).status,405);
  } finally { await close(server); }
});
test('local API proxy preserves authorization and body without changing response semantics',async () => {
  let captured;
  const upstream = http.createServer(async (request,response) => { let body=''; for await (const chunk of request) body += chunk; captured = {path:request.url,authorization:request.headers.authorization,body}; response.writeHead(201,{'Content-Type':'application/json'}); response.end('{"status":"created"}'); });
  const apiPort = await listen(upstream); const server = createDevServer({apiPort}); const port = await listen(server);
  try {
    const response = await fetch(`http://127.0.0.1:${port}/api/v1/orders?limit=50`,{method:'POST',headers:{Authorization:'Bearer local-test-token','Content-Type':'application/json'},body:'{"quote_id":"test"}'});
    assert.equal(response.status,201); assert.deepEqual(await response.json(),{status:'created'}); assert.deepEqual(captured,{path:'/api/v1/orders?limit=50',authorization:'Bearer local-test-token',body:'{"quote_id":"test"}'});
  } finally { await close(server); await close(upstream); }
});
test('oversized uploads fail before reaching the API and unavailable API gives explicit 502',async () => {
  const upstream = http.createServer((request,response) => response.end()); const apiPort = await listen(upstream); await close(upstream);
  const server = createDevServer({apiPort}); const port = await listen(server);
  try {
    const status = await new Promise((resolve,reject) => { const request = http.request({host:'127.0.0.1',port,path:'/api/v1/admin/images',method:'POST',headers:{'Content-Length':5*1024*1024+1}},response => { response.resume(); response.on('end',() => resolve(response.statusCode)); }); request.on('error',reject); request.end(); });
    assert.equal(status,413);
    const response = await fetch(`http://127.0.0.1:${port}/api/v1/catalog`); assert.equal(response.status,502); assert.equal((await response.json()).error.code,'local_502');
  } finally { await close(server); }
});
