package com.example.recipegenie.data

import com.example.recipegenie.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object SpoonacularRepository {

    private const val BASE_URL = "https://api.spoonacular.com/recipes/random"
    private const val FIND_BY_INGREDIENTS_URL = "https://api.spoonacular.com/recipes/findByIngredients"
    private const val COMPLEX_SEARCH_URL = "https://api.spoonacular.com/recipes/complexSearch"
    private val client = OkHttpClient()

    suspend fun getHomeRecipes(category: String): List<Recipe> {
        val apiKey = BuildConfig.SPOONACULAR_API_KEY
        if (apiKey.isBlank()) return fallbackRecipes(category)

        return runCatching {
            val url = buildString {
                append(BASE_URL)
                append("?number=12")
                append("&addRecipeInformation=true")
                append("&fillIngredients=true")
                append("&instructionsRequired=true")
                if (category != "all") {
                    append("&tags=").append(category)
                }
                append("&apiKey=").append(apiKey)
            }

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Spoonacular request failed with ${response.code}")
                }
                parseRecipes(response.body?.string().orEmpty())
            }
        }.getOrElse {
            fallbackRecipes(category)
        }
    }

    fun fallbackRecipes(category: String): List<Recipe> {
        val allRecipes = SampleRecipeData.recipes
        if (category == "all") return allRecipes.take(10)
        return allRecipes.filter { it.category == category }.ifEmpty { allRecipes }.take(10)
    }

    suspend fun searchRecipesByIngredients(ingredients: List<String>): List<Recipe> {
        val normalized = ingredients.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (normalized.isEmpty()) return emptyList()

        val apiKey = BuildConfig.SPOONACULAR_API_KEY
        if (apiKey.isBlank()) return fallbackIngredientRecipes(normalized)

        return runCatching {
            val url = buildString {
                append(FIND_BY_INGREDIENTS_URL)
                append("?ingredients=").append(normalized.joinToString(","))
                append("&number=12")
                append("&ranking=1")
                append("&ignorePantry=true")
                append("&apiKey=").append(apiKey)
            }

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Ingredient search failed with ${response.code}")
                }
                parseIngredientSearch(response.body?.string().orEmpty())
            }
        }.getOrElse {
            fallbackIngredientRecipes(normalized)
        }
    }

    suspend fun findImageUrlByTitle(title: String): String {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return ""

        val apiKey = BuildConfig.SPOONACULAR_API_KEY
        if (apiKey.isBlank()) return ""

        return runCatching {
            val url = buildString {
                append(COMPLEX_SEARCH_URL)
                append("?query=").append(java.net.URLEncoder.encode(normalizedTitle, "UTF-8"))
                append("&number=1")
                append("&addRecipeInformation=true")
                append("&apiKey=").append(apiKey)
            }

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Title image search failed with ${response.code}")
                }
                parseFirstImageUrl(response.body?.string().orEmpty())
            }
        }.getOrDefault("")
    }

    private fun parseRecipes(json: String): List<Recipe> {
        val root = JSONObject(json)
        val items = root.optJSONArray("recipes") ?: return emptyList()

        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(
                    Recipe(
                        id = item.opt("id")?.toString().orEmpty(),
                        title = item.optString("title"),
                        imageUrl = item.optString("image"),
                        cookTimeMinutes = item.optInt("readyInMinutes", 0),
                        servings = item.optInt("servings", 2),
                        difficulty = difficultyFromTime(item.optInt("readyInMinutes", 0)),
                        rating = ratingFromScore(item.optDouble("spoonacularScore", 82.0)),
                        category = categoryFromDishTypes(item),
                        cuisine = parseCuisine(item),
                        description = item.optString("summary").stripHtml(),
                        ingredients = parseIngredients(item),
                        steps = parseSteps(item),
                        nutrition = Nutrition(0, 0, 0, 0)
                    )
                )
            }
        }
    }

    private fun parseIngredientSearch(json: String): List<Recipe> {
        val items = JSONArray(json)
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val used = item.optInt("usedIngredientCount", 0)
                val missed = item.optInt("missedIngredientCount", 0)
                add(
                    Recipe(
                        id = item.opt("id")?.toString().orEmpty(),
                        title = item.optString("title"),
                        imageUrl = item.optString("image"),
                        cookTimeMinutes = 20 + (missed * 5),
                        servings = 2,
                        difficulty = when {
                            missed == 0 -> "Easy"
                            missed <= 2 -> "Medium"
                            else -> "Hard"
                        },
                        rating = (4.2f + (used * 0.1f)).coerceAtMost(4.9f),
                        usedIngredientCount = used,
                        missedIngredientCount = missed
                    )
                )
            }
        }
    }

    private fun parseFirstImageUrl(json: String): String {
        val root = JSONObject(json)
        val results = root.optJSONArray("results") ?: return ""
        val first = results.optJSONObject(0) ?: return ""
        return first.optString("image")
    }

    private fun parseCuisine(item: JSONObject): String {
        val cuisines = item.optJSONArray("cuisines") ?: return ""
        return buildList {
            for (i in 0 until cuisines.length()) {
                add(cuisines.optString(i))
            }
        }.filter { it.isNotBlank() }.joinToString()
    }

    private fun parseIngredients(item: JSONObject): List<Ingredient> {
        val array = item.optJSONArray("extendedIngredients") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val ingredient = array.optJSONObject(i) ?: continue
                val measures = ingredient.optJSONObject("measures")
                val metric = measures?.optJSONObject("metric")
                val amountValue = metric?.opt("amount")?.toString()?.takeIf { it.isNotBlank() }
                val unit = metric?.optString("unitShort").orEmpty()
                val amount = when {
                    !amountValue.isNullOrBlank() && unit.isNotBlank() -> "$amountValue $unit"
                    !amountValue.isNullOrBlank() -> amountValue
                    else -> ingredient.optString("original")
                }

                add(
                    Ingredient(
                        name = ingredient.optString("nameClean").ifBlank {
                            ingredient.optString("name")
                        },
                        amount = amount
                    )
                )
            }
        }
    }

    private fun parseSteps(item: JSONObject): List<Step> {
        val instructions = item.optJSONArray("analyzedInstructions") ?: return defaultSteps(item)
        if (instructions.length() == 0) return defaultSteps(item)

        val firstInstruction = instructions.optJSONObject(0) ?: return defaultSteps(item)
        val steps = firstInstruction.optJSONArray("steps") ?: return defaultSteps(item)
        if (steps.length() == 0) return defaultSteps(item)

        return buildList {
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                val instructionText = step.optString("step")
                add(
                    Step(
                        stepNumber = step.optInt("number", i + 1),
                        instruction = instructionText,
                        tip = buildStepTip(step),
                        durationSeconds = parseStepDurationSeconds(step, instructionText)
                    )
                )
            }
        }
    }

    private fun defaultSteps(item: JSONObject): List<Step> {
        val title = item.optString("title").ifBlank { "this recipe" }
        return listOf(
            Step(
                stepNumber = 1,
                instruction = "Prepare the ingredients for $title.",
                tip = "Measure and keep everything ready before you start cooking.",
                durationSeconds = 5 * 60
            ),
            Step(
                stepNumber = 2,
                instruction = "Cook everything until the dish is ready to serve.",
                tip = "Adjust the heat as needed and stir occasionally.",
                durationSeconds = 12 * 60
            ),
            Step(
                stepNumber = 3,
                instruction = "Plate and enjoy while warm.",
                tip = "Let the dish rest briefly before serving.",
                durationSeconds = 2 * 60
            )
        )
    }

    private fun parseStepDurationSeconds(step: JSONObject, instruction: String): Int {
        val length = step.optJSONObject("length")
        val explicitDuration = length?.let { convertToSeconds(it.optDouble("number"), it.optString("unit")) }
        return explicitDuration?.takeIf { it > 0 } ?: estimateDurationSeconds(instruction)
    }

    private fun convertToSeconds(amount: Double, unit: String): Int? {
        if (amount <= 0.0) return null
        val normalizedUnit = unit.lowercase()
        val seconds = when {
            "second" in normalizedUnit -> amount
            "minute" in normalizedUnit -> amount * 60
            "hour" in normalizedUnit -> amount * 3600
            else -> return null
        }
        return seconds.toInt().coerceAtLeast(30)
    }

    private fun estimateDurationSeconds(instruction: String): Int {
        val text = instruction.lowercase()
        val numberMatch = Regex("(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val minutesFromText = when {
            Regex("\\b(\\d+)\\s*(hour|hours|hr|hrs)\\b").containsMatchIn(text) ->
                (numberMatch ?: 1) * 60
            Regex("\\b(\\d+)\\s*(minute|minutes|min|mins)\\b").containsMatchIn(text) ->
                numberMatch ?: 5
            Regex("\\b(\\d+)\\s*(second|seconds|sec|secs)\\b").containsMatchIn(text) ->
                ((numberMatch ?: 30) / 60).coerceAtLeast(1)
            "marinate" in text || "rest" in text || "chill" in text || "refrigerate" in text -> 15
            "bake" in text || "roast" in text || "simmer" in text || "boil" in text -> 12
            "saute" in text || "sauté" in text || "fry" in text || "cook" in text -> 6
            "mix" in text || "stir" in text || "whisk" in text || "combine" in text -> 3
            "chop" in text || "slice" in text || "prep" in text || "prepare" in text -> 4
            else -> 5
        }
        return (minutesFromText.coerceAtLeast(1)) * 60
    }

    private fun buildStepTip(step: JSONObject): String? {
        val equipment = step.optJSONArray("equipment")
        if (equipment != null && equipment.length() > 0) {
            val firstTool = equipment.optJSONObject(0)?.optString("name").orEmpty()
            if (firstTool.isNotBlank()) {
                return "Use a $firstTool for best results."
            }
        }
        val ingredients = step.optJSONArray("ingredients")
        if (ingredients != null && ingredients.length() > 0) {
            val firstIngredient = ingredients.optJSONObject(0)?.optString("name").orEmpty()
            if (firstIngredient.isNotBlank()) {
                return "Keep an eye on the $firstIngredient while cooking."
            }
        }
        return null
    }

    private fun difficultyFromTime(minutes: Int): String = when {
        minutes <= 20 -> "Easy"
        minutes <= 40 -> "Medium"
        else -> "Hard"
    }

    private fun ratingFromScore(score: Double): Float {
        val normalized = (score / 20.0).coerceIn(3.5, 5.0)
        return String.format("%.1f", normalized).toFloat()
    }

    private fun categoryFromDishTypes(item: JSONObject): String {
        val dishTypes = item.optJSONArray("dishTypes") ?: return "all"
        val values = buildList {
            for (i in 0 until dishTypes.length()) {
                add(dishTypes.optString(i).lowercase())
            }
        }
        return when {
            "breakfast" in values -> "breakfast"
            "lunch" in values -> "lunch"
            "dinner" in values -> "dinner"
            "snack" in values || "snacks" in values -> "snacks"
            "dessert" in values || "desserts" in values -> "desserts"
            else -> "all"
        }
    }

    private fun String.stripHtml(): String =
        replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun fallbackIngredientRecipes(ingredients: List<String>): List<Recipe> {
        val matches = SampleRecipeData.recipes.mapNotNull { recipe ->
            val recipeIngredients = recipe.ingredients.orEmpty().map { it.name.lowercase() }
            val usedCount = ingredients.count { ingredient ->
                recipeIngredients.any { recipeIngredient ->
                    recipeIngredient.contains(ingredient) || ingredient.contains(recipeIngredient)
                }
            }
            if (usedCount == 0) {
                null
            } else {
                recipe.copy(
                    usedIngredientCount = usedCount,
                    missedIngredientCount = (recipeIngredients.size - usedCount).coerceAtLeast(0)
                )
            }
        }

        return matches.sortedWith(
            compareByDescending<Recipe> { it.usedIngredientCount }
                .thenBy { it.missedIngredientCount }
        ).take(10)
    }
}
