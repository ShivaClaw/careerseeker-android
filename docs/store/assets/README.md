# Store assets — index

Staged Play graphic assets, on the **official CareerSeeker brand** (from Claude Design:
project "CareerSeeker Site Reskin"). Palette near-black `#0A0A0B` · lime `#A3FF12` · yellow
`#FFE500`; radar/targeting logo mark. **Verified specs (2026-07-24, answer/9866151):** icon 512×512
32-bit PNG (alpha) ≤1024KB; feature graphic 1024×500 JPEG/24-bit PNG (no alpha); phone screenshots
min 2 / max 8, JPEG or 24-bit PNG (no alpha).

| File | What | State |
| --- | --- | --- |
| `icon.svg` / `.png` | Play hi-res icon, **512×512**, official radar logo | Done — rendered + viewed |
| `feature-graphic.svg` / `.png` | Feature graphic, **1024×500**, logo + wordmark | Done — rendered + viewed (embed Space Grotesk for final) |
| `Gate-P5-ICON.md` | **RESOLVED** — brand decided; in-app launcher swap pending Design-Language call | — |
| `CAPTURE.md` | Pixel-10 screenshot procedure + specs | Device-gated (awaits handset) |
| `01..05-*.png` | The five demo-mode screenshots | **Not yet captured** (no device 2026-07-24) |

**Rendered + verified (2026-07-24):** `icon.png` (512×512) and `feature-graphic.png` (1024×500)
produced from the SVGs with headless Chrome, pixel sizes confirmed via PNG IHDR, and viewed to
confirm they reproduce the official logo on-brand.

**Source of truth:** the logo vector is `uploads/site-v2-backup-2026-07-22/assets/logo.svg` in the
Claude Design "CareerSeeker Site Reskin" project. The SVGs here embed that mark's exact paths.

**SVG → PNG recipe (deterministic, exact size):**

```bash
CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --window-size=1024,500 --default-background-color=00000000 \
  --screenshot=feature-graphic.png "file:///<abs>/feature-graphic.svg"   # icon: --window-size=512,512
# verify: python -c "import struct;b=open('x.png','rb').read();print(struct.unpack('>II',b[16:24]))"
```

**Notes:**
- Wordmark in the feature graphic renders with a system-sans fallback here (Space Grotesk isn't
  installed for headless Chrome). For the production PNG, install/embed Space Grotesk or convert the
  text to paths so the wordmark matches the brand exactly.
- The 512 icon may keep alpha (Play allows it); the feature graphic is solid `#0A0A0B` (no alpha).
- Screenshots (`01..05`) require the Pixel 10 — see `CAPTURE.md`.
- The **in-app** adaptive launcher icon is still the placeholder; swapping it to this mark is folded
  into the Design-Language implementation decision (`Gate-P5-ICON.md`).
