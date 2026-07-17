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
- [x] Load a simple program from a CPC disk image
- [x] Launch Prince of Persia from disk
- [x] Reach Prince of Persia intro/menu
- [x] Reach controllable gameplay (all locked by cpcPrinceSmoke
  frameCrc32=0x658B018F; cpcBasicSmoke locks the boot banner at
  0x2A1A5DBE)

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
- [x] Verify firmware can list or load a simple disk file (2026-07-16:
  cpcPrinceSmoke types RUN"PRINCE through the firmware and AMSDOS loads
  the game off media/prinpere.dsk end-to-end)

## Phase 8: Launcher And Debug Workflow

- [x] Define canonical CPC desktop launch command
- [x] Support CPC ROM and disk arguments in `DesktopLaunchConfig`
- [x] Add optional I/O port tracing for CPC (CpcBoard.setIoTraceSink →
  bus IoAddressSpace; probe --trace-io; all decoded ports covered)
- [x] Add optional ROM/RAM banking trace (covered by --trace-io: gate
  array RAM config and &DF00 ROM select are IoAddressSpace-decoded)
- [x] Add optional FDC command trace (CpcFdcDevice.TraceSink,
  command+result with args/ST0; probe --trace-fdc; pinned by
  CpcFdcDeviceTraceTest)
- [x] Add framebuffer PNG dump support for CPC debug runs
  (--dump-frame via shared ProbeOutput.writePng)
- [x] CPC headless probe launcher: CpcRomProbeLauncher with C64 option
  parity (--disk/--keys/--keys-after-frames/--press-key-after-frames/
  --expect-frame-crc/--dump-frame/--stop-pc/--watch-addr/
  --profile-pc-top/--trace-io/--trace-fdc) + generic cpcRomProbe task.
  Note: prefer an unused address for --stop-pc sentinels — 0xFFFF holds
  a firmware RET and fires early.
- [x] Document the CPC run/debug workflow (this plan + the probe usage
  line; CpcKeyboardTyper cadence frozen at PRESS=2/GAP=2, BASIC settle
  = 300 frames, proven by the POKE &4000,42 firmware round-trip test).
  Canonical manual probe run (NOTE: gradle's --args parser rejects a
  literal double quote — use the script escape \x22):
  `./gradlew -q :app-desktop:cpcRomProbe --args='media/cpc6128.rom
  30000000 --disk=media/prinpere.dsk --keys=RUN\x22PRINCE<CR>
  --keys-after-frames=300 --press-key-after-frames=1500:<SP>
  --dump-frame=build/cpc/prince.png'`

## Phase 9: Prince Of Persia Bring-Up

- [x] Acquire a known-good CPC Prince disk image outside the repository
  (media/prinpere.dsk, extended CPC DSK, gitignored)
- [x] Boot CPC firmware with the intended ROM set (media/cpc6128.rom)
- [x] Mount the Prince disk image (probe --disk / desktop positional)
- [x] Run the disk boot command used by the image (RUN"PRINCE via
  CpcKeyboardTyper — the quote is SHIFT+2, now typeable)
- [x] Trace the first failure point (none left — the 2026-07-16 audit
  played through to level-1 gameplay; earlier fixes in the progress log)
- [x] Add a focused regression test or probe for each emulator bug found
  (the locked smokes are the standing regression gates)
- [x] Reach intro/menu — cpcPrinceSmoke passes the title
- [x] Reach gameplay — cpcPrinceSmoke locks a level-1 gameplay frame
  with the HUD split: frameCrc32=0x658B018F (30M instructions, ×3)
- [x] Verify keyboard controls in gameplay (audit: joystick-0 right and
  cursor-right both move the prince; smoke advances the title by <SP>)
- [x] Capture a reference screenshot once gameplay is reached
  (build/cpc/prince.png regenerated by every cpcPrinceSmoke run)

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

