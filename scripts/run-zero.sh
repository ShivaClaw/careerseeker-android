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
# Two of the four notification triggers need the GitHub API, which a shell script
# here cannot reach: `gh` is absent from this sandbox (B-7's neighbourhood; see
# C-97-7). Those are printed as a MANUAL section with the exact queries and the
# last verified answers, never guessed at and never folded into the verdict as
# though they had been checked.
#
# Read that limit as the script's, not the session's. Run 99 answered both
# queries through the GitHub MCP server — no `gh` involved — and section 6 now
# says so, because "gh ABSENT" had been read for three runs as "unanswerable"
# when it only ever meant "not answerable from bash". A probe that overstates
# what is out of reach costs as much as one that overstates what it checked.
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

# ---- recorded state, as of run 198 (2026-09-10) ----------------------------
# 2026-09-10, run 198: a MERGE CASCADE began -- 7 merges in 4 minutes (#32, #34, #35, #54,
# #55, #56, #57). 2026-09-11, run 202: it continued and #33 landed inside integration merge
# #59, so ALL THREE slice commits are now on main and 7328a0b joins SLICE_LANDED. Each
# baseline here is the tip MEASURED at the time, and may be stale by the next firing -- that
# is the script working, not failing. A MOVED report means re-derive, not that this is wrong.
#
# 2026-09-11, run 204: run 203 RECORDED the move to 14469ad (C-203-4) but did not re-pin the
# constant below, so section 4 reported "engine main MOVED" on an already-recorded, already-spent
# change -- and would have done so at every future firing. That is precisely the signal-destroying
# staleness this block warns about, committed by the block's own rule. Re-pinned here, and the rule
# restated because it was missed once: THE RUN THAT RECORDS A MOVE RE-PINS THE CONSTANT, in the
# same commit. A baseline nobody advances is a baseline that stops measuring.
BASE_ENGINE_MAIN=14469ad665d3f55a42724ddc2f2fa44585216158   # 2026-09-10 — Brandon, docs-only (run 203/204)
BASE_ANDROID_MAIN=ebfaf8108e635551c3beac851424a4407c5a8fdd  # 2026-08-06
SLICE_COMMITS="8575539 22b028e 7328a0b"                     # the assigned S5 slice
# Run 198 (2026-09-10): a human merged PR #32, so two of the three are now ON main and will
# stay there. Before this, section 1 flagged ANY landing as a change -- correct while nothing
# had landed, but from now on it would fire forever on a permanent condition, which is the
# same signal-destroying staleness the BASE_ENGINE_MAIN comment describes. So record which
# ones are EXPECTED on main; section 1 now flags only a DEVIATION from this expectation.
SLICE_LANDED="8575539 22b028e 7328a0b"                      # expected ancestors of origin/main
BASE_ENGINE_DRAFTS=2                                        # run 204, MCP-measured: #58, #26 only
BASE_ANDROID_DRAFTS=6
BASE_MERGED_SINCE_RUN95=16                                  # the S-series landing, #32..#59
# Run 204: RETURN-DAY.md §3's landing plan is EXECUTED, so fleet-probe reports ROT 6/6 and exits 1
# permanently -- every branch it names is deleted at origin BECAUSE it merged (all six proven
# ancestors of origin/main, C-204-1). Section 3 used to fail the whole verdict on that, which meant
# a spent signal masked every future one. Pinned, on the same "flag a DEVIATION, not the expected
# state" pattern SLICE_LANDED already uses above. Retired, not silenced: a 7th rot still fires.
BASE_PLAN_ROT=6
BASE_PLAN_ROWS=6
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
  expected_landed=no
  for l in $SLICE_LANDED; do [ "$l" = "$c" ] && expected_landed=yes; done
  if git -C "$ENGINE" merge-base --is-ancestor "$c" origin/main 2>/dev/null; then
    if [ "$expected_landed" = yes ]; then
      note "on main (expected)  $desc"
    else
      note "MERGED  $desc"
      bad "$c is now an ancestor of origin/main — THE SLICE LANDED. This is a change."
    fi
  else
    if [ "$expected_landed" = yes ]; then
      bad "$c was expected ON main and is NOT — main may have been rewritten. Re-derive."
    else
      note "off-main  $desc"
    fi
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
plan_out=$("$ANDROID/scripts/fleet-probe.sh" plan "$ENGINE" RETURN-DAY.md 2>&1)
plan_rc=$?
plan_sum=$(printf '%s\n' "$plan_out" | grep -E '^plan rows:' | tail -1)
if [ -z "$plan_sum" ]; then
  printf '%s\n' "$plan_out" | tail -3 | sed 's/^/  /'
  bad "fleet-probe.sh plan printed no summary line (exit $plan_rc) — the guard itself is broken."
