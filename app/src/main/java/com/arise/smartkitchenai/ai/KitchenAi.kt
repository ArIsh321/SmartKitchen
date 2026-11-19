package com.arise.smartkitchenai.ai

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class TipType { ORDERING, WASTE, EXPIRY, PREP, SALES, OTHER }

data class AiTip(val title: String, val detail: String, val type: TipType)

data class CachedTips(val tips: List<AiTip>, val generatedAtMillis: Long)

/**
 * Gemini (via Firebase AI Logic) for money-saving recommendations.
 * The latest result per branch filter is cached on the device so the dashboard opens instantly.
 */
class KitchenAi(private val context: Context) {

    /** False until google-services.json is added and the app is rebuilt. */
    val isConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    private val prefs = context.getSharedPreferences("ai_cache", Context.MODE_PRIVATE)

    private val _cache = MutableStateFlow(loadCache())
    /** Cached tips keyed by [cacheKey]. */
    val cache: StateFlow<Map<String, CachedTips>> = _cache.asStateFlow()

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig { responseMimeType = "application/json" },
            systemInstruction = content { text(SYSTEM_INSTRUCTION) },
        )
    }

    /** Sends the kitchen summary to Gemini, caches and returns the recommendations. */
    suspend fun generate(key: String, kitchenSummary: String): List<AiTip> {
        val text = model.generateContent(kitchenSummary).text.orEmpty()
        val tips = parseTips(text)
        if (tips.isNotEmpty()) {
            val entry = CachedTips(tips, System.currentTimeMillis())
            _cache.value = _cache.value + (key to entry)
            prefs.edit { putString(key, toJson(entry).toString()) }
        }
        return tips
    }

    private fun parseTips(text: String): List<AiTip> {
        val json = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = runCatching {
            if (json.startsWith("[")) JSONArray(json) else JSONObject(json).getJSONArray("recommendations")
        }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val title = o.optString("title").trim()
            val detail = o.optString("detail").trim()
            if (title.isEmpty() && detail.isEmpty()) return@mapNotNull null
            val type = runCatching { TipType.valueOf(o.optString("type").uppercase()) }.getOrDefault(TipType.OTHER)
            AiTip(title.ifEmpty { "Recommendation" }, detail, type)
        }
    }

    private fun toJson(entry: CachedTips) = JSONObject().apply {
        put("at", entry.generatedAtMillis)
        put("tips", JSONArray().apply {
            entry.tips.forEach { put(JSONObject().put("title", it.title).put("detail", it.detail).put("type", it.type.name)) }
        })
    }

    private fun loadCache(): Map<String, CachedTips> = prefs.all.mapNotNull { (key, value) ->
        runCatching {
            val o = JSONObject(value as String)
            key to CachedTips(parseTips(o.getJSONArray("tips").toString()), o.getLong("at"))
        }.getOrNull()
    }.toMap()

    companion object {
        /** Cache key for a branch filter (null = all branches). */
        fun cacheKey(branchId: Long?) = "tips_${branchId ?: "all"}"

        private const val MODEL_NAME = "gemini-3.5-flash"

        private val SYSTEM_INSTRUCTION = """
            You are SmartKitchen AI, an inventory, food-cost and food-waste advisor for restaurant owners.
            Using ONLY the data provided, give 4 to 6 specific, actionable recommendations that reduce
            food waste and food cost or protect revenue: ordering amounts, prep adjustments, using items before
            they expire, specials, storage, reorder timing. Always name the ingredient and use the numbers given
            (quantities, days, money, percentages). Put the biggest money-saving recommendation first.
            If there is little data, recommend which records to start keeping (usage, waste, expiry dates, daily sales).

            Respond with a JSON array only. Each item:
            {"title": "max 6 words", "detail": "1-2 sentences with numbers", "type": "ORDERING|WASTE|EXPIRY|PREP|SALES|OTHER"}
        """.trimIndent()
    }
}
