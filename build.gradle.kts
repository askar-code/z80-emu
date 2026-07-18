import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

subprojects {
    group = "dev.z8emu"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release = 21
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

tasks.register("spectrumVerification") {
    group = "verification"
    description = "Runs the currently supported Spectrum verification wall, including zexdoc and optional local-media tests."
    dependsOn(
        ":cpu-z80:test",
        ":cpu-z80:zexDocTest",
        ":chip-ay:test",
        ":machine-spectrum:test",
        ":machine-spectrum:externalMediaTest",
        ":app-desktop:test",
        ":app-desktop:spectrum48RomSmoke",
        ":app-desktop:spectrum128RomSmoke",
        ":app-desktop:spectrum48TapeSmoke",
        ":app-desktop:spectrum128TapeSmoke",
    )
}

tasks.register("spectrumAccuracyVerification") {
    group = "verification"
    description = "Runs the target Spectrum accuracy wall, including strict zexall; it stays red until all plan gaps are fixed."
    dependsOn(
        "spectrumVerification",
        ":cpu-z80:zexAllTest",
    )
}
