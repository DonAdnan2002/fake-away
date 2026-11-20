package com.fakeaway.app.features.screen_shield.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.fakeaway.app.FakeAwayApplication
import com.fakeaway.app.MainActivity
import com.fakeaway.app.R
import com.fakeaway.app.core.domain.Verdict
import com.fakeaway.app.core.i18n.LocaleManager
import com.fakeaway.app.features.screen_shield.data.ScreenCaptureController
import com.fakeaway.app.features.screen_shield.data.ScreenImageCropper
import com.fakeaway.app.features.screen_shield.domain.ScreenScanResult
import com.fakeaway.app.features.voice_detector.domain.VoiceAnalysisResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Foreground overlay service providing a draggable floating Fake Away icon over
 * Facebook, TikTok, WhatsApp, or any app for 1-tap Screen OCR + Image + Audio AI verification.
 */
class FloatingShieldService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: ImageView
    private var menuView: View? = null
    private var statusView: View? = null
    private var resultCardView: View? = null

    private var captureController: ScreenCaptureController? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isBusy = false

    private val appContainer by lazy { (application as FakeAwayApplication).container }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundSafely()
        attachFloatingBubble()
        appContainer.shieldRuntimeState.setShieldRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }

        if (resultCode == Activity.RESULT_OK && data != null && captureController == null) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, data)
            captureController = ScreenCaptureController(
                context = this,
                mediaProjection = projection,
                onProjectionStopped = { stopSelf() }
            )
        }
        return START_NOT_STICKY
    }

    private fun localized(): Context =
        LocaleManager.localizedContext(this, appContainer.preferences.language.value)

    private fun startForegroundSafely() {
        val loc = localized()
        val channelId = "fakeaway_shield_channel"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                loc.getString(R.string.overlay_notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, channelId)
            .setContentTitle(loc.getString(R.string.overlay_notif_title))
            .setContentText(loc.getString(R.string.overlay_notif_text))
            .setSmallIcon(R.drawable.ic_shield_bubble)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasMic = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val types = if (hasMic) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachFloatingBubble() {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF0A0F1E.toInt())
            setStroke(dp(2), 0xFF38BDF8.toInt())
        }

        bubbleView = ImageView(this).apply {
            setImageResource(R.drawable.ic_shield_bubble)
            background = bg
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
            elevation = dp(8).toFloat()
        }

        val params = overlayParams(
            width = dp(56),
            height = dp(56),
            gravity = Gravity.TOP or Gravity.END,
            x = dp(16),
            y = dp(240)
        )

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        bubbleView.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = ev.rawX
                    touchY = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX - (ev.rawX - touchX).toInt()
                    params.y = startY + (ev.rawY - touchY).toInt()
                    runCatching { windowManager.updateViewLayout(bubbleView, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(ev.rawX - touchX) < dp(8) && abs(ev.rawY - touchY) < dp(8)) {
                        if (!isBusy) toggleActionMenu(params)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, params)
    }

    private fun toggleActionMenu(anchorParams: WindowManager.LayoutParams) {
        if (menuView != null) {
            removeViewSafely(menuView)
            menuView = null
            return
        }

        val loc = localized()
        val container = LinearLayout(loc).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(0xF2131B2E.toInt(), 0xFF38BDF8.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            elevation = dp(10).toFloat()
        }

        val scanScreenBtn = styledMenuButton(loc, loc.getString(R.string.overlay_scan_screen), 0xFF38BDF8.toInt()) {
            removeMenu()
            triggerScreenScan()
        }
        val scanAudioBtn = styledMenuButton(loc, loc.getString(R.string.overlay_scan_audio), 0xFF00E5A0.toInt()) {
            removeMenu()
            triggerAudioScan()
        }
        val stopBtn = styledMenuButton(loc, loc.getString(R.string.overlay_stop), 0xFFFF4D6D.toInt()) {
            removeMenu()
            stopSelf()
        }

        container.addView(scanScreenBtn)
        container.addView(spacer(dp(6)))
        container.addView(scanAudioBtn)
        container.addView(spacer(dp(6)))
        container.addView(stopBtn)

        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.END,
            x = anchorParams.x,
            y = anchorParams.y + dp(64)
        )
        menuView = container
        windowManager.addView(container, params)
    }

    private fun triggerScreenScan() {
        val controller = captureController
        if (controller == null) {
            showSimpleStatusCard(localized().getString(R.string.overlay_capture_failed), autoDismiss = true)
            return
        }
        isBusy = true
        serviceScope.launch {
            try {
                dismissResultCard()
                setOverlaysVisible(false)
                val frame = controller.captureFrame()
                setOverlaysVisible(true)

                if (frame == null) {
                    showSimpleStatusCard(localized().getString(R.string.overlay_capture_failed), autoDismiss = true)
                    return@launch
                }

                showSimpleStatusCard(localized().getString(R.string.overlay_scanning_screen), autoDismiss = false)
                val result = withContext(Dispatchers.Default) {
                    val regions = ScreenImageCropper.extractRegions(frame)
                    val ocrPixels = ScreenImageCropper.toArgbPixels(regions.ocrBitmap)
                    val mediaPixels = ScreenImageCropper.toArgbPixels(regions.mediaBitmap)
                    val previewPath = savePreviewBitmap(regions.mediaBitmap)
                    val res = appContainer.analyzeScreenUseCase(
                        ocrPixels = ocrPixels,
                        ocrWidth = regions.ocrBitmap.width,
                        ocrHeight = regions.ocrBitmap.height,
                        mediaPixels = mediaPixels,
                        mediaWidth = regions.mediaBitmap.width,
                        mediaHeight = regions.mediaBitmap.height,
                        uiLanguage = appContainer.preferences.language.value,
                        previewImagePath = previewPath
                    )
                    if (regions.ocrBitmap !== frame) regions.ocrBitmap.recycle()
                    if (regions.mediaBitmap !== frame && regions.mediaBitmap !== regions.ocrBitmap) {
                        regions.mediaBitmap.recycle()
                    }
                    frame.recycle()
                    res
                }
                dismissStatusCard()
                appContainer.shieldRuntimeState.publishScreenScan(result)
                showScreenResultCard(result)
            } finally {
                setOverlaysVisible(true)
                isBusy = false
            }
        }
    }

    private fun triggerAudioScan() {
        val controller = captureController
        if (controller == null) {
            showSimpleStatusCard(localized().getString(R.string.overlay_capture_failed), autoDismiss = true)
            return
        }
        isBusy = true
        serviceScope.launch {
            try {
                dismissResultCard()
                val hasMic = ContextCompat.checkSelfPermission(
                    this@FloatingShieldService,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                val captured = controller.capturePlayingAudio(
                    durationSeconds = 6,
                    canUseMicFallback = hasMic
                ) { remainingSec ->
                    withContext(Dispatchers.Main) {
                        showSimpleStatusCard(
                            localized().getString(R.string.overlay_capturing_audio, remainingSec),
                            autoDismiss = false
                        )
                    }
                }

                showSimpleStatusCard(localized().getString(R.string.voice_analyzing), autoDismiss = false)
                val outcome = appContainer.analyzeVoiceUseCase(
                    samples16k = captured.samples16k,
                    sourceLabel = if (captured.usedMicFallback) "Overlay Audio (Mic)" else "Overlay Playback Audio",
                    usedMicFallback = captured.usedMicFallback
                )
                dismissStatusCard()

                outcome.fold(
                    onSuccess = { voiceRes ->
                        appContainer.shieldRuntimeState.publishOverlayVoiceScan(voiceRes)
                        showVoiceResultCard(voiceRes)
                    },
                    onFailure = {
                        showSimpleStatusCard(localized().getString(R.string.voice_error_too_short), autoDismiss = true)
                    }
                )
            } finally {
                isBusy = false
            }
        }
    }

    private fun showScreenResultCard(result: ScreenScanResult) {
        val loc = localized()
        val accent = verdictColorInt(result.overallVerdict)
        val pct = (result.overallRiskScore * 100f).roundToInt()

        val card = buildResultCardContainer(loc, accent)
        card.addView(titleText(loc, "${verdictText(loc, result.overallVerdict)} • $pct%", accent))

        result.imageResult?.let { img ->
            card.addView(
                bodyText(
                    loc,
                    loc.getString(R.string.overlay_image_ai_label, (img.aiScore * 100f).roundToInt()),
                    0xFFF1F5F9.toInt()
                )
            )
        }
        result.factCheckResult?.let { fc ->
            card.addView(bodyText(loc, fc.explanation.take(180), 0xFF94A3B8.toInt()))
        }
        if (result.extractedText.isNotBlank()) {
            card.addView(
                bodyText(
                    loc,
                    "${loc.getString(R.string.overlay_ocr_label)} ${result.extractedText.take(130)}",
                    0xFFCBD5E1.toInt()
                )
            )
        }

        card.addView(spacer(dp(10)))
        card.addView(actionRow(loc, openTab = "shield"))
        attachResultCard(card)
    }

    private fun showVoiceResultCard(result: VoiceAnalysisResult) {
        val loc = localized()
        val accent = verdictColorInt(result.verdict)
        val pct = (result.overallFakeScore * 100f).roundToInt()

        val card = buildResultCardContainer(loc, accent)
        card.addView(titleText(loc, "${verdictText(loc, result.verdict)} • $pct%", accent))
        card.addView(
            bodyText(
                loc,
                loc.getString(R.string.overlay_voice_ai_label, pct),
                0xFFF1F5F9.toInt()
            )
        )
        if (result.usedMicFallback) {
            card.addView(
                bodyText(loc, loc.getString(R.string.overlay_mic_fallback_note), 0xFFFFB703.toInt())
            )
        }
        card.addView(spacer(dp(10)))
        card.addView(actionRow(loc, openTab = "voice"))
        attachResultCard(card)
    }

    private fun buildResultCardContainer(loc: Context, borderColor: Int): LinearLayout =
        LinearLayout(loc).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(0xF50A0F1E.toInt(), borderColor)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(12).toFloat()
        }

    private fun actionRow(loc: Context, openTab: String): LinearLayout {
        val row = LinearLayout(loc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val dismissBtn = styledMenuButton(loc, loc.getString(R.string.overlay_dismiss), 0xFF94A3B8.toInt()) {
            dismissResultCard()
        }
        val reportBtn = styledMenuButton(loc, loc.getString(R.string.overlay_open_full_report), 0xFF38BDF8.toInt()) {
            dismissResultCard()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_TARGET_TAB, openTab)
            }
            startActivity(intent)
        }
        row.addView(dismissBtn)
        row.addView(spacer(dp(8)))
        row.addView(reportBtn)
        return row
    }

    private fun attachResultCard(view: View) {
        dismissResultCard()
        val params = overlayParams(
            width = resources.displayMetrics.widthPixels - dp(24),
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            x = 0,
            y = dp(48)
        )
        resultCardView = view
        windowManager.addView(view, params)
    }

    private fun showSimpleStatusCard(message: String, autoDismiss: Boolean) {
        dismissStatusCard()
        val loc = localized()
        val card = LinearLayout(loc).apply {
            orientation = LinearLayout.HORIZONTAL
            background = cardBg(0xEE131B2E.toInt(), 0xFF38BDF8.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(bodyText(loc, message, 0xFFF1F5F9.toInt()))
        }
        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            x = 0,
            y = dp(80)
        )
        statusView = card
        windowManager.addView(card, params)

        if (autoDismiss) {
            card.postDelayed({ dismissStatusCard() }, 2500L)
        }
    }

    private fun savePreviewBitmap(bitmap: Bitmap): String? = runCatching {
        val file = File(cacheDir, "last_shield_capture.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
        file.absolutePath
    }.getOrNull()

    private fun setOverlaysVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.INVISIBLE
        if (::bubbleView.isInitialized) bubbleView.visibility = v
        menuView?.visibility = v
        statusView?.visibility = v
        resultCardView?.visibility = v
    }

    private fun removeMenu() {
        removeViewSafely(menuView)
        menuView = null
    }

    private fun dismissStatusCard() {
        removeViewSafely(statusView)
        statusView = null
    }

    private fun dismissResultCard() {
        removeViewSafely(resultCardView)
        resultCardView = null
    }

    private fun removeViewSafely(view: View?) {
        if (view != null) runCatching { windowManager.removeView(view) }
    }

    private fun overlayParams(
        width: Int,
        height: Int,
        gravity: Int,
        x: Int,
        y: Int
    ): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
    }

    private fun cardBg(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun styledMenuButton(
        ctx: Context,
        label: String,
        accent: Int,
        onClick: () -> Unit
    ): Button = Button(ctx).apply {
        text = label
        isAllCaps = false
        setTextColor(0xFF0A0F1E.toInt())
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(accent)
        }
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { onClick() }
    }

    private fun titleText(ctx: Context, text: String, color: Int) = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        textSize = 16f
        setPadding(0, 0, 0, dp(6))
    }

    private fun bodyText(ctx: Context, text: String, color: Int) = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        textSize = 13f
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun spacer(sizePx: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
    }

    private fun verdictColorInt(verdict: Verdict): Int = when (verdict) {
        Verdict.REAL -> 0xFF00E5A0.toInt()
        Verdict.SUSPICIOUS -> 0xFFFFB703.toInt()
        Verdict.FAKE -> 0xFFFF4D6D.toInt()
        Verdict.UNVERIFIED -> 0xFF94A3B8.toInt()
    }

    private fun verdictText(loc: Context, verdict: Verdict): String = when (verdict) {
        Verdict.REAL -> loc.getString(R.string.verdict_real)
        Verdict.SUSPICIOUS -> loc.getString(R.string.verdict_suspicious)
        Verdict.FAKE -> loc.getString(R.string.verdict_fake)
        Verdict.UNVERIFIED -> loc.getString(R.string.verdict_unverified)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        super.onDestroy()
        appContainer.shieldRuntimeState.setShieldRunning(false)
        serviceScope.cancel()
        captureController?.release()
        captureController = null
        removeMenu()
        dismissStatusCard()
        dismissResultCard()
        if (::bubbleView.isInitialized) removeViewSafely(bubbleView)
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        const val ACTION_STOP = "com.fakeaway.app.ACTION_STOP_SHIELD"
        private const val NOTIFICATION_ID = 4041
    }
}
