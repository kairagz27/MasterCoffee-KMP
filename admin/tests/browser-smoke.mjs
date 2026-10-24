// Optional real-localhost UI test. Requires Playwright and an installed browser.
// No browser profile, user cookies or storage are reused.
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';
import path from 'node:path';

const adminRoot = fileURLToPath(new URL('../',import.meta.url));
const origin = process.env.ADMIN_URL || 'http://localhost:8081';
if (!['localhost','127.0.0.1','[::1]'].includes(new URL(origin).hostname)) throw new Error('Browser smoke test is restricted to localhost development.');
const password = process.env.DEMO_PASSWORD;
if (!password) throw new Error('Set DEMO_PASSWORD for local development accounts.');
const modulePath = process.env.PLAYWRIGHT_MODULE;
const {chromium} = modulePath ? await import(pathToFileURL(modulePath).href) : await import('playwright');
const browser = await chromium.launch({headless:true,...(process.env.BROWSER_EXECUTABLE ? {executablePath:process.env.BROWSER_EXECUTABLE} : {})});
const context = await browser.newContext({viewport:{width:1440,height:900},locale:'ru-RU',colorScheme:'light',reducedMotion:'reduce'});
const page = await context.newPage();
const evidence = path.join(adminRoot,'evidence'); await mkdir(evidence,{recursive:true});
const errors = []; const sessions = [];
page.on('pageerror',error => errors.push(error.message));
async function request(route,{token,method='GET',body,key} = {}) {
  const response = await fetch(`${origin}/api/v1/${route}`,{method,headers:{'Content-Type':'application/json',...(token ? {Authorization:`Bearer ${token}`} : {}),...(key ? {'Idempotency-Key':key} : {})},...(body === undefined ? {} : {body:JSON.stringify(body)})});
  const data = response.status === 204 ? null : await response.json();
  assert.ok(response.ok,`${method} ${route}: ${response.status} ${data?.error?.code || ''}`); return data;
}
async function login(role) {
  await page.getByLabel('Email',{exact:true}).fill(`${role}@mastercoffee.local`);
  await page.getByLabel('Пароль',{exact:true}).fill(password);
  const result = page.waitForResponse(response => response.url().endsWith('/api/v1/auth/login') && response.request().method() === 'POST');
  await page.getByRole('button',{name:'Войти',exact:true}).click();
  const session = await (await result).json(); sessions.push(session.token); return session;
}
async function screenshot(name) { await page.screenshot({path:path.join(evidence,name),fullPage:true}); }
async function noHorizontalOverflow() { assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),true,'Layout must fit viewport'); }
try {
  await page.goto(origin); await page.getByRole('heading',{name:'Войти в кабинет'}).waitFor();
  await screenshot('login-1440x900.png'); await noHorizontalOverflow();
  await page.getByLabel('Email',{exact:true}).focus(); await page.keyboard.press('Tab'); assert.equal(await page.getByLabel('Пароль',{exact:true}).evaluate(node => node === document.activeElement),true,'Keyboard traverses labeled login controls');
  const staff = await login('staff');
  await page.getByRole('heading',{name:'Очередь заказов',exact:true}).waitFor();
  await page.getByText('Обновлено ',{exact:false}).waitFor();
  const initiallyEmpty = await page.getByText('Сейчас активных заказов нет. Новые появятся здесь автоматически.',{exact:true}).isVisible();
  if (initiallyEmpty) await screenshot('empty-1440x900.png');
  await page.route('**/api/v1/staff/orders?limit=50',async route => { await new Promise(resolve => setTimeout(resolve,600)); await route.continue(); });
  await page.getByRole('button',{name:'Обновить',exact:true}).click(); await page.locator('#orders-list[aria-busy="true"]').waitFor(); await screenshot('loading-1440x900.png');
  await page.locator('#orders-list[aria-busy="false"]').waitFor(); await page.unroute('**/api/v1/staff/orders?limit=50');
  assert.equal(await page.getByRole('button',{name:'Меню',exact:true}).isVisible(),false,'Staff has no admin navigation');
  const customer = await request('auth/login',{method:'POST',body:{email:'customer@mastercoffee.local',password}}); sessions.push(customer.token);
  const catalog = await request('catalog?cafe_id=1'); const product = catalog.products.find(item => item.id === 1);
  const quote = await request('orders/quote',{token:customer.token,method:'POST',body:{cafe_id:1,items:[{product_id:product.id,size_id:product.sizes[0].id,quantity:1,addon_ids:[],sugar:1,temperature:'standard',order_type:'takeaway'}],comment:'Проверка кабинета: заказ браузерного теста'}});
  const order = await request('orders',{token:customer.token,method:'POST',body:{quote_id:quote.id},key:`browser-smoke-${Date.now()}`});
  await page.getByRole('button',{name:'Обновить',exact:true}).click();
  await page.getByRole('button',{name:new RegExp(`Заказ ${order.number},`)}).click();
  await page.getByRole('heading',{name:`Заказ № ${order.number}`,exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Принять заказ',exact:true}).isVisible(),false,'Unpaid order cannot be accepted');
  await screenshot('queue-1440x900.png'); await noHorizontalOverflow();
  // Request failure exercises the real UI's stale-data recovery without a fake response.
  await page.route('**/api/v1/staff/orders?limit=50',route => route.abort('failed'));
  await page.getByRole('button',{name:'Обновить',exact:true}).click();
  await page.getByText('Не удалось обновить. Показаны последние полученные данные.',{exact:true}).waitFor();
  await page.getByRole('heading',{name:`Заказ № ${order.number}`,exact:true}).waitFor();
  await screenshot('partial-1440x900.png');
  await page.unroute('**/api/v1/staff/orders?limit=50');
  await page.getByRole('button',{name:'Обновить',exact:true}).click();
  await page.getByText('Обновлено ',{exact:false}).waitFor();
  await page.setViewportSize({width:390,height:844});
  await screenshot('order-390x844.png'); await noHorizontalOverflow();
  await page.getByLabel('Номер заказа или код из QR',{exact:true}).fill(order.qr.payload);
  await page.getByRole('button',{name:'Найти',exact:true}).click();
  await page.getByRole('status').filter({hasText:`Найден заказ № ${order.number}`}).waitFor();
  await page.getByRole('button',{name:'Подтвердить оплату на кассе',exact:true}).click();
  await page.getByRole('status').filter({hasText:'Оплата заказа'}).waitFor();
  for (const label of ['Принять заказ','Начать готовить','Заказ готов','Подтвердить выдачу']) { const control = page.getByRole('button',{name:label,exact:true}); await control.waitFor(); await control.click(); }
  await page.getByText('Заказ выдан. Лояльность рассчитана сервером.',{exact:true}).waitFor();
  await screenshot('completed-390x844.png');
  assert.equal((await request(`orders/${order.id}`,{token:customer.token})).status,'completed');
  await page.getByLabel('Номер заказа или код из QR',{exact:true}).fill(order.qr.payload); await page.getByRole('button',{name:'Найти',exact:true}).click();
  await page.getByRole('status').filter({hasText:'QR has already been used'}).waitFor();
  // Revoke the actual browser session on the server, then verify private UI is removed.
  await request('auth/logout',{token:staff.token,method:'POST',body:{}});
  await page.getByRole('button',{name:'Обновить',exact:true}).click();
  await page.getByRole('alert').filter({hasText:'Сессия истекла'}).waitFor();
  assert.equal(await page.getByRole('heading',{name:`Заказ № ${order.number}`,exact:true}).isVisible(),false);
  await login('customer'); await page.getByRole('alert').filter({hasText:'не имеет доступа'}).waitFor(); await screenshot('permission-390x844.png');
  const admin = await login('admin');
  await page.setViewportSize({width:1440,height:900});
  await page.getByRole('button',{name:'Меню',exact:true}).click();
  const adminCatalog = await request('admin/catalog?cafe_id=1',{token:admin.token});
  const espresso = adminCatalog.products.find(item => item.id === 1);
  await page.getByRole('button',{name:new RegExp(`^${espresso.name}`)}).first().click();
  assert.equal(await page.getByLabel('Чек повышает уровень (кофе/чай)',{exact:true}).isChecked(),espresso.loyalty_eligible);
  await page.getByRole('button',{name:'Сохранить товар',exact:true}).click();
  await page.getByRole('status').filter({hasText:'Товар сохранён сервером.'}).waitFor();
  await screenshot('menu-1440x900.png'); await noHorizontalOverflow();
  await page.setViewportSize({width:390,height:844}); await screenshot('menu-390x844.png'); await noHorizontalOverflow();
  await page.evaluate(() => { document.documentElement.style.fontSize = '20px'; }); await noHorizontalOverflow();
  await page.evaluate(() => { document.documentElement.style.fontSize = ''; }); await page.setViewportSize({width:1440,height:900});
  await page.getByRole('button',{name:'Stories',exact:true}).click(); await page.getByRole('button',{name:'Добавить story',exact:true}).click();
  const title = `Проверка браузера ${Date.now()}`;
  await page.getByLabel('Заголовок',{exact:true}).fill(title);
  await page.locator('#story-editor').getByLabel('Загрузить изображение (JPEG / PNG, до 5 МБ)',{exact:true}).setInputFiles(path.join(adminRoot,'assets/master-logo.png'));
  await page.getByRole('status').filter({hasText:'Изображение загружено.'}).waitFor();
  await page.getByLabel('Показывать в приложении',{exact:true}).uncheck();
  await page.getByRole('button',{name:'Сохранить story',exact:true}).click(); await page.getByRole('status').filter({hasText:'Story сохранена сервером.'}).waitFor();
  const stories = await request('admin/catalog?cafe_id=1',{token:admin.token}); const created = stories.stories.find(story => story.title === title); assert.ok(created && !created.active);
  page.once('dialog',dialog => dialog.accept()); await page.getByRole('button',{name:'Удалить',exact:true}).click(); await page.getByRole('status').filter({hasText:'Story удалена сервером.'}).waitFor();
  assert.equal((await request('admin/catalog?cafe_id=1',{token:admin.token})).stories.some(story => story.id === created.id),false);
  await page.getByRole('button',{name:'Выйти',exact:true}).click(); await page.getByRole('heading',{name:'Войти в кабинет',exact:true}).waitFor();
  assert.equal(await page.getByLabel('Email',{exact:true}).inputValue(),'');
  assert.deepEqual(errors,[],'No unhandled browser JavaScript errors');
  const result = {testedAt:new Date().toISOString(),origin,orderNumber:order.number,orderID:order.id,initiallyEmpty,viewports:['1440x900','390x844'],checks:['real staff/customer/admin login and permissions','QR lookup and payment','all preparation states and issuance','used QR rejection','session expiry and account DOM isolation','loading aria state','stale queue after failed request and recovery','preserved menu loyalty flag','real story image upload/create/delete','desktop and narrow overflow','keyboard login focus'],limitations:['camera hardware not exercised','browser is headless isolated Chromium; not a physical phone'],unhandledErrors:errors};
  await writeFile(path.join(evidence,'browser-smoke.json'),JSON.stringify(result,null,2));
  process.stdout.write(`PASS: real local API + cabinet workflow ${order.number}; screenshots in admin/evidence\n`);
} finally {
  for (const token of sessions) if (token) try { await request('auth/logout',{token,method:'POST',body:{}}); } catch { /* revoked sessions are already closed */ }
  await context.close(); await browser.close();
}
