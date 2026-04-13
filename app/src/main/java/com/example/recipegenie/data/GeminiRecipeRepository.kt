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

                if (expandedRecipes.isEmpty()) {
                    error("Gemini could not generate recipe details")
                }

                expandedRecipes.take(5)
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
            - Each step must be specific to this recipe title and ingredient list.
            - Mention real ingredients, cooking actions, or texture changes in the steps.
            - Do not write generic steps that could fit any recipe.
            - Avoid quotes and avoid using the pipe character except as separator.
        """.trimIndent()

        val text = callGemini(prompt, apiKey)
        Log.d(TAG, "Expanded recipe for '$recipeIdea':\n$text")
        return parseRecipeBlock(text, index)
            ?: repairAndParseRecipeBlock(text, recipeIdea, index, apiKey)
            ?: error("Gemini returned invalid recipe format")
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

        val ingredients = parseIngredients(lines)
        val steps = parseSteps(lines)

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

    private fun repairAndParseRecipeBlock(
        originalText: String,
        recipeIdea: String,
        index: Int,
        apiKey: String
    ): Recipe? {
        val repairPrompt = """
            Reformat this recipe into the exact structure below and keep the content recipe-specific for "$recipeIdea".

            TITLE: recipe title
            DESCRIPTION: one short sentence
            COOK_TIME_MINUTES: integer
            SERVINGS: integer
            DIFFICULTY: Easy or Medium or Hard
            RATING: decimal from 4.0 to 4.9
            CATEGORY: breakfast or lunch or dinner or snacks or desserts or all
            CUISINE: cuisine name
            INGREDIENT: ingredient name | amount | yes/no
            STEP: 1 | instruction sentence | tip sentence or none | duration seconds or 0
            NUTRITION: calories | protein | carbs | fat | fiber

            Original text:
            $originalText
        """.trimIndent()

        val repaired = callGemini(repairPrompt, apiKey)
        Log.d(TAG, "Repaired recipe for '$recipeIdea':\n$repaired")
        return parseRecipeBlock(repaired, index)
    }

    private fun parseIngredients(lines: List<String>): List<Ingredient> {
        return lines.mapNotNull { line ->
            if (!line.startsWith("INGREDIENT:", ignoreCase = true)) return@mapNotNull null
            val content = line.substringAfter(":").trim()
            val parts = content.split("|").map { it.trim() }
            when {
                parts.size >= 3 -> Ingredient(
                    name = parts[0],
                    amount = parts[1],
                    isAvailable = parts[2].equals("yes", ignoreCase = true)
                )
                parts.size == 2 -> Ingredient(
                    name = parts[0],
                    amount = parts[1],
                    isAvailable = true
                )
                parts.size == 1 && parts[0].isNotBlank() -> Ingredient(
                    name = parts[0],
                    amount = "to taste",
                    isAvailable = true
                )
                else -> null
            }
        }
    }

    private fun parseSteps(lines: List<String>): List<Step> {
        val explicitSteps = lines.mapNotNull { line ->
            if (!line.startsWith("STEP:", ignoreCase = true)) return@mapNotNull null
            val content = line.substringAfter(":").trim()
            val parts = content.split("|").map { it.trim() }
            when {
                parts.size >= 4 -> Step(
                    stepNumber = parts[0].toIntOrNull() ?: 1,
                    instruction = parts[1],
                    tip = parts[2].takeUnless { it.equals("none", ignoreCase = true) || it.isBlank() },
                    durationSeconds = parts[3].toIntOrNull()?.takeIf { it > 0 }
                )
                parts.size == 3 -> Step(
                    stepNumber = parts[0].toIntOrNull() ?: 1,
                    instruction = parts[1],
                    tip = parts[2].takeUnless { it.equals("none", ignoreCase = true) || it.isBlank() },
                    durationSeconds = null
                )
                parts.size == 2 -> Step(
                    stepNumber = parts[0].toIntOrNull() ?: 1,
                    instruction = parts[1],
                    tip = null,
                    durationSeconds = null
                )
                parts.size == 1 && parts[0].isNotBlank() -> {
                    val match = Regex("""^(\d+)[\).\-\s]+(.+)$""").find(parts[0])
                    if (match != null) {
                        Step(
                            stepNumber = match.groupValues[1].toIntOrNull() ?: 1,
                            instruction = match.groupValues[2].trim(),
                            tip = null,
                            durationSeconds = null
                        )
                    } else {
                        Step(
                            stepNumber = 1,
                            instruction = parts[0],
                            tip = null,
                            durationSeconds = null
                        )
                    }
                }
                else -> null
            }
        }

        if (explicitSteps.size >= 3) {
            return explicitSteps.mapIndexed { index, step ->
                step.copy(stepNumber = if (step.stepNumber > 0) step.stepNumber else index + 1)
            }
        }

        val numberedLines = lines.mapNotNull { line ->
            val match = Regex("""^(\d+)[\).\-\s]+(.+)$""").find(line) ?: return@mapNotNull null
            Step(
                stepNumber = match.groupValues[1].toIntOrNull() ?: 1,
                instruction = match.groupValues[2].trim(),
                tip = null,
                durationSeconds = null
            )
        }

        return numberedLines
    }

    private fun List<String>.valueAfter(prefix: String): String =
        firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            .orEmpty()
}
