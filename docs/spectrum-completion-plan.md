# ZX Spectrum 48K/128K Completion Plan

Рабочий контракт завершения уже существующей реализации Spectrum. Создан
2026-07-18 после повторного аудита кода, тестов и реальных загрузок. Этот файл
является источником правды: найденные дефекты, обязательные дополнения и
результаты проверок должны сначала отражаться здесь, затем закрываться кодом.

## Status legend

`[ ]` not started · `[~]` in progress · `[x]` verified · `[!]` blocked

## Target and definition of done

Цель v1 — честная и воспроизводимая эмуляция **stock ZX Spectrum 48K** и
**оригинального ZX Spectrum 128K**:

- стандартные ROM, клавиатура, ULA, 48K/128K memory/paging и AY-3-8912;
- cold boot и загрузка распространённых TAP/TZX без ручного вмешательства;
- runtime insert/eject/rewind/play и смена стороны/образа ленты;
- Kempston и Sinclair 1/2 joystick profiles;
- загрузка и сохранение распространённых `.sna` и `.z80` snapshots;
- положительные, воспроизводимые CPU, ROM, tape, video и audio gates;
- clean checkout проходит обычные unit tests без proprietary media.

Не входят в обязательный stock v1: +2A/+3, Beta Disk/TR-DOS, `.trd/.scl`,
Interface 1/Microdrive и точное моделирование конкретных аналоговых ревизий
ULA. Они оформляются отдельным scope, если будут заявлены как поддерживаемые.

Статус на старте плана: функционально зрелая beta — базовые игры 128K реально
загружаются, но текущие зелёные тесты не доказывают заявленную точность и
несколько обычных пользовательских сценариев сломаны.

## Audited baseline (2026-07-18)

- [x] `:machine-spectrum:test --rerun-tasks`: 56 tests passed locally.
- [x] `:cpu-z80:test --rerun-tasks`: 36 tests passed locally.
- [x] `:app-desktop:test`: 21 tests passed locally.
- [x] Stormlord 128K headless path reached gameplay deterministically:
  `PC=880E`, `t=1137785183`, tape block `6/6`.
- [x] Robocop Side A loads with local ignored media, but its regression oracle
  is negative-only and therefore is not yet an acceptance gate.
- [x] Audit found that `zexTest` can pass despite ZEX reporting failures.
  Direct output: zexdoc has one failing group (`<daa,cpl,scf,ccf>`), zexall
  has twelve failing groups.
- [x] This audit did not modify the existing C64 plan or its follow-up file.

## Phase 0 — Honest, hermetic verification

The build must fail on real regressions and must work without local ROM/game
files before accuracy work can be trusted.

### Z80 gates

- [x] Make `ZexHarnessTest` fail on `ERROR`/failed groups, not merely require
  `Tests complete`.
- [x] Keep the fast default unit suite, but add `zexDocTest` and `zexAllTest`
  reference gates. `spectrumVerification` includes the green documented gate;
  `spectrumAccuracyVerification` adds the strict target gate.
- [x] Make zexdoc fully clean; store/report failing group output on failure.
- [x] Make zexall fully clean. Treat undocumented flags, WZ/MEMPTR and Q as
  CPU correctness work, not as test exclusions.

### Reproducible Spectrum gates

- [x] Remove unconditional dependence of the regular test task on ignored
  `media/128.rom` and `media/RobocopA.tzx`.
- [x] Move proprietary-media checks into an explicit optional E2E task. Missing
  media must produce a clear skip/precondition message, not break clean clone.
- [x] Give every headless probe positive expectations (`PC`, frame CRC, screen
  text, tape EOF/block as applicable) and a non-zero exit on mismatch/timeout.
- [x] Add a root `spectrumVerification` aggregate covering CPU unit tests,
  zexdoc, Spectrum unit tests, desktop tests and available media smokes. Keep
  strict zexall in the explicit `spectrumAccuracyVerification` target wall.
