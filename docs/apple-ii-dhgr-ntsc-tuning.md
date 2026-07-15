# Apple II DHGR NTSC Tuning Notes

This note captures the current Prince of Persia double-hires title-screen
tuning state after the SuperDrive/WOZ bring-up, plus the later ordinary-HGR
gameplay fix. It is meant as a handoff for a fresh debugging thread: do not
repeat the rejected experiments below unless a new hypothesis changes the
reason they failed.

## Current Baseline

All rejected DHGR renderer and test experiments described below were reverted
after visual review. The DHGR path remains the committed `3ecddf8` baseline in
`Apple2VideoDevice`:

- builds the Apple IIe double-hires 560 half-pixel stream from aux plus main HGR
  bytes;
- keeps luma and chroma as Y/I/Q samples through the 4-phase, 12-bit NTSC
  lookup;
- downsamples luma to the 280-pixel framebuffer with a 2:3 late-sample bias;
- averages chroma over the previous, first, second, and next 14 MHz samples
  before the final RGB conversion;
- uses the tuned local YIQ-to-RGB coefficients;
- clamps only a truly idle 12-bit signal to black, so non-idle black-ending
  sequences can retain intentional dark-blue NTSC bleed.

There is no AppleWin neighborhood mask or native 560-pixel framebuffer in the
retained code. Ordinary HGR now feeds the same rolling NTSC/YIQ table through
its own phase and alignment; this is intentionally separate from the rejected
DHGR experiments below.

Current deterministic long probe:

```bash
./gradlew :app-desktop:apple2RomProbe --args='--machine=apple2e media 800000000 --disk="media/Prince of Persia side A.woz" --disk2-rom=build/apple2-disk2-roms/341-0027-p5.bin --keys=<SPACE> --key-poll-pc=0CC2 --profile-pc-top=16 --watch-addr=2F00,3000,4000,6000,7000,8000 --dump-frame=build/apple2-pop-woz-side-a-dhgr-space.png'
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

Useful baseline and rejected-candidate artifacts:

- committed 400M baseline: `build/apple2-dhgr-smart/baseline.png`;
- rejected 280-pixel mask hybrid: `build/apple2-dhgr-smart/final-confirmed.png`;
- full-frame sweep: `build/apple2-dhgr-smart/rgb-mask-margin-full-sweep-x2.png`;
- globe-edge sweep: `build/apple2-dhgr-smart/rgb-mask-margin-top-globe-sweep-x4.png`;
- copyright/blend sweep: `build/apple2-dhgr-smart/blend-sweep-bottom-x5.png`.

## Gameplay HGR Follow-Up (2026-07-14)

The severe black/white checkerboard seen after the intro was not a new DHGR
regression. Prince switches its normal intro/gameplay scenes to ordinary HGR,
and the old HGR renderer exposed only four hard palette colors instead of
decoding the composite signal.

Evidence that ruled out a same-day regression:

- detached end-of-April commit `3ecddf8` and the July 14 `HEAD` reached the
  same PC, registers, and t-state after the same 1.2-billion-instruction probe;
- both produced frame CRC `0x7DCCE7F8` before the HGR fix;
- the two PNG files were byte-identical with SHA-256
  `5f8d8080d3d00c1bd2ac4f25010d9170edeb14ad8c4d86ecba75c0ecc9478889`;
- the affected desktop capture contained only black, white, fixed blue, and
  fixed orange in the emulated frame, exactly matching the old HGR palette
  constants.

Exact visual references for the affected HGR scenes:

- game start:
  `https://cdn.mobygames.com/screenshots/15893560-prince-of-persia-apple-ii-game-start.png`;
- palace intro:
  `https://cdn.mobygames.com/screenshots/15893542-prince-of-persia-apple-ii-intro-2.png`;
- local phase comparison, reference on the left and z8 phase-0 HGR on the
  right: `build/apple2-video-mode/pop-hgr-reference-left-z8-right.png`.

Rejected diagnoses during this follow-up:

