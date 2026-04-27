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
- [x] Add a first-pass hi-res artifact-color renderer once PoP exposed the need

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
- [x] Recognize 800 KB ProDOS ordered `.po` images, read their root catalog, and
  report them as unsupported media instead of treating them as raw memory-loaded
  programs
- [x] Add a headless ProDOS `.po` root-catalog inspection task for future 3.5-inch
  bring-up
- [x] Read ProDOS standard file data through seedling, sapling, sparse sapling,
  and tree storage blocks
- [x] Introduce a read-only Apple II block-device contract and carry ProDOS `.po`
  media through desktop launch config as a recognized block device
- [x] Add a ProDOS file extraction path for preserving loader bytes outside the
  disk image
- [x] Add a ProDOS loader scanner for printable strings, absolute 6502
  references, and Apple II `$C0xx` area classification
- [ ] Implement a clean-room Disk II boot ROM fallback if external PROM usage is
  not enough for routine testing
- [x] Add a first 5.25-inch WOZ1 reader once the PoP target supplied protected
  low-level media

## Phase 10: Desktop And Debug Workflow

- [x] Define canonical Apple II desktop launch command
- [x] Support Apple II ROM bundle or 64 KB memory image in `DesktopLaunchConfig`
- [x] Add optional Apple II raw program media argument
- [x] Add optional Apple II disk media argument after Disk II support exists
- [x] Remove Apple II desktop disk auto-turbo; keep Apple II execution realtime
- [x] Add optional memory-mapped I/O trace
- [x] Add optional soft-switch trace
- [x] Add optional Disk II trace
- [x] Add optional trace-tail mode for late-boot I/O diagnosis
- [x] Add probe stop-PC, watch-address, and PC hot-spot profiling helpers for
  timing diagnosis
- [x] Add framebuffer PNG dump support for Apple II debug runs
- [x] Add an Apple II headless probe launcher for BASIC prompt bring-up
- [x] Document the Apple II run/debug workflow

## Phase 11: First Real Software Target

- [x] Pick a concrete Apple II disk target
- [x] Record exact launch command and ROM/disk assumptions
- [x] Re-scope Prince of Persia from Apple II Plus to a minimal Apple IIe 128K
  compatibility profile
- [ ] Run it in the desktop shell
- [x] Trace the first failure point
- [x] Add a focused regression test or probe for each emulator bug found
- [x] Reach stable visible output
- [x] Verify keyboard controls if the target is interactive
- [x] Capture a reference screenshot once the target runs

## Phase 12: Apple IIe And Compatibility Polish

- [x] Add Apple II Plus slot-0 Language Card behavior needed by 64K software
- [x] Add Apple IIe 128K model config and desktop aliases for the PoP path
- [~] Add auxiliary memory and 80-column card behavior
- [ ] Add more complete soft-switch behavior
- [x] Improve hi-res artifact color handling enough for the PoP WOZ path
- [x] Add joystick/paddle input if a real target needs it
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

## Prince Of Persia Boot Plan

- [x] Recognize the local 800 KB `.po` image as ProDOS ordered media and read
  its root `PRODOS` file
- [x] Scan the extracted `PRODOS` loader and confirm it is not an Apple II Plus
  target: it contains a `//C OR //E WITH 128K` requirement string and touches
  language-card plus slot-6 I/O addresses
- [x] Add `apple2e` / `appleiie` / `apple2e-128k` desktop aliases backed by an
  explicit Apple IIe 128K model profile
- [x] Add first Apple IIe auxiliary-memory and 80-column-card soft-switch layer:
  `80STORE`, `RAMRD`, `RAMWRT`, `ALTZP`, `80COL`, `ALTCHARSET`, and status
  reads
- [x] Acquire local enhanced Apple IIe ROM parts outside the repository:
  `342-0303-a.e8`, `342-0304-a.e10`, `342-0265-a.chr`, and `341-0132-d.e12`
