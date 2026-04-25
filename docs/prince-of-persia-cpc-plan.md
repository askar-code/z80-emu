# Prince of Persia CPC Platform Plan

This is the working checklist for adding an Amstrad CPC platform capable of
running the CPC release of Prince of Persia. Keep this file updated as work
lands, so the next debugging pass can resume from the current state.

## Status Legend

- `[ ]` Not started
- `[~]` In progress
- `[x]` Done

## Target

- [x] Choose target platform: `Amstrad CPC 6128`
- [x] Keep the first target game explicit: `Prince of Persia` CPC disk release
- [x] Boot CPC firmware to a usable BASIC prompt
- [ ] Load a simple program from a CPC disk image
- [ ] Launch Prince of Persia from disk
- [ ] Reach Prince of Persia intro/menu
- [ ] Reach controllable gameplay

## Phase 1: Machine Skeleton

- [x] Add Gradle module `machine-cpc`
- [x] Add `CpcModelConfig` for CPC 6128 clock, RAM size, frame timing, and PSG clock
- [x] Add `CpcMachine`
- [x] Add `CpcBoard`
- [x] Add `CpcBus`
- [x] Add `CpcMemory`
- [x] Wire the existing Z80 CPU core into the CPC board
- [x] Add minimal tests for machine creation and CPU/bus execution
- [x] Register `--machine=cpc6128` in the desktop launcher
- [x] Add a thin CPC desktop runner adapter using the shared desktop runtime

## Phase 2: Memory And ROM Banking

- [x] Model the Z80 64K address space over CPC 6128 128K RAM
- [x] Support lower ROM mapping
- [x] Support upper ROM mapping
- [x] Support firmware ROM
- [x] Support BASIC ROM
- [x] Support AMSDOS or disk ROM mapping
- [x] Implement Gate Array RAM/ROM configuration bits
- [x] Add tests for RAM bank selection
- [x] Add tests for lower ROM enable/disable
- [x] Add tests for upper ROM selection

## Phase 3: Gate Array Video

- [x] Implement Gate Array palette registers
- [x] Implement CPC display modes 0, 1, and 2
- [x] Render screen pixels from CPC video RAM layout
- [x] Render border color
- [x] Add framebuffer tests for known byte patterns in each display mode
- [x] Expose CPC video through `VideoMachineBoard.renderFrame()`

## Phase 4: CRTC 6845

- [x] Add a CPC CRTC device
- [x] Implement core CRTC register reads/writes
- [x] Use CRTC start address for framebuffer rendering
- [~] Model basic frame geometry well enough for firmware and games
- [x] Add a basic Gate Array interrupt cadence for firmware services
- [x] Add tests for screen base changes
- [x] Defer exact raster effects until Prince or another test case requires them

## Phase 5: PPI 8255 And Keyboard

- [x] Add a CPC PPI 8255 device
- [x] Implement the subset of PPI mode behavior used by firmware and games
- [x] Add CPC keyboard matrix model
- [x] Connect keyboard scanning through the CPC I/O path
- [x] Add joystick mapping if the Prince disk supports joystick input
- [x] Add desktop keyboard controller for CPC keys
- [x] Add tests for key matrix row/column reads

## Phase 6: AY-3-8912

- [x] Reuse the existing AY device where possible
- [x] Wire CPC AY register select and data ports
- [x] Set explicit CPC PSG clock from `CpcModelConfig`
- [x] Connect AY audio to the shared PCM mono audio path
- [x] Connect AY I/O ports if needed for keyboard scanning
- [x] Add tests for AY port decoding

## Phase 7: Disk Support

- [x] Add standard `.dsk` image loader
- [x] Add extended `.dsk` support if the target Prince image requires it
- [x] Add a uPD765/FDC device
- [x] Implement FDC `read sector`
- [x] Implement FDC `seek`
- [x] Implement FDC `recalibrate`
- [x] Implement FDC `sense interrupt status`
- [x] Implement FDC `read ID` if firmware or Prince requires it
- [x] Model drive motor/status bits needed by firmware
- [x] Add tests using a tiny synthetic DSK image
- [~] Verify firmware can list or load a simple disk file

## Phase 8: Launcher And Debug Workflow

