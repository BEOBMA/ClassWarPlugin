import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "2.4.20-RC"
    id("com.gradleup.shadow") version "8.3.11"
}

group = "org.beobma"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

val targetJavaVersion = 25
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.shadowJar {
    relocate("kotlin", "org.beobma.classWarPlugin.libs.kotlin")
}

val verifyShadowJarContents = tasks.register("verifyShadowJarContents") {
    dependsOn("shadowJar")

    doLast {
        val shadowArchive = tasks.named<org.gradle.api.tasks.bundling.Jar>("shadowJar")
            .get()
            .archiveFile
            .get()
            .asFile
        val requiredEntries = listOf(
            "org/beobma/classWarPlugin/gameClass/list/TimeManiqulator.class",
            "org/beobma/classWarPlugin/status/list/CheckpointStatus.class",
        )

        ZipFile(shadowArchive).use { archive ->
            val missingEntries = requiredEntries.filter { archive.getEntry(it) == null }
            check(missingEntries.isEmpty()) {
                "Shadow JAR is missing required runtime classes: ${missingEntries.joinToString()}"
            }
        }
    }
}

tasks.build {
    dependsOn(verifyShadowJarContents)
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