- [x] Remove temporary wrong-ROM bring-up hacks: the synthetic `$FBB3`
  Apple IIe machine-id overlay and the global `BRA` opcode in the NMOS 6502
  core
- [ ] Verify whether that layer satisfies the loader's 128K machine check once
  a bootable `.po` controller path exists
- [x] Add clean enhanced Apple IIe 16 KB firmware mapping from the local
  `342-0303-a.e8` / `342-0304-a.e10` ROM halves, preserving `$C0xx` I/O and
  slot-ROM override behavior
- [x] Add model-specific 65C02 CPU support for enhanced Apple IIe if the real
  ROM or PoP path executes 65C02-only opcodes; keep `Mos6502Cpu` NMOS-clean
- [x] Add 65C02 zero-page `RMB`/`SMB` bit opcodes needed after the enhanced IIe
  `$C6xx` firmware reached `SMB0` (`$87`)
- [ ] Decode ProDOS boot blocks 0-1 from the local `.po` image and record the
  actual chain into `PRODOS`
- [x] Add a boot-block scanner for `.po` blocks 0-1 and record the first PoP
  boot markers
- [x] Add a boot-blocks-only probe mode that loads `.po` blocks 0-1 as a
  diagnostic program without installing the ProDOS block shim, so it can run
  against an explicit controller/probe path
- [~] Add a repeatable headless PoP probe command that logs PC, language-card
  switches, SuperDrive host/shared-RAM traffic, and eventual block reads
- [x] Add a first read-only ProDOS block-device shim for diagnostics only; do
  not treat it as a real Apple 3.5, IWM, SmartPort, or production desktop boot
  path
- [ ] Diagnose the current block-shim stop: with real enhanced IIe
  ROM and 65C02 mode, PoP reaches `$EE00` through loaded ProDOS code but finds
  empty language-card RAM there; determine whether the next fix is LC bank
  semantics, ProDOS block-driver protocol, or SuperDrive/SWIM media behavior
- [x] Add a real Apple 3.5 / SuperDrive controller-ROM path for desktop `.po`
  booting
- [x] Remove the dead-end slot-6 IWM skeleton and externally supplied 256-byte
  IWM slot-ROM path; the current 3.5-inch route is the SuperDrive controller
  card, not a host-visible `$C600` firmware page
- [x] Add a minimal SuperDrive SWIM register core for controller firmware
  bring-up: mode/status/handshake registers, the firmware self-test shift
  pattern, side-select state, diagnostic LED state, and internal I/O tracing
- [x] Split the SWIM write-data latch from the incoming read-data latch so
  controller writes no longer echo back as fake disk bytes
- [x] Add SuperDrive bidirectional shared-RAM tracing so `--trace-superdrive`
  shows onboard controller I/O, Apple II accesses to `$C0nX`, `$CnXX`, and
  `$C800-$CFFF`, plus controller-side reads/writes of host-touched shared RAM
- [x] Add the 65C02 opcodes proven by the real SuperDrive firmware path:
  `PHX`/`PLX`/`PHY`/`PLY`, `TSB`/`TRB`, and immediate/indexed `BIT`, all
  CMOS-only with NMOS illegal-opcode behavior preserved
- [x] Attach 800 KB 3.5-inch media to the SuperDrive/SWIM path underneath the
  controller firmware instead of through ProDOS block copies
- [x] Add a byte-oriented SWIM media stream fed by a ProDOS-ordered 800 KB
  Apple 3.5 GCR image
- [x] Add the first SWIM ISM-mode behavior needed by the real controller ROM:
  the IWM-to-ISM switch sequence and 16-byte parameter RAM readback
- [x] Let boot-block probes warm up the real Apple IIe ROM before direct block
  entry so firmware vectors such as `$03F0` are initialized by ROM code
- [~] Tighten SWIM data-ready semantics beyond the current byte-stream model
- [x] Diagnose the post-ISM SuperDrive boundary: the direct boot-block probe was
  the wrong `$Cx5C` host-service path for the SuperDrive card, while the real
  `PR#5` path boots 800 KB `.po` media through the controller ROM