- 2026-07-17: Phase 10d HYPOTHESIS FALSIFIED by prototypes (no batch):
  bumping ALL −4t instruction classes in a scratch core (ED OUT/IN 16t,
  PUSH/RST 16t, IM1 16t, block I/O and LDI/LDD +4) moves the seam's
  LEFT edge only from x=268 to x=332 and then stops; the RIGHT edge
  x=661 is invariant to CPU timing AND to the horizontal event-mapping
  constant (DISPLAY_START_TSTATES swept 80..176 moves only the left
  edge). Conclusion: the residual seam is an EVENT-TO-ROW ATTRIBUTION
  question (the tail of display row 191 renders with a palette the
  hardware applies from row 192), NOT an instruction-timing deficit —
  cpu-z80 per-opcode surgery is OFF the table for this bug. Next
  investigation (cheap, render-side): dump the completed-frame event
  list around pass lines 262-264 for the canonical frame, identify the
  event painting the row-191 tail, and determine the hardware-correct
  attribution (likely an off-by-one in the event-line vs pixel-row
  convention interacting with the game's mid-line write). Also for the
  record: the canonical scene's seam history — Phase 8/10a: 0 red
  (heuristic hid it by construction), 10b: 190, 10c: 172, prototypes:
  166 floor.
- 2026-07-17: Phase 10c (`codex/cpc-p10c-waitstates`): gate-array wait
  states — every Z80 bus access aligns to the 4t 1 MHz grid via the
  CpuBus *WaitStates hooks (Spectrum-contention pattern; port stamps
  wait-adjusted). Root-caused from the user-reported red seam at
  display line 191: reference (CPC-Power) shows none; a −16t INT-phase
  prototype changed zero pixels (falsified); the machine ran ~10-25%
  fast without wait states. Seam narrowed 60→42 red samples (left
  third cleared); committed oracle rowRedSamples(227) < 50. Residual =
  frozen deviations that accumulate (grid-invisible −4t: OUT (C),r
  12t vs hw 16t — Prince's split burst; PUSH/internal-cycle class; IM1
  accept 14-17t vs ~16t). Rebaseline #3: Prince smoke 0x1A1D7BE5,
  transition 0xD8C9A44B, flash 0x6DA07FC9; BASIC smoke unchanged;
  settle/cadence/press-key constants all survive; frame counts +13-15%
  (BASIC 384→433). Fault injection 5/5. PHASE 10d CANDIDATE (the
  complete fix): cpu-z80 internal-cycle phase advancement + exact
  per-opcode CPC tables, coordinated with a Spectrum contention
  re-verification — the deepest-core change in the repo; take it only
  with the full pipeline and both machines' suites as gates.
- 2026-07-17: Phase 10b (`codex/cpc-p10b-vsync`): CPC timing anchored to
  real CRTC state. CpcCrtcDevice gained vertical counters + R7-derived
  VSYNC (frozen 16-line width, type-1 behavior); the gate array applies
  the hardware R52/VSYNC re-sync (2 HSYNCs after VSYNC start, interrupt
  iff counter >= 32 — NOTE: cpctech/grimware document this rule
  INVERTED; CRTC Compendium 27.3.2 + Caprice32 are authoritative); PPI
  port B reads the real VSYNC; render passes are VSYNC-anchored with a
  CRTC-latched display top (the 10a debounced anchor deleted); the
  border framebuffer mapping got its missing +36. Frozen reset
  convention (reset == VSYNC start) kept the PPI window and frame
  length identical to the old stubs; interrupt phase shifted +512 t
  (sanctioned). Rebaseline #2: cpcPrinceSmoke 0x658B018F→0x83FBECD8
  (exactly one raster-split line moved), oracle CRCs
  0x47960CBC/0xF080DDB7 (route numbers unchanged); cpcBasicSmoke
  0x2A1A5DBE UNCHANGED (proven mapping-invariant). Spec critiqued twice
  (22+12 findings), fault injection 8/8, review minors fixed with
  re-proven kills. Both Phase-4/8 'Known Risk Areas' approximations
  (event-phase heuristic, time-derived VSYNC) are now closed.
- 2026-07-17: Phase 10a (`codex/cpc-p10a-anchor`): fixed the flickering
  red stripes on Prince room transitions (user-reported). The
  display-phase anchor is now debounced (adopt after 2 consecutive
  equal candidates) and sticky (holds through candidate-free/transient
  frames) instead of re-derived per frame; displayEventTop() deleted.
  Root-cause evidence: PoP uses firmware-default CRTC values and never
  resets the R52 counter, so no machine-state formula can replace the
  event-pattern detection — stabilization was the correct minimal fix.
  New oracle CpcPrinceTransitionTest replays the transition route
  (f=102 red samples 754→46, locked CRC 0x35DEF6C2; f=147 is the game's
  own fall-damage flash, byte-identical 0x4A61AEDD). Probe gained
  --hold-key with cursor-key names. Both smoke CRCs byte-identical;
  fault injection 5/5 (A-none-A gap closed at review). Deferred to a
  possible Phase 10b: true VSYNC-anchored display timing + R52/VSYNC
  re-sync.
- 2026-07-16: Campaign audit (2 agents, checkbox claims verified against
  code) found the machine already plays Prince end-to-end; Phase 8
  landed as `codex/cpc-p8-tooling` (spec critiqued: 15 findings incl.
  the CpcKeyboardController comma/period swap that would have poisoned
  CpcKeyMap; review 1 minor fixed; fault injection 6/6): CpcKeyMap/
  CpcKeyboardTyper/CpcRomLocator, observation-only IO+FDC trace seams,
  CpcRomProbeLauncher (+cpcRomProbe task), locked gates cpcBasicSmoke
  0x2A1A5DBE and cpcPrinceSmoke 0x658B018F (boot→RUN"PRINCE→title→
  level-1 gameplay+HUD in one CRC). Phases 7/9 checkboxes retro-checked
  with evidence. KNOWN DESKTOP BUG (Phase 10 candidate):
  CpcKeyboardController swaps VK_COMMA/VK_PERIOD vs the real matrix and
  lacks a colon mapping — CpcKeyMap is authoritative, the controller
  needs the same table.
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
