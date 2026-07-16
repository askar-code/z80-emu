import java.io.ByteArrayOutputStream

plugins {
    application
}

dependencies {
    implementation(project(":machine-apple2"))
    implementation(project(":machine-c64"))
    implementation(project(":machine-cpc"))
    implementation(project(":machine-radio86rk"))
    implementation(project(":machine-spectrum"))
    testImplementation(testFixtures(project(":machine-apple2")))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

application {
    mainClass = "dev.z8emu.app.desktop.DesktopLauncher"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    listOf(
        "z8emu.tapeTurboFrames",
    ).forEach { name ->
        providers.systemProperty(name).orNull?.let { value ->
            systemProperty(name, value)
        }
    }
}

tasks.register<JavaExec>("spectrumTapeProbe") {
    group = "application"
    description = "Runs the headless Spectrum tape probe launcher."
    mainClass.set("dev.z8emu.app.desktop.SpectrumTapeProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") }
        .associateWith { System.getProperty(it) })
}

tasks.register<JavaExec>("apple2RomProbe") {
    group = "application"
    description = "Runs the headless Apple II ROM bring-up probe."
    mainClass.set("dev.z8emu.app.desktop.Apple2RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("apple2.") }
        .associateWith { System.getProperty(it) })
}

tasks.register<JavaExec>("c64RomProbe") {
    group = "application"
    description = "Runs the headless Commodore 64 ROM bring-up probe."
    mainClass.set("dev.z8emu.app.desktop.C64RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("c64.") }
        .associateWith { System.getProperty(it) })
}

tasks.register<JavaExec>("cpcRomProbe") {
    group = "application"
    description = "Runs the headless Amstrad CPC ROM bring-up probe."
    mainClass.set("dev.z8emu.app.desktop.CpcRomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("cpc.") }
        .associateWith { System.getProperty(it) })
}

tasks.register<JavaExec>("cpcBasicSmoke") {
    group = "verification"
    description = "Boots the CPC 6128 firmware to the settled BASIC banner and checks the frame CRC."
    mainClass.set("dev.z8emu.app.desktop.CpcRomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("cpc.") }
        .associateWith { System.getProperty(it) })
    args(
        providers.gradleProperty("cpc.rom").orElse("media/cpc6128.rom").get(),
        "3000000",
        "--expect-frame-crc=2A1A5DBE",
        "--dump-frame=build/cpc/basic.png"
    )
}

tasks.register<JavaExec>("cpcPrinceSmoke") {
    group = "verification"
    description = "Loads Prince of Persia from CPC disk, enters gameplay, and checks the frame CRC."
    mainClass.set("dev.z8emu.app.desktop.CpcRomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("cpc.") }
        .associateWith { System.getProperty(it) })
    args(
        providers.gradleProperty("cpc.rom").orElse("media/cpc6128.rom").get(),
        "30000000",
        "--disk=" + providers.gradleProperty("cpc.prince").orElse("media/prinpere.dsk").get(),
        "--keys=RUN\"PRINCE<CR>",
        "--press-key-after-frames=1500:<SP>",
        "--expect-frame-crc=658B018F",
        "--dump-frame=build/cpc/prince.png"
    )
}

val c64PrgFile = rootProject.layout.projectDirectory.file("build/c64/hello.prg")
tasks.register<JavaExec>("c64PrgSmoke") {
    group = "verification"
    description = "Loads a tokenized BASIC PRG and checks its screen output."
    mainClass.set("dev.z8emu.app.desktop.C64RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("c64.") }
        .associateWith { System.getProperty(it) })
    val targetFile = c64PrgFile.asFile
    doFirst {
        val target = targetFile
        target.parentFile.mkdirs()
        target.writeBytes(byteArrayOf(
            0x01, 0x08, 0x0E, 0x08, 0x0A, 0x00, 0x99.toByte(), 0x22,
            0x48, 0x45, 0x4C, 0x4C, 0x4F, 0x22, 0x00, 0x00, 0x00
        ))
    }
    args(
        providers.gradleProperty("c64.roms").orElse("media").get(),
        "10000000",
        "--prg=build/c64/hello.prg",
        "--expect-screen=HELLO",
        "--dump-frame=build/c64/prg-hello.png"
    )
}

