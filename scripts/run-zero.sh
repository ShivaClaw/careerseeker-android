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
# Some notification triggers are printed as a MANUAL section with the exact
# queries and the last verified answers, never guessed at and never folded into
# the verdict as though they had been checked.
#
# Read that limit as the script's, not the session's. Run 99 answered the board
# queries through the GitHub MCP server — no `gh` involved — and section 6 now
# says so, because "gh ABSENT" had been read for three runs as "unanswerable"
# when it only ever meant "not answerable from bash". A probe that overstates
# what is out of reach costs as much as one that overstates what it checked.
#
# RUN 227 APPLIED THAT LESSON TO THIS SCRIPT ITSELF, AND IT COST A TRIGGER'S
# WORTH OF BLINDNESS TO LEARN. The paragraph above used to say the API was
# unreachable from bash. Nobody had tried: `curl` reaches api.github.com
# anonymously, HTTP 200, for both the runs list and the per-run step array
# (C-227-1). So "a gate result" — trigger 4, and the one B-31 was filed on —
# is no longer MANUAL. It is §4b, it executes, and it can fail the verdict.
#
# RUN 228 FINISHED THAT THOUGHT. Run 227 watched ONE of the two gates and said so,
# recording the engine repo's identical exposure as next intent. §4c now reads it:
# the same check, the same verb (read, never ran), pointed at engine `main`, where
# `Verify-Alpha.ps1` enforces $ExpectedOfflineTotal and the doc/verifier drift trap
# and the relay job runs the engine-side copy of the shared-vector guard. Watching
# one end of a cross-repo invariant is not watching it.
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
#   RUNZERO_GATE_RUN=<run id> scripts/run-zero.sh          # §4b replay (android)
#   RUNZERO_ENGINE_GATE_RUN=<run id> scripts/run-zero.sh   # §4c replay (engine)
#
# Exit 0  — NOTHING MOVED, and every local check passed.
# Exit 1  — something moved, or a local check failed. Read the report; do not
#           proceed on the strength of the last recorded run's conclusions.
#
# §4b/§4c can also print '??' without failing: that is "the gate could not be READ",
# which is not "the gate is fine". The VERDICT repeats it for that reason.

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
#
# 2026-09-17, run 238: the SECOND time a firing recorded a move and left the constant behind --
# the same defect run 204 fixed on BASE_ENGINE_MAIN, in the same block, on a different line.
# Engine drafts went 2 -> 3 when #60 (harness-count drift) opened; run 222 was the first firing
# whose LEDGER LINE says `board 3+6 open` (FIRINGS.md:166) and every line since says the same,
# so SIXTEEN firings recorded the move while §6 below kept printing "Engine 2 open ... both
# draft" -- a generated narrative contradicting, word for word, the ledger written beside it.
# It read as authoritative because it is stamped "Last VERIFIED (run 204, MCP)", which is how a
# stale baseline does its damage: it does not look uncertain. Re-pinned to the MCP-measured 3,
# and the rule restated because it has now been missed twice: THE RUN THAT RECORDS A MOVE
# RE-PINS THE CONSTANT, in the same commit -- a ledger field is not a re-pin.
BASE_ENGINE_DRAFTS=3                                        # run 238, MCP-measured: #60, #58, #26
BASE_ANDROID_DRAFTS=6
BASE_MERGED_SINCE_RUN95=16                                  # the S-series landing, #32..#59
# Run 204: RETURN-DAY.md §3's landing plan is EXECUTED, so fleet-probe reports ROT 6/6 and exits 1
# permanently -- every branch it names is deleted at origin BECAUSE it merged (all six proven
# ancestors of origin/main, C-204-1). Section 3 used to fail the whole verdict on that, which meant
# a spent signal masked every future one. Pinned, on the same "flag a DEVIATION, not the expected
# state" pattern SLICE_LANDED already uses above. Retired, not silenced: a 7th rot still fires.
BASE_PLAN_ROT=6
BASE_PLAN_ROWS=6

# ---- the gate check, added run 227 (2026-09-15) — B-31's open half ---------
# B-31 recorded that NO section of this script looks at a workflow run, so runs 222-225 each
# printed NOTHING MOVED while their own pushes were dying in step 4 of 14 and every check
# below it reported `skipped`. B-31's "smallest unblock" said the fix could not be done in
# bash because `gh` is ABSENT, and therefore had to be a fifth MANUAL paragraph relying on the
# session to act -- which §6 already does, and which 225 firings read past.
#
# THAT PREMISE IS FALSE, AND §6's OWN RULE IS WHAT CATCHES IT: 'gh ABSENT' means the gh BINARY
# is not on PATH, never that this container has no GitHub API path. Run 227 measured it --
# api.github.com answers 200 unauthenticated for both the runs list and the per-run step array
# (C-227-1). So this is NOT a fifth advisory paragraph. It is a check that RUNS, and it can
# move the verdict, which is the only difference that mattered.
#
# It matches steps BY NAME, never by number: the job's step array also carries 'Set up job'
# and three 'Post ...' entries, and their numbering is not contiguous (1-14, then 26-29).
#
# 'Upload debug APK' is deliberately NOT required. It is gated on workflow_dispatch (B-25), so
# on an ordinary push it is skipped BY DESIGN. That one legitimate skip beside eight mandatory
# ones is exactly the B-25/B-31 ambiguity -- 'a red job with a green gate' vs 'a red job with
# no gate at all' -- so the distinction is encoded here rather than left to a reader's memory.
GATE_OWNER=ShivaClaw
GATE_REPO=careerseeker-android
GATE_WORKFLOW=ci.yml
GATE_BRANCH=claude/android-a0-probe     # fallback only; the checked-out branch wins
GATE_REQUIRED_STEPS='Assert every cited C-/B- id resolves
Assert :core has no Android dependency
Assert vendored sync vectors match the pinned main-repo commit
Unit tests (:core)
Unit tests (:app, Robolectric)
Assemble debug APK
Lint
Assert no analytics or tracking SDKs ship'
GATE_OPTIONAL_STEPS='Upload debug APK'   # B-25: workflow_dispatch-gated, skipped by design

