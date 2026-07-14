# Simplify sweep — tranche queue and deferred findings

Whole-repo quality review (reuse / simplification / efficiency / altitude) run
in tranches via `.claude/workflows/simplify-tranche.js`. One tranche = one
Workflow invocation, triggered explicitly with a token budget
(e.g. «транш 1 +300k»). Confirmed `safe` findings are applied immediately
(large mechanical batches go through Codex delegation); `risky` ones get an
extra gate or land here as deferred items. After each tranche: full
`./gradlew test`, commit, update this file.

## Tranche queue

- [x] 1. `platform` — platform/core + chips/ay (~1.7k lines) — DONE 2026-07-14
- [ ] 2. `cpu-z80` — cpu/z80 (~3.8k lines)
- [ ] 3. `cpu-6502-i8080` — cpu/mos6502 + cpu/i8080 (~4.4k lines)
- [ ] 4. `machine-spectrum` — machines/spectrum (~3.9k lines)
- [ ] 5. `machine-rk` + `machine-cpc` — two areas in one run (~4.6k lines)
- [ ] 6. `apple2-core` — machines/apple2 minus disk/ (~3k lines)
- [ ] 7. `apple2-disk` — machines/apple2 disk subsystem (~4k lines)
- [ ] 8. `app-desktop` — apps/desktop (~5.9k lines)
- [ ] 9. `cross-reuse` + `cross-altitude` — whole repo, run last

## Deferred findings

(none yet)

## Tranche log

### Tranche 1: `platform` (platform/core + chips/ay) — 2026-07-14

- Review: 16 raw findings → 15 confirmed / 1 rejected by adversarial verify;
  11 unique after cross-finder dedup (fillRow and FixedSlotMemoryMap accessors
  found twice; AY hot-path fix found by two angles; IoAccess write fix merged
  from reuse+efficiency halves).
- Applied: 11/11 via Codex (gpt-5.6-sol xhigh) in worktree, 0 skipped,
  0 deferred. Merged as 05b2a6c.
- Extra gates: differential ring-buffer test vs old per-byte semantics
  (20k+300 rounds, two capacities) for the drainAudio arraycopy rewrite;
  AY register-read change verified bit-identical by diff review.
- Verification: `./gradlew build` green (all modules), apple2BasicSmoke and
  apple2SuperDrivePopSmoke (frame CRC) green.
- Note: a Gradle daemon started under the tool sandbox poisons later builds
  in the main checkout (`fileHashes.lock: Operation not permitted`) —
  `./gradlew --stop`, then rebuild.
