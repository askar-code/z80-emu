plugins {
    application
}

dependencies {
    implementation(project(":machine-spectrum48k"))
}

application {
    mainClass = "dev.z8emu.app.desktop.DesktopLauncher"
}