tasks.register<JavaExec>("c64JoySmoke") {
    group = "verification"
    description = "Types a delayed CIA port read and checks joystick input through the C64 probe."
    mainClass.set("dev.z8emu.app.desktop.C64RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("c64.") }
        .associateWith { System.getProperty(it) })
    args(
        providers.gradleProperty("c64.roms").orElse("media").get(),
        "10000000",
        "--type-after-screen=READY.",
        "--keys=POKE<SP>53280,PEEK(56320)AND15:FOR<SP>I=1TO2000:NEXT:POKE<SP>53280,PEEK(56320)AND15<CR>",
        "--joy=R400",
        "--expect-frame-crc=C793420B",
        "--dump-frame=build/c64/joy-smoke.png"
    )
}

tasks.register<JavaExec>("c64BoulderDashSmoke") {
    group = "verification"
    description = "Autostarts Boulder Dash, presses fire on port 1, moves right, and checks the cave frame."
    mainClass.set("dev.z8emu.app.desktop.C64RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("c64.") }
        .associateWith { System.getProperty(it) })
    args(
        providers.gradleProperty("c64.roms").orElse("media").get(),
        "20000000",
        "--prg=" + providers.gradleProperty("c64.boulderdash").orElse("media/boulderdash.prg").get(),
        "--joy=.300,F4,.200,R30,.60",
        "--joy-port=1",
        "--expect-frame-crc=B96BF819",
        "--dump-frame=build/c64/boulderdash-smoke.png"
    )
}

val c64CrtFile = rootProject.layout.projectDirectory.file("build/c64/easyflash-smoke.crt")
tasks.register<JavaExec>("c64CrtSmoke") {
    group = "verification"
    description = "Boots a synthetic EasyFlash cartridge and checks its screen output."
    mainClass.set("dev.z8emu.app.desktop.C64RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("c64.") }
        .associateWith { System.getProperty(it) })
    val targetFile = c64CrtFile.asFile
    doFirst {
        val target = targetFile
        target.parentFile.mkdirs()
        fun putShort(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value ushr 8).toByte()
            bytes[offset + 1] = value.toByte()
        }
        fun putInt(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = (value ushr 24).toByte()
            bytes[offset + 1] = (value ushr 16).toByte()
            bytes[offset + 2] = (value ushr 8).toByte()
            bytes[offset + 3] = value.toByte()
        }
        fun chip(type: Int, bank: Int, loadAddress: Int, data: ByteArray): ByteArray {
            val packet = ByteArray(0x10 + data.size)
            "CHIP".toByteArray(Charsets.US_ASCII).copyInto(packet)
            putInt(packet, 0x04, packet.size)
            putShort(packet, 0x08, type)
            putShort(packet, 0x0A, bank)
            putShort(packet, 0x0C, loadAddress)
            putShort(packet, 0x0E, data.size)
            data.copyInto(packet, 0x10)
            return packet
        }

        val header = ByteArray(0x40)
        "C64 CARTRIDGE   ".toByteArray(Charsets.US_ASCII).copyInto(header)
        putInt(header, 0x10, 0x40)
        putShort(header, 0x14, 0x0100)
        putShort(header, 0x16, 32)
        header[0x18] = 1
        header.fill(0x20, 0x20, 0x40)
        "EASYFLASH SMOKE".toByteArray(Charsets.US_ASCII).copyInto(header, 0x20)

        val hirom = ByteArray(0x2000) { 0xEA.toByte() }
        val code = ByteArrayOutputStream()
        fun ldaSta(value: Int, address: Int) {
            code.write(0xA9)
            code.write(value)
            code.write(0x8D)
            code.write(address and 0xFF)
            code.write(address ushr 8)
        }
        ldaSta(0x1B, 0xD011)
        ldaSta(0xC8, 0xD016)
        ldaSta(0x14, 0xD018)
        ldaSta(0x0E, 0xD020)
        ldaSta(0x06, 0xD021)
        byteArrayOf(
            0x05, 0x01, 0x13, 0x19, 0x06, 0x0C, 0x01, 0x13, 0x08, 0x20, 0x0F, 0x0B
        ).forEachIndexed { index, screenCode ->
            ldaSta(screenCode.toInt(), 0x0400 + index)
        }
        val loopAddress = 0xE000 + code.size()
        code.write(0x4C)
        code.write(loopAddress and 0xFF)
        code.write(loopAddress ushr 8)
        code.toByteArray().copyInto(hirom)
        hirom[0x1FFC] = 0x00
        hirom[0x1FFD] = 0xE0.toByte()

        val output = ByteArrayOutputStream()
        output.writeBytes(header)
        output.writeBytes(chip(0, 0, 0x8000, ByteArray(0x2000)))
        output.writeBytes(chip(2, 0, 0xE000, hirom))
        target.writeBytes(output.toByteArray())
    }
    args(
        providers.gradleProperty("c64.roms").orElse("media").get(),
        "2000000",
        "--crt=build/c64/easyflash-smoke.crt",
        "--expect-screen=EASYFLASH<SP>OK",
        "--dump-frame=build/c64/crt-smoke.png",
        "--expect-frame-crc=81533B92"
    )
}

