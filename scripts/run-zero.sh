#!/usr/bin/env bash
#
# run-zero.sh — the whole of a firing's re-derivation, in one command.
#
# WHY THIS EXISTS
#
# B-18 records that this routine keeps firing past its own stop condition, and
# that its smallest human unblock — a person stopping the schedule — has not
# happened. Every attempt logged against B-18 so far has tried to lower the COST
# of a firing rather than end it: run 48 put a banner where the reader actually
# looks, run 53 pushed the same facts outside the repository. This is the next
# one, and it aims at the largest remaining cost.
#
# Runs 96, 97 and 98 each independently re-derived the same state and rejected
# every candidate slice they could find — eight candidates between them, one
# answer. The derivation itself is not the expensive part; the four record files
# are ~45,000 lines and reading enough of them to know that nothing moved is what
# each firing actually spends itself on. This script answers "has anything moved
# since the last recorded run?" from the repositories, in seconds, so a firing
# that finds NOTHING MOVED can stop early and cheaply instead of re-deriving it
# by hand for the ninth time.
#
# WHAT THIS IS NOT
#
# This is NOT a gate, and it does not pretend anything ran that did not. It runs
# no build, no test suite, and no Verify-Alpha.ps1. It does not decide whether a
# slice is worth taking — it establishes the ground state a run needs before it
# can decide, and it says out loud which checks it cannot perform here.
#
# Two of the four notification triggers need the GitHub API and `gh` is absent
# from this sandbox (B-7's neighbourhood; see C-97-7). Those are printed as a
# MANUAL section with the exact queries and the last recorded answers, never
# guessed at and never folded into the verdict as though they had been checked.
#
# BASELINES
#
# The recorded-state constants below are what "unmoved" means. They are pinned
# deliberately, the same way VECTORS.lock pins the corpus: a mismatch is the
# signal this script exists to raise, not a bug in the script. When something
# legitimately moves, the run that records the move updates the constant in the
# same commit — that is the doc/verifier drift rule the engine repo's CLAUDE.md
# states, applied here.
#
# USAGE
#
#   scripts/run-zero.sh [<engine-checkout>]      # default ../careerseeker
#
# Exit 0  — NOTHING MOVED, and every local check passed.
# Exit 1  — something moved, or a local check failed. Read the report; do not
#           proceed on the strength of the last recorded run's conclusions.

set -uo pipefail

ENGINE=${1:-../careerseeker}
ANDROID=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

# ---- recorded state, as of run 98 (2026-08-25) -----------------------------
BASE_ENGINE_MAIN=aac05f3f93f0ca06cbc9dfa7884f74a126f078dc   # 2026-08-12
BASE_ANDROID_MAIN=ebfaf8108e635551c3beac851424a4407c5a8fdd  # 2026-08-06
SLICE_COMMITS="8575539 22b028e 7328a0b"                     # the assigned S5 slice
BASE_ENGINE_DRAFTS=22
BASE_ANDROID_DRAFTS=6
BASE_MERGED_SINCE_RUN95=0
# ---------------------------------------------------------------------------

FAIL=0
note()  { printf '  %s\n' "$*"; }
head2() { printf '\n== %s\n' "$*"; }
bad()   { printf '  !! %s\n' "$*"; FAIL=1; }

if [ ! -d "$ENGINE/.git" ]; then
  echo "run-zero: '$ENGINE' is not a git checkout. Pass the engine clone as \$1." >&2
  exit 1
fi
ENGINE=$(cd "$ENGINE" && pwd)

# The android root is derived from this script's own location, so a copy of this
# file run from somewhere else would report confidently about the wrong tree.
# Refuse rather than mislead: every section below reads from $ANDROID.
for marker in STATE.md scripts/repin-vectors.sh \
              core/src/test/resources/sync-vectors/VECTORS.lock; do
  if [ ! -e "$ANDROID/$marker" ]; then
    echo "run-zero: '$ANDROID' does not look like the android checkout (no $marker)." >&2
    echo "run-zero: run this script from its place in scripts/, not from a copy." >&2
    exit 1
  fi
done

echo "run-zero — one firing's re-derivation"
echo "  android : $ANDROID"
echo "  engine  : $ENGINE"

# --- 0. rule one ------------------------------------------------------------
head2 "0. Rule one — fetch both checkouts (every count below is taken after this)"
for r in "$ANDROID" "$ENGINE"; do
  if git -C "$r" fetch --all --prune >/dev/null 2>&1; then
    note "fetched $(basename "$r")"
  else
    bad "fetch FAILED in $r — every count below is untrustworthy. Stop."
  fi
done

# --- 1. the assigned slice --------------------------------------------------
head2 "1. The assigned S5 slice — built, and still off main?"
for c in $SLICE_COMMITS; do
  if ! git -C "$ENGINE" cat-file -e "${c}^{commit}" 2>/dev/null; then
    bad "$c does NOT exist in the engine checkout — re-derive from scratch."
    continue
  fi
  desc=$(git -C "$ENGINE" log -1 --format='%h %ad %s' --date=short "$c")
  if git -C "$ENGINE" merge-base --is-ancestor "$c" origin/main 2>/dev/null; then
    note "MERGED  $desc"
    bad "$c is now an ancestor of origin/main — THE SLICE LANDED. This is a change."
  else
    note "off-main  $desc"
  fi