- [x] Define canonical CPC desktop launch command
- [x] Support CPC ROM and disk arguments in `DesktopLaunchConfig`
- [ ] Add optional I/O port tracing for CPC
- [ ] Add optional ROM/RAM banking trace
- [ ] Add optional FDC command trace
- [ ] Add framebuffer PNG dump support for CPC debug runs
- [ ] Consider a CPC headless probe launcher after the first real disk boot
- [ ] Document the CPC run/debug workflow

## Phase 9: Prince Of Persia Bring-Up

- [ ] Acquire a known-good CPC Prince disk image outside the repository
- [ ] Boot CPC firmware with the intended ROM set
- [ ] Mount the Prince disk image
- [ ] Run the disk boot command used by the image
- [ ] Trace the first failure point
- [ ] Add a focused regression test or probe for each emulator bug found
- [ ] Reach intro/menu
- [ ] Reach gameplay
- [ ] Verify keyboard controls in gameplay
- [ ] Capture a reference screenshot once gameplay is reached

## Phase 10: Compatibility Polish

- [ ] Tighten frame timing if gameplay, input, or audio expose timing drift
- [ ] Add missing CRTC behavior required by real software
- [ ] Add missing FDC behavior required by real software
- [ ] Improve keyboard latency and key mapping ergonomics
- [ ] Add CPC screenshots to documentation if useful
- [ ] Add CPC-specific notes to `docs/architecture.md`
- [ ] Keep Spectrum and Radio-86RK tests green after CPC changes

## First Implementation Slice

The first code slice should stay deliberately small:

- [x] Create `machine-cpc`
- [x] Add the CPC model, machine, board, bus, and memory shell
- [x] Wire Z80 execution through the CPC bus
- [x] Add RAM/ROM banking tests for the shell
- [x] Add launcher recognition for `--machine=cpc6128`

Do not start with FDC or video before the machine skeleton and memory model are
testable.

## Known Risk Areas

- [ ] uPD765/FDC behavior may need more than the simplest read path
- [ ] CPC screen memory layout and CRTC start address must be correct
- [~] CPC raster event phase currently uses a lower-display split heuristic;
  replace it with real CRTC display-enable timing when more CPC software is in scope
- [ ] CPC 6128 RAM banking bugs can look like random loader failures
- [ ] Timing can start approximate, but may need tightening for smooth gameplay
- [ ] Keyboard scanning crosses PPI/AY behavior and should be tested carefully

## Progress Log

- 2026-04-25: Selected Amstrad CPC 6128 as the target platform because it has an
  official Prince of Persia release and can reuse the existing Z80 and AY work.
- 2026-04-25: Created this working plan and checklist.
- 2026-04-25: Completed the phase 1 skeleton: added `machine-cpc`, CPC model,
  machine, board, bus, memory, Z80 execution, desktop launcher recognition, and
  a placeholder CPC desktop runner.
- 2026-04-25: Added the initial CPC 6128 memory model with lower ROM, upper ROM
  selection, 128K RAM banking configurations, and focused tests.
- 2026-04-25: Added initial Gate Array and CRTC devices: palette/border writes,
  mode 0/1/2 framebuffer decoding, CRTC register access, screen start address
  handling, and framebuffer tests.
- 2026-04-25: Added CPC PPI/keyboard input: mode 0 PPI subset, PSG register
  select/read/write handshake for keyboard scans, active-low 10x8 matrix,
  joystick 0 row mapping, desktop key controller, and protocol tests.
- 2026-04-25: Replaced the temporary CPC PSG shim with a shared `chip-ay`
  module. Spectrum 128 and CPC now use the same `Ay38912Device`; CPC keyboard
  scanning uses AY port A, and CPC desktop audio is wired to the shared PCM path.
- 2026-04-25: Added the first Gate Array interrupt generator: a latched 52-HSYNC
  maskable interrupt with acknowledge and mode-control bit 4 reset. The CPC
  firmware prompt now reaches a usable keyboard path in headless ROM testing.
- 2026-04-25: Started phase 7 disk support: added a standard CPCEMU `.dsk`
  loader, a polling uPD765/FDC subset on `&FA7E`/`&FB7E`/`&FB7F`, read-sector,
  read-ID, seek, recalibrate, sense-interrupt, drive-status, and synthetic DSK
  regression tests. Desktop CPC launch now accepts an optional `.dsk` argument.