- [x] Drop the Apple IIc Plus detour from the PoP route
- [x] Classify the new `Prince of Persia side A.woz` and
  `Prince of Persia side B.woz` images as WOZ1 5.25-inch media requiring
  `128K` and `2e|2e+|2c|2gs`
- [x] Add a WOZ reader that can feed Disk II from 5.25 bit/nibble tracks
- [x] Boot PoP side A on the Apple IIe 128K profile through the real Disk II
  soft-switch path; the slot-6 PROM finds the WOZ `D5 AA 96` prologue, jumps
  into the loaded boot sector at `$0801`, reads the custom-format tracks,
  displays the title/game graphics, and reaches the first room when `<SPACE>` is
  injected at the game's `$0CC2` keyboard poll
- [x] Fix Apple IIe `ALTZP` language-card behavior so main/aux high memory
  have separate language-card backing stores
- [x] Implement Apple IIe `$C019` vertical-blank status so PoP's frame wait
  loop can leave `$0D13/$0D16`
- [x] Add named pushbutton input state for `$C061/$C062/$C063`, defaulting to
  released instead of relying on unmapped I/O reads
- [x] Add centered paddle timers for `$C064-$C067` and a `$C070` trigger so
  joystick reads no longer default to full-left
- [x] Add desktop bindings for game-port controls: arrows drive paddle axes,
  and `Space` / `Shift` / `Control` drive pushbutton 0
- [ ] Add probe scripting for game-port buttons/paddles if headless gameplay
  checks need more than keyboard-latch injection
- [ ] Add side-B handling once side A reaches the game loader's disk prompt

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
- 2026-04-26: Added optional Apple II trace hooks to the headless probe:
  `--trace-io` records shared I/O address-space routing, `--trace-soft-switches`
  filters Apple II `$C0xx` soft-switch accesses, and `--trace-disk2` records
  detailed Disk II switch/read-latch state with track, motor, drive, and Q6/Q7
  flags. Use `--trace-limit=N` to keep long disk boots readable.
- 2026-04-26: Re-verified Oregon Trail keyboard progression after the I/O
  routing cleanup. A headless `1<CR>` injection at the menu keyboard poll
  advances to the role-selection screen, captured at
  `build/apple2-oregon-prom-choice1-cr.png` with frame CRC `E6A0C03F`. The
  desktop-shell launch item remains open until the same path is manually
  checked through the Swing runner.
- 2026-04-26: Added `apple2RomProbe --expect-frame-crc=...` so graphics-mode
  targets such as Oregon Trail can be validated by final rendered framebuffer
  CRC instead of only by text-page contents. The Oregon `1<CR>` role-selection
  smoke now exits green against frame CRC `E6A0C03F`.
- 2026-04-26: Added a minimal ProDOS ordered block-image reader for 800 KB `.po`
  files. It can read the volume name and root directory entries from the ProDOS
  directory block chain. The desktop launcher now fails early with an explicit
  unsupported media message and catalog summary instead of trying to load a
  3.5-inch ProDOS image as a raw Apple II program; real boot support remains
  deferred until a ProDOS block device / 3.5-inch controller path exists.
- 2026-04-26: Added `./gradlew :app-desktop:apple2ProDosCatalog` to print the
  root catalog of a ProDOS `.po` image without starting the desktop launcher.
  The local Prince of Persia image reports `volume=/P.O.P.` and root entry
  `PRODOS`.
- 2026-04-26: Added ProDOS standard file data reads for seedling, sapling, sparse
  sapling, and tree file storage. The `.po` catalog task now prints CRC32 for
  standard files, giving a stable check for the local Prince of Persia `PRODOS`
  loader file before 3.5-inch/SmartPort emulation exists.