- Missing `$C05E/$C05F` DHIRES state was a real emulation omission and was
  fixed, but adding it did not change the affected frame CRC. The game was
  already rendering this scene as HGR.
- AUX/MAIN bit-order reversal was considered, but inspection showed that z8
  already emitted seven AUX bits followed by seven MAIN bits, matching
  AppleWin and MAME.
- Restricting the white-ringing clamp from every trailing `0xF` nibble to a
  full `0xFFF` sequence did not change the affected frame and was reverted.
- Reusing the DHGR initial phase for HGR removed the checkerboard but rotated
  the scene to neon green/purple. That phase-1 candidate had frame CRC
  `0x61F303C9` and was rejected.

Retained HGR implementation:

- expands each 7 MHz HGR data bit into two 14 MHz signal samples;
- applies the byte high bit as a one-sample delay and carries the displaced
  final half-pixel into the next shifted byte, matching AppleWin's
  `g_aPixelDoubleMaskHGR`/`g_nLastColumnPixelNTSC` behavior;
- runs the 560-sample line through the shared rolling NTSC/YIQ table;
- samples into the existing 280-pixel framebuffer with HGR phase `0` and
  signal offset `0` while leaving DHGR phase `3` and offset `2` unchanged.

Current deterministic checks:

```text
400M DHGR title:       0x19DA4830 (unchanged)
1.2B HGR scene:        0xDEDBD99E
SuperDrive HGR smoke:  0x54BCF7D0
```

The HGR phase-0 result reproduces the reference's blue arches, warm stone, and
brown/orange floor instead of the old four-color checkerboard. The reference
still contains more intermediate CRT/composite shades, so future display
filter work may soften the result, but it should start from this signal model
rather than restoring the fixed HGR palette.

The metric sweep slightly favored the rejected mask hybrid, but visual review
did not. This is the clearest example of why these measurements are diagnostic
only:

```text
candidate,yellowStripe,maroonStripe,webFull,webLeft,webCenter,webGlobe,copyrightSharpness
reference,0.97,1.57,0.00,0.00,0.00,0.00,60.77
previous_yiq,1.30,5.35,35.69,34.86,28.15,34.89,59.47
rgb_mask_margin4,1.89,0.00,34.70,34.65,27.20,34.18,57.99
```

For the mask candidate, the yellow interior pairs at `x=63..71` measured
`0.00`, and the maroon aggregate measured `0.00`. Reducing the guard from four
to two half-pixels lowered the aggregate yellow score to `1.09`, but changed
real edge structure. Despite the favorable aggregate RMSE, two separate live
reviews judged the result essentially unchanged and then "so-so". The whole
mask branch was therefore rejected and reverted.

## Remaining Visual Gap

The committed YIQ renderer remains visibly less convincing than AppleWin on the
Prince title, although it was judged better than the MAME color candidates. Its
main remaining weaknesses are:

- mild phase striping in some nominally flat DHGR fills;
- softer small copyright text and ornaments than AppleWin;
- some color fringing around high-contrast contours;
- the 280x192 reference contains tens of thousands of colors and is an exact
  2x source duplication at 560x384, so it was likely postprocessed rather than
  captured from a raw 16-color or 560-pixel idealized framebuffer;
- no tested AppleWin, MAME, mask-stabilized, or native-560 variant improved the
  whole title enough to replace the baseline.

Do not treat a 560-pixel framebuffer as the next obvious step: it was implemented
and visually rejected. Another attempt needs a genuinely new signal/display
hypothesis and must beat the committed baseline in a live side-by-side review.

Measured regions used during tuning:

```text
yellow square interior: x=61,y=158,w=16,h=7
maroon strip:           x=115,y=170,w=31,h=2
left globe/context:     x=0,y=150,w=72,h=42
center blue texture:    x=92,y=120,w=92,h=26
full frame:             x=0,y=0,w=280,h=192
```

Treat these as guide rails, not as the only acceptance criteria. Several failed
experiments improved those numbers while making the title visibly worse.

## Experiment Ledger

Every renderer branch tried in this tuning run is listed here; detailed notes
and artifacts follow below.

