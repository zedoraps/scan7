"""Usage: python3 gen_scanjo_kit.py OUT_DIR FONTS_DIR DESIGN_DIR
Generate the Scanjo brand kit (icon C: scan bar through last row, mark centred)."""
import json, math, os, shutil, sys
import cairosvg
from PIL import Image, ImageDraw, ImageFont
from shapely.geometry import box
from shapely.ops import substring

OUT = sys.argv[1]
FONTS = sys.argv[2]
DESIGN = sys.argv[3]

TILE, INK, BEAM = '#2A6FD8', '#FFFFFF', '#12233D'
NAVY = '#12233D'
XS, YS = [18, 35, 52, 69], [12, 33, 54]
CW, CH, RX = 13, 17, 3
BAR_Y, BAR_H = 59.5, 6          # centre 62.5 = middle of last row (54..71)
DY = 50 - (12 + 71) / 2          # 8.5, centres the mark in the tile
ROT = -8
DASH, GAP = 3.0, 2.5

def rr_path(x, y, w, h, r):
    return (f"M{x+r},{y}h{w-2*r}a{r},{r} 0 0 1 {r},{r}v{h-2*r}a{r},{r} 0 0 1 -{r},{r}"
            f"h-{w-2*r}a{r},{r} 0 0 1 -{r},-{r}v-{h-2*r}a{r},{r} 0 0 1 {r},-{r}z")

def dash_paths(x, y):
    """Dashed outline of the cleared card as separate open paths (VectorDrawable has no dasharray)."""
    ring = box(x + RX, y + RX, x + CW - RX, y + CH - RX).buffer(RX, quad_segs=8).exterior
    # start at the top edge, x+RX, like an SVG rect
    coords = list(ring.coords)[:-1]
    start = min(range(len(coords)), key=lambda i: (abs(coords[i][1] - y) + abs(coords[i][0] - (x + RX))))
    from shapely.geometry import LineString
    ring2 = LineString(coords[start:] + coords[:start + 1])
    L = ring2.length
    n = max(1, round(L / (DASH + GAP)))
    period = L / n
    d = DASH * period / (DASH + GAP)
    out = []
    for i in range(n):
        seg = substring(ring2, i * period, i * period + d)
        pts = list(seg.coords)
        out.append('M' + 'L'.join(f"{px:.2f},{py:.2f}" for px, py in pts))
    return out

def mark_elems(ink=INK, mark=TILE, beam=BEAM, beam_line=TILE, dashed_svg=True):
    """Return list of (kind, attrs) primitives in 100x100 space (before group transform)."""
    els = []
    for y in YS:
        for x in XS:
            if x == 52:
                if dashed_svg:
                    els.append(('rect', dict(x=x, y=y, width=CW, height=CH, rx=RX, fill='none', stroke=ink,
                                             **{'stroke-width': 2.2, 'stroke-dasharray': f'{DASH} {GAP}'})))
                else:
                    for p in dash_paths(x, y):
                        els.append(('path', dict(d=p, fill='none', stroke=ink, **{'stroke-width': 2.2, 'stroke-linecap': 'butt'})))
            else:
                els.append(('rect', dict(x=x, y=y, width=CW, height=CH, rx=RX, fill=ink)))
    els.append(('rect', dict(x=38.5, y=40.2, width=6, height=2.6, rx=1.3, fill=mark)))
    els.append(('rect', dict(x=72.5, y=19.2, width=6, height=2.6, rx=1.3, fill=mark)))
    els.append(('rect', dict(x=10, y=BAR_Y, width=80, height=BAR_H, rx=3, fill=beam, stroke=beam_line, **{'stroke-width': 2})))
    return els

def svg_el(kind, a):
    return f"<{kind} " + ' '.join(f'{k}="{v}"' for k, v in a.items()) + "/>"

def icon_svg(radius=23, tile=TILE, size=100, **kw):
    bg = '' if tile is None else f'<rect width="100" height="100" rx="{radius}" fill="{tile}"/>'
    body = '\n    '.join(svg_el(k, a) for k, a in mark_elems(**kw))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 100 100">\n'
            f'  <title>Scanjo</title>\n  {bg}\n  <g transform="translate(0 {DY}) rotate({ROT} 50 50)">\n    {body}\n  </g>\n</svg>\n')

