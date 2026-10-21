# Prompt for Claude Code

Copy the `scanjo_brand_kit/` folder into the repo root (for example as `brand/scanjo/`), then paste this:

---

Apply the Scanjo brand from `brand/scanjo/` to the Android app. Read `brand/scanjo/BRAND.md` first. It is the spec.

1. Copy everything under `brand/scanjo/android/res/` into `app/src/main/res/`, replacing the existing launcher icon files (`ic_launcher*`, `mipmap-*`). Keep any other existing resources.
2. Make sure `AndroidManifest.xml` uses `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
3. Copy `brand/scanjo/compose/ScanjoTheme.kt` into the UI theme package. Adjust the `package` line and the `R` import to match the app's namespace.
4. Wrap the app content in `ScanjoTheme { … }`. Read colors from `LocalScanjoColors.current` and text styles from `ScanjoType`. Don't use `MaterialTheme.colorScheme` or Material default components for visible UI.
5. Theme follows the system: Sky Day in light mode, Sky Night in dark mode. Set the status and navigation bars to the `ground` color with matching light/dark icons.
6. Camera overlay: draw detected cards with `ScanOverlay.box` (3 dp) plus a 2 dp `ScanOverlay.boxOutline` outside it.
7. Draw the ✓ as a vector icon; the bundled fonts don't contain it. Write negative numbers with `−` (U+2212).
8. Put the screen-specific layouts from the earlier design handoff on top of these tokens. Don't invent new colors. If a color is missing, add it to `ScanjoTheme.kt` and `colors.xml` for both themes and tell me.

When done, build, run on a device in light and dark mode, and list anything in `BRAND.md` you couldn't follow.

---

Play Store listing assets: `icon/scanjo_playstore_512.png` and `store/feature_graphic_1024x500.png`.
To regenerate any icon file, run `tools/gen_scanjo_kit.py` (needs `cairosvg`, `shapely`, `pillow`). The comment at its top shows how to call it.
