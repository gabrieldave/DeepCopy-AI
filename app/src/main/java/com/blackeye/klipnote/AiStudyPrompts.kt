package com.blackeye.klipnote

import android.content.Context
import androidx.annotation.Keep

@Keep
enum class AiStudyMode {
    SUMMARY,
    BRIEF,
    INFOGRAPHIC,
    DIAGRAMS,
    DEEP_RESEARCH,
    BUILD_PROMPT,
    STUDY_QUIZ,
    CLEAN_SCRIPT;

    fun chipLabel(context: Context): String = when (this) {
        SUMMARY -> context.getString(R.string.ai_chip_summary)
        BRIEF -> context.getString(R.string.ai_chip_brief)
        INFOGRAPHIC -> context.getString(R.string.ai_chip_infographic)
        DIAGRAMS -> context.getString(R.string.ai_chip_diagrams)
        DEEP_RESEARCH -> context.getString(R.string.ai_chip_research)
        BUILD_PROMPT -> context.getString(R.string.ai_chip_build_prompt)
        STUDY_QUIZ -> context.getString(R.string.ai_chip_study_quiz)
        CLEAN_SCRIPT -> context.getString(R.string.ai_chip_clean_script)
    }

    fun modeName(context: Context): String = when (this) {
        SUMMARY -> context.getString(R.string.ai_mode_summary)
        BRIEF -> context.getString(R.string.ai_mode_brief)
        INFOGRAPHIC -> context.getString(R.string.ai_mode_infographic)
        DIAGRAMS -> context.getString(R.string.ai_mode_diagrams)
        DEEP_RESEARCH -> context.getString(R.string.ai_mode_research)
        BUILD_PROMPT -> context.getString(R.string.ai_mode_build_prompt)
        STUDY_QUIZ -> context.getString(R.string.ai_mode_study_quiz)
        CLEAN_SCRIPT -> context.getString(R.string.ai_mode_clean_script)
    }
}

@Keep
object AiStudyPrompts {

    const val MASTER_ALL_8 = "MASTER_ALL_8"

    fun masterAll8Label(context: Context): String =
        context.getString(R.string.ai_master_all_label)

    fun masterAll8ModeName(context: Context): String =
        context.getString(R.string.ai_master_all_mode)

    fun build(
        context: Context,
        mode: AiStudyMode,
        transcript: String,
        sourceTitleOrUrl: String
    ): String {
        val replyInSpanish = AppLocales.isAppSpanish(context)
        val source = sourceTitleOrUrl.trim().ifBlank { context.getString(R.string.untitled) }
        val body = intentFor(mode, replyInSpanish)
        return buildString {
            appendLine(languageRule(context, replyInSpanish))
            appendLine()
            appendLine(body)
            appendLine()
            appendSourceAndTranscript(context, source, transcript)
        }
    }

    fun buildMasterAll8(
        context: Context,
        transcript: String,
        sourceTitleOrUrl: String
    ): String {
        val replyInSpanish = AppLocales.isAppSpanish(context)
        val source = sourceTitleOrUrl.trim().ifBlank { context.getString(R.string.untitled) }
        return buildString {
            appendLine(languageRule(context, replyInSpanish))
            appendLine()
            appendLine(masterAll8Intent(replyInSpanish))
            appendLine()
            appendSourceAndTranscript(context, source, transcript)
        }
    }

    private fun intentFor(mode: AiStudyMode, es: Boolean): String = when (mode) {
        AiStudyMode.SUMMARY -> if (es) summaryIntentEs() else summaryIntentEn()
        AiStudyMode.BRIEF -> if (es) briefIntentEs() else briefIntentEn()
        AiStudyMode.INFOGRAPHIC -> if (es) infographicIntentEs() else infographicIntentEn()
        AiStudyMode.DIAGRAMS -> if (es) diagramsIntentEs() else diagramsIntentEn()
        AiStudyMode.DEEP_RESEARCH -> if (es) deepResearchIntentEs() else deepResearchIntentEn()
        AiStudyMode.BUILD_PROMPT -> if (es) buildPromptIntentEs() else buildPromptIntentEn()
        AiStudyMode.STUDY_QUIZ -> if (es) studyQuizIntentEs() else studyQuizIntentEn()
        AiStudyMode.CLEAN_SCRIPT -> if (es) cleanScriptIntentEs() else cleanScriptIntentEn()
    }

