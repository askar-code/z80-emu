# Apple II Plus Runbook

This runbook covers the Apple II-family targets currently in use: `Apple II
Plus` / `Apple ][+` for the original prompt/DOS path, and the minimal `Apple IIe
128K` compatibility profile used by the Prince of Persia WOZ bring-up. The
launcher ids are `apple2`/`apple2plus` for II Plus and
`apple2e`/`appleiie`/`apple2e-128k` for the current IIe profile.

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
- `--expect-frame-crc=...`: final rendered framebuffer CRC32 that must match;
  use this for graphics-mode targets where the text page is not the visible UI
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
- `--trace-io`: print Apple II memory-mapped I/O accesses routed through the
  shared I/O address-space map
- `--trace-soft-switches`: print Apple II `$C0xx` soft-switch accesses without
  enabling unrelated future I/O traces
- `--trace-disk2`: print detailed Disk II switch/read-latch state transitions
- `--superdrive35-rom=...`: install the Apple II 3.5 / SuperDrive controller
  card host-side skeleton with an externally supplied 32 KB controller ROM
- `--superdrive35-media=...`: attach an 800 KB ProDOS-ordered `.po` image as
  low-level Apple 3.5 GCR media under the SuperDrive controller without loading
  boot blocks directly
- `--superdrive35-slot=N`: choose the SuperDrive card slot, default `5`
- `--superdrive35-warmup-tstates=N`: let the SuperDrive card's onboard 65C02
  run before the probe entry point; useful for direct boot-block probes that
  bypass the normal Apple II ROM slot-scan delay
- `--trace-limit=N`: cap retained trace lines, default `256`
- `--trace-tail`: retain the last `--trace-limit` lines instead of the first
  lines; useful once boot traces are too noisy and the interesting I/O is late
- `--dump-frame=...`: write the final Apple II framebuffer to a PNG file
- `--prodos-boot-blocks=...`: diagnostics-only `.po` boot-block loader; it
  loads blocks 0-1 and starts them without installing the ProDOS block-device
  shim, so it can be combined with `--superdrive35-rom=...`

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
./gradlew :app-desktop:apple2RomProbe --args='apple2plus-12k.rom 220000000 --disk=build/apple2-oregon-sit/oregon-trail-side-a.dsk --disk2-rom=build/apple2-disk2-roms/341-0027-p5.bin --key-poll-pc=6205,6208 --keys=1<CR> --dump-frame=build/apple2-oregon-prom-choice1-cr.png --expect-frame-crc=E6A0C03F --profile-pc-top=20'
```

For a short Disk II routing trace, add:

```bash
./gradlew :app-desktop:apple2RomProbe --args='apple2plus-12k.rom 2000000 --disk=build/apple2-oregon-sit/oregon-trail-side-a.dsk --disk2-rom=build/apple2-disk2-roms/341-0027-p5.bin --trace-io --trace-disk2 --trace-limit=80'
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
- supported: read-only WOZ1 5.25-inch media under the Disk II controller
- supported: 800 KB ProDOS ordered `.po` images as byte-oriented Apple 3.5 GCR
  media under the SuperDrive controller ROM
- deferred: clean-room Disk II boot ROM, write support, `.nib`, WOZ2, and
  copy-protection-accurate bit timing

Inspect a ProDOS `.po` image root catalog:

```bash
./gradlew :app-desktop:apple2ProDosCatalog --args='"Prince of Persia (Cracked 3.5 floppy for IIc+).po"'
```

For the local Prince of Persia image, expected output includes:

```text
volume=/P.O.P.
PRODOS type=0xFF key=8 blocks=9 eof=4096 storage=2 crc32=0xE5273478
```

Extract the ProDOS loader file for disassembly or byte-level comparison:

```bash
./gradlew :app-desktop:apple2ProDosCatalog --args='"Prince of Persia (Cracked 3.5 floppy for IIc+).po" --extract=PRODOS --output=build/apple2-pop/PRODOS.bin'
```

