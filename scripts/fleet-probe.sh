#!/usr/bin/env bash
#
# fleet-probe.sh — ask the engine repo's UNMERGED branch fleet two questions
# before writing code against it.
#
# WHY THIS EXISTS
#
# The forty-fourth iteration (2026-08-16) was routed by STATE.md's ordered
# intent to build ITEM 1: "nothing consumes the 409's `latest` at runtime yet".
# It was already built. `SyncPublisher.ReconcileTo`, a `RelaySink` whose call
# site is mutation-tested, and the harness assertions all landed on
# `claude/s6-counter-reconciliation` (PR #46) on 2026-08-14 -- two days BEFORE
# the intent naming it as open was written.
#
# The cause is structural, not carelessness. Thirteen draft PRs are open and
# none is merged, so `origin/main` is not the state of the program. A session
# that derives "what is still missing" by reading main -- which is the honest
# and obvious thing to do -- sees every already-solved-but-unmerged problem as
# open. PR #53 was cut depth-1 off main for exactly that reason, and so
# re-implemented #45's typed push result as `PushOutcome` alongside #46's
# `RelayPushResult`: two incompatible answers to one defect.
#
# Both questions below are cheap. Neither had a one-command form, which is the
# whole reason the duplication was not caught at derivation time.
#
# WHAT THIS IS NOT
#
# This is not a gate, and it decides nothing. `symbol` can only prove a name
# EXISTS somewhere -- read the branch before concluding it does what you need.
# `matrix` reports textual conflicts from `git merge-tree`; a clean probe is
# not proof two branches are semantically compatible, and a conflicting one is
# not proof the work is wasted. Merge order and merge policy stay Brandon's.
#
# USAGE
#
#   scripts/fleet-probe.sh symbol <engine-checkout> <regex> [<regex> ...]
#   scripts/fleet-probe.sh matrix <engine-checkout> <branch>
#
#   # Is the mechanism already built anywhere in the fleet?
#   scripts/fleet-probe.sh symbol ../careerseeker ReconcileTo
#
#   # What would #53 actually cost the branches it overlaps?
#   scripts/fleet-probe.sh matrix ../careerseeker claude/s6-resume-reconciliation
#
# Run `git fetch --all --prune` in the engine checkout first. Every count this
# prints is only as fresh as the refs it reads -- the stale-refs incident of
# 2026-08-06 is why that is rule one of every iteration.

set -uo pipefail

mode="${1:-}"
repo="${2:-}"

if [ -z "$mode" ] || [ -z "$repo" ]; then
  sed -n '/^# USAGE/,/^# Run /p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi

if ! git -C "$repo" rev-parse --git-dir >/dev/null 2>&1; then
  echo "fleet-probe: '$repo' is not a git checkout" >&2
  exit 2
fi

# The fleet is every remote branch that is not main and not Terra's. Derived,
# never hardcoded: a hardcoded list is the same staleness bug one level up.
#
# The pattern is '**', not '*'. for-each-ref matches a single '*' only up to the
# next slash, so 'refs/remotes/origin/*' selects exactly `origin/main` and every
# `origin/claude/...` branch silently vanishes. The first draft of this script
# had that bug and printed "not present in any unmerged branch" for three
# symbols that are present in four -- the very false negative this file exists
# to prevent, reproduced inside the tool. Asserted by --self-test below.
fleet() {
  git -C "$repo" for-each-ref --format='%(refname:short)' 'refs/remotes/origin/**' \
    | grep -vE '/(main|HEAD)$' \
    | grep -vE '/(codex|autonomy)/'
}

