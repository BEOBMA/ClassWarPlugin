package org.beobma.classWarPlugin.testing

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class StatusTestCatalogTest {
    @Test fun `every public concrete status has an application item`() {
        val root = Path.of("src/main/kotlin/org/beobma/classWarPlugin/status/list")
        val pattern = Regex("(?m)^(?:open )?class ([A-Za-z0-9_]+)")
        val declared = Files.list(root).use { paths -> paths.filter { it.toString().endsWith(".kt") }.toList()
            .flatMap { path -> pattern.findAll(Files.readString(path)).map { it.groupValues[1] }.toList() }.toSet() }
        assertEquals(declared,StatusTestCatalog.entries.map { it.id }.toSet())
        assertEquals(declared.size,StatusTestCatalog.entries.size)
    }
    @Test fun `stable identifiers lookup exactly and no unknown item can instantiate a status`() {
        StatusTestCatalog.entries.forEach { assertSame(it,StatusTestCatalog.find(it.id)) }
        assertNull(StatusTestCatalog.find("java.lang.Runtime"))
        assertNull(StatusTestCatalog.find(""))
        assertNotNull(StatusTestCatalog.find("VibrationExplosion"))
        assertNotNull(StatusTestCatalog.find("CheckpointStatus"))
    }
    @Test fun `laboratory is not in the playable class catalog`() {
        assertFalse("status-laboratory" in org.beobma.classWarPlugin.ability.AbilityCatalog.enabledClassIds())
    }
}
