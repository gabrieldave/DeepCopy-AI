package com.blackeye.klipnote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ClarityMuted = Color(0xFFB0BCCE)
val ClarityOnBg = Color(0xFFE8EEF7)
val ClarityAccent = Color(0xFF10B981)
val ClarityCard = Color(0xFF151C28)

object HelpCopy {
    const val AUTO =
        "Usa primero los servicios gratis; si uno se acaba, pasa al siguiente."
    const val KEYS =
        "Cada quien pone sus propias llaves gratis. Se cifran y se quedan en tu teléfono."
    const val COOKIES =
        "Algunos videos solo se pueden bajar con sesión iniciada (privados, cuentas cerradas, " +
            "o cuando Instagram o YouTube te pide entrar). Conecta tu cuenta solo si te sale ese aviso. " +
            "Tus datos se quedan en tu teléfono."
    const val LANGUAGE =
        "Si no sabes, deja «Detectar solo». «Mezclan español e inglés» sirve cuando cambian de idioma. " +
            "«Siempre en…» fuerza un idioma."
    const val DIARIZE =
        "Marca cuándo cambia la persona que habla (solo con Deepgram)."
    const val YTDLP =
        "Se actualiza solo cuando YouTube, TikTok u otros cambian algo. Casi nunca hace falta forzarlo."
    const val METER_ADJUST =
        "La app calcula cuánto te queda. Si no coincide con la página del servicio, escribe aquí " +
            "lo que dice la página. Casi nunca hace falta."
    const val ZERO_COST =
        "Para gastar \$0: no registres tarjeta en Groq, Inworld, AssemblyAI ni Deepgram. " +
            "El interruptor «Modo \$0» solo evita el servicio de pago al final. " +
            "Si pones tarjeta y se acaba el regalo, el proveedor puede cobrarte."
    const val SERVICES =
        "Conectas hasta 4 servicios gratis con tus llaves. La app los usa en orden automático."
}

@Composable
fun SectionHeader(
    title: String,
    help: String? = null,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            color = ClarityOnBg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (help != null) {
            IconButton(
                onClick = { open = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.HelpOutline,
                    contentDescription = "Ayuda",
                    tint = ClarityMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    if (open && help != null) {
        HelpDialog(title = title, body = help, onDismiss = { open = false })
    }
}

@Composable
fun HelpDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ClarityCard,
        titleContentColor = ClarityOnBg,
        textContentColor = ClarityOnBg,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = ClarityMuted) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido", color = ClarityAccent)
            }
        }
    )
}

@Composable
fun InlineHelpLink(label: String, helpTitle: String, helpBody: String) {
    var open by remember { mutableStateOf(false) }
    Text(
        label,
        color = ClarityAccent,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { open = true }
            .padding(vertical = 4.dp)
    )
    if (open) {
        HelpDialog(title = helpTitle, body = helpBody, onDismiss = { open = false })
    }
}

fun sourceKindLabel(source: String): String {
    val s = source.lowercase()
    return when {
        "youtube.com" in s || "youtu.be" in s -> "YouTube"
        "instagram.com" in s || "instagr.am" in s -> "Instagram"
        "tiktok.com" in s -> "TikTok"
        "facebook.com" in s || "fb.watch" in s -> "Facebook"
        "twitter.com" in s || "x.com" in s -> "X"
        s.startsWith("http") -> "Enlace"
        else -> "Archivo"
    }
}
