plugins {
    `java-library`
}

import org.gradle.api.tasks.testing.Test

dependencies {
    api(project(":emu-platform"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("zex")
    }
}

val zexTest by tasks.registering(Test::class) {
    description = "Runs long-running zexdoc/zexall CPU reference tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("zex")
    }
}