| Attempt | Outcome |
| --- | --- |
| Saturated-yellow special case | Flattened one square; rejected as Prince/color-specific |
| Chroma I/Q integrator `/16` | Darker and less saturated; rejected |
| RGB and reconstructed-YIQ post-smoothing | Reduced stripes by blurring detail; rejected |
| Four-half-pixel post-RGB aperture | Flat fills, but left shadows and soft text; rejected |
| 140-color-cell renderer | Flat but posterized and coarse; rejected |
| Local period-4 RGB pair stabilization | Better metrics, visible globe shadows; rejected |
| Broad black-nibble clamp | Crushed dark-blue texture; rejected |
| Carry, phase, offset, and first/second/weighted sample sweeps | Moved artifacts or shifted hues; rejected |
| Palette, gain, luma, chroma, and YIQ-matrix sweeps | No general visual win; rejected or folded into baseline coefficients |
| Committed YIQ-before-RGB aperture | Best non-regressed baseline; retained in `3ecddf8` |
| MAME `prbw`, `prcolor`, and box colorizers | Structurally useful, too posterized; rejected as target |
| Exact AppleWin monitor and Color TV tables | Sharper or flatter in places, globally farther; rejected |
| AppleWin idealized five-bit mask and palette | Removed stripes, posterized title; rejected |
| 280-pixel AppleWin-mask stability hybrid | Slight metric gain, no convincing live visual gain; reverted |
| Guard margins `8/6/4/2/0` and blend weights `2:3`, `3:5`, `1:2` | Traded stripes against fringes/detail; no accepted variant |
| YIQ only before stable regions | No useful visual change and slightly worse metrics; rejected |
| Native 560 idealized output | Neon/posterized; rejected |
| Native 560 rolling composite output | Sharp but exposed a strong 14 MHz comb; rejected |
| Native 560 guarded composite/stable hybrid | Cleaner detail in crops, poor whole-screen appearance; rejected |

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

### AppleWin-Mask Stability Hybrid At 280 Pixels

Result artifacts:

- `build/apple2-dhgr-smart/final-confirmed.png`
- `build/apple2-dhgr-smart/reference-left-final-right-x3.png`
- `build/apple2-dhgr-smart/rgb-mask-margin-full-sweep-x2.png`
- `build/apple2-dhgr-smart/rgb-mask-margin-top-globe-sweep-x4.png`
- rejected 400M CRC was `0xA6BBE423`;
- rejected 800M plus Space CRC was `0xC469CF94`.

What it did:

- reconstructed AppleWin's five-bit-neighborhood, four-phase color mask as a
  clean local algorithm;
- decoded the rolling 14 MHz stream through the local RGB lookup;
- downsampled each half-pixel pair with the existing `2:3` late-sample weight;
- averaged adjacent 280-pixel outputs only when the idealized mask stayed
  unchanged through a full four-sample color cycle plus one guard cycle on both
  sides;
- added tests for all 16 repeating masks, transition guards, and a synthetic
  flat DHGR scanline.

Sweeps performed:

- guard margins `8`, `6`, `4`, `2`, and `0`;
- half-pixel weights `2:3`, `3:5`, and `1:2`;
- an extra YIQ treatment only before stable regions.

Why it looked promising:

- full-frame RMSE improved from `35.69` to `34.70`;
- center RMSE improved from `28.15` to `27.20`;
- globe RMSE improved from `34.89` to `34.18`;
- the measured maroon stripe became `0.00` and stable yellow interior pairs
  became `0.00`;
- the implementation and its invariants were easier to understand and test.

Why it was rejected:

- copyright sharpness dropped from `59.47` to `57.99`;
- shorter guards and stronger late-sample bias added colored leading fringes;
- the first live review judged it visually "basically as before" despite the
  cleaner code;
- a second live review called the result "so-so";
- the numerical improvements did not amount to a convincing whole-screen
  improvement over the committed YIQ baseline.

The renderer changes and the new `Apple2VideoDeviceTest` were reverted. Keep
the behavioral derivation as evidence, but do not restore this branch solely
because its regional metrics are lower.

