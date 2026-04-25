# Apple II Platform Plan

This is the working checklist for adding an Apple II-family platform. Keep this
file updated as work lands, so the next debugging pass can resume from the
current state.

## Status Legend

- `[ ]` Not started
- `[~]` In progress
- `[x]` Done

## Target

- [x] Choose target family: `Apple II`
- [x] Lock the first concrete machine target to `Apple II Plus` / `Apple ][+`
- [x] Keep `apple2` as the family/default launcher id, but treat it as Apple II Plus until additional model configs exist
- [x] Do not expose `apple2e`/`appleiie` aliases until Apple IIe-specific behavior is implemented
- [x] Keep the first platform milestone explicit: boot to an Applesoft BASIC prompt
- [x] Render the first text-mode screen through Apple II text memory
- [x] Accept desktop keyboard input through the Apple II keyboard strobe path
- [x] Produce a simple speaker click or tone through the shared PCM path
- [x] Run a tiny memory-loaded BASIC or machine-code program without Disk II emulation
- [ ] Load and run a known simple disk image after the prompt path is stable
- [ ] Add 80-column, auxiliary memory, and more complete Apple IIe behavior only after the base machine works

## Architecture Rule

- [x] If Apple II work exposes a needed architecture cleanup, either make the
  cleanup in the same focused slice or add an explicit checklist item here
  before moving on
- [x] Keep reusable pieces such as `cpu-mos6502`, desktop launch plumbing,
  session lifecycle, keyboard dispatch, frame dumping, and debug probes
  machine-neutral unless a concrete Apple II behavior requires otherwise
- [x] Refactor shared desktop frame sessions when needed for early machines; the
  first Apple II runner can now run without forcing a placeholder PCM source

## Phase 1: CPU Core

- [x] Confirm no existing Commodore 64-created `cpu-mos6502` is present in this branch
- [x] Add Gradle module `cpu-mos6502`
- [x] Implement registers, flags, stack, reset vector, IRQ, and NMI
- [~] Implement core addressing modes
- [~] Implement documented NMOS 6502 opcodes first
- [~] Add focused instruction tests for arithmetic, branches, stack, interrupts, and page crossing
- [ ] Decide whether undocumented opcodes are needed before the first real software target
- [x] Keep CPU memory and I/O access fully behind `CpuBus`

## Phase 2: Machine Skeleton

- [x] Add Gradle module `machine-apple2`
- [x] Add `Apple2ModelConfig` for CPU clock, frame timing, RAM size, and video geometry
- [x] Add `Apple2Machine`
- [x] Add `Apple2Board`
- [x] Add `Apple2Bus`
- [x] Add `Apple2Memory`
- [x] Wire `cpu-mos6502` into the Apple II board
- [x] Add minimal tests for machine creation and CPU/bus execution
- [x] Register `--machine=apple2` in the desktop machine definitions
- [x] Add a thin Apple II desktop runner using the shared desktop session plumbing

## Phase 3: ROM And Memory Map

- [x] Model the 6502 64K address space over base RAM and ROM
- [x] Support monitor and Applesoft ROM mapping at the top of memory
- [x] Support RAM reads/writes for the main 48K or 64K base configuration
- [x] Add memory-mapped I/O dispatch for `$C000-$C0FF`
- [x] Add slot ROM address ranges as unmapped or empty stubs at first
- [x] Add tests for reset vector fetch through ROM
- [x] Add tests for RAM, ROM, and I/O address routing
- [ ] Keep language card, auxiliary memory, and bank-switched expansion cards out of the first milestone

## Phase 4: Text Video

- [x] Add an `Apple2VideoDevice`
- [x] Implement standard 40x24 text mode first
- [x] Render text page 1 from `$0400-$07FF`
- [~] Support normal, inverse, and flashing character attributes if the ROM prompt needs them
- [x] Add character generator lookup from a local ROM image or generated glyph table
- [x] Expose Apple II video through `VideoMachineBoard.renderVideoFrame()`
- [x] Add framebuffer tests for known text-page byte patterns
- [ ] Defer lo-res, hi-res, mixed mode, and 80-column rendering until later phases

## Phase 5: Keyboard, Speaker, And Soft Switches

