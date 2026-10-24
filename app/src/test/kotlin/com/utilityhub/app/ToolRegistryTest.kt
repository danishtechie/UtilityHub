package com.utilityhub.app

import com.utilityhub.app.tools.ToolHost
import com.utilityhub.app.tools.ToolRegistry
import com.utilityhub.logic.catalog.ToolCatalog
import com.utilityhub.logic.catalog.ToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    @Test fun `every tool the catalog calls available has a real implementation`() {
        assertEquals(emptyList<String>(), ToolRegistry.unimplementedAvailableIds())
    }

    @Test fun `planned tools have no host so they can never be opened`() {
        ToolCatalog.all.filter { it.status == ToolStatus.PLANNED }.forEach { assertNull(it.id, ToolRegistry.host(it.id)) }
    }

    @Test fun `only file tools are metered - text and calculator tools stay free`() {
        ToolCatalog.all.filter { it.metered }.forEach { meta ->
            assertTrue("${meta.id} is metered but is not a file tool", ToolRegistry.host(meta.id) is ToolHost.Files)
        }
        ToolCatalog.all.filter { it.category.name in setOf("TEXT", "EVERYDAY") }.forEach { assertTrue("${it.id} must be free", !it.metered) }
    }

    @Test fun `file tools that need several inputs have a sensible limit`() {
        ToolCatalog.all.mapNotNull { ToolRegistry.host(it.id) as? ToolHost.Files }.forEach {
            assertTrue(it.tool.maxInputs in 1..200)
        }
    }
}
