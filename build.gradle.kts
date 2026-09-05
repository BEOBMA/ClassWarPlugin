import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "2.4.20-RC"
    id("com.gradleup.shadow") version "8.3.11"
}

group = "org.beobma"
version = "1.0.4.1"

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
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(kotlin("test"))
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}

val targetJavaVersion = 25
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.shadowJar {
    relocate("kotlin", "org.beobma.classWarPlugin.libs.kotlin")
    relocate("com.google.gson", "org.beobma.classWarPlugin.libs.gson")
}

tasks.test {
    useJUnitPlatform()
}

val verifyShadowJarContents = tasks.register("verifyShadowJarContents") {
    dependsOn("shadowJar")

    doLast {
        val shadowArchive = tasks.named<org.gradle.api.tasks.bundling.Jar>("shadowJar")
            .get()
            .archiveFile
            .get()
            .asFile
        val requiredRuntimeEntries = listOf(
            "org/beobma/classWarPlugin/libs/kotlin/jvm/internal/Intrinsics.class",
            "org/beobma/classWarPlugin/libs/gson/Gson.class",
            "META-INF/LICENSE",
        )

        ZipFile(shadowArchive).use { archive ->
            val compiledClassEntries = listOf(
                layout.buildDirectory.dir("classes/kotlin/main").get().asFile,
                layout.buildDirectory.dir("classes/java/main").get().asFile,
            ).filter { it.isDirectory }
                .flatMap { root ->
                    root.walkTopDown()
                        .filter { it.isFile && it.extension == "class" }
                        .map { it.relativeTo(root).path.replace('\\', '/') }
                        .toList()
                }
            val missingEntries = (requiredRuntimeEntries + compiledClassEntries).distinct()
                .filter { archive.getEntry(it) == null }
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
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
