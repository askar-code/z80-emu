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
- [x] Implement core addressing modes
- [x] Implement documented NMOS 6502 opcodes first
- [x] Add focused instruction tests for arithmetic, branches, stack, interrupts, page crossing, and opcode coverage
- [x] Decide whether undocumented opcodes are needed before the first real software target: keep them illegal until a concrete target requires them
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
- [x] Defer lo-res, hi-res, mixed mode, and 80-column rendering until after
  the BASIC prompt milestone

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
- [x] Render the prompt through the desktop runner
- [x] Type a simple BASIC line from the host keyboard
- [x] Verify `PRINT` output appears in text memory through the headless probe
- [x] Add an external-ROM headless BASIC smoke task for reaching the prompt and stable input loop
- [x] Add a headless Apple II probe key-injection path for BASIC command smoke tests

## Phase 7: Memory-Loaded Program Support

- [x] Add a small app-side raw binary loader for debugger-style bring-up
- [x] Add optional start-address override for tiny machine-code tests
- [x] Reuse the `apple2RomProbe` key script path for BASIC command text injection when useful
- [x] Add tests for binary placement in memory
- [x] Run a tiny machine-code program that writes to text page 1
- [x] Run a tiny program that toggles the speaker
- [x] Defer disk image loading until the prompt, keyboard, video, and speaker paths are stable

## Phase 8: Lo-Res And Hi-Res Graphics

- [x] Implement lo-res graphics page 1
- [x] Implement mixed text/graphics soft switches
- [x] Add framebuffer tests for lo-res color blocks
- [x] Implement hi-res graphics page 1
- [x] Add framebuffer tests for simple hi-res byte patterns
- [x] Add page 2 support for lo-res and hi-res page selection
- [x] Defer artifact-color precision until real software exposes a need

## Phase 9: Disk II And Disk Images

- [x] Add `.dsk` image parsing after the prompt milestone
- [x] Decide whether to start with DOS 3.3 sector-order images only
- [x] Add a Disk II controller card model in slot 6
- [x] Implement the phase stepping, drive select, motor, read latch, and write-protect soft switches needed for boot/load
- [x] Add synthetic disk-image tests before using real software disks
- [ ] Boot a simple DOS 3.3 disk without synthetic sector copies
- [ ] Load and run a simple BASIC program from disk
- [x] Replace the compatibility-level Disk II read latch with cycle-paced byte
  timing tied to the Apple II clock
- [x] Model brief Disk II motor spin-down/coast so DOS/RWTS can observe a still
  rotating disk after `C088` motor-off
- [x] Remove the synthetic `$C600` / `$C65C` sector-copy shortcut
- [x] Remove DOS/RWTS memory patching from the Disk II controller
- [x] Support externally supplied 256-byte Disk II PROM bytes for slot 6
- [x] Treat slot 6 ROM as empty unless an external Disk II PROM is supplied
- [x] Verify Oregon Trail boot through a real external Disk II PROM
- [ ] Implement a clean-room Disk II boot ROM fallback if external PROM usage is
  not enough for routine testing
- [ ] Defer nibble-perfect `.nib`/`.woz` handling until a concrete target requires it

## Phase 10: Desktop And Debug Workflow

- [x] Define canonical Apple II desktop launch command
- [x] Support Apple II ROM bundle or 64 KB memory image in `DesktopLaunchConfig`
- [x] Add optional Apple II raw program media argument
- [x] Add optional Apple II disk media argument after Disk II support exists
- [x] Remove Apple II desktop disk auto-turbo; keep Apple II execution realtime
- [ ] Add optional memory-mapped I/O trace
- [ ] Add optional soft-switch trace
- [ ] Add optional Disk II trace
- [x] Add probe stop-PC, watch-address, and PC hot-spot profiling helpers for
  timing diagnosis
- [x] Add framebuffer PNG dump support for Apple II debug runs
- [x] Add an Apple II headless probe launcher for BASIC prompt bring-up
- [x] Document the Apple II run/debug workflow

## Phase 11: First Real Software Target

- [x] Pick a concrete Apple II disk target
- [x] Record exact launch command and ROM/disk assumptions
- [ ] Run it in the desktop shell
- [x] Trace the first failure point
- [x] Add a focused regression test or probe for each emulator bug found
- [x] Reach stable visible output
- [ ] Verify keyboard controls if the target is interactive
- [x] Capture a reference screenshot once the target runs

## Phase 12: Apple IIe And Compatibility Polish

- [x] Add Apple II Plus slot-0 Language Card behavior needed by 64K software
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

- 2026-04-26: Verified Oregon Trail boot through a real externally supplied
  Disk II 16-sector P5 boot PROM (`341-0027`, kept outside git under
  `build/apple2-disk2-roms/341-0027-p5.bin`). The headless probe reaches the
  Oregon Trail title/menu through the real `$C600` path with no synthetic sector
  copies or DOS/RWTS memory patching; injecting `1<CR>` advances to the next
  Oregon role-selection screen. Slot 6 now reads as empty ROM bytes unless an
  external P5 PROM is supplied; P6 (`341-0028`) is the companion logic PROM and
  is not loaded by the current CPU-visible slot-ROM option.

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
- 2026-04-25: Closed the BASIC prompt bring-up milestone as a reproducible
  workflow: the desktop runner renders the Apple II Plus prompt and accepts
  manual `PRINT 2+2` input, `apple2BasicSmoke` wraps the external-ROM headless
  BASIC probe, and `docs/apple-ii-plus-runbook.md` records the canonical
  desktop and probe commands. Disk II remains deferred; the next implementation
  layer is raw memory-loaded program support.
