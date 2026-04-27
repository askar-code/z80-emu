# Apple II DHGR NTSC Tuning Notes

This note captures the current Prince of Persia double-hires title-screen
tuning state after the SuperDrive/WOZ bring-up. It is meant as a handoff for a
fresh debugging thread: do not repeat the rejected experiments below unless a
new hypothesis changes the reason they failed.

## Current Baseline

The current committed renderer in `Apple2VideoDevice`:

- builds the Apple IIe double-hires 560 half-pixel stream from aux plus main HGR
  bytes;
- decodes the rolling composite signal through a 4-phase, 12-bit NTSC lookup;
- uses the tuned YIQ-to-RGB coefficients currently in `ntscRgb`;
- writes the 280-pixel framebuffer by blending each pair of half-pixels with a
  slight late-sample bias;
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
frameCrc32=0x723291BA
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

## Open Visual Problem

The current renderer is the best non-regressed state found so far, but it still
has a visible 1-pixel phase stripe in some flat DHGR fills:

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
current   yellow even/odd ~= 6.35

reference maroon even/odd ~= 0.28
current   maroon even/odd ~= 28.18
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

## Hypotheses Worth Testing Next

### 1. Decode In YIQ, Downsample Before RGB

The failed 4-phase aperture averaged already-decoded RGB. A better version would
keep separate Y/I/Q sample streams:

- keep luma sharp enough for copyright text and arabic-style ornament strokes;
- low-pass or phase-average only I/Q enough to suppress color crawl;
- convert to RGB only after sampling at the 280-pixel output positions.

This could reduce maroon/yellow color striping without blurring white text.

### 2. Correct 560-To-280 Sample Position

AppleWin keeps a 560-pixel framebuffer and its 280x192 screenshot path samples a
specific half-pixel position rather than blindly averaging every pair. The local
current blend may still be half a 14 MHz sample off.

Things to check:

- compare first-half, second-half, and 2:3 / 3:2 blends on the full title, not
  only the left globe;
- account for AppleWin's 14M-mode one-pixel pre-render/shift comments;
- verify that changing sample position does not reintroduce the red leading echo
  left of the globe.

Relevant old artifacts:

- `build/apple2-pop-downsample-mode-check-x8.png`
- `build/apple2-pop-left-globe-weight-sweep-x10.png`
- `build/apple2-pop-left-globe-tight-halfpair-x12.png`

### 3. Signal-Pattern-Aware Flat-Fill Stabilization

A global 140-cell model was too coarse, but the stripe problem is worst in flat
repeating DHGR fills. A possible general fix is to detect stable repeating
signal patterns over several 14 MHz samples or adjacent cells and stabilize only
those regions.

Important constraint:

- the condition must be based on local signal regularity, not screen coordinates
  or named colors;
- it must not change thin text strokes, copyright letters, or ornament edges.

This is riskier than a cleaner YIQ sampling model, but it addresses the specific
"flat fills stripe, detail should stay sharp" split.

### 4. Validate Against Another Emulator

The MobyGames image may have been produced by a particular emulator/filter. A
new pass should generate the same PoP title from at least one independent
emulator path, preferably AppleWin or MAME, and compare:

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
