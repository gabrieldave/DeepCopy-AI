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

    /** Etiqueta del chip en UI. */
    val chipLabel: String
        get() = when (this) {
            SUMMARY -> "📝 Resumen"
            BRIEF -> "⚡ Brief"
            INFOGRAPHIC -> "📊 Infografía"
            DIAGRAMS -> "🔀 Diagramas"
            DEEP_RESEARCH -> "🔍 Investigar"
            BUILD_PROMPT -> "🤖 Crear Prompt"
            STUDY_QUIZ -> "🎓 Clase + Quiz"
            CLEAN_SCRIPT -> "✨ Pulir Guion"
        }

    /** Nombre corto para la barra «Prompt de [Modo]». */
    val modeName: String
        get() = when (this) {
            SUMMARY -> "Resumen"
            BRIEF -> "Brief"
            INFOGRAPHIC -> "Infografía"
            DIAGRAMS -> "Diagramas"
            DEEP_RESEARCH -> "Investigar"
            BUILD_PROMPT -> "Crear Prompt"
            STUDY_QUIZ -> "Clase + Quiz"
            CLEAN_SCRIPT -> "Pulir Guion"
        }
}

@Keep
object AiStudyPrompts {

    private const val PREFS = "klipnote_ai_study"
    private const val KEY_REPLY_IN_SPANISH = "reply_in_spanish"

    fun isReplyInSpanish(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REPLY_IN_SPANISH, true)

    fun setReplyInSpanish(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REPLY_IN_SPANISH, value).apply()
    }

    fun build(
        mode: AiStudyMode,
        transcript: String,
        sourceTitleOrUrl: String,
        replyInSpanish: Boolean = true
    ): String {
        val source = sourceTitleOrUrl.trim().ifBlank { "Sin título" }
        val body = when (mode) {
            AiStudyMode.SUMMARY -> summaryIntent()
            AiStudyMode.BRIEF -> briefIntent()
            AiStudyMode.INFOGRAPHIC -> infographicIntent()
            AiStudyMode.DIAGRAMS -> diagramsIntent()
            AiStudyMode.DEEP_RESEARCH -> deepResearchIntent()
            AiStudyMode.BUILD_PROMPT -> buildPromptIntent()
            AiStudyMode.STUDY_QUIZ -> studyQuizIntent()
            AiStudyMode.CLEAN_SCRIPT -> cleanScriptIntent()
        }
        return buildString {
            appendLine(languageRule(replyInSpanish))
            appendLine()
            appendLine(body)
            appendLine()
            appendLine("--- FUENTE ---")
            appendLine(source)
            appendLine()
            appendLine("--- TRANSCRIPCIÓN COMPLETA ---")
            append(transcript.trim())
        }
    }

    private fun languageRule(replyInSpanish: Boolean): String =
        if (replyInSpanish) {
            "IDIOMA DE RESPUESTA: Analiza fielmente la transcripción original " +
                "(aunque esté en inglés u otro idioma) y entrega TODO el resultado en " +
                "español claro y profesional."
        } else {
            "IDIOMA DE RESPUESTA: Responde en el mismo idioma de la transcripción."
        }

    private fun summaryIntent(): String = """
        Eres un tutor de estudio experto. Elabora un **Resumen completo de estudio sin relleno**:
        1) Idea central en 1-2 frases.
        2) Explicación estructurada de cada concepto o paso clave.
        3) Datos, cifras o fórmulas mencionadas (si las hay).
        4) Conclusión práctica: qué debe recordar y aplicar el lector.
        Sé fiel al contenido; no inventes información que no esté en la fuente.
    """.trimIndent()

    private fun briefIntent(): String = """
        Eres un editor ejecutivo. Entrega un **Brief de lectura rápida**:
        1) Síntesis en exactamente 2 líneas.
        2) Los 5 puntos más valiosos y accionables (viñetas cortas).
        3) La lección principal lista para aplicar hoy (1 frase clara).
        Prioriza claridad y utilidad inmediata; cero relleno.
    """.trimIndent()

    private fun infographicIntent(): String = """
        Eres diseñador instruccional + director de arte.
        1) Extrae los puntos clave del contenido.
        2) Diseña primero el **esquema completo** de una infografía didáctica dividida en
           bloques visuales cortos de alto impacto (título de bloque, bullets, jerarquía visual).
        3) Inmediatamente después, escribe un **prompt visual detallado** listo para generar
           la infografía (estilo, layout, tipografía sugerida, iconografía, paleta) en español.
        El esquema y el prompt visual deben estar claramente separados.
    """.trimIndent()

    private fun diagramsIntent(): String = """
        Eres arquitecto de información. Transforma el proceso, método o arquitectura explicada
        en la transcripción en tres entregables:
        (1) Un diagrama de flujo visual paso a paso en texto limpio (cajas → flechas).
        (2) Un mapa conceptual jerárquico (nodos padre/hijo).
        (3) Un bloque de código Mermaid listo para renderizar (`flowchart TD` o `mindmap`,
           elige el más adecuado al contenido).
        Sé fiel a la lógica del autor; no agregues pasos inventados.
    """.trimIndent()

    private fun deepResearchIntent(): String = """
        Actúa como investigador senior. Con base en la transcripción:
        1) Analiza las afirmaciones del video (qué se afirma vs. qué se demuestra).
        2) Profundiza técnicamente en los conceptos mencionados.
        3) Aporta contexto adicional verificado y casos de estudio reales relacionados.
        4) Señala posibles limitaciones, contrapuntos o riesgos.
        5) Lista qué investigar después (preguntas abiertas + fuentes sugeridas).
        Separa claramente hechos del video, contexto externo y opiniones/hipótesis.
    """.trimIndent()

    private fun buildPromptIntent(): String = """
        Eres ingeniero de prompts. Haz ingeniería inversa del video:
        Extrae la metodología, fórmula o marco de trabajo que enseña el autor y conviértelo
        en un **Prompt Maestro reutilizable** con:
        - Rol
        - Objetivo
        - Variables de entrada entre corchetes `[...]`
        - Pasos exactos de ejecución
        El Prompt Maestro debe permitir que una IA ejecute ese sistema cuando el usuario lo pegue
        con sus propias variables, sin necesitar el video original.
    """.trimIndent()

    private fun studyQuizIntent(): String = """
        Eres un profesor experto. Enséñame este tema con claridad:
        1) Explicación didáctica con ejemplos claros.
        2) Un ejercicio práctico de aplicación inmediata.
        3) Exactamente 5 preguntas clave para dominar el tema, con las respuestas al final
           (sección separada «RESPUESTAS»).
        Mantén un tono motivador pero preciso; no inventes contenido ajeno a la fuente.
    """.trimIndent()

    private fun cleanScriptIntent(): String = """
        Eres un editor de guiones. Limpia todas las muletillas, repeticiones y cortes del
        lenguaje hablado y entrega el texto perfectamente redactado por párrafos y subtítulos,
        respetando el **100% de las ideas originales** (sin añadir ni omitir conceptos).
        Conserva el orden lógico del discurso; solo mejora claridad y fluidez escrita.
    """.trimIndent()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
