# Commodore 64 Platform Plan

This is the working checklist for adding a Commodore 64 platform. Keep this
file updated as work lands, so the next debugging pass can resume from the
current state.

## Status Legend

- `[ ]` Not started
- `[~]` In progress
- `[x]` Done

## Target

- [x] Choose target platform: `Commodore 64`
- [x] Keep the first platform milestone explicit: boot to a usable BASIC `READY.` prompt
- [ ] Run a tiny machine-code or BASIC `PRG` without disk-drive emulation
- [ ] Render the first text-mode BASIC screen through VIC-II character mode
- [ ] Accept desktop keyboard input through the C64 keyboard matrix
- [ ] Play a simple SID tone through the shared PCM path
- [ ] Load and run a known simple game or demo from `PRG`
- [ ] Add `.d64` and 1541 drive support only after the main machine is stable

## Phase 1: CPU Core

- [ ] Add Gradle module `cpu-mos6502`
- [ ] Implement registers, flags, stack, reset vector, IRQ, and NMI
- [ ] Implement core addressing modes
- [ ] Implement documented NMOS 6502 opcodes first
- [ ] Add focused instruction tests for arithmetic, branches, stack, interrupts, and page crossing
- [ ] Decide whether undocumented opcodes are needed before the first real software target
- [ ] Add a thin 6510 wrapper or mode for the C64 CPU port behavior
- [ ] Keep CPU memory and I/O access fully behind `CpuBus`

## Phase 2: Machine Skeleton

- [ ] Add Gradle module `machine-c64`
- [ ] Add `C64ModelConfig` for clock, frame timing, RAM size, and video geometry
- [ ] Add `C64Machine`
- [ ] Add `C64Board`
- [ ] Add `C64Bus`
- [ ] Add `C64Memory`
- [ ] Wire `cpu-mos6502` into the C64 board
- [ ] Add minimal tests for machine creation and CPU/bus execution
- [ ] Register `--machine=c64` in the desktop machine definitions
- [ ] Add a thin C64 desktop runner using the shared desktop session plumbing

## Phase 3: ROM And Memory Banking

- [ ] Model the 6510 64K address space over 64 KB RAM
- [ ] Support BASIC ROM mapping at `$A000-$BFFF`
- [ ] Support KERNAL ROM mapping at `$E000-$FFFF`
- [ ] Support character ROM visibility behind I/O banking
- [ ] Implement the 6510 processor port at `$0000/$0001`
- [ ] Implement RAM-under-ROM writes and reads according to active banking state
- [ ] Add tests for BASIC/KERNAL/character ROM visibility
- [ ] Add tests for RAM-under-ROM behavior
- [ ] Keep cartridge mapping out of the first milestone

## Phase 4: VIC-II Text Video

- [ ] Add a `C64VicIiDevice`
- [ ] Implement enough VIC-II registers for reset-time firmware and text mode
- [ ] Render standard 40x25 character text mode
- [ ] Support border color and background color registers
- [ ] Support character ROM glyph lookup
- [ ] Support screen RAM and color RAM lookup
- [ ] Expose C64 video through `VideoMachineBoard.renderVideoFrame()`
- [ ] Add framebuffer tests for known text and color RAM patterns
- [ ] Defer sprites, bitmap modes, badlines, and raster tricks until real software needs them

## Phase 5: CIA, Keyboard, And Timers

- [ ] Add `C64CiaDevice` for CIA 1
- [ ] Add `C64CiaDevice` for CIA 2 when VIC banking or serial I/O needs it
- [ ] Implement enough port A/B behavior for keyboard scanning
- [ ] Add C64 keyboard matrix model
- [ ] Add desktop keyboard controller for C64 keys
- [ ] Implement timer behavior required by KERNAL/BASIC
- [ ] Wire CIA interrupt output into the CPU interrupt line
- [ ] Add tests for key matrix scanning through CIA ports
- [ ] Add tests for timer underflow and interrupt behavior

## Phase 6: BASIC Prompt Bring-Up

- [ ] Acquire known-good BASIC, KERNAL, and character ROM images outside the repository
- [ ] Boot the C64 ROM set headlessly
- [ ] Trace reset vector execution through KERNAL init
- [ ] Reach the BASIC `READY.` loop
- [ ] Render the prompt through the desktop runner
- [ ] Type a simple BASIC line from the host keyboard
- [ ] Verify `PRINT` output appears in screen RAM and framebuffer
- [ ] Add a headless regression test for reaching the prompt or stable idle loop

## Phase 7: PRG Loader