- [x] Add deterministic positive smokes:
  - [x] 48K ROM/BASIC boot;
  - [x] 48K cold-start TAP load;
  - [x] 128K menu boot;
  - [x] 128K TAP/TZX cold-start load;
  - [x] Stormlord gameplay frame/state;
  - [x] Robocop post-load selection and keyboard-game scenes when local media
    are available.
- [x] Update the Robocop runbook budget from the stale 1.4B t-states to the
  measured safe bound (about 2.0–2.1B).
- [x] Freeze the Robocop post-load scene as a positive expected result and
  reproduce both frame hashes in two independent runs.
- [x] Add CI only after the hermetic subset is genuinely reproducible.

## Phase 1 — Confirmed Z80 correctness defects

- [x] Fix `LD R,A`: all eight bits of A replace R. Do not preserve old R bit 7.
- [x] Keep the CPU in HALT when a maskable interrupt is pending but `IFF1=0`.
- [x] Diagnose and fix the zexdoc `<daa,cpl,scf,ccf>` group instead of accepting
  completion text. Check DAA plus the Q/undocumented flag interaction.
- [x] Add the missing internal/no-MREQ phases for indexed memory operations,
  `INC/DEC (HL)`, stack/control-flow operations, block instructions, interrupt
  acknowledge and HALT fetches.
- [x] Expose the address/phase needed for Spectrum contention during internal
  cycles; raw cycle totals alone are insufficient.
- [x] Implement the WZ/MEMPTR and Q behavior required by the remaining zexall
  failures and real software.
- [x] Re-run all other Z80 machines after each timing batch.

Acceptance: strict zexdoc and zexall both complete without error markers, and
all machine modules using `cpu-z80` remain green.

## Phase 2 — ULA, I/O and contention timing

### Confirmed defects

- [x] Replace `SpectrumContentionModel.ioPortDelay` summation with sequential
  ULA contention points that account for waits already inserted. The known
  48K boundary example must be 6 wait states, not 15.
- [x] On 128K, contend high port addresses mapped through a currently paged odd
  RAM bank, not only the fixed `0x4000–0x7FFF` range.
- [x] Decode the ULA on every even port (`A0=0`), not only `xxFE`; define and
  test overlap behavior with other partially decoded devices.
- [x] Timestamp IN/OUT at the real I/O data phase rather than roughly three
  t-states early. Preserve CPC and other machine semantics when changing the
  shared bus contract.
- [x] Stop double-advancing ULA after a mid-instruction FE/7FFD write. Boards
  should synchronize to absolute machine time.
- [x] Synchronize ULA before a CPU write mutates screen RAM, so earlier fetches
  cannot observe future bytes.
- [x] Capture bitmap and attribute bytes at their distinct ULA fetch phases.
- [x] Remove the hardware-false automatic write to the ROM-owned 128K `BANKM`
  system variable at `0x5B5C`.

### Accuracy completion

- [x] Align border raster coordinates with the physical Spectrum scan sequence;
  verify timing-sensitive border bars with whole-frame CRCs.
- [x] Quantize 48K border-colour changes to the real 4T ULA latch and keep the
  visible raster output origin separate from floating-bus fetch timing.
  Kosarev's open `screen_timing` program now runs hermetically to its IM2/HALT
  draw loop and matches every pixel in the published visual window; two
  independent cold-ROM TAP runs reproduced the same zero-difference result.
- [x] Cover floating bus values and timing at representative frame positions,
  including idle slots, successive byte groups/lines and 128K shadow screen.
- [x] Reconcile the floating-bus absolute t-state origin with Fuse's
  unattached-port path: the shared interrupt-origin clock maps the first fetch
  to 14338/14364, exactly three t-states after each model's contention start.
- [x] Define reset RAM behavior explicitly; RESET must not accidentally clear
  only whichever 128K banks happen to be mapped.
- [x] Add FLASH cadence and shadow-screen whole-frame tests.
- [x] Track ULA snow and EAR revision/analogue behavior as explicit accuracy
  limits until implemented. Undioded keyboard-matrix ghosting is implemented.

