import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';

const base = (process.env.API_URL || 'http://localhost:8080').replace(/\/$/, '');
const password = process.env.DEMO_PASSWORD;
assert(password?.length >= 12, 'Set DEMO_PASSWORD to the local development seed password');
const checks = [];
async function request(path, { token, method = 'GET', body, key, expected = 200 } = {}) {
  const response = await fetch(`${base}${path}`, {
    method, signal: AbortSignal.timeout(25000),
    headers: { ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(key ? { 'Idempotency-Key': key } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const data = response.status === 204 ? null : await response.json();
  assert([expected].flat().includes(response.status), `${method} ${path}: expected ${expected}, got ${response.status} ${data?.error?.code || ''}`);
  return data;
}
const api = '/api/v1';
const login = email => request(`${api}/auth/login`, { method: 'POST', body: { email, password } });
await request('/healthz'); await request('/readyz'); checks.push('health/readiness');
const catalog = await request(`${api}/catalog?cafe_id=1`);
assert(catalog.cafes.length > 0 && catalog.products.length > 0);
const product = catalog.products.find(p => p.available && p.is_drink && p.loyalty_eligible);
assert(product?.sizes.length); checks.push('seed catalog and cafes');
const email = `e2e-${randomUUID()}@example.test`;
const customer = await request(`${api}/auth/register`, { method: 'POST', expected: 201, body: { email, password, name: 'E2E customer' } });
assert.equal(customer.user.role, 'customer'); assert.equal(customer.user.email_verified, false);
const other = await request(`${api}/auth/register`, { method: 'POST', expected: 201, body: { email: `other-${randomUUID()}@example.test`, password, name: 'Other account' } });
const staff = await login('staff@mastercoffee.local');
const admin = await login('admin@mastercoffee.local');
const me = await request(`${api}/auth/me`, { token: customer.token });
assert.equal(me.id, customer.user.id); checks.push('registration/login/session identity');
await request(`${api}/staff/orders`, { token: customer.token, expected: 403 });
await request(`${api}/admin/catalog`, { token: staff.token, expected: 403 });
await request(`${api}/auth/register`, { method: 'POST', expected: 400, body: { email: 'role@example.test', name: 'Attack', password, role: 'admin' } });
checks.push('server role enforcement');
const input = { cafe_id: 1, items: [{ product_id: product.id, size_id: product.sizes[0].id, quantity: 2, addon_ids: [], sugar: 1, order_type: 'takeaway', temperature: 'standard' }], comment: 'E2E: payment at counter' };
await request(`${api}/orders/quote`, { method: 'POST', token: customer.token, body: { ...input, total: 1 }, expected: 400 });
const quote = await request(`${api}/orders/quote`, { method: 'POST', token: customer.token, body: input, expected: 201 });
assert.equal(quote.currency, 'KZT'); assert.equal(quote.money_unit, 'whole_kzt'); assert.equal(quote.total, quote.subtotal - quote.discount);
checks.push('server quote and forged total rejection');
const key = randomUUID();
const orders = await Promise.all(Array.from({ length: 8 }, () => request(`${api}/orders`, { method: 'POST', token: customer.token, key, body: { quote_id: quote.id }, expected: [200, 201] })));
const order = orders[0]; assert(orders.every(o => o.id === order.id)); assert.equal(order.total, quote.total);
assert.equal(order.status, 'created'); assert.equal(order.payment_status, 'unpaid'); assert(order.qr?.token?.length >= 32);
const nextQuote = await request(`${api}/orders/quote`, { method: 'POST', token: customer.token, body: input, expected: 201 });
await request(`${api}/orders`, { method: 'POST', token: customer.token, key, body: { quote_id: nextQuote.id }, expected: 409 });
await request(`${api}/orders/${order.id}`, { token: other.token, expected: 404 });
checks.push('concurrent idempotency, payload conflict, account isolation');
const queue = await request(`${api}/staff/orders?cafe_id=1`, { token: staff.token });
assert(queue.orders.some(o => o.id === order.id));
const found = await request(`${api}/staff/orders/lookup`, { method: 'POST', token: staff.token, body: { code: order.qr.payload } });
assert.equal(found.id, order.id);
await request(`${api}/staff/orders/${order.id}/payment`, { method: 'POST', token: customer.token, body: { qr_token: order.qr.token }, expected: 403 });
await request(`${api}/staff/orders/${order.id}/status`, { method: 'PATCH', token: staff.token, body: { status: 'completed' }, expected: 409 });
const paid = await request(`${api}/staff/orders/${order.id}/payment`, { method: 'POST', token: staff.token, body: { qr_token: order.qr.token } });
assert.equal(paid.payment_status, 'paid');
await request(`${api}/staff/orders/${order.id}/payment`, { method: 'POST', token: staff.token, body: { qr_token: order.qr.token }, expected: 409 });
checks.push('staff queue, QR lookup, cash payment, replay rejection');
for (const status of ['accepted', 'preparing', 'ready']) {
  await request(`${api}/staff/orders/${order.id}/status`, { method: 'PATCH', token: staff.token, body: { status } });
  const observed = await request(`${api}/orders/${order.id}`, { token: customer.token }); assert.equal(observed.status, status);
}
const before = await request(`${api}/loyalty`, { token: customer.token }); assert.equal(before.qualifying_orders, 0);
await request(`${api}/staff/orders/${order.id}/status`, { method: 'PATCH', token: staff.token, body: { status: 'completed' } });
// The same completed state may be an idempotent success or an explicit transition conflict.
await request(`${api}/staff/orders/${order.id}/status`, { method: 'PATCH', token: staff.token, body: { status: 'completed' }, expected: [200, 409] });
const after = await request(`${api}/loyalty`, { token: customer.token }); assert.equal(after.qualifying_orders, 1);
const history = await request(`${api}/orders?limit=1`, { token: customer.token }); assert.equal(history.orders[0].id, order.id); assert.equal(history.orders[0].status, 'completed');
assert.equal((await request(`${api}/orders`, { token: other.token })).orders.length, 0);
checks.push('preparation/readiness/delivery observed by customer, history and exactly-once loyalty');
assert((await request(`${api}/admin/catalog`, { token: admin.token })).products.length > 0);
await request(`${api}/auth/logout`, { method: 'POST', token: customer.token, expected: 204 });
await request(`${api}/auth/me`, { token: customer.token, expected: 401 });
const restored = await login(email); assert.equal(restored.user.id, me.id);
await request(`${api}/auth/logout`, { method: 'POST', token: restored.token, expected: 204 });
await request(`${api}/auth/logout`, { method: 'POST', token: other.token, expected: 204 });
await request(`${api}/auth/logout`, { method: 'POST', token: staff.token, expected: 204 });
await request(`${api}/auth/logout`, { method: 'POST', token: admin.token, expected: 204 });
checks.push('logout revocation and fresh login');
console.log(JSON.stringify({ result: 'PASS', order: order.number, checks }, null, 2));