- [x] Add an Apple II keyboard device with key data and strobe state
- [x] Implement `$C000` keyboard data read
- [x] Implement `$C010` strobe clear
- [x] Add desktop keyboard controller for Apple II keys
- [x] Add speaker toggle behavior on `$C030`
- [x] Connect speaker output to the shared PCM mono path
- [x] Implement the core video soft switches needed by ROM and text mode
- [x] Add tests for keyboard strobe behavior
- [x] Add tests for speaker toggle producing non-zero PCM

## Phase 6: BASIC Prompt Bring-Up

- [x] Acquire known-good Apple II monitor and Applesoft ROM images outside the repository
- [x] Keep the assembled local Apple II Plus ROM at repo root as ignored file
  `apple2plus-12k.rom`; use this path for future headless and desktop runs
- [x] Boot the Apple II ROM set headlessly
- [x] Trace reset vector execution through monitor init
- [x] Reach the Applesoft BASIC prompt headlessly
- [ ] Render the prompt through the desktop runner
- [ ] Type a simple BASIC line from the host keyboard
- [x] Verify `PRINT` output appears in text memory through the headless probe
- [~] Add a headless regression test for reaching the prompt or stable input loop
- [x] Add a headless Apple II probe key-injection path for BASIC command smoke tests

## Phase 7: Memory-Loaded Program Support

- [ ] Add a small app-side raw binary loader for debugger-style bring-up
- [ ] Add optional start-address override for tiny machine-code tests
- [ ] Add a simple text injection path for BASIC commands if useful
- [ ] Add tests for binary placement in memory
- [ ] Run a tiny machine-code program that writes to text page 1
- [ ] Run a tiny program that toggles the speaker
- [ ] Defer disk image loading until the prompt, keyboard, video, and speaker paths are stable

## Phase 8: Lo-Res And Hi-Res Graphics

- [ ] Implement lo-res graphics page 1
- [ ] Implement mixed text/graphics soft switches
- [ ] Add framebuffer tests for lo-res color blocks
- [ ] Implement hi-res graphics page 1
- [ ] Add framebuffer tests for simple hi-res byte patterns
- [ ] Add page 2 support when a concrete software target needs it
- [ ] Defer artifact-color precision until real software exposes a need

## Phase 9: Disk II And Disk Images

- [ ] Add `.dsk` image parsing after the prompt milestone
- [ ] Decide whether to start with DOS 3.3 sector-order images only
- [ ] Add a Disk II controller card model in slot 6
- [ ] Implement the phase stepping, drive select, motor, read latch, and write-protect soft switches needed for boot/load
- [ ] Add synthetic disk-image tests before using real software disks
- [ ] Boot a simple DOS 3.3 disk
- [ ] Load and run a simple BASIC program from disk
- [ ] Defer nibble-perfect `.nib`/`.woz` handling until a concrete target requires it

## Phase 10: Desktop And Debug Workflow

- [ ] Define canonical Apple II desktop launch command
- [~] Support Apple II ROM bundle and optional program or disk argument in `DesktopLaunchConfig`
- [ ] Add optional memory-mapped I/O trace
- [ ] Add optional soft-switch trace
- [ ] Add optional Disk II trace
- [ ] Add framebuffer PNG dump support for Apple II debug runs
- [x] Add an Apple II headless probe launcher for BASIC prompt bring-up
- [ ] Document the Apple II run/debug workflow

## Phase 11: First Real Software Target

- [ ] Pick a simple Apple II disk or binary target that starts in 40-column mode
- [ ] Record exact launch command and ROM/disk assumptions
- [ ] Run it in the desktop shell
- [ ] Trace the first failure point
- [ ] Add a focused regression test or probe for each emulator bug found
- [ ] Reach stable visible output
- [ ] Verify keyboard controls if the target is interactive
- [ ] Capture a reference screenshot once the target runs

## Phase 12: Apple IIe And Compatibility Polish

- [ ] Add Apple IIe model config after the Apple II+/base path works
- [ ] Add auxiliary memory and 80-column card behavior
- [ ] Add more complete soft-switch behavior
- [ ] Improve hi-res artifact color handling
- [ ] Add joystick/paddle input if a real target needs it
- [ ] Add Mockingboard only if a concrete target needs richer audio
- [ ] Add Apple II-specific notes to `docs/architecture.md`
- [ ] Keep Spectrum, Radio-86RK, CPC, and any C64 tests green after Apple II changes

## First Implementation Slice

The first code slice should stay deliberately small:

- [x] Create or reuse `cpu-mos6502`
- [x] Implement enough 6502 reset and instruction execution for a tiny test program
- [x] Create `machine-apple2`
- [x] Add the Apple II model, machine, board, bus, and memory shell
- [x] Wire CPU execution through the Apple II bus
- [x] Add RAM read/write tests and reset-vector execution tests
- [x] Add desktop launcher recognition for `--machine=apple2`

Do not start with Disk II, lo-res/hi-res rendering, 80-column mode, auxiliary
memory, or joystick/paddle input before the CPU, memory map, and reset path are
testable.

## Known Risk Areas

- [ ] Apple II memory-mapped I/O is simple in shape but full of soft-switch side effects
- [ ] Text mode is easy; hi-res artifact colors can become a rabbit hole
- [ ] Disk II is elegant but subtle; keep it out of the first vertical slice
- [ ] Apple IIe compatibility can sprawl into auxiliary memory and 80-column behavior
- [ ] ROM images and software images should stay outside the repository
- [ ] If C64 and Apple II are both pursued, `cpu-mos6502` must stay machine-neutral

## Progress Log

- 2026-04-25: Added Apple II as an alternative next legendary platform plan
  for comparison with Commodore 64. The first milestone is an Applesoft BASIC
  prompt with text video, keyboard strobe, and simple speaker output; Disk II
  and Apple IIe expansion behavior are deliberately deferred.
- 2026-04-25: Landed the first Apple II implementation slice: `cpu-mos6502`
  exists with reset, stack, core status flags, IRQ/NMI, and a small tested
  opcode subset; `machine-apple2` now has model, memory, bus, board, machine,
  placeholder video frame, reset-image execution tests, and desktop launcher
  recognition via `--machine=apple2` with a 64 KB memory image.
- 2026-04-25: Added the first Apple II memory-map and video slice: optional
  top-of-address-space system ROM, `$C000-$C0FF` I/O dispatch, empty slot ROM
  stubs, core video soft switches, 40x24 text-page rendering from Apple II's
  non-linear text memory layout, and framebuffer tests for page 1/page 2 text.
- 2026-04-25: Added Apple II keyboard and speaker devices: `$C000` keyboard
  data, `$C010` strobe clear, `$C030` speaker toggle, desktop typed-key input,
  speaker PCM output, and tests for CPU-visible keyboard/speaker I/O.
- 2026-04-25: Started BASIC prompt bring-up infrastructure: desktop Apple II
  launch now accepts 4 KB, 8 KB, or 12 KB system ROM images in addition to
  64 KB memory images; added `apple2RomProbe` headless task that prints CPU
  state, text page, and bytes around PC; validated the path with a synthetic
  12 KB ROM whose reset vector runs three NOPs at `$D000`.
- 2026-04-25: Found local MAME-style Apple II ROM sets under
  `~/Downloads/apple2` and `~/Downloads/apple2p`; assembled
  `apple2plus-12k.rom` from Apple II Plus D0/D8/E0/E8/F0 chips plus the shared
  `341-0020-00.f8` monitor ROM. The file is ignored by git via `*.rom`; use it
  as the canonical local ROM path for future Apple II runs. Expanded the tested
  6502 subset enough for the real Apple II Plus ROM to clear the screen, print
  `APPLE ][`, and run 200000 headless probe instructions without hitting an
  illegal opcode; the current screen shows the prompt/input area and waits in
  ROM keyboard polling, so the next milestone is scripted keyboard input and a
  BASIC smoke test.
- 2026-04-25: Fixed the project target wording: the implementation remains
  under the `apple2` family/module/launcher id, but the first concrete machine
  config is now explicitly `Apple II Plus`. Apple IIe aliases stay disabled
  until an Apple IIe model config and compatibility behavior are actually added.
- 2026-04-25: Added headless Apple II Plus keyboard injection to
  `apple2RomProbe` with script tokens such as `<SP>` and `<CR>`, plus optional
  `--expect-screen`. The current BASIC smoke command is
  `./gradlew :app-desktop:apple2RomProbe --args='apple2plus-12k.rom 1500000 --keys=PRINT<SP>2+2<CR> --expect-screen=4'`;
  it reaches `status=expectation-met` with `PRINT 2+2` on line 2 and `4` on
  line 3. Also added `[` and `]` text glyphs so `APPLE ][` no longer renders as
  `APPLE ??` in the desktop window.
