# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

z8-emu is a multi-machine 8-bit emulator platform in Java 21 (Gradle, JUnit 5). Emulated machines: ZX Spectrum 48K/128K, Radio-86RK, Amstrad CPC 6128, Apple II Plus / IIe. The desktop shell is Swing; the core is designed to run headless in tests and probe launchers.

## Commands

```bash
./gradlew build                 # build + test everything
./gradlew test                  # all tests
./gradlew :cpu-z80:test         # one module's tests
./gradlew :cpu-z80:test --tests "dev.z8emu.cpu.z80.Z80CpuTest"                # one test class
./gradlew :cpu-z80:test --tests "dev.z8emu.cpu.z80.Z80CpuTest.someMethod"     # one test method
```

Run the desktop app (working dir is repo root, so local ROM/media files resolve as relative paths):

```bash
./gradlew :app-desktop:run --args='--machine=48|128|radio86rk|cpc6128|apple2|apple2plus|apple2e [machine-options] <rom-or-memory-image> [media]'
# examples
./gradlew :app-desktop:run --args='--machine=apple2plus apple2plus-12k.rom'
./gradlew :app-desktop:run --args='--machine=128 128.rom STRML128.TAP'
```

Headless probes and smoke tasks (all in `:app-desktop`; options are `-Dz8emu.*` / `-Dapple2.*` system properties passed before `--args`):

```bash
./gradlew :app-desktop:spectrumTapeProbe --args='128.rom STRML128.TAP /tmp/out'   # tape run + frame PNGs, see docs/headless-spectrum-probe.md
./gradlew :app-desktop:apple2RomProbe               # Apple II ROM bring-up probe
./gradlew :app-desktop:apple2BasicSmoke             # boots Applesoft, types PRINT 2+2, expects 4
./gradlew :app-desktop:apple2SuperDriveSystemSmoke  # boots ProDOS system disk to banner
./gradlew :app-desktop:apple2SuperDrivePopSmoke     # boots Prince of Persia .po, checks frame CRC
```

ROMs, tapes, and disk images (`*.rom`, `*.tap`, `*.tzx`, `*.dsk`, etc.) are gitignored but expected in the repo root; probes and smoke tasks reference them by relative path (overridable via `-P` properties like `-Papple2.rom=...`). Never commit them.

Gradle daemon pitfall: a daemon started under the Claude Code Bash sandbox survives and poisons later builds in this checkout with `fileHashes.lock (Operation not permitted)`. Fix: `./gradlew --stop`, then rebuild without the sandbox.

## Architecture

Full design doc: `docs/architecture.md`. Machine-specific runbooks and plans live in `docs/` (e.g. `apple-ii-plus-runbook.md`, `headless-spectrum-probe.md`).

Module layout (Gradle project → directory):

- `:emu-platform` (`platform/core`) — shared contracts only: `Cpu`, `CpuBus`, `IoAddressSpace` routing, `MachineRuntime`, `TStateCounter`, memory banks/maps, `FrameBuffer`, PCM audio sources.
- `:cpu-z80`, `:cpu-i8080`, `:cpu-mos6502` (`cpu/*`) — CPU cores: registers, decode, interrupts, timing tables. No RAM arrays, port maps, contention, or UI.
- `:chip-ay` (`chips/ay`) — shared AY-3-891x PSG.
- `:machine-*` (`machines/*`) — board wiring per machine: memory map, port decoding, devices (video, keyboard, tape/disk, audio), contention/wait-state policy, media loaders.
- `:app-desktop` (`apps/desktop`) — Swing window, keyboard mapping, audio output, plus all headless probe launchers. Depends on machines; core modules never depend on it.

Key invariants (from `docs/architecture.md`):

- **Time is master.** Everything coordinates on a t-state timeline. The CPU returns consumed t-states per instruction; the board decides contention and wait states. Machine timing rules (e.g. Spectrum ULA contention) belong in the machine module, never in the CPU module.
- **CPU ↔ hardware only via the bus.** Every externally visible cycle (opcode fetch, memory, I/O, interrupt ack, refresh) goes through the `CpuBus`.
- **Hot path stays allocation-free.** No per-access objects, boxing, reflection, streams, or string logging in execution paths. Prefer primitive fields and arrays.
- **I/O dispatch is a declarative ordered list** of mapping entries (`IoAddressSpace`). Do not preemptively replace it with table dispatch; that's a documented later optimization.
- No Spectrum (or other machine) assumptions in `:emu-platform`; no UI classes in core modules.
