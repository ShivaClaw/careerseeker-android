#!/usr/bin/env bash
#
# repin-vectors.sh — re-vendor the shared sync-protocol vectors at a new pin,
#                    or verify the vendored corpus still matches the current one.
#
# WHY THIS EXISTS
#
# RETURN-DAY.md §3 ends with a step that has no tooling behind it: after the six
# merges land, `main` carries a vector the phone does not
# (pairing-high-bit-confirm.json, arriving with PR #51), the phone is BEHIND by
# one file, and NOTHING REPORTS IT. That is B-16/H7. android CI's drift step
# fetches upstream at `?ref=$PIN` (ci.yml:100-101), so a stale pin is invisible
# to it by construction: the corpus matches the pin, the pin is the stale thing,
# and the job is green. Nothing fails at runtime either — ProtocolVectorsTest
# enumerates from the phone's own index.json, so a vector it has never seen is
# a vector it never looks for.
#
# So the re-pin is a step a human has to remember, in the same sitting as the
# merges, with no check to catch them if they don't. Every previous run recorded
# that fact and then wrote it out by hand. This makes it one command.
#
# WHAT THIS IS NOT
#
# This is NOT a decision about which upstream ref the two repos should track.
# That is H3 in BLOCKED.md and it is Brandon's — the three options there
# (advisory CI job / compare against main once the stack lands / accept and
# document) are untouched by this script. This only performs the mechanical
# half: given a pin you name, make the vendored bytes match it exactly, and
# refuse to write anything it cannot prove.
#
# It is also NOT the android gate. It runs no Gradle. After a re-pin, :core:test
# still has to run — `scripts/core-probe.sh`, or the full gate on a machine with
# the Android SDK.
#
# USAGE
#   scripts/repin-vectors.sh --check              # verify against the CURRENT pin; write nothing
#   scripts/repin-vectors.sh --check <rev>        # report what re-pinning to <rev> WOULD change
#   scripts/repin-vectors.sh <rev>                # re-pin and re-vendor
#
#   <rev> is anything `git rev-parse` accepts in the engine clone — a full SHA,
#   a short SHA, or a ref such as origin/main. It is resolved to a full 40-hex
#   commit SHA before anything is written, because ci.yml:75 extracts the pin
#   with `grep -oE '[0-9a-f]{40}' | head -1` and a short SHA there would leave
#   the file with no pin CI can read.
#
#   The engine clone (ShivaClaw/careerseeker) is located in this order:
#     --engine <path>  |  $CAREERSEEKER_ENGINE  |  ../careerseeker  |  ../careerseeker-sync
#
# EXIT CODES
#   0  the vendored corpus matches the pin (--check), or the re-pin was written
#      and re-verified
#   1  drift found in --check, or a precondition failed and nothing was written
#
# WHAT IT REFUSES TO DO
#
# Every write is preceded by proofs, and any failure aborts before the working
# tree is touched:
#
#   * the rev must resolve to a commit that exists in the engine clone;
#   * that commit must actually carry docs/sync-vectors/v1;
#   * the corpus at that commit must satisfy its own generator
#     (`node docs/sync-vectors/generate.mjs --check`) — this is the property
#     VECTORS.lock's guarantee rests on, that the bytes are generator output
#     rather than something hand-edited. Skipped, loudly, only if node is absent;
#   * after writing, VECTORS.lock must contain EXACTLY ONE 40-hex string and it
#     must be the new pin. This is ci.yml:75's contract, and run 54 recorded it
#     as the sharp edge of this file: any 40-hex string added above the pin line
#     silently repoints CI at another commit.
#   * after writing, the vendored tree must be byte-identical to the archive it
#     came from.
#
# It rewrites the `# Pinned commit:` line and nothing else in VECTORS.lock. The
# prose below that line is human judgement — it records WHY a pin was chosen and
# what is known to be true of it — so the script leaves it alone and prints a
# reminder that it now needs a note.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
vendor_dir="$repo_root/core/src/test/resources/sync-vectors/v1"
lock_file="$repo_root/core/src/test/resources/sync-vectors/VECTORS.lock"

check_only=0
engine=""
rev=""

while [ $# -gt 0 ]; do
    case "$1" in
        --check)  check_only=1; shift ;;
        --engine) engine="${2:-}"; shift 2 ;;
        -h|--help)
            sed -n '2,90p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        -*) echo "repin-vectors: unknown option: $1" >&2; exit 1 ;;
        *)
            if [ -n "$rev" ]; then
                echo "repin-vectors: more than one rev given ('$rev' and '$1')" >&2
                exit 1
            fi
            rev="$1"; shift ;;
    esac
done

die() { echo "repin-vectors: $*" >&2; exit 1; }

[ -d "$vendor_dir" ] || die "no vendored corpus at $vendor_dir"
[ -f "$lock_file" ]  || die "no VECTORS.lock at $lock_file"

