# Backend MVP

Go 1.27.1, chi, pgx v5, PostgreSQL 17. Handler → service → repository. Original backend imported from `https://github.com/kairagz27/master-coffee-go`, commit `e21c4058e5960e193e770e0e8cb6700ac3f8c9da`; history retained by subtree import. No live Firebase reads or writes.

## JSON contract

All routes below start with `/api/v1`. Responses are direct objects, without an envelope. Authenticated calls require `Authorization: Bearer TOKEN`. Errors: `{"error":{"code":"...","message":"...","request_id":"..."}}`. POST/PATCH/PUT JSON calls require `Content-Type: application/json`; unknown fields, multiple JSON values and bodies over 64 KiB are rejected. Opaque tokens are cryptographically random 256 bits, sessions stored as SHA-256 hashes, passwords bcrypt cost 12. Passwords: 12–72 UTF-8 bytes. Email is explicitly unverified. Rate limit: 20 auth attempts per IP/minute, process memory bounded to 10,000 live keys; behind a reverse proxy configure topology carefully, since forwarded IP headers are deliberately untrusted.

| Method | Path | Request / response |
|---|---|---|
| POST | `/auth/register` | `{email,password,name}` →201 `{token,expires_at,user}` |
| POST | `/auth/login` | `{email,password}` →200 `{token,expires_at,user}` |
| GET | `/auth/me` | User `{id,email,name,role,cafe_id,email_verified:false}` |
| POST | `/auth/logout` |204; revokes current session |
| GET | `/catalog?cafe_id=1` | `{cafes,products,addons,stories}` |
| GET | `/cafes` | `{cafes}` |
| GET | `/loyalty` | `{qualifying_orders,discount_percent,next_threshold,next_discount_percent}`; last two nullable |
| POST | `/orders/quote` | QuoteInput below →201 Quote |
| POST | `/orders` | `{quote_id}` plus `Idempotency-Key` (8–128 bytes) →201 Order; same key+quote →200 original Order; different quote →409 |
| GET | `/orders?limit=20&before=1005` | Own history `{orders,next_before}`; descending sequence cursor, maximum limit100 |
| GET | `/orders/{id}` | Own Order; other owner's order404 |
| POST | `/orders/{id}/cancel` | `{}` → Order; only created+unpaid |
| POST | `/orders/{id}/qr` | `{}` → Order with fresh QR; only owner and unpaid active order |
| GET | `/staff/orders?search=MC-001000&limit=50&cafe_id=1` | `{orders,next_before}`; staff's cafe forced, admin may filter any cafe |
| GET | `/staff/orders/{id}` | Order; wrong cafe403 |
| POST | `/staff/orders/lookup` | `{code}` (number or QR payload/raw token) →Order; QR checks cafe, expiry and prior consumption |
| POST | `/staff/orders/{id}/payment` | `{}` for cashier number lookup, or `{qr_token}` binding scanned QR →Order |
| PATCH | `/staff/orders/{id}/status` | `{status}` →Order |
| GET | `/admin/catalog?cafe_id=1` | Catalog including inactive stories |
| PUT | `/admin/products/{integer_id}` | Full Product, ID optional when matching URL; upsert, new product available in all cafes |
| PATCH | `/admin/cafes/{cafe_id}/products/{product_id}` | `{available:true}` |
| PUT | `/admin/stories/{string_id}` | `{title,image_url,active}`; upsert |
| DELETE | `/admin/stories/{id}` |204 |
| POST | `/admin/images` | Raw PNG/JPEG bytes, maximum5MiB and4096×4096 →201 `{url:"/uploads/random.png"}` |

```json
{
  "cafe_id": 1,
  "items": [{"product_id":3,"size_id":"3-350","quantity":1,"addon_ids":["essence-cherry"],"sugar":0,"order_type":"takeaway","temperature":"standard"}],
  "comment": "Без крышки"
}
```

Quote: `{id,expires_at,cafe_id,items,subtotal,discount_percent,discount,total,currency:"KZT",money_unit:"whole_kzt"}`. Quote lifetime5min. Create revalidates catalogue and loyalty inside a transaction synchronized with catalogue edits; changed snapshot or price409 `price_changed` requires a new quote and explicit customer review. Unavailability409. Quote cannot create a second order even with another key. A transaction advisory lock serializes concurrent equal idempotency keys. Identical keys remain valid after quote expiry once an order exists.

