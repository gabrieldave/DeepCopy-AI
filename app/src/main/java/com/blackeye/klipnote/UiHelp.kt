package com.blackeye.klipnote

import com.blackeye.klipnote.ui.KlipNeutrals
import com.blackeye.klipnote.ui.KlipPalette

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ClarityMuted get() = KlipNeutrals.Muted
val ClarityOnBg get() = KlipNeutrals.OnBg
val ClarityAccent get() = KlipPalette.accent
val ClarityCard get() = KlipNeutrals.Card

object HelpCopy {
    @StringRes val AUTO = R.string.help_auto_routing_body
    @StringRes val KEYS = R.string.help_my_keys_body
    @StringRes val COOKIES = R.string.help_login_videos_body
    @StringRes val LANGUAGE = R.string.help_language_body
    @StringRes val DIARIZE = R.string.help_diarize_body
    @StringRes val YTDLP = R.string.help_download_engine_body
    @StringRes val METER_ADJUST = R.string.help_meter_adjust_body
    @StringRes val ZERO_COST = R.string.help_zero_cost_body
    @StringRes val SERVICES = R.string.help_free_services_body

    fun auto(context: Context) = context.getString(R.string.help_auto_routing_body)
    fun keys(context: Context) = context.getString(R.string.help_my_keys_body)
    fun cookies(context: Context) = context.getString(R.string.help_login_videos_body)
    fun language(context: Context) = context.getString(R.string.help_language_body)
    fun diarize(context: Context) = context.getString(R.string.help_diarize_body)
    fun ytdlp(context: Context) = context.getString(R.string.help_download_engine_body)
    fun meterAdjust(context: Context) = context.getString(R.string.help_meter_adjust_body)
    fun zeroCost(context: Context) = context.getString(R.string.help_zero_cost_body)
    fun services(context: Context) = context.getString(R.string.help_free_services_body)
}

@Composable
fun SectionHeader(
    title: String,
    help: String? = null,
    modifier: Modifier = Modifier,
    @StringRes helpRes: Int? = null
) {
    val helpText = help ?: helpRes?.let { stringResource(it) }
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
        if (helpText != null) {
            IconButton(
                onClick = { open = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.HelpOutline,
                    contentDescription = stringResource(R.string.cd_help),
                    tint = ClarityMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    if (open && helpText != null) {
        HelpDialog(title = title, body = helpText, onDismiss = { open = false })
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
                Text(stringResource(R.string.help_got_it), color = ClarityAccent)
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

fun sourceKindLabel(context: Context, source: String): String {
    val s = source.lowercase()
    return when {
        "youtube.com" in s || "youtu.be" in s -> context.getString(R.string.source_youtube)
        "instagram.com" in s || "instagr.am" in s -> context.getString(R.string.source_instagram)
        "tiktok.com" in s -> context.getString(R.string.source_tiktok)
        "facebook.com" in s || "fb.watch" in s -> context.getString(R.string.source_facebook)
        "twitter.com" in s || "x.com" in s -> context.getString(R.string.source_x)
        s.startsWith("http") -> context.getString(R.string.source_link)
        else -> context.getString(R.string.source_file)
    }
}