# ---- the ENGINE gate, added run 228 (2026-09-15) — §4b's other half --------
# Run 227 built §4b for the android repo and recorded, as next intent rather than a blocker,
# that "the engine repo's CI has the identical exposure and is NOT yet watched". This is that
# half, and the exposure is worse there than here, for a reason specific to what that CI runs.
#
# The android gate guards a build. The ENGINE gate is where `Verify-Alpha.ps1` executes, and
# that script is the enforcement point for the engine repo's two documented failure modes:
# `$ExpectedOfflineTotal` (a dropped harness assertion becomes a hard failure instead of a
# quiet count drop) and the doc/verifier drift trap. Its relay job separately runs
# `node docs/sync-vectors/generate.mjs --check` -- the SAME cross-repo vector guard that
# §4b watches on the android side. So the corpus this program pins has a guard at each end,
# and until this section existed the firing routine watched exactly one of them.
#
# Branch is `main`, not a work branch: main is where the drift trap has to hold. Nothing here
# is claimed to have RUN in this sandbox -- Verify-Alpha.ps1 needs Windows (B-7). This READS
# what windows-latest reported, which is the same verb §4b uses.
#
# Required steps verified first-person against .github/workflows/ci.yml at engine main
# 14469ad and against the live step arrays of runs 495 and 497 (C-228-1). Two jobs:
# 'Build and offline harnesses' on windows-latest, 'Blind relay (Worker)' on ubuntu-latest.
# There is NO legitimate skip in this workflow -- no workflow_dispatch-gated step, no B-25
# analogue -- so GATE2_OPTIONAL_STEPS is deliberately empty and ANY skip is a finding.
GATE2_OWNER=ShivaClaw
GATE2_REPO=careerseeker
GATE2_WORKFLOW=ci.yml
GATE2_BRANCH=main
GATE2_REQUIRED_STEPS='Build Release with warnings as errors
Run offline alpha verification
Typecheck
Test
Validate config (no deploy)
Assert the relay has no decryption path
Assert sync vectors match their generator'
GATE2_OPTIONAL_STEPS=''
# ---------------------------------------------------------------------------

# ---- repository SETTINGS, added run 230 (2026-09-15) — §4d -----------------
# Run 228 recorded this as next intent, not as a blocker: "nothing in the firing routine
# asserts a repository SETTING. Every drift check compares FILE CONTENTS." That is the same
# shape as the gate blind spot §4b/§4c closed — an exposure with nothing watching it — and it
# is measurable from the same anonymous API, so it is closed here rather than carried again.
#
# IT IS ALSO THE GAP B-29 WAS FOUND IN, AND B-29 IS WHY THE POLARITY BELOW IS NOT OBVIOUS.
# `careerseeker-android/README.md:7` says "This repository is private, always." The live
# setting has read `private: false` since at least 2026-09-04. Asserting the README's value
# would therefore paint this section RED on every firing, forever, for a divergence the owner
# already knows about (escalated run 203, C-203-1) and alone can decide. A check that is red
# every run is not a check; it is the green-tick problem inverted, and it would break the
# empty-firing rule at run 118 by making every firing look like a finding.
#
# SO THIS ASSERTS THE RECORDED BASELINE, NOT THE DOCUMENTED IDEAL -- exactly what §4 does for
# the two mains. It answers one question only: HAS THE SETTING MOVED SINCE THE HOUSE LAST
# LOOKED? A flip to private is the owner ANSWERING B-29, and that is a change worth the full
# records. Staying public is the known open divergence, and it is printed every run as a
# standing note that never goes quiet -- never as a pass of README.md:7, which it is not.
#
# The engine expectation is genuinely public and is load-bearing beyond B-29: §4b and §4c
# read the Actions API with NO TOKEN, so an engine repo that went private takes both gate
# checks blind with it. README.md's repo-split table gives the reason the engine is public
# (the alpha ZIP is served from it, and the relay's whole audit claim is that anyone can read
# it) -- so here the baseline and the documented ideal agree, and a flip is unambiguously a
# finding.
#
# careerseeker-ios IS DELIBERATELY NOT CHECKED, and its absence is documented rather than
# silent. Run 203 measured it alongside the android repo, but this session's GitHub scope is
# the two repos below; a third would be read out of scope. B-29 covers both, and the ios half
# stays a MANUAL §6 query for a session whose scope includes it.
#
# RUN 231 WIDENED THIS SECTION FROM ONE FIELD TO FOUR, and found that run 230's own account of
# what was reachable was half wrong. 230 recorded as next intent: "`archived`, `default_branch`
# and branch protection are the same shape of silent event -- no commit, no file, no guard.
# `archived` is the cheapest; branch protection ... needs a token and would go `??`-blind here."
# The first clause is right and is closed below. THE SECOND IS THE ASSUMPTION THIS PROGRAM KEEPS
# MAKING ABOUT ITS OWN REACH, for the third time (run 221 on `dotnet ABSENT`, run 227 on the
# Actions API, this one). `/repos/O/R/branches/main/protection` IS 403 without a token. But
# `/repos/O/R/branches/main` answers 200 ANONYMOUSLY and carries `.protected` plus
# `.protection.required_status_checks.enforcement_level` -- which is the half that matters
# (C-231-1). Measured before built, per run 228's rule.
#
# WHY EACH FIELD IS HERE. All four change with NO COMMIT BEHIND THEM, which is the blind class
# §4d exists for:
#   private         B-29's field. Run 230's.
#   archived        an archived repo takes every push read-only. A firing would push, fail, and
#                   the failure would look like the transport flaking, not like a decision.
#   default_branch  LOAD-BEARING FOR THIS SCRIPT. §4 pins both mains by SHA, §4c reads the
#                   engine gate on `main`. Repoint the default and those baselines keep
#                   comparing a branch that is no longer the one anybody lands on -- green,
#                   confidently, about the wrong ref.
#   protected       the gates §4b/§4c watch are only worth what merging requires of them.
#
# THE POLARITY IS THE SAME ONE B-29 FORCED, and for `protected` it is NOT cosmetic. Both mains
# measure `protected: false` with ZERO required status checks (C-231-2). Asserting the ideal --
# that the gate enforcing $ExpectedOfflineTotal, the doc/verifier drift trap and the engine-side
# half of the shared-vector guard should be REQUIRED to pass -- would paint this red on every
# firing, forever, for a setting only the owner can change. So it asserts the RECORDED state and
# prints the exposure as a standing note every run, exactly as B-29's is printed. That finding
# is filed as B-32; it is not flipped here.
SETTING_REPOS='android:ShivaClaw:careerseeker-android:false:false:main:false
engine:ShivaClaw:careerseeker:false:false:main:false'
#                label:owner:repo:private:archived:default_branch:protected
# ---------------------------------------------------------------------------