def w(path, text):
    p = os.path.join(OUT, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w').write(text)

def png(svg, path, size):
    p = os.path.join(OUT, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    cairosvg.svg2png(bytestring=svg.encode(), write_to=p, output_width=size, output_height=size)

# ---------- SVG masters ----------
w('icon/scanjo_icon.svg', icon_svg(radius=23))
w('icon/scanjo_icon_square.svg', icon_svg(radius=0))
w('icon/scanjo_icon_round.svg', icon_svg(radius=50))
w('icon/scanjo_mark_on_dark.svg', icon_svg(tile=None))

png(icon_svg(radius=0), 'icon/scanjo_playstore_512.png', 512)
png(icon_svg(radius=23), 'icon/scanjo_icon_1024.png', 1024)

# ---------- Android adaptive icon (vector) ----------
# 108dp canvas, art scaled so everything stays inside the 66dp safe circle.
SCALE = 0.66
OFF = (108 - 100 * SCALE) / 2

def vd_rect_path(a):
    return rr_path(a['x'], a['y'], a['width'], a['height'], a['rx'])

def vector_xml(ink, mark, beam, beam_line, mono=False):
    lines = []
    for kind, a in mark_elems(ink=ink, mark=mark, beam=beam, beam_line=beam_line, dashed_svg=False):
        if kind == 'path':
            lines.append(f'        <path android:pathData="{a["d"]}" android:strokeColor="{a["stroke"]}" android:strokeWidth="{a["stroke-width"]}" android:strokeLineCap="butt"/>')
        else:
            extra = ''
            if 'stroke' in a:
                extra = f' android:strokeColor="{a["stroke"]}" android:strokeWidth="{a["stroke-width"]}"'
            lines.append(f'        <path android:pathData="{vd_rect_path(a)}" android:fillColor="{a["fill"]}"{extra}/>')
    body = '\n'.join(lines)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Scanjo launcher {'monochrome (themed icon)' if mono else 'foreground'}. Generated; edit gen_scanjo_kit.py, not this file. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
  <group android:translateX="{OFF:.2f}" android:translateY="{OFF:.2f}" android:scaleX="{SCALE}" android:scaleY="{SCALE}">
    <group android:translateY="{DY}" android:rotation="{ROT}" android:pivotX="50" android:pivotY="50">
{body}
    </group>
  </group>
</vector>
'''

w('android/res/drawable/ic_launcher_foreground.xml', vector_xml(INK, TILE, BEAM, TILE))
adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
'''
w('android/res/mipmap-anydpi-v26/ic_launcher.xml', adaptive)
w('android/res/mipmap-anydpi-v26/ic_launcher_round.xml', adaptive)
w('android/res/values/ic_launcher_background.xml',
  f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">{TILE}</color>\n</resources>\n')

# Legacy PNG mipmaps (minSdk < 26 only)
for d, s in dict(mdpi=48, hdpi=72, xhdpi=96, xxhdpi=144, xxxhdpi=192).items():
    png(icon_svg(radius=23), f'android/res/mipmap-{d}/ic_launcher.png', s)
    png(icon_svg(radius=50), f'android/res/mipmap-{d}/ic_launcher_round.png', s)

# Safe-zone check of the adaptive foreground
from shapely.geometry import Polygon
from shapely import affinity
pts = []
for kind, a in mark_elems(dashed_svg=True):
    g = box(a['x'] - 1.1, a['y'] - 1.1, a['x'] + a['width'] + 1.1, a['y'] + a['height'] + 1.1)
    g = affinity.rotate(g, ROT, origin=(50, 50))
    g = affinity.translate(g, 0, DY)
    g = affinity.scale(g, SCALE, SCALE, origin=(0, 0))
    g = affinity.translate(g, OFF, OFF)
    pts += list(g.exterior.coords)
rmax = max(math.hypot(x - 54, y - 54) for x, y in pts)
print(f'adaptive foreground max radius {rmax:.1f}dp (safe zone 33dp)')
assert rmax <= 33, 'mark leaves the adaptive-icon safe zone'


# ---------- True single-color mark (cut-outs), for themed icon + mono SVG ----------
from shapely.geometry import LineString as _LS
from shapely.ops import unary_union
def rrect(x, y, ww, hh, r):
    return box(x + r, y + r, x + ww - r, y + hh - r).buffer(r, quad_segs=8)
def mono_geom():
    solid = [rrect(x, y, CW, CH, RX) for y in YS for x in XS if x != 52]
    dashes = []
    for y in YS:
        for d in dash_paths(52, y):
            pts = [tuple(map(float, q.split(','))) for q in d[1:].split('L')]
            dashes.append(_LS(pts).buffer(1.1, cap_style=2, join_style=2))
    marks = unary_union([rrect(38.5, 40.2, 6, 2.6, 1.3), rrect(72.5, 19.2, 6, 2.6, 1.3)])
    bar = rrect(10, BAR_Y, 80, BAR_H, 3)
    g = unary_union(solid + dashes).difference(marks).difference(bar.buffer(1.4))
    return unary_union([g, bar])
def geom_path(g):
    polys = getattr(g, 'geoms', [g]); parts = []
    for pg in polys:
        for ring in [pg.exterior] + list(pg.interiors):
            c = list(ring.coords)[:-1]
            parts.append('M' + 'L'.join(f"{x:.2f},{y:.2f}" for x, y in c) + 'Z')
    return ''.join(parts)
MONO_D = geom_path(mono_geom())
def mono_svg(color=NAVY):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">\n  <title>Scanjo</title>\n'
            f'  <g transform="translate(0 {DY}) rotate({ROT} 50 50)">\n    <path fill="{color}" fill-rule="evenodd" d="{MONO_D}"/>\n  </g>\n</svg>\n')
w('icon/scanjo_mark_mono.svg', mono_svg())
w('icon/scanjo_mark_mono_white.svg', mono_svg('#FFFFFF'))
w('android/res/drawable/ic_launcher_monochrome.xml', f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Scanjo themed-icon (monochrome) layer: one solid shape, the system tints it. Generated. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
  <group android:translateX="{OFF:.2f}" android:translateY="{OFF:.2f}" android:scaleX="{SCALE}" android:scaleY="{SCALE}">
    <group android:translateY="{DY}" android:rotation="{ROT}" android:pivotX="50" android:pivotY="50">
        <path android:fillColor="#FF000000" android:fillType="evenOdd" android:pathData="{MONO_D}"/>
    </group>
  </group>
</vector>
''')

# ---------- Colors ----------
DAY = dict(ground='#EAF3FB', surface='#FFFFFF', line='#CFE0F0', text='#12233D', muted='#4F6580', accent='#2A6FD8',
           accentText='#2563C7', accentTint='#DCE9FB', onAccent='#FFFFFF', penalty='#D62E7E', onPenalty='#FFFFFF',
           doneBg='#E1ECF6', doneLine='#D6E4F1', doneInk='#6B7F98', openDash='#9FB6CF')
NIGHT = dict(ground='#10172E', surface='#1B2443', line='#2E3B66', text='#EEF3FF', muted='#A0AECB', accent='#6FA0FF',
             accentText='#8DB4FF', accentTint='#1F2F57', onAccent='#10172E', penalty='#FF5FA2', onPenalty='#10172E',
             doneBg='#131B35', doneLine='#1B2443', doneInk='#8190B0', openDash='#3C4A78')
PLAYERS = [('amber', '#FFC24B'), ('violet', '#B39BFF'), ('lime', '#A8E55C'), ('sand', '#F2E3C4'), ('sky', '#7FA8FF'),
           ('mint', '#4FE0A8'), ('khaki', '#C9B28A'), ('salmon', '#FF9C8A'), ('steel', '#A9B8CC'), ('chartreuse', '#E2F06B'),
           ('chalk', '#E8EDF3'), ('peach', '#FFB27A'), ('rose', '#F2A7C3'), ('leaf', '#6FCF7F'), ('olive', '#9DB86A')]
SCAN = dict(scanBox='#2A6FD8', scanBoxOutline='#FFFFFF', scanCleared='#FFFFFF', scanOnBox='#FFFFFF')

def snake(k):
    return ''.join('_' + c.lower() if c.isupper() else c for c in k)

def colors_xml(theme, extra=True):
    rows = [f'    <color name="sj_{snake(k)}">{v}</color>' for k, v in theme.items()]
    if extra:
        rows.append('    <!-- Player colors (same in both modes) -->')
        rows += [f'    <color name="sj_player_{n}">{h}</color>' for n, h in PLAYERS]
        rows.append('    <!-- Camera overlay (same in both modes) -->')
        rows += [f'    <color name="sj_{snake(k)}">{v}</color>' for k, v in SCAN.items()]
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + '\n'.join(rows) + '\n</resources>\n'

w('android/res/values/colors.xml', colors_xml(DAY))
w('android/res/values-night/colors.xml', colors_xml(NIGHT, extra=False))

tokens = dict(name='Scanjo', developer='Bastelapps',
              icon=dict(tile=TILE, ink=INK, beam=BEAM, rotationDeg=ROT, note='Same icon in light and dark mode'),
              themes=dict(skyDay=DAY, skyNight=NIGHT), players=dict(PLAYERS), scanOverlay=SCAN,
              fonts=dict(display='Fredoka 600 (500/700 available)', mono='DM Mono 400/500'))
w('tokens.json', json.dumps(tokens, indent=2) + '\n')

# ---------- Compose theme ----------
def kt_colors(name, t):
    return f'val {name} = ScanjoColors(\n' + ',\n'.join(f'    {k} = Color(0xFF{v[1:].upper()})' for k, v in t.items()) + ',\n)'
players_kt = ',\n'.join(f'    Color(0xFF{h[1:].upper()}), // {n}' for n, h in PLAYERS)
fields = '\n'.join(f'    val {k}: Color,' for k in DAY)
kt = f'''package net.bastelapps.scanjo.ui.theme

// Generated from the Scanjo brand kit (tokens.json). Two themes: Sky Day (default) and Sky Night (dark mode).
// No Material color scheme is used on purpose: read colors from LocalScanjoColors.

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import net.bastelapps.scanjo.R

@Immutable
data class ScanjoColors(
{fields}
)

{kt_colors('SkyDay', DAY)}

{kt_colors('SkyNight', NIGHT)}

/** Avatar / player colors, identical in both themes. Pink is never a player color (reserved for the ×2 penalty). */
val PlayerColors = listOf(
{players_kt}
)

object ScanOverlay {{
    val box = Color(0xFF2A6FD8)          // 3dp stroke around a detected card
    val boxOutline = Color(0xFFFFFFFF)   // 2dp white outline outside it (Skyjo's low cards are blue)
    val cleared = Color(0xFFFFFFFF)      // dashed, for slots of a cleared column
}}

val Fredoka = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)
val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

object ScanjoType {{
    val score = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, lineHeight = 64.sp)
    val title = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 32.sp)
    val heading = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
    val rowTotal = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
    val button = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val body = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp)
    val tag = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    val label = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.2.em)
}}

