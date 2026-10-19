# Scan7 – Player avatars: assets + implementation plan

An avatar = **color + pattern + face**, drawn as three stacked layers on a tilted card (−8°, already baked into the shapes).
12 faces × 6 patterns (incl. plain) × 15 colors = 1,080 combinations.

## Files in this kit
| Path | What |
|---|---|
| `layers/base_card.svg` | Card shape, white – **tint with the player color** at runtime |
| `layers/patterns/pattern_*.svg` (5) | stripes, dots, zigzag, grid, waves – already clipped to the card, dark ink with alpha |
| `layers/faces/face_*.svg` (12) | happy, cool, wink, wow, grin, sleepy, cheeky, focus, hearts, grumpy, silly, dizzy |
| `android/drawable/avatar_*.xml` (18) | The same layers as ready-made Android VectorDrawables |
| `examples/avatar_*.svg` (10) | Fully composed example avatars (docs, store screenshots, tests) |
| `avatar_catalog.json` | IDs of faces, patterns, colors (with hex), reserved colors, example presets |
| `avatar_preview.png` | Contact sheet of everything |

All layers share `viewBox 0 0 100 100`; every shape is a plain filled path (no strokes, masks, patterns or transforms), so they scale cleanly and convert to VectorDrawable 1:1.

## Layer order
1. `avatar_base` tinted with the player color
2. `avatar_pattern_<id>` (skip for `plain`)
3. `avatar_face_<id>`

Ink color on patterns/faces is `#0A1220` (ground). Pattern alpha is baked in (0.26–0.30).

## Data model
```kotlin
@Serializable
data class Avatar(val face: String, val pattern: String, val color: String) // ids from avatar_catalog.json
data class Player(val id: String, val name: String, val avatar: Avatar, ...)
```
- Store **ids**, not hex values, so colors can be tuned later.
- Unknown id (e.g. after removing an option) → fall back to `happy` / `plain` / first color.
- Default for a new player: random face + pattern, and the **first color not used by players already in the game**.

## Compose component
```kotlin
@Composable
fun PlayerAvatar(avatar: Avatar, size: Dp, modifier: Modifier = Modifier, contentDescription: String?) {
    Box(modifier.size(size).semantics { this.contentDescription = contentDescription ?: "" }) {
        Image(painterResource(R.drawable.avatar_base), null,
              colorFilter = ColorFilter.tint(AvatarColors.byId(avatar.color)), modifier = Modifier.matchParentSize())
        patternRes(avatar.pattern)?.let { Image(painterResource(it), null, Modifier.matchParentSize()) }
        Image(painterResource(faceRes(avatar.face)), null, Modifier.matchParentSize())
    }
}
```
`faceRes`/`patternRes` map ids → `R.drawable.avatar_face_<id>` / `avatar_pattern_<id>` with a `when` (no reflection, keeps R8 happy).

## Sizes used in the app
| Where | Size |
|---|---|
| Avatar editor preview | 88 dp |
| Table-view card (≤4 / 5–6 / 7–10 players) | replaces the token inside the score ring: 32 / 27 / 21 dp tall |
| List rows (normal / compact) | 30 / 20 dp tall |
| Picker buttons (face / pattern) | 50 / 42 dp |
| Camera header, dialogs, bars | 24–36 dp |

## Avatar editor (see design canvas "Avatar editor")
- Live preview + name field at the top; shuffle button in the header.
- **Face:** 3 rows × 4 buttons (each shows the current avatar with that face).
- **Pattern:** 1 row × 6 buttons (each shows the current avatar with that pattern).
- **Color:** 3 rows × 5 swatches (tilted mini cards). Colors already used by other players in the current game get a small dot so people avoid duplicates (still allowed).
- Selected item: 2.5 dp cyan border. Buttons ≥ 44 dp.

## Rules
- Never offer accent cyan `#38D6F5` or bust magenta `#FF4F9A` as player colors.
- Avatars never replace text: names stay visible next to them; content description = "Mia, sand avatar, grin face".
- Avatar is a player property (saved with the player), reused across games.

## Implementation steps (for coding agents)
1. Copy `android/drawable/*.xml` into `app/src/main/res/drawable/`; copy `avatar_catalog.json` into `app/src/main/assets/` (or turn it into Kotlin constants).
2. Add `Avatar` to the player model + database migration (existing players get a random avatar with their old color mapped to the nearest catalog color).
3. Build `PlayerAvatar` + a preview screen that renders `examples` presets; compare against `avatar_preview.png`.
4. Replace the initial-letter token everywhere (round screen table/list, review, camera header, scoreboard, game over).
5. Build the avatar editor screen (player editor) and the "new player" defaults.
6. Unit test: every catalog id maps to a drawable; JSON round-trip of `Avatar`; fallback for unknown ids.