# The pin of record, read exactly the way ci.yml:75 reads it, so this script and
# CI can never disagree about which commit the corpus claims to come from.
#
# The `head -1` in that pipeline is the whole reason for the count check: a second
# 40-hex string anywhere above the pin line would silently become the pin CI reads,
# and nothing would say so. Run 54 found this by measurement and the file has kept
# to short SHAs in its prose ever since. Enforce it here rather than trust it —
# and enforce it BEFORE writing, so a file that already violates it is never
# half-rewritten.
lock_hex_count="$(grep -coE '[0-9a-f]{40}' "$lock_file" || true)"
current_pin="$(grep -oE '[0-9a-f]{40}' "$lock_file" | head -1 || true)"
[ -n "$current_pin" ] || die "VECTORS.lock contains no 40-hex commit SHA"
if [ "$lock_hex_count" -ne 1 ]; then
    die "VECTORS.lock contains $lock_hex_count 40-hex strings; ci.yml:75 takes the FIRST one, so the pin is ambiguous. Reduce the others to short SHAs by hand first."
fi

# --- locate the engine clone -------------------------------------------------

if [ -z "$engine" ]; then
    for candidate in "${CAREERSEEKER_ENGINE:-}" "$repo_root/../careerseeker" "$repo_root/../careerseeker-sync"; do
        [ -n "$candidate" ] || continue
        if [ -d "$candidate/.git" ]; then engine="$candidate"; break; fi
    done
fi

if [ -z "$engine" ]; then
    cat >&2 <<'MSG'
repin-vectors: no engine clone found.

This needs a clone of ShivaClaw/careerseeker to read the vectors from. Point it
at one:

    scripts/repin-vectors.sh --engine /path/to/careerseeker <rev>
    CAREERSEEKER_ENGINE=/path/to/careerseeker scripts/repin-vectors.sh <rev>

or place it alongside this repo as ../careerseeker or ../careerseeker-sync.
MSG
    exit 1
fi
engine="$(cd "$engine" && pwd)"

# Rule one, and it is not decoration: a rev resolved against stale refs resolves
# to a stale commit, silently. This does not fetch for you — fetching is a
# network action and this script does not take them on your behalf — but it does
# say when the clone last heard from the remote.
if ! git -C "$engine" rev-parse --git-dir >/dev/null 2>&1; then
    die "$engine is not a git repository"
fi

# --- resolve the target ------------------------------------------------------

target_rev="${rev:-$current_pin}"

if ! target_pin="$(git -C "$engine" rev-parse --verify --quiet "${target_rev}^{commit}")"; then
    cat >&2 <<MSG
repin-vectors: '$target_rev' does not resolve to a commit in $engine.

If it is a commit you expect to exist, the clone may not have it yet:

    git -C "$engine" fetch --all --prune
MSG
    exit 1
fi

if ! git -C "$engine" cat-file -e "$target_pin:docs/sync-vectors/v1" 2>/dev/null; then
    die "commit $target_pin carries no docs/sync-vectors/v1"
fi

echo "engine clone : $engine"
echo "current pin  : $current_pin"
echo "target pin   : $target_pin${rev:+  (from '$rev')}"

# Where the pin sits relative to main is not a pass/fail condition — the whole
# S5 stack is off-main by design and has been since 2026-08-09 — but it is the
# single most misread fact about this file, so state it rather than let a reader
# assume.
if git -C "$engine" merge-base --is-ancestor "$target_pin" origin/main 2>/dev/null; then
    echo "pin position : on origin/main"
else
    echo "pin position : NOT an ancestor of origin/main (off-main pin; see VECTORS.lock)"
fi

# --- extract the corpus at the target pin ------------------------------------

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# The whole docs/sync-vectors/ directory, not just v1/, because generate.mjs
# resolves OUT_DIR relative to its own file and cannot --check without it.
git -C "$engine" archive "$target_pin" docs/sync-vectors | tar -x -C "$work"
src_dir="$work/docs/sync-vectors/v1"
[ -d "$src_dir" ] || die "extraction produced no v1/ directory"

# --- prove the corpus is generator output, not hand-edited -------------------

if command -v node >/dev/null 2>&1; then
    if [ -f "$work/docs/sync-vectors/generate.mjs" ]; then
        echo
        echo "generator check at $target_pin:"
        if ! ( cd "$work" && node docs/sync-vectors/generate.mjs --check ); then
            die "the corpus at $target_pin does not match its own generator; refusing to vendor it"
        fi
    else
        echo "WARNING: no generate.mjs at $target_pin — cannot prove the corpus is generator output" >&2
    fi
else
    echo "WARNING: node not found — SKIPPING the generator check." >&2
    echo "WARNING: the vendored bytes will match the pin, but nothing here proves the pin's" >&2
    echo "WARNING: bytes are generator output. Re-run with node available before relying on it." >&2
fi

# --- compare the two corpora as sets, then as bytes --------------------------

ls "$src_dir"    | sort > "$work/upstream.names"
ls "$vendor_dir" | sort > "$work/vendored.names"