Order: `{id,number,sequence,cafe_id,user_id,status,payment_status,subtotal,discount_percent,discount,total,currency,money_unit,items,comment,created_at,updated_at,qr}`. `qr` only in owner responses: `{token,payload:"mastercoffee:order:TOKEN",expires_at,used}`. Thirty minute QR; server generated token carries no PII or price. Refresh revokes old token. Payment consumes QR transactionally; QR lookup or QR-bound payment after use409; expired410. Repeated number-based payment is idempotent. The cashier may explicitly use the order number rather than a QR, authenticated and cafe scoped. QR is an order reference, not proof of actual money transfer: cashier is responsible for verifying cash/card-register payment.

Item snapshot: `{product_id,name,size_id,size_label,quantity,addon_ids,addon_names,sugar,order_type,temperature,unit_price,line_total,is_drink,loyalty_eligible}`. Product `{id,name,description,category,image_key,is_drink,loyalty_eligible,available,sizes:[{id,label,price}]}`. Cafe `{id,name,address,working_hours,phone,image_key,active}`. Addon `{id,name,group,price}` with group `syrup|essence|milk`, max two unique addons/group. Empty addon list means no additions. Quantity1–20, sugar0–5, order_type `takeaway|dine_in`, temperature `standard|hot|cool`. Stable IDs of sizes combine original product and millilitres, e.g.`3-350`. Catalogue preserves original18 products,12 cafe addresses, all9 charged/noncharged additions: syrup and alternative milk0KZT, essences60KZT, matching original Kotlin lists.

## Business rules

Whole integer KZT, no floats. Discount applied to aggregate beverage subtotal including additions; final beverage total rounded half up once to whole KZT: `(beverageSubtotal*(100-percent)+50)/100`. Discount equals subtotal minus rounded total. Food receives no discount. All times stored as PostgreSQL `timestamptz`, serialized UTC RFC3339.

Original loyalty terms in App.kt: initial3%, at50 qualifying receipts5%, at70→7%, at100→10%; the discount used for an order is its creation snapshot. One completed, paid receipt containing a qualifying beverage increases receipt balance once. All18 original products are beverages and receive the discount. Explicit `loyalty_eligible` distinguishes coffee/tea receipts: fresh juice19 is false; other source products are true, including glintwine12 because it is classified under original “Чаи”. That source classification is commercially ambiguous (description says juice with spices); confirm its eligibility with the owner before a pilot. Admins can set eligibility explicitly, and it is snapshotted at purchase. Migration002 backfills only missing fields in known original products/order items, preserves explicit operator choices and expires old quotes for customer review; it does not rewrite posted ledger awards. Ledger `UNIQUE(order_id)` and row lock prevent repeated rewards; there is no spendable MC Coins balance in this MVP. Promotions/referrals/online payments are disabled. The source counted shared Firebase orders contrary to its own ownership and receipt terms; the new calculation is owner-specific.

Preparation `created → accepted → preparing → ready → completed`. Each forward step requires `paid`, skips and backward transitions409; repeating the current state is idempotent. Payment separately `unpaid → paid`. Customer or staff may cancel only `created+unpaid`; cancellation after payment needs a real refund workflow outside this MVP and is rejected. Order items never depend on mutable menu names/prices after purchase. Staff payment/status operations append an audit event; completion and ledger award commit atomically.

## Configuration / operations

`DATABASE_URL` required. `APP_ENV=development|test|production` (default development), `HTTP_ADDR=:8080`, `UPLOAD_DIR=./uploads`, `CORS_ORIGINS=http://localhost:8081`, `SESSION_TTL_HOURS=168`, `QR_TTL_MINUTES=30`, `AUTO_MIGRATE=false`, `SEED_DEMO=false`, `DEMO_PASSWORD` required when seeding demos. Production rejects demo seed, automatic migration, non-TLS DB connections (`sslmode=verify-full` required), and non-HTTPS/wildcard CORS origins. Deploy behind HTTPS. Tokens never appear in access log URL paths or query logging; logs include route templates and request IDs only.

