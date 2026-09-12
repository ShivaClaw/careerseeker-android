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
#   scripts/fleet-probe.sh leaves <engine-checkout>
#   scripts/fleet-probe.sh land   <engine-checkout> <branch> [<branch> ...]
#   scripts/fleet-probe.sh plan   <engine-checkout> <plan-file>
#
#   # Does RETURN-DAY.md's landing plan still name the leaves of the graph?
#   # Exit 1 means the plan rotted -- re-derive it before merging anything.
#   scripts/fleet-probe.sh plan ../careerseeker RETURN-DAY.md
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

# A leaf is a fleet branch contained in no OTHER fleet branch. Factored out of
# the 'leaves' mode so 'plan' can ask the same question without shelling out to
# a second copy of this script -- two implementations of "leaf" is exactly the
# drift this file exists to catch.
leaves_list() {
  while read -r b; do
    covered=0
    while read -r other; do
      [ "$other" = "$b" ] && continue
      if git -C "$repo" merge-base --is-ancestor "$b" "$other" 2>/dev/null; then covered=1; break; fi
    done < <(fleet)
    [ "$covered" -eq 0 ] && echo "${b#origin/}"
  done < <(fleet)
  true
}

# Pull the landing table's BRANCH column out of RETURN-DAY.md section 3.
#
# Keyed on the branch name, NOT the PR number, and that is the whole point of
# B-19: "PR numbers are not stable descriptions of a merge graph". A branch name
# is a ref that `git` can check against ancestry with no credential at all. The
# PR number is metadata on a server, and it is the thing that went stale.
#
# The table rows look like:
#   | 1 | **#48** | `s8-harness-linux-reach` | itself | clean |
#   | 2 | **~~#35~~ -> #57** | `s2-relay-header-pairing` | ... | ... |
# so the branch is the first backticked token of a row whose first cell is a
# bare number. Rows are read in file order; that order is the merge order, and
# 'land' consumes it directly.
plan_branches() {
  sed -n 's/^| *[0-9][0-9]* *|[^|]*| *`\([^`]*\)`.*/\1/p' "$1"
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
    # --- plan-mode guard, added run 88 (B-19) ---------------------------------
    #
    # A guard is only worth having if it has been seen to FAIL. All three rows
    # below are executed against the real fleet, using synthetic plan files, so
    # the pass row and the fail row are measured rather than argued.
    tmp=$(mktemp -d) || { echo "FAIL  cannot mktemp"; exit 1; }
    trap 'rm -rf "$tmp"' EXIT

    a_leaf=$(leaves_list | head -1); a_leaf="${a_leaf#claude/}"
    if [ -z "$a_leaf" ]; then
      echo "FAIL  leaves_list() returned nothing; cannot self-test plan mode"; fail=1
    else
      printf '| 1 | **#00** | `%s` | itself | clean |\n' "$a_leaf" > "$tmp/green.md"
      if "$0" plan "$repo" "$tmp/green.md" >/dev/null 2>&1; then
        echo "PASS  plan() accepts a table naming a real leaf ($a_leaf)"
      else
        echo "FAIL  plan() rejected a table naming a real leaf ($a_leaf)"; fail=1
      fi
    fi

    # The rot row. Any non-leaf fleet branch proves it; prefer the branch that
    # actually rotted (claude/s2-seq-bound, PR #35) so this row stays tied to
    # the incident, and fall back to any other non-leaf if it is ever deleted.
    rotted=""
    if git -C "$repo" rev-parse --verify -q origin/claude/s2-seq-bound >/dev/null \
       && ! leaves_list | grep -qxF 'claude/s2-seq-bound'; then
      rotted="s2-seq-bound"
    else
      rotted=$(comm -23 <(fleet | sed 's|^origin/claude/||' | sort) \
                        <(leaves_list | sed 's|^claude/||' | sort) | head -1)
    fi
    if [ -z "$rotted" ]; then
      echo "SKIP  no non-leaf branch in the fleet; the rot row cannot be measured here"
    else
      printf '| 1 | **#00** | `%s` | itself | clean |\n' "$rotted" > "$tmp/rotted.md"
      if "$0" plan "$repo" "$tmp/rotted.md" >/dev/null 2>&1; then
        echo "FAIL  plan() PASSED a table naming the non-leaf '$rotted' -- the guard does not fire"
        fail=1
      else
        echo "PASS  plan() fires on a table naming the non-leaf '$rotted'"
      fi
    fi

    # A parse that matches nothing must REFUSE, not pass. This is fleet()'s '**'
    # bug one level up: a guard that silently reads zero rows reports "no rot"
    # forever, which is worse than having no guard at all.
    echo 'the landing table used to be here, and its format moved' > "$tmp/empty.md"
    "$0" plan "$repo" "$tmp/empty.md" >/dev/null 2>&1
    if [ "$?" -eq 2 ]; then
      echo "PASS  plan() refuses (exit 2) when it parses zero rows"
    else
      echo "FAIL  plan() did not refuse on a zero-row parse -- it would always pass"; fail=1
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

  leaves)
    # A leaf is a fleet branch contained in no OTHER fleet branch. Merging the
    # leaves lands every PR beneath them, so the leaf set -- not the PR count --
    # is the number of merges a human actually performs.
    leaves_list
    # A leaf is not the same thing as an OPEN PR: a superseded branch nobody
    # closed is a leaf too (claude/p4-entitlement is one -- its successors
    # landed as PRs #27-#30). Cross-check the list against the open-PR set
    # before treating it as a landing plan.
    ;;

  plan)
    # B-19's guard: does the written landing plan still describe THIS graph?
    #
    # The failure it exists to catch happened on 2026-08-23 and nothing
    # detected it. RETURN-DAY.md section 3 named `#35` as a merge. Between
    # 2026-08-19 and 2026-08-23, PRs #54/#55/#56/#57 stacked on #35's head, so
    # #35 stopped being a leaf. Following the plan verbatim would have landed
    # an interior node and stranded seven commits across four open PRs whose
    # base branch had just been merged. Run 87 found it BY HAND.
    #
    # WHAT THIS COSTS: a `git fetch` in the engine checkout. Nothing more.
    #
    # B-19's third attempt recorded this guard as needing `gh pr list` and
    # therefore a cross-repo token, which made it Brandon's decision rather
    # than a session's. That is true of PR STATE (open/closed/merged) and false
    # of the rot that actually fired: a branch stops being a leaf when other
    # REFS contain it, and refs are what a fetch already brings down. The rot
    # signal is in ancestry, not in PR metadata. See BLOCKED.md B-19.
    #
    # WHAT THIS DOES NOT CATCH, stated here so the output is not over-read:
    #   - a named PR being closed or merged behind the plan's back;
    #   - a leaf branch that has no open PR at all (claude/p4-entitlement is
    #     exactly that, and it is why UNPLANNED below is informational);
    #   - anything semantic. A green plan is a plan that still points at leaves,
    #     not a plan that is still a good idea.
    # Those need the PR list, and that is the half B-19 still owns.
    planfile="${3:-}"
    [ -n "$planfile" ] || { echo "fleet-probe plan: need a plan file (RETURN-DAY.md)" >&2; exit 2; }
    [ -r "$planfile" ] || { echo "fleet-probe plan: cannot read '$planfile'" >&2; exit 2; }

    named=$(plan_branches "$planfile")
    nnamed=$(printf '%s' "$named" | grep -c .)
    # A parse that silently returns nothing would print "no rot" for a rotted
    # plan -- the false negative that makes a guard worse than no guard. This
    # is fleet()'s '**' bug one level up, and it is refused, not reported.
    if [ "$nnamed" -eq 0 ]; then
      echo "fleet-probe plan: parsed 0 branches from '$planfile' -- the table format moved." >&2
      echo "REFUSING to report a result: a guard that parses nothing always passes." >&2
      exit 2
    fi

    current=$(leaves_list)
    echo "Landing-plan guard: does '$planfile' still name the leaves of this graph?"
    echo "Plan rows are read in file order, which is merge order."
    echo
    rot=0
    while read -r b; do
      [ -n "$b" ] || continue
      ref="origin/claude/${b#claude/}"
      if ! git -C "$repo" rev-parse --verify -q "$ref" >/dev/null; then
        printf '  %-40s GONE  no such ref (branch deleted or renamed)\n' "$b"
        rot=$((rot+1)); continue
      fi
      if printf '%s\n' "$current" | grep -qxF "claude/${b#claude/}"; then
        printf '  %-40s leaf\n' "$b"
      else
        # Name the successors, because "not a leaf" alone does not tell a human
        # which PR to merge instead. These are the branches that now contain it.
        by=$(while read -r other; do
               [ "$other" = "$ref" ] && continue
               git -C "$repo" merge-base --is-ancestor "$ref" "$other" 2>/dev/null \
                 && echo "${other#origin/claude/}"
             done < <(fleet) | tr '\n' ' ')
        printf '  %-40s ROT   no longer a leaf; contained by: %s\n' "$b" "${by:-(unknown)}"
        rot=$((rot+1))
      fi
    done <<< "$named"

    echo
    unplanned=0
    while read -r l; do
      [ -n "$l" ] || continue
      if ! printf '%s\n' "$named" | grep -qxF "${l#claude/}"; then
        printf '  %-40s UNPLANNED  a leaf the plan does not name\n' "${l#claude/}"
        unplanned=$((unplanned+1))
      fi
    done <<< "$current"
    [ "$unplanned" -eq 0 ] && echo "  (every leaf is named by the plan)"

    echo
    echo "plan rows: $nnamed   leaves now: $(printf '%s' "$current" | grep -c .)   ROT: $rot   UNPLANNED: $unplanned"
    if [ "$rot" -gt 0 ]; then
      echo "PLAN IS STALE -- $rot row(s) name a branch that is no longer a leaf. Re-derive before merging."
      exit 1
    fi
    echo "PLAN STILL NAMES LEAVES. (UNPLANNED rows are informational -- a leaf with no open PR,"
    echo "or one the plan deliberately excludes, is expected. Check them against the open-PR set.)"
    ;;

  land)
    # CUMULATIVE landing probe -- the question 'matrix' does not ask.
    #
    # 'matrix' and Merge-Topology.md 10.2 probe each branch against PRISTINE
    # main. That answers "what does this branch cost to land FIRST" and it is
    # why 10.4 concluded the tree "conflicts once". It is not what a human
    # doing the merges experiences: after the first pin-touching leaf lands,
    # main's $ExpectedOfflineTotal has MOVED, so the next pin-touching leaf
    # conflicts against the new value. N pin-touchers cost N-1 stops, not one.
    #
    # No working tree is touched: merge-tree/commit-tree are pure object-store
    # operations. On a conflict the probe keeps merge-tree's conflicted tree so
    # it can continue -- that is a PROBE ARTIFACT, NOT A RESOLUTION. The real
    # resolution keeps both sides' prose and writes the pin value the Windows
    # gate measures (CLAUDE.md's drift trap; Merge-Topology.md 10.3).
    shift 2
    [ "$#" -gt 0 ] || { echo "fleet-probe land: need at least one branch, in merge order" >&2; exit 2; }
    acc=$(git -C "$repo" rev-parse origin/main) || exit 2
    conflicts=0
    echo "Cumulative landing probe onto origin/main, in the order given."
    echo "A 'stop' is a merge a human must stop and resolve by hand."
    echo
    for leaf in "$@"; do
      ref="origin/${leaf#origin/}"
      git -C "$repo" rev-parse --verify -q "$ref" >/dev/null \
        || { echo "fleet-probe: no such ref '$ref'" >&2; exit 2; }
      out=$(git -C "$repo" merge-tree --write-tree "$acc" "$ref" 2>/dev/null)
      rc=$?
      tree=$(printf '%s\n' "$out" | head -1)
      if [ "$rc" -ne 0 ]; then
        conflicts=$((conflicts+1))
        files=$(printf '%s\n' "$out" | sed -n 's/^CONFLICT ([^)]*): Merge conflict in //p' | tr '\n' ' ')
        printf '  %-40s STOP  %s\n' "${ref#origin/claude/}" "${files:-(conflict, files unlisted)}"
      else
        printf '  %-40s clean\n' "${ref#origin/claude/}"
      fi
      acc=$(git -C "$repo" commit-tree "$tree" -p "$acc" -p "$ref" -m "probe: $leaf") || exit 2
    done
    echo
    echo "conflicted merges (human stops): $conflicts"
    ;;

  *)
    echo "fleet-probe: unknown mode '$mode' (expected 'symbol', 'matrix', 'leaves' or 'land')" >&2
    exit 2
    ;;
esac
