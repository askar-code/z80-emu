# Commodore 64 Platform Plan

Working contract for adding a Commodore 64 (PAL) machine. Delivery model:
per-phase ТЗ is derived from this plan, implementation is delegated to Codex
in worktree batches (`codex/c64-pN-*`), every phase ends with an adversarial
diff review plus an executable gate run on main. Keep this file updated as
phases land. (Rewritten 2026-07-14 after a foundation audit; the original
checklist predated the `cpu-mos6502` module, which now exists and is
battle-tested by the Apple II machines.)

## Status Legend

`[ ]` not started · `[~]` in progress · `[x]` done

## Already in place (audited, do not re-plan)

- `:cpu-mos6502` — NMOS/65C02 variants (`Mos6502Variant`, ctor-selected).
  All documented opcodes with page-cross/branch cycle adjustments;
  `runInstruction()` returns consumed cycles. IRQ/NMI via
  `requestMaskableInterrupt()` / `clearMaskableInterrupt()` /
  `requestNonMaskableInterrupt()`; `serviceInterrupt` pushes PC + status
  (B clear), sets I, reads FFFE/FFFA, costs 7 cycles. NMI has priority and
  is edge-latched.
- Level-triggered IRQ delivery: `MachineRuntime.runInstruction()` polls
  `MachineBoard.maskableInterruptLineActive(t)` after every instruction and
  re-asserts/clears the CPU pending flag. C64 board ORs CIA1 + VIC-II lines.
  Precedents: Spectrum ULA (stateless, derived from frame offset), CPC Gate
  Array (latched + ack).
- Desktop/probe infrastructure is shared and thin after the simplify sweep:
  non-generic `AbstractFrameDesktopSession` + `bindKeyboardController`,
  `AbstractMappedHostKeyboardController<K>` (implement `keysFor`/`updateKey`),
  `ProbeOutput`, `CrashReportWriter`, `--expect-screen`/`--expect-frame-crc`
  smoke pattern with exit-code pass/fail (template: `apple2BasicSmoke`).
- Audio contract: `ClockedPcmMonoSource(sourceClockHz, sampleRate)` with one
  abstract `short nextPcmSample()`, ticked from the board's
  `onTStatesElapsed`; drained by the desktop engine (Beeper/AY/Apple II
  speaker precedents).

## Known gaps the plan must close (from the audit)

- **No NMI plumbing** in `MachineBoard`/`MachineRuntime` — nothing calls
  `requestNonMaskableInterrupt()`. Closed in Phase 1.
- **6502 core never calls `bus.acknowledgeInterrupt()`** (Z80-only path) —
  so CIA "ICR read clears IRQ" and VIC "write $D019 clears raster IRQ" MUST
  live in the device register handlers; the board line just reflects
  latched device state. (Design consequence, not a platform change.)
- **No RDY/DMA-stall hook in the CPU** — VIC-II badline cycle stealing has
  no CPU-side mechanism. Deferred; first lands as board-level clock skew
  (extra t-states in `onTStatesElapsed`), documented approximation.
- **Illegal NMOS opcodes throw `IllegalStateException`** — good loud
  diagnostics for bring-up; stable illegals (LAX/SAX/DCP/...) are a
  backlog phase, needed by many games/demos.
- **Decimal mode is textbook BCD, not bug-exact NMOS** (V from binary
  result, N/Z from adjusted result; invalid-BCD nibbles not NMOS-quirky).
  Fine for BASIC/KERNAL; the Klaus functional test may fail its decimal
  section — contingency in Phase 0.
- **No 6502 functional-test harness** (Z80 has zexdoc/zexall). Closed in
  Phase 0 with Klaus Dormann's test, modeled on `ZexHarness` + the
  tag-excluded `zexTest` Gradle task pattern.
- 6510 on-chip port ($00/$01) is not a CPU concern — implemented as a board
  bus intercept.

## Global decisions (sanctioned, do not re-litigate per phase)

- **PAL only** for now: 985 248 Hz CPU clock, 63 cycles/line × 312 lines =
  19 656 t-states/frame (~50.12 Hz). 1 t-state = 1 PHI2 cycle.
- **NMOS 6510** = `Mos6502Cpu` with `Mos6502Variant.NMOS_6502`.
- **No cartridge port** in v1: EXROM/GAME inactive; PLA table covers the 8
  LORAM/HIRAM/CHAREN combinations only.
