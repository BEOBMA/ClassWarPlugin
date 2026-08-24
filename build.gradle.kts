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
            "org/beobma/classWarPlugin/gameClass/list/GunBlader\$BulletStatus.class",
            "org/beobma/classWarPlugin/status/list/CheckpointStatus.class",
            "org/beobma/classWarPlugin/status/list/GunBulletStatus.class",
            "org/beobma/classWarPlugin/status/list/GamblerCardStatus.class",
            "org/beobma/classWarPlugin/status/list/TimePhaseStatus.class",
            "org/beobma/classWarPlugin/status/list/SniperAmmoStatus.class",
            "org/beobma/classWarPlugin/status/list/SpiderWebChargeStatus.class",
            "org/beobma/classWarPlugin/status/list/MathAnswerStackStatus.class",
            "org/beobma/classWarPlugin/gameClass/list/BackRoom.class",
            "org/beobma/classWarPlugin/gameClass/list/Chameleon.class",
            "org/beobma/classWarPlugin/gameClass/list/Dwarf.class",
            "org/beobma/classWarPlugin/gameClass/list/HideAndSeek.class",
            "org/beobma/classWarPlugin/gameClass/list/JustLight.class",
            "org/beobma/classWarPlugin/gameClass/list/LuckyOne.class",
            "org/beobma/classWarPlugin/gameClass/list/PatAndMatt.class",
            "org/beobma/classWarPlugin/gameClass/list/Peanuts.class",
            "org/beobma/classWarPlugin/gameClass/list/RainbowBridge.class",
            "org/beobma/classWarPlugin/gameClass/list/Reverse.class",
            "org/beobma/classWarPlugin/gameClass/list/Sagittarius.class",
            "org/beobma/classWarPlugin/gameClass/list/ShyPerson.class",
            "org/beobma/classWarPlugin/gameClass/list/Terrorist.class",
            "org/beobma/classWarPlugin/gameClass/list/ThunderclapFlash.class",
            "org/beobma/classWarPlugin/gameClass/list/Train.class",
            "org/beobma/classWarPlugin/gameClass/list/TrainCarriage.class",
            "org/beobma/classWarPlugin/gameClass/list/WoundsWind.class",
            "org/beobma/classWarPlugin/gameClass/list/Blacksmith.class",
            "org/beobma/classWarPlugin/gameClass/list/Brave.class",
            "org/beobma/classWarPlugin/gameClass/list/BurningPainStatus.class",
            "org/beobma/classWarPlugin/status/list/AttackSpeedDecrease.class",
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