    private fun StringBuilder.appendSourceAndTranscript(
        context: Context,
        source: String,
        transcript: String
    ) {
        appendLine(context.getString(R.string.ai_source_header))
        appendLine(source)
        appendLine()
        appendLine(context.getString(R.string.ai_transcript_header))
        append(transcript.trim())
    }

    private fun languageRule(context: Context, replyInSpanish: Boolean): String =
        if (replyInSpanish) {
            context.getString(R.string.ai_language_rule_es)
        } else {
            context.getString(R.string.ai_language_rule_match)
        }

    private fun masterAll8Intent(es: Boolean): String = if (es) {
        """
            Actúa como un equipo multidisciplinario de expertos. Entrega UNA respuesta con 8 secciones
            numeradas. Sé fiel a la transcripción; no inventes hechos.
        """.trimIndent()
    } else {
        """
            Act as a multidisciplinary expert team. Deliver ONE answer with 8 numbered sections.
            Stay faithful to the transcript; do not invent facts.
        """.trimIndent()
    }

    private fun summaryIntentEs() = """
        Eres un tutor de estudio experto. Resumen completo sin relleno: idea central, conceptos, datos y conclusión práctica.
    """.trimIndent()

    private fun summaryIntentEn() = """
        You are an expert study tutor. Full summary without filler: core idea, concepts, data, and practical takeaway.
    """.trimIndent()

    private fun briefIntentEs() = """
        Brief ejecutivo: 2 líneas de síntesis, 5 puntos accionables y lección principal para hoy.
    """.trimIndent()

    private fun briefIntentEn() = """
        Executive brief: 2-line synthesis, 5 actionable bullets, and the main lesson for today.
    """.trimIndent()

    private fun infographicIntentEs() = """
        Esquema de infografía didáctica y prompt visual detallado listo para generar la imagen.
    """.trimIndent()

    private fun infographicIntentEn() = """
        Didactic infographic outline plus a detailed visual generation prompt.
    """.trimIndent()

    private fun diagramsIntentEs() = """
        Diagrama de flujo en texto, mapa conceptual jerárquico y bloque Mermaid listo para renderizar.
    """.trimIndent()

    private fun diagramsIntentEn() = """
        Text flowchart, hierarchical concept map, and Mermaid block ready to render.
    """.trimIndent()

    private fun deepResearchIntentEs() = """
        Afirmaciones vs demostración, profundización técnica, contexto verificado, limitaciones y siguientes pasos.
    """.trimIndent()

    private fun deepResearchIntentEn() = """
        Claims vs evidence, technical depth, verified context, limitations, and next research steps.
    """.trimIndent()

    private fun buildPromptIntentEs() = """
        Convierte el método en un Prompt Maestro reutilizable: Rol, Objetivo, variables [...] y pasos.
    """.trimIndent()

    private fun buildPromptIntentEn() = """
        Turn the method into a reusable Master Prompt: Role, Goal, variables [...], and steps.
    """.trimIndent()

    private fun studyQuizIntentEs() = """
        Explicación didáctica, ejercicio práctico y 5 preguntas con respuestas en sección RESPUESTAS.
    """.trimIndent()

    private fun studyQuizIntentEn() = """
        Clear teaching, a practice exercise, and 5 questions with answers in a RESPONSES section.
    """.trimIndent()

    private fun cleanScriptIntentEs() = """
        Limpia muletillas y entrega guion por párrafos conservando el 100% de las ideas originales.
    """.trimIndent()

    private fun cleanScriptIntentEn() = """
        Remove speech filler and deliver polished paragraphs keeping 100% of the original ideas.
    """.trimIndent()
}