Scan the loader as linear 6502 code loaded at `$2000`:

```bash
./gradlew :app-desktop:apple2ProDosCatalog --args='"Prince of Persia (Cracked 3.5 floppy for IIc+).po" --scan=PRODOS --load-address=2000 --scan-limit=24'
```

Useful current markers from the local image:

```text
scan=PRODOS
scanBytes=4096
scanCrc32=0xE5273478
loadAddress=0x2000
strings:
  offset=0x009F pc=0x209F "REQUIRES A //C OR //E WITH 128K"
absoluteRefs:
  offset=0x000A pc=0x200A opcode=0x20 JSR abs target=0x2031
  offset=0x000D pc=0x200D opcode=0x20 JSR abs target=0x205E
  offset=0x0010 pc=0x2010 opcode=0x20 JSR abs target=0x20E4
  offset=0x0013 pc=0x2013 opcode=0x20 JSR abs target=0x2123
ioRefs:
  offset=0x004B pc=0x204B opcode=0x8E STX abs target=0xC081 area=language-card
  offset=0x0102 pc=0x2102 opcode=0x2C BIT abs target=0xC08B area=language-card
  offset=0x0265 pc=0x2265 opcode=0x2C BIT abs target=0xC0E8 area=slot6-io
  offset=0x026B pc=0x226B opcode=0x2C BIT abs target=0xC0ED area=slot6-io
  offset=0x026E pc=0x226E opcode=0x8D STA abs target=0xC0EF area=slot6-io
```

The Apple IIc Plus detour has been removed. The current PoP target is Apple IIe
128K again, using the new low-level 5.25-inch WOZ images:

- `Prince of Persia side A.woz`
- `Prince of Persia side B.woz`

Their WOZ metadata says:

```text
WOZ1
diskType=1
requires_ram=128K
requires_machine=2e|2e+|2c|2gs
```

The current implementation has a first WOZ1 reader wired into the existing
Disk II controller. It feeds 5.25 bit/nibble tracks through the normal slot-6
soft switches; it is not a ProDOS block-image path and not an Apple IIc Plus
firmware path.

Only side A is mounted today. `Prince of Persia side B.woz` is kept for the
later disk-swap/two-drive step; the current Disk II controller has one inserted
media image in drive 1 and returns no data when software selects drive 2.

Current side-A WOZ probe:

```bash
./gradlew :app-desktop:apple2RomProbe --args='--machine=apple2e . 100000000 --disk="Prince of Persia side A.woz" --disk2-rom=build/apple2-disk2-roms/341-0027-p5.bin --stop-pc=0800,0801,0802,0803,0804 --profile-pc-top=16 --watch-addr=0800,0801,0802,0803,0804,0805,0806,0807,0808,0809'
```

Expected boot-sector stop:

```text
status=stop-pc-reached
pc=0x0801
lastPc=0xC6F8
0800: 01
0801: A9
0802: 60
0803: 8D
```

A longer run now gets past the Disk II ROM, the custom track reader, and the
title screen:

```bash
./gradlew :app-desktop:apple2RomProbe --args='--machine=apple2e . 800000000 --disk="Prince of Persia side A.woz" --disk2-rom=build/apple2-disk2-roms/341-0027-p5.bin --keys=<SPACE> --key-poll-pc=0CC2 --profile-pc-top=16 --watch-addr=2F00,3000,4000,6000,7000,8000 --dump-frame=build/apple2-pop-woz-side-a-dhgr-space.png'
```

Expected current keyboard-latch probe result:

```text
status=max-instructions-reached
textMode=false
keysInjected=1/1
frameCrc32=0x723291BA
```

Interpretation: track-0 standard boot works, the custom reader touches tracks
including `01,02,03,04,09,10,11,12,13,19,20,21,34`, and the loader reaches the
title/game loop. The PoP input poll reads the keyboard at PC `$0CC2`, then
checks pushbuttons at `$C061/$C062`. A disk trace with `--trace-tail` shows the
last custom reads on track 34 followed by motor-off at `$C0E8`, so the live loop
after that is no longer a Disk II boot failure.