Acceptance: focused contention/port/floating-bus tests pass on both models,
and at least one external timing-sensitive visual test has a stable oracle.

## Phase 3 — Tape engine and real cold-start flow

### Blocking defects

- [x] 128K autoplay must wait for the ROM menu before pressing Enter; the old
  fixed 12-frame key hold expires long before the menu appears.
- [x] 48K autoplay must type `LOAD ""`, wait for the ROM loader routine and
  only then start the tape.
- [x] Fix play-after-EOF and rewind at non-zero machine time. Rewinding tape
  position must not reset the absolute machine clock and immediately fast
  forward back to EOF.
- [x] Make desktop and headless probe use the same tested cold-start state
  machine rather than two different autoplay implementations.

### TZX correctness and coverage

- [x] Fix pause signal polarity/end level.
- [x] Convert TZX pulse units (3.5 MHz reference) to each machine clock,
  including original 128K timing.
- [x] Publish an honest supported-block matrix in
  [`spectrum-media-support.md`](spectrum-media-support.md).
- [x] Implement executable control flow: jump, loop, call/return and select
  blocks (`0x23–0x28`).
- [x] Implement and validate set signal level (`0x2B`).
- [x] Handle commonly encountered signal blocks without false claims:
  - [x] direct recording (`0x15`) with explicit sampled levels;
  - [x] CSW RLE/Z-RLE (`0x18`) with arbitrary source sample rate;
  - [x] generalized data (`0x19`) rejected before playback with the exact
    unsupported block id until its non-trivial symbol model is implemented.
- [x] Add malformed/truncated image tests and execution limits for hostile
  control-flow blocks.

Acceptance: 48K and 128K cold-start smokes work from reset, replay after EOF
works at arbitrary machine time, and supported TZX behavior is deterministic.

## Phase 4 — Mandatory user-facing completion

These capabilities were absent from the older generic architecture plan but
are required before calling a desktop stock 48K/128K emulator complete.

- [x] Runtime tape insert, eject and replace without restarting the emulator.
- [x] Tape block browser/selection and a practical multi-side workflow:
  previous/next block and side replacement are implemented; a named graphical
  block list remains optional product polish.
- [x] `.sna` load/save for supported 48K/128K variants:
  - [x] canonical 48K codec and atomic machine capture/restore;
  - [x] 48K launch-time and runtime desktop load plus atomic save;
  - [x] original 128K variants;
  - [x] 128K desktop load/save path.
- [x] `.z80` load/save for common v1/v2/v3 48K/128K variants, with explicit
  errors for unsupported extensions:
  - [x] 48K v1 raw and ED-ED compressed codec plus machine capture/restore;
  - [x] 48K v1 launch-time and runtime desktop load plus compressed save;
  - [x] v2/v3 and 128K memory-page variants;
  - [x] v2/v3/128K desktop load/save path.
- [x] Kempston joystick device and host mapping.
- [x] Sinclair Interface 2 joystick profiles 1 and 2.
- [x] Thread-safe host keyboard/joystick state transfer.
- [x] Keyboard matrix ghosting (or a documented compatibility toggle if exact
  matrix behavior conflicts with host-key rollover).
- [x] Persist user-selectable input/media settings where the desktop platform
  already supports preferences.

Acceptance: a user can boot, load, play, save/restore state and change media
for representative 48K and 128K games without restarting the application.

## Phase 5 — AY/beeper and final compatibility pass

- [x] Timestamp AY and beeper register changes inside instructions so generated
  audio does not apply writes only at instruction boundaries.
- [x] Add deterministic AY vectors for tone, noise LFSR, mixer gating, all
  envelope shapes and amplitude behavior.
- [x] Add at least one PCM CRC/music-scene regression for 128K.
- [x] Define turbo-mode audio behavior so accelerated loading cannot silently
  corrupt or indefinitely overrun the realtime audio queue.
- [x] Run a documented compatibility matrix of representative BASIC programs,
  48K games, 128K games, turbo loaders and timing-sensitive demos.
