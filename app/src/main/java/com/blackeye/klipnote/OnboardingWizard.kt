package com.blackeye.klipnote

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val Bg = Color(0xFF0B0F17)
private val Card = Color(0xFF151C28)
private val Accent = Color(0xFF10B981)
private val OnBg = Color(0xFFE8EEF7)
private val Muted = Color(0xFFB0BCCE)
private val Amber = Color(0xFFFBBF24)

object OnboardingPrefs {
    private const val PREFS = "klipnote_onboarding"
    private const val KEY_DONE = "done"

    fun isDone(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DONE, true)
            .apply()
    }
}

@Composable
fun OnboardingWizard(
    onFinished: () -> Unit,
    onSavedKey: () -> Unit
) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    var keyText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun finish() {
        OnboardingPrefs.markDone(context)
        onFinished()
    }

    Dialog(
        onDismissRequest = { /* solo con Saltar / Listo */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "KlipNote",
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paso ${page + 1} de 3",
                    color = Muted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))
                when (page) {
                    0 -> Text(
                        "KlipNote convierte videos y audios en texto, gratis.",
                        color = OnBg,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 30.sp
                    )
                    1 -> {
                        Text(
                            "Necesitas una llave gratis. Te recomendamos Groq: tarda 2 minutos.",
                            color = OnBg,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 28.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://console.groq.com/keys")
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Conseguir mi llave", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = keyText,
                            onValueChange = {
                                keyText = it
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Pega aquí tu llave (gsk_…)", color = Muted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Color(0xFF243044),
                                focusedTextColor = OnBg,
                                unfocusedTextColor = OnBg,
                                cursorColor = Accent
                            )
                        )
                        error?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = Color(0xFFF87171), fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Card)
                                .padding(14.dp)
                        ) {
                            Text(
                                "Para gastar \$0",
                                color = Amber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "NO registres tarjeta en Groq ni en los otros servicios gratis. " +
                                    "Si pones tarjeta y se acaba el regalo, te pueden cobrar.",
                                color = OnBg,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    else -> Text(
                        "¡Listo! Pega un enlace o comparte un video a KlipNote.",
                        color = OnBg,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 30.sp
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                when (page) {
                    0 -> Button(
                        onClick = { page = 1 },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Continuar", fontWeight = FontWeight.Bold) }
                    1 -> {
                        Button(
                            onClick = {
                                val trimmed = keyText.trim()
                                if (trimmed.isBlank()) {
                                    error = "Pega tu llave o pulsa Saltar"
                                    return@Button
                                }
                                ApiKeyStore.setGroq(context, trimmed)
                                onSavedKey()
                                page = 2
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Guardar llave y continuar", fontWeight = FontWeight.Bold) }
                        TextButton(
                            onClick = { page = 2 },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Continuar sin llave", color = Muted) }
                    }
                    else -> Button(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Empezar", fontWeight = FontWeight.Bold) }
                }
                TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Saltar", color = Muted)
                }
            }
        }
    }
}
