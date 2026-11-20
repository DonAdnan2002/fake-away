package com.fakeaway.app.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationCompletenessTest {

    @Test
    fun allTrilingualStringResources_haveIdenticalKeysAndNonEmptyValues() {
        val resDir = resolveResDir()
        val files = listOf(
            "en" to File(resDir, "values/strings.xml"),
            "ar" to File(resDir, "values-ar/strings.xml"),
            "ckb" to File(resDir, "values-b+ckb/strings.xml"),
            "ku" to File(resDir, "values-ku/strings.xml")
        )

        val parsed = files.associate { (code, file) ->
            assertTrue("Missing resource file: ${file.absolutePath}", file.exists())
            code to parseStringsXml(file)
        }

        val baseKeys = parsed.getValue("en").keys
        assertTrue("Base strings.xml must contain entries", baseKeys.isNotEmpty())

        for ((code, map) in parsed) {
            assertEquals("Key mismatch in $code strings.xml", baseKeys, map.keys)
            for ((key, value) in map) {
                assertFalse("Empty string value for '$key' in '$code'", value.isBlank())
            }
        }

        // Verify Kurdish BCP 47 and fallback qualifier files stay identical
        assertEquals(parsed.getValue("ckb"), parsed.getValue("ku"))

        // Spot check that Arabic and Kurdish are actually translated (not left in English)
        assertTrue(parsed.getValue("ar").getValue("nav_voice") != parsed.getValue("en").getValue("nav_voice"))
        assertTrue(parsed.getValue("ckb").getValue("nav_voice") != parsed.getValue("en").getValue("nav_voice"))
    }

    private fun resolveResDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate app/src/main/res from ${File(".").absolutePath}")
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val elem = nodes.item(i)
            val name = elem.attributes.getNamedItem("name")?.nodeValue ?: continue
            map[name] = elem.textContent ?: ""
        }
        return map
    }
}
