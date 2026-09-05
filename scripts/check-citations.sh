#!/usr/bin/env bash
#
# check-citations.sh — fail when a cited C-/B- id resolves to nothing.
#
# WHY THIS EXISTS
#
# These records sell exactly one property: every claim has a command that
# re-verifies it. The mechanism is a citation -- a run writes "(C-76-3)" in
# LOG.md, STATE.md or a PR body, and a reader is meant to be able to look
# C-76-3 up in AUDIT-REQUEST.md and re-run it. A citation pointing at nothing
# does not merely fail to help; it reads exactly like one that works, so it
# spends the reader's trust and gives nothing back.
#
# Nothing in either repository checked that. Run 75 (2026-08-21) proved the
# gap the expensive way: B-22, C-75-11 and C-75-12 were appended with a bare
# relative path after the shell's cwd had reset to /home/user, so all three
# landed OUTSIDE any repository while the pushed commit and the PR body
# already cited them as filed. It was caught by luck -- a later command in
# the same run happened to fail loudly with "fatal: not a git repository".
# A silent `cat >>` would have closed that iteration with green CI, a
# refreshed PR, and two citations pointing at nothing. C-75-13 filed the
# general form as the lane's strongest records-side successor candidate.
# This is it.
#
# WHAT IT DOES
#
#   definitions  headings in AUDIT-REQUEST.md and BLOCKED.md
#                  "### C-76-3 -- ..."   "## B-22 -- ..."
#   citations    every C-/B- id mentioned in prose across the record set
#   failure      a cited id with no definition, unless it is listed in
#                KNOWN_ABSENT below with a reason
#
# WHAT IT IS NOT
#
# It does NOT check that a citation is APT -- that C-76-3 says what the
# sentence citing it claims. That needs a reader. This checks only that the
# referent exists, which is the half a machine can do and the half that
# failed in run 75.
#
# It is also not a gate. It needs no Android SDK, no JBR, no .NET, no
# network -- grep and awk. That is deliberate: the hazard it guards lands in
# records commits, which are exactly the commits that skip heavy checks.
#
# THE PARSER, AND WHY IT LOOKS LIKE THIS
#
# C-75-13 predicted the whole difficulty would be range and ellipsis forms,
# and that a naive parser "will drown in false positives". Measured against
# the real corpus (698 distinct ids cited), the prediction was right in shape
# and wrong in size -- three false positives, each a distinct lesson:
#
#   1. RANGES are real and must be expanded: "C-CUR-1...13" cites all
#      thirteen. Only the ellipsis forms count -- the em-dash does NOT
#      introduce a range. "C-S4T-4 -- a blind relay could truncate" is an id
#      followed by prose, and reading that dash as a range invents a citation
#      of C-S4T-4..(some number in the sentence). Measured: 4 such em-dash
#      pairs in the corpus, all prose, zero ranges.
#
#   2. COMBINED HEADINGS define more than one id: AUDIT-REQUEST.md carries
#      "### C-RES-1 / C-RES-2 -- what each STOP actually contains". Reading
#      only the first id off a heading reports C-RES-2 -- cited five times --
#      as dangling. It is defined; the parser was wrong.
#
#   3. MILESTONE LABELS are not citations: LOG.md has "### S5.B-0 The
#      environment finding that decided the slice". A word-boundary match
#      finds "B-0" inside it and reports a blocker that was never claimed to
#      exist. An id must not be preceded by an alphanumeric or a dot.
#
# Findings 2 and 3 were parser defects found by running it, not by reading
# it, and both are pinned by the self-test below.
#
# USAGE
#
#   scripts/check-citations.sh            # report and exit 1 on dangling
#   scripts/check-citations.sh --list     # also print the resolved inventory
#   scripts/check-citations.sh --self-test  # prove the guard actually fires
#
set -uo pipefail