added=$(comm -23 "$work/upstream.names" "$work/vendored.names" || true)
removed=$(comm -13 "$work/upstream.names" "$work/vendored.names" || true)

changed=""
while read -r name; do
    [ -n "$name" ] || continue
    if ! cmp -s "$src_dir/$name" "$vendor_dir/$name"; then
        changed="$changed$name"$'\n'
    fi
done < <(comm -12 "$work/upstream.names" "$work/vendored.names")

n_added=$(printf '%s' "$added"     | grep -c . || true)
n_removed=$(printf '%s' "$removed" | grep -c . || true)
n_changed=$(printf '%s' "$changed" | grep -c . || true)
pin_moves=0
[ "$target_pin" = "$current_pin" ] || pin_moves=1

echo
echo "vendored: $(wc -l < "$work/vendored.names") files    at pin: $(wc -l < "$work/upstream.names") files"
[ "$n_added"   -eq 0 ] || { echo "  + added upstream, not vendored ($n_added):"; printf '      %s\n' $added; }
[ "$n_removed" -eq 0 ] || { echo "  - vendored, absent at pin ($n_removed):";     printf '      %s\n' $removed; }
[ "$n_changed" -eq 0 ] || { echo "  ~ same name, different bytes ($n_changed):";  printf '      %s\n' $changed; }

total=$(( n_added + n_removed + n_changed ))

if [ "$check_only" -eq 1 ]; then
    echo
    if [ "$total" -eq 0 ] && [ "$pin_moves" -eq 0 ]; then
        echo "OK: the vendored corpus is byte-identical to pin $target_pin, and the pin is unchanged."
        exit 0
    fi
    if [ "$total" -eq 0 ]; then
        echo "OK: the vendored bytes already match $target_pin; only the recorded pin would move."
        echo "    Re-run without --check to write it."
        exit 1
    fi
    echo "DRIFT: $total file(s) differ between the vendored corpus and $target_pin."
    if [ "$pin_moves" -eq 0 ]; then
        echo "       The pin did not move, so this is a LOCAL EDIT to vendored bytes — that is a"
        echo "       cross-repo drift event. Do not 'fix' it by re-vendoring until you know why."
    else
        echo "       Re-run without --check to re-vendor at $target_pin."
    fi
    exit 1
fi

# --- write -------------------------------------------------------------------

if [ "$total" -eq 0 ] && [ "$pin_moves" -eq 0 ]; then
    echo
    echo "Nothing to do: already pinned to $target_pin and byte-identical to it."
    exit 0
fi

# Replace the directory wholesale rather than copying over it, so a file deleted
# upstream is deleted here too. An additive-only re-vendor would leave a phantom
# vector in the corpus, and the set comparison in ci.yml would then report it as
# "vendored vector(s) absent upstream" — a true error with a misleading cause.
rm -rf "$vendor_dir"
cp -R "$src_dir" "$vendor_dir"

# One line, chosen by anchor rather than by line number so a comment added above
# it cannot silently move the write.
tmp_lock="$work/VECTORS.lock"
awk -v pin="$target_pin" '
    !done && /^# Pinned commit:/ { print "# Pinned commit: " pin; done = 1; next }
    { print }
    END { if (!done) exit 3 }
' "$lock_file" > "$tmp_lock" || die "VECTORS.lock has no '# Pinned commit:' line to rewrite"
cp "$tmp_lock" "$lock_file"

# --- re-verify what was just written -----------------------------------------

hex_count=$(grep -coE '[0-9a-f]{40}' "$lock_file" || true)
read_back="$(grep -oE '[0-9a-f]{40}' "$lock_file" | head -1)"

if [ "$hex_count" -ne 1 ]; then
    die "VECTORS.lock now contains $hex_count 40-hex strings; ci.yml:75 takes the FIRST one and would be ambiguous. Fix by hand."
fi
if [ "$read_back" != "$target_pin" ]; then
    die "VECTORS.lock reads back as $read_back, not $target_pin. Fix by hand."
fi
if ! diff -r "$src_dir" "$vendor_dir" >/dev/null; then
    die "the vendored tree is not byte-identical to the archive it came from. Fix by hand."
fi

echo
echo "OK: re-pinned $current_pin -> $target_pin"
echo "    $(ls "$vendor_dir" | wc -l) vector files vendored; VECTORS.lock reads back as the new pin."
cat <<MSG

NEXT, and none of it is automatic:

  1. Add a dated note to VECTORS.lock saying WHY this pin. The script rewrote the
     pin line only; the prose above and below it still describes the old one.
  2. Run :core's tests — the vectors are only vendored, not yet proven against
     this phone's codec:
         scripts/core-probe.sh
     and the full gate on a machine with the Android SDK:
         ./gradlew --no-daemon checkCoreIsAndroidFree :core:test :app:test \\
             :app:assembleDebug :app:lintDebug --rerun-tasks
  3. Commit the corpus and the lock together. They are one unit; a pin that moves
     without its bytes, or bytes that move without their pin, is exactly the
     drift this file exists to make impossible.
MSG