- 2026-04-25: Extended the DSK loader for `EXTENDED CPC DSK` images. The local
  `prinpere.dsk` target image loads as 40 tracks, 1 side, with 9 sectors on
  track 0; its CP/M directory contains `PRINCE.BIN`.
- 2026-04-25: Fixed the first Prince black-screen hang after `RUN"PRINCE` by
  exposing an approximate VSYNC signal on PPI port B bit 0. The game was waiting
  on `IN A,(&F5xx)` after loading and the old fixed `0xFE` port-B stub kept
  VSYNC permanently inactive.
- 2026-04-25: Tightened Gate Array I/O decoding to `A15=0,A14=1` (`&7Fxx`
  style ports) after Prince exposed intermittent whole-screen palette changes.
- 2026-04-25: Corrected CPC frame timing from `80000` to `79872` t-states so a
  frame contains exactly six 52-HSYNC Gate Array interrupt periods. This removes
  the slow beat where Prince palette writes could be sampled as a whole-screen
  red/blue/white transient.
- 2026-04-25: Started repository cleanup after adding the third machine: grouped
  physical Gradle module directories under `apps/`, `platform/`, `cpu/`,
  `chips/`, and `machines/` while preserving the existing project names.
- 2026-04-25: Fixed Prince's mixed main-screen/HUD rendering by recording Gate
  Array screen mode and ink state per raster line. Prince switches mode/palette
  inside the frame, so rendering the whole frame with one final Gate Array state
  made either the main room or the `60 MINUTES` line look wrong.
- 2026-04-25: Refined the raster split capture to sample each scanline near the
  start of active display instead of at the end of the line. Prince enables the
  HUD mode/palette briefly and switches back before the scanline is over.
- 2026-04-25: Fixed the `60 MINUTES` overlay by aligning the active CPC display
  lower in the frame and rendering Gate Array mode/ink changes from per-frame
  events at display-byte timing. The HUD palette/mode changes occur near
  scanline 253, while the old frame geometry rendered the final text rows around
  scanline 229.
- 2026-04-25: Added a temporary desktop title marker
  `video=raster-events-v4` so live CPC windows can be distinguished from older
  emulator processes while validating the HUD rendering fix. The v4 pass also
  moves the horizontal display event sample later so Prince's `LEVEL 1` HUD text
  is rendered after the game finishes its late palette writes.
- 2026-04-25: Added opt-in CPC desktop diagnostics for the remaining manual-run
  HUD discrepancy: `z8emu.cpcAutoStartPrince` drives the same Prince startup
  sequence through the real Swing runner, and `z8emu.cpcFrameDumpDir` dumps the
  exact `FrameBuffer` handed to `FrameDisplayPanel.present()`.
- 2026-04-25: Fixed Gradle propagation for CPC desktop diagnostic system
  properties and added `:app-desktop:cpcPrinceDebug`, which launches
  `cpc6128.rom` + `prinpere.dsk` with frame dumps enabled at
  `/tmp/z8-cpc-manual-dump`.
- 2026-04-25: Extended CPC frame dumps with `.txt` sidecars containing CPU,
  CRTC, and completed Gate Array raster events so manual-run HUD corruption can
  be compared against clean autostart frames by exact mode/palette timing.
- 2026-04-25: Fixed the manual-run Prince HUD corruption root cause: the live
  run can shift the Gate Array event phase by about eight scanlines relative to
  the fixed output crop. Rendering now derives a per-frame display event offset
  from the lower HUD mode/palette split instead of always sampling events at
  `BORDER_TOP + y`.
- 2026-04-25: Centered the visible CPC active display in the 272-line desktop
  frame by making the top and bottom borders 36 lines each. Gate Array event
  sampling remains independently phase-aligned, so the Prince HUD fix no longer
  depends on a visually low output crop.
- 2026-04-25: Removed temporary CPC desktop diagnostics after validating the
  HUD fix: the Prince autostart helper, frame PNG/sidecar dumper,
  `video=raster-events-v4` title marker, debug system properties, and
  `:app-desktop:cpcPrinceDebug` task are no longer in the normal runner.
- 2026-04-25: Left the remaining CPC display-phase heuristic documented as
  technical debt: `CpcGateArrayDevice` still derives event sampling phase from
  the lower raster split until the CRTC has real display-enable/raster counters.
