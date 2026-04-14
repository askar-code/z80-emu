plugins {
    application
}

dependencies {
    implementation(project(":machine-spectrum"))
}

application {
    mainClass = "dev.z8emu.app.desktop.DesktopLauncher"
}

tasks.named<JavaExec>("run") {
    providers.systemProperty("z8emu.tapeTurboFrames").orNull?.let {
        systemProperty("z8emu.tapeTurboFrames", it)
    }
}