# Resolve the repository root from this script's own location, so the script
# works from any cwd. Run 75's incident was a bare relative path outliving
# its cd; a guard against that class must not contain one.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Documents that DEFINE ids (headings) and documents that CITE them (prose).
# AUDIT-REQUEST.md and BLOCKED.md are both -- they cite each other freely.
DEF_DOCS=("AUDIT-REQUEST.md" "BLOCKED.md")
CITE_DOCS=("LOG.md" "STATE.md" "BLOCKED.md" "AUDIT-REQUEST.md" "RETURN-DAY.md" \
           "docs/S-Ladder.md" "HANDOFF-Android-Alpha.md" "docs/Merge-Topology.md" \
           "docs/protocol-questions.md" "docs/CLAUDE-ANDROID-MISSION.md")

# Ids that are cited but deliberately do not exist. Each needs a reason, and
# the reason is the point: an unexplained entry here is how a guard rots into
# a rubber stamp.
#
#   B-11  Never filed, on purpose. An iteration investigated a CI stall,
#         reserved B-11 for it, then proved the stall was its own churn under
#         cancel-in-progress read through a cache -- "B-11 was never warranted
#         and is not filed" (BLOCKED.md). B-12's opening paragraph exists to
#         explain the hole to whoever reaches for the missing number. Both
#         mentions are that explanation, not a claim that B-11 exists.
KNOWN_ABSENT=("B-11")

MODE="report"
case "${1:-}" in
  --list)      MODE="list" ;;
  --self-test) MODE="selftest" ;;
  "")          ;;
  *) echo "unknown argument: $1" >&2; exit 2 ;;
esac