- 2026-04-26: Added an `Apple2BlockDevice` read-only block-device contract and
  made `Apple2ProDosBlockImage` implement it. Desktop Apple II media loading now
  carries 800 KB ProDOS `.po` images through `DesktopLaunchConfig` as recognized
  block media, then fails in the Apple II open path with an explicit
  "not desktop-bootable until a real Apple II 3.5 / SuperDrive controller path exists"
  message.
- 2026-04-26: Extended `apple2ProDosCatalog` with
  `--extract=<name> --output=<path>`, so the local Prince of Persia `PRODOS`
  loader can be written to `build/apple2-pop/PRODOS.bin` for future disassembly
  and CRC checks without ad hoc extraction scripts.
- 2026-04-26: Extended `apple2ProDosCatalog` with
  `--scan=<name> --load-address=<hex> --scan-limit=<count>`. The local Prince
  of Persia `PRODOS` scan at `$2000` shows startup calls through `$2031`,
  `$205E`, `$20E4`, `$2123`, an explicit
  `REQUIRES A //C OR //E WITH 128K` string, language-card accesses at
  `$C081/$C08B`, and slot-6 I/O references at `$C0E8/$C0ED/$C0EF`. This keeps
  the next 3.5-inch/ProDOS controller step anchored to observed loader behavior
  instead of guessing from the image extension alone.
- 2026-04-26: Re-scoped the Prince of Persia boot path to an explicit
  Apple IIe 128K compatibility target. The desktop launcher now accepts
  `--machine=apple2e`, `--machine=appleiie`, and `--machine=apple2e-128k`,
  backed by an `Apple IIe 128K` model profile. The model is only a launch/config
  boundary for now; auxiliary memory, 80-column-card soft switches, and any
  Apple II 3.5 / SuperDrive-specific controller behavior still need separate
  implementation.
- 2026-04-26: Confirmed from Jordan Mechner's released source archive that PoP
  has dedicated `RW1835` 18-sector disk routines and a 3.5-inch boot/routine
  path. Treat low-level controller emulation as a likely requirement for the
  original PoP disk, while keeping a ProDOS block-device shim as a useful first
  diagnostic path for the `.po` image.
- 2026-04-26: Added `apple2ProDosCatalog --scan-boot --load-address=0800` for
  ProDOS boot blocks 0-1. The local PoP image reports boot-block CRC32
  `C720393F`, contains the `PRODOS` filename and
  `*** UNABLE TO LOAD PRODOS ***` message in the boot area, and the linear scan
  shows a `JMP $2000` marker matching the extracted `PRODOS` loader load
  address. The same scan also sees language-card references in the boot blocks,
  so the aux/language-card path is part of boot, not only a later game-loader
  concern.
- 2026-04-26: Added the first Apple IIe auxiliary-memory layer. `Apple2AuxMemory`
  is installed only for model configs above 64 KB, so Apple II Plus behavior is
  unchanged. The implemented IIe switches cover `80STORE` (`$C000/$C001`),
  `RAMRD` (`$C002/$C003`), `RAMWRT` (`$C004/$C005`), `ALTZP` (`$C008/$C009`),
  `80COL` (`$C00C/$C00D`), `ALTCHARSET` (`$C00E/$C00F`), and status reads in
  `$C013-$C01F`. Memory routing now supports aux zero page/stack, aux
  `$0200-$BFFF` reads/writes, and 80STORE+PAGE2 text-page bank selection; the
  bootable PoP path will determine whether more complete IIe ROM/slot behavior
  is needed next.
- 2026-04-26: Added local enhanced Apple IIe ROM parts in the workspace only:
  `342-0303-a.e8` and `342-0304-a.e10` are 8 KB ROM halves,
  `342-0265-a.chr` is the 4 KB character ROM, and `341-0132-d.e12` is a 2 KB
  companion ROM. Removed the temporary wrong-ROM probes that made an Apple II
  Plus ROM look like IIe firmware: no synthetic `$FBB3` machine-id overlay and
  no global `BRA` implementation in the NMOS `Mos6502Cpu`.