tasks.register<JavaExec>("c64ReadySmoke") {
    group = "verification"
    description = "Boots the C64 KERNAL/BASIC ROMs to the READY. prompt."
    mainClass.set("dev.z8emu.app.desktop.C64RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("c64.") }
        .associateWith { System.getProperty(it) })
    args(
        providers.gradleProperty("c64.roms").orElse("media").get(),
        "10000000",
        "--expect-screen=READY.",
        "--dump-frame=build/c64/ready.png"
    )
}

tasks.register<JavaExec>("c64BasicSmoke") {
    group = "verification"
    description = "Types a BASIC expression through the C64 keyboard matrix and checks the result."
    mainClass.set("dev.z8emu.app.desktop.C64RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("c64.") }
        .associateWith { System.getProperty(it) })
    args(
        providers.gradleProperty("c64.roms").orElse("media").get(),
        "10000000",
        "--type-after-screen=READY.",
        "--keys=PRINT<SP>2+2<CR>",
        "--expect-screen=<SP>4",
        "--dump-frame=build/c64/basic-2plus2.png"
    )
}

tasks.register<JavaExec>("apple2ProDosCatalog") {
    group = "application"
    description = "Prints the root catalog of an Apple II ProDOS 800 KB .po image."
    mainClass.set("dev.z8emu.app.desktop.Apple2ProDosCatalogLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("apple2BasicSmoke") {
    group = "verification"
    description = "Runs the Apple II Plus external-ROM BASIC smoke probe."
    mainClass.set("dev.z8emu.app.desktop.Apple2RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    args(
        providers.gradleProperty("apple2.rom").orElse("media/apple2plus-12k.rom").get(),
        "1500000",
        "--keys=PRINT<SP>2+2<CR>",
        "--expect-screen=4"
    )
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("apple2.") }
        .associateWith { System.getProperty(it) })
}

tasks.register<JavaExec>("apple2SuperDriveSystemSmoke") {
    group = "verification"
    description = "Boots an external Apple II 3.5 / SuperDrive system disk image to the ProDOS banner."
    mainClass.set("dev.z8emu.app.desktop.Apple2RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    val superDriveRom = providers.gradleProperty("apple2.superdrive35.rom").orElse("media/341-0438-A.bin").get()
    val systemDisk = providers.gradleProperty("apple2.superdrive35.systemDisk")
        .orElse("build/apple2-superdrive/apple2e-iic-iicplus-system-disk.po")
        .get()
    args(
        "--machine=apple2e",
        "media",
        "12000000",
        "--superdrive35-rom=$superDriveRom",
        "--superdrive35-media=$systemDisk",
        "--superdrive35-slot=5",
        "--superdrive35-warmup-tstates=2000000",
        "--expect-screen=PRODOS<SP>8<SP>V1.5"
    )
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("apple2.") }
        .associateWith { System.getProperty(it) })
}

tasks.register<JavaExec>("apple2SuperDrivePopSmoke") {
    group = "verification"
    description = "Boots the external Prince of Persia 800 KB .po image through the SuperDrive path and checks the hires frame CRC."
    mainClass.set("dev.z8emu.app.desktop.Apple2RomProbeLauncher")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
    val superDriveRom = providers.gradleProperty("apple2.superdrive35.rom").orElse("media/341-0438-A.bin").get()
    val popDisk = providers.gradleProperty("apple2.superdrive35.popDisk")
        .orElse("media/Prince of Persia (Cracked 3.5 floppy for IIc+).po")
        .get()
    args(
        "--machine=apple2e",
        "media",
        "20000000",
        "--superdrive35-rom=$superDriveRom",
        "--superdrive35-media=$popDisk",
        "--superdrive35-slot=5",
        "--superdrive35-warmup-tstates=2000000",
        "--expect-frame-crc=54BCF7D0"
    )
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("apple2.") }
        .associateWith { System.getProperty(it) })
}
