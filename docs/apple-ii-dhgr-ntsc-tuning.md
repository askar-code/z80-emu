# Apple II DHGR NTSC Tuning Notes

This note captures the current Prince of Persia double-hires title-screen
tuning state after the SuperDrive/WOZ bring-up. It is meant as a handoff for a
fresh debugging thread: do not repeat the rejected experiments below unless a
new hypothesis changes the reason they failed.

## Current Baseline

The current committed renderer in `Apple2VideoDevice`:

- builds the Apple IIe double-hires 560 half-pixel stream from aux plus main HGR
  bytes;
- decodes the rolling composite signal through a 4-phase, 12-bit NTSC lookup
  that keeps Y/I/Q samples until final 280-pixel output;
- writes the 280-pixel framebuffer with a slight late-sample luma bias, but
  samples chroma through a 4-half-pixel YIQ aperture before RGB conversion;
- uses the tuned YIQ-to-RGB coefficients currently in `ntscRgb`;
- clamps only a truly idle 12-bit signal to black, so non-idle black-ending
  sequences can retain dark-blue color bleed.

Current deterministic long probe:

```bash
./gradlew :app-desktop:apple2RomProbe --args='--machine=apple2e . 800000000 --disk="Prince of Persia side A.woz" --disk2-rom=build/apple2-disk2-roms/341-0027-p5.bin --keys=<SPACE> --key-poll-pc=0CC2 --profile-pc-top=16 --watch-addr=2F00,3000,4000,6000,7000,8000 --dump-frame=build/apple2-pop-woz-side-a-dhgr-space.png'
```

Expected current result:

```text
status=max-instructions-reached
keysInjected=1/1
frameCrc32=0xD2D64DA3
```

Reference target used during tuning:

- `https://cdn.mobygames.com/screenshots/15893521-prince-of-persia-apple-ii-title-screen.png`
- local resized copy: `build/apple2-pop-reference-mobygames-280-exact.png`

Useful current comparison images:

- `build/apple2-pop-bottom-coordinate-overview-x4.png`
- `build/apple2-pop-bottom-strip-stripes-x4.png`
- `build/apple2-pop-yellow-square-correct-x14.png`
- `build/apple2-pop-reference-vs-blend-x2.png`
- `build/apple2-pop-reference-vs-blend-red-x2.png`

Current YIQ/chroma-aperture validation artifacts:

- `build/apple2-dhgr-next/pop-yiq-chroma-aperture-equal4.png`
- `build/mame-pop/compare-config/z8-yiq-lower-left-x8.png`
- `build/mame-pop/compare-config/z8-yiq-bottom-strip-x10.png`

Compared with the old RGB-pair baseline, the equal 4-half-pixel chroma aperture
kept the left globe/context metric flat while reducing flat-fill striping:

```text
candidate,yellowStripe,maroonStripe,webFull,webLeft,webCenter,webGlobe,copyrightSharpness
old_rgb_pair,6.35,28.18,75.66,58.28,136.61,74.51,43.49
yiq_equal4,1.30,5.35,75.97,58.12,136.31,74.50,43.08
```

Visual review target: no new left-side shadow/trail was visible in the
globe-edge crop, unlike the rejected post-RGB aperture and period-4 pair
stabilization attempts.

## Open Visual Problem

The current renderer is the best non-regressed state found so far. The old
post-RGB pair blend had a strong 1-pixel phase stripe in some flat DHGR fills;
the current YIQ/chroma-aperture renderer reduces it materially but does not make
every flat fill perfectly emulator-RGB-flat:

- the yellow square near the lower-left blue area is striped;
- the maroon strip near the bottom is striped;
- the reference has those areas mostly flat, aside from edge artifacts.

The same stripe appears in both yellow and maroon, so the bug is not a
yellow-specific palette issue. It is probably a general 14 MHz phase leak during
NTSC decode or 560-to-280 sampling.

Measured regions used during tuning:

```text
yellow square interior: x=61,y=158,w=16,h=7
maroon strip:           x=115,y=170,w=31,h=2
left globe/context:     x=0,y=150,w=72,h=42
center blue texture:    x=92,y=120,w=92,h=26
full frame:             x=0,y=0,w=280,h=192
```