- 2026-04-26: Added clean enhanced Apple IIe firmware loading. The desktop
  launcher and `apple2RomProbe` can now use the project directory or either
  standard ROM half as the Apple IIe ROM source; `342-0304-a.e10` is mapped as
  `$C000-$DFFF` and `342-0303-a.e8` as `$E000-$FFFF`. `$C000-$C0FF` remains I/O,
  and internal Cx ROM selection now belongs to the Apple IIe MMU soft switches
  instead of being assumed for every empty slot.
  A live probe of `--machine=apple2e .` starts from the real reset vector
  `$FA62`.
- 2026-04-26: Added a model-specific 65C02 mode for the Apple IIe profile while
  keeping default `Mos6502Cpu` NMOS-clean. The PoP `PRODOS` loader reaches and
  requires `BRA` at `$2016`; it later also executes the 65C02 immediate NOP
  opcode `$02`. With those implemented only for `CMOS_65C02`, the PoP block
  probe now advances to `$EE00` instead of failing on opcode coverage.
- 2026-04-26: The boot-blocks-only IWM probe showed another real enhanced-IIe
  CPU coverage gap before any IWM access: the boot path entered `$C6xx`
  firmware and hit `SMB0 $AA` (`$87`) at `$C6B0`. Added CMOS-only `RMB`/`SMB`
  zero-page bit opcodes while keeping those bytes illegal on the default NMOS
  6502.
- 2026-04-26: After `RMB`/`SMB`, the boot-blocks-only IWM probe runs through
  the `$C6xx` firmware copy/check loops instead of crashing, but still does not
  reach `$C0E*` IWM accesses. A 20M-instruction run lands in a tight `$C734`
  loop with visible `MMU`, so the next live boundary is Apple IIe MMU/aux/LC
  compatibility in the firmware path, not physical 3.5 media yet.
- 2026-04-26: Fixed Apple IIe Cx ROM selection so `$C006` selects external
  slot ROMs and `$C007` selects internal Cx ROM, with `$C00A/$C00B` controlling
  internal/external `$C300`. This removed the false `$C6xx` internal-firmware
  path from the IWM boot-block probe: with no real slot-6 controller ROM loaded,
  `C600` and `C6FF` now read `FF` and the PoP boot blocks show
  `*** UNABLE TO LOAD PRODOS ***` instead of the IIe `MMU` self-test loop.
- 2026-04-26: Added the host-visible Apple II 3.5 / SuperDrive controller card
  shape instead of treating it as a 256-byte slot ROM. `Apple2SlotBus` now has
  shared `$C800-$CFFF` expansion-window routing, and
  `Apple2SuperDriveController` models the card's Apple II bus windows: `$C0nX`
  bank latch, read-only-from-host `$Cn00-$CnFF` controller RAM at
  `$7B00-$7BFF`, and `$C800-$CBFF` banked plus `$CC00-$CFFF` fixed controller
  RAM. `apple2RomProbe` accepts
  `--superdrive35-rom=<32k-rom>` and `--superdrive35-slot=<slot>`; this still
  does not run the card's onboard 65C02/SWIM firmware, so it is plumbing for
  the real controller path, not a boot-complete shortcut.
- 2026-04-26: Verified the supplied `341-0438-a.bin` as the real Apple II
  3.5 / SuperDrive controller ROM (`32768` bytes, CRC32 `c73ff25b`, SHA1
  `440c3f84176c7b9f542da0b6ddf4fb13ec326c46`). Added onboard controller 65C02
  execution against that ROM, plus missing CMOS `STZ` and accumulator
  `INC`/`DEC` opcodes needed by the controller firmware. The controller now
  runs millions of firmware instructions and initializes the host-visible
  `$C500` / `$C800` RAM windows. New boundary: the card firmware reaches its
  SWIM/media path, but no 3.5-inch media/SWIM behavior is attached yet.
