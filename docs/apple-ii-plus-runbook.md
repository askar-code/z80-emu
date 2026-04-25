# Apple II Plus Runbook

This runbook covers the current Apple II-family target: `Apple II Plus` /
`Apple ][+`. The launcher ids are `apple2` and `apple2plus`; `apple2e` and
`appleiie` intentionally remain unsupported until a real Apple IIe model exists.

## Local ROM

The canonical local ROM path is:

```bash
apple2plus-12k.rom
```

The file is intentionally ignored by git through `*.rom`. It must stay outside
the repository history.

The current local image was assembled from the Apple II Plus D0/D8/E0/E8/F0 ROM
chips plus the shared F8 monitor ROM found under `~/Downloads/apple2` and
`~/Downloads/apple2p`.

## Desktop Prompt Smoke

Launch the Apple II Plus desktop shell from the repo root:

```bash
./gradlew :app-desktop:run --args='--machine=apple2plus apple2plus-12k.rom'
```

Expected first screen:

- `APPLE ][` at the top
- Applesoft `]` prompt
- flashing cursor at the prompt

Manual smoke:

```text
PRINT 2+2
```

Press Enter. The next line should print:

```text
4
```

## Headless BASIC Smoke

Use the dedicated smoke task when the default local ROM path exists:

```bash
./gradlew :app-desktop:apple2BasicSmoke
```

Use `-Papple2.rom=...` when the ROM lives elsewhere:

```bash
./gradlew :app-desktop:apple2BasicSmoke -Papple2.rom=/path/to/apple2plus-12k.rom
```

The task is an external-ROM regression, not part of normal `test`, because the
ROM must not be committed. Expected key output:

```text
status=expectation-met
keysInjected=10/10
expectScreen=4
expectScreenFound=true
02|]PRINT 2+2
03|4
```

## Direct Probe Command

For custom probe runs, call `apple2RomProbe` directly:

```bash
./gradlew :app-desktop:apple2RomProbe --args='apple2plus-12k.rom 1500000 --keys=PRINT<SP>2+2<CR> --expect-screen=4'
```

Arguments:

- first positional argument: 4 KB, 8 KB, or 12 KB Apple II system ROM image, or
  a full 64 KB memory image
- second positional argument: max instruction count
- `--keys=...`: key script injected at the ROM keyboard polling loop
- `--expect-screen=...`: text that must appear on the visible 40x24 text page
- `--key-poll-pc=0x....`: override the ROM key-poll PC, default `0xFD21`;
  comma-separated values are allowed for boot code that polls before ROM input
- `--stop-pc=0x....`: stop as soon as the CPU reaches one of the listed PCs
- `--watch-addr=0x....`: print selected memory or I/O bytes in the final state
- `--poke-on-pc=0x....:0x....=0x..`: write diagnostic bytes when the CPU reaches
  a PC; multiple assignments are comma-separated, multiple PCs are
  semicolon-separated
- `--profile-pc-callers=0x....`: for each listed PC, count stack return
  addresses so DOS/RWTS delay subroutine callers can be separated
- `--profile-pc-top=N`: print the hottest PCs seen during the probe run
- `--dump-frame=...`: write the final Apple II framebuffer to a PNG file

Useful key script tokens:

- `<SP>` for space
- `<CR>`, `<ENTER>`, or `<RETURN>` for carriage return
- `<TAB>`, `<ESC>`, `<BS>`, `<LEFT>`, `<RIGHT>`
- `\r`, `\n`, `\t`, `\\`, and `\xNN`

## Raw Program Loader

For debugger-style bring-up before Disk II, the desktop launcher accepts one
optional raw Apple II binary after the ROM path:

```bash
./gradlew :app-desktop:run --args='--machine=apple2plus --load-address=0800 --start-address=0800 apple2plus-12k.rom /path/to/program.bin'
```

Options:

- `--load-address=....`: raw binary load address, hexadecimal; default `0800`
- `--start-address=....`: optional PC override after ROM/memory image creation

Without `--start-address`, the raw program is placed in RAM and the machine
continues from the ROM reset vector. With `--start-address`, the CPU starts at
that address immediately after the load.

## Disk II Hardware Smoke

The current Disk II path supports DOS 3.3 ordered 140 KB images (`.do` or
DOS-order `.dsk`) as rotating media in slot 6. The old synthetic `$C600` /
`$C65C` sector-copy shortcut has been removed: disk bytes must now be consumed
through the Disk II soft switches and read latch.

Run a disk image in the desktop shell without a Disk II PROM:

```bash
./gradlew :app-desktop:run --args='--machine=apple2plus apple2plus-12k.rom build/apple2-oregon-sit/oregon-trail-side-a.dsk'
```

This inserts the disk and then lets the Apple II Plus ROM boot normally to the
monitor/BASIC path. It does not automatically jump into slot 6 without a real
Disk II PROM, and slot 6 reads as empty ROM bytes until `--disk2-rom` is
supplied. Apple II desktop execution stays at realtime; there is no disk
auto-turbo path.

Run a disk image through an externally supplied Disk II slot ROM:

```bash
./gradlew :app-desktop:run --args='--machine=apple2plus --disk2-rom=/path/to/disk2.rom apple2plus-12k.rom build/apple2-oregon-sit/oregon-trail-side-a.dsk'
```

The Disk II ROM file must be exactly 256 bytes. When both `--disk2-rom` and a
disk image are present, the launcher starts at `$C600`; the CPU then executes
the slot ROM and all disk bytes come through the Disk II latch/soft switches.
No Apple Disk II PROM bytes are committed to this repository.