The startup/title artwork uses Apple IIe double-hires (`HIRES` plus `80COL`,
status `$C01D/$C01F` both set). If it appears vertically shredded while normal
intro/gameplay HGR screens look fine, the problem is the video renderer missing
aux+main double-hires composition or using the wrong DHGR color phase, not a bad
side-A WOZ image. The current DHGR renderer builds the 560 half-pixel signal,
decodes it through a rolling 12-bit AppleWin-style NTSC table with tuned
YIQ-to-RGB coefficients, then averages pairs back into the 280-pixel
framebuffer. Only a truly idle 12-bit signal is clamped to black, so dark blue
texture keeps the rolling NTSC color bleed instead of being crushed. This keeps
the title artwork sharp while preserving subpixel color detail in thin
ornaments; it is still a pragmatic composite approximation, not a full analog
monitor model.

Manual desktop controls for the current PoP path:

- hold `Space` briefly on the title screen to trigger pushbutton 0 and start
  the game
- `Left` / `Right` drive paddle 0
- `Up` / `Down` drive paddle 1
- `Space`, `Shift`, or `Control` drive pushbutton 0

Apple IIe 128K compatibility command for the older `.po` diagnostic image:

```bash
./gradlew :app-desktop:run --args='--machine=apple2e apple2plus-12k.rom "Prince of Persia (Cracked 3.5 floppy for IIc+).po"'
```

Do not use `apple2plus-12k.rom` as an Apple IIe stand-in for PoP debugging.
The workspace now has local enhanced Apple IIe ROM parts:
`342-0303-a.e8`, `342-0304-a.e10`, `342-0265-a.chr`, and `341-0132-d.e12`.
The two CPU-visible ROM halves are now wired for `--machine=apple2e`: use the
project directory (`.`) or either standard ROM-half path as the ROM argument.
The loader maps `342-0304-a.e10` to `$C000-$DFFF` and `342-0303-a.e8` to
`$E000-$FFFF`; `$C0xx` remains I/O. Apple IIe Cx ROM selection follows the
MMU soft switches: `$C006` selects external slot ROMs, `$C007` selects internal
Cx ROM, and `$C00A/$C00B` choose internal/external `$C300`.

Real Apple IIe ROM reset-vector smoke:

```bash
./gradlew :app-desktop:apple2RomProbe --args='--machine=apple2e . 20 --stop-pc=FA62 --watch-addr=FFFC,FFFD,C100,C65C,C800,D000'
```

Expected markers:

```text
status=stop-pc-reached
imageBytes=16384
pc=0xFA62
opcode=0xD8
FFFC: 62
FFFD: FA
```

Current PoP block-shim probe:

```bash
./gradlew :app-desktop:apple2RomProbe --args='--machine=apple2e . 300000 --prodos-boot="Prince of Persia (Cracked 3.5 floppy for IIc+).po" --stop-pc=0000 --watch-addr=D000,D100,D800,E000,EE00,FFFC,FFFD,FFFE,FFFF --profile-pc-top=8'
```

Current result: the boot path gets past the observed 65C02 opcodes (`BRA` at
`$2016` and immediate NOP `$02` later in the loader) and loads some high-memory
code, but stops after executing `$EE00` from empty language-card RAM:

```text
status=stop-pc-reached
pc=0x0000
lastPc=0xEE00
lastOpcode=0x00
D000: 4C
D800: 00
EE00: 00
```

That is the next live debugging boundary: verify language-card bank/read state
and the diagnostic ProDOS block-device shim before assuming the SuperDrive/SWIM
media path is required.

Current desktop behavior: generic `.po` media arguments are catalog-recognized,
but the Swing launcher still does not auto-select the SuperDrive card. Use the
explicit `apple2RomProbe --superdrive35-*` path or the dedicated smoke tasks
for 3.5-inch boot checks:

