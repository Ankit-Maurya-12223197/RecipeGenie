// AiChefActivity.kt
package com.example.recipegenie

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipegenie.adapters.ChatAdapter
import com.example.recipegenie.data.ChatMessage
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AiChefActivity : AppCompatActivity() {

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: TextInputEditText
    private lateinit var fabSend: FloatingActionButton
    private lateinit var llTyping: LinearLayout
    private lateinit var chipRecipeContext: Chip
    private lateinit var btnBackChat: View
    private lateinit var llSuggestions: View
    private lateinit var chipSubstitute: Chip
    private lateinit var chipLessSpicy: Chip
    private lateinit var chipNutrition: Chip
    private lateinit var chipDouble: Chip

    private val httpClient = OkHttpClient()
    private var recipeTitle = ""
    private var recipeId = ""

    // Gemini API endpoint — store your key in local.properties, never hardcode
    private val GEMINI_API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chef)

        recipeId = intent.getStringExtra("recipe_id") ?: ""
        recipeTitle = intent.getStringExtra("recipe_title") ?: "this recipe"

        bindViews()
        setupRecyclerView()
        setupClickListeners()
        setupSuggestionChips()
        sendWelcomeMessage()
    }

    private fun bindViews() {
        rvChat = findViewById(R.id.rv_chat)
        etMessage = findViewById(R.id.et_message)
        fabSend = findViewById(R.id.fab_send)
        llTyping = findViewById(R.id.ll_typing)
        chipRecipeContext = findViewById(R.id.chip_recipe_context)
        btnBackChat = findViewById(R.id.btn_back_chat)
        llSuggestions = findViewById(R.id.ll_suggestions)
        chipSubstitute = findViewById(R.id.chip_substitute)
        chipLessSpicy = findViewById(R.id.chip_less_spicy)
        chipNutrition = findViewById(R.id.chip_nutrition)
        chipDouble = findViewById(R.id.chip_double)
        chipRecipeContext.text = recipeTitle
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        rvChat.apply {
            layoutManager = LinearLayoutManager(this@AiChefActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupClickListeners() {
        btnBackChat.setOnClickListener { finish() }
        fabSend.setOnClickListener { sendMessage() }
        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
    }

    private fun setupSuggestionChips() {
        val suggestions = mapOf(
            chipSubstitute to "What can I substitute for cream in $recipeTitle?",
            chipLessSpicy to "How do I make $recipeTitle less spicy?",
            chipNutrition to "What is the nutritional breakdown of $recipeTitle?",
            chipDouble to "How do I double the recipe for $recipeTitle?"
        )
        suggestions.forEach { (chip, question) ->
            chip.setOnClickListener {
                etMessage.setText(question)
                sendMessage()
            }
        }
    }

    private fun sendWelcomeMessage() {
        addAiMessage(
            "Hi! I'm your AI Chef \uD83D\uDC68\u200D\uD83C\uDF73 I'm here to help with $recipeTitle.\n\n" +
                    "Ask me about substitutions, spice adjustments, nutrition info, or scaling the recipe!"
        )
    }

    private fun sendMessage() {
        val text = etMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        addUserMessage(text)
        etMessage.setText("")
        hideKeyboard()
        llSuggestions.visibility = View.GONE
        showTyping(true)
        callGeminiApi(text)
    }

    private fun addUserMessage(text: String) {
        messages.add(ChatMessage(text = text, isFromAi = false))
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    private fun addAiMessage(text: String) {
        messages.add(ChatMessage(text = text, isFromAi = true))
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    private fun callGeminiApi(userMessage: String) {
        // IMPORTANT: Store GEMINI_API_KEY in local.properties and expose via BuildConfig
        // local.properties  →  GEMINI_API_KEY=your_key_here
        // build.gradle (app) →  buildConfigField "String", "GEMINI_API_KEY", "\"${localProperties['GEMINI_API_KEY']}\""
        val apiKey = BuildConfig.GEMINI_API_KEY

        val contentsArray = JSONArray()

        // System persona as opening turns
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text",
                "You are a friendly expert AI Chef. The user is cooking: $recipeTitle. " +
                        "Give concise practical advice. Use bullet points when listing options. " +
                        "Keep responses under 120 words unless detail is needed."
            )))
        })
        contentsArray.put(JSONObject().apply {
            put("role", "model")
            put("parts", JSONArray().put(JSONObject().put("text",
                "Got it! I'm ready to help with $recipeTitle.")))
        })

        // Last 6 messages for context
        messages.takeLast(6).forEach { msg ->
            contentsArray.put(JSONObject().apply {
                put("role", if (msg.isFromAi) "model" else "user")
                put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            })
        }

        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
        })

        val body = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 300)
            })
        }

        val request = Request.Builder()
            .url("$GEMINI_API_URL?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showTyping(false)
                    addAiMessage("Couldn't connect. Please check your internet and try again.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    showTyping(false)
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val aiText = JSONObject(responseBody)
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                            addAiMessage(aiText)
                        } catch (e: Exception) {
                            addAiMessage("Couldn't read the response. Please try again.")
                        }
                    } else {
                        addAiMessage("Something went wrong (${response.code}). Please try again.")
                    }
                }
            }
        })
    }

    private fun showTyping(show: Boolean) {
        llTyping.visibility = if (show) View.VISIBLE else View.GONE
        fabSend.isEnabled = !show
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(this, InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(etMessage.windowToken, 0)
    }
}
