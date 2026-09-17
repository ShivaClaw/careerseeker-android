#!/usr/bin/env bash
#
# b7-probe.sh — B-7, measured against the hosts the build actually resolves.
#
# WHY THIS EXISTS
#
# Run 46 recorded C-ENV-1 under the headline "the android gate's absence is
# measured here, not assumed". Its one network measurement was:
#
#   curl -s -o /dev/null -w "%{http_code}\n" https://dsl.maven.google.com/ --max-time 15
#
# reported as `000` for "Google's Maven host — unreachable", and that command sat
# in AUDIT-REQUEST.md as B-7's re-verification command for 196 firings.
#
# THERE IS NO SUCH HOST. `dsl.maven.google.com` has no DNS record (run 242
# measured it two ways: getent and getaddrinfo both fail to resolve it). A name
# that does not resolve returns `000` whether the egress policy denies Google
# Maven or allows it, so that command cannot distinguish "denied" from "typo",
# and it would keep printing `000` — reading as "B-7 unchanged" — on the day the
# policy is widened. It is a self-confirming probe: the strongest form of the
# defect class this house keeps finding (C-227-1, C-238-2, C-239-1, C-240-1,
# C-241-1), and the first instance that was never valid rather than gone stale.
#
# B-7 ITSELF IS REAL. That is the part run 46 got right, and this script
# re-measures it first-person rather than inheriting it.
#
# THE TRAP THIS SCRIPT EXISTS TO PRINT
#
# The naive correction — point the probe at `maven.google.com` — produces a
# WORSE wrong answer than the typo did. `maven.google.com` answers **HTTP 301**
# at its host root, so a root probe reads "reachable" and a firing could conclude
# Google Maven is open. It is not: the 301 redirects into
# `dl.google.com/dl/android/maven2/...`, which is the host the egress policy
# denies, so every actual ARTIFACT fetch dies there with 403 CONNECT.
#
# Therefore this probe tests an ARTIFACT PATH, not a host root, and it tests both
# Google hosts plus a reachable control. A probe of a host root is not a probe of
# a repository.
#
# WHAT THIS IS NOT
#
# This is NOT a gate and it builds nothing. It answers exactly one question —
# "can this sandbox fetch the Android toolchain?" — and it does not route around
# a denial. /root/.ccr/README.md is explicit that a 403/407 from the proxy is an
# organization egress policy decision, to be reported and not worked around.
#
# USAGE
#   scripts/b7-probe.sh
#
# Exit 0 — B-7 HOLDS: Google Maven artifacts are unreachable (expected here).
# Exit 1 — B-7 MAY HAVE LIFTED: an artifact came back. Re-derive before relying
#          on it, then tell the owner — this is the trigger that would make the
#          android gate runnable in the cloud for the first time.

set -uo pipefail

# The AGP the build pins, and therefore the exact artifact Gradle's google()
# repository must fetch before :app can configure at all.
#
# B7_BASES / B7_ARTIFACT_PATH exist ONLY to replay the exit-1 ("B-7 lifted") arm
# against a repository that IS allowed, so the detector is proven in both
# directions rather than assumed to work on the day it matters:
#   B7_BASES=https://repo1.maven.org/maven2 \
#   B7_ARTIFACT_PATH=org/jetbrains/kotlin/kotlin-stdlib/2.0.0/kotlin-stdlib-2.0.0.pom \
#     scripts/b7-probe.sh      # expect: HTTP 200 and exit 1
# Never use them to "measure" B-7 itself — the defaults below are the question.
AGP_PATH=${B7_ARTIFACT_PATH:-com/android/tools/build/gradle/9.3.0/gradle-9.3.0.pom}
read -r -a BASES <<<"${B7_BASES:-https://dl.google.com/dl/android/maven2 https://maven.google.com}"

echo "b7-probe — can this sandbox fetch the Android toolchain?"
echo