- [x] Revalidate Robocop side A's post-selection gate after exact internal Z80
  timing. A frame-by-frame trace proved the old three-frame key pulse ended
  inside the game's `AC75` `EI`/`HALT`/`DJNZ` delay before the menu scanner.
  Holding `1` for 12 frames reaches the unchanged keyboard-game hash three
  frames after release. The gate now uses a bounded 120-frame semantic wait,
  fails if the known broken system-menu hash appears first, and passed two
  independent external-media runs.
- [x] Replace the false-positive GOTHIK completion oracle. The old first EOF
  scene (`Block ? 2F` / `Rewind to 00`, PC around `CE06`-`CE0E`) was the loader
  requesting another tape pass, not a completed game load. The real TAP gate
  now replays all 154 blocks and reaches title plus gameplay; the original TZX
  preserves its inter-stage pause and reaches the same scenes in one pass.
  The regular 48K smoke now freezes the TZX title rather than the TAP rewind
  prompt. Desktop turbo frame publication is synchronized and its UI updates
  are coalesced so rendering cannot tear or starve input.
- [x] Replace stale Spectrum sections in `docs/architecture.md` with links to
  implemented modules, this plan, supported media matrix and known limits.

## Required command gates

The aggregate tasks are `spectrumVerification` (supported wall) and
`spectrumAccuracyVerification` (adds strict zexall). Every completed batch also
runs the relevant subset explicitly:

```text
./gradlew :cpu-z80:test
./gradlew :cpu-z80:zexDocTest
./gradlew :cpu-z80:zexAllTest
./gradlew :machine-spectrum:test
./gradlew :app-desktop:test
```

For changes to shared CPU/bus timing, also run all dependent machine tests (at
minimum CPC, Radio-86RK and Spectrum) rather than validating Spectrum alone.

## Progress log

- 2026-07-18 — fresh audit completed; plan created. Work started in parallel
  on honest ZEX gates/Z80 defects, ULA/I/O timing, and tape/autostart.
- 2026-07-18 — regular Spectrum tests made hermetic; the ignored-media Robocop
  scenario moved to `:machine-spectrum:externalMediaTest`. Both regular and
  external-media tasks passed locally. Root `spectrumVerification` task added;
  the separate target wall stays red until strict zexall and positive smokes
  are complete.
- 2026-07-18 — fixed `LD R,A`, masked-INT HALT behavior and full-input DAA.
  Strict zexdoc is green. The honest zexall gate now reports eleven remaining
  undocumented flag/WZ/Q groups; it remains a required accuracy target rather
  than being hidden from the supported green verification wall.
- 2026-07-18 — fixed sequential 48K/128K I/O contention, dynamic 128K high-port
  contention, all-even ULA decode with overlapping device fan-out, false BANKM
  writes and ULA double-advance. Independent `:machine-spectrum:test` run is
  green with 67 tests.
- 2026-07-18 — unified desktop/headless cold-start state machine, added real
  48K `LOAD ""` entry and menu-timed 128K Enter, and fixed replay/rewind at
  non-zero machine time. Real GOTHIK 48K and Stormlord 128K probes reached the
  ROM loader with tape playing; independent desktop suite is green (23 tests).
- 2026-07-18 — timestamped active-screen RAM mutation at the end of each Z80
  write cycle, split bitmap/attribute ULA fetches into T/T+1 events, and added
  FLASH plus 128K bank 5/7 whole-frame CRCs. Full Spectrum suite: 81/81 green.
- 2026-07-18 — implemented CP/LDI/LDD/CPI/CPD undocumented flags, BIT via
  WZ/MEMPTR, Q-sensitive SCF/CCF and broader WZ updates. Strict zexdoc and the
  full zexall suite now both pass with no exclusions or accepted error groups.
- 2026-07-18 — added positive 48K BASIC and 128K Tape Loader ROM smokes with
  fixed whole-frame CRCs and honest skip behavior when optional ROMs are not
  present. Headless ROM/tape probes now fail on declared expectation mismatch.
