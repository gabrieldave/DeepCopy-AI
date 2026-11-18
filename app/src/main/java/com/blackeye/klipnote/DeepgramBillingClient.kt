package com.blackeye.klipnote

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente de saldo oficial Deepgram (projects + balances).
 * Sin project_id / 403 billing: falla cerrado a modo local — nunca errores rojos técnicos.
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
        /** Key sin billing:read o sin project_id — UI usa contador local, sin rojo. */
        LOCAL_USAGE_ONLY,
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
            // Sin llave: modo local silencioso (no simular 401 de billing).
            return BalanceSnapshot(
                status = BillingScopeStatus.LOCAL_USAGE_ONLY,
                projectId = null,
                balanceUsd = null,
                message = null
            )
        }

        val projectResult = resolveProjectId(context, apiKey)
        val projectId = projectResult.projectId
        if (projectId == null) {
            // 403 / sin permiso / sin project_id → silencioso, contador local.
            return BalanceSnapshot(
                status = when (projectResult.httpCode) {
                    401 -> BillingScopeStatus.UNAUTHORIZED
                    else -> BillingScopeStatus.LOCAL_USAGE_ONLY
                },
                projectId = null,
                balanceUsd = null,
                message = null
            )
        }

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
                        message = null
                    )
                    403 -> BalanceSnapshot(
                        status = BillingScopeStatus.LOCAL_USAGE_ONLY,
                        projectId = projectId,
                        balanceUsd = null,
                        message = null
                    )
                    else -> BalanceSnapshot(
                        status = BillingScopeStatus.LOCAL_USAGE_ONLY,
                        projectId = projectId,
                        balanceUsd = null,
                        message = null
                    )
                }
            }
        } catch (_: Exception) {
            BalanceSnapshot(
                status = BillingScopeStatus.LOCAL_USAGE_ONLY,
                projectId = projectId,
                balanceUsd = null,
                message = null
            )
        }
    }

    private data class ProjectResolve(
        val projectId: String?,
        val httpCode: Int?
    )

    private fun resolveProjectId(context: Context, apiKey: String): ProjectResolve {
        BillingManager.getProjectId(context)?.let {
            return ProjectResolve(projectId = it, httpCode = 200)
        }

        val request = Request.Builder()
            .url(PROJECTS_URL)
            .addHeader("Authorization", "Token $apiKey")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return ProjectResolve(projectId = null, httpCode = response.code)
                }
                val root = JSONObject(raw)
                val projects = root.optJSONArray("projects")
                    ?: return ProjectResolve(projectId = null, httpCode = response.code)
                if (projects.length() == 0) {
                    return ProjectResolve(projectId = null, httpCode = response.code)
                }
                val first = projects.getJSONObject(0)
                val id = first.optString("project_id")
                    .ifBlank { first.optString("projectId") }
                    .ifBlank { first.optString("id") }
                    .takeIf { it.isNotBlank() }
                if (id != null) {
                    BillingManager.setProjectId(context, id)
                }
                ProjectResolve(projectId = id, httpCode = response.code)
            }
        } catch (_: Exception) {
            ProjectResolve(projectId = null, httpCode = null)
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
                val amount = when {
                    b.has("amount") && !b.isNull("amount") -> {
                        when (val rawAmount = b.get("amount")) {
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