done

# --- 2. vectors and the pin -------------------------------------------------
head2 "2. Vectors — generator at the pin, and the vendored corpus against it"
if out=$("$ANDROID/scripts/repin-vectors.sh" --check 2>&1); then
  printf '%s\n' "$out" | sed 's/^/  /'
else
  printf '%s\n' "$out" | sed 's/^/  /'
  bad "repin-vectors.sh --check FAILED — cross-repo drift. Stop and read VECTORS.lock."
fi

# --- 3. repository guards ---------------------------------------------------
head2 "3. Repository guards"
if out=$("$ANDROID/scripts/check-citations.sh" 2>&1 | tail -2); then
  printf '%s\n' "$out" | sed 's/^/  /'
else
  printf '%s\n' "$out" | sed 's/^/  /'
  bad "check-citations.sh FAILED — a cited C-/B- id does not resolve."
fi
if out=$("$ANDROID/scripts/fleet-probe.sh" plan "$ENGINE" RETURN-DAY.md 2>&1 | tail -3); then
  printf '%s\n' "$out" | sed 's/^/  /'
else
  printf '%s\n' "$out" | sed 's/^/  /'
  bad "fleet-probe.sh plan FAILED — the landing plan rotted. Re-derive it."
fi

# --- 4. have the mains moved? ----------------------------------------------
head2 "4. Both mains, against the recorded baselines"
check_main() {
  local repo=$1 label=$2 want=$3 got
  got=$(git -C "$repo" rev-parse origin/main 2>/dev/null)
  local when; when=$(git -C "$repo" log -1 --format='%ad' --date=short origin/main 2>/dev/null)
  if [ "$got" = "$want" ]; then
    note "$label main unmoved  ${got:0:7}  ($when)"
  else
    bad "$label main MOVED  ${want:0:7} -> ${got:0:7}  ($when) — re-derive everything."
  fi
}
check_main "$ENGINE"  "engine " "$BASE_ENGINE_MAIN"
check_main "$ANDROID" "android" "$BASE_ANDROID_MAIN"

# --- 5. toolchain, so nothing is claimed that could not have run ------------
head2 "5. Toolchain — stated so no claim can be misread"
for t in dotnet pwsh sdkmanager avdmanager emulator adb gh node git java gradle; do
  printf '  %-12s %s\n' "$t" "$(command -v "$t" >/dev/null 2>&1 && echo PRESENT || echo ABSENT)"
done
printf '  %-12s %s\n' "ANDROID_HOME" "${ANDROID_HOME:-UNSET}"
note ""
note "No gate is reachable from here: neither Verify-Alpha.ps1 nor the five-task"
note "android command. scripts/core-probe.sh runs :core:test — ONE of those five."

# --- 6. what this cannot check ---------------------------------------------
head2 "6. MANUAL — two notification triggers this script cannot answer (gh absent)"
cat <<EOF
  Run 82's standing test notifies on: main moving (checked above, section 4),
  a PR merged or undrafted, the stored prompt changing, or a gate result.
  It does NOT fire on another firing, and NOT on another draft PR.

  Query these two by hand, via the GitHub API, and compare:

    list_pull_requests owner=ShivaClaw repo=careerseeker         state=all
    list_pull_requests owner=ShivaClaw repo=careerseeker-android state=all

  Last recorded (run 98): ${BASE_ENGINE_DRAFTS} engine + ${BASE_ANDROID_DRAFTS} android open, ALL draft:true,
  ${BASE_MERGED_SINCE_RUN95} merged since. Note that the list rows' 'merged' field reads false even for
  PRs that demonstrably merged (C-89-2) — use the commit graph, not that field.

  The stored prompt: compare against the two facts known stale — it still says
  pin '679a317' (real pin is in section 2) and 'S5 ... NOT STARTED' (section 1).
EOF

# --- verdict ----------------------------------------------------------------
head2 "VERDICT"
if [ "$FAIL" -eq 0 ]; then
  cat <<'EOF'
  NOTHING MOVED on every check this sandbox can run, and all three guards are green.

  If the two MANUAL checks above also come back unchanged, then this firing has
  the same ground state as run 98, and STATE.md's newest banner already describes
  it. Runs 96, 97 and 98 each derived candidate slices independently — eight
  between them — and the standing precondition rejected all eight. The lane is
  exhausted; that is a recorded finding, not a fresh one to re-discover.

  B-18's smallest human unblock is unchanged: a human stops the schedule.
EOF
  exit 0
else
  cat <<'EOF'
  SOMETHING MOVED, or a local check failed. Do NOT carry forward the last recorded
  run's conclusions. Re-derive from the repositories, and treat the flagged line
  above as this firing's finding.
EOF
  exit 1
fi