- 2026-07-18 — corrected TZX pause end level, cross-model pulse scaling with
  fractional carry and `0x2B` signal-level handling; published the executable
  tape block matrix in `spectrum-media-support.md`.
- 2026-07-18 — froze the optional Robocop Side A regression at both the control
  selection frame and the post-selection keyboard-game frame; both hashes were
  reproduced in two independent external-media runs.
- 2026-07-18 — added strict Kempston port decode with wired-AND device overlap,
  Cursor/Kempston/Sinclair 1/Sinclair 2 host profiles, and synchronized rebuild
  of keyboard plus joystick state. Spectrum/app suites were green at 89/29.
- 2026-07-18 — added optional cold-start tape gates with full positive oracles:
  GOTHIK 48K reaches EOF `154/154` at `PC=CE0E`, CRC `9C77B8C9`;
  Stormlord 128K reaches keyboard gameplay at `PC=8F46`, CRC `310D3A7E`.
  Both new Gradle smoke tasks passed from reset with all five expectations.
- 2026-07-18 — implemented canonical 48K SNA load/save and Z80 v1 raw plus
  ED-ED-compressed load/save behind an immutable snapshot model. SNA save does
  not modify the live stack, malformed loads are atomic, and 19 focused codec
  and machine-state tests pass. 128K and Z80 v2/v3 remain open explicitly.
- 2026-07-18 — made RAM reset semantics explicit: construction zero-fills every
  physical bank for deterministic power-on, while warm RESET preserves all
  three 48K or all eight 128K banks and restores only stock paging/device state.
  Focused all-bank RESET tests and the full Spectrum module suite are green.
- 2026-07-18 — implemented TZX direct recording `0x15` and CSW `0x18` in both
  RLE and zlib-wrapped Z-RLE forms, with model-clock scaling, exact pulse-count
  validation, malformed-stream limits and spec-accurate final-level/pause
  behavior. Generalized data `0x19` remains an explicit early unsupported error.
- 2026-07-18 — connected 48K SNA/Z80 v1 to the desktop: launch with a snapshot,
  runtime load via Cmd+Shift+O and atomic save via Cmd+Shift+S. Parsing finishes
  before machine mutation, existing-file replacement is confirmed, and 128K or
  v2/v3 inputs fail explicitly instead of being misread as tape. App suite 37/37.
- 2026-07-18 — added deterministic AY PCM vectors for tone, the 17-bit noise
  sequence, mixer gating, all 16 envelope shapes and all 16 fixed amplitude
  levels. `:chip-ay:test` is green and is now part of `spectrumVerification`.
- 2026-07-18 — reconciled floating-bus origin against Fuse's unattached-port
  implementation: first screen bytes now appear at 14338 (48K) and 14364
  (128K), both `contentionStart + 3`, rather than the prior Ramsoft-origin
  14347/14368 constants. Focused idle/fetch/line/shadow-bank tests are green.
- 2026-07-18 — mapped border writes from the physical top-left pixel timing
  rather than centering the cropped framebuffer inside the machine frame.
  The focused border-placement and whole-frame CRC regression is green.
- 2026-07-18 — implemented undioded Spectrum keyboard-matrix ghosting by
  resolving connected row/column components. Direct, multi-row, three-key
  phantom-key, disconnected-component and bridge-release tests are green.
- 2026-07-18 — added runtime TAP/TZX replacement (`Cmd+O`), eject (`Cmd+E`),
  previous/next block navigation and queued play/rewind/stop. All mutations run
  on the emulation thread, malformed replacements leave the mounted side
  untouched, autoplay keys are released on eject, and live status exposes
  file, transport, block and turbo state. Machine and desktop suites are green.
- 2026-07-18 — implemented parse-time TZX program execution for signed jumps,
  loops, call/return and select (`0x23–0x28`). Physical and generated block
  counts, executed instructions and pending pulses are bounded; malformed
  targets/stacks/nesting and runaway programs fail before playback. Focused,
  full machine and desktop suites are green.