- 2026-04-26: Added `lastPc` / `lastOpcode` to `apple2RomProbe` state output.
  The current PoP block-shim probe stops at `$0000` after executing `$EE00`
  where language-card RAM is empty, while the underlying enhanced IIe ROM at
  `$EE00` is valid. The next debugging pass should focus on LC bank/read state
  and the ProDOS block-driver path, not on ROM identity.
- 2026-04-26: Reclassified the synthetic ProDOS block device as an explicit
  diagnostic shim. `Apple2ProDosBlockController` was renamed to
  `Apple2ProDosBlockShimController`, its generated driver was renamed
  `writeSyntheticDriver`, and desktop `.po` launch no longer auto-boots through
  it. The shim remains available only through explicit probe paths such as
  `apple2RomProbe --prodos-boot=...` while the real SuperDrive path is pending.
- 2026-04-26: Removed the earlier low-level Apple 3.5 / IWM slot-6 skeleton and
  its `--iwm35` / `--iwm35-rom` probe flags. That route modeled the wrong
  host-visible shape for this PoP target; the real enhanced-IIe route is the
  Apple II 3.5 / SuperDrive controller card with the 32 KB `341-0438-a.bin`
  firmware and shared RAM windows.
- 2026-04-26: Added `apple2RomProbe --prodos-boot-blocks=<disk.po>` as a
  diagnostics-only mode that loads ProDOS boot blocks 0-1 at the configured
  boot address and starts them with the configured slot in X, but does not
  install the synthetic block-device shim. This lets the same local PoP boot
  blocks run against explicit controller paths such as the SuperDrive probe
  before the 3.5-inch media stream exists.
- 2026-04-26: Advanced the real SuperDrive path past controller-CPU coverage
  issues. `--trace-superdrive` now traces both onboard controller I/O and host
  accesses to `$C0nX`, `$CnXX`, and `$C800-$CFFF`. The minimal SWIM core now
  passes the SuperDrive ROM self-test sequence far enough for the boot blocks
  to enter the card firmware. Added CMOS-only 65C02 `PHX`/`PLX`/`PHY`/`PLY`,
  `TSB`/`TRB`, and immediate/indexed `BIT`; the latest PoP SuperDrive probe has
  no `superdrive35CpuFault` and reaches the boot-block error screen
  `*** UNABLE TO LOAD PRODOS ***`. The host trace shows the boot firmware
  writing a command packet through shared RAM (`$C802-$C80A`) and strobing
  `$C800=01`, so the current boundary is the real SWIM/media response beneath
  the controller firmware, not Apple IIe ROM identity or missing 65C02 opcodes.
- 2026-04-26: Extended SuperDrive tracing across the shared-RAM mailbox. Host
  writes to controller RAM are marked, and later onboard-controller reads/writes
  of those offsets are reported as `controller-ram-host-touched`. The latest
  PoP SuperDrive probe shows the packet is not being lost: after the host
  strobes `$C800=01`, the controller ROM at `$D1B8` reads command byte `$0000`,
  command fields `$0002=81`, `$0003=01`, `$0005=02`, `$0006=00`, returns
  status `$0001=00`, and clears `$0000=FF`. That moves the active boundary below
  the mailbox into the SWIM/media layer, where the stub still returns synthetic
  data/status and the firmware waits around its disk routines instead of seeing
  real 3.5-inch media data.
- 2026-04-26: Split the SWIM outgoing write-data latch from the incoming
  read-data latch. The controller firmware can still write disk-control bytes,
  but those bytes no longer become fake media data on later reads. The current
  PoP probe still reaches the same visible `*** UNABLE TO LOAD PRODOS ***`
  loop; the more precise boundary is now the SWIM status/data-ready bit around
  controller ROM `$F5F2`, where the firmware waits on status `$57` with bit 7
  clear because no real media stream is attached yet.
