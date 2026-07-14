plugins {
    base
}

group = "com.cortezromeo.taixiu"
version = "3.0.0"

subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        "compileOnly"(rootProject.libs.jetbrains.annotations)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencyLocking {
        lockAllConfigurations()
    }
}

tasks.named("build") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}