# ---------------------------------------------------------------------------
# extract_definitions <root>  -> one id per line
#
# All ids on a heading line, not just the first (lesson 2 above).
# ---------------------------------------------------------------------------
extract_definitions() {
  local root="$1" doc
  for doc in "${DEF_DOCS[@]}"; do
    [ -f "$root/$doc" ] || continue
    # Fences are skipped on THIS side too, and the reason is the opposite of the
    # citation side's. There, a fenced id is a harmless fixture. Here, a
    # heading-shaped line inside a fence -- "### B-23 -- a blocker" quoted in an
    # example -- would register as a DEFINITION, and a phantom definition makes a
    # genuinely dangling citation look resolved. That failure is SILENT, which is
    # strictly worse than the noisy one. Zero such lines exist in the corpus today
    # (C-77-12); this keeps it that way as the records grow.
    # NOTE the heading pattern is spelled out rather than written /^#{2,4} /.
    # mawk -- the default awk on Ubuntu, including this repo's runner image --
    # does NOT support interval quantifiers, and silently matches NOTHING rather
    # than erroring. Written the short way, this extractor returned 27
    # definitions instead of 707 and the whole corpus read as dangling (C-77-12).
    # grep -E supports intervals and awk may not; do not "simplify" this back.
    awk '
      /^[ \t]*(```|~~~)/ { infence = !infence; next }
      infence { next }
      /^(##|###|####) / { print }
    ' "$root/$doc" | grep -ohE '\b(C-[A-Za-z0-9]+-[0-9]+|B-[0-9]+)\b'
  done | sort -u
}

# ---------------------------------------------------------------------------
# extract_citations <root>  -> "id<TAB>file:line" per occurrence
#
# Handles, in this order per line:
#   ranges        C-CUR-1...13 / C-STOP-1..5   (ellipsis forms ONLY)
#   continuations C-A2-1/-2/-3                 (abbreviated siblings)
#   atoms         C-76-3, B-22
# and rejects any match preceded by [A-Za-z0-9.] (lesson 3 above).
# ---------------------------------------------------------------------------
extract_citations() {
  local root="$1" doc
  for doc in "${CITE_DOCS[@]}"; do
    [ -f "$root/$doc" ] || continue
    awk -v FNAME="$doc" '
      # FENCED CODE BLOCKS ARE NOT CITATIONS.
      #
      # AUDIT-REQUEST.md is, by design, mostly commands. A command is a FIXTURE,
      # not a claim: the re-verification block for this very check contains
      #   printf "Filed this run as B-23 (C-77-4)" >> LOG.md
      # to demonstrate that the guard catches an unfiled id. Reading that as a
      # citation of B-23 makes the document that documents the guard fail the
      # guard -- which is exactly what happened the first time this ran against
      # its own records (C-77-11). A citation is a claim made in PROSE.
      /^[ \t]*(```|~~~)/ { infence = !infence; next }
      infence { next }
      {
        line = $0
        delete seen

        # -- ranges: FAM-A<ellipsis>B  expands to FAM-A .. FAM-B
        tmp = line
        while (match(tmp, /(C-[A-Za-z0-9]+)-[0-9]+ *(\.\.\.|\.\.|\xe2\x80\xa6) *-?[0-9]+/)) {
          hit = substr(tmp, RSTART, RLENGTH)
          pre = (RSTART > 1) ? substr(tmp, RSTART - 1, 1) : " "
          tmp = substr(tmp, RSTART + RLENGTH)
          if (pre ~ /[A-Za-z0-9.]/) continue
          fam = hit; sub(/-[0-9]+ *(\.\.\.|\.\.|\xe2\x80\xa6).*$/, "", fam)
          lo = hit; sub(/^C-[A-Za-z0-9]+-/, "", lo); sub(/ *(\.\.\.|\.\.|\xe2\x80\xa6).*$/, "", lo)
          hi = hit; sub(/^.*(\.\.\.|\.\.|\xe2\x80\xa6) *-?/, "", hi)
          lo += 0; hi += 0
          # A sane range only. A wild upper bound means the match ran into
          # prose, and inventing 400 citations from it would be the drowning
          # C-75-13 warned about.
          if (hi >= lo && hi - lo <= 60) {
            for (n = lo; n <= hi; n++) seen[fam "-" n] = 1
          }
        }

        # -- continuations: FAM-A/-B/-C
        tmp = line
        while (match(tmp, /(C-[A-Za-z0-9]+)-[0-9]+( *\/ *-[0-9]+)+/)) {
          hit = substr(tmp, RSTART, RLENGTH)
          pre = (RSTART > 1) ? substr(tmp, RSTART - 1, 1) : " "
          tmp = substr(tmp, RSTART + RLENGTH)
          if (pre ~ /[A-Za-z0-9.]/) continue
          fam = hit; sub(/-[0-9]+( *\/ *-[0-9]+)+$/, "", fam)
          rest = hit; sub(/^C-[A-Za-z0-9]+-/, "", rest)
          n = split(rest, parts, / *\/ *-/)
          for (i = 1; i <= n; i++) { p = parts[i] + 0; seen[fam "-" p] = 1 }
        }

        # -- atoms
        tmp = line
        while (match(tmp, /(C-[A-Za-z0-9]+-[0-9]+|B-[0-9]+)/)) {
          hit = substr(tmp, RSTART, RLENGTH)
          pre = (RSTART > 1) ? substr(tmp, RSTART - 1, 1) : " "
          post_i = RSTART + RLENGTH
          post = (post_i <= length(tmp)) ? substr(tmp, post_i, 1) : " "
          tmp = substr(tmp, post_i)
          if (pre ~ /[A-Za-z0-9.]/) continue      # S5.B-0 is a milestone label
          if (post ~ /[0-9A-Za-z]/) continue      # ran into a longer token
          seen[hit] = 1
        }

        for (id in seen) printf "%s\t%s:%d\n", id, FNAME, FNR
      }
    ' "$root/$doc"
  done
}

