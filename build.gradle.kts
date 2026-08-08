import org.gradle.api.tasks.bundling.Zip

plugins {
    java
}

group = "dev.stamchuk"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all"))
    }

    jar {
        archiveClassifier.set("")
    }

    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to version)
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    register<Zip>("resourcePackZip") {
        archiveBaseName.set("Cigarette-resourcepack")
        archiveVersion.set(project.version.toString())
        destinationDirectory.set(layout.buildDirectory.dir("resourcepack"))
        from(layout.projectDirectory.dir("resourcepack/Cigarette-resourcepack"))
    }

    build {
        dependsOn("resourcePackZip")
    }
}