- [ ] Add a simple `.prg` loader utility
- [ ] Load two-byte little-endian start address plus payload into C64 RAM
- [ ] Add an app-side autoload path for `--machine=c64 <rom-bundle> [program.prg]`
- [ ] Decide whether autoload should jump directly or inject a BASIC `LOAD`/`RUN` workflow
- [ ] Add tests for PRG parsing and memory placement
- [ ] Run a tiny hand-made PRG that writes to screen RAM
- [ ] Run a tiny hand-made PRG that writes to SID registers
- [ ] Defer `.d64` until PRG execution, video, keyboard, and SID basics are stable

## Phase 8: SID Audio

- [ ] Add Gradle module `chip-sid` only if the SID model is useful beyond C64
- [ ] Otherwise keep the first SID subset inside `machine-c64`
- [ ] Implement register writes and basic oscillator state
- [ ] Implement at least one pulse or triangle voice well enough for audible tests
- [ ] Connect SID output to `PcmMonoSource`
- [ ] Add tests for register state and non-zero generated PCM
- [ ] Defer filters and exact analog behavior until real software needs them

## Phase 9: Desktop And Debug Workflow

- [ ] Define canonical C64 desktop launch command
- [ ] Support C64 ROM bundle and optional `PRG` argument in `DesktopLaunchConfig`
- [ ] Add optional memory banking trace
- [ ] Add optional VIC-II register trace
- [ ] Add optional CIA interrupt/timer trace
- [ ] Add framebuffer PNG dump support for C64 debug runs
- [ ] Add a C64 headless probe launcher after BASIC prompt is stable
- [ ] Document the C64 run/debug workflow

## Phase 10: First Real Software Target

- [ ] Pick a simple `PRG` demo or game that does not require 1541 disk behavior
- [ ] Record exact launch command and ROM set assumptions
- [ ] Run it in the desktop shell
- [ ] Trace the first failure point
- [ ] Add a focused regression test or probe for each emulator bug found
- [ ] Reach stable visible output
- [ ] Verify keyboard controls if the target is interactive
- [ ] Capture a reference screenshot once the target runs

## Phase 11: Disk And 1541 Support

- [ ] Add `.d64` image parsing after the PRG milestone
- [ ] Decide whether to model enough IEC serial protocol in the C64 machine first
- [ ] Add a 1541 drive model only when a concrete disk target needs it
- [ ] Reuse `cpu-mos6502` for the 1541 CPU
- [ ] Add 1541 memory, VIA devices, and disk image sector access
- [ ] Implement enough DOS command behavior for `LOAD`
- [ ] Add synthetic `.d64` tests before using real game disks
- [ ] Keep the 1541 path separate from the main C64 prompt milestone

## Phase 12: Compatibility Polish

- [ ] Tighten VIC-II timing if raster interrupts or badlines expose drift
- [ ] Add sprite rendering
- [ ] Add bitmap and multicolor modes
- [ ] Add more complete CIA behavior
- [ ] Improve SID envelope and waveform behavior
- [ ] Add undocumented 6502 opcodes if real software depends on them
- [ ] Add C64-specific notes to `docs/architecture.md`
- [ ] Keep Spectrum, Radio-86RK, and CPC tests green after C64 changes

## First Implementation Slice

The first code slice should stay deliberately small:

- [ ] Create `cpu-mos6502`
- [ ] Implement enough 6502 reset and instruction execution for a tiny test program
- [ ] Create `machine-c64`
- [ ] Add the C64 model, machine, board, bus, and memory shell
- [ ] Wire CPU execution through the C64 bus
- [ ] Add RAM read/write tests and reset-vector execution tests
- [ ] Add desktop launcher recognition for `--machine=c64`

Do not start with SID, VIC-II raster timing, sprites, `.d64`, or 1541 drive
emulation before the CPU, memory map, and reset path are testable.

## Known Risk Areas

- [ ] The 6510 processor port is small but central; banking bugs can look like ROM or CPU bugs
- [ ] VIC-II timing can become a large project if raster effects are allowed into the first milestone
- [ ] CIA timer/interrupt behavior may be required earlier than expected by KERNAL routines
- [ ] SID can absorb a lot of time; start with audible, testable basics
- [ ] Real C64 software may rely on undocumented 6502 opcodes
- [ ] 1541 is effectively a second 6502 machine; keep it out of the first vertical slice
- [ ] ROM images and game images should stay outside the repository

## Progress Log

- 2026-04-25: Selected Commodore 64 as the next ambitious target platform after
  Spectrum, Radio-86RK, and Amstrad CPC. The first milestone is a usable BASIC
  `READY.` prompt plus a simple `PRG` loader, with `.d64` and 1541 support
  deliberately deferred.