For DOS 3.3 / 16-sector disks such as Oregon Trail, use the Disk II interface
card P5 boot PROM:

```bash
build/apple2-disk2-roms/341-0027-p5.bin
```

The commonly mirrored Disk II interface ROM set contains four 256-byte files:

- `341-0009` / 13-sector P5: CPU-visible boot PROM for older DOS 3.2-era disks
- `341-0010` / 13-sector P6: companion logic PROM for older 13-sector cards
- `341-0027` / 16-sector P5: CPU-visible boot PROM for DOS 3.3 disks
- `341-0028` / 16-sector P6: companion logic PROM for 16-sector cards

Reference mirror:
`https://mirrors.apple2.org.za/Apple%20II%20Documentation%20Project/Interface%20Cards/Disk%20Drive%20Controllers/Apple%20Disk%20II%20Interface%20Card/ROM%20Images/`

Only P5 is loaded by the current `--disk2-rom` option because P5 is the ROM the
6502 sees in the slot ROM window (`$C600-$C6FF` for slot 6). P6 belongs to the
card's disk-control logic; the current Disk II abstraction already models that
side directly instead of loading P6 bytes.

Headless external-PROM boot probe:

```bash
./gradlew :app-desktop:apple2RomProbe --args='apple2plus-12k.rom 200000000 --disk=build/apple2-oregon-sit/oregon-trail-side-a.dsk --disk2-rom=build/apple2-disk2-roms/341-0027-p5.bin --dump-frame=build/apple2-oregon-disk2-prom.png --profile-pc-top=20'
```

Expected result: the run reaches the Oregon Trail menu and waits in its keyboard
poll loop. To smoke the first menu choice:

```bash
./gradlew :app-desktop:apple2RomProbe --args='apple2plus-12k.rom 220000000 --disk=build/apple2-oregon-sit/oregon-trail-side-a.dsk --disk2-rom=build/apple2-disk2-roms/341-0027-p5.bin --key-poll-pc=6205,6208 --keys=1<CR> --dump-frame=build/apple2-oregon-prom-choice1-cr.png --profile-pc-top=20'
```

Disk II timing sanity check:

- 300 RPM means one full track revolution is about 200ms.
- 32 Apple II CPU cycles per raw disk byte gives roughly 6400 raw bytes per
  track in this emulator's nominal clock model.
- A raw sequential 35-track read therefore has a rotational lower bound of
  about 7.0s before track-to-track seek and software decode overhead.
- DOS/RWTS-style full-track reads are closer to two revolutions per track in
  the common interleaved path, so a normal DOS-ish full-disk read budget is
  still in the tens of seconds, not minutes.

Motor coast note: a real Disk II mechanism keeps rotating briefly after `C088`
motor-off, so this controller keeps disk bytes moving for a short spin-down
window. Unlike the removed turbo path, this does not patch DOS/RWTS memory or
copy sectors directly.

Current disk limits:

- supported: read-only DOS 3.3 ordered 35-track, 16-sector, 140 KB images
- supported: slot 6 motor, motor spin-down/coast, drive select, phase stepping,
  read latch, and write-protect sense
- supported: clock-paced Disk II read-latch byte timing
- supported: externally supplied 256-byte Disk II slot ROM loading
- deferred: clean-room Disk II boot ROM, write support, `.nib`, `.woz`, 800 KB
  3.5-inch ProDOS images, and copy-protection-accurate bit timing

## Video Coverage

Current Apple II Plus video support covers:

- 40x24 text mode
- lo-res graphics page 1 and page 2
- hi-res graphics page 1 and page 2 with monochrome bit rendering
- mixed text/graphics mode with the bottom four text rows

Hi-res artifact-color precision is intentionally deferred until a real software
target needs it.

## Manual Graphics Smoke

Launch the desktop shell and try these from the Applesoft prompt:

```text
GR
```

This should switch to lo-res mixed mode and leave the bottom text prompt
visible.

For a visible lo-res memory pattern:

```text
POKE -16304,0
POKE -16298,0
FOR I=0 TO 255:POKE 1024+I,I:NEXT
```

For hi-res through Applesoft:

```text
HGR
HCOLOR=3
HPLOT 0,0 TO 279,159
```

These are emulator smoke checks, not visual reference tests. Hi-res artifact
colors are still intentionally approximate.

Headless PNG equivalents:

```bash
./gradlew :app-desktop:apple2RomProbe --args='apple2plus-12k.rom 600000 --keys=GR<CR> --dump-frame=build/apple2-graphics/gr.png'
```

```bash
./gradlew :app-desktop:apple2RomProbe --args='apple2plus-12k.rom 1600000 --keys=POKE<SP>-16304,0<CR>POKE<SP>-16298,0<CR>FOR<SP>I=0<SP>TO<SP>255:POKE<SP>1024+I,I:NEXT<CR> --dump-frame=build/apple2-graphics/lores-poke.png'
```

```bash
./gradlew :app-desktop:apple2RomProbe --args='apple2plus-12k.rom 1600000 --keys=HGR<CR>HCOLOR=3<CR>HPLOT<SP>0,0<SP>TO<SP>279,159<CR> --dump-frame=build/apple2-graphics/hires-hplot.png'
```

## Current Limits

- Disk II currently supports the Oregon/DOS-order read path, not writable or
  nibble-perfect protected media.
- 80-column mode, auxiliary memory, joystick, and Apple IIe behavior are
  intentionally deferred.
- Raw memory-loaded programs are supported for debugger-style bring-up before
  and alongside Disk II.
