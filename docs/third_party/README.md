# Third-party assets

| Asset | Files in the app | Source | License |
|---|---|---|---|
| Unbounded (Bold 700, SemiBold 600) | `app/app/src/main/res/font/unbounded_bold.ttf`, `unbounded_semibold.ttf` | [google/fonts `ofl/unbounded/Unbounded[wght].ttf`](https://github.com/google/fonts/tree/main/ofl/unbounded), static instances cut with `fonttools varLib.instancer --static --update-name-table` | SIL OFL 1.1, [Unbounded-OFL.txt](Unbounded-OFL.txt) |
| DM Mono (Regular, Medium) | `app/app/src/main/res/font/dm_mono_regular.ttf`, `dm_mono_medium.ttf` | [google/fonts `ofl/dmmono/`](https://github.com/google/fonts/tree/main/ofl/dmmono), unchanged | SIL OFL 1.1, [DMMono-OFL.txt](DMMono-OFL.txt) |
| Agent skills navigation-3, camerax, litert-runtime, play-policy-insights, r8-analyzer (not in the app) | `.agents/skills/` | [android/skills](https://github.com/android/skills), installed with `npx skills` (versions in `skills-lock.json`) | Apache-2.0, [Apache-2.0.txt](Apache-2.0.txt) |
| Agent skill yolo-export (not in the app) | `.agents/skills/yolo-export/` | [ultralytics/skills](https://github.com/ultralytics/skills), installed with `npx skills` | AGPL-3.0, the repo's [LICENSE](../../LICENSE) |

Neither font declares a Reserved Font Name, so the instanced Unbounded files may keep the name.
`Apache-2.0.txt` is the Apache License 2.0 (LiteRT, Jetpack, Kotlin). The app bundles every
`*.txt` here plus the repo's `LICENSE` (AGPL-3.0) as `assets/licenses/<lower-case stem>.txt`
(`copyLicenseTexts` in app/app/build.gradle.kts) and shows them under About & licenses
(`ui/about/AboutScreen.kt`, `LicenseText` ids must match the file stems).