case "$mode" in
  self-test|--self-test)
    # Guards the one bug that makes this tool WORSE than no tool: a fleet that
    # silently narrows to nothing prints "not present" for code that is present,
    # which is the false negative the whole file exists to prevent.
    fail=0
    n=$(fleet | grep -c .)
    if [ "$n" -lt 2 ]; then
      echo "FAIL  fleet() returned $n branch(es); the '**' pattern has regressed to '*'"
      fail=1
    else
      echo "PASS  fleet() sees $n branches (more than just origin/main)"
    fi
    if fleet | grep -qE '/claude/'; then
      echo "PASS  fleet() includes claude/* branches"
    else
      echo "FAIL  fleet() found no claude/* branch"; fail=1
    fi
    if fleet | grep -qE '/(main|HEAD)$|/(codex|autonomy)/'; then
      echo "FAIL  fleet() leaked main, HEAD, or a codex/autonomy ref"; fail=1
    else
      echo "PASS  fleet() excludes main, HEAD, codex/* and autonomy/*"
    fi
    [ "$fail" -eq 0 ] && echo "self-test: OK" || echo "self-test: FAILED"
    exit "$fail"
    ;;

  symbol)
    shift 2
    [ "$#" -gt 0 ] || { echo "fleet-probe symbol: need at least one regex" >&2; exit 2; }
    for pattern in "$@"; do
      echo "=== '$pattern' ==="
      found=0
      while read -r b; do
        # -- limits the search to code; a symbol named only in a doc or a
        # records file is a mention, not an implementation.
        hits=$(git -C "$repo" grep -lE "$pattern" "$b" -- 'src/*' 'tests/*' 'relay/src/*' 'relay/test/*' 2>/dev/null | wc -l)
        if [ "$hits" -gt 0 ]; then
          found=1
          printf '  %-46s %s file(s)\n' "${b#origin/}" "$hits"
        fi
      done < <(fleet)
      [ "$found" -eq 1 ] || echo "  (not present in any unmerged branch's code)"
      echo
    done
    ;;

  matrix)
    target="${3:-}"
    [ -n "$target" ] || { echo "fleet-probe matrix: need a branch" >&2; exit 2; }
    ref="origin/${target#origin/}"
    git -C "$repo" rev-parse --verify -q "$ref" >/dev/null \
      || { echo "fleet-probe: no such ref '$ref'" >&2; exit 2; }

    echo "Leaf-vs-leaf conflict probe: $ref against the rest of the fleet."
    echo "SRC counts conflicts in src/ tests/ relay/ EXCLUDING *.md; DOC counts the rest."
    echo "The pin/doc family (README.md, docs/CareerSeeker-Project-Summary.md,"
    echo "docs/External-Audit-Handoff.md, scripts/Verify-Alpha.ps1, src/Engine/README.md)"
    echo "is the EXPECTED additive cost -- Merge-Topology.md 10.2/10.3. SRC is not."
    echo
    printf '  %-46s %-5s %-5s %s\n' BRANCH SRC DOC 'CONFLICTING SOURCE/TEST FILES'
    while read -r b; do
      [ "$b" = "$ref" ] && continue
      conf=$(git -C "$repo" merge-tree --write-tree "$ref" "$b" 2>/dev/null \
        | sed -n 's/^CONFLICT ([^)]*): Merge conflict in //p')
      # An unrelated-histories or otherwise unusable pair yields no tree; say so
      # rather than printing 0 and reading as clean.
      if [ -z "$conf" ]; then
        printf '  %-46s %-5s %-5s %s\n' "${b#origin/}" 0 0 '-'
        continue
      fi
      src=$(printf '%s\n' "$conf" | grep -E '^(src|tests|relay)/' | grep -vE '\.md$')
      nsrc=$(printf '%s' "$src" | grep -c . )
      ndoc=$(( $(printf '%s\n' "$conf" | grep -c .) - nsrc ))
      list=$(printf '%s' "$src" | tr '\n' ' ')
      printf '  %-46s %-5s %-5s %s\n' \
        "${b#origin/}" "$nsrc" "$ndoc" "${list:--}"
    done < <(fleet)
    ;;

  *)
    echo "fleet-probe: unknown mode '$mode' (expected 'symbol' or 'matrix')" >&2
    exit 2
    ;;
esac
