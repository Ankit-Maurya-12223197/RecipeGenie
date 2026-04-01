package com.example.recipegenie.data

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
            val ideas = generateRecipeIdeas(normalized, apiKey)
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

                if (combinedRecipes.size < 2) error("Gemini returned too few recipes")
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
        return text.lines()
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotBlank() }
            .distinct()
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

        val steps = listOf(
            Step(1, "Prep the ingredients for $title and keep them ready on the counter.", "Measure everything before starting.", 180),
            Step(2, "Heat oil in a pan and cook the main ingredients until fragrant and lightly colored.", "Stir to avoid sticking.", 420),
            Step(3, "Add seasoning and combine everything until the flavors come together.", "Taste and adjust salt if needed.", 300),
            Step(4, "Finish $title, plate it neatly, and serve while warm.", "Garnish before serving for better flavor.", 120)
        )

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

    private fun List<String>.valueAfter(prefix: String): String =
        firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            .orEmpty()
}
