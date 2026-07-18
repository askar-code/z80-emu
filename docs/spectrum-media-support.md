# Spectrum Media Support Matrix

Current executable support for stock ZX Spectrum 48K/128K. This matrix is
intentionally narrower than the file extensions themselves: a `.tzx` file is
accepted only when every executable block in that image is supported.

## Tape images

| Format / block | Status | Notes |
|---|---:|---|
| TAP | Supported | Standard ROM-speed pilot, sync and data pulses. |
| TZX `0x10` standard data | Supported | Header/data pilot selection and pause. |
| TZX `0x11` turbo data | Supported | Custom pilot/sync/bit timing and used-bit count. |
| TZX `0x12` pure tone | Supported | May be combined with following pulse/data blocks. |
| TZX `0x13` pulse sequence | Supported | May be combined with following data. |
| TZX `0x14` pure data | Supported | Custom zero/one timing and used-bit count. |
| TZX `0x15` direct recording | Supported | Explicit MSB-first sample levels, used-bit count and model-clock scaling. |
| TZX `0x18` CSW recording | Supported | RLE and zlib-wrapped Z-RLE, extended durations and arbitrary source sample rate. |
| TZX `0x20` pause / stop | Supported | Non-zero pause settles EAR LOW after the specified lead-in; zero stops playback. |
| TZX `0x21`, `0x22` group | Metadata only | Parsed/skipped; no effect on signal. |
| TZX `0x2A` stop in 48K mode | Supported | Stops only on the 48K model. |
| TZX `0x2B` set signal level | Supported | LOW/HIGH values are validated and applied to subsequent playback. |
| TZX `0x30`–`0x35`, `0x5A` metadata/glue | Metadata only | Payload is bounds-checked and skipped. |
| TZX `0x19` generalized data | Unsupported | Loader fails with the exact block id. |
| TZX `0x23`–`0x28` control flow | Supported | Signed jump, loop, call/return and select are validated and expanded before playback; the desktop deterministically chooses the first select option. |

TAP/TZX pulse units use the format's 3.5 MHz reference clock and are converted
to the selected machine clock with fractional carry, so original 128K playback
does not accumulate per-pulse rounding drift.

## Runtime behavior

- Cold-start autoplay is supported for the official 48K BASIC flow (`LOAD ""`)
  and original 128K Tape Loader menu.
- Play, stop and rewind are supported; replay after EOF preserves the absolute
  machine clock and restarts from tape position zero. This covers real
  multi-pass loaders: the flattened `GOTHIK.TAP` asks for `Rewind to 00`, and
  one `Cmd+P` at EOF starts its required second pass. The original
  `GOTHIK.TZX` preserves the inter-stage pause and needs only one pass.
- `Cmd+O` inserts or replaces TAP/TZX, `Cmd+E` ejects, and `Cmd+[` / `Cmd+]`
  select the previous/next block. A playing side continues playing after a
  successful replacement; a stopped side stays stopped. Parsing completes
  before replacement, so malformed media cannot eject the current side.
- The status line exposes the mounted filename, play/stop/EOF state, current
  block, total blocks and real/turbo mode. A named graphical block list is not
  implemented.
- The last successfully selected tape and snapshot directories are remembered
  independently across desktop launches.
- Spectrum turbo mode mutes the host audio path deliberately. Entering and
  leaving turbo flushes the output line and generated turbo PCM is drained and
  discarded, so accelerated loading cannot build a stale queue that plays back
  after returning to realtime speed.

## Desktop input profiles

Cmd+J cycles Cursor, Kempston, Sinclair 1 and Sinclair 2 at runtime and persists
the choice across desktop launches. The explicit process-wide override
`-Dz8emu.spectrum.joystick=cursor|kempston|sinclair1|sinclair2` takes precedence;
Gradle desktop launches forward it. Cursor is the default and safe fallback.

- Arrow keys provide direction in every profile.
- Numpad 0 is Fire in every profile; Ctrl is also Fire outside Cursor mode.
- Kempston reads active-high direction/Fire bits through the hardware port;
  Sinclair 1/2 and Cursor profiles drive their corresponding keyboard chords.

## Snapshots and disks

- `.sna`: canonical 48K and original-128K load/save are supported at launch and
  runtime. The 128K 131103-byte and duplicate-fixed-page 147487-byte layouts
  are accepted. SNA cannot preserve 128K AY state, so the desktop warns before
  and after saving it; use Z80 for a lossless stock-128K state.
- `.z80`: v1/v2/v3 48K and stock original-128K images are loaded, including raw
  and ED-ED-compressed pages. Desktop saves both models as compressed canonical
  v3, which also permits a 48K snapshot at `PC=0000` and preserves 128K AY plus
  7FFD/all-bank state.
- Malformed and known unsupported snapshot variants produce typed, specific
  errors before machine state changes. Desktop shortcuts are Cmd+Shift+O for
  load and Cmd+Shift+S for atomic save.
- `.trd` / `.scl`: outside stock 48K/128K v1. They require a separately scoped
  Beta Disk/TR-DOS implementation.

## Known hardware-fidelity limits

- Early-ULA display snow caused by refresh-address interaction is not modeled.
  Contention, normal bitmap/attribute fetch phases, floating bus, border writes,
  FLASH and 128K shadow-screen selection are covered independently; those
  passes must not be read as a snow-emulation claim.
- Tape input is a deterministic digital EAR level. The electrical threshold,
  hysteresis, filtering and board/ULA revision differences of a physical EAR
  input are not modeled. Beeper output likewise uses fixed MIC/EAR levels rather
  than a selectable analogue circuit revision.
- Z80 v3 frame-counter fields are validated, but arbitrary mid-frame raster and
  audio phase is not restored because the shared runtime currently resets its
  master clock when applying a snapshot.
