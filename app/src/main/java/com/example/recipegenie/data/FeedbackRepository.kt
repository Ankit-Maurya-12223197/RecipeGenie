package com.example.recipegenie.data

import com.example.recipegenie.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object FeedbackRepository {

    private const val RESEND_URL = "https://api.resend.com/emails"
    private const val FROM_EMAIL = "onboarding@resend.dev"
    private const val TO_EMAIL = "ankitpddu638631@gmail.com"

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    fun sendFeedback(
        userName: String,
        userEmail: String,
        feedback: String
    ): Result<Unit> {
        val apiKey = BuildConfig.RESEND_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Resend API key is missing"))
        }

        return runCatching {
            val body = JSONObject().apply {
                put("from", FROM_EMAIL)
                put("to", JSONArray().put(TO_EMAIL))
                put("subject", "RecipeGenie Feedback")
                put(
                    "text",
                    buildString {
                        appendLine("Feedback from RecipeGenie")
                        appendLine()
                        appendLine("Name: ${userName.ifBlank { "Unknown User" }}")
                        appendLine("Email: ${userEmail.ifBlank { "Not provided" }}")
                        appendLine()
                        appendLine("Message:")
                        append(feedback)
                    }
                )
            }

            val request = Request.Builder()
                .url(RESEND_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    error("Resend request failed (${response.code}): $errorBody")
                }
            }
        }
    }
}