- 2026-04-26: Added the first real 800 KB Apple 3.5 media layer under the
  SuperDrive/SWIM path. `Apple2GcrEncoding` now shares the 6-and-2 table
  between Disk II and 3.5 media encoding, `Apple2Gcr35Media` maps the
  ProDOS-ordered `.po` image into 80 tracks, 2 heads, Apple 3.5 zone sector
  counts, address/data prologs, tag bytes, and Mac-style GCR data triples, and
  `Apple2SwimMediaStream` feeds bytes into the SWIM read latch with a data-ready
  status bit. The PoP SuperDrive probe now inserts the same `.po` image as
  low-level media when `--prodos-boot-blocks` is used with the SuperDrive
  controller. Current boundary: the stream is byte-oriented, track remains fixed
  at 0, side selection is only the known controller I/O side latch, and the boot
  still ends at `*** UNABLE TO LOAD PRODOS ***`, so the next layer is drive
  positioning and more exact SWIM read/status behavior.
- 2026-04-26: Added a minimal SWIM ISM-mode layer after the real controller ROM
  exposed the next failure: it switches from IWM register mode into ISM/SWIM
  mode, writes the 16-byte parameter RAM through `$0A03`, resets the parameter
  index via `$0A06`, and expects to read the same values back through the
  `$0A0B` alias. The previous stub kept returning the old self-test shift
  pattern, so the controller firmware repeated the parameter test. The new
  `Apple2SwimController` handles the IWM-to-ISM sequence and parameter RAM
  readback, moving the PoP SuperDrive probe past the previous
  `*** UNABLE TO LOAD PRODOS ***` screen.
- 2026-04-26: Added `apple2RomProbe --host-warmup-instructions=N` for direct
  boot-block probes. Running the real enhanced Apple IIe ROM before loading the
  PoP boot blocks initializes firmware vectors such as `$03F0=FA59`; the probe
  then explicitly reselects external Cx ROM via `$C006` before jumping to the
  boot blocks. Without that, the direct diagnostic path eventually jumped
  through an uninitialized `$03F0` vector to `$0000`, which was a probe setup
  artifact rather than a SuperDrive media failure. Current live boundary with
  `--host-warmup-instructions=200000`: the host no longer falls into `$0000`,
  but the boot blocks still spin around `$0896` with `$0C00` and `$2000` empty
  while the SuperDrive controller idles near `$98D3/$98D7`.
- 2026-04-26: Corrected the SuperDrive card model against the real `341-0438-a`
  firmware and the MAME `a2superdrive` map: host writes to `$Cn00-$CnFF` are
  ignored, while `$C800-$CFFF` remains shared RAM. After warmup the controller
  RAM page contains tiny entries such as `$C500=4C 59 FF`, `$C50D=4C 59 FF`,
  and `$C523=4C 59 FF`, with `$C55C` still zero. That means the direct
  `--prodos-boot-blocks` probe is not a valid boot path for this SuperDrive
  card when the boot code expects a Disk-II/ProDOS-style `$Cx5C` service entry;
  it is only a boot-block diagnostic. Do not add more `$Cx5C` shims to the
  SuperDrive card.
- 2026-04-26: Added `apple2RomProbe --superdrive35-media=<disk.po>` so the
  real SuperDrive controller path can attach the local PoP 3.5-inch media
  without also injecting the diagnostic boot blocks. Use this for `PR#5` /
  host-firmware experiments; `--prodos-boot-blocks` stays a boot-block
  diagnostic only.
- 2026-04-27: Removed the Apple IIc Plus detour from code and docs. The
  `apple2cplus` launcher aliases, 32 KB `341-0625-a.256` ROM loader,
  built-in IWM/MIG model, and `--apple35-media` probe path are gone. The PoP
  route is back to Apple IIe 128K. The new `Prince of Persia side A.woz` and
  `Prince of Persia side B.woz` files are WOZ1 5.25-inch images created by
  Passport.py, with metadata `requires_ram=128K` and
  `requires_machine=2e|2e+|2c|2gs`, so the next implementation layer is a WOZ
  5.25 Disk II bit/nibble stream, not IIc Plus firmware.