```text
Apple II ProDOS .po images need an explicit Apple 3.5 / SuperDrive path for booting
```

The released PoP source archive includes `RW1835` 18-sector disk routines and
a 3.5-inch boot/routine path. For this target, keep these probes ready:

- a diagnostic ProDOS block-device shim for quick `.po` block reads
- an Apple II 3.5 / SuperDrive controller card path for enhanced IIe-style
  bring-up; this is an intelligent card, not a 256-byte `$Cn00` ROM

The old bare `--iwm35` / `--iwm35-rom` slot-6 probe was removed. It modeled the
wrong host-visible shape for this PoP route: the real IIe target is the
intelligent SuperDrive card with a 32 KB controller ROM and shared RAM windows.

Current SuperDrive controller-card probe:

```bash
./gradlew :app-desktop:apple2RomProbe --args='--machine=apple2e . 3000000 --host-warmup-instructions=200000 --superdrive35-rom=341-0438-a.bin --superdrive35-slot=5 --superdrive35-warmup-tstates=1000000 --prodos-boot-blocks="Prince of Persia (Cracked 3.5 floppy for IIc+).po" --prodos-boot-slot=5 --trace-superdrive --trace-limit=2600 --profile-pc-top=12 --watch-addr=03F0,03F1,C500,C55C,C800,C801,C802,C803,C804,C805,C806,C807,C808,C809,C80A,0048,0049,004A,004B,0C00,0C01,0C23,2000,2001,2002,2003'
```

Prefer this form when checking the real host firmware path, because it attaches
the same media but does not inject PoP boot blocks or call the wrong `$Cx5C`
entry:

```bash
./gradlew :app-desktop:apple2RomProbe --args='--machine=apple2e . 20000000 --superdrive35-rom=341-0438-A.bin --superdrive35-media="Prince of Persia (Cracked 3.5 floppy for IIc+).po" --superdrive35-slot=5 --superdrive35-warmup-tstates=2000000 --dump-frame=build/apple2-superdrive/pop-superdrive-20m.png --profile-pc-top=8'
```

Current result with the enhanced Apple IIe ROM: the real slot-boot path boots
800 KB `.po` media through the SuperDrive controller ROM. The local Apple IIe /
IIc / IIc Plus system disk reaches the ProDOS 8 banner, and the local PoP 3.5
image reaches hires graphics. The PoP smoke frame is
`build/apple2-superdrive/pop-superdrive-20m.png` with CRC32 `0xF386BEDD`.

For regression checks, prefer the dedicated external-ROM/media smoke tasks:

```bash
./gradlew :app-desktop:apple2SuperDriveSystemSmoke
./gradlew :app-desktop:apple2SuperDrivePopSmoke
```

They default to local ignored files:

- `341-0438-A.bin`
- `build/apple2-superdrive/apple2e-iic-iicplus-system-disk.po`
- `Prince of Persia (Cracked 3.5 floppy for IIc+).po`

Override them when needed:

```bash
./gradlew :app-desktop:apple2SuperDrivePopSmoke -Papple2.superdrive35.rom=/path/to/341-0438-A.bin -Papple2.superdrive35.popDisk=/path/to/pop.po
```

This path requires the real 32 KB Apple II 3.5 / SuperDrive controller ROM.
The local `341-0438-a.bin` has been verified:

```text
bytes=32768
crc32=c73ff25b
sha1=440c3f84176c7b9f542da0b6ddf4fb13ec326c46
```

The emulator now runs the card's onboard 65C02 firmware and exposes the Apple
II-visible windows: `$C0nX` selects the controller RAM bank, `$Cn00-$CnFF`
reads controller RAM at `$7B00-$7BFF`, and `$C800-$CFFF` exposes the shared
expansion window. Host writes to `$Cn00-$CnFF` are intentionally ignored; the
Apple II side writes shared command packets through `$C800-$CFFF`. This matches
MAME's `a2superdrive` map and the real card shape.

