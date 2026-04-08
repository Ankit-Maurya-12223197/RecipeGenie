package com.example.recipegenie.data

import android.util.Log
import com.example.recipegenie.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object GeminiRecipeRepository {

    private const val GEMINI_API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    private const val TAG = "GeminiRecipeRepo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun generateRecipesFromIngredients(ingredients: List<String>): List<Recipe> {
        val normalized = ingredients.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (normalized.isEmpty()) return emptyList()

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) error("Gemini API key is missing")

        return withContext(Dispatchers.IO) {
            val ideas = ensureMinimumIdeas(generateRecipeIdeas(normalized, apiKey), normalized)
            if (ideas.isEmpty()) error("Gemini returned no recipe ideas")

            coroutineScope {
                val expandedRecipes = ideas.take(5).mapIndexed { index, idea ->
                    async {
                        runCatching {
                            expandRecipeIdea(normalized, idea, index, apiKey)
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
                    .distinctBy { it.title.lowercase() }

                val combinedRecipes = (expandedRecipes + buildFallbackRecipesFromIdeas(
                    ingredients = normalized,
                    ideas = ideas,
                    existingTitles = expandedRecipes.map { it.title.lowercase() }
                )).distinctBy { it.title.lowercase() }

                combinedRecipes.take(5)
            }
        }
    }

    private fun generateRecipeIdeas(ingredients: List<String>, apiKey: String): List<String> {
        val prompt = """
            Suggest 5 distinct recipe ideas using these ingredients: ${ingredients.joinToString(", ")}.

            Return plain text only.
            Output exactly one recipe title per line.
            No numbering.
            No bullets.
            No explanation.
            Keep each title short and realistic.
        """.trimIndent()

        val text = callGemini(prompt, apiKey)
        Log.d(TAG, "Idea response:\n$text")
        return text.lines()
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun ensureMinimumIdeas(ideas: List<String>, ingredients: List<String>): List<String> {
        val combined = (ideas + buildLocalIdeaFallbacks(ingredients)).distinct()
        return combined.take(5)
    }

    private fun expandRecipeIdea(
        ingredients: List<String>,
        recipeIdea: String,
        index: Int,
        apiKey: String
    ): Recipe {
        val prompt = """
            Create one complete recipe for: $recipeIdea
            Available ingredients: ${ingredients.joinToString(", ")}.

            Return plain text only using this exact format:
            TITLE: recipe title
            DESCRIPTION: one short sentence
            COOK_TIME_MINUTES: integer
            SERVINGS: integer
            DIFFICULTY: Easy or Medium or Hard
            RATING: decimal from 4.0 to 4.9
            CATEGORY: breakfast or lunch or dinner or snacks or desserts or all
            CUISINE: cuisine name
            INGREDIENT: ingredient name | amount | yes/no
            INGREDIENT: ingredient name | amount | yes/no
            STEP: 1 | instruction sentence | tip sentence or none | duration seconds or 0
            STEP: 2 | instruction sentence | tip sentence or none | duration seconds or 0
            STEP: 3 | instruction sentence | tip sentence or none | duration seconds or 0
            STEP: 4 | instruction sentence | tip sentence or none | duration seconds or 0
            NUTRITION: calories | protein | carbs | fat | fiber

            Rules:
            - Use yes only for ingredients from the available list.
            - You may include 1 to 3 extra ingredients marked no.
            - Provide 4 to 8 ingredients.
            - Provide 4 to 6 specific steps.
            - Avoid generic wording.
            - Avoid quotes and avoid using the pipe character except as separator.
        """.trimIndent()

        val text = callGemini(prompt, apiKey)
        Log.d(TAG, "Expanded recipe for '$recipeIdea':\n$text")
        return parseRecipeBlock(text, index) ?: error("Gemini returned invalid recipe format")
    }

    private fun callGemini(prompt: String, apiKey: String): String {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.6)
                put("maxOutputTokens", 800)
            })
        }

        val request = Request.Builder()
            .url("$GEMINI_API_URL?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Gemini request failed with ${response.code}")
            return extractCandidateText(response.body?.string().orEmpty())
        }
    }

    private fun extractCandidateText(responseBody: String): String {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates") ?: return ""

        return buildString {
            for (candidateIndex in 0 until candidates.length()) {
                val candidate = candidates.optJSONObject(candidateIndex) ?: continue
                val parts = candidate.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?: continue
                for (partIndex in 0 until parts.length()) {
                    val text = parts.optJSONObject(partIndex)?.optString("text").orEmpty()
                    if (text.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(text.trim())
                    }
                }
            }
        }.trim()
    }

    private fun parseRecipeBlock(block: String, index: Int): Recipe? {
        val lines = block.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("```") }

        val title = lines.valueAfter("TITLE:").ifBlank { return null }
        val description = lines.valueAfter("DESCRIPTION:")
        val cookTime = lines.valueAfter("COOK_TIME_MINUTES:").toIntOrNull() ?: 20
        val servings = lines.valueAfter("SERVINGS:").toIntOrNull() ?: 2
        val difficulty = lines.valueAfter("DIFFICULTY:").ifBlank { "Medium" }
        val rating = lines.valueAfter("RATING:").toFloatOrNull() ?: 4.5f
        val category = lines.valueAfter("CATEGORY:").ifBlank { "all" }
        val cuisine = lines.valueAfter("CUISINE:")

        val ingredients = lines.filter { it.startsWith("INGREDIENT:") }.mapNotNull { line ->
            val parts = line.removePrefix("INGREDIENT:").split("|").map { it.trim() }
            if (parts.size < 3) return@mapNotNull null
            Ingredient(
                name = parts[0],
                amount = parts[1],
                isAvailable = parts[2].equals("yes", ignoreCase = true)
            )
        }

        val steps = lines.filter { it.startsWith("STEP:") }.mapNotNull { line ->
            val parts = line.removePrefix("STEP:").split("|").map { it.trim() }
            if (parts.size < 4) return@mapNotNull null
            Step(
                stepNumber = parts[0].toIntOrNull() ?: 1,
                instruction = parts[1],
                tip = parts[2].takeUnless { it.equals("none", ignoreCase = true) || it.isBlank() },
                durationSeconds = parts[3].toIntOrNull()?.takeIf { it > 0 }
            )
        }

        val nutritionParts = lines.valueAfter("NUTRITION:").split("|").map { it.trim() }
        val nutrition = if (nutritionParts.size >= 5) {
            Nutrition(
                calories = nutritionParts[0].toIntOrNull() ?: 0,
                protein = nutritionParts[1].toIntOrNull() ?: 0,
                carbs = nutritionParts[2].toIntOrNull() ?: 0,
                fat = nutritionParts[3].toIntOrNull() ?: 0,
                fiber = nutritionParts[4].toIntOrNull() ?: 0
            )
        } else null

        if (ingredients.size < 3 || steps.size < 3) return null

        val usedCount = ingredients.count { it.isAvailable }
        val missedCount = (ingredients.size - usedCount).coerceAtLeast(0)

        return Recipe(
            id = "ai_${UUID.randomUUID()}_$index",
            title = title,
            imageUrl = "",
            cookTimeMinutes = cookTime,
            servings = servings,
            difficulty = difficulty,
            rating = rating,
            category = category,
            cuisine = cuisine,
            description = description,
            ingredients = ingredients,
            steps = steps,
            nutrition = nutrition,
            usedIngredientCount = usedCount,
            missedIngredientCount = missedCount
        )
    }

    private fun buildFallbackRecipesFromIdeas(
        ingredients: List<String>,
        ideas: List<String>,
        existingTitles: List<String>
    ): List<Recipe> {
        return ideas
            .filter { it.lowercase() !in existingTitles }
            .take(3)
            .mapIndexed { index, title ->
                buildFallbackRecipe(title, ingredients, index)
            }
    }

    private fun buildLocalIdeaFallbacks(ingredients: List<String>): List<String> {
        val main = ingredients.take(2).joinToString(" ").replaceFirstChar { it.uppercase() }.ifBlank { "Mixed Veg" }
        return listOf(
            "$main Skillet",
            "$main Rice Bowl",
            "$main Masala Wrap",
            "$main Stir Fry"
        )
    }

    private fun buildFallbackRecipe(
        title: String,
        ingredients: List<String>,
        index: Int
    ): Recipe {
        val availableIngredients = ingredients.take(4).map {
            Ingredient(
                name = it.replaceFirstChar { char -> char.uppercase() },
                amount = "1 portion",
                isAvailable = true
            )
        }

        val missingIngredients = listOf(
            Ingredient("Salt", "to taste", false),
            Ingredient("Oil", "1 tbsp", false)
        )

        val allIngredients = (availableIngredients + missingIngredients).take(6)

        val steps = buildFallbackSteps(title, allIngredients)

        val usedCount = allIngredients.count { it.isAvailable }
        val missedCount = allIngredients.size - usedCount

        return Recipe(
            id = "ai_fallback_${UUID.randomUUID()}_$index",
            title = title,
            imageUrl = "",
            cookTimeMinutes = 18 + (index * 4),
            servings = 2,
            difficulty = if (index % 2 == 0) "Easy" else "Medium",
            rating = 4.3f + (index * 0.1f),
            category = inferCategoryFromTitle(title),
            cuisine = inferCuisineFromTitle(title),
            description = "A Gemini-inspired recipe idea built from your available ingredients.",
            ingredients = allIngredients,
            steps = steps,
            nutrition = Nutrition(
                calories = 320 + (index * 40),
                protein = 10 + index,
                carbs = 28 + (index * 3),
                fat = 14 + index,
                fiber = 5 + index
            ),
            usedIngredientCount = usedCount,
            missedIngredientCount = missedCount
        )
    }

    private fun inferCategoryFromTitle(title: String): String {
        val lower = title.lowercase()
        return when {
            "toast" in lower || "oats" in lower || "omelette" in lower || "breakfast" in lower -> "breakfast"
            "pasta" in lower || "curry" in lower || "rice" in lower || "dinner" in lower -> "dinner"
            "wrap" in lower || "sandwich" in lower || "bowl" in lower || "salad" in lower -> "lunch"
            "cake" in lower || "brownie" in lower || "dessert" in lower -> "desserts"
            else -> "all"
        }
    }

    private fun inferCuisineFromTitle(title: String): String {
        val lower = title.lowercase()
        return when {
            "masala" in lower || "paneer" in lower || "tikka" in lower -> "Indian"
            "pasta" in lower || "risotto" in lower -> "Italian"
            "fried rice" in lower || "noodle" in lower -> "Asian"
            else -> "Fusion"
        }
    }

    private fun buildFallbackSteps(title: String, ingredients: List<Ingredient>): List<Step> {
        val lowerTitle = title.lowercase()
        val primary = ingredients.firstOrNull()?.name ?: "the main ingredient"
        val secondary = ingredients.getOrNull(1)?.name ?: "the remaining ingredients"

        return when {
            "wrap" in lowerTitle || "roll" in lowerTitle -> listOf(
                Step(1, "Slice and prep $primary and $secondary so the filling cooks evenly.", "Keep the pieces bite-sized for easier rolling.", 180),
                Step(2, "Saute the filling in a pan until aromatic and lightly browned.", "Cook on medium heat so the filling stays juicy.", 360),
                Step(3, "Warm the wrap base and spread a little seasoning or sauce over it.", "A warm wrap folds without cracking.", 90),
                Step(4, "Add the filling, roll tightly, and toast briefly before serving.", "Slice diagonally for a cleaner presentation.", 150)
            )
            "bowl" in lowerTitle || "rice" in lowerTitle -> listOf(
                Step(1, "Prep $primary, $secondary, and the remaining ingredients before heating the pan.", "Keep wet and dry ingredients separate at first.", 180),
                Step(2, "Cook the vegetables or protein until lightly caramelized and flavorful.", "Avoid overcrowding the pan so everything browns.", 420),
                Step(3, "Add the base and seasonings, then toss until everything is evenly coated.", "Fold gently to keep the texture intact.", 240),
                Step(4, "Transfer to a bowl, garnish, and serve hot.", "A squeeze of lemon or herbs at the end brightens the dish.", 60)
            )
            "skillet" in lowerTitle || "stir fry" in lowerTitle -> listOf(
                Step(1, "Chop $primary and $secondary into even pieces for quick cooking.", "Uniform cuts help everything finish at the same time.", 180),
                Step(2, "Heat oil in a skillet and cook the aromatics until fragrant.", "Do not let the aromatics burn.", 120),
                Step(3, "Add the main ingredients and stir fry until tender with a little color.", "Keep the heat fairly high for better flavor.", 360),
                Step(4, "Season, toss once more, and serve immediately.", "Finish with a quick garnish for freshness.", 90)
            )
            else -> listOf(
                Step(1, "Prepare $primary, $secondary, and the rest of the ingredients before you start cooking.", "This makes the cooking process smoother.", 180),
                Step(2, "Cook the main ingredients until fragrant and lightly golden.", "Stir occasionally so nothing sticks.", 360),
                Step(3, "Add the remaining ingredients and simmer or toss until the flavors come together.", "Taste and adjust the seasoning at this stage.", 300),
                Step(4, "Plate $title and serve warm.", "Add a final garnish for extra texture and color.", 60)
            )
        }
    }

    private fun List<String>.valueAfter(prefix: String): String =
        firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            .orEmpty()
}