`go run ./cmd/migrate` runs versioned embedded SQL under an advisory lock with SHA-256 checksum verification. API seeds missing catalogue entries idempotently without overwriting changes. With explicit development seed enabled, accounts `customer@mastercoffee.local`, `staff@mastercoffee.local` (cafe1), `admin@mastercoffee.local` share env `DEMO_PASSWORD`; startup never resets existing passwords. Never enable demos in a public deployment.

`GET /healthz` liveness, `/readyz` DB ping. HTTP header/body/write/idle timeouts5/15/25/60sec, application timeout20sec, SQL statement timeout15sec, pool max15. SIGINT/SIGTERM shutdown waits up to10sec. Docker nonroot process, uploads in `/data/uploads` volume. Persistent tables: `users`, `sessions`, `cafes`, `products`, `product_availability`, `addons`, `stories`, `quotes`, `orders`, `order_items`, `loyalty_ledger`, `order_events`, `schema_migrations`.

Run `go test ./...` and `go vet ./...` from backend. PostgreSQL integration tests require `TEST_DATABASE_URL`, point only to a disposable test database; tests create isolated schemas and drop only their schema. Production migrations should run with a separate DDL credential before API startup; email verification/reset, refunds, distributed rate limiting, retention policies and deployment review remain pilot preparation work.

## Усиление проверки входных данных

JSON использует точные имена полей из OpenAPI. Повторяющиеся ключи, другой регистр, `null` вместо скалярного поля/массива/позиции заказа, неизвестные поля и несколько JSON-значений получают400. Пустые добавки передаются как `"addon_ids":[]`. Это предотвращает превращение `price:null` в нулевую цену и `active:null` в ложное значение. MIME проверяется как `application/json` с допустимыми параметрами; суффиксы вроде `application/json-invalid` не принимаются. Ошибки отсутствующего изображения, panic и deadline имеют JSON-формат; panic payload не записывается в лог. Создание пользователя и его первой сессии выполняется одной транзакцией: ошибка записи сессии не оставляет частично созданный аккаунт.

## Создание сотрудников без SQL и публичного назначения ролей

`cmd/accounts` доступна только оператору с учётными данными БД. HTTP-регистрация создаёт только customer; маршрута для самостоятельной смены роли нет. CLI создаёт новый staff с существующей активной кофейней либо нового admin без привязки. Существующий email вызывает409: команда не повышает привилегии покупателя и не заменяет его пароль.

Пароль передаётся через закрытый файл, а не аргумент командной строки. На Linux установите для файла права0600, на Windows ограничьте доступ ACL текущим оператором. Используйте секрет-хранилище или ввод в редакторе; не вставляйте реальный пароль в shell history. Пример после настройки `DATABASE_URL` или `DATABASE_URL_FILE` и выполнения миграций:

```sh
cd backend
go run ./cmd/accounts --email staff@your-owned-domain --name "Сотрудник" --role staff --cafe 1 --password-file /private/path/staff-password
```

В Docker тот же инструмент входит в образ как `/app/coffee-accounts`. Для admin используйте `--role admin` и не передавайте `--cafe`. Команда выводит профиль без пароля и токена; `email_verified` остаётся false. Создание сотрудника не подтверждает владение его почтой. Ввод/сброс забытых паролей и изменение существующих ролей требуют отдельного административного процесса.

## Локальная сеть и будущий HTTPS

Локальный Compose публикует API и кабинет на loopback: `API_BIND=127.0.0.1`, `ADMIN_BIND=127.0.0.1`. Для Xiaomi через USB используйте `adb reverse`; открывать сеть не требуется. Для явной проверки телефона по LAN задайте `API_BIND=0.0.0.0`, ограничьте Windows Firewall своей доверенной сетью и используйте фактический адрес компьютера. Демонстрационные пароли предназначены для закрытого development-стенда.

`deploy/compose.https.yaml` — отдельная конфигурация будущего развёртывания из двух существующих сервисов: nginx кабинета завершает TLS, Go API доступен только внутри Docker-сети. Не объединяйте её с development `compose.yaml`. Шаблон не содержит PostgreSQL без TLS: нужен настоящий внешний PostgreSQL с серверным сертификатом и CA, которые подходят его DNS-имени.

