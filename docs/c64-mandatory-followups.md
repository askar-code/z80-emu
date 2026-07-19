# C64 Mandatory Review Follow-ups

Blocking follow-ups from the 2026-07-18 implementation review. This file is
limited to confirmed defects and required work that was not already an
actionable item in `commodore-64-plan.md`. It is a must-fix queue, not a general
wishlist.

Do not mark an item complete without an executable regression gate. When an
item lands, record the implementation commit, frozen behavior, and gate result
here, then fold the resulting contract into `commodore-64-plan.md`.

Status: `[ ]` open · `[~]` in progress · `[x]` complete

## Confirmed correctness defects

### [ ] C64-F01 — NMOS memory RMW must perform the old-value dummy write (P0)

Current `Mos6502Cpu` memory read-modify-write helpers perform:

```text
read -> write modified value
```

An NMOS 6502/6510 performs:

```text
read -> write original value -> write modified value
```

This is externally visible for memory-mapped devices. In particular, the
standard C64 `INC $D019` raster-IRQ acknowledgement is currently broken:
`$D019` reads as `$F1`, but the emulator writes only the incremented `$F2`, so
VIC-II raster bit 0 is never cleared. The old-value write of `$F1` is what
acknowledges the interrupt on hardware.

Required scope:

- Add the NMOS old-value write to all documented memory RMW instructions:
  `ASL`, `LSR`, `ROL`, `ROR`, `INC`, and `DEC`, for every addressing mode.
- Apply the same bus sequence to NMOS undocumented RMW families (`SLO`, `RLA`,
  `SRE`, `RRA`, `DCP`, and `ISC`).
- Preserve the appropriate 65C02 bus behavior; do not blindly share the NMOS
  double-write path across variants.
- Treat the write sequence as an observable bus contract, not merely an
  arithmetic implementation detail. The later cycle-aware CPU work must also
  place the two writes on their correct cycles.

Acceptance gates:

- CPU bus-spy tests assert ordered writes `[old, new]` for every NMOS RMW
  family and representative addressing modes.
- A C64 integration test enables a raster IRQ, executes `INC $D019`, and proves
  that the VIC IRQ line is released.
- Existing MOS 6502, 65C02/Apple II, Klaus, and C64 gates remain green.

References:

- `cpu/mos6502/.../Mos6502Cpu.java` (`incrementMemory`, `decrementMemory`,
  shifts/rotates, and undocumented RMW helpers)
