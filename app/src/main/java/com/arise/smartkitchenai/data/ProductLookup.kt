package com.arise.smartkitchenai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ProductInfo(val name: String, val category: String, val unit: String)

/** Looks up packaged products by barcode in the free Open Food Facts database. */
class ProductLookup {

    /** @return product info, or null if the barcode isn't in the database. Throws IOException on network errors. */
    suspend fun lookup(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        val code = URLEncoder.encode(barcode, "UTF-8")
        val url = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,brands,quantity,categories_tags")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            // Open Food Facts asks apps to identify themselves.
            setRequestProperty("User-Agent", "SmartKitchenAI/1.0 (Android)")
        }
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> return@withContext null
                else -> throw java.io.IOException("Lookup failed (HTTP ${conn.responseCode})")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optInt("status") != 1) return@withContext null
            val product = json.optJSONObject("product") ?: return@withContext null

            val name = product.optString("product_name").trim()
            if (name.isBlank()) return@withContext null
            val brand = product.optString("brands").substringBefore(',').trim()
            val size = product.optString("quantity").trim()
            val fullName = buildString {
                if (brand.isNotEmpty() && !name.contains(brand, ignoreCase = true)) append(brand).append(' ')
                append(name)
                if (size.isNotEmpty()) append(" (").append(size).append(')')
            }
            // Packaged items are counted per pack by default; the user can change it.
            ProductInfo(fullName, mapCategory(product.optJSONArray("categories_tags")), unit = "pcs")
        } finally {
            conn.disconnect()
        }
    }

    /** Maps Open Food Facts category tags (e.g. "en:dairies") to simple kitchen categories. */
    private fun mapCategory(tags: JSONArray?): String {
        if (tags == null) return ""
        val all = (0 until tags.length()).joinToString(" ") { tags.optString(it) }.lowercase()
        val rules = listOf(
            "Frozen" to listOf("frozen"),
            "Seafood" to listOf("seafood", "fishes", "fish"),
            "Meat" to listOf("meats", "poultr", "beef", "chicken"),
            "Dairy" to listOf("dairies", "milks", "cheeses", "yogurts", "butters", "creams"),
            "Bakery" to listOf("breads", "bakery", "pastries"),
            "Produce" to listOf("fresh-fruits", "fresh-vegetables", "fruits", "vegetables"),
            "Spices & sauces" to listOf("spices", "condiments", "sauces"),
            "Oils" to listOf("oils", "fats"),
            "Dry goods" to listOf("cereals", "pastas", "rices", "flours", "legumes", "sugars"),
            "Beverages" to listOf("beverages", "drinks", "juices", "waters"),
        )
        return rules.firstOrNull { (_, keys) -> keys.any { it in all } }?.first.orEmpty()
    }
}
