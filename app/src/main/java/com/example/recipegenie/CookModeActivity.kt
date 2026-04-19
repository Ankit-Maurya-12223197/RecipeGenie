// CookModeActivity.kt
package com.example.recipegenie

import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.recipegenie.data.Step
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CookModeActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "recipe_genie_prefs"
        private const val KEY_COOKED_DATE = "cooked_recipes_date"
        private const val KEY_COOKED_COUNT = "cooked_recipes_count"
        private const val KEY_STREAK_LAST_DATE = "cooked_streak_last_date"
        private const val KEY_STREAK_COUNT = "cooked_streak_count"
    }

    private var steps: List<Step> = emptyList()
    private var currentStepIndex = 0
    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var timeLeftMillis = 0L
    private var totalTimeMillis = 0L

    private lateinit var tvStepProgress: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvTimerLabel: TextView
    private lateinit var tvTip: TextView
    private lateinit var timerProgress: CircularProgressIndicator
    private lateinit var llStepDots: LinearLayout
    private lateinit var btnPrevious: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnExitCook: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cook_mode)

        // Keep screen on during cooking
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        steps = intent.getParcelableArrayListExtra<Step>("steps") ?: emptyList()

        bindViews()
        setupClickListeners()
        buildStepDots()
        displayStep(0)
    }

    private fun bindViews() {
        tvStepProgress = findViewById(R.id.tv_step_progress)
        tvInstruction = findViewById(R.id.tv_step_instruction)
        tvTimer = findViewById(R.id.tv_timer)
        tvTimerLabel = findViewById(R.id.tv_timer_label)
        tvTip = findViewById(R.id.tv_step_tip)
        timerProgress = findViewById(R.id.timer_progress)
        llStepDots = findViewById(R.id.ll_step_dots)
        btnPrevious = findViewById(R.id.btn_previous_step)
        btnNext = findViewById(R.id.btn_next_step)
        btnExitCook = findViewById(R.id.btn_exit_cook)
    }

    private fun setupClickListeners() {
        btnExitCook.setOnClickListener {
            countDownTimer?.cancel()
            finish()
        }

        // Tap timer circle to start/pause
        timerProgress.setOnClickListener { toggleTimer() }
        tvTimer.setOnClickListener { toggleTimer() }
        tvTimerLabel.setOnClickListener { toggleTimer() }

        btnPrevious.setOnClickListener {
            if (currentStepIndex > 0) {
                countDownTimer?.cancel()
                isTimerRunning = false
                displayStep(currentStepIndex - 1)
            }
        }

        btnNext.setOnClickListener {
            if (currentStepIndex < steps.size - 1) {
                countDownTimer?.cancel()
                isTimerRunning = false
                displayStep(currentStepIndex + 1)
            } else {
                // Last step — show completion
                showCompletionDialog()
            }
        }
    }

    private fun buildStepDots() {
        llStepDots.removeAllViews()
        steps.forEachIndexed { index, _ ->
            val dot = View(this).apply {
                val size = resources.getDimensionPixelSize(R.dimen.step_dot_size)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.step_dot_margin)
                }
                background = if (index == currentStepIndex)
                    resources.getDrawable(R.drawable.bg_dot_active, null)
                else
                    resources.getDrawable(R.drawable.bg_dot_inactive, null)
            }
            llStepDots.addView(dot)
        }
    }

    private fun displayStep(index: Int) {
        currentStepIndex = index
        val step = steps[index]

        tvStepProgress.text = "Step ${index + 1} of ${steps.size}"
        tvInstruction.text = step.instruction
        tvTip.text = step.tip ?: ""
        tvTip.visibility = if (step.tip.isNullOrEmpty()) View.GONE else View.VISIBLE

        btnPrevious.isEnabled = index > 0
        btnPrevious.alpha = if (index > 0) 1f else 0.4f
        btnNext.text = if (index == steps.size - 1) "Finish" else getString(R.string.next_step)

        // Setup timer
        val durationSeconds = step.durationSeconds ?: 0
        if (durationSeconds > 0) {
            totalTimeMillis = durationSeconds * 1000L
            timeLeftMillis = totalTimeMillis
            updateTimerDisplay()
            tvTimerLabel.text = getString(R.string.tap_to_start)
            timerProgress.visibility = View.VISIBLE
            timerProgress.setProgressCompat(100, false)
        } else {
            tvTimer.text = "--"
            tvTimerLabel.text = "No timer"
            timerProgress.setProgressCompat(0, false)
        }

        // Update step dots
        updateStepDots()
    }

    private fun toggleTimer() {
        if (isTimerRunning) pauseTimer() else startTimer()
    }

    private fun startTimer() {
        if (timeLeftMillis <= 0) return
        isTimerRunning = true
        tvTimerLabel.text = "Tap to pause"

        countDownTimer = object : CountDownTimer(timeLeftMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMillis = millisUntilFinished
                updateTimerDisplay()
                val progress = ((timeLeftMillis.toFloat() / totalTimeMillis) * 100).toInt()
                timerProgress.setProgressCompat(progress, false)
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onFinish() {
                isTimerRunning = false
                timeLeftMillis = 0
                tvTimer.text = "Done!"
                tvTimerLabel.text = "Timer complete"
                timerProgress.setProgressCompat(0, false)
                // Vibrate device
                val vibrator = getSystemService(android.os.Vibrator::class.java)
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(
                    longArrayOf(0, 400, 200, 400), -1))
            }
        }.start()
    }

    private fun pauseTimer() {
        isTimerRunning = false
        countDownTimer?.cancel()
        tvTimerLabel.text = "Tap to resume"
    }

    private fun updateTimerDisplay() {
        val minutes = (timeLeftMillis / 1000) / 60
        val seconds = (timeLeftMillis / 1000) % 60
        tvTimer.text = String.format("%d:%02d", minutes, seconds)
    }

    private fun updateStepDots() {
        for (i in 0 until llStepDots.childCount) {
            llStepDots.getChildAt(i)?.background = if (i == currentStepIndex)
                resources.getDrawable(R.drawable.bg_dot_active, null)
            else
                resources.getDrawable(R.drawable.bg_dot_inactive, null)
        }
    }

    private fun showCompletionDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Recipe complete!")
            .setMessage("Great job! You've finished cooking. Enjoy your meal 🎉")
            .setPositiveButton("Done") { _, _ ->
                recordCookedRecipe()
                finish()
            }
            .show()
    }

    private fun recordCookedRecipe() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val today = currentDateKey()
        val cookedDate = prefs.getString(KEY_COOKED_DATE, null)
        val currentCount = if (cookedDate == today) {
            prefs.getInt(KEY_COOKED_COUNT, 0)
        } else {
            0
        }
        val lastStreakDate = prefs.getString(KEY_STREAK_LAST_DATE, null)
        val currentStreak = prefs.getInt(KEY_STREAK_COUNT, 0)
        val nextStreak = when (daysBetween(lastStreakDate, today)) {
            0 -> currentStreak.coerceAtLeast(1)
            1 -> currentStreak + 1
            else -> 1
        }

        prefs.edit()
            .putString(KEY_COOKED_DATE, today)
            .putInt(KEY_COOKED_COUNT, currentCount + 1)
            .putString(KEY_STREAK_LAST_DATE, today)
            .putInt(KEY_STREAK_COUNT, nextStreak)
            .apply()
    }

    private fun currentDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun daysBetween(fromDate: String?, toDate: String): Int {
        if (fromDate.isNullOrBlank()) return Int.MAX_VALUE
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            val from = format.parse(fromDate) ?: return Int.MAX_VALUE
            val to = format.parse(toDate) ?: return Int.MAX_VALUE
            val diffMillis = startOfDay(to).time - startOfDay(from).time
            (diffMillis / (24 * 60 * 60 * 1000L)).toInt()
        } catch (_: ParseException) {
            Int.MAX_VALUE
        }
    }

    private fun startOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
