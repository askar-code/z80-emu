plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":emu-platform"))
    api(project(":cpu-mos6502"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
