# Agent skills

Third-party skills in the SKILL.md format, copied unchanged from their source repositories (no
`npx skills`). Claude Code reads them through the `.claude/skills` link; other tools read this
folder. AGENTS.md wins where a skill disagrees with it.

| Skill | Source | Commit | License | Use it for |
|---|---|---|---|---|
| `navigation-3` | [android/skills](https://github.com/android/skills) `navigation/navigation-3` | 5bda39f | Apache-2.0 | the planned switch from the own back stack to Navigation 3 (platform audit batch D) |
| `camerax` | android/skills `camera/camerax` | 5bda39f | Apache-2.0 | camera code and its tests |
| `litert-runtime` | android/skills `device-ai/litert/litert-runtime` | 5bda39f | Apache-2.0 | `CardDetector` and anything else on LiteRT |
| `play-policy-insights` | android/skills `play/play-policy-insights` | 5bda39f | Apache-2.0 | a policy audit before a Play submission (run once per release, not on every change) |
| `r8-analyzer` | android/skills `performance/r8-analyzer` | 5bda39f | Apache-2.0 | a keep-rule audit of the minified release build |
| `yolo-export` | [ultralytics/skills](https://github.com/ultralytics/skills) `skills/yolo-export` | 607a76c | AGPL-3.0 | exporting the detector to LiteRT (fp32 / int8) |

Project rules that override the skills:

- No DI framework and no ViewModel requirement. Recipes with Hilt, Koin or `hiltViewModel()` are
  examples only; keep `AppGraph` and the existing route/controller pattern.
- No ML Kit analyzers: detection runs on our own LiteRT model.
- Keep the test style of the repo (JUnit asserts, fakes); don't add Truth or MockK without asking.
- Ultralytics: never use Ultralytics Platform, `ul cloud`, `ul://` datasets or models, and never set
  `ULTRALYTICS_API_KEY`. Export and training run locally; photos never leave the machine.
- `play-policy-insights` runs its bundled Python scripts locally; `play_store_scraper.py` only fetches
  the public Play listing of the package id.

To update a skill, copy the folder again from its source repository and change the commit in this
table.
