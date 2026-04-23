plugins {
    application
}

dependencies {
    implementation(project(":machine-radio86rk"))
    implementation(project(":machine-spectrum"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

application {
    mainClass = "dev.z8emu.app.desktop.DesktopLauncher"
}

tasks.named<JavaExec>("run") {
    providers.systemProperty("z8emu.tapeTurboFrames").orNull?.let {
        systemProperty("z8emu.tapeTurboFrames", it)
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
