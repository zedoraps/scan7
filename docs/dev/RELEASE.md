# Releasing Scan7

Scan7 is open source under the **GNU AGPL v3.0** ([LICENSE](../../LICENSE)), because it ships an
Ultralytics YOLO (AGPL-3.0) detector. Everything in the repo is set up for a Google Play release, a
GitHub release and (optionally) F-Droid without any secret in git. This file is the checklist of what
is configured and what the user still has to do by hand.

## What the build does

| Topic | How | Where |
|---|---|---|
| versionCode | `git rev-list --count HEAD` (commit count; full clone needed), override `-Pscan7.versionCode=N`, fallback 1 | `app/app/build.gradle.kts` |
| versionName | `scan7.versionName` (bump per release), override `-Pscan7.versionName=…` | `app/gradle.properties` |
| Source / privacy links | `scan7.sourceUrl`, `scan7.privacyUrl` become `BuildConfig.SOURCE_URL` / `PRIVACY_URL`, shown in About | `app/gradle.properties` (`zedoraps/scan7`) |
| Release signing | `signingConfigs.release` from `scan7.upload.*` Gradle properties or `SCAN7_UPLOAD_*` env vars; unsigned (with a warning) when absent | `app/app/build.gradle.kts` |
| Detector model | not in git; `./gradlew downloadModel` fetches the released `model.tflite` and checks its SHA-256; `checkModelAsset` fails the build early with that hint | `app/model.properties` |
| License texts | `copyLicenseTexts` copies `LICENSE` + `docs/third_party/*.txt` into the APK (`assets/licenses/`) for About & licenses | `app/app/build.gradle.kts` |
| Debug features | debug menu (5 taps on the wordmark), launch hooks (`debug_*` extras) and "Save scans for training" exist in **debug builds only**; release builds never write training photos | `BuildConfig.DEBUG` checks |
| Play / FOSS build | the flavorless default is the **Play build** with Google Play services Nearby (multiplayer *Share this game* / *Join a game*, beta); `-Pscan7.foss=true` builds without Play services (no multiplayer; F-Droid); `-Pscan7.flavors=true` adds both as flavors `play` (default) / `foss` instead | `app/app/build.gradle.kts`, `src/play/`, `src/foss/` |
| Backup | Auto Backup / device transfer include only the Room database and shared preferences; not `files/`, caches or the shared-storage training folders | `res/xml/backup_rules.xml`, `data_extraction_rules.xml` |

Build commands (from `app/`):
```sh
./gradlew bundleRelease      # app/build/outputs/bundle/release/app-release.aab  (Play)
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release(-unsigned).apk  (GitHub / sideload)
./gradlew downloadModel      # fresh clone: fetch assets/model.tflite (SHA-256 checked)
./gradlew -Pscan7.foss=true assembleRelease   # without Play services / multiplayer (F-Droid)
```

### Why a static About screen (not AboutLibraries / oss-licenses)
The app has few dependency families under three licenses (AGPL-3.0, Apache-2.0, OFL-1.1). The
license plugins need a Gradle plugin that has to keep up with AGP 9 (oss-licenses also pulls in Play
services, which F-Droid rejects) and add a UI library, for a list of six entries. The static list
lives in `ui/about/AboutScreen.kt` (`ThirdPartyNotices`). **Update it when you add a new dependency
family.** The license texts come from the repo files, so they have a single source.

### Why the model is a release asset (not in git, not LFS)
- Committing every model version to git (~10 MB each) bloats the history. The project decided
  against that earlier (AGENTS.md).
- Git LFS needs an extra tool for every contributor, costs LFS bandwidth on GitHub, and F-Droid's
  build server doesn't fetch LFS objects reliably.
- A GitHub release asset with a pinned SHA-256 (`app/model.properties`) is simple, reproducible
  (the build verifies the exact file) and keeps the git repo small. The cost is one network step
  after cloning (`./gradlew downloadModel`), which F-Droid has to accept (see F-Droid below).

On a model update: `cd ml && uv run scripts/export.py runs/<run>/weights/best.pt` (installs into the
app), upload `app/app/src/main/assets/model.tflite` to a new release (tag e.g. `model-ft_v2`), set
`model.url` + `model.sha256` (`shasum -a 256 …/model.tflite`) + `model.version` in
`app/model.properties`, and commit that together with `assets/model_version.txt`.

