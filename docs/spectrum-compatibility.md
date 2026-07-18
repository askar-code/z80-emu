# ZX Spectrum Compatibility Matrix

Evidence recorded on 2026-07-18 for the stock ZX Spectrum 48K and original
ZX Spectrum 128 supported by this repository. This is a matrix of scenarios
that actually have a positive oracle, not a claim that every program using the
same file extension works.

Most ROMs and games below are local, gitignored media. A clean checkout and CI
run the hermetic gates and skip those rows; a skip is not a pass. Ivan
Kosarev's MIT-licensed screen-timing corpus is embedded with attribution and
runs in every hermetic test pass. See
[`spectrum-media-support.md`](spectrum-media-support.md) for format-level support
and [`headless-spectrum-probe.md`](headless-spectrum-probe.md) for probe options.

## Status language

- **PASS (media)**: the named local image reached a specific, repeatable PC,
  frame hash/CRC, EOF state or scene under a bounded command.
- **PASS (open corpus)**: a redistributable upstream program and its published
  oracle run hermetically from source-attributed fixtures.
- **PASS (synthetic)**: focused unit gates passed, but no real program is being
  credited with compatibility.
- **UNTESTED**: local media may exist, but no positive oracle was executed.
- **MISSING CORPUS**: no representative image and positive oracle are present.

## Real ROM and software evidence

| Scenario | Image and model | Positive oracle observed | Status | Gate |
|---|---|---|---:|---|
| Official 48K BASIC boot | `media/48.rom`, Spectrum 48K | BASIC input loop at PC `15DE`; uniform gray frame CRC `A6E9608F` | **PASS (media)** | `spectrum48RomSmoke` |
| Official 128K boot menu | `media/128.rom`, Spectrum 128 | Tape Loader menu at PC `3685`; whole-frame CRC `A3BE0D95` | **PASS (media)** | `spectrum128RomSmoke` |
| GOTHIK converted TAP | `media/GOTHIK.TAP`, Spectrum 48K | Pass 1 reaches the expected `Rewind to 00` prompt (`CE0E`/`9C77B8C9`); replay reaches title (`B436`/`97CEB405`) and Enter reaches gameplay (`A0D1`/`B64B9264`) | **PASS (media, manual replay)** | `GothikMultipassRegressionTest` |
| GOTHIK original TZX | `media/GOTHIK.TZX`, Spectrum 48K | Preserved inter-stage timing reaches title in one pass (`B3EA`/`97CEB405`) and gameplay after Enter (`A489`/`B64B9264`) | **PASS (media)** | `GothikMultipassRegressionTest`; `spectrum48TapeSmoke` also freezes the title CRC |
| Stormlord 128 | `media/STRML128.TAP`, Spectrum 128 | EOF `6/6`; configured keyboard controls and entered gameplay at PC `8F46`; frame CRC `310D3A7E` | **PASS (media)** | `spectrum128TapeSmoke` |
| Robocop side A: control selection | `media/RobocopA.tzx`, Spectrum 128 | Post-load controls scene hash `1281281213` | **PASS (media)** | `RobocopSideARegressionTest` |
| Robocop side A: keyboard game scene | `media/RobocopA.tzx`, Spectrum 128 | Holding `1` for 12 frames reaches keyboard-controlled game scene hash `-1004593151` within a bounded 120-frame wait, before the known broken system-menu hash | **PASS (media)** | `RobocopSideARegressionTest` |
| Robocop side B | `media/RobocopB.tzx`, Spectrum 128 | No side-change or scene oracle recorded | **UNTESTED** | none |
| Heartbroken title/menu | `media/Heartbroken.tzx`, Spectrum 48K | Three runs reached EOF `10/10`, PC `FE14` and CRC `1AB7C24A`; the rendered frame visibly contains the title and `S: START GAME` menu | **PASS (media)** | bounded probe below |
| Heartbroken gameplay | `media/Heartbroken.tzx`, Spectrum 48K | No start-game input sequence or gameplay oracle recorded | **UNTESTED** | none |
| Kosarev screen timing (early) | embedded `screen_timing_early.tap`, Spectrum 48K | Unmodified upstream TAP/code reaches IM2 draw-loop HALT at PC `833F`; the published `expected_output.png` window matches with zero pixel differences | **PASS (open corpus)** | `KosarevScreenTimingRegressionTest` |

