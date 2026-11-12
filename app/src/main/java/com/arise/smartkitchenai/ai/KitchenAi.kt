package com.arise.smartkitchenai.ai

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content

/** Gemini (via Firebase AI Logic) for money-saving recommendations. */
class KitchenAi(private val context: Context) {

    /** False until google-services.json is added and the app is rebuilt. */
    val isConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content { text(SYSTEM_INSTRUCTION) },
        )
    }

    /** Sends the kitchen data summary to Gemini and returns a list of recommendations. */
    suspend fun recommendations(kitchenSummary: String): List<String> {
        val text = model.generateContent(kitchenSummary).text.orEmpty()
        return parseTips(text)
    }

    private fun parseTips(text: String): List<String> {
        val tips = text.lines()
            .map { it.trim() }
            .filter { it.startsWith("-") || it.startsWith("•") || it.startsWith("*") }
            .map { it.trimStart('-', '•', '*', ' ').replace("**", "").trim() }
            .filter { it.isNotEmpty() }
        // Fall back to the raw text if the model ignored the list format.
        return tips.ifEmpty { listOfNotNull(text.trim().takeIf { it.isNotEmpty() }) }
    }

    private companion object {
        const val MODEL_NAME = "gemini-3.5-flash"

        val SYSTEM_INSTRUCTION = """
            You are SmartKitchen AI, an inventory and food-waste advisor for restaurant owners.
            Using ONLY the data provided, give 4 to 6 specific, actionable recommendations that reduce
            food waste and save money: ordering amounts, prep adjustments, using items before they expire,
            menu specials, storage, and reorder timing. Always name the ingredient and use the numbers given
            (quantities, days, money). Put the biggest money-saving recommendation first.
            If there is little data, say which records to start keeping (usage, waste, expiry dates).
            Output plain text only: one recommendation per line, each line starting with "- ".
            No markdown, no headings, no introduction or closing sentence.
        """.trimIndent()
    }
}
