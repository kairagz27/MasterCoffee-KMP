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

Run `go test ./...` and `go vet ./...` from backend. PostgreSQL integration tests require `TEST_DATABASE_URL`, point only to a disposable test database; tests create isolated schemas and drop only their schema. Production migrations should run with a separate DDL credential before API startup; production secret rotation, email verification/reset, staff provisioning, refunds, distributed rate limiting, retention policies and security review remain pilot preparation work.
