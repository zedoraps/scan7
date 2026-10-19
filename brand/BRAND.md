# Scan7 brand

## Logo
A dark card with a centered cyan **7**, tilted slightly, crossed by a tilted white scan line across its lower part, on a cyan tile.
The artwork is original. It must not reuse Flip 7's card art, frames, fonts or name styling.

## Colors ("Deep Cyan")
| Role | Hex | Use |
|---|---|---|
| Ground | `#0A1220` | App background |
| Surface | `#15223A` | Panels, sheets, score cards |
| Text | `#EEF4FA` | Primary text |
| Muted text | `#93A4BC` | Labels, secondary text |
| Accent | `#38D6F5` | Brand, scores, primary buttons, scan boxes |
| Bust | `#FF4F9A` | Bust warning only |

Color rules:
- **Bust is magenta, never orange or red.** Orange is the modifier cards' color and red is Second Chance.
- **Never show bust by color alone.** Always pair it with the word "Bust" and/or an icon.
- **Scan boxes are cyan with a white outline**, so they stay visible on the light-blue Freeze card.
- Dark theme by default (people play in dim rooms).

## Type
- The wordmark and big numbers use **Unbounded** (Bold), from Google Fonts. The wordmark is "scan" in text color and "7" in accent.
- UI labels use **DM Mono** or the system font.

## Files
| File | Use |
|---|---|
| `ic_launcher_foreground.svg` / `_432.png` | Adaptive icon foreground layer (108 dp canvas, artwork in the safe zone) |
| `ic_launcher_monochrome.svg` / `_432.png` | Android 13+ themed icon layer |
| `colors.xml` | Color resources, incl. `ic_launcher_background` |
| `play_store_icon_512.png` | Google Play listing icon (full-bleed square; Play rounds it) |
| `scan7_icon_1024.png`, `scan7_icon_rounded.svg` | Rounded icon for slides, website, splash |

## Adding the icon in Android Studio
1. Copy `colors.xml` entries into `res/values/colors.xml`.
2. Right-click `res`, choose **New → Image Asset**, then Launcher Icons (Adaptive and Legacy).
3. Foreground layer: `ic_launcher_foreground.svg` (Asset type: Image), Trim: No, Resize: 100%.
4. Background layer: Color `#38D6F5`.
5. Options tab: enable the monochrome layer and pick `ic_launcher_monochrome.svg`.
6. Click Finish. Android Studio generates all mipmap sizes and the adaptive XML.
