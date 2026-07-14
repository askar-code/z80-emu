export const meta = {
  name: 'simplify-tranche',
  description: 'One tranche of a whole-codebase quality review: area finders piped straight into verification, budget-guarded',
  phases: [
    { title: 'Find', detail: 'quality-angle finders for the requested areas' },
    { title: 'Verify', detail: 'per-finder adversarial verification' },
  ],
}

const FINDINGS_SCHEMA = {
  type: 'object', additionalProperties: false, required: ['findings'],
  properties: {
    findings: {
      type: 'array', maxItems: 8,
      items: {
        type: 'object', additionalProperties: false,
        required: ['file', 'line', 'summary', 'cost', 'fix', 'confidence'],
        properties: {
          file: { type: 'string' },
          line: { type: 'integer' },
          summary: { type: 'string' },
          cost: { type: 'string' },
          fix: { type: 'string' },
          confidence: { type: 'string', enum: ['high', 'medium'] },
        },
      },
    },
  },
}

const VERDICTS_SCHEMA = {
  type: 'object', additionalProperties: false, required: ['verdicts'],
  properties: {
    verdicts: {
      type: 'array',
      items: {
        type: 'object', additionalProperties: false,
        required: ['id', 'verdict', 'safety', 'reason', 'fix'],
        properties: {
          id: { type: 'integer' },
          verdict: { type: 'string', enum: ['CONFIRMED', 'REJECTED'] },
          safety: { type: 'string', enum: ['safe', 'risky'] },
          reason: { type: 'string' },
          fix: { type: 'string' },
        },
      },
    },
  },
}

const DELIBERATE = `Deliberate project decisions — do NOT flag these (they are known and intentional):
- Hot-path code deliberately avoids allocations, boxing, streams, lambdas, reflection, and string logging (docs/architecture.md). Never suggest streams/Optional/lambda rewrites, per-access objects, or "extract a small value class" in CPU/bus/memory/video/audio execution paths.
- The giant switch-based instruction decode in Z80Cpu / I8080Cpu / Mos6502Cpu is intentional and table-like. Do not flag its sheer size or per-opcode repetitiveness.
- IoAddressSpace uses a linear ordered mapping-entry list BY DESIGN; compiled 64K table dispatch is a documented later optimization (docs/architecture.md). Do not propose it.
- Machine modules (machines/*) are intentionally independent of each other: similar board/bus/device wiring across machines is acceptable. Only flag cross-machine duplication when a shared platform-level home clearly exists AND semantics are identical.
- CPU cores must not know about machines. Never suggest moving machine timing, contention, or wait-state policy into cpu/*.
- Probe launchers and scanners in apps/desktop (*ProbeLauncher, *Scanner) are operational bring-up tools: verbose console output and long linear main() flows are acceptable there. Flag only clear duplication or dead code.
- Apple II DHGR/NTSC chroma constants were recently hand-tuned (docs/apple-ii-dhgr-ntsc-tuning.md); treat magic numbers in that code as deliberate.`

const ANGLES = {
  reuse: `REUSE: flag code that re-implements something the codebase already has (helpers, components, utilities, query/format/validation logic). Grep shared/utility modules and sibling modules; NAME the existing thing to call instead, and verify it really exists and has the same semantics.`,
  simplification: `SIMPLIFICATION: flag unnecessary complexity — redundant or derivable state, copy-paste with slight variation, deep nesting, dead code, unused exports/imports/params, single-caller abstractions that obscure, functions where a const would do. Name the simpler form that does the same job.`,
  efficiency: `EFFICIENCY: flag wasted work — redundant computation or repeated I/O, work re-done per frame/per access that could be done once, allocations inside emulation hot loops, blocking work on startup, long-lived objects built from closures capturing large scopes. Name the cheaper alternative. Do NOT manufacture micro-optimizations.`,
  altitude: `ALTITUDE: check code is implemented at the right depth, not as fragile bandaids. Special cases layered on shared infrastructure signal the mechanism should be generalized instead. Flag per-call-site conventions that should be a mechanism, copy-pasted policy that belongs in one place, and configuration hardcoded where a parameter exists. Only flag when a concretely deeper home EXISTS in this codebase and migration is tractable; name it precisely.`,
}