- **Memory-mapped I/O dispatch**: PLA decides whether $D000–$DFFF is I/O,
  CHAR ROM, or RAM in `C64Bus`; when I/O, register dispatch goes through a
  memory-mapped `IoAddressSpace` (Radio-86RK precedent — buys IoTraceSink
  debugging for free during bring-up): VIC $D000–$D3FF mirror, SID
  $D400–$D7FF mirror, color RAM $D800–$DBFF, CIA1 $DC00–$DCFF mirror,
  CIA2 $DD00–$DDFF mirror, IO1/IO2 open-bus.
- **Full-frame snapshot rendering** first (Radio-86RK / Apple II pattern):
  `renderVideoFrame()` renders once per frame from current state. The
  raster counter ($D012 + $D011.7) is timeline-derived from the master
  clock, so raster polling and raster IRQs work even though rendering is
  per-frame. Cycle-exact per-line rendering, badlines, and mid-frame
  register splits are OUT until a target program needs them.
- **ROM files** (user-provided, in the gitignored `media/` directory,
  never committed): `basic.901226-01.bin` (8K), `kernal.901227-03.bin` (8K),
  `characters.901225-01.bin` (4K — actual local filename; the chip is often
  distributed as `chargen.901225-01.bin`). Loader accepts a directory
  argument (`media`) plus `-Dc64.basicRom= / -Dc64.kernalRom= /
  -Dc64.chargenRom=` overrides, following `Apple2RomImageLoader`
  conventions.
- Frame geometry: 384×272 visible PAL window (borders included), 320×200
  text window centered; exact numbers frozen in Phase 2 for CRC baselines.
- PETSCII→ASCII screen scrape for `--expect-screen` (as landed in
  Phase 2): strip bit 7 (reverse video), then 0x00 → '@', 0x01–0x1A →
  A–Z, the whole 0x20–0x3F block passes through as ASCII (space, digits,
  punctuation — the boot banner needs '*'), rest '.'.

## Phase queue

- [x] 0. Module skeleton, memory + PLA + 6510 port, Klaus gate
- [x] 1. CIA 6526 pair + NMI platform plumbing
- [x] 2. VIC-II text mode + raster + boot to READY. (headless probe)
- [ ] 3. Keyboard matrix + desktop runner + BASIC smoke
- [ ] 4. PRG loading (probe option + desktop media)
- [ ] 5. SID minimal (3 voices, ADSR, no filter) through PCM
- [ ] 6. Accuracy/games backlog (illegals, badlines, sprites, bitmap, tape/disk)

---

### Phase 0: skeleton + memory/PLA + Klaus gate

New Gradle project `:machine-c64` (`machines/c64`), api :emu-platform +
:cpu-mos6502 (mirror `machines/apple2/build.gradle.kts`).

- `C64Memory`: 64K RAM; BASIC ROM $A000–$BFFF; KERNAL $E000–$FFFF; CHAR ROM
  $D000–$DFFF when banked in; 1K color RAM nibbles (separate array; upper
  nibble reads — pick a convention in the ТЗ and freeze it); writes under
  ROM always hit RAM.
- 6510 port intercept at $0000/$0001: DDR + data, bits 0–2
  LORAM/HIRAM/CHAREN with pull-ups on input bits, bits 3–5 datasette stubs;
  reset state $2F/$37.
- `C64Bus implements CpuBus` + `C64Board implements VideoMachineBoard`
  (renderVideoFrame stubbed black frame for now); PLA banking decode; the
  I/O window via memory-mapped `IoAddressSpace` with stub open-bus devices.
- `C64Machine implements BoardBackedMachine<C64Board>` on the standard
  five-machine scaffold (TStateCounter → board → cpu(NMOS) → runtime).
- **Klaus Dormann functional test**: `Functional6502Harness` in
  cpu/mos6502 tests (flat-64K bus, load binary at $0000, PC=$0400, loop
  until PC self-loop; success PC asserted, any other trap reports PC),
  tag-gated Gradle task `:cpu-mos6502:klausTest` (zexTest pattern), binary
  gitignored in `media/`. Contingency: if the decimal section fails on
  textbook-BCD flags, fix `decimalAdd`/`decimalSubtract` NMOS flag
  semantics in the core (preferred) rather than assembling a
  decimal-disabled binary.
- Tests: full 8-row PLA truth table (per-region read source per combo),
  write-under-ROM, $00/$01 DDR semantics, reset defaults, color RAM width.
- Gate: `:machine-c64:test` + `:cpu-mos6502:klausTest` green.

