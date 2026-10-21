# Scan7 project brief: Flip 7 card scanner

> Living document. Initial version from 2026-10-18; details may still change.

## Goal
An Android app that scans Flip 7 cards on the table with the phone camera, recognizes each card on-device, and calculates the player's score automatically. Players can correct mistakes before the score is saved.

## Branding
The brand kit is in `brand/` (see `brand/BRAND.md`).
- App name: **Scan7** (original name and logo; never use Flip 7's artwork, fonts or logo in the app).
- Launcher icon: adaptive icon from `brand/ic_launcher_foreground.svg`, background `#38D6F5`, monochrome layer `brand/ic_launcher_monochrome.svg`.
- Colors (dark theme by default): ground `#0A1220`, surface `#15223A`, text `#EEF4FA`, muted text `#93A4BC`, accent cyan `#38D6F5`, bust magenta `#FF4F9A`. Resources in `brand/colors.xml`.
- Bust is always magenta **plus** the word "Bust" or an icon (never color alone; never orange or red, which are card colors).
- Fonts: Unbounded (Bold) for the wordmark and big score numbers; DM Mono or the system font for UI text.

## Setup
- MacBook Pro M2 Pro, 16 GB RAM (all development and training happens locally)
- Android phone connected over USB for testing (the emulator camera is not good enough)
- Android Studio installed; Python 3.12 in a virtual environment (managed with `uv`) for the ML work

## Repository layout
```
scan7/
  docs/          ← this brief, notes
  ml/            ← Python: data generation, training, export
    data/
      cards_flat/    ← one straight-on photo per card on a dark background, named by class
      backgrounds/   ← 30–50 photos of tables, floors, fabrics, desk mats, no cards
      real_photos/   ← real game photos with multiple cards (to be labeled by hand)
    scripts/
  app/           ← Android app (Kotlin + Compose)
```

## Tech stack
- **App:** Kotlin and Jetpack Compose, CameraX for the camera, LiteRT (TFLite) with the GPU delegate for inference.
- **ML:** Python, PyTorch with the MPS backend (`device="mps"`; set `PYTORCH_ENABLE_MPS_FALLBACK=1` if an op is unsupported).
- **Model:** a small object detector (e.g. YOLO nano/small at 640px), exported to LiteRT with int8 quantization.
- **Licensing note:** Ultralytics YOLO is AGPL. That is fine for a personal project. If the app might be sold, use an Apache-licensed alternative (MediaPipe Model Maker, RT-DETR) or a commercial license. **Decision (2026-10-18): Ultralytics YOLO.** The app stays personal/open source. A commercial release would need an Ultralytics license or a switch to an Apache-licensed model.
- **Fallback architecture** (only if look-alike classes are confused): a 1-class card detector plus a crop classifier.

## Cards (German edition)
23 classes:
- Numbers: `00`–`12` (13 classes)
- Modifiers: `plus2`, `plus4`, `plus6`, `plus8`, `plus10`, `x2`
- Actions: `freeze`, `flip3`, `second_chance`
- `back` (face-down card, should be detected but ignored in scoring)

Useful facts about the designs:
- Number cards are cream with a distinct color per number; modifiers are orange; Second Chance is red; Freeze is light blue; Flip Three is yellow.
- 6 and 9 have a dot under them, so full 360° rotation is safe in augmentation.
- Similar-looking pairs to watch: 3 / 7 / 10 (reddish), 1 / 12 (gray).
- Number cards have a word banner at the bottom (German: EINS, ZWEI…). The model must ignore this text so it also works with the English edition (ONE, TWO…).
- **Card variants across editions** (same class, different look): the English edition's rainbow-striped `00` ("ZERO"), other card backs (e.g. turquoise frame, "THE GREATEST CARD GAME OF ALL TIME!"), and English banners/modifier text. In the English edition 6 and 9 have **no dot**, so orientation comes only from the banner or arch text. The generator gets the variants as extra cutouts of their class (`<class>_<n>`). "Flip 7: With a Vengeance" is a different game: its rules and cards are in the app as a debug preview (`GameKind.VENGEANCE`, `Deck.VENGEANCE`, typed in); the detector gets its own class set from photos of the real cards.
- The deck is unbalanced: one 0, one 1, two 2s … twelve 12s. It has few modifiers and actions. The training data must be class-balanced.

## How cards lie on the table
- 1–10 cards per player, side by side in a row, flat on the table.
- Normally not overlapping, sometimes slightly overlapping.
- The app scans **one player's row at a time** (decided 2026-10-18). Neighbouring rows may be partly visible in the frame.

## Milestones

### 1. Card cutouts
- A script detects each card in `cards_flat/` against the dark background, corrects perspective, and saves a clean rectangular cutout with a rounded-corner alpha mask.
- Output a contact sheet image to check all 23 cutouts at a glance.
- Measure the word-banner area on number cards once (as a fraction of card size) and store it in a config file.
- Ideally 2–3 photos per class (different physical copies / lighting) to avoid overfitting to a single photo.

### 2. Synthetic data generator
The generator writes YOLO-format images and labels:
- Compose top-down onto a random background crop. The main layout is 1–10 cards side by side in rows, mostly non-overlapping with occasional slight overlap. A smaller share of scattered, fanned or more heavily overlapping layouts adds robustness. Random rotation (full 360°) and scale.
- Then apply a perspective warp to the whole scene (simulating a phone held at an angle), and transform the box labels to match.
- Augment: brightness, contrast, color temperature (warm lamp light), shadows, glare spots, blur, noise, JPEG compression.
- Text randomization on ~50% of number cards: replace the banner with random fake words, blur it, or fill it with the card's cream color.
- Random erasing of small patches (fingers, other objects).
- Occlusion rule: drop a label if more than ~70% of the card is hidden.
- Class balancing so rare cards appear as often as common ones.
- Configurable count (start with ~5,000), train/val split.
- A preview script that draws boxes and labels on random samples to verify correctness.

### 3. Real photo labeling
- Set up Label Studio (or similar) with the 23 classes, export to YOLO format.
- Pre-label with the synthetic-trained model so only corrections are needed.
- Split real photos: a fixed **test set that is never used for training**, and the rest for fine-tuning.

### 4. Training and evaluation
- Train on synthetic data, then fine-tune with real photos.
- Evaluate on the real test set: mAP, per-class precision/recall, confusion matrix. Report the weakest classes.
- Check text-independence: test images where the banner is covered.
  Plan (after the real photos are labeled, together with the fine-tune):
  1. Synthetic language test: same test scenes with banners as German / English (ONE…TWELVE, never used in training) / blank; compare per-class precision/recall.
  2. Real test photos with banners covered by paper (5–10, in real_photos/test).
  3. English-edition photos if available.
  If text matters, also randomize the small modifier text ("DIE SUMME DEINER ZAHLENKARTEN +2") and use foreign (non-English) words on banners in the generator, then fine-tune.

### 5. Export
- Export to LiteRT (int8, plus fp16 for comparison). Verify the exported model gives the same detections as the PyTorch model on test images.

### 6. Android app
- CameraX preview and image analysis, running the model on the GPU delegate.
- Draw boxes and labels over the preview.
- Temporal smoothing: only accept a card after it is detected consistently across several frames.
- Freeze-frame "scan" button, then a review screen where players can tap a card to correct or remove it. (Open idea: scan runs on a full-resolution still capture instead of the preview frame.)
- Multiple players, score per round, running totals; the game ends at 200 points.

### 7. Scoring logic (plain code, never the model)
- Sum of the number cards.
- If any number appears twice, the player busts (0 points). Show a warning so the player can confirm (Second Chance may have been used).
- `x2` doubles the number-card sum only.
- Then add `plus` modifiers.
- +15 bonus for 7 different number cards.
- Action cards and `back` don't affect the score.
- Unit tests for all of these rules.

## Working agreement
- One milestone at a time. Show results (contact sheet, sample previews, metrics) before moving on.
- Run the scripts and fix errors directly.
- Keep the README up to date with setup steps and the commands to run each stage.
- Ask before big decisions (model/license choice, app UX changes).