- 2026-04-25: Completed the first raw memory-loaded program layer: desktop
  launch accepts an optional Apple II raw binary media argument with
  `--load-address` and `--start-address`, defaults raw loads to `$0800`, and
  tests now cover RAM placement plus tiny machine-code programs that write to
  text page 1 and toggle the speaker.
- 2026-04-25: Added the first Apple II graphics renderer slice: lo-res page 1
  and page 2 color blocks, hi-res page 1 and page 2 monochrome bit rendering,
  and mixed text/graphics composition with framebuffer tests. Hi-res artifact
  color precision remains deferred until a real software target requires it.
- 2026-04-25: Closed the documented NMOS 6502 CPU matrix: all 151 documented
  opcodes now dispatch, including missing indexed/indirect ALU addressing,
  absolute/indexed read-modify-write opcodes, `RTI`, `CLV`, decimal ADC/SBC,
  and `LDX`/`STX` variants. Added grouped regression tests plus a documented
  opcode coverage test. Undocumented opcodes remain intentionally illegal until
  a real software target proves they are needed.
- 2026-04-25: Added Apple II framebuffer PNG dumps to `apple2RomProbe` via
  `--dump-frame=...` and used it to verify BASIC-driven `GR`, lo-res memory
  pattern drawing, and `HGR`/`HPLOT` output at 280x192.
- 2026-04-26: Started Disk II from the first real target, Oregon Trail Side A.
  Added DOS-order 140 KB disk image parsing, slot 6 Disk II soft switches,
  DOS 3.3 sector-order mapping, 6-and-2 nibblized track streams, write-protect
  sense, and the initial slot-6 bring-up scaffolding. Oregon exposed a real 64K
  Apple II Plus requirement, so slot-0 Language Card soft switches
  `$C080-$C08F` and banked `$D000-$FFFF` RAM were added immediately rather than
  deferred. The early headless Oregon probe reached a stable hi-res title screen
  and wrote `build/apple2-oregon-lc.png`.
- 2026-04-26: Diagnosed the Oregon title-to-menu realtime delay with
  `apple2RomProbe` stop-PC, watch-address, and PC hot-spot profiling. The first
  keyboard poll at `$6205` arrives after about 217M emulated cycles, dominated
  by DOS/RWTS-style motor-on wait loops at `$BDA0/$BDA1` and `MSWAIT` at
  `$BA02/$BA03`, so the next correctness work is Disk II timing fidelity rather
  than a generic turbo shortcut.
- 2026-04-26: Replaced the Disk II latch's read-per-byte shortcut with a
  clock-paced raw-byte model: the latch advances every 32 Apple II CPU cycles,
  immediate repeated reads return the previous latch with bit 7 clear, and long
  gaps skip forward through the track stream. Added controller-level regression
  tests plus updated the slot-6 nibble-stream smoke to advance emulated time.
  Oregon still reaches the first `$6205` keyboard poll, now at about 227M
  emulated cycles; the remaining delay is still dominated by DOS/RWTS motor
  wait loops, so the next Disk II accuracy suspect is boot/RWTS motor-time
  accounting.
- 2026-04-26: Used the full-disk timing budget as a Disk II sanity check and
  fixed the synthetic 16-sector track stream from 6640 to 6400 raw bytes. At
  32 CPU cycles per raw byte, a track is now about 200ms in the current Apple II
  clock model, matching the nominal 300 RPM disk geometry. Oregon's first
  `$6205` keyboard poll moved down to about 221M emulated cycles, but the
  remaining delay is still orders of magnitude above a physical full-disk read
  budget, keeping the focus on synthetic RWTS/motor-time accounting.
- 2026-04-26: Confirmed the motor-ready accounting hypothesis with a controlled
  `apple2RomProbe --poke-on-pc=BD90:0046=00,0047=00` run. Forcing DOS/RWTS
  `MONTIMEL/MONTIMEH` ready at the `$BD90` motor check moves Oregon's first
  `$6205` keyboard poll from about 221M to about 57M emulated cycles and
  removes the `$BDA0/$BDA1` hot spot. That confirmed the bad direction: patching
  DOS/RWTS motor-time bytes was a compatibility shortcut, not real Disk II
  emulation, and was later removed.
- 2026-04-26: Traced the remaining `MSWAIT` hot spot with
  `--profile-pc-callers=BA00,BA02`. Nearly all of it came from the `$BD89`
  return path after DOS/RWTS failed to see `$C08C` changing before motor-on. The
  old controller froze disk bytes immediately on `C088` motor-off; the real
  mechanism keeps rotating briefly. Added a short motor spin-down/coast window,
  which removes that repeated spin-up caller and moves Oregon's first `$6205`
  poll from about 57M to about 15M emulated cycles.
- 2026-04-26: Removed the turbo Disk II shortcut after review. The controller no
  longer handles the private `$C0FF` command, no longer copies physical sectors
  directly into RAM from Java, and no longer patches DOS/RWTS motor-time bytes.
  The Apple II desktop runner no longer uses the disk auto-turbo/keyboard-wait
  detector. Desktop disk launch inserts the disk without auto-jumping to slot 6
  unless an external Disk II PROM is supplied.
- 2026-04-26: Added external Disk II PROM loading for the non-turbo boot path.
  Desktop accepts `--disk2-rom=/path/to/disk2.rom`, validates that the file is
  exactly 256 bytes, installs it at `$C600-$C6FF`, and auto-starts slot 6 when a
  disk image is also present. The headless Apple II probe accepts the same
  `--disk2-rom` option. Local search did not find a Disk II PROM yet, so Oregon
  verification is blocked on supplying that external ROM or writing a clean-room
  fallback.
