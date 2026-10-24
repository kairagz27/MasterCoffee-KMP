# Master Coffee

Локальный MVP заказа кофе: общее приложение Kotlin Multiplatform, Go API, PostgreSQL и кабинет сотрудника. Покупатель выбирает кофейню и напиток, проверяет серверный расчёт и создаёт заказ. Сотрудник подтверждает оплату на кассе, приготовление и выдачу. Клиент видит фактические статусы, историю и свою лояльность.

Онлайн-эквайринг, MC Coins, рефералы и промокоды отключены. Firebase и Cloudinary не требуются; живые Firebase-данные не читались и не переносились. Это локальный MVP, не готовый production-релиз.

## Структура

- `composeApp/` — Compose UI, domain/data/presentation, ViewModel + StateFlow, Ktor, защищённое хранение токенов Android/iOS.
- `iosApp/` — сохранённый Xcode target.
- `backend/` — Go/chi: handler → service → repository; SQL-миграции, seed, тесты.
- `admin/` — статический кабинет, nginx с прокси API.
- `compose.yaml` — PostgreSQL, API и кабинет с healthchecks и постоянными volumes.
- `docs/` — [архитектура и схема](docs/architecture.md), [аудит](docs/audit.md), [API/OpenAPI](docs/openapi.yaml), [backend](docs/backend.md), [мобильное приложение](docs/mobile.md), [результаты проверок](docs/verification.md).

