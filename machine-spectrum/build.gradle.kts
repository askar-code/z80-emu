plugins {
    `java-library`
}

dependencies {
    api(project(":emu-platform"))
    api(project(":cpu-z80"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
