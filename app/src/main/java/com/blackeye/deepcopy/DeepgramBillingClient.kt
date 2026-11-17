package com.blackeye.deepcopy

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente de saldo oficial Deepgram (projects + balances).
 * No crash ante 403 Member / 401; estados tipados para la UI.
 */
object DeepgramBillingClient {

    private const val PROJECTS_URL = "https://api.deepgram.com/v1/projects"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    enum class BillingScopeStatus {
        OK,
        MEMBER_KEY_NO_BILLING_SCOPE,
        UNAUTHORIZED,
        ERROR
    }

    data class BalanceSnapshot(
        val status: BillingScopeStatus,
        val projectId: String?,
        val balanceUsd: Double?,
        val message: String?
    )

    fun fetchOfficialBalance(context: Context, apiKey: String): BalanceSnapshot {
        if (apiKey.isBlank()) {
            return BalanceSnapshot(
                status = BillingScopeStatus.UNAUTHORIZED,
                projectId = null,
                balanceUsd = null,
                message = "API Key vacía"
            )
        }

        val projectId = resolveProjectId(context, apiKey)
            ?: return BalanceSnapshot(
                status = BillingScopeStatus.ERROR,
                projectId = null,
                balanceUsd = null,
                message = "No se pudo obtener project_id"
            )

        val request = Request.Builder()
            .url("https://api.deepgram.com/v1/projects/$projectId/balances")
            .addHeader("Authorization", "Token $apiKey")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> {
                        val total = sumUsdBalances(raw)
                        BalanceSnapshot(
                            status = BillingScopeStatus.OK,
                            projectId = projectId,
                            balanceUsd = total,
                            message = null
                        )
                    }
                    401 -> BalanceSnapshot(
                        status = BillingScopeStatus.UNAUTHORIZED,
                        projectId = projectId,
                        balanceUsd = null,
                        message = "API Key inválida o sin acceso (401)"
                    )
                    403 -> BalanceSnapshot(
                        status = BillingScopeStatus.MEMBER_KEY_NO_BILLING_SCOPE,
                        projectId = projectId,
                        balanceUsd = null,
                        message = "API Key sin permiso billing:read (rol Member). " +
                            "Usando auditoría local exacta; usa una llave Admin en Deepgram " +
                            "para ver saldo oficial en vivo."
                    )
                    else -> BalanceSnapshot(
                        status = BillingScopeStatus.ERROR,
                        projectId = projectId,
                        balanceUsd = null,
                        message = "Balances ${response.code}: ${raw.take(200)}"
                    )
                }
            }
        } catch (e: Exception) {
            BalanceSnapshot(
                status = BillingScopeStatus.ERROR,
                projectId = projectId,
                balanceUsd = null,
                message = e.message ?: "Error de red al consultar saldo"
            )
        }
    }

    private fun resolveProjectId(context: Context, apiKey: String): String? {
        BillingManager.getProjectId(context)?.let { return it }

        val request = Request.Builder()
            .url(PROJECTS_URL)
            .addHeader("Authorization", "Token $apiKey")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401 || response.code == 403) return null
                    return null
                }
                val root = JSONObject(raw)
                val projects = root.optJSONArray("projects") ?: return null
                if (projects.length() == 0) return null
                val first = projects.getJSONObject(0)
                val id = first.optString("project_id")
                    .ifBlank { first.optString("projectId") }
                    .ifBlank { first.optString("id") }
                    .takeIf { it.isNotBlank() }
                if (id != null) {
                    BillingManager.setProjectId(context, id)
                }
                id
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sumUsdBalances(raw: String): Double {
        val root = JSONObject(raw)
        val balances = root.optJSONArray("balances") ?: return 0.0
        var total = 0.0
        for (i in 0 until balances.length()) {
            val b = balances.getJSONObject(i)
            val units = b.optString("units", b.optString("unit", "")).lowercase()
            if (units == "usd" || units.isBlank()) {
                // amount puede venir como number o string
                val amount = when {
                    b.has("amount") && !b.isNull("amount") -> {
                        val rawAmount = b.get("amount")
                        when (rawAmount) {
                            is Number -> rawAmount.toDouble()
                            is String -> rawAmount.toDoubleOrNull() ?: 0.0
                            else -> b.optDouble("amount", 0.0)
                        }
                    }
                    else -> 0.0
                }
                total += amount
            }
        }
        return total
    }
}