Landed 2026-07-15 (Codex batch `codex/c64-p0-skeleton`, adversarially
reviewed + fault-injected; merge `3a4527d`). Klaus baseline: success trap
$3469 after 30 646 177 instructions / 96 241 367 t-states — the decimal
contingency did NOT trigger (the default Klaus build checks documented BCD
behavior only, which the textbook core satisfies); the NMOS flag fix stays
dormant in the backlog. Frozen Phase 0 conventions recorded: color RAM
reads return the low nibble with upper nibble 0; CPU writes to $0000/$0001
leave RAM[0]/RAM[1] untouched (real hardware deposits the VIC phi1 open-bus
byte — revisit when the VIC exists, Phase 2); 6510 port bits 6/7 capacitor
fade not modeled (inputs without pull-ups read 0 immediately).

### Phase 1: CIA 6526 ×2 + NMI plumbing

- **Platform (minimal, machine-agnostic)**: add
  `default boolean nonMaskableInterruptLineActive(long t) { return false; }`
  to `MachineBoard`; `MachineRuntime` edge-detects it (falling→rising edge
  calls `cpu.requestNonMaskableInterrupt()` once). Unit-test in platform.
- Single `C64CiaDevice` class, two instances. Ports A/B with DDR mixing;
  timers A/B: 16-bit down-counters, latch reload, one-shot/continuous,
  start/stop/force-load, B-counts-A-underflows cascade; ICR read-clears
  (and drops the line), write with bit 7 sets/clears mask; underflow raises
  the line when masked in. TOD minimal (frame-driven), serial register
  stub — note stubs explicitly.
- Clocked from `onTStatesElapsed` (1 cycle = 1 t-state), allocation-free.
- Board: `maskableInterruptLineActive` = CIA1 line (VIC ORs in at Phase 2);
  `nonMaskableInterruptLineActive` = CIA2 line (RESTORE joins in Phase 3).
- Tests: cycle-exact underflow timing, reload/one-shot, cascade, ICR
  read-clear both directions, mask write polarity, DDR port mixing,
  runtime NMI edge detector (no retrigger while held).
- Gate: `:machine-c64:test` + `:emu-platform:test` green.

Landed 2026-07-15 (Codex batch `codex/c64-p1-cia`, adversarially reviewed +
fault-injected 6/6). Frozen Phase 1 conventions: ICR IR bit is a set-only
latch (cleared only by ICR read/reset — clearing a mask bit does not drop
an asserted line, per VICE/MAME); TOD is halted after reset with HR=$01
until the first 10THS write, ticks via a 98 525-t-state accumulator
(deliberate deviation from the plan's original "frame-driven" wording —
the board has no frame notion), models the hour-12 write PM flip and
re-latches HR reads only when not already latched; CIA external port
inputs are fixed 0xFF until the Phase 3 keyboard; CNT/SP/FLAG/alarm/PB
timer outputs inert stubs; CRB INMODE 11 treated as 10 (CNT assumed high).
End-to-end NMI verified through MachineRuntime with a phase-safe stop-timer
re-arm protocol (a bare mid-stream ICR read is not phase-safe — underflow
can re-latch within the next instruction).

### Phase 2: VIC-II text mode + raster + READY.

- `C64VideoDevice`: register file $D000–$D02E (mirrored through $D3FF);
  timeline-derived raster counter; raster-compare IRQ ($D012/$D011.7 →
  latch in $D019, mask $D01A, ack by writing 1s to $D019); $D018 matrix/
  charset pointers + CIA2 port A VIC bank; standard text mode (ECM/BMM/MCM
  = 0): 40×25, charset from CHAR ROM or RAM per bank rules, color RAM low
  nibbles, border $D020 / background $D021; fixed 16-color PAL palette
  (Pepto values as named ARGB constants — hand-tuned, deliberate).
  Rendering reuses the FrameBuffer (Apple II scratch-reuse pattern).
- `C64RomProbeLauncher` (lean `Apple2RomProbeLauncher` descendant): boot
  with the three ROMs, run N instructions, screen scrape via the PETSCII
  map, `--expect-screen=`, `--dump-frame=` + CRC via `ProbeOutput`,
  `--stop-pc` + pc-profile debug aids. Gradle tasks `c64RomProbe` +
  `c64ReadySmoke` (expect `READY.`; banner
  `**** COMMODORE 64 BASIC V2 ****` on the scrape).
- Boot dependencies to watch: KERNAL RAM test length, jiffy IRQ via CIA1
  timer A, screen-editor init; debug hangs with the pc-profile.
- Gate: `c64ReadySmoke` green; frame PNG dumped, CRC recorded here as the
  Phase-2 baseline.