Representative current stripe measurements against the MobyGames reference:

```text
reference yellow even/odd ~= 0.77
old RGB-pair yellow even/odd ~= 6.35
current      yellow even/odd ~= 1.30

reference maroon even/odd ~= 0.28
old RGB-pair maroon even/odd ~= 28.18
current      maroon even/odd ~= 5.35
```

Treat these as guide rails, not as the only acceptance criteria. Several failed
experiments improved those numbers while making the title visibly worse.

## Rejected Experiments

### Targeted Yellow Flattening

Result artifacts:

- `build/apple2-pop-yellow-square-flat-result-x14.png`
- old long-probe CRC was `0x0AEB544C`

What it did:

- detected/forced a saturated yellow-like case in the renderer;
- made the yellow square flatter.

Why it was rejected:

- it was a screen/color-specific hack;
- it did not explain or fix the same striping on the maroon strip;
- it would be very easy to overfit Prince of Persia instead of improving Apple
  II DHGR rendering.

Do not bring this back.

### Chroma Integrator Slowdown

Result artifact:

- `build/apple2-pop-woz-side-a-dhgr-400m-chroma-iq16-java.png`

What it did:

- changed the chroma I/Q update in `buildDoubleHiResNtscTable` from `/8.0` to
  `/16.0`.

Why it was rejected:

- it did not remove the stripe root cause;
- it darkened/desaturated the image and worsened the maroon region;
- the 400M frame CRC for that experiment was `0x2F16331F`.

### Chroma-Only And RGB Post-Smoothing

Result artifacts:

- `build/apple2-pop-chroma-smooth-root-candidates-x8.png`
- `build/apple2-pop-rgb-smooth-root-candidates-x8.png`

What it did:

- smoothed final pixels after RGB conversion, or smoothed chroma after converting
  final pixels back to an approximate YIQ space.

Why it was rejected:

- chroma-only smoothing left luma/even-odd striping visible;
- RGB smoothing reduced stripes but visibly blurred text and ornaments;
- it treated the final framebuffer as the source, after too much information was
  already lost.

### Four-Half-Pixel Post-Decode Aperture

Result artifacts:

- `build/apple2-pop-4phase-root-check-x8.png`
- `build/apple2-pop-aperture-sweep-x8.png`
- `build/apple2-pop-final-aperture-full-compare-x2.png`
- old long-probe CRC was `0x245A161B`

What it did:

- decoded the 560 half-pixel signal to RGB first;
- averaged a full 4-phase window before writing each 280-pixel output sample.

Why it looked promising:

- yellow even/odd dropped to about `0.14`;
- maroon even/odd dropped to about `0.00`;
- it was a general signal-domain idea, not a yellow-only hack.

Why it was rejected:

- it made the title too soft;
- it created visible left-side shadows/trails around sharp elements;
- it hurt the readability of small text and ornaments.

Do not reintroduce this as a simple post-RGB aperture.

### 140-Color Cell Model

Result artifacts:

- `build/apple2-pop-140cell-variant-sweep-x8.png`
- `build/apple2-pop-java140-full-compare-x2.png`
- old long-probe CRC was `0xFD3375D9`

What it did:

- treated each 4-bit DHGR color cell as stable color;
- painted each 140-color cell as two identical 280-pixel output pixels.

Why it looked promising:

- no blur;
- no aperture shadows;
- flat fills became stable.

Why it was rejected:

- it made small text, especially the copyright line, too coarse and hard to
  read;
- it lost NTSC half-tone detail and made the full title screen look too
  posterized;
- the bottom-left arabic-style ornament and fine blue texture became less
  faithful despite flatter color areas.

Do not make this the default renderer unless there is an explicit separate
"idealized DHGR" video mode.

### Local Period-4 Pair Stabilization

Result artifacts:

- `build/apple2-dhgr-downsample/stable-period4-full-x2.png`
- `build/apple2-dhgr-downsample/stable-period4-lower-left-x6.png`
- `build/apple2-dhgr-downsample/stable-period4-bottom-strip-x8.png`
- rejected long-probe CRC was `0xE8B45CBA`

What it did:

