# z8-emu Audit: Grounding a Commodore 64 Platform Plan

Repo is Java 21 / Gradle multi-module. Key invariant (CLAUDE.md:54-59): time is master, CPU returns consumed t-states per instruction, board owns timing/contention, CPU talks to hardware only via `CpuBus`. The C64's 6510 maps to `cpu-mos6502` (NMOS variant), and the closest existing 6502 machine is `machine-apple2`.

## Q1 — cpu-mos6502 core

Files: `cpu/mos6502/src/main/java/dev/z8emu/cpu/mos6502/Mos6502Cpu.java`, `Mos6502Registers.java`, `Mos6502Variant.java`; interface `platform/core/.../cpu/Cpu.java`.

**IRQ/NMI public API** (defined on `Cpu`, implemented in `Mos6502Cpu`):
- `void requestMaskableInterrupt()` — `Mos6502Cpu.java:41-44`, sets `irqPending = true`.
- `void clearMaskableInterrupt()` — `Mos6502Cpu.java:46-49`, sets `irqPending = false`.
- `void requestNonMaskableInterrupt()` — `Mos6502Cpu.java:51-54`, sets `nmiPending = true`.
- State is two booleans: `irqPending`, `nmiPending` (`Mos6502Cpu.java:16-17`).
- There is **no** `clearNonMaskableInterrupt` and **no** query/getter for line state. A board asserts IRQ by calling `requestMaskableInterrupt()` and deasserts with `clearMaskableInterrupt()`.

**Level- vs edge-triggered:** Neither is modeled at the pin level — both are latched pending flags. Critically, in `runInstruction()` (`Mos6502Cpu.java:57-70`) the pending flag is **cleared on service** (`nmiPending = false` at 59; `irqPending = false` at 63). So a held-low IRQ line is NOT re-triggering by itself. True level behavior is emulated one layer up by `MachineRuntime` re-asserting/clearing every instruction (see Q5). For the CPU in isolation, IRQ behaves edge-like (one service per `requestMaskableInterrupt()` call). NMI is correctly edge-latched. Priority is correct: NMI checked before IRQ; IRQ gated by `!flagSet(FLAG_I)` (`Mos6502Cpu.java:58-65`).

**serviceInterrupt path** — `Mos6502Cpu.java:349-355`:
- `pushWord(registers.pc())` then `pushStatus(false)` (B flag cleared for hardware IRQ/NMI; contrast `brk()` at :340-347 which pushes with B set), then `setFlag(FLAG_I, true)`, then `setPc(readVector(vectorAddress))`. Returns **7** cycles.
- Vectors verified: `IRQ_VECTOR = 0xFFFE`, `NMI_VECTOR = 0xFFFA`, `RESET_VECTOR = 0xFFFC` (`Mos6502Cpu.java:8-10`). I-flag is set on entry (correct). Test coverage: `Mos6502CpuTest.java:1418-1436` (maskable→0xFFFE, NMI→0xFFFA, B clear on push).