Backend импортирован с полной историей Git subtree из [master-coffee-go](https://github.com/kairagz27/master-coffee-go), SHA `e21c4058e5960e193e770e0e8cb6700ac3f8c9da`; [подробности](docs/backend-origin.md). Вложенного `.git` нет; исходный репозиторий сохранён.

## Требования

- Docker Desktop/Engine с Compose v2, Linux containers; интернет для первой загрузки образов.
- Для Android: JDK 21, Android SDK 36, Android Studio с KMP/Compose, устройство Android 8.0/API 26+ или эмулятор. Gradle wrapper 8.14.3 включён.
- Kotlin/serialization 2.3.0, Compose 1.10.0, AGP 8.13.2.
- Для запуска Go вне Docker: Go 1.27.1 и PostgreSQL 17.
- Node.js 22+ для тестов кабинета и сквозного HTTP-сценария; npm-зависимостей у кабинета нет.
- Для iOS нужны macOS/Xcode. На Windows iOS не собирался и не запускался.
- Рекомендуется не менее 10–15 ГБ свободного диска и 16 ГБ RAM. На ограниченной машине запускайте Gradle и Docker-сборки последовательно.

## Запуск API, БД и кабинета

Из корня репозитория в PowerShell:

```powershell
Copy-Item .env.example .env
# Измените локальные пароли в .env до доступа из сети.
docker compose up -d --build --wait
docker compose ps
Invoke-RestMethod http://localhost:8080/readyz
```

В Linux/macOS вместо `Copy-Item` используйте `cp .env.example .env`.

- Кабинет: [localhost:8081](http://localhost:8081).
- API: [localhost:8080/healthz](http://localhost:8080/healthz), готовность [readyz](http://localhost:8080/readyz).
- Каталог: [api/v1/catalog](http://localhost:8080/api/v1/catalog?cafe_id=1).
- PostgreSQL для локальных Go-тестов: `127.0.0.1:55432` (порт можно изменить через `DB_PORT`).

`.env.example` содержит только демонстрационные локальные пароли. `APP_ENV=development`, `AUTO_MIGRATE=true` применяют миграции при старте; seed создаёт недостающий каталог. Демо-аккаунты создаются только при явном `SEED_DEMO=true`, используют `DEMO_PASSWORD` из `.env`:

| Роль | Email | Доступ |
| --- | --- | --- |
| Покупатель | customer@mastercoffee.local | Свои заказы и профиль |
| Сотрудник | staff@mastercoffee.local | Кофейня №1 |
| Администратор | admin@mastercoffee.local | Все кофейни, меню и stories |

Пароль по умолчанию в примере: `LocalCoffee-2026!`. Изменение `DEMO_PASSWORD` после первого запуска не меняет уже созданные пароли. Для нового покупателя доступна регистрация в приложении; пароль 12–72 байта UTF-8. Email не считается подтверждённым. Production отказывается запускаться с демо-аккаунтами, автоматическими миграциями, незащищённым подключением БД и HTTP CORS origins. Production HTTPS и отдельное создание сотрудников требуют подготовки перед пилотом.

Миграции вручную:

```powershell
docker compose run --rm api /app/coffee-migrate
```

Не редактируйте применённые SQL-файлы: контрольная сумма проверяется. Добавляйте новую миграцию. Seed не перезаписывает изменения оператора.

Остановка и последующий запуск с сохранением данных:

```powershell
docker compose down
docker compose up -d --wait
```

Не используйте `docker compose down -v`, если нужно сохранить БД и изображения.

## Android

Откройте корень проекта в Android Studio и задайте JDK 21. `local.properties` должен содержать путь к вашему Android SDK; файл не коммитится. Для Windows можно использовать JBR из Android Studio:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :composeApp:testDebugUnitTest :composeApp:assembleDebug --console=plain
```

APK: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`. Это debug APK, не подписанный production release.

```powershell
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

По умолчанию debug API: `http://10.0.2.2:8080/api/v1` — Android Emulator. Адрес задаётся при сборке:

```powershell
# Телефон через USB
adb reverse tcp:8080 tcp:8080
.\gradlew.bat :composeApp:assembleDebug -PmasterCoffeeApiUrl=http://127.0.0.1:8080/api/v1

# Телефон и компьютер в одной LAN; замените адрес компьютера
.\gradlew.bat :composeApp:assembleDebug -PmasterCoffeeApiUrl=http://192.168.1.10:8080/api/v1

# Будущий HTTPS-сервер
.\gradlew.bat :composeApp:assembleRelease -PmasterCoffeeApiUrl=https://coffee.example/api/v1
```

После смены адреса установите новый APK. Для LAN нужен доступ к порту API через локальный firewall; не открывайте локальный demo-стенд в интернет. HTTP разрешён только debug-сборке; release требует HTTPS. Настройка iOS API URL описана в [mobile.md](docs/mobile.md).

Приложение сохраняет публичный каталог и корзину, привязанную к аккаунту. Без сети доступен кеш каталога; успешное создание заказа не имитируется. При тайм-ауте повторяется тот же запрос с сохранённым ключом. При выходе очищаются токен, корзина, ожидающий запрос и приватное UI-состояние.

## Демонстрация между двумя ролями

1. В приложении зарегистрируйте покупателя или войдите демо-аккаунтом.
2. Выберите кофейню №1, напиток, объём и добавки; откройте корзину.
3. Запросите расчёт сервера, проверьте сумму и подтвердите заказ.
4. Войдите в кабинет как `staff@mastercoffee.local`; найдите заказ в очереди, по номеру либо QR. Камера доступна в поддерживаемом браузере на localhost/HTTPS; ручной ввод работает без камеры.
5. Получите оплату на кассе и подтвердите её. Переведите заказ последовательно: принят → готовится → готов → выдан.
6. Приложение получает статусы сервера. После выдачи заказ остаётся в истории, а подходящий чек учитывается в лояльности один раз.

QR действует 30 минут, содержит только непредсказуемый серверный токен. Просроченный QR можно обновить из приложения. Повторно использованный QR и QR чужой кофейни отклоняются. Сотрудник может найти заказ по номеру и подтвердить фактически полученную оплату без сканера. Клиент не может подтвердить оплату самостоятельно.

Администратор может изменять меню, доступность по кофейням, создавать/выключать stories и загружать PNG/JPEG до 5 МиБ и 4096×4096. Изображения сохраняются в Docker volume. [Кабинет и ограничения камеры](admin/README.md).

## Деньги и лояльность

Все суммы — целые тенге (`KZT`, `whole_kzt`). Сервер рассчитывает цену и округляет итоговую сумму напитков до целого тенге по правилу half up; еда не получает скидку. При изменении меню между расчётом и подтверждением сервер требует новый расчёт. История хранит снимки названий, параметров и цен.

Исходные уровни сохранены: 3% до 50 подходящих чеков, 5% от 50, 7% от 70, 10% от 100. Один оплаченный и выданный чек с кофейным/чайным напитком учитывается один раз; сок сам по себе не повышает уровень. Флаг `loyalty_eligible` задан явно; исходная категория «Чаи» сохранена, включая глинтвейн. Скидка действует на все товары с `is_drink`, включая добавки. Это уточнение опубликованных исходных правил, а не перенос ошибочного общего счётчика Firebase. Подробности и неоднозначности каталога — в [backend.md](docs/backend.md).

Отменить можно только неоплаченный заказ в состоянии created. Возвраты после оплаты и эквайринг не реализованы.

## Проверки и CI

```powershell
# Мобильные common-тесты на Android JVM + debug APK
.\gradlew.bat :composeApp:testDebugUnitTest :composeApp:assembleDebug

# Go; интеграционные тесты требуют отдельную тестовую БД
cd backend
go vet ./...
$env:TEST_DATABASE_URL = 'postgres://coffee:local-coffee-db-change-me@localhost:55432/mastercoffee_test?sslmode=disable'
go test -count=1 ./...
cd ..

# Кабинет
node --test admin/tests/*.test.mjs

# Полный HTTP-сценарий покупателя и сотрудника на запущенном local demo
$env:DEMO_PASSWORD = 'LocalCoffee-2026!'
node scripts/e2e.mjs
```

Перед первым Go integration run создайте отдельную БД:

```powershell
docker compose exec -T db createdb -U coffee mastercoffee_test
```

Тесты создают изолированные схемы и удаляют только свои схемы. Без `TEST_DATABASE_URL` интеграционные тесты пропускаются: это не доказательство проверки PostgreSQL. На Linux CI запускает `go test -race` с PostgreSQL service. CI также валидирует OpenAPI, запускает кабинетные/common-тесты, собирает debug APK, проходит Compose e2e и проверяет backup/restore. [Фактически проведённые проверки](docs/verification.md) отделены от настроенных CI-проверок.

OpenAPI локально: `pip install openapi-spec-validator==0.7.2`, затем `python -m openapi_spec_validator docs/openapi.yaml`.

## Резервная копия и восстановление

PowerShell из корня:

```powershell
.\scripts\backup.ps1
.\scripts\restore-check.ps1 -BackupFile .\backups\mastercoffee-YYYYMMDD-HHMMSS.dump -DatabaseName mastercoffee_restore_check
```

Linux/macOS:

```sh
sh scripts/backup.sh
sh scripts/restore-check.sh backups/mastercoffee-YYYYMMDD-HHMMSS.dump mastercoffee_restore_check
```

Backup создаёт `pg_dump -Fc` и отдельный tar.gz volume изображений. Восстановление выполняется в новую отдельную БД; существующая БД не перезаписывается. Скрипт отказывается повторно использовать существующее имя. Убедитесь, что количества записей и API-сценарий восстановленной копии корректны, прежде чем переключать сервер.

Для переключения на восстановленную БД измените `POSTGRES_DB` в локальном `.env` и пересоздайте контейнеры с сохранением volumes. Для изображений сначала остановите API, скопируйте проверенный архив в контейнер и распакуйте каталог `uploads` под `/data`; заранее сохраните текущий volume. Не публикуйте backup — он содержит пользователей и хеши паролей. Непроверенный архив не считается рабочей резервной копией.

## Ограничения до пилота

Нужны отдельные проверка iOS на macOS/Xcode и физических телефонах, камера и доступность на устройствах, email verification/password reset, безопасное создание сотрудников, HTTPS, ротация секретов, мониторинг/retention и нагрузочная проверка. При nginx все входы разделяют IP rate-limit процесса; топологию доверенного прокси следует настроить перед многопользовательским пилотом. Локальная кассовая отметка не заменяет платёжного провайдера и кассовый учёт.

Исходный дизайн: Montserrat, бирюзовая тема и изображения сохранены. Старый скриншот репозитория отражает исходный интерфейс, не является доказательством новой сборки.