- kept the rolling NTSC lookup and current late-biased half-pixel blend;
- detected local period-4 signal regularity in the 14 MHz DHGR stream;
- averaged the two 280-pixel outputs only when both pixels in the pair looked
  locally stable.

Why it looked promising:

- yellow stripe metric dropped from `11.00` to `5.50`;
- maroon stripe metric dropped from `48.81` to `0.00`;
- full-frame RMSE and copyright sharpness stayed essentially unchanged.

Why it was rejected:

- visual review still showed unacceptable left-side shadows at the globes;
- it was still a post-decode pair stabilization, so it could hide the stripe
  metric without fixing the underlying signal phase model.

Do not reintroduce this exact period-4 RGB pair averaging path.

### Broad Black-Nibble Clamp

Result artifacts:

- `build/apple2-dhgr-next/pop-black-nibble-clamp.png`
- `build/apple2-dhgr-next/black-nibble-lower-left.png`
- rejected long-probe CRC was `0x82CB1C3C`

What it did:

- copied AppleWin's broad black-ghosting clamp behavior for sequences whose low
  nibble is black (`sequence & 0x0F == 0`);
- replaced the current narrower rule that clamps only a truly idle all-zero
  12-bit signal.

Why it was rejected:

- it did not materially improve the yellow or maroon stripe metrics;
- it increased full-frame and left-context RMSE;
- it visibly crushed the dark-blue center texture.

Keep the current idle-only black clamp unless a future signal model can remove
edge trails without losing dark-blue NTSC detail.

### AppleWin Carry/Pre-Render Shift Sweep

Result artifacts:

- `build/apple2-dhgr-downsample/carry-full-x2.png`
- `build/apple2-dhgr-downsample/carry-lower-left-x6.png`
- `build/apple2-dhgr-next/applewin-carry-globe-candidates-x5.png`

What it did:

- tested AppleWin-style byte carry, where the previous cell's `main6` bit is
  emitted before the next cell's aux bits;
- tested candidate phase/offset/sample combinations inspired by AppleWin's 14M
  pre-render comments and 280x192 screenshot path.

Why it was rejected:

- the carry variants produced large hue shifts in the title and bottom
  ornaments;
- sample-only changes moved the stripe/shadow tradeoff between regions instead
  of fixing the signal model;
- the visually plausible no-carry `second` sample improved some edges but made
  the yellow square and full-frame match worse.

Do not repeat the existing `p0/p3 offset` carry sweep without a new derivation
of AppleWin's border/pre-render coordinate mapping.

### Broad Palette/Gain Tweaks

What happened:

- small YIQ-to-RGB coefficient tuning helped the overall warmth and remains in
  the current baseline;
- separate RGB gain-style patches were effectively invisible or hard to reason
  about;
- changing `chromaFilter` / `colorTvLumaFilter` knobs directly pushed the image
  in the wrong direction and was rolled back.

Rule for next attempts:

- palette constants are fair game only after the sampling/phase model is
  stable;
- do not add generic gain multipliers just to chase the MobyGames screenshot.

## External Emulator Cross-Check

### MAME 0.287 Apple IIe

MAME was installed with Homebrew and run against the same side-A WOZ. Useful
command shape:

```bash
mame apple2ee \
  -rompath build/mame-roms \
  -cfg_directory build/mame-pop/cfg \
  -nvram_directory build/mame-pop/nvram \
  -sl4 "" \
  -gameio paddles \
  -flop1 "Prince of Persia side A.woz" \
  -skip_gameinfo \
  -nothrottle \
  -video none \
  -sound none \
  -snapshot_directory build/mame-pop/snap-config \
  -autoboot_script build/mame-pop/snapshot-a2video-config.lua
```

Notes:

- `-sl4 ""` avoids the default speech/Mockingboard path that requires Votrax
  ROMs;
- `-gameio paddles` exposes Apple II game-port paddle/button inputs;
- raw native snapshots are 560x192, so compare either first/second half-pixel
  or explicit 560-to-280 downsample variants;
- MAME's built-in `Color` monitor is not a direct composite reference for this
  tuning task.

Relevant MAME source inspected:

- `src/mame/apple/apple2video.cpp`
- `src/mame/apple/apple2video.h`

What the source shows:

- DHGR words are built as `aux7 + (main7 << 7)`, matching our aux-then-main
  14 MHz stream;