- `machines/c64/.../C64VideoDevice.java` (`$D019` write-one-to-clear handler)
- [MOS MCS6500 single-cycle execution tables](https://xotmatrix.com/6502/6502-single-cycle-execution.html)
- [NMOS 6510: Unintended Opcodes — dummy writes and `INC $D019`](https://c64.cz/data2/download/x20/206881/NoMoreSecrets-NMOS6510UnintendedOpcodes-20202412.pdf)

### [ ] C64-F02 — VIC-II collision sources must raise `$D019` IRQ bits (P0)

Sprite-sprite and sprite-data collision values are latched in `$D01E/$D01F`,
but the collision path never sets the corresponding VIC interrupt latch. Only
the raster source currently reaches `interruptLatch`, so enabling `$D01A.1` or
`$D01A.2` can never assert IRQ.

Required behavior:

- First sprite-data collision sets `$D019` bit 1 (IMDC).
- First sprite-sprite collision sets `$D019` bit 2 (IMMC).
- The matching `$D01A` mask bit participates in the VIC IRQ output.
- Reading `$D01E/$D01F` clears the collision-value register only. It must not
  acknowledge the corresponding `$D019` interrupt source.
- Writing a one to `$D019` bit 1/2 acknowledges that source independently of
  the collision-value register and other pending VIC sources.
- The existing documented limitation for collisions outside the modeled
  display window remains separate; this item fixes IRQ behavior for collisions
  that the current renderer already detects.

Acceptance gates:

- Separate sprite-data and sprite-sprite tests cover latch, mask, IRQ, read
  clearing, and write-one-to-clear behavior.
- A combined-source test proves that acknowledging one VIC source preserves
  the others.

Reference: [Commodore 64 Programmer's Reference Guide — `$D019/$D01A`](https://www.devili.iki.fi/Computers/Commodore/C64/Programmers_Reference/Chapter_3/page_151.html)

### [ ] C64-F03 — Implement SID `$D418.7` (`3 OFF`) semantics (P1)

The mixer currently uses only the low volume nibble of `$D418`; bit 7 is
ignored, and a test freezes `$0F` and `$FF` as producing identical audio.
Hardware disconnects voice 3 from the direct audio path when `3 OFF` is one.

Required behavior:

- With `3 OFF=1` and `FILT 3=0`, voice 3 contributes nothing to the direct mix.
- Voices 1 and 2 remain unchanged.
- Preserve enough routing state for the future filter implementation: when
  voice 3 is routed through an implemented filter, `3 OFF` must suppress only
  its direct path.
- Replace the existing equality test with positive mute/routing tests and
  deliberately rebaseline affected SID PCM CRCs.

Reference: [MOS 6581 SID datasheet](https://www.cpcwiki.eu/imgs/9/9d/Mos_6581_sid.pdf)

### [ ] C64-F04 — Reset must not erase main RAM or color RAM (P1)

`C64Board.reset()` calls `C64Memory.reset()`, which currently fills all 64K of
RAM and color RAM with zero. A hardware reset resets chips and CPU state but
does not clear the installed memory. This makes warm-reset behavior incorrect
and destroys RAM-resident state, cartridge signatures, and bytes under ROM.

Required behavior:

- Separate deterministic power-on initialization from hardware reset.
- Preserve main RAM and color RAM across `C64Machine.reset()`.
- Continue resetting the 6510 port, CIA, VIC-II, SID, cartridge registers,
  keyboard state, clocks, and interrupt lines as appropriate.
- If tests require a deterministic initial RAM image, make that an explicit
  construction/power-on policy rather than reset semantics.

Acceptance gates:

- Seed representative low RAM, screen RAM, RAM under BASIC/KERNAL/I/O, and
  color RAM; reset; prove every byte remains intact while device state resets.
- Verify that cold construction still boots the real KERNAL deterministically.

## Required additions missing from the current plan

### [ ] C64-A01 — Make SID chip state advance independently of PCM sampling (P1)

Oscillators, noise, and envelopes currently advance in 22/23-cycle batches
inside `nextPcmSample()`. Consequently OSC3/ENV3 reads are quantized to the
44.1 kHz output cadence, and a register write can retroactively affect chip
cycles accumulated before the write.

Required direction:

- Advance SID digital state from elapsed chip cycles, not from audio-buffer
  demand.
- Make register reads observe state at the current emulated SID cycle.
- Split elapsed intervals at register writes so old and new register values
  apply to the correct portions of time.
- Keep PCM resampling as a consumer of already-advanced SID state.

Acceptance gates:

- OSC3 and ENV3 change at chip-cycle resolution in deterministic tests.
- A write-boundary test proves that a control/frequency write does not affect
  cycles preceding it.
- Audio remains deterministic; any PCM CRC changes are explicitly recorded.

### [ ] C64-A02 — Require a phase-aware MOS 6502 bus timeline (P1)

The existing plan mentions badline clock skew and a possible RDY hook, but a
board-level extra-cycle approximation is not sufficient as the final accuracy
contract. CPU reads, writes, dummy accesses, IRQ sampling, and device events
must have positions inside the instruction.

Required direction:

- Add phase-aware MOS 6502 bus events, including documented dummy reads and
  writes.
- Model RDY/BA/AEC stalls without completing CPU memory operations early.
- Let VIC badlines and sprite DMA steal their actual CPU bus slots.
- Define instruction-boundary IRQ/NMI sampling rules, including CLI/SEI/PLP/RTI
  delays and relevant NMI/IRQ races.
- Render/apply VIC events at their actual cycle rather than attributing all
  writes to the final state of an instruction.

This strengthens the existing Phase 6e-b item; it must not be closed by clock
skew alone.

### [ ] C64-A03 — Complete the CIA 6526 contract beyond KERNAL timers (P1)

The current CIA is sufficient for KERNAL boot and jiffy IRQs, but several
hardware-visible functions are frozen as stubs without an actionable backlog.

Add explicit tasks and gates for:

- FLAG interrupt input;
- serial shift register with CNT/SP behavior and interrupt source;
- Timer B input mode `11` rather than treating it as mode `10`;
- PB6/PB7 timer output modes;
- CRA 50/60 Hz TOD selection and external TOD cadence;
- interrupt-source interactions with ICR masking/read-clear behavior.

TOD alarm itself is already in `commodore-64-plan.md` and is not duplicated by
this item.

### [ ] C64-A04 — Add a single repeatable C64 verification wall (P1)

The current tests are strong, but ordinary `test`/`build` excludes Klaus and
does not execute any C64 `JavaExec` smoke. `c64ReadySmoke` also records a frame
but does not assert the documented `0x8C23AF62` baseline.

Required direction:

- Add an aggregate local task such as `c64Verification` covering MOS 6502,
  machine, platform, desktop, Klaus, READY, BASIC, PRG, CRT, joystick, and the
  real-game smoke when its user-provided asset is available.
- Wire all deterministic, redistributable gates into `check` or CI.
- Make missing proprietary/user media explicit rather than silently treating
  a skipped external gate as full verification.
- Add `--expect-frame-crc=8C23AF62` to `c64ReadySmoke` and keep its visible-text
  assertion.
- Print a concise aggregate result so phase completion cannot omit a gate by
  accident.

### [ ] C64-A05 — Complete the host keyboard surface (P2)

The matrix device can represent arbitrary keys, but the desktop mapping omits
important C64 controls such as function keys, Commodore, CTRL, and RUN/STOP.

Required scope:

- Map F1/F3/F5/F7 and their shifted F2/F4/F6/F8 forms.
- Map Commodore, CTRL, RUN/STOP, both shifts where host APIs distinguish them,
  and retain the existing RESTORE mapping.
- Document host shortcuts and joystick-mode conflicts.
- Add controller-to-matrix tests for every non-character control.

### [ ] C64-A06 — Support baseline non-EasyFlash cartridges (P2)

The PLA already models 8K, 16K, and Ultimax line combinations, but the `.crt`
loader rejects every hardware type except EasyFlash type 32. “CRT support” is
therefore narrower than the bus implementation suggests.

Required baseline:

- Generic normal 8K cartridge;
- generic normal 16K cartridge;
- generic Ultimax cartridge;
- header EXROM/GAME line handling and reset-vector boot gates for each.

Ocean, Action Replay, freezer cartridges, writable flash/EAPI, and other
banking hardware can remain later device-specific phases, but should be named
explicitly rather than implied by a generic `.crt` label.

### [ ] C64-A07 — Add the `$D418` volume-DAC/digi path and SID model policy (P2)

The current output is only the three digital waveforms multiplied by the low
volume nibble. Changing `$D418` with silent voices therefore produces no
audio, so classic volume-register samples/digis are absent. This is independent
of the already-planned multimode filter.

Required direction:

- Model the audible DAC/bias step caused by `$D418` volume changes.
- Define whether the first implementation targets 6581, 8580, or an explicit
  selectable model; do not silently combine incompatible analogue behavior.
- Keep the implementation deterministic and DC-blocked while retaining the
  transient needed for volume-register playback.
- Add a small register-driven digi PCM gate and record its baseline.

### [ ] C64-A08 — Replace fixed I/O values with an explicit data-bus model (P2)

Several currently frozen conventions substitute constants for hardware bus
state: color RAM upper nibbles are zero, unmapped/Ultimax reads are `$FF`, SID
write-only reads are zero, and the 6510-port ghost byte is absent. These are
documented deviations but have no actionable convergence task.

Required direction:

- Introduce a machine-level last/floating data-bus policy with documented
  decay or a deliberately staged approximation.
- Source color RAM upper nibbles from the appropriate bus value.
- Route unmapped and write-only I/O reads through that policy.
- Define CPU and VIC ownership separately where their visible bus values
  differ, including Ultimax and `$0000/$0001` cases.
- Add focused compatibility tests before replacing the existing constants;
  do not rebaseline unrelated frame gates without an explained state change.

## Already tracked elsewhere — do not duplicate

The following gaps remain mandatory for broader compatibility but are already
present in `commodore-64-plan.md`:

- VIC-II badlines/cycle stealing and border tricks;
- CIA TOD alarm;
- datasette/TAP and host/D64 loading, with true 1541 later;
- SID filter;
- remaining NMOS decimal/unstable-opcode deviations;
- sprite/collision behavior outside borders and other frozen VIC deviations.

## Project hygiene when this queue is touched

- Update the stale Phase 6 summary checkbox and obsolete earlier-plan claims
  after folding completed items back into `commodore-64-plan.md`.
- Mark `c64-audit-report.md` clearly as a historical foundation audit; it still
  describes NMI, Klaus, and undocumented opcodes as absent.
- Add C64 launch, probe, smoke, and verification commands to `CLAUDE.md`.
