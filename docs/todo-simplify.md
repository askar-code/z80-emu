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
- [x] 2. `cpu-z80` — cpu/z80 (~3.8k lines) — DONE 2026-07-14
- [x] 3. `cpu-6502-i8080` — cpu/mos6502 + cpu/i8080 (~4.4k lines) — DONE 2026-07-14
- [x] 4. `machine-spectrum` — machines/spectrum (~3.9k lines) — DONE 2026-07-14
- [x] 5. `machine-rk` + `machine-cpc` — two areas in one run (~4.6k lines) — DONE 2026-07-14
- [x] 6. `apple2-core` — machines/apple2 minus disk/ (~3k lines) — DONE 2026-07-14
- [ ] 7. `apple2-disk` — machines/apple2 disk subsystem (~4k lines)
- [ ] 8. `app-desktop` — apps/desktop (~5.9k lines)
- [ ] 9. `cross-reuse` + `cross-altitude` — whole repo, run last

## Deferred findings

(none yet)

## Tranche log

### Tranche 6: `apple2-core` (machines/apple2, non-disk) — 2026-07-14

- Review: 16 confirmed / 0 rejected → 8 unique (record convergence: all four
  angles independently found the per-frame glyph rebuild).
- Applied: 8/8 via Codex. Headliners: static GLYPHS table (was ~60k
  allocations + ~400k string-char parses per second of text rendering);
  framebuffer + NTSC scratch reuse (was a fresh 215 KB buffer per frame);
  dead luma filter removed from the NTSC builder; auxMemory-null convention
  and 4-arg renderFrame overload removed; dead ROM-size policy / language
  card overloads / frame dimension fields deleted; C800-CFFF bounds unified.
- Codex ran its own deterministic pre/post frame CRCs in all six video
  modes (text/lo-res/mixed-lores/hi-res/mixed-hires/DHGR) — all matched.
  It also correctly adapted the one test that held two frames across the
  now-reused buffer (reorder only; equal assertion strength, verified).
- Review gates: agent diff review 6/6 CLEAN — proved glyph index covers all
  256 screen bytes, NtscFilter.filter mutates only its own state, no
  renderFrame caller retains the buffer, II Plus aux path identical
  (installed()==false ≡ old null), C800 exclusivity conventions matched.
- Verification on main: `./gradlew build` + all three Apple II smokes
  (BASIC, PoP frame CRC, ProDOS system banner) green. @Test 46→46.

### Tranche 5: `machine-rk` + `machine-cpc` — 2026-07-14

- Review: 38 confirmed / 0 rejected → 23 unique (RK 11, CPC 12). Split as an
  A/B experiment: RK applied BY HAND in the main checkout, CPC delegated to
  Codex in a worktree — same findings pipeline, different apply route.
- RK (hand, 11/11, commit 4590e2b): allocation-free video decode path,
  writeCommand switch, single attribute row, IoTraceSink-based bus tracing,
  shared Radio86RomLocator + Radio86MonitorConsole (deleted duplicates in
  test driver and desktop probe launcher), DMA-derived screen scrape
  (readVideoByte deleted), JDK readAllBytes, BooleanSupplier, batched test
  predicate polling. Gate: deterministic fixed-instruction frame-CRC
  differential vs HEAD (three checkpoints, bit-identical; the one observed
  diff traced to the sanctioned batched-polling stop-point shift).
- CPC (Codex, 12/12, merge f34ed4c): FDC int ring buffers (unwrap-on-grow
  proven order-preserving), dataLength()/single data() fetch,
  queueSectorResult, resumable eventIndexAt, border strip painting with run
  fills, per-frame CRTC parameter cache (proven a pure snapshot — renderFrame
  has no interleaved register writes), dead surface removals, floorMod
  collapse (proven for negative t-states), HUD test helper. Gate: dedicated
  adversarial diff-review agent, 6/6 categories CLEAN; @Test 23→23.