val LocalScanjoColors = staticCompositionLocalOf {{ SkyDay }}

@Composable
fun ScanjoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {{
    CompositionLocalProvider(LocalScanjoColors provides if (dark) SkyNight else SkyDay, content = content)
}}
'''
w('compose/ScanjoTheme.kt', kt)

# ---------- Fonts ----------
for f in ['fredoka_medium', 'fredoka_semibold', 'fredoka_bold', 'dm_mono_regular', 'dm_mono_medium']:
    p = os.path.join(OUT, 'android/res/font'); os.makedirs(p, exist_ok=True)
    shutil.copy(os.path.join(FONTS, f + '.ttf'), p)
shutil.copy(os.path.join(FONTS, 'fontsource-fredoka-5.3.0/package/LICENSE'), os.path.join(OUT, 'android/res/font/../../../FONT_LICENSE_Fredoka.txt'))
shutil.copy(os.path.join(FONTS, 'fontsource-dm-mono-5.3.0/package/LICENSE'), os.path.join(OUT, 'android/res/font/../../../FONT_LICENSE_DM_Mono.txt'))

# ---------- Design reference ----------
for f in ['Main.dc.html', 'Logo.dc.html']:
    os.makedirs(os.path.join(OUT, 'design/reference'), exist_ok=True)
    shutil.copy(os.path.join(DESIGN, f), os.path.join(OUT, 'design/reference', f))

# ---------- Feature graphic + preview ----------
def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name + '.ttf'), size)

def paste_icon(img, svg, size, xy):
    tmp = os.path.join(OUT, '_tmp.png')
    cairosvg.svg2png(bytestring=svg.encode(), write_to=tmp, output_width=size, output_height=size)
    ic = Image.open(tmp).convert('RGBA'); img.alpha_composite(ic, xy); os.remove(tmp)

fg = Image.new('RGBA', (1024, 500), DAY['ground'])
paste_icon(fg, icon_svg(radius=23), 260, (110, 120))
d = ImageDraw.Draw(fg)
F = font('fredoka_semibold', 128)
d.text((420, 150), 'scan', font=F, fill=DAY['text'])
d.text((420 + d.textlength('scan', font=F), 150), 'jo', font=F, fill=DAY['accent'])
d.text((424, 310), 'Scan your grid. Lowest score wins.', font=font('fredoka_medium', 34), fill=DAY['text'])
p = os.path.join(OUT, 'store/feature_graphic_1024x500.png'); os.makedirs(os.path.dirname(p), exist_ok=True)
fg.convert('RGB').save(p)

pv = Image.new('RGBA', (1200, 420), DAY['ground'])
x = 40
for s, r in [(256, 23), (128, 23), (96, 50), (48, 23)]:
    paste_icon(pv, icon_svg(radius=r), s, (x, 82 + (256 - s) // 2)); x += s + 40
dark = Image.new('RGBA', (240, 200), NIGHT['ground']); pv.alpha_composite(dark, (x, 40))
paste_icon(pv, icon_svg(radius=23), 128, (x + 56, 76))
paste_icon(pv, mono_svg(), 120, (x + 60, 270))
pv.convert('RGB').save(os.path.join(OUT, 'icon/preview.png'))
print('done')