Landed 2026-07-16 (Codex batch `codex/c64-p2-vic`, adversarial review 0
findings, fault-injection 6/6 killed). The real KERNAL 901227-03 + BASIC
901226-01 boot to READY. in 591 300 instructions on the first attempt —
through RAMTAS, CINT's $FF5E `LDA $D012` PAL-detection poll (which also
exercises the raster-compare IRST latch at line 311) and the CIA1 jiffy
IRQ. **Phase-2 frame CRC baseline: 0xC72BD0D1** (`c64ReadySmoke` boot
screen, 384×272, Pepto palette, border 14 / background 6). Frozen Phase 2
simplifications: light pen reads 0, sprite/collision registers read 0
(real chip read-clears), ECM/BMM/MCM stored but renderer is always
standard text, XSCROLL/YSCROLL inert, full-frame snapshot only.

### Phase 3: keyboard + desktop runner

- `C64KeyboardDevice`: 8×8 matrix through CIA1 port A (column select,
  active-low) / port B (row read) honoring DDR; joystick overlay deferred
  (note the port-B collision). RESTORE key → NMI line (OR into the board's
  `nonMaskableInterruptLineActive`).
- Probe `--keys=` script (Apple II syntax) typing through the matrix with
  press/gap frames.
- Desktop: `DesktopMachineKind.C64`; `C64Definition` in
  `DesktopMachineDefinitions` (+ registration list, aliases, `loadRom`
  switch, `DesktopMachineDefinitionsTest` case); `C64DesktopRunner` Session
  (post-sweep ~80 lines: panel, statusTitle, runSlice, renderVideoFrame,
  bindKeyboardController); `C64KeyboardController extends
  AbstractMappedHostKeyboardController<matrix-position>` host mapping.
- Gate: `c64BasicSmoke` = boot → `--keys=PRINT<SP>2+2<CR>` →
  `--expect-screen= 4`; manual desktop launch (`--machine=c64 media`).

### Phase 4: PRG loading

- `.prg` = 2-byte LE load address + payload. Probe `--prg=` and desktop
  media arg: run to READY (screen scrape), inject payload, fix BASIC
  pointers ($2B TXTTAB … $2D–$33 VARTAB/ARYTAB/STREND chain) when the load
  address is $0801, then inject `RUN<CR>`; `--prg-sys=<addr>` overrides
  with `SYS`.
- Deterministic gate with no external asset: tokenized BASIC hello program
  built byte-by-byte in the test, run via probe, scrape asserted. Then one
  real freeware PRG checked by hand in the desktop.

### Phase 5: SID minimal

- `C64SidDevice extends ClockedPcmMonoSource(985_248, 44_100)`: 3 voices —
  tri/saw/pulse/noise (23-bit LFSR), 16-bit frequency, pulse width, ADSR
  with the standard rate table, voice-3 readback $D41B/$D41C, master
  volume $D418. **No filter in v1** (registers accepted, documented inert).
- Gate: deterministic register-script → PCM CRC test (self-baseline), plus
  an audible desktop check.

### Phase 6: accuracy & games backlog (unordered, pull as needed)

- Stable NMOS illegals in `:cpu-mos6502` (LAX SAX DCP ISC SLO RLA SRE RRA
  ANC ALR ARR SBX + multi-byte NOPs) behind the NMOS variant, per-opcode
  tests; re-run Klaus extended/illegal suites if adopted.
- Badline/cycle-stealing approximation (board clock skew; possibly a CPU
  stall hook later), sprites + collision registers, multicolor text,
  bitmap modes, $D016/$D011 scroll, border tricks. Each lands with new
  frame-CRC baselines.
- CIA TOD alarm, datasette (.tap), KERNAL LOAD/SAVE vector trap for fast
  host/.d64 file access; true 1541 is far-future.
- SID filter (multimode 12dB) once something audible needs it.

## Verification model (every phase)

1. Codex batch in a worktree from HEAD (`codex/c64-pN-*`), ТЗ generated
   from the phase section + frozen guardrails (no changes outside named
   modules, no committed ROMs/binaries, test parity, allocation-free hot
   paths, DELIBERATE list from the simplify sweep).
2. Codex self-gates: module tests + the phase's headless gate (ROMs copied
   into the worktree like the Apple II media).
3. Adversarial review agent on the diff; differential CRC where a baseline
   exists.
4. Merge from the main checkout (check pwd), full `./gradlew build` + all
   existing machine smokes (Apple II ×3, Spectrum pixel gate when touched)
   + the new C64 gates on main.
5. Update this file (checkboxes, baseline CRCs, deviations), commit.
