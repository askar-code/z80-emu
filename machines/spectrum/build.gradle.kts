import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
}

dependencies {
    api(project(":chip-ay"))
    api(project(":emu-platform"))
    api(project(":cpu-z80"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("external-media")
    }
}

val externalMediaTest by tasks.registering(Test::class) {
    description = "Runs Spectrum regressions that require local, gitignored ROM and game media."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    workingDir = rootProject.projectDir
    useJUnitPlatform {
        includeTags("external-media")
    }
    testLogging {
        events("failed", "skipped")
        showStandardStreams = true
    }
}