### Native 560-Pixel Framebuffer Variants

Result artifacts:

- `build/apple2-dhgr-560/idealized-native.png`
- `build/apple2-dhgr-560/composite-native.png`
- `build/apple2-dhgr-560/composite-stable-native.png`
- `build/apple2-dhgr-560/current-left-native-right.png`
- `build/apple2-dhgr-560/globe-reference-current-native.png`.

Architecture tried:

- changed the Apple II framebuffer from `280x192` to `560x192`;
- doubled text, lores, and ordinary hires horizontally so every Apple II mode
  kept the same displayed proportions;
- changed the desktop panel from `2x2` scaling to `1x2` scaling;
- let DHGR map one 14 MHz sample to one framebuffer pixel;
- temporarily updated test coordinates and the SuperDrive frame CRC.

Three renderers were checked on this path:

1. Native AppleWin-like idealized neighborhood color.
   - 400M CRC: `0xC59E7075`.
   - Preserved 560-position geometry but looked neon, coarse, and posterized.

2. Native rolling composite color for every half-pixel.
   - 400M CRC: `0x0262C06B`.
   - Restored fine detail, but exposed a strong 14 MHz comb and visible
     half-pixel striping on flat colors.

3. Native rolling composite with stable-mask palette replacement.
   - 400M CRC: `0xE156107B`.
   - 800M plus Space CRC: `0x7878F658`, with `keysInjected=1/1`.
   - Temporary SuperDrive smoke CRC: `0x82F547E0`.
   - Made copyright and some ornament edges sharper and avoided the broad
     aperture shadow, but the whole title still looked visibly worse.

Why the entire 560 branch was rejected:

- the final live verdict was unambiguously negative;
- retaining both 560 half-pixels exposed signal structure that the displayed
  image should integrate rather than present literally;
- idealized color removed that comb only by becoming posterized;
- the guarded hybrid sat between those failures without becoming convincing.

All 560 code, desktop scaling, tests, and CRC changes were reverted. The
`build/apple2-dhgr-560/` files are ignored diagnostic artifacts only and must
not be committed.

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
  -flop1 "media/Prince of Persia side A.woz" \
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

### AppleWin Follow-Up After YIQ Aperture (Historical)

AppleWin upstream was fetched and matched the local source snapshot already used
during tuning:

```text
NTSC.cpp sha1=9f807fa06f321b83af67afa7c48cad1ea0a9cfd2
Video.cpp sha1=2f4f228b717dd04b2c9c94dc95a4a4ccc4f4f03f
```

Portable AppleWin ideas were checked again on top of the then-current YIQ aperture:

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

Conclusion: keep the z8 YIQ aperture and tuned coefficients. The later geometry,
mask, and 560-pixel investigations added evidence but did not produce an
accepted replacement.

### AppleWin Geometry And Idealized-Mask Follow-Up