# ---------------------------------------------------------------------------
# run_check <root> -> prints report; returns 0 clean, 1 dangling
# ---------------------------------------------------------------------------
run_check() {
  local root="$1" quiet="${2:-}"
  local defs cites dangling
  defs="$(extract_definitions "$root")"
  cites="$(extract_citations "$root")"

  local n_def n_cite
  n_def=$(printf '%s\n' "$defs" | grep -c . || true)
  n_cite=$(printf '%s\n' "$cites" | cut -f1 | sort -u | grep -c . || true)

  # cited ids with no definition, minus the documented-absent list
  local absent_pat=""
  local k
  for k in "${KNOWN_ABSENT[@]}"; do
    absent_pat="${absent_pat}${absent_pat:+|}^${k}\$"
  done

  dangling="$(printf '%s\n' "$cites" | cut -f1 | sort -u \
    | grep -vxF -f <(printf '%s\n' "$defs") \
    | { [ -n "$absent_pat" ] && grep -vE "$absent_pat" || cat; } )"

  if [ -z "$quiet" ]; then
    echo "definitions: $n_def   cited: $n_cite   documented-absent: ${#KNOWN_ABSENT[@]}"
  fi

  if [ -z "$dangling" ]; then
    [ -z "$quiet" ] && echo "OK: every cited C-/B- id resolves to an entry that exists."
    return 0
  fi

  echo "::error::dangling citation(s) -- cited, but defined nowhere:"
  local id near
  while IFS= read -r id; do
    [ -z "$id" ] && continue
    echo "  $id"
    printf '%s\n' "$cites" | awk -F'\t' -v want="$id" '$1==want{print "      " $2}' | head -5
    # NEAR-MISS HINT (run 92).
    #
    # Run 91 wrote its five entries into AUDIT-REQUEST.md as list items --
    # "- **C-91-1** — the corpus is intact" -- instead of headings. The ids were
    # in the right FILE, in the right ORDER, with correct commands under them;
    # only the heading level was missing. The guard was right to fail, but it
    # said "defined nowhere", which is the one description that sends a reader
    # looking in the wrong place: the entry is not missing, it is unparseable.
    # Run 92 spent its slice on that diagnosis. This prints it instead.
    near="$(find_near_miss "$root" "$id")"
    if [ -n "$near" ]; then
      echo "      ^ NEAR MISS: present in a definition doc but NOT on a heading line:"
      printf '%s\n' "$near" | sed 's/^/        /'
      echo "        fix: make it a heading -- '### $id — <title>' -- not a list item."
    fi
  done <<< "$dangling"
  return 1
}

# ---------------------------------------------------------------------------
# find_near_miss <root> <id> -> "file:line: text" for a line in a DEF_DOC that
# looks like an ATTEMPTED DEFINITION of <id> but is not a heading, or empty.
#
# WHY THE SHAPE TEST, AND NOT JUST "IS THE ID IN THIS FILE"
#
# The first cut of this fired on any off-heading mention in a DEF_DOC. That is
# too broad, and it misfired the same day it was written: B-25's prose cites
# "(C-92-8)" mid-sentence, in BLOCKED.md, a DEF_DOC -- an ordinary forward
# reference, not a mis-filed definition. The hint told the author to "make it a
# heading", which would have been wrong. A hint that gives confident wrong
# advice is worse than a terse correct verdict.
#
# A mis-filed definition has a shape: the id OPENS the line, after at most a
# list marker and a bold/emphasis run.
#
#   - **C-91-1** — the corpus is intact      <- attempted definition, hint
#   * C-91-1: the corpus is intact           <- attempted definition, hint
#   ...verified this run (**C-92-8**).       <- prose citation, NO hint
#
# It never suppresses a failure -- it only explains one -- so a false NEGATIVE
# here costs a sentence of help, while a false POSITIVE costs the reader's
# trust in every other line the guard prints. Bias to silence.
# ---------------------------------------------------------------------------
find_near_miss() {
  local root="$1" id="$2" doc
  for doc in "${DEF_DOCS[@]}"; do
    [ -f "$root/$doc" ] || continue
    awk -v FNAME="$doc" -v WANT="$id" '
      /^[ \t]*(```|~~~)/ { infence = !infence; next }
      infence { next }
      /^(##|###|####) / { next }               # a heading would have been a definition
      {
        # strip leading space, an optional list marker, and an optional
        # opening bold/italic run -- what is left must START with the id.
        t = $0
        sub(/^[ \t]+/, "", t)
        sub(/^([-*+]|[0-9]+[.)])[ \t]+/, "", t)
        sub(/^(\*\*|__|\*|_|`)+/, "", t)
        if (index(t, WANT) != 1) next
        rest = substr(t, length(WANT) + 1, 1)
        if (rest ~ /[0-9A-Za-z]/) next          # ran into a longer token
        u = $0; sub(/^[ \t]+/, "", u)
        if (length(u) > 88) u = substr(u, 1, 85) "..."
        printf "%s:%d: %s\n", FNAME, FNR, u
      }
    ' "$root/$doc"
  done | head -3
}

