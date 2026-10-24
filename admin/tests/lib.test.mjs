import test from 'node:test';
import assert from 'node:assert/strict';
import {createApi,ApiError,nextStatus,safeImageUrl,money} from '../lib.mjs';

const json = (body,status=200) => new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
test('payment and preparation are separate; unpaid or terminal orders have no next action',() => {
  assert.equal(nextStatus({status:'created',payment_status:'unpaid'}),null);
  assert.equal(nextStatus({status:'created',payment_status:'paid'}),'accepted');
  assert.equal(nextStatus({status:'accepted',payment_status:'paid'}),'preparing');
  assert.equal(nextStatus({status:'preparing',payment_status:'paid'}),'ready');
  assert.equal(nextStatus({status:'ready',payment_status:'paid'}),'completed');
  for (const status of ['completed','cancelled','unknown']) assert.equal(nextStatus({status,payment_status:'paid'}),null);
});
test('image URLs reject script, insecure remote and path traversal sources',() => {
  for (const value of ['javascript:alert(1)','data:image/svg+xml,x','http://example.com/a.png','//evil.example/a.png','/uploads/../secrets','/admin/users',null]) assert.equal(safeImageUrl(value),null);
  assert.equal(safeImageUrl('/uploads/a.png'),'/uploads/a.png'); assert.equal(safeImageUrl('https://example.com/a.png'),'https://example.com/a.png');
});
test('prices are displayed in whole KZT',() => { assert.match(money(1290),/1\s290 ₸/); });
test('API sends authenticated JSON and preserves creation precondition',async () => {
  let captured;
  const api = createApi({fetchImpl:async (url,options) => { captured = {url,options}; return json({id:20}); }}); api.authenticate('test-token');
  assert.deepEqual(await api.request('admin/products/20',{method:'PUT',body:{name:'Coffee'},headers:{'If-None-Match':'*'}}),{id:20});
  assert.equal(captured.url,'/api/v1/admin/products/20'); assert.equal(captured.options.headers.Authorization,'Bearer test-token'); assert.equal(captured.options.headers['If-None-Match'],'*'); assert.equal(captured.options.body,'{"name":"Coffee"}');
});
test('expired session clears token and invokes expiry once',async () => {
  let expired = 0; const headers = [];
  const api = createApi({onExpired:() => expired++,fetchImpl:async (url,options) => { headers.push(options.headers); return json({error:{code:'unauthorized',message:'Сессия истекла'}},401); }});
  api.authenticate('expired'); await assert.rejects(api.request('auth/me'),error => error instanceof ApiError && error.status === 401); await assert.rejects(api.request('auth/me'));
  assert.equal(expired,1); assert.equal(headers[0].Authorization,'Bearer expired'); assert.equal(headers[1].Authorization,undefined);
});
test('logout invalidates an older request even if transport resolves after abort',async () => {
  let resolve;
  const api = createApi({fetchImpl:() => new Promise(done => { resolve = done; })}); api.authenticate('previous');
  const pending = api.request('staff/orders'); api.clear(); api.authenticate('next-account'); resolve(json({orders:[{id:'private-order'}]}));
  await assert.rejects(pending,error => error.code === 'stale_session');
});
test('an old unauthorized response cannot expire the next account',async () => {
  let resolve; let expired = 0; let call = 0; const sent = [];
  const api = createApi({onExpired:() => expired++,fetchImpl:(url,options) => { sent.push(options.headers.Authorization); return call++ === 0 ? new Promise(done => { resolve = done; }) : Promise.resolve(json({id:'next-account'})); }});
  api.authenticate('previous'); const pending = api.request('auth/me'); api.authenticate('next'); resolve(new Response('unauthorized',{status:401}));
  await assert.rejects(pending,error => error.code === 'stale_session'); assert.equal(expired,0); assert.deepEqual(await api.request('auth/me'),{id:'next-account'}); assert.equal(sent[1],'Bearer next');
});
test('network errors and timeouts never return a successful mutation',async () => {
  const offline = createApi({fetchImpl:async () => { throw new TypeError('offline'); }});
  await assert.rejects(offline.request('staff/orders/1/payment',{method:'POST',body:{}}),error => error.code === 'network');
  const delayed = createApi({timeoutMs:5,fetchImpl:(url,{signal}) => new Promise((resolve,reject) => signal.addEventListener('abort',() => reject(new DOMException('aborted','AbortError'))))});
  await assert.rejects(delayed.request('staff/orders/1/payment',{method:'POST',body:{}}),error => error.code === 'timeout');
});
test('empty 204 logout response is accepted; unexpected HTML and structured errors surface',async () => {
  assert.equal(await createApi({fetchImpl:async () => new Response(null,{status:204})}).request('auth/logout',{method:'POST',body:{}}),null);
  await assert.rejects(createApi({fetchImpl:async () => new Response('<html>bad gateway</html>',{status:502})}).request('staff/orders'),/неожиданный/);
  await assert.rejects(createApi({fetchImpl:async () => json({error:{message:'QR просрочен',code:'qr_expired'}},409)}).request('staff/orders/lookup',{method:'POST',body:{code:'x'}}),error => error.code === 'qr_expired' && error.message === 'QR просрочен');
});