const COMMON = `You are a code-QUALITY review agent for the z8-emu repo at /Users/askar/z8-emu.
Context: a multi-machine 8-bit emulator platform in Java 21 (Gradle multi-module, JUnit 5). Machines: ZX Spectrum 48K/128K, Radio-86RK, Amstrad CPC 6128, Apple II Plus/IIe. Modules: platform/core = shared contracts (Cpu, CpuBus, IoAddressSpace, MachineRuntime, memory, FrameBuffer, PCM audio); cpu/* = CPU cores; chips/ay = AY-3-891x PSG; machines/* = per-machine board wiring, devices, media loaders; apps/desktop = Swing shell + headless probe launchers. Unit tests live per-module under src/test/java; full suite: ./gradlew test (run from repo root). See CLAUDE.md and docs/architecture.md for invariants.
Rules:
- Quality only. Do NOT hunt for correctness bugs.
- Read the actual files (current working tree) before flagging anything. Never flag from pattern-matching alone.
- Report AT MOST 8 findings — only issues clearly worth fixing, each with a concrete maintenance/perf cost. Fewer, better findings beat noise. If the area is clean, return an empty list.
- Fixes must be behavior-preserving. Emulation output (frames, audio, timing, trace) must stay bit-identical. No API-shape changes, no architecture rewrites.
- Skip style nits, naming preferences, and generated files.
- "file" = repo-relative path; "line" = 1-indexed line in the current file.
${DELIBERATE}
Your final output goes through StructuredOutput; return raw findings only.`

const AREAS = {
  'platform': { scope: 'platform/core chips/ay', hint: 'Largest: Ay38912Device.java (257), IoAddressSpace.java (179); everything else <160 lines. ~30 files, ~1.7k lines incl. tests. These are shared contracts — changes ripple into every machine, so prefer flagging dead/unused surface over restructuring.', angles: ['reuse', 'simplification', 'efficiency', 'altitude'] },
  'cpu-z80': { scope: 'cpu/z80', hint: 'Largest: Z80Cpu.java (1949), Z80CpuTest.java (1331), Z80Registers.java (299). ~3.8k lines. Decode switch size is deliberate; look instead for duplicated flag/arithmetic helpers, dead code, unused fields. Test files: reuse+simplification only.', angles: ['reuse', 'simplification', 'efficiency', 'altitude'] },
  'cpu-6502-i8080': { scope: 'cpu/mos6502 cpu/i8080', hint: 'Largest: Mos6502CpuTest.java (1714), Mos6502Cpu.java (1516), I8080Cpu.java (785). ~4.4k lines. Decode switch size is deliberate. Test files: reuse+simplification only.', angles: ['reuse', 'simplification', 'efficiency', 'altitude'] },
  'machine-spectrum': { scope: 'machines/spectrum', hint: 'Largest: SpectrumUlaDevice.java (455), Spectrum48kMachineTest.java (399), TapeDevice.java (295). ~3.9k lines. Contains 48K and 128K boards side by side — duplication BETWEEN spectrum48k and spectrum128k packages IS in scope (same module).', angles: ['reuse', 'simplification', 'efficiency', 'altitude'] },
  'machine-rk': { scope: 'machines/radio86rk', hint: 'Largest: Radio86VideoDevice.java (464), Radio86MonitorBootTest.java. ~2k lines.', angles: ['reuse', 'simplification', 'efficiency', 'altitude'] },
  'machine-cpc': { scope: 'machines/cpc', hint: 'Largest: CpcMachineTest.java (496), CpcGateArrayDevice.java (419), CpcFdcDevice.java (324). ~2.6k lines.', angles: ['reuse', 'simplification', 'efficiency', 'altitude'] },
  'apple2-core': { scope: 'machines/apple2', hint: 'IMPORTANT: review ONLY files NOT under a .../disk/ package — the disk subsystem is a separate area. In scope: board, bus, memory, soft switches, video, device/, and non-disk tests. Largest in scope: Apple2MachineTest.java (871), Apple2VideoDevice.java (484). ~3k lines.', angles: ['reuse', 'simplification', 'efficiency', 'altitude'] },
  'apple2-disk': { scope: 'machines/apple2/src/main/java/dev/z8emu/machine/apple2/disk machines/apple2/src/test/java/dev/z8emu/machine/apple2/disk', hint: 'Largest: Apple2SuperDriveControllerTest.java (556), Apple2SuperDriveController.java (478), Apple2SwimController.java (378), Apple2Disk2Controller.java (300). ~4k lines. Several controllers (Disk2, SuperDrive, SWIM, ProDOS shim) share GCR/media concepts — the reuse angle is promising. Test files: reuse+simplification only.', angles: ['reuse', 'simplification', 'efficiency', 'altitude'] },
  'app-desktop': { scope: 'apps/desktop', hint: 'Largest: Apple2RomProbeLauncher.java (1233), SpectrumTapeProbeLauncher.java (509), SpectrumDesktopRunner.java (436), DesktopMachineDefinitions.java (416). ~5.9k lines. Probe launchers are operational tools (verbosity OK); focus on duplication BETWEEN per-machine runners / keyboard controllers / display panels, and on dead launch options.', angles: ['reuse', 'simplification', 'efficiency', 'altitude'] },
}