## Changelog (tenzir-ship)
The changelog lives in `changelog/`. [tenzir-ship](https://github.com/tenzir/ship) manages it
(Apache-2.0, run via `uvx`, no install). Each user-visible change gets one Markdown file in
`changelog/unreleased/`, and cutting a release moves these files into `changelog/releases/vX.Y.Z/`
(with `notes.md`). The config is `changelog/config.yaml` (no PR numbers, no authors). Write entries
for players, not developers: what changed in the app, no class names or refactors. Released files
are history. Never edit them; add a new entry instead.

**Per change** (together with the change; type `feature` | `change` | `bugfix` | `breaking`):
```sh
uvx tenzir-ship add --type bugfix --title "Torch stays on after a scan" \
  --description "The torch no longer turns off when you open the review."
uvx tenzir-ship validate
uvx tenzir-ship show unreleased --release --markdown   # preview the next notes
```
Titles are noun phrases in sentence case; extend an existing unreleased entry instead of adding a
second one for the same feature.

**Cutting a release** (clean tree on `main`):
```sh
uvx tenzir-ship release create --title "<title>" --intro "<2–3 sentences for players>"   # dry run
uvx tenzir-ship release create --title "<title>" --intro "<…>" --yes   # writes releases/vX.Y.Z/
v=$(uvx tenzir-ship release version --bare); v=${v#v}                # e.g. 0.1.0 (it prints v0.1.0)
sed -i '' "s/^scan7.versionName=.*/scan7.versionName=$v/" app/gradle.properties
git add changelog app/gradle.properties && git commit -m "Release v$v"
```
tenzir-ship picks the next version from the entry types. In 0.x, feature, change and breaking bump
the minor version and bugfix bumps the patch version; the first release is 0.1.0. tenzir-ship can't
edit `gradle.properties`, hence the `sed`. Then write the Play "What's new" (≤ 500 characters per language) from
`changelog/releases/vX.Y.Z/notes.md` into [store/whats-new.md](../store/whats-new.md).
`uvx tenzir-ship release publish --tag --yes` publishes the release (tag `vX.Y.Z`, push, GitHub
release with `notes.md` as the body). It **pushes**, so run it only with the user's OK. Attach the
APK to the GitHub release afterwards (`gh release upload vX.Y.Z …`). There is no CI release
workflow yet (tenzir-ship offers one; add it once CI can build and attach the APK). There is no
root CHANGELOG.md, because the notes live in `changelog/releases/` and on the GitHub releases.

## Checklist (manual steps)

### 1. Public source repository (required by the AGPL before the first public release)
- [x] Before publishing, check that no photo was ever committed: `git log --all --name-only --format= | grep -Ei '\.(jpe?g|png|heic)$' | sort -u`
      (expected: only brand/, design/, docs/readme/, docs/store/ and res/ images; **never** anything from
      `ml/data/`). If anything private shows up, rewrite the history
      (git filter-repo) *before* the first push. (Checked 2026-10-18 and 2026-10-19: clean. On 2026-10-19 the history was rewritten anyway to drop the old `ml/data/manifest.csv`, which named and described private photos.)
- [x] Read `ml/data/web_test/sources.csv` once (no photos, only sources). The commit author name/e-mail become public too.
- [x] Create the public GitHub repository and push `main` (and tags): https://github.com/zedoraps/scan7.
- [x] Repository set to `zedoraps/scan7` in `app/gradle.properties` (`scan7.sourceUrl`,
      `scan7.privacyUrl`), `app/model.properties` (`model.url`), `docs/store/listing-en.md`.
- [x] Enable **GitHub Pages** (Settings → Pages → deploy from branch `main`, folder `/docs`). The
      privacy policy is then at `https://zedoraps.github.io/scan7/privacy.html` (`docs/privacy.md`,
      English + German). The other files in `docs/` are published too, which is fine because
      they are public in the repo anyway.
- [x] Create the model release: `gh release create model-ft_v5 app/app/src/main/assets/model.tflite --title "Detector model ft_v5" --notes "YOLO26n fp32, 800 px (AGPL-3.0)"`,
      then check a fresh clone: `git clone … && cd scan7/app && ./gradlew downloadModel assembleDebug`. (Release created 2026-10-19; the fresh-clone check is still open.)
- [x] Every app release: tag it (`git tag v0.1.0`), so the source of each published build is
      findable (AGPL § 6 / § 13).

### 2. Upload key (never in the repo, never sent anywhere)
```sh
mkdir -p ~/keys && keytool -genkeypair -v -keystore ~/keys/scan7-upload.jks \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Scan7 upload"
```
Then in `~/.gradle/gradle.properties` (outside the repo):
```properties
scan7.upload.storeFile=/Users/<you>/keys/scan7-upload.jks
scan7.upload.storePassword=…
scan7.upload.keyAlias=upload
scan7.upload.keyPassword=…
```
(or `SCAN7_UPLOAD_STORE_FILE`, `SCAN7_UPLOAD_STORE_PASSWORD`, `SCAN7_UPLOAD_KEY_ALIAS`,
`SCAN7_UPLOAD_KEY_PASSWORD` in CI). `./gradlew bundleRelease` then signs the AAB; without them it
warns "release build is UNSIGNED".
- [ ] Back up the keystore and both passwords (password manager + offline copy). If the upload key
      is lost, Play support can reset it, but that takes days.
- [ ] **Play App Signing** is on by default for new apps: on the first upload Play Console asks to
      let Google create and keep the app signing key. Accept. You only ever upload with the upload
      key, and Play re-signs the APKs it delivers.
- [x] GitHub release APKs: Play-installed and GitHub-installed copies have **different
      signatures** (Google's app signing key vs. yours) and can't update each other. Either sign
      GitHub APKs with the upload key and say so in the release notes, or create a separate
      "github" key the same way. (v0.1.0: signed with the upload key, said so in the release notes.)

### 3. Google Play developer account
- [ ] Register at <https://play.google.com/console/signup> as a **personal** account: one-time
      fee **US$ 25**, legal name and address, **identity verification** (government ID), a verified
      phone number and e-mail, and the Play Console app on an Android phone for device
      verification. Verification can take a few days.
- [ ] Public developer contact e-mail (shown on the listing).

### 4. Closed test (new personal accounts)
Personal accounts created after 13 Nov 2023 must run a **closed test with at least 12 testers who
stay opted in for 14 days in a row** before they can apply for production access.
- [ ] Create the app (App, Free, default language English (US)).
- [ ] Internal testing first: upload the AAB, install it from Play on your own phone, smoke test.
- [ ] Closed testing track: add ≥ 12 testers (e-mail list or Google Group; more than 12 is safer
      because people drop out), share the opt-in link, keep them opted in for 14 days.
      Ask them to actually play a round (Play asks how the test went and what changed).
- [ ] Then *Dashboard → Apply for production* and answer the questions about the test.

### 5. App content (Play Console → Policy → App content)
Answers are drafted in [store/README.md](../store/README.md).
- [ ] Privacy policy URL (GitHub Pages, step 1).
- [ ] Ads: No. App access: everything available without login.
- [ ] **Data safety**: "No data collected / shared" (multiplayer game data goes only phone to phone, see store/README.md).
- [ ] **Content rating** (IARC questionnaire): expected Everyone / PEGI 3 / USK 0.
- [ ] Target audience: 13+ (avoids the Families policy for now).
- [ ] Government / financial / health / news declarations: none.

### 6. Store listing
- [ ] Texts from [store/listing-en.md](../store/listing-en.md) and the German translation from
      [store/listing-de.md](../store/listing-de.md) (add de-DE under *Store listing → Translations*).
- [ ] Icon [store/icon-512.png](../store/icon-512.png), feature graphic
      [store/feature-graphic-en.png](../store/feature-graphic-en.png) / [-de.png](../store/feature-graphic-de.png)
      (`uv run --project ml python -I docs/store/make_feature_graphic.py`), 2–8 phone screenshots
      at 1080 × 1920 per language (shot list in store/README.md), freshly made on an emulator:
      `ANDROID_SERIAL=localhost:5561 scripts/store-screenshots.sh`, then optionally
      `uv run --project ml python -I docs/store/caption_screenshots.py` for the captioned set
      (`docs/store/screenshots/<locale>/captioned/`).
- [ ] Category Entertainment (or Tools), contact e-mail, website = the GitHub repo.
- [ ] Keep the disclaimer "Unofficial score keeper for Flip 7. Not affiliated with The Op Games."
      in the description; never use the game's artwork or logo.

### 7. Each release
- [ ] Cut the release with tenzir-ship (see *Changelog* above): notes, `scan7.versionName`, commit, tag `vX.Y.Z`, push.
- [ ] `cd app && ./gradlew testDebugUnitTest lintRelease bundleRelease assembleRelease`.
- [ ] Check the merged release manifest (`app/app/build/outputs/logs/manifest-merger-release-report.txt`):
      no `INTERNET`, no permissions beyond the list in *Permissions of the release build* below
      without updating the Data safety answers and the permission declarations.
- [ ] Sideload the release APK on a test device once: no debug menu on 5 wordmark taps, About shows
      the right version and links, scanning works, nothing appears in `Pictures/Scan7 Training`.
- [ ] Upload the AAB to the internal track, then closed, then production (staged rollout, e.g. 20 %).
- [ ] **Pre-launch report** (runs automatically on closed/open tracks; Firebase Test Lab robo test
      on real devices): look for crashes, the camera-permission flow and accessibility warnings.
      The robots have no Flip 7 cards in front of the camera, so detection itself isn't tested there.
- [ ] GitHub release `vX.Y.Z` with the APK attached (see the signature note in step 2) and
      `changelog/releases/vX.Y.Z/notes.md` as the text; Play "What's new" from
      [store/whats-new.md](../store/whats-new.md).

### 8. F-Droid (optional)
- The easiest route is the **IzzyOnDroid** repo. It picks up the APK from GitHub releases; open a request there.
- Main F-Droid repo: open a "Request for Packaging" / merge request in `fdroiddata` with
  `metadata/net.moesli.scan7.yml` (License: AGPL-3.0-only, `AutoUpdateMode: Version`,
  `UpdateCheckMode: Tags`, build `subdir: app/app`, `gradle: [yes]`, and pass the versionCode
  explicitly, e.g. `gradleprops: [scan7.versionCode=<code>]`, since F-Droid clones may be shallow).
  Open points they will review: the **prebuilt model** (`model.tflite` downloaded by
  `downloadModel`; the build server has no general network access, so they may want it fetched
  in `prebuild` with the checksum, or committed for their builds; training photos are not
  public, so the model isn't reproducible from source alone), and the LiteRT binaries from
  Google Maven (Apache-2.0).
- **Proprietary dependencies (F-Droid NonFreeDep): solved.** `litert-api` 2.3.0 pulls in
  `com.google.android.play:ai-delivery` (AI Pack / Play-delivered models, unused by Scan7), which
  brings `asset-delivery`, `androidx.work` and the proprietary `play-services-basement` /
  `play-services-tasks`. `app/app/build.gradle.kts` excludes `ai-delivery` for all configurations:
  the LiteRT jars and native libraries don't reference those
  classes, and the bundled model loads and detects without them (checked on the API 37 emulator:
  the GPU accelerator falls back to CPU, no ClassNotFound / NoClassDefFound). Check
  `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` for
  `com.google.android.gms` / `com.google.android.play` after every LiteRT update.
- **F-Droid builds with `-Pscan7.foss=true`** (e.g. `gradleprops: [scan7.foss=true, scan7.versionCode=<code>]`).
  The default Play build links `play-services-nearby` (proprietary) for multiplayer. The FOSS build
  has no Nearby, no Share / Join, none of the radio permissions and no foreground service. Check that
  `./gradlew -Pscan7.foss=true :app:dependencies --configuration releaseRuntimeClasspath` shows no
  `com.google.android.gms`. Multiplayer is not an F-Droid target (user decision).
- Store texts for F-Droid can live in `fastlane/metadata/android/<lang>/` (title, short and full
  description, images); copy them from `docs/store/` when doing this.

## Permissions of the release build
Check with `aapt2 dump badging app/build/outputs/apk/release/app-release*.apk | grep permission`
(or the merged manifest, `app/app/build/intermediates/merged_manifests/release/*/AndroidManifest.xml`).
**No `INTERNET`** in either build.

| Permission | Build | Why | Asked at runtime |
|---|---|---|---|
| `CAMERA` | both | scanning the cards; also the QR code on *Join a game* | first scan / first join |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | Play | Nearby checks them on every API level (8032) | no (install-time) |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` (`maxSdkVersion=30`) | Play | Nearby on Android 10–11 | no |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | Play | Nearby discovery refuses without both, even on Android 17 (8034 / 8036). Scan7 never reads or stores the location | on Share / Join |
| `BLUETOOTH_ADVERTISE` / `CONNECT` / `SCAN`, `NEARBY_WIFI_DEVICES` | Play | Nearby on Android 12+ / 13+ ("Nearby devices") | on Share / Join |
| `ACCESS_LOCAL_NETWORK` | Play | Android 17 local network protection (Wi-Fi LAN medium) | on Share / Join |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Play | `ShareService` keeps the link alive while sharing / joined (screen off, camera open) | no |
| `POST_NOTIFICATIONS` | Play | the "sharing a game" notification of that service (optional) | on Share / Join |
| `ACCESS_NETWORK_STATE` | Play | added by `play-services-nearby` | no |
| `net.moesli.scan7.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | both | AndroidX's own signature-level permission | no |

Hardware features `bluetooth`, `bluetooth_le`, `location` (+ `.gps`, `.network`) and `wifi` are
declared `required="false"`, so phones without them can still install Scan7 (multiplayer then
can't reach other phones). Runtime permissions are requested only when the user taps *Share this
game* or *Join a game*, never at startup. In the Play Console the location permission needs the
declaration / explanation from [store/README.md](../store/README.md).

## Follow-ups (not blocking)
- Done: `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and WorkManager's boot receiver / services are gone
  with the `ai-delivery` exclusion (see F-Droid above). LiteRT's own `FOREGROUND_SERVICE_DATA_SYNC`
  is still removed with `tools:node="remove"` in the main manifest; the Play manifest
  (`src/play/AndroidManifest.xml`) declares `FOREGROUND_SERVICE` + `_CONNECTED_DEVICE` again for
  `ShareService`. The FOSS build should list only `CAMERA` (plus the AndroidX signature permission), as before multiplayer; re-check with aapt2 when building it.
