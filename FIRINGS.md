# FIRINGS — one line per no-change firing

**House law from run 118 (2026-08-28).** A firing whose `scripts/run-zero.sh` verdict is
`NOTHING MOVED` **and** whose five escalation triggers are all negative appends **one line here**
and writes to **none** of `STATE.md`, `LOG.md`, `BLOCKED.md`, `AUDIT-REQUEST.md`.

A firing that finds **anything** — a moved `main`, a PR merged or undrafted, a changed stored
prompt, an executed gate, or a genuine new finding about the product, the protocol or the board —
writes a full entry in the four records **exactly as before**. This file is for the empty firings
only, and it must never become a place to record a real one cheaply.

Generate the line; do not hand-write it:

```bash
scripts/firing-line.sh <run> ../careerseeker <engine-open> <android-open> <esc-ledger> '<note>'
```

The line belongs **inside the fenced ledger block at the end of this file**, under the last run.
A bare `>> FIRINGS.md` appends *after* the closing fence and drops the line out of the block —
run 122 did exactly that and had to undo it. Print the line, read it, then insert it.

Every field except the two board counts is derived from `run-zero.sh`'s own output, so a line
cannot claim a state the probe did not report. The board counts come from the GitHub API, which no
shell script here can reach (`run-zero.sh` §6), and the script refuses to invent them.

## Why this file exists — the measurement that produced it

Six attempts are logged against **B-18**. Attempts 3–6 each lowered a firing's **read** cost: the
banner at run 48, the out-of-repo escalation at run 53, `run-zero.sh` at run 98. **None touched the
write cost**, and run 118 measured it for the first time (**C-118-3**). Lines added to the four
records per no-change firing:

| Run | STATE | LOG | BLOCKED | AUDIT | Total |
| --- | --- | --- | --- | --- | --- |
| 111 | 46 | 102 | 49 | 130 | **327** |
| 112 | 50 | 100 | 40 | 131 | **321** |
| 113 | 60 | 112 | 60 | 148 | **380** |
| 114 | 44 | 124 | 54 | 170 | **392** |
| 115 | 37 | 107 | 51 | 141 | **336** |
| 116 | 49 | 109 | 55 | 148 | **361** |
| 117 | 49 | 113 | 59 | 134 | **355** |

Median **355**. The four records stood at **50,862 lines** when this was written, and the schedule
fired **five times** on 2026-08-28 — roughly **1,700 lines a day** restating a state that has not
changed since 2026-08-13.

The cost is not merely wasteful, it is **self-defeating**: every 355 lines added lengthens the next
session's read, which is the exact cost `run-zero.sh` was built to remove. The two mitigations were
working against each other, and that is the finding. This file ends that.

**What it does not do.** It does not lower the firing **count** — only a human stopping the schedule
does that (**B-18**'s smallest unblock, unchanged). It does not make an empty firing useful. And a
session that ignores this rule and writes a 355-line entry anyway is not prevented from doing so;
nothing here is enforced, it is only written where the reader looks.

---

## Ledger

Run 118 is the first firing to follow the rule. Earlier runs are **not** back-filled as lines —
their evidence is in the four records, and inventing probe output for a run that never ran the probe
would be exactly the fabrication these records exist to prevent. Their write cost is the table above.

```
118 | 2026-08-28 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1052/1053/1 | board 22+6 open | esc 11 | declined: S5 spec half, 83rd
119 | 2026-08-28 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 84th; attempt 7 first trial
120 | 2026-08-29 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 85th
121 | 2026-08-29 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 86th
122 | 2026-08-29 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 87th; slice re-verified from the three commits
123 | 2026-08-29 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 88th
124 | 2026-08-29 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 89th; slice re-verified from the three commits diffs
125 | 2026-08-29 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 90th; slice re-verified from the spec body + both A2 amendments
126 | 2026-08-30 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 91st; slice re-verified by running generate.mjs --check at the pin
127 | 2026-08-30 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 92nd; board re-verified via MCP (28 open, all draft, newest merge still #44 2026-08-13)
128 | 2026-08-30 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 93rd; board re-verified via MCP (28 open, all draft, newest merge still #44 2026-08-13); send predicate unmet -- next defensible date on/after 2026-09-01
129 | 2026-08-30 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 94th; board re-verified via MCP (28 open, all draft, newest merge still #44 2026-08-13); send predicate unmet -- 3 of 5 calendar days since run 112, next defensible date on/after 2026-09-01
130 | 2026-08-30 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 95th; slice re-verified by hand -- §4.3.3 body, §3.1 ciphertext cap, §7.2 decrypt_failed, invalid-unknown-field.json, generate.mjs --check OK 29/29; board re-verified via MCP (28 open, all draft, newest merge still #44 2026-08-13); send predicate unmet -- 3 of 5 calendar days since run 112, next defensible date on/after 2026-09-01
131 | 2026-08-30 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 96th; slice re-verified by hand -- §4.3.3 body, §3.1 ciphertext cap, §7.2 decrypt_failed, invalid-unknown-field.json, generate.mjs --check OK 29/29 exit 0; board re-verified via MCP (28 open, all draft, newest merge still #44 2026-08-13); Play targetSdk floor 36 live 2026-08-31 -- app declares 37, compliant, not a finding; send predicate unmet -- 3 of 5 calendar days since run 112, next defensible date on/after 2026-09-01
132 | 2026-08-31 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 97th; slice re-verified via run-zero s1 (all three commits off-main) + generator check at pin OK 29/29; board re-verified via MCP (28 open, all draft:true, newest merge still #44 2026-08-13); prompt unchanged -- carries all three known stalenesses (pin 679a317, S5 NOT STARTED, /pair page absent though #42 merged); send predicate unmet -- 4 of 5 calendar days since run 112, next defensible date on/after 2026-09-01
133 | 2026-08-31 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 98th; board re-verified via MCP (28 open, all draft:true, newest merge still #44 2026-08-13); prompt unchanged -- same two stale facts (pin 679a317, S5 NOT STARTED), 23rd day; send predicate unmet -- 4 of 5 calendar days since run 112, next defensible date on/after 2026-09-01
134 | 2026-08-31 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 99th; board re-verified via MCP (22 engine + 6 android open, all draft:true, newest merge still #44 2026-08-13); prompt unchanged -- same stale facts (pin 679a317, S5 NOT STARTED, /pair page absent though #42 merged), 24th day; send predicate unmet -- 4 of 5 calendar days since run 112 sent on 2026-08-27, next defensible date on/after 2026-09-01, i.e. the next firing
135 | 2026-08-31 | NOTHING MOVED | pin 7328a0b | corpus 29/29 | gen OK | mains aac05f3/ebfaf81 | cites 1054/1055/1 | board 22+6 open | esc 11 | declined: S5 spec half, 100th; board re-verified via MCP (22 engine + 6 android open, all draft:true, newest merge still #44 2026-08-13); prompt unchanged -- same stale facts (pin 679a317, S5 NOT STARTED, /pair page absent), 25th day; Play targetSdk floor 36 went live today, app declares 37, compliant -- not a finding; send predicate unmet by one day -- run 112 sent 2026-08-27, next defensible date on/after 2026-09-01
```