// Cross-cutting finders see the WHOLE repo — run them last, after area
// tranches and planned refactors have landed.
const CROSS = {
  'cross-reuse': `${COMMON}

Angle — CROSS-CUTTING ${ANGLES.reuse}

Scope: the ENTIRE repo source. Your job is duplication that per-area reviewers cannot see because the copies live in DIFFERENT modules: the same utility re-implemented in two machine modules; parallel media-loader/GCR/framebuffer logic; helpers duplicated between apps/desktop and a machine module that belong in platform/core. Use wide greps (function names, distinctive string literals, regexes) to find candidates, then read both copies to confirm identical semantics. Report where the single shared home should be. Remember: machine modules staying independent is deliberate — only flag when a platform-level home clearly fits.`,
  'cross-altitude': `${COMMON}

Angle — CROSS-CUTTING ${ANGLES.altitude}

Scope: the ENTIRE repo source. Look for system-level altitude problems per-area reviewers cannot see: a policy enforced by convention at N call sites across modules when one mechanism could own it (frame presentation, keyboard controller wiring, media-type detection, probe option parsing, trace sinks); special-case branches in platform/core serving exactly one machine; the same cross-cutting concern solved differently in different machine modules. Only flag when the deeper home concretely exists (or is one small new class in platform/core) and migration is tractable. Name every call site you expect the fix to touch.`,
}

// ---- build finder list from args.areas ----
// args may arrive as a JSON string — handle both.
const input = typeof args === 'string' ? JSON.parse(args) : args
const requested = (input && input.areas) || []
if (!requested.length) throw new Error('pass args.areas, e.g. {areas:["platform"]}')

const finderDefs = []
for (const key of requested) {
  if (CROSS[key]) {
    finderDefs.push({ label: key, prompt: CROSS[key] })
    continue
  }
  const area = AREAS[key]
  if (!area) throw new Error(`unknown area: ${key}`)
  for (const angle of area.angles) {
    finderDefs.push({
      label: `${key}:${angle}`,
      prompt: `${COMMON}

Angle — ${ANGLES[angle]}

Scope (review ONLY files under): ${area.scope}
${area.hint}
List the scope with: git ls-files ${area.scope}
You may Grep/Read OUTSIDE the scope to check whether a helper already exists elsewhere or how a symbol is used — but findings must point INSIDE the scope.`,
    })
  }
}

const FIND_FLOOR = 60_000    // don't start a finder with less than this left
const VERIFY_FLOOR = 25_000  // don't start a verifier with less than this left

let nextId = 1
const skippedFinders = []

phase('Find')
log(`Tranche areas: ${requested.join(', ')} -> ${finderDefs.length} finders; budget ${budget.total ? Math.round(budget.total / 1000) + 'k' : 'unbounded'}`)

