# Master Coffee

[Проверки текущей ветки](https://github.com/kairagz27/MasterCoffee-KMP/actions/workflows/ci.yml?query=branch%3Acodex%2Flocal-mvp) · [PR #1](https://github.com/kairagz27/MasterCoffee-KMP/pull/1)

Локальный MVP заказа кофе: общее приложение Kotlin Multiplatform, Go API, PostgreSQL и кабинет сотрудника. Покупатель выбирает кофейню и напиток, проверяет серверный расчёт и создаёт заказ. Сотрудник подтверждает оплату на кассе, приготовление и выдачу. Клиент видит фактические статусы, историю и свою лояльность.

Онлайн-эквайринг, MC Coins, рефералы и промокоды отключены. Firebase и Cloudinary не требуются; живые Firebase-данные не читались и не переносились. Это локальный MVP, не готовый production-релиз.

## Структура

- `composeApp/` — Compose UI, domain/data/presentation, ViewModel + StateFlow, Ktor, защищённое хранение токенов Android/iOS.
- `iosApp/` — сохранённый Xcode target.
- `backend/` — Go/chi: handler → service → repository; SQL-миграции, seed, тесты.
- `admin/` — статический кабинет, nginx с прокси API.
- `compose.yaml` — PostgreSQL, API и кабинет с healthchecks и постоянными volumes.
- `docs/` — [архитектура и схема](docs/architecture.md), [аудит](docs/audit.md), [API/OpenAPI](docs/openapi.yaml), [backend](docs/backend.md), [мобильное приложение](docs/mobile.md), [результаты проверок](docs/verification.md).

Маршрут для изучения кода: [learning-guide.md](docs/learning-guide.md). Короткие объяснения компромиссов: [decisions.md](docs/decisions.md). Повторная проверка недостатков и границ среды: [quality-audit.md](docs/quality-audit.md).

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

Локальный Compose подставляет `POSTGRES_PASSWORD` в URL подключения: выбирайте длинный случайный пароль из URL-safe букв, цифр, `-` и `_`. Для собственного PostgreSQL URL специальные символы реквизитов нужно URL-кодировать; HTTPS-шаблон читает готовый URL из файла.

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

Пароль по умолчанию в примере: `LocalCoffee-2026!`. Изменение `DEMO_PASSWORD` после первого запуска не меняет уже созданные пароли. Для нового покупателя доступна регистрация в приложении; пароль 12–72 байта UTF-8. Email не считается подтверждённым. Production отказывается запускаться с включённым `SEED_DEMO`, автоматическими миграциями, незащищённым подключением БД и HTTP CORS origins. Уже существующие демо-аккаунты автоматически не удаляются: development-БД с такими учётными записями нельзя переносить в публичный стенд. [Будущий HTTPS-запуск, файловые секреты и CLI создания сотрудников](docs/backend.md#локальная-сеть-и-будущий-https) подготовлены отдельно; публичное развёртывание не выполнялось.

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

Для LAN также задайте `API_BIND=0.0.0.0` в `.env`: по умолчанию контейнеры API и кабинета доступны только с локального компьютера. Для USB не требуется открывать порт в LAN.

### Xiaomi 15 / Android 16 через USB

Сначала `adb devices -l` должен показать физический телефон в состоянии `device`. `unauthorized` означает, что на телефоне ещё не разрешена отладка этого компьютера. Не удаляйте приложение ради установки новой debug-сборки.

Подготовьте APK с адресом USB reverse:

```powershell
.\gradlew.bat :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest -PmasterCoffeeApiUrl=http://127.0.0.1:8080/api/v1
New-Item -ItemType Directory -Force artifacts/android-phone | Out-Null
Copy-Item composeApp/build/outputs/apk/debug/composeApp-debug.apk artifacts/android-phone/composeApp-phone-debug.apk
.\scripts\check-xiaomi.ps1 -Install
```

Скрипт проверяет устройство и готовность API, настраивает `adb reverse tcp:8080 tcp:8080`, обновляет приложение через `install -r` и запускает его. Он не выполняет uninstall, clear data, очистку Keystore или AVD. При нескольких телефонах передайте `-Serial`. Ошибка несовместимой подписи не исправляется удалением данных: пересоберите APK с тем же локальным debug keystore. CI APK может иметь другую debug-подпись, поэтому для обновления уже установленного Android Studio приложения используйте локальную сборку.

Для управляемых Compose UI-тестов и отдельного настоящего API-сценария на подключённом телефоне:

```powershell
$env:DEMO_PASSWORD = 'LocalCoffee-2026!' # ваш локальный development seed password
.\scripts\check-xiaomi.ps1 -RunTests
```

Тестовый host использует изолированные хранилища и не очищает существующий аккаунт приложения. Live-тест регистрирует отдельного покупателя и проводит оплату/выдачу через API сотрудника. Артефакты находятся в игнорируемом `artifacts/device-*`. Результат этих тестов не заменяет ручную проверку настоящего MainActivity на Xiaomi, физической камеры и TalkBack.

### Release и R8

Release требует явно переданный HTTPS URL, включает R8 и resource shrinking. Адрес production-сервера не зашит в исходники:

```powershell
.\gradlew.bat :composeApp:lintRelease :composeApp:assembleRelease -PmasterCoffeeApiUrl=https://YOUR-REAL-API-HOST/api/v1
```

Без HTTPS-адреса сборка должна завершиться ошибкой конфигурации. Для автоматической проверки компиляции CI передаёт `https://localhost/api/v1`; это тест конфигурации/R8, без заявления о работающем production API. Release APK остаётся unsigned до отдельной настройки подписи. Метрики запуска и прокрутки по debug-сборке не заявляются.

Приложение сохраняет публичный каталог и корзину, привязанную к аккаунту. Без сети доступен кеш каталога; успешное создание заказа не имитируется. При тайм-ауте повторяется тот же запрос с сохранённым ключом. При выходе очищаются токен, корзина, ожидающий запрос и приватное UI-состояние.

Синхронные операции платформенного хранения и разбор кеша выполняются за `BackgroundStorage` на фоновом dispatcher. Форматы существующих корзин/pending сохраняются. Back возвращает к меню, черновик параметров переживает пересоздание Activity, а аккаунт восстанавливает выбранный экран и кофейню. Поколение запросов и отмена корутин защищают от поздних ответов после выхода.

## Демонстрация между двумя ролями

1. В приложении зарегистрируйте покупателя или войдите демо-аккаунтом.
2. Выберите кофейню №1, напиток, объём и добавки; откройте корзину.
3. Запросите расчёт сервера, проверьте сумму и подтвердите заказ.
4. Войдите в кабинет как `staff@mastercoffee.local`; найдите заказ в очереди, по номеру либо QR. Камера доступна в поддерживаемом браузере на localhost/HTTPS; ручной ввод работает без камеры.
5. Получите оплату на кассе и подтвердите её. Переведите заказ последовательно: принят → готовится → готов → выдан.
6. Приложение получает статусы сервера. После выдачи заказ остаётся в истории, а подходящий чек учитывается в лояльности один раз.

QR действует 30 минут, содержит только непредсказуемый серверный токен. Просроченный QR можно обновить из приложения. Повторно использованный QR и QR чужой кофейни отклоняются. Сотрудник может найти заказ по номеру и подтвердить фактически полученную оплату без сканера. Клиент не может подтвердить оплату самостоятельно.

### Реальные кадры с Xiaomi 15

25 октября 2026: пользователь оформил MC-001111 в гибридном интерфейсе настоящего MainActivity; сотрудник обработал его в кабинете. Телефон получил готовность и выдачу, лояльность выросла до двух завершённых чеков. [Галерея трёх свежих снимков](docs/evidence/2026-10-25/index.html) открывается в браузере после клонирования; [этапы кабинета](admin/evidence/2026-10-25/phone-hybrid-cabinet/journey.json) сохранены отдельно. [Непрерывная запись готовности → выдачи](docs/evidence/2026-10-25/hybrid-handoff.mp4) длится около минуты; статус меняется на 23-й секунде.

<p>
  <img src="docs/evidence/2026-10-25/02-created-qr.png" width="240" alt="Xiaomi: создан серверный заказ MC-001111, сумма 863 тенге">
  <img src="docs/evidence/2026-10-25/04-ready-paid.png" width="240" alt="Xiaomi: MC-001111 готов к выдаче и оплачен">
  <img src="docs/evidence/2026-10-25/05-completed-paid.png" width="240" alt="Xiaomi: MC-001111 выдан, оплачен и появился в истории">
</p>

Отдельно проверена потеря ответа уже созданного MC-001110: тайм-аут → завершение процесса → восстановленный pending → тот же заказ без дубликата. После проверки неоплаченный заказ отменён. [Снимок сохранённого запроса](docs/evidence/2026-10-25/10-pending-after-process-restart.png), [финальная отмена](docs/evidence/2026-10-25/12-cancelled-recovery-order.png), [сравнение PostgreSQL до/после](docs/evidence/2026-10-25/recovery-proof.json). Подробные границы ручных и автоматических проверок — в [отчёте](docs/verification.md).

Администратор может изменять меню, доступность по кофейням, создавать/выключать stories и загружать PNG/JPEG до 5 МиБ и 4096×4096. Изображения сохраняются в Docker volume. [Кабинет и ограничения камеры](admin/README.md).

## Деньги и лояльность

Все суммы — целые тенге (`KZT`, `whole_kzt`). Сервер рассчитывает цену и округляет итоговую сумму напитков до целого тенге по правилу half up; еда не получает скидку. При изменении меню между расчётом и подтверждением сервер требует новый расчёт. История хранит снимки названий, параметров и цен.

Исходные уровни сохранены: 3% до 50 подходящих чеков, 5% от 50, 7% от 70, 10% от 100. Один оплаченный и выданный чек с кофейным/чайным напитком учитывается один раз; сок сам по себе не повышает уровень. Флаг `loyalty_eligible` задан явно; исходная категория «Чаи» сохранена, включая глинтвейн. Скидка действует на все товары с `is_drink`, включая добавки. Это уточнение опубликованных исходных правил, а не перенос ошибочного общего счётчика Firebase. Подробности и неоднозначности каталога — в [backend.md](docs/backend.md).

Отменить можно только неоплаченный заказ в состоянии created. Возвраты после оплаты и эквайринг не реализованы.

## Проверки и CI

```powershell
# Мобильные common-тесты на Android JVM + debug APK
.\gradlew.bat :composeApp:testDebugUnitTest :composeApp:lintDebug :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest

# Compose instrumentation на уже подключённом устройстве; live-тест включается отдельно
.\gradlew.bat :composeApp:connectedDebugAndroidTest

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

Android CI дополнительно проверяет Lint и release/R8, сохраняет отдельный USB-phone APK и пару app/test APK. Отдельная job запускает Android 16 emulator: настоящий MainActivity проверяет смену A/B и Keystore, smoke — холодный запуск/rotation/background и системные иконки, управляемый Compose host — Back/языки/темы/шрифт 135%. Live-тест проходит полный UI-заказ с настоящим API/PostgreSQL. Успех определяется по JUnit и обязательным маркерам завершения: нулевой shell exit code `am instrument` сам по себе не считается успехом. Скриншоты и отчёты публикуются как CI artifacts; локальная проверка Xiaomi указывается отдельно. Native MainActivity A/B fixture включается только на чистом эмуляторе и пропускается на физических телефонах; managed live-тест имеет отдельное явное включение.

OpenAPI локально: `pip install openapi-spec-validator==0.7.2`, затем `python -m openapi_spec_validator docs/openapi.yaml`.

## Резервная копия и восстановление

PowerShell из корня:

```powershell
.\scripts\backup.ps1
$taskBackup = Get-ChildItem .\backups -Filter 'mastercoffee-*.dump' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
.\scripts\restore-check.ps1 -BackupFile $taskBackup.FullName -DatabaseName mastercoffee_restore_check
```

Linux; на macOS для shell backup дополнительно нужен `sha256sum` из GNU coreutils:

```sh
sh scripts/backup.sh
task_backup=$(ls -t backups/mastercoffee-*.dump | head -n 1)
sh scripts/restore-check.sh "$task_backup" mastercoffee_restore_check
```

Backup создаёт `pg_dump -Fc`, fingerprint из того же PostgreSQL snapshot и отдельный tar.gz volume изображений с SHA-256. Restore check сравнивает новую БД с сохранённым fingerprint, включая quotes, и проверяет последовательности; изменения live DB после backup не вызывают ложную ошибку. Имя новой БД должно начинаться с `mastercoffee_restore_`; существующая БД не перезаписывается. [Гарантии и ограничения backup](docs/backend.md#резервная-копия-с-зафиксированным-основанием-проверки).

Для переключения на восстановленную БД измените `POSTGRES_DB` в локальном `.env` и пересоздайте контейнеры с сохранением volumes. Для изображений сначала остановите API, скопируйте проверенный архив в контейнер и распакуйте каталог `uploads` под `/data`; заранее сохраните текущий volume. Не публикуйте backup — он содержит пользователей и хеши паролей. Непроверенный архив не считается рабочей резервной копией.

## Ограничения до пилота

Нужны отдельные проверка iOS на macOS/Xcode и физических телефонах, камера и доступность на устройствах, email verification/password reset, реальное HTTPS-развёртывание и production-подпись, ротация секретов, мониторинг/retention и нагрузочная проверка. CLI создаёт новых сотрудников, но не повышает роль существующего покупателя. При nginx все входы разделяют IP rate-limit процесса; топологию доверенного прокси следует настроить перед многопользовательским пилотом. Локальная кассовая отметка не заменяет платёжного провайдера и кассовый учёт.

Гибрид исходного дизайна: Montserrat, фирменный teal и изображения, боковая шторка, плавающая навигация с тенью и фотофон лояльности соединены с новыми состояниями заказа. Для светлой темы логотип и fallback изображений имеют контрастный фирменный цвет. Свежие кадры и границы проверок находятся в [отчёте](docs/verification.md).
