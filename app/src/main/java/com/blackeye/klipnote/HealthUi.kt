package com.blackeye.klipnote

import android.content.Context

/** User-visible health / offer labels (localized). */
internal object HealthUi {
    fun noKey(context: Context): String = context.getString(R.string.health_no_key)

    fun keyReady(context: Context, mask: String?): String =
        context.getString(
            R.string.health_key_ready_mask,
            context.getString(R.string.health_key_ready),
            mask ?: "••••"
        )

    fun bagEmpty(context: Context): String = context.getString(R.string.health_bag_empty)

    fun keyInvalid(context: Context): String = context.getString(R.string.health_key_invalid)

    fun noBalance(context: Context): String = context.getString(R.string.health_no_balance)

    fun active(context: Context, suffix: String): String =
        context.getString(R.string.health_active, suffix)

    fun activeFree(context: Context, suffix: String): String =
        context.getString(R.string.health_active_free, suffix)

    fun activeLocal(context: Context): String =
        context.getString(R.string.health_active_local)

    fun pausedLimit(context: Context): String =
        context.getString(R.string.health_paused_limit)

    fun pausedDaily(context: Context): String =
        context.getString(R.string.health_paused_daily)

    fun offerEnded(context: Context): String =
        context.getString(R.string.health_offer_ended)

    fun ready(context: Context, suffix: String): String =
        context.getString(R.string.health_ready, suffix)

    fun zeroModeBlocked(context: Context): String =
        context.getString(R.string.health_zero_mode_blocked)

    fun genericError(context: Context): String =
        context.getString(R.string.health_error)
}