- 2026-04-27: Added read-only WOZ1 5.25 media support under the existing Disk II
  controller. Desktop launch config and `apple2RomProbe --disk=<disk.woz>` now
  recognize WOZ1 media and attach it to slot 6. The local PoP side A probe with
  the real Disk II PROM reaches the first address prologue, loads the boot
  sector, and stops at `$0801` when requested; a longer 200M-instruction probe
  initially exposed a circular-track bug in the WOZ bit-to-byte converter.
- 2026-04-27: Fixed WOZ circular-track LSS conversion by warming the shift/latch
  state across one full revolution before collecting bytes. PoP side A no
  longer corrupts the track-0 sector that straddles the WOZ chunk boundary, gets
  through the second-stage load, reads the custom non-DOS tracks, and reaches
  the `$49xx` language-card copy trampoline. That moved the live boundary away
  from image recognition or standard Disk II boot and temporarily exposed the
  post-custom-read BRK path at copied language-card `$F8D6` / vector `$04F0`.
- 2026-04-27: Fixed the next IIe compatibility gaps exposed by PoP side A:
  `ALTZP` now selects separate main/aux language-card RAM for `$D000-$FFFF`,
  and `$C019` now reports frame-clock vertical blank. The side-A WOZ probe now
  gets through title/game startup; injecting `<SPACE>` at the game's `$0CC2`
  keyboard poll reaches the first room and produces
  `build/apple2-pop-woz-side-a-space.png` with frame CRC32 `0x452C1071`.
- 2026-04-27: Added a small Apple II game-port pushbutton device for
  `$C061-$C063`, so PoP's button checks now route through named released-by-
  default input state instead of falling through the unmapped I/O default.
- 2026-04-27: Added basic Apple II game-port paddle timers: `$C070` triggers
  the timers and `$C064-$C067` return bit 7 until each centered-by-default
  paddle delay expires. Desktop Apple II input now maps left/right/up/down to
  paddle axes and `Space`/`Shift`/`Control` to pushbutton 0, fixing the manual
  PoP symptom where the prince immediately ran left because the joystick axis
  effectively read as zero.
- 2026-04-27: Replaced monochrome hi-res rendering with a first-pass Apple II
  artifact-color model: isolated pixels use violet/green or blue/orange by
  phase/high-bit, adjacent pixels render white, and off pixels render black.
  Added a first-pass Apple IIe double-hires renderer for `HIRES+80COL`, using
  aux+main HGR bytes and an AppleWin-style rolling 12-bit NTSC lookup instead of
  a fixed 16-color DHGR palette. The side-A title no longer looks vertically
  shredded, overly magenta, or crushed into coarse 140-color cells. The tuned
  YIQ-to-RGB coefficients make the palette warmer and keep yellow ornaments
  closer to the reference without changing the DHGR signal alignment, and
  non-idle black-ending NTSC sequences retain dark-blue color bleed instead of
  being crushed to black. The
  keyboard-latch title probe now writes
  `build/apple2-pop-woz-side-a-dhgr-space.png` with frame CRC32 `0x723291BA`.
- 2026-04-27: Brought the real Apple IIe + SuperDrive card path through an
  800 KB `.po` boot. The blocker after GCR decode was the onboard 65C02
  firmware executing CMOS `STA (zp)` opcode `$92`; it is now implemented only
  for `CMOS_65C02`, preserving NMOS illegal-opcode behavior. The larger
  SuperDrive fix was modeling Mac/SuperDrive drive-sense semantics rather than
  the older Apple IIgs-style function map: `$0A40/$0A41` select side, function
  comes from low phases plus side, and status `$0F` reports DD/800K media. A
  headless `PR#5` probe now boots the Apple II system disk to the ProDOS 8
  banner and boots the local PoP 3.5-inch image to hires graphics, dumping
  `build/apple2-superdrive/pop-superdrive-20m.png` with frame CRC32
  `0xF386BEDD`.

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
