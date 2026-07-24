# Gate P5-ICON — choose the launcher / store icon (Brandon decides)

**Status:** OPEN gate. The launcher icon shipping today is the **template placeholder** — a
stylised compass needle with an in-code note "Placeholder art. Real launcher assets are a P5
store-readiness deliverable" (`app/src/main/res/drawable/ic_launcher_foreground.xml`,
`res/mipmap-anydpi/ic_launcher.xml`). Play requires a **512×512 32-bit PNG (with alpha), ≤1024KB**
hi-res icon (verified 2026-07-24, answer/9866151), and the on-device adaptive launcher icon should
match it. Rather than silently ship placeholder art, three concrete options are drafted below for
your pick. **No icon is committed as final until you choose.**

Brand palette (from the app): slate `#1B2733` · off-white `#F2F5F7` · blue `#7FB2D9`.

---

## The three options (rendered 512×512 PNGs in this folder — open to view)

### Option A — "Compass" · `icon-option-a-compass.svg` / `.png`  ← recommended
The existing needle motif, refined, inside a dashed compass ring. Navigation/"seeking" metaphor;
**brand-continuous** with what's in the repo today; simplest silhouette, so it reads cleanly at a
48dp launcher size. Lowest risk.

### Option B — "Paired link" · `icon-option-b-paired.svg` / `.png`
A desktop monitor and a phone joined by an encrypted link. **Most literal** about what the app *is*
(a paired window to your PC). Trade-off: two devices + a connector is the busiest of the three and
loses detail at very small sizes.

### Option C — "Monogram C + needle" · `icon-option-c-monogram.svg` / `.png`
A bold ring-shaped letter **C** cradling the compass needle. Combines a recognizable lettermark with
the seeking motif — the most **distinctive** as a standalone brand mark. Trade-off: the needle inside
the C is a touch more intricate than A.

| | A · Compass | B · Paired link | C · C-monogram |
| --- | --- | --- | --- |
| Brand continuity | ✅ strongest | ➖ new | ◐ needle kept |
| Reads at 48dp | ✅ | ➖ busiest | ✅ |
| "What is it?" clarity | ◐ abstract | ✅ literal | ◐ abstract |
| Distinctiveness | ◐ | ✅ | ✅ strongest |

**Recommendation:** **Option A** for the safest, most legible launcher icon that stays continuous
with the current mark; **Option C** if you want a more ownable brand symbol. B is the clearest
"paired-to-your-PC" story if that literal read matters more than small-size legibility.

---

## Adaptive-icon safe zone (applies to whichever you pick)

The on-device launcher icon is an **adaptive icon**: the OS masks it to a circle/squircle/etc., and
only the **central ~66dp of the 108dp** foreground is guaranteed visible. All three marks are drawn
within the central region, but the compass **ring** in Option A sits near that boundary — if you pick
A, nudge the ring inward ~8–10px before final so no mask clips it. The 512×512 Play hi-res icon is an
unmasked square, so the full art shows there regardless.

---

## What happens after you pick (follow-up, not done now)

1. Finalize the chosen SVG (safe-zone nudge; the Option-A "Dashboard" feature-graphic underline tweak
   is separate, see `README.md`).
2. Export the **512×512** hi-res PNG for the Play listing (headless-Chrome recipe in `README.md`).
3. Replace `ic_launcher_foreground.xml` with the chosen vector (adaptive foreground) — a small,
   additive asset change on `claude/p5-store`, run through the full Gradle ritual, CI green.
4. Keep the slate background (`@color/ic_launcher_background = #1B2733`) or update it to match.

Until then the placeholder stays in the build; it does not block the staged listing text.

*Decision owner: Brandon. Record the pick here (A / B / C / "iterate on X") and the follow-up commit
closes the gate.*
