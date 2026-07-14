plugins {
    `java-library`
}

import org.gradle.api.tasks.testing.Test

dependencies {
    api(project(":emu-platform"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("klaus")
    }
}

val klausTest by tasks.registering(Test::class) {
    description = "Runs the Klaus Dormann 6502 functional reference test."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    workingDir = rootProject.projectDir
    systemProperties(System.getProperties().stringPropertyNames()
        .filter { it.startsWith("z8emu.klaus.") }
        .associateWith { System.getProperty(it) })
    testLogging {
        showStandardStreams = true
    }
    useJUnitPlatform {
        includeTags("klaus")
    }
}
