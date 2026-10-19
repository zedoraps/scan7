# Scan7 player avatars: assets and implementation plan

An avatar is a **color, a pattern and a face**, drawn as three stacked layers on a tilted card (−8°, already baked into the shapes).
12 faces × 6 patterns (incl. plain) × 15 colors = 1,080 combinations.

## Files in this kit
| Path | What |
|---|---|
| `layers/base_card.svg` | Card shape, white. **Tint it with the player color** at runtime |
| `layers/patterns/pattern_*.svg` (5) | stripes, dots, zigzag, grid, waves, already clipped to the card, dark ink with alpha |
| `layers/faces/face_*.svg` (12) | happy, cool, wink, wow, grin, sleepy, cheeky, focus, hearts, grumpy, silly, dizzy |
| `android/drawable/avatar_*.xml` (18) | The same layers as ready-made Android VectorDrawables |
| `examples/avatar_*.svg` (10) | Fully composed example avatars (docs, store screenshots, tests) |
| `avatar_catalog.json` | IDs of faces, patterns, colors (with hex), reserved colors, example presets |
| `avatar_preview.png` | Contact sheet of everything |

All layers share `viewBox 0 0 100 100`. Every shape is a plain filled path (no strokes, masks, patterns or transforms), so the layers scale without artifacts and convert to VectorDrawable 1:1.

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
- An unknown id (e.g. after removing an option) falls back to `happy` / `plain` / the first color.
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
`faceRes`/`patternRes` map ids to `R.drawable.avatar_face_<id>` / `avatar_pattern_<id>` with a `when`. It uses no reflection, so R8 can still shrink the resources.

## Sizes used in the app
| Where | Size |
|---|---|
| Avatar editor preview | 88 dp |
| Table-view card (≤4 / 5–6 / 7–10 players) | replaces the token inside the score ring, 32 / 27 / 21 dp tall |
| List rows (normal / compact) | 30 / 20 dp tall |
| Picker buttons (face / pattern) | 50 / 42 dp |
| Camera header, dialogs, bars | 24–36 dp |

## Avatar editor (see design canvas "Avatar editor")
- Live preview + name field at the top; shuffle button in the header.
- Face: 3 rows × 4 buttons, each showing the current avatar with that face.
- Pattern: 1 row × 6 buttons, each showing the current avatar with that pattern.
- Color: 3 rows × 5 swatches (tilted mini cards). Colors already used by other players in the current game get a small dot so people avoid duplicates (still allowed).
- The selected item has a 2.5 dp cyan border. Buttons are ≥ 44 dp.

## Rules
- Never offer accent cyan `#38D6F5` or bust magenta `#FF4F9A` as player colors.
- Avatars never replace text. Names stay visible next to them, and the content description is "Mia, sand avatar, grin face".
- The avatar is a player property, saved with the player and reused across games.

## Implementation steps (for coding agents)
1. Copy `android/drawable/*.xml` into `app/src/main/res/drawable/`; copy `avatar_catalog.json` into `app/src/main/assets/` (or turn it into Kotlin constants).
2. Add `Avatar` to the player model, with a database migration (existing players get a random avatar with their old color mapped to the nearest catalog color).
3. Build `PlayerAvatar` and a preview screen that renders `examples` presets; compare against `avatar_preview.png`.
4. Replace the initial-letter token everywhere (round screen table/list, review, camera header, scoreboard, game over).
5. Build the avatar editor screen (player editor) and the "new player" defaults.
6. Unit-test that every catalog id maps to a drawable, the JSON round-trip of `Avatar`, and the fallback for unknown ids.