`--host-warmup-instructions` is separate: it runs the Apple IIe host ROM before
the diagnostic boot blocks are loaded. This initializes firmware vectors such
as `$03F0=FA59`. After that warmup the probe reselects external Cx ROM through
`$C006`, because the IIe ROM warmup can leave internal Cx ROM selected and a
direct slot boot needs the external card page.

When this probe combines `--prodos-boot-blocks` with a SuperDrive controller,
the `.po` image is also inserted below the controller firmware as a low-level
Apple 3.5 GCR media stream. The stream is built from ProDOS block order into
80 tracks, 2 heads, Apple 3.5 zone sector counts, address/data fields, 12-byte
tags, and byte-oriented GCR data. This is no longer the synthetic ProDOS block
shim, but it is still not a bit-cell-accurate drive model.

`--trace-superdrive` traces both sides of the card: onboard controller I/O
(`src=controller`), Apple II host accesses to `$C0nX`, `$CnXX`, and the
`$C800` shared RAM window (`src=host`), and controller-side reads/writes of
host-touched shared RAM (`region=controller-ram-host-touched`). Before the
ISM parameter-RAM implementation, the PoP probe reached the boot blocks'
visible failure loop with no controller CPU fault:

```text
pc=0x094D
superdrive35CpuPc=0xD1A6
superdrive35Track=0
superdrive35Side=0
superdrive35SwimDataReady=1
superdrive35Swim=data:0xFF handshake:0xF0 status:0xDF mode:0x1F
11|@@@@@@*** UNABLE TO LOAD PRODOS ***@@@@@
```

After the minimal ISM parameter-RAM implementation and host ROM warmup, the
current boundary has moved. The old visible error screen is no longer the live
stop; a representative run now ends in the boot-block loop around `$0896`:

```text
status=max-instructions-reached
pc=0xC27D
superdrive35CpuPc=0x98D3
superdrive35Swim=data:0xFF handshake:0xF0 status:0xCF mode:0x0F
03F0: 59
03F1: FA
0C00: 00
2000: 00
```

The trace shows the host can touch the external slot page after warmup, but it
still sees zeros from the early SuperDrive Cnxx service path:

```text
src=host pc=0x081F R region=cnxx addr=0xC5FF value=0x00
src=host pc=0xC55C R region=cnxx addr=0xC55C value=0x00
```

This is now classified as a probe-target mismatch, not as a SWIM/media failure.
After warmup the real controller firmware exposes small entries such as
`$C500=4C 59 FF`, `$C50D=4C 59 FF`, and `$C523=4C 59 FF`, then leaves `$C55C`
zero. The direct `--prodos-boot-blocks` boot code is trying to use a
Disk-II/ProDOS-style `$Cx5C` entry that this SuperDrive card does not provide.

The earlier mailbox checkpoint remains useful only when a real host firmware or
driver reaches that path. The host-side marker is a command packet written
through shared RAM, followed by the strobe:

```text
W region=c800 addr=0xC802 value=0x81
W region=c800 addr=0xC803 value=0x01
W region=c800 addr=0xC804 value=0x0C
W region=c800 addr=0xC805 value=0x02
W region=c800 addr=0xC80A value=0x0C
W region=c800 addr=0xC800 value=0x01
```

The matching controller-side marker proves the mailbox bridge is alive: the
onboard 65C02 consumes that packet, writes a zero status byte, and clears the
command byte.

```text
R region=controller-ram-host-touched addr=0x0000 value=0x01 pc=0xD1B8
R region=controller-ram-host-touched addr=0x0002 value=0x81 pc=0xD1BE
R region=controller-ram-host-touched addr=0x0003 value=0x01 pc=0xD1C2
R region=controller-ram-host-touched addr=0x0005 value=0x02 pc=0xD1C6
R region=controller-ram-host-touched addr=0x0006 value=0x00 pc=0xD1CA
W region=controller-ram-host-touched addr=0x0001 value=0x00 pc=0xD29C
W region=controller-ram-host-touched addr=0x0000 value=0xFF pc=0xD1D5
```

