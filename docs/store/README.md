# Store listing kit

Drafts and specs for the Google Play listing (and an F-Droid / GitHub release). The manual steps
around them are in [../RELEASE.md](../RELEASE.md).

| File | What |
|---|---|
| [listing-en.md](listing-en.md) | Name, short + full description, category (English, default language) |
| [listing-de.md](listing-de.md) | The same in German |
| [icon-512.png](icon-512.png) | Play icon, 512 × 512, 32-bit PNG (copy of `brand/play_store_icon_512.png`; full-bleed square, Play rounds the corners) |
| [feature-graphic-en.png](feature-graphic-en.png), [-de.png](feature-graphic-de.png) | Feature graphic 1024 × 500, 24-bit PNG, made by [make_feature_graphic.py](make_feature_graphic.py) |
| `screenshots/<locale>/` | Phone screenshots 1080 × 1920 (not in git: rerun `scripts/store-screenshots.sh`), `captioned/` = with caption band ([caption_screenshots.py](caption_screenshots.py)) |
| this file | Disclaimer, Data safety answers, content rating, screenshot shot list, feature graphic spec |

## Disclaimer (use everywhere: listing, GitHub README, F-Droid summary)
- EN: **Unofficial score keeper for Flip 7. Not affiliated with The Op Games.**
- DE: **Inoffizieller Punktezähler für Flip 7. Keine Verbindung zu The Op Games.**

