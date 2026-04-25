rootProject.name = "z8-emu"

include(
    "emu-platform",
    "cpu-i8080",
    "cpu-mos6502",
    "cpu-z80",
    "chip-ay",
    "machine-spectrum",
    "machine-radio86rk",
    "machine-cpc",
    "machine-apple2",
    "app-desktop",
)

project(":app-desktop").projectDir = file("apps/desktop")
project(":emu-platform").projectDir = file("platform/core")
project(":cpu-i8080").projectDir = file("cpu/i8080")
project(":cpu-mos6502").projectDir = file("cpu/mos6502")
project(":cpu-z80").projectDir = file("cpu/z80")
project(":chip-ay").projectDir = file("chips/ay")
project(":machine-spectrum").projectDir = file("machines/spectrum")
project(":machine-radio86rk").projectDir = file("machines/radio86rk")
project(":machine-cpc").projectDir = file("machines/cpc")
project(":machine-apple2").projectDir = file("machines/apple2")
