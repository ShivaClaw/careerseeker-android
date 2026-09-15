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

FAIL=0
GATE_BLIND=0
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
            success)  ;;
            skipped)  bad "gate step NOT EXECUTED (skipped): $want"; gate_skipped=1 ;;
            "")       bad "gate step ABSENT from the run: $want"; gate_missing=1 ;;
            *)        bad "gate step $got: $want"; gate_failed=1 ;;
          esac
        done <<EOF
$GATE_REQUIRED_STEPS
EOF
        if [ $gate_skipped = 1 ] || [ $gate_missing = 1 ]; then
          note ""
          note "$gate_signature"
        elif [ $gate_failed = 1 ]; then
          note ""
          note "The toolchain is fine and a REAL check is failing — a different, and better,"
          note "problem than B-31. Read the job log before touching anything."
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

# --- 5. toolchain, so nothing is claimed that could not have run ------------
head2 "5. Toolchain — stated so no claim can be misread"
for t in dotnet pwsh sdkmanager avdmanager emulator adb gh node git java gradle; do
  printf '  %-12s %s\n' "$t" "$(command -v "$t" >/dev/null 2>&1 && echo PRESENT || echo ABSENT)"
done
printf '  %-12s %s\n' "ANDROID_HOME" "${ANDROID_HOME:-UNSET}"
note ""
note "No gate is reachable from here: neither Verify-Alpha.ps1 nor the five-task"
note "android command. scripts/core-probe.sh runs :core:test — ONE of those five."
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
if [ "$FAIL" -eq 0 ]; then
  cat <<'EOF'
  NOTHING MOVED on every check this sandbox can run, and all five guards are green
  (citations, plan-rot against its pinned spent state, conflict markers, vectors,
  and -- since run 227 -- the gates' own step arrays: §4b android, and §4c the engine
  gate too, added at run 228).

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