"Flip 7" appears only as a descriptive reference (what the app is for), never as part of the app's
name or styled like the game's logo. No artwork, card images, fonts or logo of The Op Games in the
icon, screenshots or feature graphic. Screenshots of the camera show real cards on the table only as
small, incidental content of a scan (the app's purpose); prefer the table / list / scoreboard
screens, which show no card art at all.

## Data safety form (Play Console → App content → Data safety)
Basis: the release build has no internet permission, no SDKs that collect data, no ads, no
analytics, no crash reporter; training-scan saving is compiled out of release behaviour
(debug builds only). Re-check these answers whenever a dependency or permission changes
(`app/app/build/outputs/logs/manifest-merger-release-report.txt`).

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not shown once "No" is answered (nothing is transmitted) |
| Do you provide a way for users to request that their data is deleted? | Not shown once "No" is answered; data lives only on the device (delete games and players in the app, or clear the app's data) |
| Result shown on the listing | "No data collected · No data shared with third parties" |

Why "No" is correct: Play counts data as *collected* only when it is transmitted off the device.
Camera frames are processed on-device and never stored or sent; players, games and settings stay
in the app's private storage. Android's own Auto Backup (user-controlled, Google account) is a
system feature and is not collection by the app.

Permissions to declare / explain: **Camera** (core feature: scanning the cards; asked at the first
scan, with an explanation screen). The merged manifest also contains `WAKE_LOCK`,
`ACCESS_NETWORK_STATE` and `RECEIVE_BOOT_COMPLETED` from `androidx.work` (pulled in by LiteRT);
they need no declaration and don't send data, but could be removed with `tools:node="remove"`
after testing (see RELEASE.md).

## Content rating (Play Console → App content → Content ratings, IARC questionnaire)
- Category: **Utility, Productivity, Communication or Other** (it is a score keeper, not a game).
- Violence, sexuality, language, controlled substances, crude humour: **No**.
- Gambling or simulated gambling: **No** (Flip 7 has points, no stakes; the app has no betting,
  no currency).
- Users can interact or exchange content: **No** (no network, no chat, no sharing).
- Shares the user's location: **No**. Digital purchases: **No**. Ads: **No**.
- Expected result: **Everyone / PEGI 3 / USK 0**.

Target audience (App content → Target audience and content): choose **13+** (13–15, 16–17, 18+).
Selecting ages under 13 puts the app under the Families policy (extra review, teacher-approved
rules); the game itself is for 8+, so this can be revisited later. "Appeals to children": No.

Other App content answers: Ads **No**; App access **all functionality available without special
access** (no login); Government app **No**; Financial features **None**; Health **No**; News **No**.

## Screenshot shot list
Play: 2–8 phone screenshots, PNG/JPEG, each side 320–3840 px, **the long side at most 2× the
short side** (so 1080 × 2400 = 2.22 is too long; use **1080 × 1920**, 9:16). Optional: 7" and 10"
tablet sets (1600 × 2560 profile). Same set per language (EN app language for the en-US listing,
German for de-DE).

Capture with the seeded demo data (fictional players, debug build on one emulator, never the
user's phone). The screenshots are **not in git** (the UI still changes); rerun before each
listing update:

```sh
ANDROID_SERIAL=localhost:5561 scripts/store-screenshots.sh     # -> docs/store/screenshots/{en-US,de-DE}/<n>_<screen>.png
uv run --project ml python -I docs/store/caption_screenshots.py   # optional -> …/<locale>/captioned/ (caption band on top)
```

`store-screenshots.sh` builds and installs a `.store` debug build, sets 1080 × 1920 @ 420 dpi,
the per-app locale (`cmd locale set-app-locales`) and status bar demo mode (12:00, full battery,
no notifications), shoots shots 1, 2, 3, 5, 6, 7 below per locale (blank / wrong captures are
retried), then resets size, density, locale, demo mode and uninstalls `.store`. Options:
`--no-build`, `LOCALES='de-DE'`, `SHOTS='1:p6:round_table …'` (`<n>:<seed>:<open>`), `WAIT=…`.
Shots 4 (sheet) and 8 (new game) can be taken with `scripts/screen-matrix.sh`
(`DEVICES=localhost:5561 PROFILES='1080x1920@420x1.0' SCREENS='p6_sheet_open p10_newgame'`).

| # | Screen (seed / open) | Caption EN | Caption DE |
|---|---|---|---|
| 1 | `p6` / `round_table` – table view mid-round | Score a whole round in a minute | Eine ganze Runde in einer Minute |
| 2 | `p6` / `review` – scan review with boxes | Scan the cards, get the points | Karten scannen, Punkte sehen |
| 3 | `p6` / `round_list` – list view | Everyone's round at a glance | Die Runde aller auf einen Blick |
| 4 | `p6` / `sheet_open` – player sheet | Fix a card or enter it by hand | Karte korrigieren oder von Hand eingeben |
| 5 | `p6` / `scoreboard` | Every round on the scoreboard | Alle Runden in der Punktetabelle |
| 6 | `gameover` / `gameover` | Who won tonight | Wer heute gewonnen hat |
| 7 | `history` / `home` – past games | Your past games | Deine vergangenen Spiele |
| 8 | `p10` / `newgame` – pick players + avatars | Your regular players, ready to go | Deine Mitspieler, sofort bereit |

The review shot (2) uses a real table photo with Flip 7 cards: that shows the app's purpose, but
keep the cards small and incidental, or leave it out if in doubt. For German shots switch the
emulator (or the app's per-app language) to German first. The debug build's Home footer shows the
version line; that is fine. Check each shot for: no
`PRIVATE_*` photo content, no real names, no card art bigger than the scan itself. Captions are
optional overlays (design them in the brand colors with DM Mono / Unbounded, never the game's
fonts).

## Feature graphic (required, 1024 × 500)
Made: [feature-graphic-en.png](feature-graphic-en.png) / [feature-graphic-de.png](feature-graphic-de.png)
(icon + wordmark, tagline "Scan your cards. Keep score." / "Karten scannen. Punkte zählen.", a
tilted phone scanning plain numbered cards in brand scan boxes). Regenerate after changing the
script:

```sh
uv run --project ml python -I docs/store/make_feature_graphic.py   # -> docs/store/feature-graphic-{en,de}.png
```

Spec:
- Size **1024 × 500 px**, JPEG or 24-bit PNG **without alpha**, ≤ 15 MB.
- Background: ground `#0A1220` with a soft surface `#15223A` panel; accent `#38D6F5`.
- Left: the Scan7 icon (from `brand/scan7_icon_1024.png`, ~300 px) and the wordmark "scan" +
  "7" (Unbounded Bold, "7" in accent). Below: the short claim in DM Mono, e.g. "Scan the cards.
  Get the score." / "Karten scannen. Punkte sehen."
- Right: a phone frame with screenshot 1 (table view), slightly tilted, or the cyan scan box
  (white outline) motif from the logo.
- Keep important content inside the central ~ 924 × 400 area (Play may crop / overlay a play
  button on promo videos). No text smaller than ~ 24 px. No Flip 7 artwork, logo or card images;
  don't write "Flip 7" large (if at all, only in the small disclaimer style).
- Make one per language if it carries text (EN, DE).
