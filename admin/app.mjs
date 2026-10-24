import {createApi, money, localDate, nextStatus, statusLabels, safeImageUrl} from './lib.mjs';

const $ = id => document.getElementById(id);
const state = {user:null,orders:[],selected:null,catalog:null,cafe:1,view:'orders',poll:null,loading:false,mutating:false,stream:null,scanTimer:null,scanGeneration:0,sessionGeneration:0,catalogGeneration:0,detailGeneration:0,lookupToken:null};
const api = createApi({onExpired:() => endSession('Сессия истекла. Войдите снова.')});
function element(tag, text, className) { const node = document.createElement(tag); if (text !== undefined) node.textContent = text; if (className) node.className = className; return node; }
function button(text, action, className = 'secondary') { const node = element('button', text, className); node.type = 'button'; node.addEventListener('click', action); return node; }
function badge(text, className) { return element('span',text,`badge ${className}`); }
function notice(message, bad = false) { if (!state.user) return; $('notice').textContent = message; $('notice').className = `notice${bad ? ' bad' : ''}`; $('notice').hidden = !message; }
function report(error) { if (state.user) notice(error.message,true); }
async function busy(control, action) { control.disabled = true; try { await action(); } catch(error) { report(error); } finally { control.disabled = false; } }
function endSession(message = '') {
  api.clear(); state.sessionGeneration++; clearInterval(state.poll); state.poll = null; stopCamera();
  Object.assign(state,{user:null,orders:[],selected:null,catalog:null,loading:false,mutating:false,lookupToken:null,view:'orders'});
  for (const id of ['orders-list','products-list','stories-list','product-editor','story-editor','order-detail','session-name','menu-cafe']) $(id).replaceChildren();
  $('order-code').value = ''; $('email').value = ''; $('password').value = ''; $('notice').textContent = ''; $('notice').hidden = true;
  $('workspace').hidden = true; $('session').hidden = true; $('login-view').hidden = false;
  $('login-error').textContent = message; $('login-error').hidden = !message; $('password').focus();
}
function startPolling() {
  clearInterval(state.poll);
  state.poll = setInterval(() => { if (state.user && state.view === 'orders' && !document.hidden && !state.loading && !state.mutating) loadOrders(false); },8000);
}
function setView(view) {
  state.view = view; $('notice').hidden = true;
  for (const name of ['orders','menu','stories']) { $(`${name}-view`).hidden = name !== view; $(`tab-${name}`).classList.toggle('active', name === view); if (name === view) $(`tab-${name}`).setAttribute('aria-current','page'); else $(`tab-${name}`).removeAttribute('aria-current'); }
  stopCamera();
  if (view === 'orders') loadOrders(); else loadCatalog();
}
function emptyOrder() {
  const wrap = element('div',undefined,'empty-detail'); wrap.append(element('span','☕','coffee-mark'),element('h2','Выберите заказ'),element('p','Откройте его из очереди или введите номер.','muted')); $('order-detail').replaceChildren(wrap);
}
function renderQueue() {
  $('queue-heading').textContent = `Активные заказы · ${state.orders.length}`;
  const list = $('orders-list'); list.replaceChildren();
  if (!state.orders.length) { list.append(element('p','Сейчас активных заказов нет. Новые появятся здесь автоматически.','empty-state')); return; }
  for (const order of state.orders) {
    const row = button('',() => selectOrder(order.id),'order-row'); row.classList.toggle('selected',state.selected?.id === order.id);
    const top = element('div',undefined,'row-top'); top.append(element('strong',`№ ${order.number}`),badge(statusLabels[order.status] || order.status,order.status));
    const bottom = element('div',undefined,'row-bottom'); bottom.append(element('span',`${localDate(order.created_at)} · ${order.payment_status === 'paid' ? 'Оплачен' : 'Не оплачен'}`),element('strong',money(order.total)));
    row.append(top,bottom); row.setAttribute('aria-label',`Заказ ${order.number}, ${statusLabels[order.status] || order.status}, ${money(order.total)}`); row.setAttribute('aria-pressed',String(state.selected?.id === order.id)); list.append(row);
  }
}
async function loadOrders(manual = true) {
  if (state.loading || !state.user) return;
  state.loading = true; $('orders-list').setAttribute('aria-busy','true'); $('refresh-orders').disabled = true;
  const generation = state.sessionGeneration;
  if (!state.orders.length && !state.selected) $('orders-list').replaceChildren(element('p','Загрузка заказов…','empty-state'));
  try {
    const result = await api.request('staff/orders?limit=50');
    if (generation !== state.sessionGeneration) return;
    state.orders = result.orders; renderQueue();
    if (state.selected) { const selectedID = state.selected.id; const detailGeneration = state.detailGeneration; const order = await api.request(`staff/orders/${encodeURIComponent(selectedID)}`); if (generation !== state.sessionGeneration) return; if (detailGeneration === state.detailGeneration && state.selected?.id === selectedID) { state.selected = order; renderOrder(); } }
    $('poll-status').textContent = `Обновлено ${new Date().toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit',second:'2-digit'})}. Автообновление каждые 8 секунд.`;
    if (manual) notice('');
  } catch(error) {
    if (generation !== state.sessionGeneration) return;
    $('poll-status').textContent = 'Не удалось обновить. Показаны последние полученные данные.';
    if (!state.orders.length) $('orders-list').replaceChildren(element('p','Очередь недоступна. Нажмите «Обновить».','empty-state'));
    report(error);
  } finally { if (generation === state.sessionGeneration) { state.loading = false; $('orders-list').setAttribute('aria-busy','false'); $('refresh-orders').disabled = false; } }
}
async function selectOrder(id) {
  const generation = state.sessionGeneration;
  const detailGeneration = ++state.detailGeneration;
  try { const order = await api.request(`staff/orders/${encodeURIComponent(id)}`); if (generation !== state.sessionGeneration || detailGeneration !== state.detailGeneration) return; state.selected = order; state.lookupToken = null; renderQueue(); renderOrder(); if (window.matchMedia('(max-width:650px)').matches) { $('order-detail').scrollIntoView({block:'start'}); $('order-detail').focus({preventScroll:true}); } }
  catch(error) { report(error); }
}
async function lookup(code) {
  const generation = state.sessionGeneration;
  const detailGeneration = ++state.detailGeneration;
  const order = await api.request('staff/orders/lookup',{method:'POST',body:{code:code.trim()}});
  if (generation !== state.sessionGeneration || detailGeneration !== state.detailGeneration) return;
  state.selected = order; state.lookupToken = code.startsWith('mastercoffee:order:') || /^[A-Za-z0-9_-]{40,}$/.test(code) ? code : null;
  notice(`Найден заказ № ${order.number}. Проверьте состав и оплату.`); renderQueue(); renderOrder(); $('order-detail').focus();
}
function renderOrder() {
  const order = state.selected; if (!order) { emptyOrder(); return; }
  const wrap = $('order-detail'); wrap.replaceChildren();
  const heading = element('div',undefined,'detail-heading'); heading.append(element('h2',`Заказ № ${order.number}`));
  const statuses = element('div',undefined,'status-group'); statuses.append(badge(statusLabels[order.status] || order.status,order.status),badge(order.payment_status === 'paid' ? 'Оплачен' : 'Не оплачен',order.payment_status)); heading.append(statuses); wrap.append(heading);
  const cafe = state.catalog?.cafes.find(c => c.id === order.cafe_id);
  wrap.append(element('p',`${localDate(order.created_at)} · ${cafe?.name || `Кофейня ${order.cafe_id}`}`,'detail-meta'));
  const items = element('ul',undefined,'line-items');
  for (const item of order.items) {
    const li = element('li'); li.append(element('strong',`${item.quantity} × ${item.name}`),element('span',money(item.line_total),'line-price'));
    const details = [item.size_label,...(item.addon_names || [])];
    if (item.sugar) details.push(`Сахар: ${item.sugar}`);
    if (item.temperature) details.push(({standard:'Обычная температура',hot:'Погорячее',cool:'Попрохладнее'})[item.temperature] || item.temperature);
    if (item.order_type) details.push(({takeaway:'С собой',dine_in:'В кофейне'})[item.order_type] || item.order_type);
    li.append(element('small',details.filter(Boolean).join(' · '))); items.append(li);
  }
  wrap.append(items);
  if (order.comment) wrap.append(element('p',`Комментарий: ${order.comment}`,'order-comment'));
  const totals = element('dl',undefined,'totals'); totals.append(element('dt','Сумма позиций'),element('dd',money(order.subtotal)),element('dt',`Скидка ${order.discount_percent}%`),element('dd',`−${money(order.discount)}`),element('dt','К оплате','grand-total'),element('dd',money(order.total),'grand-total')); wrap.append(totals);
  const actions = element('div',undefined,'order-actions');
  if (order.payment_status === 'unpaid' && !['cancelled','completed'].includes(order.status)) actions.append(button('Подтвердить оплату на кассе',event => mutateOrder(event.currentTarget,'payment',state.lookupToken ? {qr_token:state.lookupToken} : {}),'primary'));
  const next = nextStatus(order);
  const labels = {accepted:'Принять заказ',preparing:'Начать готовить',ready:'Заказ готов',completed:'Подтвердить выдачу'};
  if (next) actions.append(button(labels[next],event => mutateOrder(event.currentTarget,'status',{status:next}),'primary'));
  if (order.status === 'created' && order.payment_status === 'unpaid') actions.append(button('Отменить заказ',event => { if (window.confirm(`Отменить неоплаченный заказ № ${order.number}?`)) mutateOrder(event.currentTarget,'status',{status:'cancelled'}); },'danger'));
  if (!actions.children.length) actions.append(element('p',order.status === 'completed' ? 'Заказ выдан. Лояльность рассчитана сервером.' : order.status === 'cancelled' ? 'Заказ отменён.' : 'Сначала подтвердите фактическую оплату на кассе.','hint'));
  wrap.append(actions);
}
async function mutateOrder(control, action, body) {
  if (state.mutating) return;
  state.mutating = true;
  const selectedID = state.selected?.id; const generation = state.sessionGeneration;
  for (const actionButton of $('order-detail').querySelectorAll('button')) actionButton.disabled = true;
  try { await busy(control,async () => {
    const order = await api.request(`staff/orders/${encodeURIComponent(selectedID)}/${action}`,{method:action === 'status' ? 'PATCH' : 'POST',body});
    if (generation !== state.sessionGeneration) return;
    if (state.selected?.id === selectedID) { state.selected = order; state.lookupToken = null; renderOrder(); }
    await loadOrders(false); notice(action === 'payment' ? `Оплата заказа № ${order.number} подтверждена сервером.` : `Заказ № ${order.number}: ${statusLabels[order.status]}.`);
  }); } finally { if (generation === state.sessionGeneration) { state.mutating = false; renderOrder(); } }
}
function makeField(form,label,type,value,required = true) {
  const id = `field-${crypto.randomUUID()}`; const title = element('label',label); title.htmlFor = id;
  const input = element(type === 'textarea' ? 'textarea' : 'input'); input.id = id; if (type !== 'textarea') input.type = type; input.value = value ?? ''; input.required = required; if (type === 'textarea') input.maxLength = 2000; else if (type === 'text') input.maxLength = 500;
  form.append(title,input); return input;
}
function checkbox(form,label,checked) { const title = element('label',undefined,'check-label'); const input = element('input'); input.type = 'checkbox'; input.checked = checked; title.append(input,document.createTextNode(label)); form.append(title); return input; }
function addImageUpload(form,target,updatePreview = () => {}) {
  const file = makeField(form,'Загрузить изображение (JPEG / PNG, до 5 МБ)','file',undefined,false); file.accept = 'image/jpeg,image/png'; file.className = 'file-input';
  file.addEventListener('change',async () => { const selected = file.files[0]; if (!selected) return; await busy(file,async () => { if (!['image/png','image/jpeg'].includes(selected.type) || selected.size > 5 * 1024 * 1024) throw new Error('Выберите JPEG или PNG размером до 5 МБ.'); const result = await api.request('admin/images',{method:'POST',body:selected,raw:true,contentType:selected.type}); target.value = result.url; updatePreview(); notice('Изображение загружено. Сохраните изменения для публикации.'); }); });
}
async function loadCatalog() {
  const generation = state.sessionGeneration;
  const requestGeneration = ++state.catalogGeneration;
  const cafeID = state.cafe;
  for (const id of ['products-list','stories-list']) { $(id).setAttribute('aria-busy','true'); if (!state.catalog) $(id).replaceChildren(element('p','Загрузка каталога…','empty-state')); }
  try {
    const data = await api.request(`${state.user.role === 'admin' ? 'admin/catalog' : 'catalog'}?cafe_id=${cafeID}`);
    if (generation !== state.sessionGeneration || requestGeneration !== state.catalogGeneration) return false;
    state.catalog = data;
    const allowedCafe = data.cafes.find(c => c.id === state.user.cafe_id);
    $('cafe-name').textContent = state.user.role === 'admin' ? 'ВСЕ КОФЕЙНИ · АДМИНИСТРАТОР' : allowedCafe?.name || `КОФЕЙНЯ ${state.user.cafe_id}`;
    $('menu-cafe').replaceChildren(); for (const cafe of data.cafes) { const option = element('option',`${cafe.name} · ${cafe.address}`); option.value = cafe.id; $('menu-cafe').append(option); } $('menu-cafe').value = String(state.cafe);
    renderProducts(); renderStories(); return true;
  } catch(error) { if (generation === state.sessionGeneration && requestGeneration === state.catalogGeneration) { report(error); if (!state.catalog) for (const id of ['products-list','stories-list']) $(id).replaceChildren(element('p','Каталог недоступен. Нажмите «Обновить» или вернитесь в раздел.','empty-state')); } return false; }
  finally { if (generation === state.sessionGeneration && requestGeneration === state.catalogGeneration) for (const id of ['products-list','stories-list']) $(id).setAttribute('aria-busy','false'); }
}
function renderProducts() {
  const list = $('products-list'); list.replaceChildren();
  list.append(button('Добавить товар',() => editProduct(null),'secondary new-product'));
  for (const product of state.catalog.products) {
    const row = element('div',undefined,'management-row'); const open = button('',() => editProduct(product),'quiet'); open.append(document.createTextNode(product.name),element('span',`${product.category} · ${product.sizes.map(s => money(s.price)).join(' / ')}`,'item-summary'));
    const cafeID = state.cafe;
    const availability = button(product.available ? 'Доступен' : 'Стоп',event => busy(event.currentTarget,async () => { await api.request(`admin/cafes/${cafeID}/products/${product.id}`,{method:'PATCH',body:{available:!product.available}}); if (await loadCatalog()) notice(`Доступность «${product.name}» обновлена.`); }),`badge ${product.available ? 'paid' : 'cancelled'}`); availability.setAttribute('aria-label',`${product.name}: ${product.available ? 'доступен, добавить в стоп-лист' : 'в стоп-листе, включить'}`);
    row.append(open,availability); list.append(row);
  }
  if (!state.catalog.products.length) list.append(element('p','В каталоге пока нет товаров.','empty-state'));
}
function editProduct(product) {
  const editor = $('product-editor'); editor.replaceChildren(element('h2',product ? product.name : 'Новый товар'));
  const form = element('form');
  const id = makeField(form,'Числовой ID товара','number',product?.id); id.min = '1'; id.max = '2147483647'; id.step = '1'; id.readOnly = !!product;
  const name = makeField(form,'Название','text',product?.name); name.maxLength = 150;
  const category = makeField(form,'Категория','text',product?.category); category.maxLength = 80;
  const description = makeField(form,'Описание','textarea',product?.description,false);
  const image = makeField(form,'Ключ изображения или адрес загруженного файла','text',product?.image_key,false);
  image.maxLength = 100;
  addImageUpload(form,image);
  const isDrink = checkbox(form,'Напиток: скидка и параметры приготовления',product?.is_drink ?? true);
  const loyaltyEligible = checkbox(form,'Чек повышает уровень (кофе/чай)',product?.loyalty_eligible ?? false);
  const fieldset = element('fieldset'); fieldset.append(element('legend','Объёмы и цены в целых тенге'));
  const sizeRows = element('div'); fieldset.append(sizeRows);
  function addSize(size = {}) {
    const row = element('div',undefined,'size-editor'); row.dataset.sizeId = size.id || `size-${crypto.randomUUID()}`;
    const labelWrap = element('div'); const label = makeField(labelWrap,'Объём','text',size.label); label.maxLength = 50;
    const priceWrap = element('div'); const price = makeField(priceWrap,'Цена, ₸','number',size.price); price.min = '0'; price.max = '1000000'; price.step = '1';
    row.append(labelWrap,priceWrap,button('Удалить',() => row.remove(),'quiet')); sizeRows.append(row);
  }
  (product?.sizes.length ? product.sizes : [{}]).forEach(addSize); fieldset.append(button('Добавить объём',() => addSize(),'secondary')); form.append(fieldset);
  const actions = element('div',undefined,'form-actions'); const save = element('button','Сохранить товар','primary'); save.type = 'submit'; actions.append(save); form.append(actions);
  form.addEventListener('submit',event => { event.preventDefault(); busy(save,async () => {
    const numericID = Number(id.value);
    if (loyaltyEligible.checked && !isDrink.checked) throw new Error('Только напиток может повышать уровень лояльности.');
    if (!product && state.catalog.products.some(item => item.id === numericID)) throw new Error('Этот ID уже занят. Выберите другой или откройте существующий товар.');
    const sizes = [...sizeRows.children].map(row => ({id:row.dataset.sizeId,label:row.querySelector('input[type=text]').value.trim(),price:Number(row.querySelector('input[type=number]').value)}));
    if (!sizes.length) throw new Error('Добавьте хотя бы один объём.');
    const updated = await api.request(`admin/products/${numericID}`,{method:'PUT',body:{id:numericID,name:name.value.trim(),description:description.value.trim(),category:category.value.trim(),image_key:image.value.trim(),is_drink:isDrink.checked,loyalty_eligible:loyaltyEligible.checked,sizes},headers:product ? undefined : {'If-None-Match':'*'}});
    if (await loadCatalog()) { editProduct(state.catalog.products.find(p => p.id === numericID) || updated); notice('Товар сохранён сервером.'); }
  }); });
  editor.append(form); if (!product) id.focus(); else if (window.matchMedia('(max-width:650px)').matches) { editor.scrollIntoView({block:'start'}); name.focus({preventScroll:true}); }
}
function renderStories() {
  const list = $('stories-list'); list.replaceChildren();
  for (const story of state.catalog.stories) { const row = element('div',undefined,'management-row'); const open = button('',() => editStory(story),'quiet'); open.append(document.createTextNode(story.title),element('span',story.active ? 'Опубликована' : 'Скрыта','item-summary')); row.append(open); list.append(row); }
  if (!state.catalog.stories.length) list.append(element('p','Stories пока нет. Добавьте первую.','empty-state'));
}
function editStory(story) {
  const editor = $('story-editor'); editor.replaceChildren(element('h2',story ? 'Редактировать story' : 'Новая story'));
  const form = element('form'); const title = makeField(form,'Заголовок','text',story?.title); title.maxLength = 200;
  const imageURL = makeField(form,'Адрес изображения','text',story?.image_url); imageURL.maxLength = 2000;
  const image = element('img'); image.alt = 'Предпросмотр story';
  const updatePreview = () => { const src = safeImageUrl(imageURL.value); image.hidden = !src; if (src) image.src = src; else image.removeAttribute('src'); }; imageURL.addEventListener('input',updatePreview); updatePreview(); form.append(image);
  addImageUpload(form,imageURL,updatePreview);
  const active = checkbox(form,'Показывать в приложении',story?.active ?? true);
  const actions = element('div',undefined,'form-actions'); const save = element('button','Сохранить story','primary'); save.type = 'submit'; actions.append(save);
  if (story) actions.append(button('Удалить',event => { if (window.confirm(`Удалить story «${story.title}»?`)) busy(event.currentTarget,async () => { await api.request(`admin/stories/${encodeURIComponent(story.id)}`,{method:'DELETE'}); editor.replaceChildren(element('p','Story удалена.','muted')); if (await loadCatalog()) notice('Story удалена сервером.'); }); },'danger'));
  form.append(actions); const id = story?.id || crypto.randomUUID();
  form.addEventListener('submit',event => { event.preventDefault(); busy(save,async () => { if (imageURL.value && !safeImageUrl(imageURL.value)) throw new Error('Используйте загруженное изображение или HTTPS-адрес.'); await api.request(`admin/stories/${encodeURIComponent(id)}`,{method:'PUT',body:{title:title.value.trim(),image_url:imageURL.value.trim(),active:active.checked}}); if (await loadCatalog()) { editStory(state.catalog.stories.find(s => s.id === id)); notice('Story сохранена сервером.'); } }); }); editor.append(form); title.focus();
}
function stopCamera() { state.scanGeneration++; clearTimeout(state.scanTimer); state.scanTimer = null; state.stream?.getTracks().forEach(track => track.stop()); state.stream = null; $('camera').srcObject = null; if ($('scanner').open) $('scanner').close(); }
async function scanQR() {
  if (!('BarcodeDetector' in window) || !navigator.mediaDevices?.getUserMedia) { notice('Сканирование в этом браузере недоступно. Введите номер заказа или код из QR вручную.',true); $('order-code').focus(); return; }
  $('scanner').showModal(); $('scanner-status').textContent = 'Разрешите доступ к камере.';
  const generation = ++state.scanGeneration;
  try {
    const formats = await BarcodeDetector.getSupportedFormats(); if (!formats.includes('qr_code')) throw new Error('Этот браузер не распознаёт QR. Введите код вручную.');
    const stream = await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'}},audio:false});
    if (generation !== state.scanGeneration) { stream.getTracks().forEach(track => track.stop()); return; }
    state.stream = stream; $('camera').srcObject = stream; await $('camera').play(); $('scanner-status').textContent = 'Наведите камеру на QR заказа.';
    const detector = new BarcodeDetector({formats:['qr_code']});
    async function detect() {
      if (generation !== state.scanGeneration) return;
      try { const codes = await detector.detect($('camera')); if (codes.length) { const code = codes[0].rawValue; stopCamera(); $('order-code').value = code; try { await lookup(code); } catch(error) { report(error); } return; } }
      catch(error) { if (generation === state.scanGeneration) $('scanner-status').textContent = 'Не удалось прочитать QR. Попробуйте снова или введите код вручную.'; }
      if (generation === state.scanGeneration) state.scanTimer = setTimeout(detect,250);
    }
    detect();
  } catch(error) { if (generation === state.scanGeneration) { stopCamera(); notice(error.message || 'Камера недоступна. Введите код вручную.',true); } }
}