else
  note "$plan_sum"
  rot=$(printf '%s\n' "$plan_sum"  | sed -n 's/.*ROT: \([0-9]*\).*/\1/p')
  rows=$(printf '%s\n' "$plan_sum" | sed -n 's/^plan rows: \([0-9]*\).*/\1/p')
  if [ "$rows" != "$BASE_PLAN_ROWS" ]; then
    bad "RETURN-DAY.md §3 now has $rows plan rows, baseline $BASE_PLAN_ROWS — the table was EDITED. Re-derive."
  elif [ "$rot" = "$BASE_PLAN_ROT" ]; then
    note "ROT $rot/$rows is the EXPECTED spent state — §3's plan was executed and its branches"
    note "swept; all six heads are ancestors of origin/main (C-204-1). Not an alarm."
  else
    bad "plan ROT $rot, baseline $BASE_PLAN_ROT — a DEVIATION from the recorded spent state. Re-derive."
  fi
fi

# --- 3b. conflict markers ---------------------------------------------------
# Added run 204. Runs 200 and 202 each COMMITTED AND PUSHED a complete, unresolved merge conflict
# into AUDIT-REQUEST.md and LOG.md -- the two files this program treats as its evidence -- and
# NOTHING detected it (C-203-9). check-citations.sh was green across the corruption and green after
# the repair, so it never measured this at all. Run 203 found it by hand, repaired it, and recorded
# that this guard belongs here; this is that guard.
#
# Keyed on '<<<<<<< ' and '>>>>>>> ' ONLY. A bare '=======' is deliberately NOT a trigger: it is
# also a valid Markdown setext H1 underline, so it would false-positive on ordinary prose. Both
# repos are free of such underlines today, but a guard that depends on that staying true is a guard
# that will cry wolf later. The two unambiguous markers cannot occur in a resolved file, and every
# real conflict carries both.
head2 "3b. Conflict markers — committed '<<<<<<<' / '>>>>>>>' in tracked Markdown"
for r in "$ANDROID" "$ENGINE"; do
  hits=$(git -C "$r" grep -n -E '^(<<<<<<< |>>>>>>> )' -- '*.md' 2>/dev/null)
  if [ -z "$hits" ]; then
    note "clean  $(basename "$r")"
  else
    printf '%s\n' "$hits" | sed 's/^/    /'
    bad "$(basename "$r") has COMMITTED conflict markers — the record is corrupt. Repair before anything else."
  fi
