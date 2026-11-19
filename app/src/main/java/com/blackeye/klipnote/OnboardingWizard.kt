package com.blackeye.klipnote

import com.blackeye.klipnote.ui.KlipNeutrals
import com.blackeye.klipnote.ui.KlipPalette

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg get() = KlipNeutrals.Bg
private val Card get() = KlipNeutrals.Card
private val Accent get() = KlipPalette.accent
private val OnAccent get() = KlipPalette.onAccent
private val OnBg get() = KlipNeutrals.OnBg
private val Muted get() = KlipNeutrals.Muted
private val Amber get() = KlipNeutrals.Amber

private const val DEBUG_BORDERS = false

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

/**
 * Pantalla completa independiente (llamar desde setContent, nunca dentro de Scaffold).
 */
@Composable
fun OnboardingWizard(
    onFinished: () -> Unit,
    onSavedKey: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var page by remember { mutableIntStateOf(0) }
    var keyText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var middleViewportPx by remember { mutableIntStateOf(0) }

    fun finish() {
        OnboardingPrefs.markDone(context)
        onFinished()
    }

    fun goNextFromKeyPage() {
        val trimmed = keyText.trim()
        if (trimmed.isNotBlank()) {
            ApiKeyStore.setGroq(context, trimmed)
            onSavedKey()
        }
        error = null
        page = 2
    }

    BackHandler {
        if (page > 0) page -= 1 else finish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        // 1) Fila superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (DEBUG_BORDERS) Modifier.border(2.dp, Color.Cyan) else Modifier
                ),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { finish() }) {
                Text(stringResource(R.string.skip), color = Muted, fontSize = 15.sp)
            }
        }

        // 2) Contenido central (único scroll). Box con weight fija el viewport;
        // heightIn(min=viewport) permite Arrangement.Center sin aplastar los botones.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { middleViewportPx = it.height }
                .then(
                    if (DEBUG_BORDERS) Modifier.border(2.dp, Color.Magenta) else Modifier
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = with(density) {
                            if (middleViewportPx > 0) middleViewportPx.toDp() else 0.dp
                        }
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(20.dp))

            when (page) {
                0 -> {
                    Text(
                        stringResource(R.string.onboarding_title_1),
                        color = OnBg,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.onboarding_body_1),
                        color = Muted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                1 -> {
                    Text(
                        stringResource(R.string.onboarding_title_2),
                        color = OnBg,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.onboarding_body_2),
                        color = Muted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
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
                            contentColor = OnAccent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.get_my_key), fontWeight = FontWeight.Bold)
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
                        placeholder = {
                            Text(stringResource(R.string.onboarding_paste_key_hint), color = Muted)
                        },
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
                            .padding(14.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            stringResource(R.string.onboarding_zero_title),
                            color = Amber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.onboarding_zero_body),
                            color = OnBg,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                else -> {
                    Text(
                        stringResource(R.string.done),
                        color = OnBg,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.onboarding_body_3),
                        color = Muted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            ProgressDots(page = page, total = 3)
            }
        }

        // 3) Botones fuera del scroll
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .then(
                    if (DEBUG_BORDERS) Modifier.border(2.dp, Color.Yellow) else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page > 0) {
                TextButton(onClick = { page -= 1 }) {
                    Text(stringResource(R.string.back), color = Muted, fontSize = 15.sp)
                }
                Spacer(Modifier.width(8.dp))
            }
            Button(
                onClick = {
                    when (page) {
                        0 -> page = 1
                        1 -> goNextFromKeyPage()
                        else -> finish()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (page == 2) {
                        stringResource(R.string.start)
                    } else {
                        stringResource(R.string.continue_btn)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ProgressDots(page: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (index == page) Accent else Muted.copy(alpha = 0.35f))
            )
        }
    }
}
