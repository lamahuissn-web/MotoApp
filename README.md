# MotoApp — the collector's and the manager's phone

The Android app for the office's own generator panel (`lamahuissn-web/generator-app`). Kotlin and
Jetpack Compose, Arabic first, Material 3.

**Status: the first slice only.** The app builds, wears the office's colours in Arabic and English,
and reaches the office's real API through the Cloudflare tunnel — it proves the connection with the
server's own answer. The collector's screens (the sheet, the reading, the collection, the receipt)
and the manager's board are built on top of the client in `data/MotoApi.kt`.

## What it talks to

`https://gen.ispnet.online/api/v1/*` — compiled in as `BuildConfig.API_BASE_URL`, so a build points
at one server and nobody has to type an address on a phone at a door. The API itself is documented
by `specs/008-mobile-app/spec.md` in the panel's repository.

Two rules the app may never break, both inherited from the API and enforced by it:

- **The phone never computes money.** A balance, a price, a discount and a receipt number all come
  from the server; the app displays them and sends back only what the collector did.
- **A retry is not a second receipt.** Every money write carries an `Idempotency-Key` that is
  generated once per confirmed attempt and kept until the answer is known.

## Building

```bash
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

./gradlew :app:testDebugUnitTest    # the parsing the screens depend on
./gradlew :app:assembleDebug        # a debug APK
```

A release build needs a signing keystore that is deliberately **not** in this repository; the
keystore is what lets a new APK update the one already on the office's phones.

## Layout

```
app/src/main/java/com/lamahuissn/motoapp/
├── MainActivity.kt          one activity, every screen a Composable of it
├── data/MotoApi.kt          the HTTP client and the server's answer shape
└── ui/
    ├── ConnectionScreen.kt  the first slice: it reaches the server and says what came back
    └── theme/               the office's colours, Material 3, sized for a phone in the sun
app/src/test/               the unit tests (the answer shape, the refusal codes)
```