- color output is either 7-bit pixel-run colorization or a 4-bit box filter;
- `B&W for NTSC shader` deliberately emits monochrome source pixels, because
  final NTSC color is expected from an external shader path;
- there is no built-in raw-snapshot composite target equivalent to our rolling
  YIQ renderer.

MAME is still useful as a structural sanity check: flat DHGR fills can be stable
in an idealized colorizer. It is not useful as the direct palette/texture target
unless a future pass captures the final external NTSC shader output.

Measured MAME native/color candidates:

```text
candidate,yellowStripe,maroonStripe,webFull,webLeft,webCenter,webGlobe,copyrightSharpness
mame_color_prbw_average,0.00,0.00,82.88,68.81,140.35,83.74,33.79
mame_color_prcolor_average,0.00,0.00,81.45,66.02,139.88,80.95,33.79
mame_color_box_average,0.00,0.00,83.02,65.99,137.41,81.01,23.55
```

These zero-stripe MAME candidates are visibly posterized and materially farther
from the web reference in left/context regions than the current z8 composite
renderer, so do not tune z8 directly toward them.

### AppleWin Follow-Up After YIQ Aperture

AppleWin upstream was fetched and matched the local source snapshot already used
during tuning:

```text
NTSC.cpp sha1=9f807fa06f321b83af67afa7c48cad1ea0a9cfd2
Video.cpp sha1=2f4f228b717dd04b2c9c94dc95a4a4ccc4f4f03f
```

Portable AppleWin ideas were checked again on top of the current YIQ aperture:

```text
candidate,yellowStripe,maroonStripe,webFull,webLeft,webCenter,webGlobe,copyrightSharpness
yiq_equal4,1.30,5.35,75.97,58.12,136.31,74.50,43.08
applewin_carry_yiq,3.70,0.00,112.39,118.29,144.83,120.53,43.13
applewin_matrix_yiq,2.06,5.69,75.93,63.45,135.36,76.37,45.43
applewin_monitor_luma,7.94,70.78,78.00,60.35,136.40,75.98,55.82
applewin_black_clamp,1.30,5.35,79.73,61.71,143.60,78.85,43.08
```

Results:

- the AppleWin carry/14M alignment removes the maroon stripe metric but badly
  regresses the full image and left globe/context crop;
- the original AppleWin YIQ matrix improves the sharpness proxy but worsens the
  yellow stripe and left/globe crops;
- AppleWin monitor luma is too sharp for this target and brings the stripes
  back strongly;
- broad AppleWin black ghosting clamp still crushes dark-blue texture and does
  not improve flat-fill striping.

Conclusion: keep the current z8 YIQ aperture and tuned coefficients. Do not copy
AppleWin code directly; its GPL implementation remains useful as behavioral
evidence, but the accepted z8 path is a local signal-model change.

## Hypotheses Worth Testing Next

### 1. Decode In YIQ, Downsample Before RGB

Status: implemented as the current YIQ/chroma-aperture renderer. The accepted
candidate keeps luma on the existing late-biased 2:3 half-pixel pair and samples
I/Q with an equal 4-half-pixel aperture before RGB conversion.

The failed 4-phase aperture averaged already-decoded RGB. The current version
keeps separate Y/I/Q sample streams:

- keep luma sharp enough for copyright text and arabic-style ornament strokes;
- low-pass or phase-average only I/Q enough to suppress color crawl;
- convert to RGB only after sampling at the 280-pixel output positions.

This reduced maroon/yellow color striping without the post-RGB blur/shadow
failure mode. If it needs another pass, tune the chroma aperture weights first;
do not return to RGB-space smoothing.

### 2. Correct 560-To-280 Sample Position

AppleWin keeps a 560-pixel framebuffer and its 280x192 screenshot path samples a
specific half-pixel position rather than blindly averaging every pair. The local
current blend may still be half a 14 MHz sample off.

Already checked:

- first-half, second-half, average, 2:3, and 3:2 no-carry sample choices;
- AppleWin-style carry/pre-render variants at the obvious phase/offset
  alignments.

Things to check:

- derive AppleWin's border/pre-render mapping exactly before trying more carry
  variants;
