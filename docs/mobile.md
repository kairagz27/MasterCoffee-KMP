# Mobile MVP

Android and shared Kotlin Multiplatform logic are in `composeApp/`; `iosArm64` and `iosSimulatorArm64` remain configured. The existing Master Coffee teal palette, Montserrat fonts, drink/cafe images, categories, sizes, sugar, takeaway/dine-in, temperature, syrup/essence/milk options, RU/KK/EN and light/dark/system themes are retained. Known catalogue translations were moved from the original dictionary into resource XML; newly authored catalogue content uses its server-provided text until translations are supplied.

## Source migration map

The original 3,334-line `App.kt` mixed Compose, state, catalogue fixtures and network writes. Firebase usage was: `stories` documents (`url`, `active`) read and uploaded from an in-app administrator screen; `orders` collection written after local success, then read without ownership filtering for history and receipt count; `Firebase.auth.currentUser.uid` used for display with a fabricated fallback ID; Android Firebase Storage used for profile-image uploads while iOS returned an empty string. Cloudinary used a public cloud/preset URL for story uploads. Local preferences contained `isLoggedIn`, profile fields, theme/language, cart and story caches. Any four OTP digits enabled local login. No live Firebase data was accessed during replacement.

These callers have been replaced with the Go API, server sessions and owner-scoped history/loyalty. Google Services, Firebase Auth/Firestore/Storage, coroutines-play-services, Cloudinary upload code, Peekaboo, Kamel and the common CIO engine were removed. One Coil image loader remains for server story images; bundled images are mapped from stable string keys in the UI. There is no profile photo uploader, in-app administrator, MC Coins, referral, promotional redemption, fake OTP or customer payment-confirmation control in this MVP. Staff/admin operations are in the web cabinet. The previous user whitespace change is retained in `docs/preexisting-user-change.patch`; the original source is backed up outside the checkout.

## Layers and session lifecycle

`domain/` holds immutable business models and the repository contract. `data/` holds separate serializable DTOs, Ktor, public catalogue/cart persistence and platform services. `presentation/` holds a ViewModel/StateFlow, resource-backed copy and screens. `App.kt` only composes dependencies, theme and lifecycle observers. There is no global mutable order list or GlobalScope.

Android uses Ktor OkHttp; iOS uses Darwin. Requests have connection/socket/request timeouts, no credential logging and no redirect forwarding. The client closes with its ViewModel. A saved token is validated by `/auth/me` before displaying an account. Android saves only AES-GCM ciphertext in private preferences with a non-exportable Android Keystore key; backups are disabled. iOS uses a `kSecClassGenericPassword`, `WhenUnlockedThisDeviceOnly` Keychain item. Ordinary settings contain only UI preferences and public catalogue/account-scoped cart/pending request data. Logout erases tokens, cart, pending keys, history, loyalty and selected order and cancels account work. Revocation is attempted server-side; an offline logout still removes the local credential, while the unreachable server session expires naturally.

Public catalogue cache is scoped by cafe. It can be shown offline with an explicit stale-cache message. Cart changes are persisted per account. Quotes and successful orders are never fabricated from cache. Before creating an order the client persists `{quote_id, idempotency_key}`. An uncertain network result retains this exact pair and the cart, including after process death. Retry recovers the server's original order. Only a successful server response clears the cart. A changed/expired quote requires a new server calculation and explicit confirmation. Price amounts and loyalty are always server supplied in whole KZT; timestamps arrive in UTC and are displayed in the device timezone.

Active orders poll every 5 seconds while the screen lifecycle is started. Preparation and payment are displayed separately. A QR uses only the server payload and expiry. The customer can refresh an unpaid QR or cancel a created unpaid order; only authenticated staff can record payment or handoff. The server is authoritative about every transition.

## API address and build

Use JDK 21, Android SDK 36 and Gradle wrapper. The default debug URL is `http://10.0.2.2:8080/api/v1` for Android Emulator. Override with a Gradle project property:

```powershell
.\gradlew.bat :composeApp:assembleDebug :composeApp:testDebugUnitTest
# USB phone:
adb reverse tcp:8080 tcp:8080
.\gradlew.bat :composeApp:assembleDebug -PmasterCoffeeApiUrl=http://127.0.0.1:8080/api/v1
# Phone on the same LAN; use this machine's actual address:
.\gradlew.bat :composeApp:assembleDebug -PmasterCoffeeApiUrl=http://192.168.1.10:8080/api/v1
# Release must receive a real HTTPS endpoint:
.\gradlew.bat :composeApp:assembleRelease -PmasterCoffeeApiUrl=https://YOUR-API-HOST/api/v1
```

Local HTTP is allowed by Android's manifest only in debug; a release client also rejects a non-HTTPS URL. Release signing/distribution is not configured. The debug profile shows its compiled API address. The APK is `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

iOS uses `MasterCoffeeAPIURL` from Info.plist, expandable via the `MASTER_COFFEE_API_URL` Xcode build setting. Debug falls back to `http://127.0.0.1:8080/api/v1` for a local iOS Simulator; release needs a real HTTPS value. Local networking is permitted in ATS, but arbitrary insecure remote loads are not. No macOS/Xcode was available on this Windows host: neither iOS compilation nor Keychain/Darwin/UI runtime behavior has been verified.

## Verification

All 12 client tests pass: successful quote/confirmation, preserving cart on failure, durable identical-key retry after timeout and process recreation, explicit fresh review after price changes, expired session validation, logout/account isolation and cancelled late responses, offline cache, lifecycle polling including cancellation of an in-flight request, API identifier-only quote serialization, typed errors/timeouts and production HTTPS enforcement. Android compile/APK succeeded. See `docs/verification.md` for emulator status. The first attempt to execute the new unit suite was interrupted by host disk/pagefile exhaustion; after releasing the build processes, the complete suite and APK build passed using a 768 MiB single-use Gradle JVM, in-process Kotlin and a 128 MiB test JVM.

UI uses scrollable forms/dialogs, safe insets, keyboard padding, flexible text heights, labelled controls and a single menu column on narrow screens or increased font scale. These implementations still need actual small-screen, font-scale and TalkBack review before pilot; no claim of certified accessibility is made.