1. Скопируйте `.env.https.example` в `.env.https.local`; заполните реальный HTTPS origin и абсолютные пути к секретам вне репозитория. Реального production endpoint проект не создаёт.
2. Подготовьте действительный `fullchain.pem`, соответствующий private key и процесс обновления сертификата. nginx принимает TLS1.2/1.3; публичный HTTP API порт отсутствует.
3. В `database_url` храните PostgreSQL URL с URL-кодированными реквизитами, фактическим hostname и `sslmode=verify-full&sslrootcert=/run/secrets/database_ca`. Файл `database_ca.pem` должен доверять сертификату БД; hostname сертификата должен совпадать с URL. Обычный локальный контейнер PostgreSQL17 без TLS этому требованию не соответствует.
4. Для будущего сервера команды ниже выполняются оператором; в этой задаче deployment не выполнялся:

```sh
docker compose --env-file .env.https.local -f deploy/compose.https.yaml build
docker compose --env-file .env.https.local -f deploy/compose.https.yaml run --rm --no-deps api /app/coffee-migrate
# Первый запуск API заполняет каталог и кофейни без демонстрационных аккаунтов.
docker compose --env-file .env.https.local -f deploy/compose.https.yaml up -d --wait api
docker compose --env-file .env.https.local -f deploy/compose.https.yaml run --rm --no-deps --user 0:0 -v /private/path/staff-password:/run/secrets/new_password:ro api /app/coffee-accounts --email staff@your-owned-domain --name "Сотрудник" --role staff --cafe 1 --password-file /run/secrets/new_password
docker compose --env-file .env.https.local -f deploy/compose.https.yaml up -d --wait
```

На пустой БД миграции создают только схему; staff можно создать после первого успешного запуска API, когда уже существует активная кофейня. API работает от пользователя `coffee`: настройте права Linux-файлов `database_url` и CA так, чтобы этот пользователь контейнера мог их читать, сохранив закрытый доступ на хосте. Одноразовая команда accounts в примере использует root внутри контейнера, чтобы прочитать смонтированный пароль с правами 0600 без изменения владельца или разрешений файла; это не меняет пользователя работающего API.

`DATABASE_URL_FILE` и `DEMO_PASSWORD_FILE` читают максимум8КиБ, удаляя только завершающий перевод строки. Одновременные `KEY` и `KEY_FILE` запрещены, чтобы не использовать неожиданный секрет. `SEED_DEMO=false`, `AUTO_MIGRATE=false` и production-проверка verify-full обязательны. Файлы секретов, TLS private keys и резервные копии нельзя коммитить. API намеренно не доверяет произвольному `X-Forwarded-For`; auth limiter за nginx разделяется между его клиентами, что допустимо для малочисленного пилота, но требует отдельного решения перед публичным запуском.

## Резервная копия с зафиксированным основанием проверки

`scripts/backup.sh` и `scripts/backup.ps1` экспортируют PostgreSQL snapshot в read-only repeatable-read транзакции. `pg_dump --snapshot` и fingerprint читают один снимок. Рядом с `.dump` сохраняется `.dump.fingerprint`, включающий пользователей, сессии, quotes, заказы/позиции, ledger/events, каталог и миграции. Проверка восстановления сравнивает отдельную БД с этим сохранённым основанием; новая активность живой БД после backup не вызывает ложного несовпадения. Такой механизм соответствует [PostgreSQL synchronized snapshot](https://www.postgresql.org/docs/current/app-pgdump.html).

Uploads неизменяемы через API; архив создаётся после DB snapshot и может содержать дополнительные ещё не использовавшиеся изображения. Архив получает SHA-256 sidecar. Храните dump, baseline и архив вместе с ограниченным доступом; shell backup устанавливает umask077. Резервные копии содержат персональные данные и хеши, даже если действующие session tokens не сохраняются открыто.

`restore-check.sh`/`.ps1` требуют новую БД с префиксом `mastercoffee_restore_`, отказываются перезаписывать существующую и не меняют live DB. После восстановления проверяются fingerprint и следующие значения order/identity sequences, чтобы новая запись не столкнулась с существующим ID. Для старых dump без baseline автоматическая проверка прекращается явно. Восстановление изображений выполняйте в новую директорию с проверкой SHA-256 и содержимого архива; затем проверьте ссылки stories/изображений и загрузку файлов через API перед переключением volume.