- verify any new sample position against the globe-edge shadow crop, not only
  the yellow and maroon flat-fill metrics;
- reject candidates that merely move the red/dark leading echo left of the
  globes.

Relevant old artifacts:

- `build/apple2-pop-downsample-mode-check-x8.png`
- `build/apple2-pop-left-globe-weight-sweep-x10.png`
- `build/apple2-pop-left-globe-tight-halfpair-x12.png`

### 3. Signal-Pattern-Aware Flat-Fill Stabilization

A global 140-cell model was too coarse, but the stripe problem is worst in flat
repeating DHGR fills. A possible general fix is to detect stable repeating
signal patterns over several 14 MHz samples or adjacent cells and stabilize only
those regions.

The simple local period-4 RGB pair-averaging version was tried and rejected
because visual review still showed left-side shadows at the globes. If this
area is revisited, the candidate needs to act before RGB/post-pair sampling or
prove that it does not create globe-edge trails.

Important constraint:

- the condition must be based on local signal regularity, not screen coordinates
  or named colors;
- it must not change thin text strokes, copyright letters, or ornament edges.

This is riskier than a cleaner YIQ sampling model, but it addresses the specific
"flat fills stripe, detail should stay sharp" split.

### 4. Validate Against Another Emulator

Status: MAME 0.287 was checked in the pass above. The MobyGames image may still
have been produced by a particular emulator/filter, so a future pass may need
AppleWin or MAME with an external NTSC shader, but the built-in MAME color modes
are not a direct composite target.

If this area is revisited, compare:

- whether the maroon strip is truly flat in that emulator;
- whether copyright text is sharper or softer than the MobyGames screenshot;
- whether the yellow square is expected to be perfectly flat or slightly
  artifacted.

Do this before fitting too closely to one web screenshot.

### 5. Add A Repeatable Visual-Metric Script

The hand-run Python snippets were useful but should become a repo script or
test helper only after the target metric is settled. At minimum it should print:

- region RMSE versus reference;
- yellow and maroon even/odd stripe metrics;
- a small-text sharpness/readability proxy for the copyright crop;
- output paths for full, lower-left, and bottom-strip comparison images.

Do not gate normal CI on copyrighted local reference/media files.

## Acceptance Criteria For The Next Renderer Change

A candidate should pass all of these before it replaces the current baseline:

1. It is general Apple II DHGR/NTSC logic.
   - no screen-coordinate special cases;
   - no Prince-specific image knowledge;
   - no yellow-only or maroon-only clamps.

2. It preserves text and fine detail.
   - `Copyright 1989 Jordan Mechner` remains at least as readable as current;
   - the arabic-style ornament in the lower-left remains readable;
   - the `PRINCE OF PERSIA` lettering stays sharp;
   - no visible left-side shadow/trail near the globe/ornaments.

3. It improves flat-fill striping materially.
   - yellow-square even/odd stripe should be much closer to reference than the
     current `~6.35`;
   - maroon-strip even/odd stripe should be much closer to reference than the
     current `~28.18`;
   - do not accept the change if these metrics improve only by making the whole
     image soft or posterized.

4. It keeps the current boot/game path deterministic.
   - update the documented long-probe CRC only after visual review;
   - the probe must still reach `status=max-instructions-reached` with
     `keysInjected=1/1`.

5. It passes the standard local checks:

```bash
./gradlew :machine-apple2:test --tests dev.z8emu.machine.apple2.Apple2MachineTest :app-desktop:apple2SuperDrivePopSmoke
git diff --check
```

6. It leaves no failed experiments behind.
   - remove probe-only constants, old CRCs, and abandoned helper methods;
   - search for known rejected markers before finishing:

```bash
rg -n 'SOLID_YELLOW|yellow-flat|chroma-iq16|140cell|aperture|0x0AEB544C|0x245A161B|0xFD3375D9' \
  machines/apple2/src/main/java/dev/z8emu/machine/apple2/Apple2VideoDevice.java \
  docs/apple-ii-plus-runbook.md docs/apple-ii-plan.md docs/apple-ii-dhgr-ntsc-tuning.md
```

The final review should be visual first. Metrics are there to catch regressions,
not to overrule the obvious: if copyright text turns to mush or the title gains
left shadows, reject it.
