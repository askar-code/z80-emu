# Headless Spectrum Tape Probe

Use `SpectrumTapeProbeLauncher` when a game needs to be debugged without opening
the desktop window. It runs a Spectrum 48K or 128K machine from a ROM and tape
file, saves frame PNGs at selected milestones, and writes a text summary with
PC, t-state, tape block, ROM/bank/screen state, and output paths.

## Basic Run

Run from the repository root:

```bash
./gradlew :app-desktop:spectrumTapeProbe \
  --args='128.rom STRML128.TAP /tmp/z8-emu-stormlord'
```

Arguments:

- `rom-path`: 16 KB Spectrum 48K ROM or 32 KB Spectrum 128 ROM.
- `tape-path`: `.tap` or `.tzx`.
- `output-dir`: optional; defaults to `/tmp/z8-emu-spectrum-probe`.

The Gradle task sets the working directory to the repository root, so local files
such as `128.rom`, `STRML128.TAP`, and `RobocopA.tzx` can be passed as relative
paths.

## Useful Options

All probe options are Java system properties. Pass them before `--args`.

```bash
./gradlew :app-desktop:spectrumTapeProbe \
  -Dz8emu.probeMaxTStates=1800000000 \
  -Dz8emu.probePostEofTStates=200000000 \
  -Dz8emu.probeMilestones=200000000,400000000,800000000,1000000000 \
  --args='128.rom STRML128.TAP /tmp/z8-emu-stormlord'
```

- `z8emu.probeMaxTStates`: hard stop for the run. Default is `2000000000`.
- `z8emu.probeMilestones`: comma-separated t-states where PNG snapshots are saved.
- `z8emu.probePostEofTStates`: keep running after tape EOF for menus or title screens.
- `z8emu.probeAutostart=false`: skip the 128K tape-loader menu flow and just play tape.
- `z8emu.probeMenuPressFrames`: frames to hold Enter on the 128K tape-loader menu.

## Keyboard Input After Load

Use `z8emu.probePostEofKeys` to press keys after tape EOF. Each entry is
`row:column:frames`, separated by commas or semicolons. The row/column values are
the Spectrum keyboard matrix used by `SpectrumKeyboardController`.

Common keys:

- `3:0`: `1`
- `5:1`: `O`
- `5:0`: `P`
- `2:0`: `Q`
- `1:0`: `A`
- `6:0`: Enter
- `7:0`: Space

Stormlord example: choose keyboard controls, define `O/P/Q/A/Space`, then press
Space again to start the game:

```bash
./gradlew :app-desktop:spectrumTapeProbe \
  -Dz8emu.probeMaxTStates=1500000000 \
  -Dz8emu.probePostEofTStates=200000000 \
  -Dz8emu.probeMilestones=1000000000,1100000000 \
  -Dz8emu.probePostEofKeys='3:0:10,5:1:10,5:0:10,2:0:10,1:0:10,7:0:10,7:0:30' \
  --args='128.rom STRML128.TAP /tmp/z8-emu-stormlord-hero'
```

The final gameplay PNG is typically named like:

```text
/tmp/z8-emu-stormlord-hero/after-eof-key-7-pc-880E-t-1137785183-blk-6-of-6.png
```

## Breaks, Watches, And Pokes

These options help narrow a game-specific failure without modifying emulator
core code:

- `z8emu.probeBreakPcs=0x71F8,0x880E`: print a state dump when PC reaches one of these values.
- `z8emu.probeExitOnBreak=true`: stop after the first break.
- `z8emu.probeBreakAfterTState=1000000000`: ignore break PCs before this t-state.
- `z8emu.probeWatchAddrs=0x5C08,0x5B5C`: print value changes after each instruction.
- `z8emu.probeWriteWatchAddrs=0x4000,0x5B5C`: print writes with registers.
- `z8emu.probeDumpFrom=0x8000 -Dz8emu.probeDumpLength=256`: dump memory on break.
- `z8emu.probePokes=0x5B5C=0x10`: apply once after tape playback starts.
- `z8emu.probeStickyPokes=0x8000=0x00`: apply before every instruction.
- `z8emu.probeBlockSummaryLimit=8`: print tape block timing summaries.

Prefer these probe switches for investigation, then turn the root cause into a
focused regression test under `machines/spectrum/src/test/java`.

## Robocop Reference

Robocop side A has a regular regression test:

```bash
./gradlew :machine-spectrum:test --tests '*RobocopSideARegressionTest'
```

Use the headless probe when the test failure needs visual evidence or a
PC/memory trace:

```bash
./gradlew :app-desktop:spectrumTapeProbe \
  -Dz8emu.probeMaxTStates=1400000000 \
  -Dz8emu.probePostEofTStates=200000000 \
  -Dz8emu.probePostEofKeys='3:0:12' \
  --args='128.rom RobocopA.tzx /tmp/z8-emu-robocop'
```

## Reading Results

The output directory contains:

- `summary.txt`: all status lines, trace lines, watches, break dumps, and image paths.
- `*.png`: frame snapshots at milestones, after post-EOF key presses, and at the final state.

Useful summary fields:

- `pc`: CPU program counter.
- `t`: current t-state.
- `frame`: ULA frame counter.
- `blk`: current tape block / total blocks.
- `rom`, `bank`, `screen`: current Spectrum 128 paging state.
- `tape`: `play`, `eof`, or `stop`.
- `ear`: current tape EAR level.
