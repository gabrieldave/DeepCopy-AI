package com.blackeye.klipnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStudyPromptsTest {

    @Test
    fun everyActionHasCompleteBilingualInstructions() {
        assertEquals(16, AiStudyMode.entries.size)
        for (mode in AiStudyMode.entries) {
            for (spanish in listOf(true, false)) {
                val prompt = AiStudyPrompts.intentFor(mode, spanish)
                assertTrue("Missing content for $mode language=$spanish", prompt.length > 95)
                assertFalse("Blank instructions for $mode", prompt.isBlank())
            }
        }
    }

    @Test
    fun masterAlwaysNamesAllEightDeliverablesInBothLanguages() {
        for (spanish in listOf(true, false)) {
            val prompt = AiStudyPrompts.masterAll8Intent(spanish)
            val sections = Regex("(?m)^([1-8])\\. ").findAll(prompt).map { it.groupValues[1] }.toList()
            assertEquals((1..8).map { it.toString() }, sections)
        }
    }

    @Test
    fun infographicAsksForActualImageOrCompleteSvg() {
        val spanish = AiStudyPrompts.intentFor(AiStudyMode.INFOGRAPHIC, true)
        val english = AiStudyPrompts.intentFor(AiStudyMode.INFOGRAPHIC, false)
        assertTrue(spanish.contains("INFOGRAFÍA VISUAL TERMINADA"))
        assertTrue(spanish.contains("SVG COMPLETO"))
        assertTrue(english.contains("FINISHED VISUAL INFOGRAPHIC"))
        assertTrue(english.contains("COMPLETE 1080"))
    }

    @Test
    fun websiteAndInteractiveActionsRequestRunnableHtml() {
        for (mode in listOf(
            AiStudyMode.WEBSITE_HTML,
            AiStudyMode.INTERACTIVE_APP,
            AiStudyMode.DATA_DASHBOARD,
            AiStudyMode.SLIDES,
            AiStudyMode.TIMELINE,
            AiStudyMode.FLASHCARDS,
            AiStudyMode.PODCAST,
            AiStudyMode.CHECKLIST
        )) {
            assertTrue("$mode must request an HTML artifact",
                AiStudyPrompts.intentFor(mode, true).contains("HTML"))
            assertTrue("$mode must request an English HTML artifact",
                AiStudyPrompts.intentFor(mode, false).contains("HTML"))
            assertTrue(mode.isCreative())
        }
    }

    @Test
    fun masterPromptBuilderIsTheOnlyTextFirstPromptCreationAction() {
        assertFalse(AiStudyMode.BUILD_PROMPT.isCreative())
        assertTrue(AiStudyPrompts.intentFor(AiStudyMode.BUILD_PROMPT, true)
            .contains("PROMPT MAESTRO"))
        assertTrue(AiStudyPrompts.intentFor(AiStudyMode.INFOGRAPHIC, true)
            .contains("NUNCA entregues un prompt"))
    }
}