The old GOTHIK oracle proved only a deterministic first tape pass and the
loader's explicit rewind request, not a complete load or gameplay. A live
desktop run exposed that false-positive interpretation. The replacement gate
now verifies the complete TAP rewind/replay flow and the original TZX's
single-pass flow through title and gameplay. The flattened TAP lacks the
original TZX's inter-stage timing and therefore legitimately needs one manual
replay (`Cmd+P` at EOF); this is media behavior, not a CPU/tape failure. Three
independent runs had previously reproduced the first-pass prompt, but that pin
is now deliberately demoted. Both complete TAP and TZX sequences passed twice;
the regular 48K smoke now uses the TZX title CRC instead. Stormlord likewise
repeated PC `8F46`, t-state `1149633370`, EOF and the unchanged gameplay CRC in
all three final-tree smoke runs.

The 48K BASIC boot CRC was intentionally re-pinned only after the corrected
visible raster origin rendered the same uniform `352x296` gray frame in three
independent final-tree runs. The 128K boot oracle remained unchanged.

The Heartbroken oracle proves a complete load and meaningful title/menu scene,
not gameplay. Robocop side A is the current real-media custom/turbo-timing case:
its expanded signal contains non-ROM bit timings including `714/1428`,
`875/1750` and `583/1166` t-states and still reaches both expected scenes.
On the final internal-timing tree, the old three-frame key pulse ended entirely
inside the game's `AC75` `EI`/`HALT`/`DJNZ` delay before its menu scanner read
the keyboard. Holding `1` for 12 frames spans that scanner; the exact game-scene
hash then appears three frames after release. The regression now waits at most
120 frames for that semantic target and fails immediately if the known broken
system-menu hash appears first. Two independent external-media runs reproduced
the exact game-scene hash.

The Kosarev gate embeds the upstream early-timing TAP and PNG from commit
`0074f3ded032fd869baaa9b973d895273cca6abd`, verifies their SHA-256 hashes,
extracts the 831-byte CODE payload, reproduces the BASIC loader's `CLS` RAM
state and executes it on a blank-ROM 48K machine. It checks IM2 setup, the
draw-loop HALT and every pixel in the published `378x376` visual window after
the documented normal-palette conversion. The fixture directory carries Ivan
Kosarev's 2017-2026 copyright notice and complete MIT license.

As an independent media-path check, two cold boots with local `media/48.rom`
loaded the unchanged upstream TAP to EOF `4/4`, accepted the post-load Enter,
and produced identical PC `833F`, t-state `95996272`, whole-frame CRC
`CA6E72D0` and PNG bytes. Both normalized crops had zero differences from the
published oracle.

## TZX capability evidence

| Capability | Real-media evidence | Synthetic evidence | Current conclusion |
|---|---|---|---|
| Standard-speed data (`0x10`) | Heartbroken loads all ten ROM-speed blocks to its title/menu; official-style blocks also appear in Robocop | Loader and playback tests | **PASS (media + synthetic)** |
| Turbo/pure tone/pulse/pure data (`0x11`–`0x14`) | Robocop side A loads custom-timed blocks and reaches control and game scenes | Exact timing, used-bit and pulse-composition tests | **PASS (media + synthetic)** |
| Direct recording (`0x15`) | No identified real-media oracle | MSB-first levels, used bits, pause polarity and 48K/128K clock-scaling tests | **PASS (synthetic only)** |
| CSW RLE/Z-RLE (`0x18`) | No identified real-media oracle | RLE, zlib-wrapped Z-RLE, extended pulses, count validation and arbitrary sample-rate tests | **PASS (synthetic only)** |
| Jump/loop/call-return/select (`0x23`–`0x28`) | No real image is known to exercise these paths under a positive scene oracle | Signed jumps, loops, nested loop/call combinations, call sequences, deterministic/default and explicit selection, malformed stacks and execution budgets | **PASS (synthetic only)** |
| Generalized data (`0x19`) | None | Exact early unsupported-block rejection | **Unsupported by design** |

Do not use the Robocop result as evidence for executable TZX control flow: the
positive game gate proves its expanded custom signal, while the original block
program has not been audited for `0x23`–`0x28` coverage.

## Timing and visual gates

The Kosarev row is a real executable timing corpus. The remaining focused gates
below isolate individual timing rules and make failures easier to diagnose.

