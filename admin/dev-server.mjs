// Local development fallback. Production serves these same files through nginx.
import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('.',import.meta.url));
const files = new Map([
  ['/',['index.html','text/html; charset=utf-8']],
  ['/index.html',['index.html','text/html; charset=utf-8']],
  ['/styles.css',['styles.css','text/css; charset=utf-8']],
  ['/app.mjs',['app.mjs','application/javascript; charset=utf-8']],
  ['/lib.mjs',['lib.mjs','application/javascript; charset=utf-8']],
  ['/assets/master-logo.png',['assets/master-logo.png','image/png']],
  ['/assets/montserrat-regular.ttf',['assets/montserrat-regular.ttf','font/ttf']],
  ['/assets/montserrat-bold.ttf',['assets/montserrat-bold.ttf','font/ttf']],
]);
const hopHeaders = new Set(['connection','keep-alive','proxy-authenticate','proxy-authorization','te','trailer','transfer-encoding','upgrade']);
const maximumBody = 5 * 1024 * 1024;
const csp = "default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self'; img-src 'self' https:; connect-src 'self'; media-src 'self' blob:; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";
function headers() { return {'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','X-Frame-Options':'DENY','Referrer-Policy':'no-referrer','Permissions-Policy':'camera=(self), microphone=(), geolocation=()','Content-Security-Policy':csp}; }
function fail(response,status,message) { if (response.writableEnded || response.headersSent) return; response.writeHead(status,{'Content-Type':'application/json; charset=utf-8'}); response.end(JSON.stringify({error:{code:`local_${status}`,message}})); }

export function createDevServer({apiPort = 8080} = {}) {
  return http.createServer(async (request,response) => {
    for (const [name,value] of Object.entries(headers())) response.setHeader(name,value);
    let url;
    try { url = new URL(request.url,'http://localhost'); } catch { fail(response,400,'Invalid URL'); return; }
    // URL parsing canonicalizes literal traversal; the allowlist below also rejects encoded traversal.
    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/uploads/')) {
      if (Number(request.headers['content-length'] || 0) > maximumBody) { fail(response,413,'Maximum upload is 5 MiB'); request.resume(); return; }
      const forwarded = {};
      for (const [name,value] of Object.entries(request.headers)) if (!hopHeaders.has(name) && value !== undefined) forwarded[name] = value;
      forwarded.host = `127.0.0.1:${apiPort}`;
      forwarded['x-forwarded-for'] = request.socket.remoteAddress;
      forwarded['x-forwarded-proto'] = 'http';
      const upstream = http.request({hostname:'127.0.0.1',port:apiPort,path:url.pathname+url.search,method:request.method,headers:forwarded},reply => {
        if (response.writableEnded) { reply.resume(); return; }
        for (const [name,value] of Object.entries(reply.headers)) if (!hopHeaders.has(name) && value !== undefined) response.setHeader(name,value);
        response.writeHead(reply.statusCode || 502); reply.pipe(response);
        reply.on('error',() => { if (!response.writableEnded) response.destroy(); });
      });
      upstream.setTimeout(20000,() => { upstream.destroy(); fail(response,504,'Local API timed out'); });
      upstream.on('error',() => fail(response,502,'Local API is unavailable. Start it on 127.0.0.1:8080.'));
      let size = 0;
      request.on('data',chunk => { size += chunk.length; if (size > maximumBody) { request.unpipe(upstream); upstream.destroy(); fail(response,413,'Maximum upload is 5 MiB'); request.resume(); } });
      request.on('aborted',() => upstream.destroy());
      response.on('close',() => { if (!response.writableEnded) upstream.destroy(); });
      request.pipe(upstream); return;
    }
    if (!['GET','HEAD'].includes(request.method)) { response.setHeader('Allow','GET, HEAD'); fail(response,405,'Method not allowed'); return; }
    const file = files.get(url.pathname);
    if (!file) { fail(response,404,'File not found'); return; }
    try {
      const content = await readFile(path.join(root,file[0]));
      response.writeHead(200,{'Content-Type':file[1],'Content-Length':content.length});
      response.end(request.method === 'HEAD' ? undefined : content);
    } catch { fail(response,500,'Static file is unavailable'); }
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const server = createDevServer();
  server.on('error',error => { process.stderr.write(`Cannot start local cabinet: ${error.code || 'server error'}\n`); process.exitCode = 1; });
  server.listen(8081,'127.0.0.1',() => process.stdout.write('Master Coffee cabinet: http://localhost:8081 (API: 127.0.0.1:8080)\n'));
  for (const signal of ['SIGINT','SIGTERM']) process.on(signal,() => server.close());
}
