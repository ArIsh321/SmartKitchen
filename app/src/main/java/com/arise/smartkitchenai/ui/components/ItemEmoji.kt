package com.arise.smartkitchenai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Picks an emoji for an ingredient from its name, then its category. Used instead of product photos. */
fun ingredientEmoji(name: String, category: String = ""): String {
    val n = name.lowercase()
    NAME_RULES.firstOrNull { (_, keys) -> keys.any { it in n } }?.let { return it.first }
    val c = category.lowercase()
    CATEGORY_RULES.firstOrNull { (_, keys) -> keys.any { it in c } }?.let { return it.first }
    return "📦"
}

private val NAME_RULES = listOf(
    "🍅" to listOf("tomato"),
    "🥑" to listOf("avocado"),
    "🌿" to listOf("basil", "parsley", "coriander", "cilantro", "mint", "herb", "thyme", "rosemary"),
    "🥬" to listOf("lettuce", "spinach", "cabbage", "kale", "salad"),
    "🧅" to listOf("onion", "shallot"),
    "🧄" to listOf("garlic"),
    "🥔" to listOf("potato"),
    "🥕" to listOf("carrot"),
    "🌶️" to listOf("chili", "chilli", "pepper"),
    "🍋" to listOf("lemon", "lime"),
    "🍎" to listOf("apple"),
    "🍌" to listOf("banana"),
    "🍄" to listOf("mushroom"),
    "🥒" to listOf("cucumber", "zucchini"),
    "🍗" to listOf("chicken", "poultry", "turkey"),
    "🥩" to listOf("beef", "steak", "lamb", "mutton", "ribeye", "veal"),
    "🥓" to listOf("bacon", "pork", "ham"),
    "🐟" to listOf("fish", "salmon", "tuna", "cod", "hammour"),
    "🦐" to listOf("shrimp", "prawn"),
    "🥚" to listOf("egg"),
    "🥛" to listOf("milk", "cream"),
    "🧀" to listOf("cheese", "mozzarella", "parmesan", "feta"),
    "🧈" to listOf("butter"),
    "🫒" to listOf("olive oil", "oil", "olive"),
    "🍚" to listOf("rice"),
    "🍝" to listOf("pasta", "spaghetti", "noodle"),
    "🍞" to listOf("bread", "bun", "flour"),
    "🧂" to listOf("salt", "spice", "cumin", "paprika"),
    "🍯" to listOf("honey", "sugar", "syrup"),
    "🥤" to listOf("juice", "soda", "cola", "water"),
    "☕" to listOf("coffee", "tea"),
)

private val CATEGORY_RULES = listOf(
    "🥬" to listOf("produce", "vegetable", "fruit"),
    "🥩" to listOf("meat"),
    "🐟" to listOf("seafood", "fish"),
    "🧀" to listOf("dairy"),
    "🍞" to listOf("bakery"),
    "🧊" to listOf("frozen"),
    "🧂" to listOf("spice", "sauce", "condiment"),
    "🫒" to listOf("oil"),
    "🍚" to listOf("dry"),
    "🥤" to listOf("beverage", "drink"),
)

/** Rounded tile showing the item's emoji, in place of a product photo. */
@Composable
fun ItemEmojiTile(name: String, modifier: Modifier = Modifier, category: String = "", size: Dp = 52.dp) {
    Box(
        modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(ingredientEmoji(name, category), fontSize = (size.value * 0.5f).sp)
    }
}