- Token economics (Claude-side, estimates): hand route ≈ 35–40k context
  tokens for 11 fixes with no separate review agent (differential gate
  instead); Codex route ≈ 10k context + 89k review-agent tokens for 12
  fixes (plus external OpenAI compute). On a small well-specified module the
  hand route is ~2x cheaper in Claude tokens; Codex's fixed overhead
  (spec + adversarial review) pays off on bigger/gnarlier batches (e.g.
  tranche 3's 450-line 6502 diff) and runs fully parallel to other work.
- IMPORTANT caveat (not captured by the token table): Codex is also an
  independent CROSS-MODEL validation layer — it re-reads the code before
  every fix and uses its SKIP right (caught the F-13 dummy-operand
  overgeneralization in tranche 3 and the F-14 package-visibility blocker
  in tranche 4; ~1-2 verifier corrections per ~12-fix batch). The hand
  route is Claude end-to-end (finder→verifier→applier), so correlated
  misreadings survive unless an EMPIRICAL gate (CRC differential, zex,
  pixel baseline) covers the change. Route choice rule: hand-apply only
  when a strong empirical gate exists or the compiler trivially catches
  errors; delegate timing/flag-critical changes without such a gate.
- Process note: hit the known merge-inside-worktree no-op pitfall; re-merged
  from the main checkout.
- Verification: `./gradlew build` green on main after both halves.

### Tranche 4: `machine-spectrum` (machines/spectrum) — 2026-07-14

- Review: 23 confirmed / 0 rejected → 14 unique. The headline cluster
  (three angles converged): Spectrum48kBus/Spectrum128Bus ~90-line
  duplication → new `SpectrumBusBase`; per-model timing moved into
  SpectrumModelConfig; ULA's `frameTStates == 70_908` machine-sniff
  replaced by explicit floatingBusDisplayStartTState injection.
- Applied: 13 fully + F-14 partial (pulse literals stay in the test —
  package visibility; duplicate helpers deleted). Net −165 lines.
- Review gates: dedicated-agent old-vs-new sweep (5 categories CLEAN;
  proved the 48K priority 0→90 and 128K registration-order changes
  neutral, and that captureDisplayUntil's old min() was a no-op);
  hand-verified paintBorderBackground 3-loop split and
  syncAndReadEarLevel boolean equivalence.
- Pixel gate: STRML128 headless probe (100/250/400M t-states), PNG MD5
  sets identical pre/post merge; probe determinism pre-verified with a
  double run. Machine-state summaries identical field-by-field.
- Verification: `./gradlew build` green (worktree + main), Robocop E2E
  in-suite. @Test counts 6/4/22 unchanged.

### Tranche 3: `cpu-6502-i8080` (cpu/mos6502 + cpu/i8080) — 2026-07-14

- Review: 22 confirmed / 0 rejected → 13 unique after dedup. Two finding
  conflicts resolved in the spec: INC/DEC helpers written lambda-free
  (the readModifyWrite variant would have contradicted the method-ref
  allocation finding), and the IndexedAddress→int encoding ordered first
  so handler routing was written against it.
- Applied: 12 fully + F-13 partial (6/11 NMOS-illegal tests converted;
  5 with distinct dummy-operand programs deliberately left). Net −239
  lines. Merged as the tranche-3 merge commit.
- Hot-path allocations removed: IndexedAddress record per abs,X/abs,Y/
  (zp),Y access; IntUnaryOperator method refs in RMW dispatch;
  DecimalResult per decimal ADC.
- Review gates: exhaustive handler-by-handler diff review by a dedicated
  agent (all 30 packed-address use sites, all cycle counts incl. fixed-7
  RMW abs,X, all 22 65C02 gate literals, 20+ sampled test conversions —
  zero discrepancies); flag-normalization invariants verified by hand in
  both register files; decimalAdd carry-before-adjust preserved.
- Verification: `./gradlew build` green in worktree and main;
  apple2BasicSmoke + apple2SuperDrivePopSmoke (frame CRC) green on main.
  @Test 77→77.

### Tranche 2: `cpu-z80` (cpu/z80) — 2026-07-14

- Review: 30 raw findings → 29 confirmed / 1 rejected; 11 unique after dedup
  (all four angles converged on the same core spots: compare8/subtract8,
  LD A,I/R, SZP flag helpers, repeating-block epilogue).
- Applied: 11/11 via Codex, 0 skipped, 0 deferred. Merged as 988128d.
  Net −92 lines in the Z80 core.
- Verified bit-identity by hand for the coverage-thin spots: ED 70/71
  routing (writeRegisterOperand code-6 no-op, outRegisterToPortC code-6→0),
  executeHaltCycle body match, subtract8 flag block vs deleted compare8,
  0x40-0x7F divert guards; ED 70/71 also covered by Z80CpuTest:608.
- Verification: `./gradlew build` + `:cpu-z80:zexTest` (zexdoc+zexall)
  green in worktree and again in main after merge.

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
