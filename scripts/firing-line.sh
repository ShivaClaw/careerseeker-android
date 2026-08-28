#!/usr/bin/env bash
#
# firing-line.sh — one no-change firing, in one line.
#
# WHY THIS EXISTS
#
# B-18 records that this routine keeps firing past its own stop condition. Six
# attempts have been logged against it. Attempts 3-6 all lowered the READ cost of
# a firing: run 48 put a banner where the reader looks, run 53 pushed the facts
# outside the repository, and run-zero.sh reaches ground state in one command.
#
# None of them touched the WRITE cost, and run 118 measured it for the first
# time. A no-change firing adds a median of ~355 lines to the four house records
# (runs 111-117: 327, 321, 380, 392, 336, 361, 355), which stood at 50,862 lines
# when this script was written. On 2026-08-28 the schedule fired five times, so
# that is roughly 1,700 lines a day of restatement.
#
# That cost is not merely wasteful, it is self-defeating: every 355 lines added
# makes the next session's read longer, which is the exact cost run-zero.sh was
# built to remove. The two mitigations were working against each other.
#
# THE RULE THIS SCRIPT SERVES (house law from run 118; see FIRINGS.md)
#
# A firing whose run-zero.sh verdict is NOTHING MOVED and whose five escalation
# triggers are all negative appends ONE line to FIRINGS.md and writes to NONE of
# STATE.md, LOG.md, BLOCKED.md or AUDIT-REQUEST.md. A firing that finds anything
# — a moved main, a merged or undrafted PR, a changed prompt, an executed gate,
# or a genuine new finding — writes a full entry in the four records as before,
# and this script is not for it.
#
# WHAT THIS IS NOT
#
# This is NOT a gate and it runs no build, no suite and no Verify-Alpha.ps1. It
# derives every field from run-zero.sh's actual output, so a line cannot claim a
# state the probe did not report. The two board counts need the GitHub API, which
# a shell script here cannot reach (run-zero.sh section 6, same limit); they are
# REQUIRED ARGUMENTS, supplied by the session that queried them, and the script
# refuses to invent them.
#
# USAGE
#   scripts/firing-line.sh <run-number> <engine-clone> <engine-open> <android-open> <esc-ledger> [note]
#
# EXAMPLE
#   scripts/firing-line.sh 118 ../careerseeker 22 6 11 'declined: S5 spec half, 83rd'
#
# It PRINTS the line and does not append it. Read it, then append it yourself:
#   scripts/firing-line.sh ... >> FIRINGS.md

set -uo pipefail

if [ "$#" -lt 5 ]; then
  sed -n '/^# USAGE/,/^$/p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi

run="$1"; engine="$2"; eng_open="$3"; and_open="$4"; ledger="$5"; note="${6:-}"
here="$(cd "$(dirname "$0")/.." && pwd)"

for n in "$run" "$eng_open" "$and_open" "$ledger"; do
  case "$n" in (*[!0-9]*|"") echo "firing-line: '$n' is not a number" >&2; exit 2;; esac
done

probe="$(bash "$here/scripts/run-zero.sh" "$engine" 2>&1)" || true

verdict=NOT-CLEAN
grep -q '^  NOTHING MOVED on every check' <<<"$probe" && verdict='NOTHING MOVED'

pin=$(grep -m1 '^  current pin' <<<"$probe" | awk '{print substr($4,1,7)}')
corpus=$(grep -m1 '^  vendored:' <<<"$probe" | awk '{print $2"/"$6}')
eng_main=$(grep -m1 '^  engine  main' <<<"$probe" | awk '{print $4}')
and_main=$(grep -m1 '^  android main' <<<"$probe" | awk '{print $4}')
cites=$(grep -m1 '^  definitions:' <<<"$probe" | awk '{print $2"/"$4"/"$6}')
gen=$(grep -qF 'OK: 29 vector files match the generator.' <<<"$probe" && echo 'gen OK' || echo 'gen ?')

if [ -z "$pin$eng_main$and_main" ]; then
  echo "firing-line: run-zero.sh produced nothing parseable; write a full entry by hand" >&2
  exit 1
fi

printf '%s | %s | %s | pin %s | corpus %s | %s | mains %s/%s | cites %s | board %s+%s open | esc %s%s\n' \
  "$run" "$(date -u +%Y-%m-%d)" "$verdict" "$pin" "$corpus" "$gen" \
  "$eng_main" "$and_main" "$cites" "$eng_open" "$and_open" "$ledger" \
  "${note:+ | $note}"