| Gate class | What it fixes as an oracle | Result on 2026-07-18 |
|---|---|---:|
| `KosarevScreenTimingRegressionTest` (1 test) | MIT-licensed 48K executable; IM2/HALT calibration, 4T border latch, raster origin, contended mid-frame bitmap/attribute writes and exact published visual window | **PASS (open corpus)** |
| `SpectrumUlaDisplayTimingTest` (9 tests) | Adjacent pixel/attribute fetches; writes before, at and after fetch; 128K shadow screen; FLASH phase CRCs; normal/shadow screen CRCs; physical border-event placement and CRC | **PASS (synthetic)** |
| `SpectrumContentionModelTest` (6 tests) | 48K/128K contention patterns, visible-window bounds, frame wrap and sequential I/O contention | **PASS (synthetic)** |
| `SpectrumBusContentionTest` (4 tests) | Contended RAM, paged odd 128K banks and instruction-phase screen writes including LDIR | **PASS (synthetic)** |
| `SpectrumBusIoTest` (6 tests) | 48K/128K floating-bus bytes, paging isolation, border and AY I/O routing | **PASS (synthetic)** |
| `SpectrumUlaIoTimingTest` (6 tests) | Even-port ULA mirrors, overlapping device fan-out, high-port contention and absolute board/ULA synchronization | **PASS (synthetic)** |
| `TzxLoaderTest` (23 tests) | Supported TZX block parsing, control-flow execution, malformed input and hostile execution limits | **PASS (synthetic)** |
| `TapeDeviceTest` (17 tests) | Signal playback, 128K clock conversion, replay at non-zero time, direct/CSW levels, eject and block boundaries | **PASS (synthetic)** |
| `Spectrum128AyPcmRegressionTest` | Embedded Z80 two-note program through CPU OUT, Spectrum bus, AY and board mixer; 3196-byte PCM/CRC `A1C662D5` plus waveform assertions | **PASS (synthetic)** |

## Reproduction commands

The supported aggregate runs all hermetic gates and executes the optional media
gates only when their files exist:

```bash
./gradlew --no-daemon spectrumVerification
```

Run the four ROM/game smokes directly:

```bash
./gradlew \
  :app-desktop:spectrum48RomSmoke \
  :app-desktop:spectrum128RomSmoke \
  :app-desktop:spectrum48TapeSmoke \
  :app-desktop:spectrum128TapeSmoke
```

Run the two Robocop side-A scene oracles:

```bash
./gradlew :machine-spectrum:externalMediaTest \
  --tests '*RobocopSideARegressionTest'
```

Reproduce the Heartbroken title/menu oracle with a hard 2.5-billion-t-state
ceiling and all five expectations enabled:

```bash
./gradlew :app-desktop:spectrumTapeProbe \
  -Dz8emu.probeMaxTStates=2500000000 \
  -Dz8emu.probePostEofTStates=200000000 \
  -Dz8emu.probeMilestones=2500000001 \
  -Dz8emu.probeExpectPc=0xFE14 \
  -Dz8emu.probeExpectEof=true \
  -Dz8emu.probeExpectBlock=10 \
  -Dz8emu.probeExpectTotalBlocks=10 \
  -Dz8emu.probeExpectFrameCrc=1AB7C24A \
  --args='media/48.rom media/Heartbroken.tzx /tmp/z8-emu-heartbroken'
```

Run the focused synthetic tape and display wall:

```bash
./gradlew :machine-spectrum:test --rerun-tasks \
  --tests '*TzxLoaderTest' \
  --tests '*TapeDeviceTest' \
  --tests '*SpectrumUlaDisplayTimingTest' \
  --tests '*SpectrumContentionModelTest' \
  --tests '*SpectrumBusContentionTest' \
  --tests '*SpectrumBusIoTest' \
  --tests '*SpectrumUlaIoTimingTest'
```

Run only the redistributable executable timing corpus:

```bash
./gradlew :machine-spectrum:test \
  --tests '*KosarevScreenTimingRegressionTest'
```

## Remaining compatibility work

The main 48K executable timing-demo gap is closed by the Kosarev corpus. A
separate open 128K/late-timing executable with a published visual oracle is
still missing, so the 48K result must not be promoted to original-128 timing
coverage.

Other explicit gaps are Robocop side B, Heartbroken gameplay, a real
direct-recording image, a real CSW image and a real TZX control-flow image.
None of those rows should be promoted based only on parser coverage.
