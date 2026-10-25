export const statusLabels = Object.freeze({created:'Новый', accepted:'Принят', preparing:'Готовится', ready:'Готов к выдаче', completed:'Выдан', cancelled:'Отменён'});
const transitions = Object.freeze({created:'accepted', accepted:'preparing', preparing:'ready', ready:'completed'});
export function nextStatus(order) { return order.payment_status === 'paid' ? transitions[order.status] ?? null : null; }
export function money(value) { return `${new Intl.NumberFormat('ru-RU', {maximumFractionDigits:0}).format(Number(value) || 0)} ₸`; }
export function localDate(value) { const date = new Date(value); return Number.isNaN(date.getTime()) ? 'Время неизвестно' : date.toLocaleString('ru-RU', {day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit'}); }
export function safeImageUrl(value) { if (typeof value !== 'string') return null; if (/^\/(uploads|assets)\/[a-zA-Z0-9/_.-]+$/.test(value) && !value.includes('..')) return value; try { const url = new URL(value); return url.protocol === 'https:' ? url.href : null; } catch { return null; } }
export class ApiError extends Error { constructor(message, status = 0, code = '') { super(message); this.name = 'ApiError'; this.status = status; this.code = code; } }
const errorMessages = Object.freeze({
  invalid_credentials:'Неверный email или пароль.',
  forbidden:'Нет доступа к заказу этой кофейни или к выбранной операции.',
  not_found:'Заказ или запись не найдены. Проверьте номер и обновите данные.',
  qr_not_found:'QR не найден. Проверьте код или найдите заказ по номеру.',
  qr_expired:'Срок QR истёк. Попросите клиента обновить QR в приложении.',
  qr_used:'Этот QR уже использован. Найдите заказ по номеру, чтобы продолжить обработку.',
  order_cancelled:'Заказ отменён. Оплата и приготовление недоступны.',
  invalid_transition:'Этот переход недоступен. Обновите заказ и соблюдайте этапы: оплата, принятие, приготовление, готовность, выдача.',
  rate_limited:'Слишком много попыток. Подождите минуту и повторите вход.',
  product_exists:'Товар с таким ID уже существует. Обновите каталог и откройте его для проверки.',
  validation:'Проверьте заполненные поля и указанные ограничения.',
  image_too_large:'Изображение слишком большое. Максимум — 5 МБ.',
  invalid_image:'Выберите корректный JPEG или PNG размером не более 4096 × 4096 пикселей.',
  unsupported_media_type:'Этот формат файла или запроса не поддерживается.',
});
export function apiErrorMessage(data) { return errorMessages[data?.error?.code] || data?.error?.message || 'Запрос не выполнен. Повторите попытку.'; }
export function createApi({fetchImpl = globalThis.fetch, onExpired = () => {}, timeoutMs = 12000} = {}) {
  let token = '';
  let generation = 0;
  const pending = new Set();
  function clear() { token = ''; generation++; for (const controller of pending) controller.abort(); pending.clear(); }
  function authenticate(value) { clear(); token = value; }
  async function request(path, {method = 'GET', body, raw = false, contentType, headers: extraHeaders = {}} = {}) {
    const controller = new AbortController(); const requestGeneration = generation; pending.add(controller);
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const headers = {Accept:'application/json',...extraHeaders};
      if (token) headers.Authorization = `Bearer ${token}`;
      if (body !== undefined) headers['Content-Type'] = contentType || 'application/json';
      const response = await fetchImpl(`/api/v1/${path}`, {method,headers,body:body === undefined ? undefined : raw ? body : JSON.stringify(body),signal:controller.signal,credentials:'same-origin',cache:'no-store'});
      if (requestGeneration !== generation) throw new ApiError('Сессия завершена.', 401, 'stale_session');
      const text = response.status === 204 ? '' : await response.text();
      if (requestGeneration !== generation) throw new ApiError('Сессия завершена.', 401, 'stale_session');
      let data;
      try { data = text ? JSON.parse(text) : null; } catch { if (response.status === 401 && token) { clear(); onExpired(); } throw new ApiError('Сервер вернул неожиданный ответ. Повторите запрос.', response.status); }
      if (!response.ok) {
        if (response.status === 401 && token) { clear(); onExpired(); }
        throw new ApiError(apiErrorMessage(data), response.status, data?.error?.code || '');
      }
      return data;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      if (requestGeneration !== generation) throw new ApiError('Сессия завершена.',401,'stale_session');
      if (error.name === 'AbortError') throw new ApiError('Время ожидания истекло. Обновите данные перед повторным действием.',0,'timeout');
      throw new ApiError('Не удалось подключиться к серверу. Проверьте сеть и повторите запрос.',0,'network');
    } finally { clearTimeout(timer); pending.delete(controller); }
  }
  return {request,authenticate,clear};
}
