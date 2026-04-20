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

    private suspend fun expandRecipeIdea(
        ingredients: List<String>,
        recipeIdea: String,
        index: Int,
        apiKey: String
    ): Recipe {
        val prompt = """
        Create exactly one complete recipe for: $recipeIdea
        Available ingredients: ${ingredients.joinToString(", ")}

        Return ONLY valid JSON.
        Do not use markdown.
        Do not add explanation.

        Use this exact JSON structure:
        {
          "title": "string",
          "description": "string",
          "cookTimeMinutes": 20,
          "servings": 2,
          "difficulty": "Easy",
          "rating": 4.5,
          "category": "dinner",
          "cuisine": "Indian",
          "ingredients": [
            {
              "name": "Paneer",
              "amount": "200g",
              "isAvailable": true
            }
          ],
          "steps": [
            {
              "stepNumber": 1,
              "instruction": "Heat oil in a pan",
              "tip": "Use medium flame",
              "durationSeconds": 60
            }
          ],
          "nutrition": {
            "calories": 320,
            "protein": 20,
            "carbs": 18,
            "fat": 14,
            "fiber": 5
          }
        }

        Rules:
        - Keep recipe realistic.
        - Mark ingredient `isAvailable=true` only if it comes from available ingredients.
        - You may add 1-2 missing ingredients with false.
        - Add 4-6 steps.
    """.trimIndent()

        val json = callGemini(prompt, apiKey)
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val recipe = parseRecipeJson(json, index)
            ?: error("Gemini returned invalid recipe JSON")
        val imageUrl = SpoonacularRepository.findImageUrlByTitle(recipe.title)
        return recipe.copy(imageUrl = imageUrl)
    }

    private fun parseRecipeJson(jsonText: String, index: Int): Recipe? {
        return try {
            val obj = JSONObject(jsonText)

            val ingredients = obj.optJSONArray("ingredients")?.let { array ->
                List(array.length()) { i ->
                    val ing = array.getJSONObject(i)
                    Ingredient(
                        name = ing.optString("name"),
                        amount = ing.optString("amount"),
                        isAvailable = ing.optBoolean("isAvailable", true)
                    )
                }
            } ?: emptyList()

            val steps = obj.optJSONArray("steps")?.let { array ->
                List(array.length()) { i ->
                    val step = array.getJSONObject(i)
                    Step(
                        stepNumber = step.optInt("stepNumber", i + 1),
                        instruction = step.optString("instruction"),
                        tip = step.optString("tip").takeIf { it.isNotBlank() },
                        durationSeconds = step.optInt("durationSeconds").takeIf { it > 0 }
                    )
                }
            } ?: emptyList()

            val nutritionObj = obj.optJSONObject("nutrition")
            val nutrition = nutritionObj?.let {
                Nutrition(
                    calories = it.optInt("calories"),
                    protein = it.optInt("protein"),
                    carbs = it.optInt("carbs"),
                    fat = it.optInt("fat"),
                    fiber = it.optInt("fiber")
                )
            }

            val usedCount = ingredients.count { it.isAvailable }
            val missedCount = ingredients.size - usedCount

            Recipe(
                id = "ai_${UUID.randomUUID()}_$index",
                title = obj.optString("title"),
                imageUrl = "",
                cookTimeMinutes = obj.optInt("cookTimeMinutes", 20),
                servings = obj.optInt("servings", 2),
                difficulty = obj.optString("difficulty", "Medium"),
                rating = obj.optDouble("rating", 4.5).toFloat(),
                category = obj.optString("category", "all"),
                cuisine = obj.optString("cuisine"),
                description = obj.optString("description"),
                ingredients = ingredients,
                steps = steps,
                nutrition = nutrition,
                usedIngredientCount = usedCount,
                missedIngredientCount = missedCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseRecipeJson failed", e)
            null
        }
    }

    private fun callGemini(prompt: String, apiKey: String): String {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 2500)
                put("responseMimeType", "application/json")
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

}