FAIL=0
GATE_BLIND=0
SETTING_BLIND=0
note()  { printf '  %s\n' "$*"; }
head2() { printf '\n== %s\n' "$*"; }
bad()   { printf '  !! %s\n' "$*"; FAIL=1; }
# warn() is loud but does NOT set FAIL. Added run 227 for exactly one situation: a check that
# could not be PERFORMED, as against one that was performed and failed. Failing the verdict on
# an unreachable API would make every firing red the moment the repo goes private (B-29) or the
# egress policy tightens -- the same signal-destroying staleness the baseline block above warns
# about. Silence would be worse: B-31 IS a silent absence. So it prints ?? here and the VERDICT
# repeats it, because a firing that is blind must know it is blind.
warn()  { printf '  ?? %s\n' "$*"; }

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

# --- 4b/4c. the gates: did CI EXECUTE, or merely report? (B-31) -------------
#
# RUN 228 MADE THIS A FUNCTION AND CALLED IT TWICE. Run 227 wrote it inline for the android
# repo and left the engine repo unwatched as declared next intent. Parameterising was the
# honest way to take that up: a SECOND hand-written copy of a detector is how the two halves
# drift apart, and this house already has a name for that failure -- it is the doc/verifier
# trap in the engine's CLAUDE.md, applied to a script instead of a doc.
#
# The refactor is behaviour-preserving for §4b BY TEST, not by inspection: the known-bad
# replay that proved the detector at run 227 (C-227-3) is re-run against this version and
# must still go red. A refactor of a detector that is only ever exercised on green input is
# an untested detector, whatever the diff looks like.
#
# ONE REAL BEHAVIOUR CHANGE, and the engine gate is why. The old matcher took the FIRST
# occurrence of a step name and stopped (`awk ... {print $1; exit}`). That is safe for one
# job; the engine workflow has TWO, so a name appearing in both could report the first job's
# conclusion while the second one's copy of that step was skipped or red. This version reads
# EVERY occurrence and takes the worst. No android step name repeats, so §4b is unaffected.

gate_fetch() {  # $1 url -> body on stdout, HTTP code as exit-carrying last line
  curl -sS --max-time 25 -w '\n%{http_code}' "$1" 2>/dev/null
}

# gate_check <label> <owner> <repo> <workflow> <branch> <tip-sha|""> <pinned-run|"">
#            <required-steps (newline-separated)> <optional-steps|""> <signature-note>
gate_check() {
  local gate_label_hdr=$1 GATE_OWNER=$2 GATE_REPO=$3 GATE_WORKFLOW=$4
  local gate_branch=$5 gate_tip=$6 gate_pin=$7
  local GATE_REQUIRED_STEPS=$8 GATE_OPTIONAL_STEPS=$9 gate_signature=${10}
  local gate_api gate_manual

  gate_api="https://api.github.com/repos/$GATE_OWNER/$GATE_REPO"
  gate_manual="actions_list method=list_workflow_runs owner=$GATE_OWNER repo=$GATE_REPO \\
    resource_id=$GATE_WORKFLOW workflow_runs_filter='{\"branch\":\"$gate_branch\"}'"

if ! command -v curl >/dev/null 2>&1 || ! command -v python3 >/dev/null 2>&1; then
  warn "curl or python3 is ABSENT — this check needs both. Run it by hand:"
  note "  $gate_manual"
  GATE_BLIND=1
else
  gate_raw=$(gate_fetch "$gate_api/actions/workflows/$GATE_WORKFLOW/runs?branch=$gate_branch&per_page=10")
  gate_code=$(printf '%s' "$gate_raw" | tail -1)
  gate_body=$(printf '%s' "$gate_raw" | sed '$d')
  if [ "$gate_code" != 200 ]; then
    warn "GitHub API answered HTTP ${gate_code:-<none>} — the gate was NOT checked this firing."
    case "$gate_code" in
      404) note "404 on a repo that exists means it is no longer publicly readable — B-29's" ;
           note "visibility decision landing. That is an ANSWER, not a bug here; record it." ;;
      403) note "403 is rate limiting or an egress denial (B-7's neighbourhood). Note that a" ;
           note "repo outside this sandbox's scope also answers 403 at the proxy, not 404." ;;
    esac
    note "This probe reads the API ANONYMOUSLY and holds no token, so none of these is"
    note "recoverable here. Re-run the query through the session's GitHub MCP path:"
    note "  $gate_manual"
    GATE_BLIND=1
  else
    # The caller may pin one run id instead of taking the branch's latest. It exists so this
    # check is FALSIFIABLE: a green section proves nothing about a detector until someone runs it
    # against a known-bad input. Replay the dead gate B-31 was filed on and watch §4b go red:
    #   RUNZERO_GATE_RUN=34896487955 scripts/run-zero.sh ../careerseeker   # run 402, C-227-3
    # and RUNZERO_ENGINE_GATE_RUN does the same for §4c.
    if [ -n "$gate_pin" ]; then
      note "REPLAY — run $gate_pin pinned; the branch's latest is ignored."
      gate_body=$(gate_fetch "$gate_api/actions/runs/$gate_pin" | sed '$d')
      gate_body="{\"workflow_runs\":[$gate_body]}"
    fi
    gate_out=$(printf '%s' "$gate_body" | python3 -c '
import json,sys
d=json.load(sys.stdin)
runs=[r for r in d.get("workflow_runs",[]) if r.get("status")=="completed"]
pend=[r for r in d.get("workflow_runs",[]) if r.get("status")!="completed"]
for r in pend[:1]:
    print("PENDING\t%s\t%s\t%s"%(r["run_number"],r["head_sha"],r["status"]))
if not runs:
    print("NORUNS"); sys.exit(0)
r=runs[0]
print("RUN\t%s\t%s\t%s\t%s\t%s"%(r["run_number"],r["id"],r["head_sha"],r["conclusion"],r["created_at"]))
')
    gate_run_id=$(printf '%s\n' "$gate_out" | awk -F'\t' '$1=="RUN"{print $3}')
    gate_num=$(printf   '%s\n' "$gate_out" | awk -F'\t' '$1=="RUN"{print $2}')
    gate_sha=$(printf   '%s\n' "$gate_out" | awk -F'\t' '$1=="RUN"{print $4}')
    gate_conc=$(printf  '%s\n' "$gate_out" | awk -F'\t' '$1=="RUN"{print $5}')
    gate_when=$(printf  '%s\n' "$gate_out" | awk -F'\t' '$1=="RUN"{print $6}')
    gate_pend=$(printf  '%s\n' "$gate_out" | awk -F'\t' '$1=="PENDING"{print $2" ("$4") on "substr($3,1,7)}')

    if [ -z "$gate_run_id" ]; then
      warn "no COMPLETED run of $GATE_WORKFLOW on $gate_branch — the gate has never reported here."
      GATE_BLIND=1
    else
      gate_label="latest completed"
      [ -n "$gate_pin" ] && gate_label="PINNED (replay) "
      note "$gate_label: run $gate_num  ${gate_sha:0:7}  $gate_conc  ($gate_when)"
      [ -n "$gate_pend" ] && note "in flight       : run $gate_pend"
      if [ -n "$gate_tip" ] && [ "$gate_sha" != "$gate_tip" ]; then
        note "branch tip is ${gate_tip:0:7} — this run does NOT cover it (a push not yet gated,"
        note "which is the ordinary state mid-firing; the NEXT firing reads the run for it)."
      fi
      # The step array is the whole point: `skipped` is not `passed`, and a job that dies in
      # its toolchain reports BOTH as one red X that B-25 has trained this house to ignore.
      gate_jraw=$(gate_fetch "$gate_api/actions/runs/$gate_run_id/jobs")
      gate_jcode=$(printf '%s' "$gate_jraw" | tail -1)
      if [ "$gate_jcode" != 200 ]; then
        warn "step array unreadable (HTTP $gate_jcode) — conclusion above is ALL that was checked."
        GATE_BLIND=1
      else
        gate_steps=$(printf '%s' "$gate_jraw" | sed '$d' | python3 -c '
import json,sys
d=json.load(sys.stdin)
for j in d.get("jobs",[]):
    for s in j.get("steps",[]):
        print("%s\t%s"%(s.get("conclusion"),s.get("name")))
')
        gate_missing=0 gate_skipped=0 gate_failed=0 gate_want_n=0
        gate_ran_ok='' gate_notrun=''
        while IFS= read -r want; do
          [ -z "$want" ] && continue
          gate_want_n=$((gate_want_n + 1))
          # EVERY occurrence, worst-first — see the run-228 note above. A step name that
          # appears in two jobs must not be cleared by whichever job happens to be listed
          # first; `skipped` in either copy is still a check that did not execute.
          got=$(printf '%s\n' "$gate_steps" | awk -F'\t' -v n="$want" '
            $2==n { c=$1
                    if (c=="failure"||c=="cancelled"||c=="timed_out") { worst=c; exit }
                    if (c=="skipped") worst="skipped"
                    else if (worst=="") worst="success" }
            END   { print worst }')
          case "$got" in
            success)  gate_ran_ok="$gate_ran_ok  passed       $want
" ;;
            skipped)  bad "gate step NOT EXECUTED (skipped): $want"; gate_skipped=1
                      gate_notrun="$gate_notrun  NOT EXECUTED $want
" ;;
            "")       bad "gate step ABSENT from the run: $want"; gate_missing=1
                      gate_notrun="$gate_notrun  ABSENT       $want