**Decimal mode (NMOS):** Implemented for ADC and SBC. `addWithCarry` checks `FLAG_D` and calls `decimalAdd` (`Mos6502Cpu.java:1308-1326`, `1344-1357`); `subtractWithCarry`→`decimalSubtract` (`1328-1342`, `1359-1370`). Caveats for C64 fidelity: it is textbook BCD, not bug-exact NMOS. V flag is computed from the binary result (:1315) while Z/N are set from the adjusted decimal result (:1325); on real NMOS, N/V/Z in decimal mode follow quirky/undefined rules and Z is based on the binary result. Invalid-BCD nibble behavior is not the documented NMOS pattern. No decimal-mode cycle penalty (that's a 65C02 trait). Test: `Mos6502CpuTest.java:1438-1461`. Verdict: functionally present, sufficient for normal BCD, not a cycle/flag-exact NMOS reference.

**Illegal / undocumented opcodes:** NOT implemented. The `switch` default calls `illegalOpcode(...)` which **throws `IllegalStateException`** with message `"Illegal MOS 6502 opcode 0x%02X at 0x%04X"` after restoring PC to the opcode address (`Mos6502Cpu.java:262`, `327-332`). So any NMOS illegal opcode (LAX/SAX/DCP/ISC/SLO/RLA/ANC/ARR/etc., used by some C64 games/demos) will crash the run. This is a real gap for a C64 plan. Tests assert this for several codes (`Mos6502CpuTest.java:407-413, 442-448, 507-524, 584-646, 1474-1489`).

**65C02 vs NMOS switch:** `enum Mos6502Variant { NMOS_6502, CMOS_65C02 }` (`Mos6502Variant.java`). Configured via constructor `Mos6502Cpu(CpuBus, Mos6502Variant)`; the no-arg-variant constructor defaults to `NMOS_6502` (`Mos6502Cpu.java:19-27`). 65C02-only opcodes are gated by `require65C02(...)` (`:334-338`) which throws `illegalOpcode` when the variant is NMOS. For C64 use `NMOS_6502` — but note the 6510 also has its own I/O port at $0000/$0001, which is a board concern, not CPU (no support/awareness in the core; the CPU has no notion of the 6510 port). No RDY/6510-specific behavior in the core.

**Cycle reporting:** `runInstruction()` returns an `int` of consumed cycles; each opcode helper returns its own count (e.g. `return 7`, plus page-cross `+1` via `crossedPage(...)` `Mos6502Cpu.java:1298-1300`, and branch-taken/page-cross adjustments `:1243-1253`). The CPU does **not** own a `TStateCounter`; the board/runtime accumulates (see Q2/Q5). It does not call any `CpuBus` wait-state methods.

**DMA stall / halt (VIC-II badlines):** ABSENT. Grep for `WaitStates|currentTState|stall|halt|RDY|ready` in `Mos6502Cpu.java` returns nothing. There is no RDY pin, no cycle-stealing hook, no per-cycle bus callout — the core executes whole instructions and returns cycles. The `CpuBus` interface does expose optional `*WaitStates(...)` hooks (`platform/core/.../bus/CpuBus.java:8-45`) but the mos6502 core never calls them (only `fetchOpcode`/`readMemory`/`writeMemory` are used). For VIC-II badline stalls you will need either (a) a new per-instruction "extra t-states" hook the board can add, or (b) model badline cost in the board's `onTStatesElapsed` timeline. Neither exists yet. This is the single biggest CPU-side gap for cycle-accurate C64 timing.

## Q2 — emu-platform contracts (platform/core)

**Machine / runtime interfaces:**
- `Machine` (`.../machine/Machine.java`): `void reset()`, `int runInstruction()`, `long currentTState()`.
- `BoardBackedMachine<B extends MachineBoard> extends Machine` (`.../machine/BoardBackedMachine.java`): adds `B board()`.
- `MachineBoard` (`.../machine/MachineBoard.java`): `CpuBus cpuBus()`, `void reset()`, `void onTStatesElapsed(int tStates, long currentTState)`, and default `boolean maskableInterruptLineActive(long currentTState)` returning `false`. **No NMI hook exists here** — a `nonMaskableInterruptLineActive` (or equivalent) must be added for C64 NMI (RESTORE key / CIA2). This is an absence that shapes the plan.
- `VideoMachineBoard extends MachineBoard` (`.../machine/VideoMachineBoard.java`): adds `FrameBuffer renderVideoFrame()`.
- `MachineRuntime implements Machine` (`.../machine/MachineRuntime.java`) — the canonical driver; constructed with `(Cpu cpu, MachineBoard board, TStateCounter clock)`. See Q5 for its per-instruction loop.

**TStateCounter** (`.../time/TStateCounter.java`): trivial `long value` with `value()`, `reset()`, `advance(int)` (throws on negative). Owned by the machine, shared into the board and clocked devices.

**CpuBus / IoAddressSpace usage:** `CpuBus` (`.../bus/CpuBus.java`) is the sole CPU↔hardware surface: `fetchOpcode`, `readMemory`, `writeMemory`, `readPort`/`writePort`, `acknowledgeInterrupt` (default `0xFF`), plus optional wait-state and `currentTState` defaults. There is also `ClockedCpuBus` (`.../bus/ClockedCpuBus.java`). `IoAddressSpace` (`.../bus/io/IoAddressSpace.java`) is the declarative ordered mapping list: `mapRead`/`mapWrite`/`mapReadWrite(name, IoSelector, handler, priority)`, dispatched by `find()` (highest-priority matching entry) with overlap-ambiguity checking (`:137-159`) and an `IoTraceSink`. For the C64 you'd map $D000-$DFFF (VIC, SID, CIA1, CIA2, color RAM) as entries here. Note: 6502 machines route memory-mapped I/O through the board's memory bus (Apple2 uses `Apple2Bus`/`Apple2SlotBus`, not `IoAddressSpace`, since 6502 has no separate I/O space) — `IoAddressSpace` is used by the Z80/i8080 port-mapped machines. For C64, memory-mapped I/O ($D000+) will be decoded in the board's `CpuBus` implementation like Apple2 does, not via `IoAddressSpace`.

**FrameBuffer** (`.../video/FrameBuffer.java`): `new FrameBuffer(width, height)` allocates `int[width*height]`; accessors `width()`, `height()`, `int[] pixels()`, plus `clear(argb)` and `setPixel(x,y,argb)`. Pixel format is packed **ARGB int** (as the naming and Swing usage imply). C64 VIC-II would build e.g. 320x200 (or bordered ~403x284) ARGB.

**Audio — PcmMonoSource / ClockedPcmMonoSource:**
- `PcmMonoSource` (`.../audio/PcmMonoSource.java`): `int DEFAULT_SAMPLE_RATE = 44100`; `int sampleRate()`; `int drainAudio(byte[] target, int offset, int length)`.
- `ClockedPcmMonoSource` is the base for clock-driven audio: ctor `(long sourceClockHz, int sampleRate[, int bufferCapacityBytes])`; subclasses implement `protected abstract short nextPcmSample()`. `onTStatesElapsed(int)` (`:72-78`) converts elapsed CPU t-states into N output samples via `samplesForElapsedTicks` (rational accumulator `sourceClockHz`→`sampleRate`, `:94-103`) and writes into a synchronized ring buffer; the host audio engine calls `drainAudio` (16-bit LE PCM, `:48-66`). `TimedDevice` (`.../device/TimedDevice.java`) is just `reset()` + `onTStatesElapsed(int)` defaults. `MixedPcmMonoSource` and `DcBlocker` exist for combining/filtering.
- Hook-in precedent: `BeeperDevice extends ClockedPcmMonoSource` (spectrum, `SAMPLE_RATE = DEFAULT_SAMPLE_RATE`), `Ay38912Device extends ClockedPcmMonoSource` (chips/ay), `Apple2SpeakerDevice extends ClockedPcmMonoSource` (apple2). The board holds the device, calls `device.onTStatesElapsed(tStates)` from its own `onTStatesElapsed(...)`, and exposes it as `PcmMonoSource audio()`. For C64, a `SidDevice extends ClockedPcmMonoSource(cpuClockHz, 44100)` follows this exactly.

**Canonical wiring (use Apple2 as the 6502 template; CPC as the Z80 one):**
`Apple2Board implements VideoMachineBoard` (`machines/apple2/.../Apple2Board.java`): ctor takes `(modelConfig, Apple2Memory, TStateCounter)` and builds keyboard, speaker, soft switches, aux memory, slot bus, video, and an `Apple2Bus` (the `CpuBus` impl). `cpuBus()` returns the bus (:66-69); `reset()` resets each device (:71-87); `onTStatesElapsed(int, long)` ticks the speaker and any drive (:89-95); `renderVideoFrame()` calls `video.renderFrame(memory, aux, softSwitches, clock.value(), frameTStates)` (:97-100). The machine class `Apple2Machine implements BoardBackedMachine<Apple2Board>` wires it: `TStateCounter clock; board = new Apple2Board(...); cpu = new Mos6502Cpu(board.cpuBus(), modelConfig.cpuVariant()); runtime = new MachineRuntime(cpu, board, clock); runtime.reset();` (:32-42). `reset/runInstruction/currentTState` delegate to the runtime (:126-139). Note Apple2Board does **not** override `maskableInterruptLineActive` — the Apple II is not interrupt-driven, so it is NOT a precedent for IRQ wiring (use CPC/Spectrum for that; Q5).

## Q3 — app-desktop integration points (apps/desktop)

To register a new machine you touch:

1. **`DesktopMachineKind` enum**: add e.g. `C64`. Currently `{SPECTRUM48, SPECTRUM128, RADIO86RK, CPC6128, APPLE2, APPLE2E}` with a helper `isSpectrum()`.

2. **`DesktopMachineDefinition` interface**: `kind()`, `validateRom(byte[], Path)`, defaulted `validateArgumentCount(int)`, `validateLaunchOptions(DesktopLaunchOptions, int)`, `loadMedia(String[, options])` (returns `DesktopLaunchConfig.LoadedMedia`), `open(DesktopLaunchConfig)`, `usage()`.

3. **`DesktopMachineDefinitions`**: add a `private static final class C64Definition implements DesktopMachineDefinition` (mirror `CpcDefinition` :239-274 or `Apple2Definition` :276-382 — the latter shows `validateLaunchOptions`, media-type detection in `loadMedia`, and `open()` building the machine + `SwingUtilities.invokeLater(() -> C64DesktopRunner.open(machine, config))`). Then: instantiate it (:33-44), add to `DEFINITIONS` list (:46-53), `register(...)` its aliases (:62-67), and add its kind to the two `switch` blocks in `loadRom` (:104-111) and (for Spectrum-only) elsewhere. There is a test `DesktopMachineDefinitionsTest.java` that will need a case.

4. **Desktop runner + Session** (mirror `Apple2DesktopRunner.java`): a final class with `static void open(C64Machine, DesktopLaunchConfig)` → `DesktopWindowRunner.open(new Session(...))`. `Session extends AbstractFrameDesktopSession` (now **non-generic**). Constructor supplies `(FrameDisplayPanel, PcmMonoSource audio, String audioThreadName, long cpuClockHz, int frameTStates)`. Override `attachMachine(JFrame)` to build the keyboard controller and call **`bindKeyboardController(controller)`** (:98-100), `statusTitle()`, `runSlice()` (advance one frame via `runUntilTState`, :121-125), `renderVideoFrame()`, `threadName()`. Session lifecycle/audio/focus are handled by the base; the base owns the `AbstractHostKeyboardController` reference for release/close. Implement `DesktopMachineSession` contract.

5. **Keyboard**: base is a Swing `KeyEventDispatcher`; subclass either it or `AbstractMappedHostKeyboardController<K>` (:128-149) which reduces to implementing `List<K> keysFor(int keyCode)` and `void updateKey(K key, boolean pressed)` — ideal for a C64 keyboard matrix (`K` = a matrix-position enum). See `Apple2KeyboardController`, `CpcKeyboardController`, `SpectrumKeyboardController` for patterns; tape/host actions via `handleTapeHostAction`.

6. **`DesktopLauncher`** dispatch is generic (:13-14): `DesktopMachineDefinitions.forKind(config.machineKind()).open(config)` — no per-machine code needed beyond the definition. Media loaded types live as sealed-ish `DesktopLaunchConfig.LoadedMedia` records (add `LoadedC64Disk`/`LoadedC64Tape`/`LoadedC64Prg` there).

**Headless probes/smokes** — template is `apple2BasicSmoke` in `apps/desktop/build.gradle.kts:60-75`. It registers a `JavaExec` task with `mainClass = "dev.z8emu.app.desktop.Apple2RomProbeLauncher"`, `workingDir = rootProject.projectDir`, and `args("apple2plus-12k.rom", "1500000", "--keys=PRINT<SP>2+2<CR>", "--expect-screen=4")`, forwarding `-Dz8emu.*`/`-Dapple2.*` system properties. The launcher parses those flags (`--machine=`, `--keys=`, `--expect-screen=`, `--expect-frame-crc=`, `--stop-pc=`, `--dump-frame=`, many more; arg parsing at :269-400), runs the machine to `maxInstructions`, injects the key script, and every 100 steps checks `screenContains(machine, expectedScreen)` (:147, :950-951). Pass/fail is via **process exit code**: on `screenExpectationFailed || frameExpectationFailed` it prints a report and `System.exit(1)` (:159-192); frame CRC path uses `--expect-frame-crc` compared to a CRC32 of the rendered frame. For C64 you'd add a `C64RomProbeLauncher` (or generalize) plus `c64BasicSmoke` boot-to-READY / `PRINT` smoke tasks the same way. The Spectrum analog `RomProbeLauncher.java` is a simpler template (run N instructions, print register state, no screen assertion).

## Q4 — CPU test harness

**mos6502 tests** (`Mos6502CpuTest.java`, ~1559 lines): pure JUnit 5, hand-assembled programs. The `boot(...)` helper pattern (`:1491-1506`) is: `bus.load(start, program...)`, `bus.writeVector(0xFFFC, start)`, `return new Mos6502Cpu(bus[, variant])` (reset in the ctor reads the reset vector to set PC). There's an overload taking `Mos6502Variant`. `TestBus implements CpuBus` is a flat `byte[0x10000]` with `load` and `writeVector` helpers (`:1534-1557`). Tests then step `cpu.runInstruction()` and assert cycle return value + register/flag/memory state. There is a full `DOCUMENTED_OPCODES` table exercised by `documentedOpcodesAreAllExecutableEntryPoints` (`:13-30, 1463-1472`) and per-opcode illegal-on-NMOS assertions. **There is NO external functional-test binary harness for 6502** (no Klaus Dormann `6502_functional_test`, no equivalent of the Z80 zex harness) — a clear gap you'd fill.

**Z80 zex harness = the template** (`ZexHarness.java` + `ZexHarnessTest.java`; build task in `cpu/z80/build.gradle.kts`):
- Build (:13-31): default `test` task `excludeTags("zex")`; a separate registered task `val zexTest by tasks.registering(Test::class)` with `includeTags("zex")`, group `verification`, reusing the test classpath. So long-running reference tests are opt-in via `:cpu-z80:zexTest`.
- Loader (`ZexHarness.java:14-46`): reads the test binary from classpath resource `/zex/<name>` (`prelim.com`, `zexdoc.cim`, `zexall.cim`) via `getResourceAsStream`, loads at `0x0100` into a `HarnessBus` (flat 64K `CpuBus`), installs CP/M traps, sets PC=0x0100, then loops `cpu.runInstruction()` until `bus.finished()` or `maxInstructions`.
- Pass/fail detection (:76-133): CP/M BDOS emulation — a trap at `0x0005` captures console output (BDOS funcs 2/9) into a `StringBuilder`; `OUT (0),A` at `0x0000` sets `finished`. The test asserts `result.finished()`, `result.failure()==null`, and `result.output().contains("Tests complete")`.
- **For a Klaus Dormann 6502 functional test**, replicate this: add a `Functional6502Harness` with a flat-64K `CpuBus`, load `6502_functional_test.bin` at `0x0000` (or configured base), set PC to the test's entry (0x0400 in the standard build), loop `runInstruction()`, and detect success by the well-known "trap" — PC settling on the success address (infinite self-loop) rather than CP/M I/O. Add a `zex`-style tagged Gradle task in `cpu/mos6502/build.gradle.kts` (which currently has no such task) so it can be excluded from the fast `test` run. Note the illegal-opcode-throws behavior (Q1) means only the *documented*-opcode Klaus test will pass; the decimal-mode and undocumented-opcode variants would fail/throw until those are addressed.

## Q5 — Interrupt usage precedent (the pattern C64 CIA/VIC IRQ follows)

Yes — two Z80 machines drive maskable interrupts on a timeline; **no machine drives NMI**, and the mos6502 machine (Apple2) drives no interrupts at all. The wiring goes through `MachineRuntime`, not the CPU directly.

**The mechanism — `MachineRuntime.runInstruction()`** (:26-40), executed once per instruction:
```
int tStates = cpu.runInstruction();      // CPU checks its own irqPending at top of instruction
clock.advance(tStates);
board.onTStatesElapsed(tStates, clock.value());
if (board.maskableInterruptLineActive(clock.value()))
    cpu.requestMaskableInterrupt();
else
    cpu.clearMaskableInterrupt();
```
So the interrupt line is a **level polled after every instruction**: the board computes line state from the current t-state, and the runtime re-asserts or clears the CPU's pending flag accordingly. Combined with the CPU clearing `irqPending` on service (Q1), this yields correct level-triggered behavior at instruction granularity. This is exactly the model C64 CIA1/IRQ and VIC-II raster IRQ should follow: the board's `maskableInterruptLineActive` returns `(cia1.irqAsserted() || vic.irqAsserted())`.

**Two board-side flavors of the line source:**
- **Spectrum 50 Hz INT — stateless/derived** (`SpectrumUlaDevice.java:139-142`): `maskableInterruptLineActive(currentTState)` returns `frameOffset < MASKABLE_INTERRUPT_TSTATES` (~32 t-states active per frame). Boards just delegate to the ULA. No acknowledge needed; the line is a pure function of the clock. Tests: `Spectrum48kMachineTest.java:124-126`.
- **CPC raster interrupt — stateful/latched + acknowledge** (this is the closest analog to a CIA): `CpcGateArrayDevice.java` keeps a boolean `interruptRequestActive` set by the HSYNC/raster counter (`onTStatesElapsed`→`onHsync`, :102-114), exposes `maskableInterruptLineActive()` (:116-118), and `acknowledgeInterrupt()` (:120-123) clears it and masks the counter. The Z80 core, on taking the interrupt, calls `bus.acknowledgeInterrupt()` → `CpcBus.java:83-84` → `gateArray.acknowledgeInterrupt()`. Tests: `CpcMachineTest.java:280-309`.

**Key implication for C64:** the mos6502 core does **not** call `bus.acknowledgeInterrupt()` anywhere (confirmed by grep — the ack-on-take path exists only in the Z80 core). So the CPC-style CPU-driven ack won't fire for the 6510. The C64 CIA's "reading ICR clears the IRQ" and VIC's "writing $D019 clears raster IRQ" must be handled inside the CIA/VIC device register handlers (a normal memory-mapped read/write through the board bus), and the board's `maskableInterruptLineActive` simply reflects the latched device state each instruction — which the Spectrum-style poll already supports cleanly.

**NMI is unimplemented end-to-end** (absence that shapes the plan): `MachineBoard` has no `nonMaskableInterruptLineActive`, `MachineRuntime` never calls `cpu.requestNonMaskableInterrupt()`, and no machine calls it. Since NMI is edge-triggered (correctly, in the CPU: latched, cleared on service, no clear method), you should add either a board hook that the runtime edge-detects, or have the board call `cpu.requestNonMaskableInterrupt()` directly on the falling edge (RESTORE key, CIA2 timers/NMI). This new plumbing in `MachineRuntime` + `MachineBoard` is required for C64.

### Summary of load-bearing absences for the C64 plan
- No RDY/DMA-stall/halt hook in `Mos6502Cpu` → VIC-II badline cycle-stealing has no CPU-side mechanism (biggest timing gap).
- Undocumented NMOS opcodes throw `IllegalStateException` → must implement illegal opcodes (many C64 titles need them).
- No NMI wiring in `MachineRuntime`/`MachineBoard` → must add a non-maskable line hook (RESTORE, CIA2).
- Decimal mode present but not bit-exact NMOS (flags/invalid-BCD) → fine for most software, note for compatibility edge cases.
- No 6502 functional-test harness → add a Klaus Dormann harness modeled on the Z80 `ZexHarness`/`zexTest` task.
- C64 memory-mapped I/O ($D000-$DFFF) will be board-`CpuBus` decode like Apple2, not `IoAddressSpace` (that's for port-mapped Z80/i8080).
- 6510 on-chip $00/$01 port is not modeled in the CPU (board concern).

---

Как видишь, все пять вопросов закрыты с file:line-ссылками. Одна деталь для прозрачности: в последнем пункте агент рекомендует Apple2-стиль декодирования I/O вместо `IoAddressSpace`, а я в плане санкционировал `IoAddressSpace` по образцу Radio-86RK — тот тоже i8080 с memory-mapped I/O через `IoAddressSpace` (агент этот прецедент не заметил), и это даёт бесплатный трейсинг регистров при отладке загрузки KERNAL. Оба варианта корректны; если предпочитаешь вариант агента — поправлю план одной строкой.