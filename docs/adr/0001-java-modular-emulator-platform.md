# ADR 0001: Java 21 And A Modular Emulator Platform

Status: Accepted

Date: 2026-04-13

## Context

This repository will first target a `ZX Spectrum 48K` emulator and later reuse the same platform to build a `Radio-86RK` emulator.

The architecture must allow us to replace:

- CPU implementation
- bus and timing policy
- memory map
- I/O map
- peripherals and board wiring

The platform must also support timing-sensitive machines. For the Spectrum this includes at least:

- Z80 refresh and interrupt behavior
- ULA contention
- port `0xFE`
- frame and line timing

At the same time, we are not trying to build a transistor-level simulator or optimize for unsafe low-level tricks at the start of the project.

## Decision

We use `Java 21 LTS` as the main implementation language and `Gradle` as the build system.

We adopt a modular emulator architecture with these rules:

1. The emulator is organized as a platform for multiple machines, not as a one-off Spectrum-specific codebase.
2. CPU cores never access RAM or ports directly. All machine-visible activity goes through a bus contract.
3. Time is modeled explicitly with a master `t-state` timeline.
4. Timing side effects such as contention and wait states belong to the machine and bus layer, not to the CPU instruction decoder.
5. Devices are replaceable modules behind stable interfaces.
6. The first implementation target is `ZX Spectrum 48K`. `128K` models and advanced peripherals are postponed.

## Initial Module Plan

Initial Gradle modules:

- `:emu-platform`
- `:cpu-z80`
- `:machine-spectrum48k`
- `:app-desktop`

Planned later:

- `:cpu-i8080`
- `:machine-radio86rk`

Logical modules inside the platform:

- clock and scheduler
- CPU bus contract
- memory and port routing
- board definition
- video, audio, keyboard, tape, ROM, RAM devices
- snapshot and test support

## Consequences

Positive:

- Faster implementation and debugging than a Rust-first approach.
- Strong enough performance for an accurate 8-bit emulator.
- Good IDE support for tracing, tooling, test harnesses, and inspection utilities.
- Clean path to support multiple machines on one shared architecture.

Tradeoffs:

- We must actively avoid allocation-heavy code in hot paths.
- Real-time audio and rendering must be designed to tolerate JVM GC behavior.
- Timing accuracy depends on explicit architecture discipline, not on language choice.

## Guardrails

- No object allocation in per-cycle or per-memory-access hot paths.
- No `Stream` API, boxing, or logging inside the execution hot path.
- CPU timing tables define nominal instruction timing; board logic adds machine-specific delays.
- UI, audio output, and file I/O stay outside the core execution modules.

## Notes

The detailed architecture, board boundaries, and phased implementation plan are documented in `docs/architecture.md`.