" ;;
            *)        bad "gate step $got: $want"; gate_failed=1
                      gate_ran_ok="$gate_ran_ok  FAILED       $want
" ;;
          esac
        done <<EOF
$GATE_REQUIRED_STEPS
EOF
        # ORDER MATTERS HERE, AND RUN 239 FOUND OUT THE EXPENSIVE WAY (C-239-1).
        #
        # This chain used to test `gate_skipped` FIRST, which made the `gate_failed` arm below
        # UNREACHABLE for every failure except one in the LAST required step. CI runs all eight
        # in ONE sequential job, so a step that fails leaves every later step `skipped` as its
        # CONSEQUENCE. The old order read those consequential skips as B-31 -- a gate that never
        # executed -- and printed the signature's claim that "the vendored-vector drift guard is
        # among the eight, so cross-repo drift is UNPROTECTED".
        #
        # On run 418 (head 08a8168) that claim was FALSE. The drift guard is step 8, the failing
        # `:app` test is step 10, so the guard had EXECUTED AND PASSED before anything skipped.
        # B-22's intermittent `ComposeTimeoutException` is by far the commonest red here -- 17 of
        # the 22 failures in run numbers 222..418 (C-239-2) -- so the section was mis-narrating
        # its single most frequent input, and overstating the program's exposure while doing it.
        # That is the same defect class as C-227-1 and C-238-2: the probe asserting about a check
        # it did not look at. A failure that the gate CAUGHT is the gate working.
        #
        # So: a failed REQUIRED step is decided first and reported as itself, with the per-step
        # ledger printed so the reader can see which guards did run rather than take a sentence's
        # word for it. B-31's signature is reserved for what it was filed on -- skips or absences
        # with NO required step failing, which is what a dead toolchain actually looks like (run
        # 402 fails at "Set up Android SDK", a step that is not required, and all eight skip).
        if [ $gate_failed = 1 ]; then
          note ""
          note "A REQUIRED CHECK FAILED. That is NOT B-31 and NOT B-25 — the toolchain is alive,"
          note "the run REACHED this check, and the check said no. It is a better problem than"
          note "either: read the job log before touching anything."
          note ""
          note "SKIPS BELOW A FAILURE ARE ITS CONSEQUENCE, NOT EVIDENCE OF A DEAD GATE. The eight"
          note "run in one sequential job, so everything after the failing step reports 'skipped'"
          note "whether or not the toolchain is healthy. Read this ledger, never the skip count:"
          printf '%s' "$gate_ran_ok$gate_notrun" | sed 's/^/  /'
          note ""
          note "Only the NOT EXECUTED / ABSENT rows above are unprotected this run. If the"
          note "vendored-vector drift guard is a 'passed' row, cross-repo drift IS guarded and"
          note "saying otherwise overstates the exposure (C-239-1)."
        elif [ $gate_skipped = 1 ] || [ $gate_missing = 1 ]; then
          note ""
          note "$gate_signature"
        elif [ -n "$GATE_OPTIONAL_STEPS" ]; then
          note "all $gate_want_n required checks EXECUTED and passed; '$GATE_OPTIONAL_STEPS' skipped by"
          note "design (B-25, workflow_dispatch). The gate is alive, not merely green."
        else
          note "all $gate_want_n required checks EXECUTED and passed, and this workflow has NO"
          note "skipped-by-design step, so any skip here would be a finding. Alive, not merely green."
        fi
      fi
    fi
  fi
