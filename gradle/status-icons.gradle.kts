import groovy.json.JsonOutput
import groovy.json.JsonSlurper

val generatedPack = layout.buildDirectory.dir("generated/status-icon-pack")
val generateStatusIconPack = tasks.register("generateStatusIconPack") {
    group = "build"
    description = "Validate icon registrations and generate optional bitmap font/translations."
    inputs.dir("resource-pack")
    inputs.file("src/main/kotlin/org/beobma/classWarPlugin/keyword/Keyword.kt")
    outputs.dir(generatedPack)
    doLast {
        val source = file("resource-pack")
        val output = generatedPack.get().asFile
        val entries = JsonSlurper().parse(source.resolve("icons.json")) as Map<*, *>
        val keywordSource = file("src/main/kotlin/org/beobma/classWarPlugin/keyword/Keyword.kt").readText()
        val keywords = Regex("(?m)^    ([A-Za-z][A-Za-z0-9]*)\\(").findAll(keywordSource)
            .map { it.groupValues[1] }.toSet()
        require(entries.size <= 6400) { "Too many icon registrations" }
        val providers = mutableListOf<Map<String, Any>>(
            mapOf("type" to "space", "advances" to mapOf(" " to 4))
        )
        val translations = linkedMapOf<String, String>()
        entries.entries.sortedBy { it.key.toString() }.forEachIndexed { index, (name, raw) ->
            // UI-only composite glyphs are deliberately not gameplay keywords.
            require(name is String && (name in keywords || name == "ResonanceMark")) { "Unknown Keyword: $name" }
            val spec = raw as? Map<*, *> ?: error("Invalid icon: $name")
            val texture = spec["texture"] as? String ?: error("Missing texture: $name")
            require(texture.matches(Regex("[a-z0-9_]+:[a-z0-9_/.-]+\\.png")) && ".." !in texture) {
                "Invalid texture path: $texture"
            }
            val height = (spec["height"] as? Number)?.toInt() ?: 9
            val ascent = (spec["ascent"] as? Number)?.toInt() ?: 8
            require(height in 1..32 && ascent in 0..height) { "Invalid height/ascent: $name" }
            val namespace = texture.substringBefore(':')
            if (namespace != "minecraft") {
                val png = source.resolve("assets/$namespace/textures/${texture.substringAfter(':')}")
                require(png.isFile && javax.imageio.ImageIO.read(png) != null) { "Missing/invalid PNG: $png" }
            }
            val glyph = (0xE000 + index).toChar().toString()
            providers.add(mapOf("type" to "bitmap", "file" to texture, "height" to height,
                "ascent" to ascent, "chars" to listOf(glyph)))
            translations["classwar.icon.${name.lowercase(java.util.Locale.ROOT)}"] =
                if (name == "ResonanceMark") glyph else "$glyph "
        }
        val activeTextures = entries.values.map { raw ->
            val texture = (raw as Map<*, *>)["texture"] as String
            "assets/${texture.substringBefore(':')}/textures/${texture.substringAfter(':')}"
        }.toSet()
        project.sync {
            from(source) {
                include("pack.mcmeta", "assets/**")
                exclude("**/.gitkeep")
                // Preserve drafts in source control without shipping unused full-resolution artwork.
                exclude { element ->
                    !element.isDirectory && element.path.startsWith("assets/classwar/textures/status/") &&
                        element.path.endsWith(".png") && element.path !in activeTextures
                }
            }
            into(output)
        }
        // Keep authored RGBA originals, but ship tiny font textures rather than megapixel glyphs.
        entries.values.forEach { raw ->
            val texture = (raw as Map<*, *>)["texture"] as String
            if (!texture.startsWith("minecraft:")) {
                val png = output.resolve("assets/${texture.substringBefore(':')}/textures/${texture.substringAfter(':')}")
                val original = javax.imageio.ImageIO.read(png)
                if (original.width > 32 || original.height > 32) {
                    val ratio = 32.0 / maxOf(original.width, original.height)
                    val small = java.awt.image.BufferedImage(
                        (original.width * ratio).toInt().coerceAtLeast(1),
                        (original.height * ratio).toInt().coerceAtLeast(1),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB,
                    )
                    val graphics = small.createGraphics()
                    try {
                        graphics.composite = java.awt.AlphaComposite.Src
                        graphics.drawImage(original.getScaledInstance(small.width, small.height, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
                    } finally { graphics.dispose() }
                    javax.imageio.ImageIO.write(small, "png", png)
                }
            }
        }
        fun json(path: String, value: Any) {
            output.resolve(path).apply { parentFile.mkdirs(); writeText(JsonOutput.prettyPrint(JsonOutput.toJson(value)), Charsets.UTF_8) }
        }
        json("assets/classwar/font/status_icons.json", mapOf("providers" to providers))
        // en_us is the client fallback language; ko_kr is included explicitly as well.
        json("assets/classwar/lang/en_us.json", translations)
        json("assets/classwar/lang/ko_kr.json", translations)
    }
}
tasks.register<Zip>("statusIconPack") {
    group = "build"
    description = "Build the optional status icon resource pack (no automatic upload)."
    dependsOn(generateStatusIconPack)
    from(generatedPack)
    destinationDirectory.set(layout.buildDirectory.dir("resource-packs"))
    archiveFileName.set("ClassWar-status-icons.zip")
}