done

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
head2 "6. MANUAL — two notification triggers THIS SCRIPT cannot answer (your session may)"
cat <<EOF
  Run 82's standing test notifies on: main moving (checked above, section 4),
  a PR merged or undrafted, the stored prompt changing, or a gate result.
  It does NOT fire on another firing, and NOT on another draft PR.

  READ SECTION 5's 'gh ABSENT' NARROWLY. It means the gh BINARY is not on PATH
  in this container — it does NOT mean your session has no GitHub API path.
  Run 99 answered both queries below through the GitHub MCP server, which needs
  no gh and which runs 96-98 did not try (C-99-1). A shell script cannot reach
  that server, so this section stays MANUAL and stays out of the verdict; but
  'this script cannot' is not 'you cannot'. TRY THE QUERIES BEFORE DEFERRING.

  Query these two, via the GitHub MCP server or any API path you have:

    list_pull_requests owner=ShivaClaw repo=careerseeker         state=all
    list_pull_requests owner=ShivaClaw repo=careerseeker-android state=all

  Last VERIFIED (run 204, 2026-09-11, MCP): THE CASCADE IS OVER AND THE QUEUE IS
  DRAINED. Engine ${BASE_ENGINE_DRAFTS} open (#58 audit F01/F02, awaiting Codex; #26 SBOM, human
  queue Q07) -- both draft. Android ${BASE_ANDROID_DRAFTS} open, all draft, and ZERO android PRs have
  EVER merged. ~${BASE_MERGED_SINCE_RUN95} engine PRs landed 2026-09-10/11 (#32..#59). The queue went 18 -> 2,
  which is the thing 150+ firings were waiting on: the LANDING problem is SOLVED.

  TWO TRAPS when you re-query, both measured, both costly:
   1. 'merged' reads false for PRs that demonstrably merged (C-89-2).
   2. 'merged_at' is ALSO null for PRs landed inside an INTEGRATION branch and
      closed by hand -- #36, #51, #49 each read closed-with-no-merged_at and are
      each an ancestor of origin/main (C-204-1). A count of merged_at UNDERSTATES
      the landing and can read as deleted-unmerged work, which it is not.
   => The commit graph is the only authority: merge-base --is-ancestor <head> origin/main.
      #53 is the ONE head deliberately not on main (closed as superseded).

  ALSO QUERY REPOSITORY VISIBILITY -- the gap B-29 was found in (run 203):

    get_repository owner=ShivaClaw repo=careerseeker-android   -> .private / .visibility
    get_repository owner=ShivaClaw repo=careerseeker-ios       -> .private / .visibility

  Every drift check in both repos compares FILE CONTENTS; nothing anywhere asserts
  a repository SETTING, which is why 202 runs never saw this. Last VERIFIED (run 203):
  BOTH report "private": false while android README.md:7 says "This repository is
  private, always." B-29 is OPEN and is the owner's decision, not a firing's -- do
  NOT flip it. Re-read B-29 before reporting; if the owner has answered, the answer
  decides which way this check should assert, and it can then move out of MANUAL.

  The stored prompt: compare against the two facts known stale — it still says
  pin '679a317' (real pin is in section 2) and 'S5 ... NOT STARTED' (section 1).
EOF

# --- verdict ----------------------------------------------------------------
head2 "VERDICT"
if [ "$FAIL" -eq 0 ]; then
  cat <<'EOF'
  NOTHING MOVED on every check this sandbox can run, and all four guards are green
  (citations, plan-rot against its pinned spent state, conflict markers, vectors).

  READ THIS BEFORE CONCLUDING THE LANE IS STILL WHAT IT WAS. The ground state this
  script described for ~100 firings ended on 2026-09-10/11. The owner landed the
  S-series himself: the queue went 18 open PRs -> 2, 26 branches were swept, and he
  wrote docs/Codex-Resume-Handoff.md on engine main. The 'landing problem' that
  every one of those firings re-derived and could not touch IS SOLVED, and it was
  never a building problem.

  So B-18's long-standing premise -- 'escalations go into an empty room; nobody is
  reading' -- is FALSE as of run 203, and the ledger clause asserting it is RETIRED.
  Do not re-arm a calendar escalation on it without re-deriving. What is left needs
  a human for reasons of ACCESS, not attention: a Windows gate, an emulator (B-4),
  a relay deploy (relay code is landed but NOT deployed), and B-29's visibility
  decision. None of those is unblockable from this sandbox, and no amount of
  re-derivation changes that.

  B-18's smallest human unblock is unchanged: a human stops or repoints the schedule.
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