- 2026-07-18 — replaced the architecture document's obsolete 48K MVP/future
  128K/AY/Radio roadmap with the implemented multi-machine module map and links
  to this completion plan, the executable media matrix and the headless probe
  runbook.
- 2026-07-18 — added a minimal GitHub Actions Spectrum verification job on
  Temurin 21. It runs the canonical hermetic `spectrumVerification` wall;
  optional proprietary ROM/game probes skip cleanly when media is absent. The
  aggregate passed locally, as did an explicit no-media smoke simulation.
- 2026-07-18 — documented non-claims for early-ULA snow, analogue EAR input and
  board-revision filtering. These remain explicit fidelity limits rather than
  being implied by the green contention/floating-bus/tape gates.
- 2026-07-18 — added core snapshot support for 128K SNA (normal and duplicate
  fixed-page layouts) plus Z80 v2/v3 48K/128K compressed and raw pages. Restore
  includes all eight banks, 7FFD, CPU state, border and AY state; unsupported
  expansion machines fail before mutation. Snapshot tests are 44/44 and the
  full Spectrum module is 163/163. Arbitrary v3 mid-frame phase remains a
  documented runtime-clock limitation.
- 2026-07-18 — connected matching-model 48K/128K SNA and Z80 v1/v2/v3 loads to
  launch-time and runtime desktop paths. Both machines atomically save
  compressed canonical Z80 v3 (including 48K `PC=0000`); 128K SNA warns before
  and after its format-level AY loss. Model mismatches fail before tape/input
  state changes. Desktop tests are 48/48 and snapshot tests are 44/44.
- 2026-07-18 — moved Spectrum IN/OUT observation to the Z80 I/O data phase
  (`instruction phase + sequential waits + 3T`) and added absolute Spectrum
  audio timelines. Focused tests prove that FE beeper and 128K AY changes split
  old/new PCM at that edge; Spectrum, AY and CPC suites are green.
- 2026-07-18 — defined bounded turbo audio semantics: entering and leaving
  turbo synchronously flushes the host line and discards queued accelerated
  PCM, while turbo continuously drains generated audio instead of allowing an
  unbounded/replayed backlog. Desktop and platform suites are green.
- 2026-07-18 — published `spectrum-compatibility.md` with strict media,
  synthetic, untested and missing-corpus labels. Official ROMs, GOTHIK,
  Stormlord, two Robocop scenes and Heartbroken title/menu have repeatable
  positive oracles. GOTHIK's EOF and frame CRC stayed unchanged through the
  CPU timing work. The open Kosarev `screen_timing` corpus was selected to
  close the remaining executable visual-timing row.
- 2026-07-18 — completed the desktop media/input workflow: block stepping and
  side replacement satisfy the required tape browser path (a named list remains
  optional polish), while Cmd+J cycles Cursor/Kempston/Sinclair 1/Sinclair 2.
  The selected profile and last valid tape/snapshot directories persist across
  launches; an explicit JVM joystick property remains the highest-priority
  override. The rerun desktop suite is green.
- 2026-07-18 — added a hermetic 128K two-note phrase that executes real Z80
  `OUT (C),A` instructions from RAM and crosses the Spectrum port decoder, AY
  and board mixer. It freezes PCM byte count `3196` and CRC `A1C662D5`
  while separately asserting silence, both waveform polarities, transitions
  and final AY registers; two independent reruns are identical.
- 2026-07-18 — selected Kosarev's MIT-licensed `screen_timing` program as the
  missing real timing corpus and ran its TAP through the official 48K ROM. It
  reaches EOF `4/4` and its drawing loop, but comparison with the upstream
  reference image found a genuine remaining bug: screen pixels agree while all
  eight border-transition rows reveal unquantized border writes (12.21% of the
  normalized crop differs). A 4T ULA border-latch fix and enabled visual oracle
  are now required before the external-timing and compatibility rows can close.
