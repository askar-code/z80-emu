plugins {
    application
}

dependencies {
    implementation(project(":machine-apple2"))
    implementation(project(":machine-cpc"))
    implementation(project(":machine-radio86rk"))
    implementation(project(":machine-spectrum"))
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
        providers.gradleProperty("apple2.rom").orElse("apple2plus-12k.rom").get(),
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
    val superDriveRom = providers.gradleProperty("apple2.superdrive35.rom").orElse("341-0438-A.bin").get()
    val systemDisk = providers.gradleProperty("apple2.superdrive35.systemDisk")
        .orElse("build/apple2-superdrive/apple2e-iic-iicplus-system-disk.po")
        .get()
    args(
        "--machine=apple2e",
        ".",
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
    val superDriveRom = providers.gradleProperty("apple2.superdrive35.rom").orElse("341-0438-A.bin").get()
    val popDisk = providers.gradleProperty("apple2.superdrive35.popDisk")
        .orElse("Prince of Persia (Cracked 3.5 floppy for IIc+).po")
        .get()
    args(
        "--machine=apple2e",
        ".",
        "20000000",
        "--superdrive35-rom=$superDriveRom",
        "--superdrive35-media=$popDisk",
        "--superdrive35-slot=5",
        "--superdrive35-warmup-tstates=2000000",
        "--expect-frame-crc=F386BEDD"
    )
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.") || it.startsWith("apple2.") }
        .associateWith { System.getProperty(it) })
}
