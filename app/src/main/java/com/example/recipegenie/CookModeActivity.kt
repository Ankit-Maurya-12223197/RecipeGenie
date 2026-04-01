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

class CookModeActivity : AppCompatActivity() {

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
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_exit_cook).setOnClickListener {
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
            timerProgress.visibility = View.VISIBLE
        } else {
            tvTimer.text = "--"
            tvTimerLabel.text = "No timer"
            timerProgress.progress = 0
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
                timerProgress.progress = progress
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onFinish() {
                isTimerRunning = false
                timeLeftMillis = 0
                tvTimer.text = "Done!"
                tvTimerLabel.text = "Timer complete"
                timerProgress.progress = 0
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
            .setPositiveButton("Done") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}