- 2026-07-18 — fixed the defect exposed by Kosarev: screen output is anchored
  at t-state `14340`/`14366` independently from floating-bus fetches at
  `14338`/`14364`, and FE border colour is latched on the hardware 4T raster
  grid. The source-attributed fixture embeds the unmodified early-timing TAP,
  published PNG and complete MIT license; its blank-ROM direct-load gate checks
  SHA-256, IM2 setup, HALT progress and every published pixel with zero
  differences. Two cold-ROM runs were byte-identical at EOF `4/4`, PC `833F`,
  t-state `95996272` and CRC `CA6E72D0`; the full Spectrum suite is green.
  Three final optional-smoke runs also reproduced 48K BASIC
  `15DE/A6E9608F`, GOTHIK `CE0E/9C77B8C9`, Stormlord `8F46/310D3A7E` and the
  unchanged 128K boot oracle, so both remaining plan items are now verified.
- 2026-07-18 — completed the audited Fuse internal/no-MREQ inventory. `CpuBus`
  now exposes address (or an explicit addressless acknowledge), phase, length
  and read/write/acknowledge type; the Z80 emits the exact indexed, CB/RMW,
  16-bit, stack/control, block, interrupt and repeated-HALT sequences. Spectrum
  evaluates contention sequentially for every internal T-state. CPU unit,
  zexdoc, zexall, Spectrum, CPC, Radio-86RK and the full root test wall pass;
  CPC's affected exact-phase pins were reproduced twice before recalibration.
- 2026-07-18 — final `spectrumAccuracyVerification` found one remaining
  real-media regression despite green ROM/GOTHIK/Stormlord, unit and ZEX walls:
  Robocop side A remains at its known control-selection hash six frames after
  key `1`. The plan is reopened until a bounded post-input run proves whether
  this is only a stale sampling delay or a genuine input/gameplay regression.
- 2026-07-18 — closed the Robocop blocker without changing either scene oracle.
  A frame trace showed that the old three-frame pulse was consumed by the
  game's `AC75` `EI`/`HALT`/`DJNZ` delay before the menu scanner. A 12-frame
  hold followed by a bounded 120-frame semantic wait reaches the exact
  keyboard-game hash three frames after release and rejects the broken
  system-menu hash if it appears first. Two independent external-media runs
  passed on the final timing tree.
- 2026-07-18 — final verification is green on the completed tree:
  `spectrumAccuracyVerification --rerun-tasks` executed all 40 Spectrum,
  ZEX, ROM and real-media tasks successfully, and `test --rerun-tasks`
  executed all 47 repository test tasks successfully. `git diff --check`
  reports no whitespace errors and the plan has no open mandatory items.
- 2026-07-18 — live desktop testing reopened the GOTHIK item. The 48K turbo
  run reached EOF `154/154` without crashing, but the frozen frame is explicitly
  the loader's `Rewind to 00` request. The previous smoke therefore proved a
  deterministic first pass, not full loading or gameplay; multi-pass transport
  and turbo equivalence are under investigation.
- 2026-07-18 — closed the GOTHIK correction. The converted TAP legitimately
  needs one replay because it lacks the original TZX's inter-stage timing: TAP
  now proves prompt `CE0E/9C77B8C9`, title `B436/97CEB405` and gameplay
  `A0D1/B64B9264`; TZX proves one-pass title `B3EA/97CEB405` and gameplay
  `A489/B64B9264`. Both sequences passed twice. `spectrum48TapeSmoke` now uses
  the one-pass TZX title. A separate turbo audit fixed concurrent frame-raster
  publication and unbounded EDT update queuing, with deterministic coalescer
  and EOF replay tests.
- 2026-07-18 — reran the final walls after the GOTHIK and turbo UI corrections:
  `spectrumAccuracyVerification --rerun-tasks` executed all 40 Spectrum, ZEX,
  ROM and real-media tasks successfully, including the honest GOTHIK title and
  gameplay gates; `test --rerun-tasks` executed all 47 repository test tasks
  successfully. `git diff --check` remains clean and no mandatory plan item is
  open.