fi
}

head2 "4b. The android gate — did CI EXECUTE on this branch, or only report? (B-31)"
a_branch=$(git -C "$ANDROID" rev-parse --abbrev-ref HEAD 2>/dev/null)
if [ -z "$a_branch" ] || [ "$a_branch" = HEAD ]; then
  a_branch=$GATE_BRANCH
  note "checkout is detached — falling back to the pinned branch $a_branch"
fi
gate_check "android" "$GATE_OWNER" "$GATE_REPO" "$GATE_WORKFLOW" \
  "$a_branch" "$(git -C "$ANDROID" rev-parse "$a_branch" 2>/dev/null)" \
  "${RUNZERO_GATE_RUN:-}" "$GATE_REQUIRED_STEPS" "$GATE_OPTIONAL_STEPS" \
  "THIS IS B-31's SIGNATURE, NOT B-25's. A skipped or absent check did not pass;
  the vendored-vector drift guard is among the eight, so cross-repo drift is
  UNPROTECTED while this holds. Do not file it as the APK-quota red X."

head2 "4c. The engine gate — is Verify-Alpha.ps1 still EXECUTING on main? (run 228)"
gate_check "engine" "$GATE2_OWNER" "$GATE2_REPO" "$GATE2_WORKFLOW" \
  "$GATE2_BRANCH" "$(git -C "$ENGINE" rev-parse "origin/$GATE2_BRANCH" 2>/dev/null)" \
  "${RUNZERO_ENGINE_GATE_RUN:-}" "$GATE2_REQUIRED_STEPS" "$GATE2_OPTIONAL_STEPS" \
  "B-31's SIGNATURE, IN THE ENGINE REPO. A skipped or absent step did not pass, and
  two of the seven are load-bearing beyond this repo: 'Run offline alpha verification'
  is where \$ExpectedOfflineTotal and the doc/verifier drift trap are enforced, and
  'Assert sync vectors match their generator' is the engine-side half of the SAME
  cross-repo vector guard §4b watches. Do not read a green run conclusion over this."

# setting_check <label> <owner> <repo> <exp-private> <exp-archived> <exp-default-branch>
#                <exp-protected>      (each true|false, except the branch name)
#
# Same contract as gate_check: it READS, it can move the verdict, and it goes loudly ?? rather
# than quietly green when it cannot perform the check. RUNZERO_SETTING_EXPECT overrides the
# expectation for every repo at once -- it exists so the comparator can be proven to FIRE on
# live input, the way RUNZERO_GATE_RUN proves §4b/§4c (C-230-2). It is a test hook, not a
# configuration knob: the baselines live in SETTING_REPOS above.
#
# RUN 231 added one hook per new field, same contract, same reason: a comparator exercised only
# on green input is untested (run 228's rule). RUNZERO_ARCHIVED_EXPECT, RUNZERO_BRANCH_EXPECT
# and RUNZERO_PROTECTED_EXPECT each override their field for every repo at once (C-231-3).
setting_check() {
  local s_label=$1 s_owner=$2 s_repo=$3 s_expect=${RUNZERO_SETTING_EXPECT:-$4}
  local s_exp_arch=${RUNZERO_ARCHIVED_EXPECT:-$5}
  local s_exp_branch=${RUNZERO_BRANCH_EXPECT:-$6}
  local s_exp_prot=${RUNZERO_PROTECTED_EXPECT:-$7}
  local s_api s_raw s_code s_body s_private s_vis s_updated s_arch s_disabled s_branch

  s_api="https://api.github.com/repos/$s_owner/$s_repo"
  if ! command -v curl >/dev/null 2>&1 || ! command -v python3 >/dev/null 2>&1; then
    warn "curl or python3 is ABSENT — $s_label setting NOT checked. Run it by hand:"
    note "  get_repository owner=$s_owner repo=$s_repo   -> .private / .visibility"
    SETTING_BLIND=1
    return
  fi

  s_raw=$(curl -sS --max-time 25 -w '\n%{http_code}' "$s_api" 2>/dev/null)
  s_code=$(printf '%s' "$s_raw" | tail -1)
  s_body=$(printf '%s' "$s_raw" | sed '$d')

  if [ "$s_code" = 404 ]; then
    # 404 on a repo known to exist means it is no longer publicly readable. gate_check's own
    # 404 branch says the same thing: out-of-scope repos answer 403 at the proxy, not 404.
    if [ "$s_expect" = true ]; then
      note "$s_label: HTTP 404 unauthenticated — private, which is the expected baseline."
    else
      bad "$s_label: HTTP 404 unauthenticated — the repo went PRIVATE. Baseline said public."
      note "  This is B-29 ANSWERED, not a bug here. Record the answer, re-point the baseline"
      note "  in SETTING_REPOS, and note that §4b/§4c read the Actions API with NO TOKEN — if"
      note "  this is the ENGINE repo, both gate checks just went blind with it."
    fi
    return
  fi
  if [ "$s_code" != 200 ]; then
    warn "$s_label: GitHub API answered HTTP ${s_code:-<none>} — the setting was NOT checked."
    note "  403 is rate limiting or an egress denial (B-7's neighbourhood), not an answer."
    SETTING_BLIND=1
    return
  fi

  s_private=$(printf '%s' "$s_body" | python3 -c \
    'import json,sys; print(str(json.load(sys.stdin).get("private")).lower())' 2>/dev/null)
  s_vis=$(printf '%s' "$s_body" | python3 -c \
    'import json,sys; print(json.load(sys.stdin).get("visibility"))' 2>/dev/null)
  s_updated=$(printf '%s' "$s_body" | python3 -c \
    'import json,sys; print(json.load(sys.stdin).get("updated_at"))' 2>/dev/null)
  s_arch=$(printf '%s' "$s_body" | python3 -c \
    'import json,sys; print(str(json.load(sys.stdin).get("archived")).lower())' 2>/dev/null)
  s_disabled=$(printf '%s' "$s_body" | python3 -c \
    'import json,sys; print(str(json.load(sys.stdin).get("disabled")).lower())' 2>/dev/null)
  s_branch=$(printf '%s' "$s_body" | python3 -c \
    'import json,sys; print(json.load(sys.stdin).get("default_branch"))' 2>/dev/null)

  if [ -z "$s_private" ]; then
    warn "$s_label: the API answered 200 but .private did not parse — NOT checked."
    SETTING_BLIND=1
    return
  fi

  printf '  %-8s private: %-5s visibility: %-8s updated_at: %s\n' \
    "$s_label" "$s_private" "$s_vis" "$s_updated"
  printf '  %-8s archived: %-5s disabled: %-5s default_branch: %s\n' \
    "" "$s_arch" "$s_disabled" "$s_branch"

  if [ "$s_private" = "$s_expect" ]; then
    note "  unmoved against the recorded baseline (private: $s_expect)."
  else
    bad "$s_label: private is '$s_private', the recorded baseline is '$s_expect'. THE SETTING MOVED."
    note "  A repository setting changed with no commit behind it, which is precisely why"
    note "  file-content drift checks could never see this class. Re-derive B-29 before"
    note "  recording NOTHING MOVED, and do NOT flip it back: visibility is the owner's call."
  fi

  # archived, added run 231. An archived repo is READ-ONLY: every push fails, and the failure
  # surfaces as a transport error, not as a decision anybody made. `disabled` is read and
  # printed alongside it but is NOT asserted -- it has no recorded baseline and this program
  # has never seen it true, so asserting it would be pinning a value nobody measured moving.
  if [ -z "$s_arch" ]; then
    warn "$s_label: .archived did not parse — NOT checked."
    SETTING_BLIND=1
  elif [ "$s_arch" = "$s_exp_arch" ]; then
    note "  unmoved against the recorded baseline (archived: $s_exp_arch)."
  else
    bad "$s_label: archived is '$s_arch', the recorded baseline is '$s_exp_arch'. THE SETTING MOVED."
    note "  An ARCHIVED repository is read-only. If this is true, every push this routine makes"
    note "  fails, and it fails looking like a network problem rather than an owner decision."
  fi

  # default_branch, added run 231. This one is load-bearing for the script reading it: §4 pins
  # both mains by SHA and §4c reads the engine gate on `main`. Repoint the default and those
  # checks keep comparing a ref nobody lands on any more, reporting green about the wrong branch.
  if [ -z "$s_branch" ]; then
    warn "$s_label: .default_branch did not parse — NOT checked."
    SETTING_BLIND=1
  elif [ "$s_branch" = "$s_exp_branch" ]; then
    note "  unmoved against the recorded baseline (default_branch: $s_exp_branch)."
  else
    bad "$s_label: default_branch is '$s_branch', baseline is '$s_exp_branch'. THE SETTING MOVED."
    note "  §4's pinned mains and §4c's gate read both name the OLD default. They will keep"
    note "  reporting 'unmoved' about a branch that is no longer the one anybody lands on."
  fi

  protection_check "$s_label" "$s_owner" "$s_repo" "$s_branch" "$s_exp_prot"
}