# ---------------------------------------------------------------------------
# self-test: a guard nobody has watched fail is not yet a guard.
#
# Builds throwaway copies of the record set and asserts the checker's verdict
# on each. Pins the two parser defects that measurement (not reading) found.
# ---------------------------------------------------------------------------
self_test() {
  local tmp rc fails=0
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN

  _case() { # name expected_rc  (fixture already built at $tmp/case)
    local name="$1" want="$2" got
    run_check "$tmp/case" quiet >"$tmp/out" 2>&1; got=$?
    if [ "$got" = "$want" ]; then
      echo "  PASS  $name (exit $got)"
    else
      echo "  FAIL  $name (exit $got, wanted $want)"; sed 's/^/        /' "$tmp/out"
      fails=$((fails+1))
    fi
  }

  _fixture() { rm -rf "$tmp/case"; mkdir -p "$tmp/case/docs"; }

  echo "self-test:"

  # 1. clean tree -> 0
  _fixture
  printf '### C-1-1 — a check\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n'  > "$tmp/case/BLOCKED.md"
  printf 'The thing held (C-1-1) and B-1 stays open.\n' > "$tmp/case/LOG.md"
  _case "clean tree passes" 0

  # 2. run 75's actual incident: a cited id that was never filed -> 1
  _fixture
  printf '### C-1-1 — a check\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n'  > "$tmp/case/BLOCKED.md"
  printf 'Filed as B-22 this run, verified (C-75-11).\n' > "$tmp/case/LOG.md"
  _case "run 75's incident is caught" 1

  # 3. combined heading defines BOTH ids (parser lesson 2)
  _fixture
  printf '### C-RES-1 / C-RES-2 — what each STOP contains\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n' > "$tmp/case/BLOCKED.md"
  printf 'See C-RES-2 for the merge evidence.\n' > "$tmp/case/LOG.md"
  _case "combined heading defines both ids" 0

  # 4. milestone label is not a citation (parser lesson 3)
  _fixture
  printf '### C-1-1 — a check\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n' > "$tmp/case/BLOCKED.md"
  printf '### S5.B-0 The environment finding that decided the slice\n' > "$tmp/case/LOG.md"
  _case "milestone label S5.B-0 is not a citation" 0

  # 5. a range cites every id in it -> the gap in the middle is caught
  _fixture
  printf '### C-X-1 — a\n### C-X-2 — b\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n' > "$tmp/case/BLOCKED.md"
  printf 'Re-verify: C-X-1…3\n' > "$tmp/case/LOG.md"
  _case "range expansion catches the missing end" 1

  # 6. em-dash after an id is prose, never a range
  _fixture
  printf '### C-X-4 — a\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n' > "$tmp/case/BLOCKED.md"
  printf 'C-X-4 — a blind relay could truncate 1 stream without decrypting.\n' > "$tmp/case/LOG.md"
  _case "em-dash after an id is prose, not a range" 0

  # 7. a fenced code block is a fixture, not a claim (C-77-11)
  _fixture
  printf '### C-1-1 — a check\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n' > "$tmp/case/BLOCKED.md"
  printf 'How it fails:\n\n```bash\nprintf "cite B-23 and C-77-4" >> LOG.md\n```\n' \
    > "$tmp/case/LOG.md"
  _case "fenced code block is a fixture, not a claim" 0

  # 7b. ...but prose AFTER the fence closes is still checked
  _fixture
  printf '### C-1-1 — a check\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n' > "$tmp/case/BLOCKED.md"
  printf '```bash\necho B-23\n```\n\nAnd then B-24 was filed.\n' > "$tmp/case/LOG.md"
  _case "prose after a closed fence is still checked" 1

  # 7c. a heading-shaped line INSIDE a fence does not define anything (C-77-12).
  #     Without this, the quoted example below would "define" B-23 and the
  #     dangling citation in the prose would pass silently.
  _fixture
  printf '### C-1-1 — a check\n\nExample of a filed blocker:\n\n```\n## B-23 — a blocker\n```\n' \
    > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n' > "$tmp/case/BLOCKED.md"
  printf 'Filed as B-23 this run.\n' > "$tmp/case/LOG.md"
  _case "fenced heading does not define an id" 1

  # 8. abbreviated continuation cites its siblings
  _fixture
  printf '### C-A2-1 — a\n### C-A2-2 — b\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n' > "$tmp/case/BLOCKED.md"
  printf 'Closed C-A2-1/-2/-3 this run.\n' > "$tmp/case/LOG.md"
  _case "continuation form cites its siblings" 1

  # 9. run 91's actual incident: the entry is in the right FILE, in the wrong
  #    FORM. Still a failure -- but the report must say which, or the reader
  #    goes looking for a missing entry that is sitting in front of them.
  _fixture
  printf '### C-1-1 — a check\n\n- **C-91-1** — a list item, not a heading\n' \
    > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n' > "$tmp/case/BLOCKED.md"
  printf 'The corpus is intact (C-91-1).\n' > "$tmp/case/LOG.md"
  _case "list-item entry still fails" 1
  if grep -q 'NEAR MISS' "$tmp/out" && grep -q 'AUDIT-REQUEST.md:3:' "$tmp/out"; then
    echo "  PASS  ...and the report names it as a form error, with the line"
  else
    echo "  FAIL  ...but the report did not name it as a form error"
    sed 's/^/        /' "$tmp/out"; fails=$((fails+1))
  fi

  # 9b. the hint must NOT fire for a genuinely absent id -- otherwise it
  #     reassures on exactly the case run 75 proved is real.
  _fixture
  printf '### C-1-1 — a check\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n' > "$tmp/case/BLOCKED.md"
  printf 'Filed as B-22 this run, verified (C-75-11).\n' > "$tmp/case/LOG.md"
  _case "genuinely absent id still fails" 1
  if grep -q 'NEAR MISS' "$tmp/out"; then
    echo "  FAIL  ...but a near-miss hint fired on an id that is truly absent"
    sed 's/^/        /' "$tmp/out"; fails=$((fails+1))
  else
    echo "  PASS  ...and no near-miss hint fired"
  fi

  # 9c. B-25's actual misfire: a DEF_DOC citing a not-yet-filed id MID-SENTENCE
  #     is an ordinary forward reference, not a mis-filed definition. The first
  #     cut of find_near_miss() fired here and told the author to "make it a
  #     heading", which would have been wrong. Still a failure; no hint.
  _fixture
  printf '### C-1-1 — a check\n' > "$tmp/case/AUDIT-REQUEST.md"
  printf '## B-1 — a blocker\n\nObserved on job 97291351051 (**C-92-8**), so it is red.\n' > "$tmp/case/BLOCKED.md"
  printf 'See B-1.\n' > "$tmp/case/LOG.md"
  _case "mid-sentence citation in a def doc still fails" 1
  if grep -q 'NEAR MISS' "$tmp/out"; then
    echo "  FAIL  ...but the hint misread a prose citation as a mis-filed definition"
    sed 's/^/        /' "$tmp/out"; fails=$((fails+1))
  else
    echo "  PASS  ...and the hint stayed silent on a prose citation"
  fi

  echo
  if [ "$fails" = 0 ]; then echo "self-test: all cases passed"; return 0; fi
  echo "self-test: $fails case(s) FAILED"; return 1
}

case "$MODE" in
  selftest) self_test; exit $? ;;
  list)
    extract_definitions "$ROOT" | sed 's/^/  def  /'
    run_check "$ROOT"; exit $?
    ;;
  *) run_check "$ROOT"; exit $? ;;
esac
