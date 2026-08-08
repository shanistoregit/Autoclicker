package com.shanistore.autoclicker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.shanistore.autoclicker.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var recordingSession: RecordingSession? = null
    private var automationEngine: AutomationEngine? = null
    private var loadedSequence: ClickSequence? = null
    private val logBuilder = StringBuilder()

    // Action to run automatically once the capture service is confirmed
    // alive after requesting permission. Lets Start Recording / Start
    // Replay request permission and proceed in one tap.
    private var pendingActionAfterCapturePermission: (() -> Unit)? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(intent)
            log("Screen capture permission granted. Waiting for capture service to start...")
            updateStatus("Starting capture...")
            waitForCaptureServiceThenRun()
        } else {
            log("Screen capture permission denied.")
            pendingActionAfterCapturePermission = null
            Toast.makeText(this, "Screen capture permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    private val loadFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            val seq = ClickSequence.loadFromFile(this, uri)
            if (seq != null) {
                loadedSequence = seq
                binding.loadedFileText.text = "Loaded: ${seq.name} (${seq.steps.size} steps)"
                log("Loaded sequence '${seq.name}' with ${seq.steps.size} steps.")
            } else {
                Toast.makeText(this, "Failed to parse .autoclick file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!OpenCVLoader.initLocal()) {
            log("Warning: OpenCV failed to initialize locally.")
        } else {
            log("OpenCV initialized.")
        }

        binding.logText.movementMethod = ScrollingMovementMethod()

        binding.btnEnableAccessibility.setOnClickListener { openAccessibilitySettings() }

        // Still available on its own for pre-granting, but Start
        // Recording / Start Replay will also trigger it automatically.
        binding.btnGrantCapture.setOnClickListener { requestScreenCapture(null) }

        binding.btnStartRecording.setOnClickListener { onStartRecordingClicked() }
        binding.btnStopRecording.setOnClickListener { stopRecording() }

        binding.btnLoadSequence.setOnClickListener { pickSequenceFile() }
        binding.btnStartReplay.setOnClickListener { onStartReplayClicked() }
        binding.btnStopReplay.setOnClickListener { stopReplay() }
    }

    // ---------- Permissions ----------

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Enable 'Shani AutoClicker' in the list, then return here.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Could not open accessibility settings.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Requests screen capture permission. If [thenRun] is provided, it runs
     * automatically once the capture service is confirmed alive -- this is
     * what lets "Start Recording" / "Start Replay" request permission and
     * proceed in a single tap, instead of needing a separate grant step first.
     */
    private fun requestScreenCapture(thenRun: (() -> Unit)?) {
        pendingActionAfterCapturePermission = thenRun
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    /**
     * Polls briefly for ScreenCaptureService.instance to become non-null
     * after asking the system to start it (service creation is async, and
     * instance being null right after the grant is expected for a moment).
     * Runs the pending action once ready, or logs clearly if the service
     * never comes up (meaning it crashed on startup).
     */
    private fun waitForCaptureServiceThenRun(attemptsLeft: Int = 15) {
        if (ScreenCaptureService.instance != null) {
            log("Capture service is ready.")
            updateStatus("Capture ready")
            val action = pendingActionAfterCapturePermission
            pendingActionAfterCapturePermission = null
            action?.invoke()
            return
        }
        if (attemptsLeft <= 0) {
            log("Capture service never came up after permission was granted -- it likely crashed on startup.")
            updateStatus("Capture failed")
            pendingActionAfterCapturePermission = null
            Toast.makeText(this, "Screen capture failed to start. Try again.", Toast.LENGTH_LONG).show()
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            waitForCaptureServiceThenRun(attemptsLeft - 1)
        }, 200)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return ClickAccessibilityService.instance != null
    }

    // ---------- Recording ----------

    private fun onStartRecordingClicked() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable the Accessibility Service first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant 'Display over other apps' permission, then tap Start Recording again.", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        if (ScreenCaptureService.instance == null) {
            log("Requesting screen capture permission before recording...")
            requestScreenCapture { beginRecording() }
            return
        }

        beginRecording()
    }

    private fun beginRecording() {
        val name = binding.sequenceNameInput.text.toString().let {
            if (TextUtils.isEmpty(it)) "sequence_${System.currentTimeMillis()}" else it
        }

        binding.btnStartRecording.isEnabled = false
        binding.btnStopRecording.isEnabled = true
        updateStatus("Countdown...")

        recordingSession = RecordingSession(
            context = this,
            sequenceName = name,
            countdownCallback = { secondsLeft ->
                runOnUiThread {
                    updateStatus(if (secondsLeft > 0) "Starting in $secondsLeft..." else "Recording!")
                }
            },
            tapRecordedCallback = { step ->
                runOnUiThread { log("Recorded step: ${step.label} at (${step.recordedX},${step.recordedY})") }
            },
            logCallback = { msg -> runOnUiThread { log(msg) } }
        )
        recordingSession?.startWithCountdown()
    }

    private fun stopRecording() {
        val session = recordingSession ?: return
        val sequence = session.stop()
        loadedSequence = sequence

        binding.btnStartRecording.isEnabled = true
        binding.btnStopRecording.isEnabled = false
        updateStatus("Idle")

        if (sequence.steps.isEmpty()) {
            log("No steps recorded.")
            return
        }

        val savedFile = sequence.saveToInternalStorage(this, sequence.name)
        binding.loadedFileText.text = "Saved: ${savedFile.name} (${sequence.steps.size} steps)"
        log("Sequence saved to ${savedFile.absolutePath}")
        Toast.makeText(this, "Saved ${sequence.steps.size} steps to ${savedFile.name}", Toast.LENGTH_LONG).show()

        recordingSession = null
    }

    // ---------- Replay ----------

    private fun pickSequenceFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        loadFileLauncher.launch(intent)
    }

    private fun onStartReplayClicked() {
        val sequence = loadedSequence
        if (sequence == null || sequence.steps.isEmpty()) {
            Toast.makeText(this, "Load or record a sequence first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable the Accessibility Service first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (ScreenCaptureService.instance == null) {
            log("Requesting screen capture permission before replay...")
            requestScreenCapture { beginReplay(sequence) }
            return
        }

        beginReplay(sequence)
    }

    private fun beginReplay(sequence: ClickSequence) {
        val retries = binding.retryCountInput.text.toString().toIntOrNull() ?: 5
        val confidence = binding.confidenceInput.text.toString().toDoubleOrNull() ?: 0.85

        automationEngine = AutomationEngine(
            sequence = sequence,
            maxRetriesPerStep = retries,
            minConfidence = confidence,
            logCallback = { msg -> runOnUiThread { log(msg) } }
        )
        automationEngine?.start()

        binding.btnStartReplay.isEnabled = false
        binding.btnStopReplay.isEnabled = true
        updateStatus("Replaying...")
    }

    private fun stopReplay() {
        automationEngine?.stop()
        automationEngine = null
        binding.btnStartReplay.isEnabled = true
        binding.btnStopReplay.isEnabled = false
        updateStatus("Idle")
    }

    // ---------- Helpers ----------

    private fun updateStatus(text: String) {
        binding.statusText.text = "Status: $text"
    }

    private fun log(message: String) {
        logBuilder.insert(0, "$message\n")
        if (logBuilder.length > 4000) logBuilder.setLength(4000)
        binding.logText.text = logBuilder.toString()
    }
}