# protection_check <label> <owner> <repo> <branch> <expected-protected: true|false>
#
# RUN 231, AND IT EXISTS BECAUSE RUN 230 GUESSED WRONG ABOUT ITS OWN REACH. 230 wrote that
# branch protection "needs a token and would go ??-blind here". Half true, and the wrong half
# was never measured: the dedicated endpoint
#   GET /repos/O/R/branches/B/protection   -> 403 "Resource not accessible by integration"
# but the branch object itself
#   GET /repos/O/R/branches/B              -> 200, anonymously
# carries `.protected` and `.protection.required_status_checks.enforcement_level` (C-231-1).
# That is the third time this program has recorded a limit it never tested -- run 221 on
# `dotnet ABSENT`, run 227 on the Actions API, run 230 here. Section 6's rule generalises:
# before believing any "this sandbox cannot", check whether it was measured or assumed.
#
# WHAT IT STILL CANNOT SEE, stated so nothing here is overread: the anonymous branch object
# gives the BOOLEAN and the enforcement level. It does NOT give required reviewers, dismissal
# rules, force-push or deletion settings, or the required-checks CONTEXT LIST when protection
# is on. So this can prove protection is OFF, and can detect it being switched on; it cannot
# audit the contents of a protection rule. That needs a token and stays out of reach (B-32).
protection_check() {
  local p_label=$1 p_owner=$2 p_repo=$3 p_branch=$4 p_expect=$5
  local p_raw p_code p_body p_protected p_level

  [ -n "$p_branch" ] || return

  p_raw=$(curl -sS --max-time 25 -w '\n%{http_code}' \
    "https://api.github.com/repos/$p_owner/$p_repo/branches/$p_branch" 2>/dev/null)
  p_code=$(printf '%s' "$p_raw" | tail -1)
  p_body=$(printf '%s' "$p_raw" | sed '$d')

  if [ "$p_code" != 200 ]; then
    warn "$p_label: branch object answered HTTP ${p_code:-<none>} — protection NOT checked."
    SETTING_BLIND=1
    return
  fi

  p_protected=$(printf '%s' "$p_body" | python3 -c \
    'import json,sys; print(str(json.load(sys.stdin).get("protected")).lower())' 2>/dev/null)
  p_level=$(printf '%s' "$p_body" | python3 -c \
    'import json,sys; d=json.load(sys.stdin).get("protection") or {}
print((d.get("required_status_checks") or {}).get("enforcement_level"))' 2>/dev/null)

  if [ -z "$p_protected" ]; then
    warn "$p_label: .protected did not parse — protection NOT checked."
    SETTING_BLIND=1
    return
  fi

  printf '  %-8s %s protected: %-5s required_status_checks: %s\n' \
    "" "$p_branch" "$p_protected" "${p_level:-<none>}"

  if [ "$p_protected" = "$p_expect" ]; then
    note "  unmoved against the recorded baseline (protected: $p_expect)."
  else
    bad "$p_label: $p_branch protected is '$p_protected', baseline is '$p_expect'. THE SETTING MOVED."
    note "  Protection changing is worth the full records in EITHER direction: switched on"
    note "  answers B-32, switched off removes a merge requirement nothing else here watches."
  fi
}

head2 "4d. Repository SETTINGS — the class no file-content check can see (run 230, widened 231)"
while IFS=: read -r s_label s_owner s_repo s_expect s_exp_arch s_exp_branch s_exp_prot; do
  [ -n "$s_label" ] || continue
  setting_check "$s_label" "$s_owner" "$s_repo" "$s_expect" \
                "$s_exp_arch" "$s_exp_branch" "$s_exp_prot"
done <<EOF
$SETTING_REPOS
EOF
if [ -n "${RUNZERO_SETTING_EXPECT:-}" ]; then
  note ""
  note "RUNZERO_SETTING_EXPECT=$RUNZERO_SETTING_EXPECT is set — the baselines above were"
  note "OVERRIDDEN. This is the falsifiability hook, not a real reading. Unset it."
fi
note ""
note "B-29 IS OPEN AND THIS SECTION DOES NOT CLOSE IT. 'unmoved' above means the setting is"
note "where the house last recorded it — it is NOT a pass of careerseeker-android/README.md:7,"
note "which says 'This repository is private, always.' That sentence and a live 'private:"
note "false' still contradict each other, the owner was told at run 203 (C-203-1), and only he"
note "decides which side gives way. What this section adds is that if he DOES decide, or if"
note "anything else moves a setting, the next firing finds out instead of reporting green."
note ""
note "B-32 IS OPEN TOO, AND 'protected: false' ABOVE IS THE RECORDED STATE, NOT AN ENDORSEMENT."
note "NEITHER main is protected and NEITHER has a required status check. So the two gates §4b"
note "and §4c read — the ones enforcing \$ExpectedOfflineTotal, the doc/verifier drift trap, and"
note "the engine-side half of the shared-vector guard — are ADVISORY. Nothing requires them to"
note "be green before a commit lands on either main. That is not a contradiction the way B-29"
note "is; it is a gap between what this program's records treat as load-bearing and what the"
note "repositories actually enforce. Like B-29 it is one setting, and like B-29 an agent is"
note "not the one to flip it. See BLOCKED.md B-32."

# --- 5. toolchain, so nothing is claimed that could not have run ------------
head2 "5. Toolchain — stated so no claim can be misread"
for t in dotnet pwsh sdkmanager avdmanager emulator adb gh node git java gradle; do
  printf '  %-12s %s\n' "$t" "$(command -v "$t" >/dev/null 2>&1 && echo PRESENT || echo ABSENT)"
done
printf '  %-12s %s\n' "ANDROID_HOME" "${ANDROID_HOME:-UNSET}"

# JDK 17, added run 240 (2026-09-17) -- C-240-1.
#
# 'java PRESENT' above is a command -v check and says NOTHING about which JDK.
# :core pins jvmToolchain(17) (core/build.gradle.kts:9), Gradle cannot
# auto-provision one (api.foojay.io is denied with dl.google.com, B-7), so the
# ONE gate task this sandbox can run needs a 17 that 'java PRESENT' does not
# imply. This container ships JDK 21 ONLY: java -version reads 21.0.10 and
# /usr/lib/jvm held 21 alone, so core-probe.sh exited 1 before Gradle started
# while §5 went on printing 'java PRESENT' and asserting the probe runs.
#
# Detection is character-for-character core-probe.sh's own guard, so the two
# CANNOT disagree. If you change one, change both.
CORE_JVM_DIR="${RUNZERO_JVM_DIR:-/usr/lib/jvm}"
if ls -d "$CORE_JVM_DIR"/*17* >/dev/null 2>&1; then CORE_JDK=PRESENT; else CORE_JDK=ABSENT; fi
printf '  %-12s %s\n' "JDK17(:core)" "$CORE_JDK"
note ""
note "No gate is reachable from here: neither Verify-Alpha.ps1 nor the five-task"
if [ "$CORE_JDK" = PRESENT ]; then
  note "android command. scripts/core-probe.sh runs :core:test — ONE of those five,"
  note "and the JDK17 line above says it is RUNNABLE. Run it; do not report a gate."
else
  note "android command. AND scripts/core-probe.sh — the ONE of those five that"
  note "normally runs here — is NOT RUNNABLE THIS FIRING: JDK17(:core) is ABSENT,"
  note "so it exits 1 before Gradle starts. This is NOT a new blocker and NOT B-7."
  note "It is one apt away, and the probe prints the same fix when you run it:"
  note ""
  note "  apt-get update -qq && apt-get install -y --no-install-recommends \\"
  note "      openjdk-17-jdk-headless"
  note ""
  note "Measured run 240 (C-240-1): that install took ~10s and core-probe.sh then"
  note "reported 348 tests / 0 failed / 0 skipped / 22 classes, the same numbers as"
  note "the eleven recordings before it. Do NOT record 'the core lane is gone'."
fi
note ""
note "READ THE TWO ABSENT LINES ABOVE THE WAY §6 TELLS YOU TO READ 'gh ABSENT':"
note "they mean NOT PREINSTALLED, not unobtainable. Run 221 measured both (C-221-6):"
note ""
note "  dotnet  -> ABSENT but INSTALLABLE. dot.net and builds.dotnet.microsoft.com are"
note "             403 CONNECT-denied by the egress policy (B-7's neighbourhood), but"
note "             packages.microsoft.com answers 200, and the apt route works:"
note "               curl -sSL -o /tmp/ms.deb \\"
note "                 https://packages.microsoft.com/config/ubuntu/24.04/packages-microsoft-prod.deb"
note "               dpkg -i /tmp/ms.deb && apt-get update -qq && apt-get install -y dotnet-sdk-8.0"
note "             With it, ALL TEN offline harnesses run here: 803 passed, 0 failed,"
note "             and 803 + 13 Windows-only skips = 816 = \$ExpectedOfflineTotal (B-10)."
note "             Last VERIFIED (run 241, 2026-09-17, first-person on this image):"
note "             sdk 8.0.131 installed by the route above; dotnet build CareerSeeker.sln"
note "             -c Release -> 0 Warning(s) / 0 Error(s); the ten harnesses summed"
note "             28+217+57+16+28+36+35+45+6+335 = 803 passed / 0 failed, and the 13"
note "             skips are 6 FullDataDeletion + 7 DPAPI vault, both in EngineHarness."
note ""
note "             THAT STAMP IS THE POINT, NOT THE NUMBERS. Before run 241 this block"
note "             carried run 221's measurement with no date, so a reader could not tell"
note "             a one-run-old recording from a twenty-run-old one. §6's board paragraph"
note "             was wrong for sixteen firings in exactly that way (run 238), and §5's"
note "             JDK17 sentence went dark unnoticed in exactly that way (C-240-1). This"
note "             block was the third instance of the pattern and the last one unstamped."
note "             If you re-measure, MOVE THE STAMP. A recording presented as a live fact"
note "             is the defect, even when — as here — the numbers still hold."
note "  pwsh    -> ABSENT but INSTALLABLE from the PowerShell GitHub release tarball."
note "             It does NOT make Verify-Alpha.ps1 runnable (that needs Windows), but"
note "             it parses the script and executes individual assertions standalone."
note ""
note "What is genuinely out of reach here is WINDOWS — DPAPI, MSIX, publish — and the"
note "android SDK (B-7, B-4). The .NET half of the engine is NOT out of reach, and a"
note "firing that reads 'dotnet ABSENT' as 'nothing measurable' is leaving real"
note "verification on the table. Run 221 found a 201-assertion doc error that way."

# --- 6. what this cannot check ---------------------------------------------
head2 "6. MANUAL — two notification triggers THIS SCRIPT cannot answer (your session may)"
cat <<EOF
  Run 82's standing test notifies on: main moving (checked above, section 4),
  a PR merged or undrafted, the stored prompt changing, or a gate result.
  It does NOT fire on another firing, and NOT on another draft PR.

  ONE OF THE FOUR LEFT THIS SECTION AT RUN 227. 'A gate result' is now measured in
  §4b -- conclusion AND step array, anonymously, no gh and no token -- because the
  belief that bash could not reach the API was never tested (C-227-1). The two PR
  queries below are still MANUAL, but read that as 'not yet attempted from bash',
  which is what 'gh ABSENT' meant for the gate too, for 226 firings.

  READ SECTION 5's 'gh ABSENT' NARROWLY. It means the gh BINARY is not on PATH
  in this container — it does NOT mean your session has no GitHub API path.
  Run 99 answered both queries below through the GitHub MCP server, which needs
  no gh and which runs 96-98 did not try (C-99-1). A shell script cannot reach
  that server, so this section stays MANUAL and stays out of the verdict; but
  'this script cannot' is not 'you cannot'. TRY THE QUERIES BEFORE DEFERRING.

  Query these two, via the GitHub MCP server or any API path you have:

    list_pull_requests owner=ShivaClaw repo=careerseeker         state=all
    list_pull_requests owner=ShivaClaw repo=careerseeker-android state=all

  Last VERIFIED (run 238, 2026-09-17, MCP): THE CASCADE IS OVER AND THE QUEUE IS
  DRAINED. Engine ${BASE_ENGINE_DRAFTS} open (#60 harness-count drift, opened by a firing; #58 audit
  F01/F02, awaiting Codex; #26 SBOM, human queue Q07) -- all three draft. Android
  ${BASE_ANDROID_DRAFTS} open, all draft, and ZERO android PRs have EVER merged.
  ~${BASE_MERGED_SINCE_RUN95} engine PRs landed 2026-09-10/11 (#32..#59). The queue went 18 -> 3,
  which is the thing 150+ firings were waiting on: the LANDING problem is SOLVED.

  THE 3 ABOVE WAS 2 UNTIL RUN 238, AND WAS WRONG FOR SIXTEEN FIRINGS. #60 opened at run 221/222
  and every ledger line from 222 on says "board 3+6 open", but this paragraph -- stamped "Last
  VERIFIED (run 204, MCP)" -- kept saying 2 and "both draft". Recording a move in FIRINGS.md is
  NOT re-pinning the constant that narrates it. See the BASELINES block above.

  THIS SECTION IS AN UNQUOTED HEREDOC: it interpolates, so a backtick here EXECUTES. Run 238
  first wrote the paragraph above with backticks around that ledger field, and the probe printed
  "board: command not found" into its own §6. Caught and fixed inside the same run, and recorded
  because the next editor of this text will reach for backticks too. Use double quotes in §6.

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
# Run 227: printed BEFORE the verdict text, because it qualifies it. A firing that could not
# read the gate has not established that its own last push was checked, and B-31 is the record
# of what that costs. This does not set FAIL -- see warn()'s comment for why.
if [ "$GATE_BLIND" -ne 0 ]; then
  printf '  ?? %s\n' \
    "A GATE WAS NOT READ THIS FIRING (§4b android, §4c engine — the line above says" \
    "which). Whatever the verdict below says, you are" \
    "blind to B-31 on that side: a dead gate would look exactly like this run does. Answer its" \
    "query through the session's GitHub path before recording NOTHING MOVED."
fi
if [ "$SETTING_BLIND" -ne 0 ]; then
  printf '  ?? %s\n' \
    "A REPOSITORY SETTING WAS NOT READ THIS FIRING (§4d). You are blind to the one drift" \
    "class that leaves no commit behind, which is the class B-29 was found in. Answer" \
    "get_repository through the session's GitHub path before recording NOTHING MOVED."
fi
if [ "$FAIL" -eq 0 ]; then
  cat <<'EOF'
  NOTHING MOVED on every check this sandbox can run, and all six guards are green
  (citations, plan-rot against its pinned spent state, conflict markers, vectors,
  the gates' own step arrays since run 227 -- §4b android, and §4c the engine gate
  too, added at run 228 -- and, added at run 230 and widened at run 231, §4d's
  repository settings: private, archived, default_branch and branch protection).

  §4d IS 'UNMOVED', NOT 'CORRECT'. TWO open blockers live behind that word, and
  neither is a firing's to close:

    B-29  the android repo reads public while its own README.md:7 says private,
          always. The owner was told at run 203.
    B-32  NEITHER main is protected and NEITHER has a required status check, so
          the gates §4b/§4c read are advisory -- nothing requires them green
          before a commit lands. Found at run 231, and the reason it was never
          found earlier is that run 230 recorded branch protection as needing a
          token WITHOUT MEASURING IT. It does not: the branch object is public.

  The second is the reusable one. Three times now this program has written down a
  limit it never tested (run 221 `dotnet ABSENT`, run 227 the Actions API, run 230
  branch protection). Before believing any "this sandbox cannot", check whether it
  was measured or assumed.

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