The later pass inspected current upstream `NTSC.cpp`, `RGBMonitor.cpp`, and
`Video.cpp` from [AppleWin](https://github.com/AppleWin/AppleWin), at repository
head `8193cc1fd1b8afeb78d5321b517548f7702c3e22` on 2026-07-14.

The full raw-composite coordinate mapping was reconstructed before trying more
offsets:

- a scanline starts at color phase zero with a zero 12-bit sequence and one
  carried DHGR bit;
- AppleWin's composite framebuffer starts three 14 MHz pixels left of the
  visible DHGR area: two pre-render pixels plus the DHGR one-pixel shift;
- its 280x192 screenshot path selects the second pixel from each 560-pixel pair;
- the resulting visible sample is equivalent to the local interior alignment
  with signal offset two and initial phase three.

This rules out a missing carry or half-pixel offset as the remaining root cause.
Exact raw AppleWin monitor and color-TV candidates were both materially farther
from the reference:

```text
candidate,yellowStripe,maroonStripe,webFull,webLeft,webCenter,webGlobe,copyrightSharpness
previous_yiq,1.30,5.35,35.69,34.86,28.15,34.89,59.47
applewin_exact_monitor,38.23,91.37,55.00,53.78,49.64,53.43,84.27
applewin_exact_color_tv,20.41,11.73,44.48,45.78,39.61,44.84,68.61
```

AppleWin's idealized DHGR path was then reconstructed behaviorally. It assigns a
four-phase color mask to each 14 MHz position from a five-bit neighborhood,
rather than painting coarse 140-cell rectangles. Pure idealized output removed
the stripe but remained too posterized; even averaging both idealized
half-pixels only reduced full-frame RMSE from `59.03` to `49.74`.

The portable part was the mask, not AppleWin's palette. It was used as proof
that the intended color stayed unchanged across a complete color cycle and one
guard cycle on each side, while edges continued through the rolling decoder.
That hybrid was cleaner than the coarse idealized renderer and scored slightly
better, but live review still rejected it. The detailed branch record and CRCs
are in `AppleWin-Mask Stability Hybrid At 280 Pixels` above.

No AppleWin source was copied. Its GPL implementation was used as behavioral
evidence for a clean local algorithm.

## Resolved Questions And Remaining Work

### 1. Decode In YIQ, Downsample Before RGB

Status: implemented as the committed baseline and retained after all later
candidates were visually rejected. Applying YIQ only to the pair before a
mask-stable region was also tested; it slightly worsened full/center metrics
without changing the yellow transition.

### 2. Correct 560-To-280 Sample Position

Status: resolved by the exact AppleWin border/pre-render derivation above.

Checked:

- first-half, second-half, average, 2:3, and 3:2 no-carry sample choices;
- AppleWin-style carry/pre-render variants at the exact derived alignment.

The local phase-three/offset-two alignment already maps to AppleWin's visible
interior sample. Do not run another offset sweep without evidence that changes
the border derivation.

Relevant old artifacts:

- `build/apple2-pop-downsample-mode-check-x8.png`
- `build/apple2-pop-left-globe-weight-sweep-x10.png`
- `build/apple2-pop-left-globe-tight-halfpair-x12.png`

### 3. Signal-Pattern-Aware Flat-Fill Stabilization

Status: investigated and rejected. The best condition used the AppleWin-derived
five-bit color mask over a 12-half-pixel window: one complete color cycle plus
one guard cycle on each side. It did not use screen coordinates, named colors,
or a fixed 140-cell palette, but it still failed live visual review.

Important constraint:

- the condition must be based on local signal regularity, not screen coordinates
  or named colors;
- it must not change thin text strokes, copyright letters, or ornament edges.

Temporary tests covered all 16 repeating masks, the transition guard, and flat
output pairs for a synthetic DHGR line. Those tests were removed with the
rejected renderer. Do not restore the mask branch without a new reason beyond
its already-known regional metric improvement.

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
   - beat the committed YIQ baseline's approximate yellow `1.30` and maroon
     `5.35` stripe measurements without changing real edge structure;
   - do not accept the change if these metrics improve only by making the whole
     image soft or posterized.

4. It keeps the current boot/game path deterministic.
   - update the documented long-probe CRC only after visual review;
   - the probe must still reach `status=max-instructions-reached` with
     `keysInjected=1/1`.

5. It passes the standard local checks:

```bash
./gradlew :machine-apple2:test :app-desktop:apple2SuperDrivePopSmoke
git diff --check
```

6. It leaves no failed experiments in executable code.
   - remove probe-only constants and abandoned helper methods;
   - keep historical CRCs and marker names in this document only;
   - search the renderer source for known rejected markers before finishing:

```bash
rg -n 'SOLID_YELLOW|yellow-flat|chroma-iq16|140cell|aperture|0x0AEB544C|0x245A161B|0xFD3375D9' \
  machines/apple2/src/main/java/dev/z8emu/machine/apple2/Apple2VideoDevice.java
```

The final review should be visual first. Metrics are there to catch regressions,
not to overrule the obvious: if copyright text turns to mush or the title gains
left shadows, reject it.