$('login-form').addEventListener('submit',async event => {
  event.preventDefault(); const control = $('login-button'); control.disabled = true; control.textContent = 'Вход…'; $('login-error').hidden = true;
  try {
    const result = await api.request('auth/login',{method:'POST',body:{email:$('email').value.trim(),password:$('password').value}}); $('password').value = '';
    if (!['staff','admin'].includes(result.user?.role)) { api.authenticate(result.token); await api.request('auth/logout',{method:'POST',body:{}}); api.clear(); throw new Error('Эта учётная запись не имеет доступа к кабинету сотрудника.'); }
    api.authenticate(result.token); state.user = result.user; state.sessionGeneration++; state.cafe = result.user.cafe_id || 1;
    $('session-name').textContent = `${result.user.name || result.user.email} · ${result.user.role === 'admin' ? 'Администратор' : 'Сотрудник'}`;
    $('login-view').hidden = true; $('workspace').hidden = false; $('session').hidden = false;
    $('tab-menu').hidden = result.user.role !== 'admin'; $('tab-stories').hidden = result.user.role !== 'admin';
    emptyOrder(); setView('orders'); startPolling(); loadCatalog(); $('tab-orders').focus();
  } catch(error) { api.clear(); $('login-error').textContent = error.message; $('login-error').hidden = false; }
  finally { control.disabled = false; control.textContent = 'Войти'; }
});
$('logout').addEventListener('click',async event => { const control = event.currentTarget; control.disabled = true; let message = ''; try { await api.request('auth/logout',{method:'POST',body:{}}); } catch(error) { message = 'Данные очищены из кабинета. Сервер не подтвердил выход; сессия завершится по сроку действия.'; } finally { endSession(message); control.disabled = false; } });
$('lookup-form').addEventListener('submit',event => { event.preventDefault(); busy(event.currentTarget.querySelector('button'),() => lookup($('order-code').value.trim())); });
$('refresh-orders').addEventListener('click',() => loadOrders());
for (const view of ['orders','menu','stories']) $(`tab-${view}`).addEventListener('click',() => setView(view));
$('refresh-menu').addEventListener('click',event => busy(event.currentTarget,loadCatalog));
$('menu-cafe').addEventListener('change',() => { state.cafe = Number($('menu-cafe').value); $('product-editor').replaceChildren(element('p','Выберите товар для редактирования.','muted')); loadCatalog(); });
$('new-story').addEventListener('click',() => editStory(null));
$('scan-button').addEventListener('click',scanQR); $('close-scanner').addEventListener('click',stopCamera); $('scanner').addEventListener('cancel',stopCamera);
document.addEventListener('visibilitychange',() => { if (document.hidden) stopCamera(); else if (state.user && state.view === 'orders') loadOrders(false); });
window.addEventListener('pagehide',() => endSession());
window.addEventListener('pageshow',event => { if (event.persisted) endSession('Войдите снова после возврата на страницу.'); });
