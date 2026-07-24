# Store assets — index

Staged Play graphic assets. **Verified specs (2026-07-24, answer/9866151):** icon 512×512 32-bit PNG
(alpha) ≤1024KB; feature graphic 1024×500 JPEG/24-bit PNG (no alpha); phone screenshots min 2 / max 8,
JPEG or 24-bit PNG (no alpha). Re-verify on account day.

| File | What | State |
| --- | --- | --- |
| `feature-graphic.svg` / `.png` | Feature graphic, **1024×500** (verified) | Draft — approve/tweak (see note) |
| `icon-option-a-compass.svg` / `.png` | Launcher/store icon option A, 512×512 | **Gate P5-ICON** — Brandon picks |
| `icon-option-b-paired.svg` / `.png` | Icon option B, 512×512 | Gate P5-ICON |
| `icon-option-c-monogram.svg` / `.png` | Icon option C, 512×512 | Gate P5-ICON |
| `Gate-P5-ICON.md` | The icon decision + options + safe-zone note | OPEN gate |
| `CAPTURE.md` | Pixel-10 screenshot procedure + specs | Device-gated (awaits handset) |
| `01..05-*.png` | The five demo-mode screenshots | **Not yet captured** (no device 2026-07-24) |

**Rendered, dimensions verified (2026-07-24):** all four PNGs above were produced from the SVGs with
headless Chrome and confirmed at exact pixel size (feature 1024×500; icons 512×512), then viewed to
confirm they render on-brand. Evidence: `docs/P5-Evidence.md` §4.

**SVG → PNG recipe (deterministic, exact size):**

```bash
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --window-size=1024,500 --default-background-color=00000000 \
  --screenshot=feature-graphic.png "file:///<abs>/feature-graphic.svg"
# icons: --window-size=512,512 and the icon svg. Verify size before upload:
#   python -c "import struct;b=open('x.png','rb').read();print(struct.unpack('>II',b[16:24]))"
```

**Notes / draft tweaks (non-blocking):**
- Feature graphic: the blue accent rule under "Dashboard" reads a little like a hyperlink underline —
  consider removing it or moving it away from the wordmark before final. Cosmetic only.
- The feature graphic has no alpha (solid slate) so it satisfies the "no alpha" rule directly. The
  512 icon **may** keep alpha (Play allows alpha on the icon).
- Screenshots (`01..05`) require the Pixel 10 — see `CAPTURE.md`.
