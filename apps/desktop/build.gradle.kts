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