echo "== 1. DNS — does the name the records name even exist?"
for h in dsl.maven.google.com dl.google.com maven.google.com repo1.maven.org; do
  if getent hosts "$h" >/dev/null 2>&1; then
    printf '  %-24s RESOLVES\n' "$h"
  else
    printf '  %-24s NO DNS RECORD\n' "$h"
  fi
done
cat <<'NOTE'

  dsl.maven.google.com is EXPECTED to have no record: it is run 46's typo, kept
  here so the difference between "does not exist" and "is denied" stays visible.
  A 000 from it is meaningless. That is C-242-1.

NOTE

echo "== 2. Host roots — and why this line is NOT the answer"
for h in dl.google.com maven.google.com; do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 25 "https://$h/" 2>/dev/null)
  printf '  %-24s HTTP %s\n' "$h" "$code"
done
cat <<'NOTE'

  maven.google.com reads 301 here. Do NOT record that as "Google Maven is
  reachable" — the redirect target is the denied host. Section 3 is the answer.

NOTE

echo "== 3. The artifact path — the question that actually decides B-7"
rc=0
for base in "${BASES[@]}"; do
  out=$(curl -sS -L -o /dev/null \
        -w 'HTTP %{http_code}  bytes=%{size_download}  final=%{url_effective}' \
        --max-time 40 "$base/$AGP_PATH" 2>&1)
  printf '  %s\n    %s\n' "$base/$AGP_PATH" "$out"
  case "$out" in
    HTTP\ 200*) rc=1 ;;
  esac
done

echo
echo "== 4. Control — a repository that IS allowed, so a failure above means something"
# B7_CONTROL_URL replays the failed-control arm without waiting for a real
# outage, in the same idiom as run-zero.sh's RUNZERO_JVM_DIR hook. Point it at a
# denied host to see the 000 branch fire:
#   B7_CONTROL_URL=https://dl.google.com/ scripts/b7-probe.sh
CONTROL_URL=${B7_CONTROL_URL:-https://repo1.maven.org/maven2/}
ctl=$(curl -s -o /dev/null -w '%{http_code}' --max-time 25 "$CONTROL_URL" 2>/dev/null)
printf '  %s  HTTP %s\n' "$CONTROL_URL" "$ctl"
# ANY http status means the CONNECT tunnel OPENED — including 429. You cannot be
# rate-limited by a server you never reached, and Maven Central rate-limits this
# egress often (run 241 hit 429 on kotlin-gradle-plugin and went green on retry).
# Only 000 is "no HTTP response at all", which is what a policy denial looks like.
# Reading 429 as a failed control is the same error class as C-242-1 itself:
# treating a code you did not think about as the code you were testing for.
if [ "$ctl" = "000" ]; then
  echo "  WARNING: the control got NO HTTP RESPONSE. The denials above may be a"
  echo "  general network fault this run, not the Google-specific policy B-7 names."
  echo "  Re-run before recording anything."
else
  echo "  control reached (any status, 429 included, means the tunnel opened), so"
  echo "  section 3's 000s are specific to the Google hosts — which is B-7."
fi

echo
echo "== VERDICT"
if [ "$rc" -eq 0 ]; then
  cat <<'NOTE'
  B-7 HOLDS — no Google Maven artifact was retrievable. AGP cannot resolve, so
  :app cannot configure, so :app:assembleDebug and :app:lintDebug are NOT
  runnable here and no android gate result may be claimed.

  This is an ORGANIZATION EGRESS POLICY decision (403 on CONNECT), per
  /root/.ccr/README.md. Do not retry it and do not route around it. It is NOT
  the same condition as JDK17(:core) being absent, which is one apt away.
NOTE
else
  cat <<'NOTE'
  B-7 MAY HAVE LIFTED — an artifact returned HTTP 200. Re-derive from scratch
  before acting: run the five-task android command and see whether it configures.
  If it does, this is the first time the android gate has been runnable in the
  cloud, and it is worth telling the owner.
NOTE
fi
exit "$rc"