This direct boot-block boundary is historical now: it remains a useful warning
that `$Cx5C` shims are the wrong shape for this card, but the real enhanced IIe
slot-boot path can boot `.po` media through the SuperDrive controller ROM. Do
not add more `$Cx5C` shims to the SuperDrive card.

Scan the ProDOS boot blocks 0-1:

```bash
./gradlew :app-desktop:apple2ProDosCatalog --args='"Prince of Persia (Cracked 3.5 floppy for IIc+).po" --scan-boot --load-address=0800 --scan-limit=24'
```

Useful current markers:

```text
bootScan=blocks0-1
bootScanBytes=1024
bootScanCrc32=0xC720393F
loadAddress=0x0800
strings:
  offset=0x0102 pc=0x0902 "&PRODOS         %`"
  offset=0x0150 pc=0x0950 "*** UNABLE TO LOAD PRODOS ***%S)"
absoluteRefs:
  offset=0x00FC pc=0x08FC opcode=0x4C JMP abs target=0x2000
ioRefs:
  offset=0x0175 pc=0x0975 opcode=0xBD LDA abs,X target=0xC080 area=language-card
  offset=0x01A7 pc=0x09A7 opcode=0xBD LDA abs,X target=0xC089 area=language-card
  offset=0x01B8 pc=0x09B8 opcode=0xBC LDY abs,X target=0xC088 area=language-card
  offset=0x01F2 pc=0x09F2 opcode=0xBD LDA abs,X target=0xC08C area=language-card
```

Current Apple IIe 128K compatibility coverage:

- `80STORE` writes at `$C000/$C001`
- `RAMRD` writes at `$C002/$C003`
- `RAMWRT` writes at `$C004/$C005`
- `ALTZP` writes at `$C008/$C009`
- `80COL` writes at `$C00C/$C00D`
- `ALTCHARSET` writes at `$C00E/$C00F`
- status reads at `$C013-$C01F`
- aux zero page/stack, aux `$0200-$BFFF`, and `80STORE+PAGE2` text-page bank
  routing

This layer is intentionally disabled for Apple II Plus configs.

## Video Coverage

Current Apple II Plus video support covers:

- 40x24 text mode
- lo-res graphics page 1 and page 2
- hi-res graphics page 1 and page 2 with first-pass NTSC artifact colors:
  black, white, violet, green, blue, and orange
- mixed text/graphics mode with the bottom four text rows

Hi-res artifact colors are still a pragmatic emulator model, not a full analog
NTSC simulation.

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
colors are still approximate.

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

- Disk II currently supports the Oregon/DOS-order read path and a first
  read-only WOZ1 5.25-inch path. It is still not writable, and protected media
  may need tighter track/timing behavior.
- Apple IIe support is partial: aux memory, 80-column switches, enhanced IIe ROM
  mapping, `ALTZP` language-card selection, `$C019` VBL status, and 65C02 mode
  exist for PoP probes, but this is not a complete IIe.
- 800 KB ProDOS `.po` media can be inspected and booted as byte-oriented Apple
  3.5 GCR under the SuperDrive controller ROM. This is enough for current
  system-disk and PoP smoke checks, but it is still not a bit-cell-accurate
  writeable drive model.
- The PoP WOZ images are 5.25-inch low-level media for Apple IIe 128K; side A
  now reaches and executes the first boot sector, reads custom-format tracks,
  displays the title/game graphics, and accepts desktop game-port input.
- Remaining visible PoP polish is mostly compatibility quality: artifact-color
  hi-res rendering now has the basic Apple II color model, but it is not a full
  NTSC simulation; headless probe scripting still only injects keyboard latch
  bytes and does not yet script game-port button/paddle events.
- Raw memory-loaded programs are supported for debugger-style bring-up before
  and alongside Disk II.