const results = await pipeline(
  finderDefs,
  async (def) => {
    if (budget.total && budget.remaining() < FIND_FLOOR) {
      skippedFinders.push(def.label)
      log(`SKIP ${def.label}: only ${Math.round(budget.remaining() / 1000)}k budget left`)
      return null
    }
    try {
      return await agent(def.prompt, { label: def.label, phase: 'Find', schema: FINDINGS_SCHEMA, effort: 'high' })
    } catch (e) {
      skippedFinders.push(def.label)
      log(`FAIL ${def.label}: ${String(e).slice(0, 120)}`)
      return null
    }
  },
  async (found, def) => {
    if (!found || !found.findings.length) return { label: def.label, confirmed: [], unverified: [], rejected: 0 }
    const batch = found.findings.map(f => ({ ...f, id: nextId++, angle: def.label }))
    log(`${def.label}: ${batch.length} findings, verifying`)
    const fallback = () => ({
      label: def.label, confirmed: [], rejected: 0,
      unverified: batch.map(f => ({ ...f, safety: 'risky' })),
    })
    if (budget.total && budget.remaining() < VERIFY_FLOOR) {
      log(`SKIP verify for ${def.label}: budget low — findings returned unverified`)
      return fallback()
    }
    let verdicts
    try {
      const res = await agent(`You are the adversarial VERIFICATION stage of a code-quality review of the repo at /Users/askar/z8-emu. Default to skepticism: a finding survives only if you can see it in the code yourself.

For EACH finding below: Read the file at the stated location (current working tree), plus any "existing helper" the fix names. Then output a verdict:
- CONFIRMED only if ALL hold: the issue is real at that location; the fix is concrete, behavior-preserving, and makes the code genuinely better (not a lateral move); any named existing helper actually exists with compatible semantics; it is NOT one of the deliberate decisions below.
- REJECTED otherwise: false or stale claims, taste-only rewrites, product/UX/copy changes, architecture rewrites, micro-noise, or duplicates within this batch (confirm the better-stated one, reject the rest with reason "duplicate of <id>").

safety: "safe" = mechanical change with low blast radius, caught by compile or existing module tests if wrong. "risky" = touches CPU instruction semantics/flags/timing tables, t-state or contention timing logic, disk encoding (GCR/nibblizer/WOZ/FDC/SWIM), video signal generation (ULA, CRTC, Apple II video/NTSC), audio sample paths, or media/snapshot file parsing.

${DELIBERATE}

Refine the "fix" of CONFIRMED findings into a precise, implementable instruction naming exact files/symbols. Return one verdict per finding id.

FINDINGS (JSON):
${JSON.stringify(batch.map(({ id, angle, file, line, summary, cost, fix, confidence }) => ({ id, angle, file, line, summary, cost, fix, confidence })), null, 1)}`,
        { label: `verify:${def.label}`, phase: 'Verify', schema: VERDICTS_SCHEMA, effort: 'medium' })
      if (!res) return fallback()
      verdicts = new Map(res.verdicts.map(v => [v.id, v]))
    } catch (e) {
      log(`verify ${def.label} failed (${String(e).slice(0, 80)}) — findings returned unverified`)
      return fallback()
    }
    const out = { label: def.label, confirmed: [], unverified: [], rejected: 0 }
    for (const f of batch) {
      const v = verdicts.get(f.id)
      if (!v) { out.unverified.push({ ...f, safety: 'risky' }); continue }
      if (v.verdict === 'CONFIRMED') {
        out.confirmed.push({ id: f.id, angle: f.angle, file: f.file, line: f.line, summary: f.summary, cost: f.cost, fix: v.fix, safety: v.safety, confidence: f.confidence })
      } else {
        out.rejected++
      }
    }
    return out
  }
)

const done = results.filter(Boolean)
const confirmed = done.flatMap(r => r.confirmed)
const unverified = done.flatMap(r => r.unverified || [])
const rejected = done.reduce((n, r) => n + r.rejected, 0)
log(`Tranche done: ${confirmed.length} confirmed, ${rejected} rejected, ${unverified.length} unverified, ${skippedFinders.length} finders skipped; spent ~${Math.round(budget.spent() / 1000)}k`)

return {
  areas: requested,
  confirmed,
  unverified,
  counts: { confirmed: confirmed.length, rejected, unverified: unverified.length },
  skippedFinders,
  spentTokens: budget.spent(),
}
