package com.ojoclaro.android.outdoor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.ojoclaro.android.BuildConfig
import com.ojoclaro.android.R
import com.ojoclaro.android.llm.HttpUrlConnectionLlmAgentNetworkClient
import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.speech.SpeechController
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service de Outdoor Guidance (tipo location).
 *
 * Vive SOLO mientras hay una sesión exterior iniciada por interacción
 * explícita (voz u overlay). Notificación visible "Estela te está orientando."
 * con acciones Repetir / Describir / Detener. Sin ACCESS_BACKGROUND_LOCATION:
 * el service en foreground cubre la navegación con pantalla apagada.
 */
class OutdoorForegroundService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var speechController: SpeechController
    private lateinit var coordinator: OutdoorNavigationCoordinator
    private var locationListener: LocationListener? = null

    // V1.9 — vigía de quietud: si navegando no llega NINGÚN update por
    // STILLNESS_SILENCE_MILLIS (quieto o GPS caído), avisa UNA vez que sigue
    // ahí. Se rearma solo cuando vuelven los updates: jamás en loop.
    private var stillnessWatchJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var lastFixElapsedMillis: Long = 0L

    @Volatile
    private var stillnessAnnounced: Boolean = false

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        speechController = SpeechController(context = this)

        val config = LlmAgentClientConfig.fromBuildConfig()
        val networkClient = HttpUrlConnectionLlmAgentNetworkClient()
        val engine = AndroidOutdoorLocationEngine(this)
        coordinator = OutdoorNavigationCoordinator(
            locationReader = OutdoorLocationReader(engine),
            routeProvider = BackendOutdoorRouteProvider(config, networkClient),
            sceneDescriber = BackendSceneDescriber(
                capturer = CameraXSceneCapturer(this, this),
                config = config,
                networkClient = networkClient,
                encodeBase64 = { bytes -> Base64.encodeToString(bytes, Base64.NO_WRAP) },
                log = ::logOutdoor
            ),
            speak = { text -> speechController.speak(text, force = true) },
            log = ::logOutdoor
        )
        activeService = WeakReference(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CONTRATO Android 12+: todo arranque vía startForegroundService DEBE
        // llamar startForeground, incluso para STOP/REPEAT sin guía activa.
        // Omitirlo mataba el proceso con ForegroundServiceDidNotStartInTime
        // (crash físico real en Moto G15 al cancelar sin navegación).
        startForegroundWithNotification(
            includeCamera = intent?.action == ACTION_DESCRIBE
        )
        when (intent?.action) {
            ACTION_START_GUIDANCE -> {
                val destination = intent.getStringExtra(EXTRA_DESTINATION).orEmpty().take(200)
                // V1.10.2 — solo true cuando el usuario confirmó "intentá igual".
                val allowImprecise = intent.getBooleanExtra(EXTRA_ALLOW_IMPRECISE, false)
                serviceScope.launch {
                    val active = coordinator.startGuidance(destination, allowImprecise)
                    if (active) startLocationUpdates() else stopSelfIfIdle()
                }
            }
            ACTION_WHERE_AM_I -> {
                serviceScope.launch {
                    coordinator.whereAmI()
                    stopSelfIfIdle()
                }
            }
            ACTION_DESCRIBE -> {
                // Android 14+: tipo camera ya incluido en el startForeground
                // de arriba (solo para esta acción).
                serviceScope.launch {
                    coordinator.describeAhead()
                    stopSelfIfIdle()
                }
            }
            ACTION_REPEAT -> {
                coordinator.repeatInstruction()
                stopSelfIfIdle()
            }
            ACTION_HOW_FAR -> {
                coordinator.howFar()
                stopSelfIfIdle()
            }
            ACTION_RECALCULATE -> {
                serviceScope.launch {
                    val active = coordinator.recalculate()
                    if (active) startLocationUpdates() else stopSelfIfIdle()
                }
            }
            ACTION_STOP -> {
                coordinator.cancelGuidance("user_stop")
                stopGuidanceAndSelf()
            }
            else -> stopSelfIfIdle()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopLocationUpdates()
        coordinator.cancelGuidance("service_destroy")
        speechController.shutdown()
        serviceScope.cancel()
        if (activeService?.get() === this) activeService = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    // --- ubicación durante la guía ---

    private fun startLocationUpdates() {
        if (locationListener != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val provider = when {
            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
                .getOrDefault(false) -> LocationManager.GPS_PROVIDER
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
                .getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        val listener = LocationListener { location -> onLocation(location) }
        locationListener = listener
        runCatching {
            manager.requestLocationUpdates(
                provider,
                UPDATE_INTERVAL_MILLIS,
                UPDATE_MIN_DISTANCE_METERS,
                listener
            )
        }.onFailure { locationListener = null }
        if (locationListener != null) startStillnessWatch()
    }

    private fun stopLocationUpdates() {
        stillnessWatchJob?.cancel()
        stillnessWatchJob = null
        val listener = locationListener ?: return
        locationListener = null
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        runCatching { manager?.removeUpdates(listener) }
    }

    private fun startStillnessWatch() {
        if (stillnessWatchJob != null) return
        lastFixElapsedMillis = SystemClock.elapsedRealtime()
        stillnessAnnounced = false
        stillnessWatchJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(STILLNESS_CHECK_INTERVAL_MILLIS)
                if (!coordinator.isNavigating) continue
                val silentFor = SystemClock.elapsedRealtime() - lastFixElapsedMillis
                if (silentFor >= OutdoorBudgets.STILLNESS_SILENCE_MILLIS && !stillnessAnnounced) {
                    stillnessAnnounced = true
                    logOutdoor("navEvent=stillness_reassurance announcedOnce=true")
                    speechController.speak(
                        OutdoorSafetyPolicy.sanitizeForSpeech(
                            "Sigo con vos. Hace un rato no recibo tu posición nueva. " +
                                "Si estás quieto, está todo bien. Si avanzaste y no " +
                                "digo nada, decí: dónde estoy. Para la próxima " +
                                "indicación, decí: repetí."
                        ),
                        force = true
                    )
                }
            }
        }
    }

    private fun onLocation(location: Location) {
        lastFixElapsedMillis = SystemClock.elapsedRealtime()
        stillnessAnnounced = false
        val ageMillis = if (location.elapsedRealtimeNanos > 0L) {
            ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L)
                .coerceAtLeast(0L)
        } else {
            0L
        }
        val fix = OutdoorLocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            ageMillis = ageMillis,
            provider = location.provider ?: "unknown"
        )
        val finished = coordinator.onLocationUpdate(fix)
        if (finished) stopGuidanceAndSelf()
    }

    private fun stopSelfIfIdle() {
        if (coordinator.state == OutdoorState.IDLE && !coordinator.isNavigating) {
            serviceScope.launch {
                waitForTtsIdle()
                stopGuidanceAndSelf()
            }
        }
    }

    private fun stopGuidanceAndSelf() {
        serviceScope.launch {
            waitForTtsIdle()
            // Mientras esperábamos el TTS pudo llegar OTRO pedido (p. ej. una
            // segunda "¿dónde estoy?"). Si hay trabajo en curso, abortar el
            // apagado: ese request hará su propio stop al terminar.
            if (coordinator.state != OutdoorState.IDLE || coordinator.isNavigating) {
                logOutdoor("stopAborted=concurrent_work state=${coordinator.state}")
                return@launch
            }
            stopLocationUpdates()
            logOutdoor("serviceStopping=true finalState=IDLE")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun waitForTtsIdle() {
        var waited = 0L
        while (speechController.isSpeaking && waited < 15_000L) {
            kotlinx.coroutines.delay(250L)
            waited += 250L
        }
        kotlinx.coroutines.delay(400L)
    }

    // --- notificación ---

    private fun startForegroundWithNotification(includeCamera: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Orientación exterior",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_tile_mic)
            .setContentTitle("Estela te está orientando.")
            .setContentText("Ayuda complementaria: seguí usando tu bastón o método habitual.")
            .setOngoing(true)
            .addAction(0, "Repetir", actionIntent(ACTION_REPEAT, 1))
            .addAction(0, "Describir", actionIntent(ACTION_DESCRIBE, 2))
            .addAction(0, "Detener", actionIntent(ACTION_STOP, 3))
            .build()
        val foregroundType = if (includeCamera) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, OutdoorForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun logOutdoor(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "route=$ROUTE_TAG $message")
    }

    companion object {
        private const val TAG = "EstelaOutdoor"
        private const val ROUTE_TAG = "ESTELA_OUTDOOR_V1"
        private const val CHANNEL_ID = "ojo_claro_outdoor"
        private const val NOTIFICATION_ID = 20260609
        private const val UPDATE_INTERVAL_MILLIS = 3_000L
        private const val UPDATE_MIN_DISTANCE_METERS = 4f
        private const val STILLNESS_CHECK_INTERVAL_MILLIS = 30_000L

        const val ACTION_START_GUIDANCE = "com.ojoclaro.android.outdoor.ACTION_START_GUIDANCE"
        const val ACTION_WHERE_AM_I = "com.ojoclaro.android.outdoor.ACTION_WHERE_AM_I"
        const val ACTION_DESCRIBE = "com.ojoclaro.android.outdoor.ACTION_DESCRIBE"
        const val ACTION_REPEAT = "com.ojoclaro.android.outdoor.ACTION_REPEAT"
        const val ACTION_HOW_FAR = "com.ojoclaro.android.outdoor.ACTION_HOW_FAR"
        const val ACTION_RECALCULATE = "com.ojoclaro.android.outdoor.ACTION_RECALCULATE"
        const val ACTION_STOP = "com.ojoclaro.android.outdoor.ACTION_STOP"
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_ALLOW_IMPRECISE = "allow_imprecise"

        @Volatile
        private var activeService: WeakReference<OutdoorForegroundService>? = null

        fun isGuidanceActive(): Boolean =
            activeService?.get()?.coordinator?.isNavigating == true

        fun currentProgress(): OutdoorRouteProgress? =
            activeService?.get()?.coordinator?.currentProgress()

        fun startGuidance(
            context: Context,
            destination: String,
            allowImprecise: Boolean = false
        ) = startWithAction(context, ACTION_START_GUIDANCE) {
            putExtra(EXTRA_DESTINATION, destination.take(200))
            putExtra(EXTRA_ALLOW_IMPRECISE, allowImprecise)
        }

        fun whereAmI(context: Context) = startWithAction(context, ACTION_WHERE_AM_I)

        fun describeAhead(context: Context) = startWithAction(context, ACTION_DESCRIBE)

        fun repeatInstruction(context: Context) = startWithAction(context, ACTION_REPEAT)

        fun howFar(context: Context) = startWithAction(context, ACTION_HOW_FAR)

        fun recalculate(context: Context) = startWithAction(context, ACTION_RECALCULATE)

        fun stopGuidance(context: Context) = startWithAction(context, ACTION_STOP)

        private fun startWithAction(
            context: Context,
            action: String,
            configure: Intent.() -> Unit = {}
        ) {
            runCatching {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, OutdoorForegroundService::class.java)
                        .setAction(action)
                        .apply(configure)
                )
            }
        }
    }
}

/**
 * Captura única en memoria con CameraX (sin preview, sin persistencia).
 * Emite un tono ANTES de capturar: nunca foto silenciosa.
 */
class CameraXSceneCapturer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : OutdoorSceneCapturer {

    override fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun captureSingleJpeg(timeoutMillis: Long): ByteArray? {
        // Indicador sonoro previo (accesible): nunca capturar en silencio.
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                .startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
        }

        val provider: androidx.camera.lifecycle.ProcessCameraProvider? =
            withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine<androidx.camera.lifecycle.ProcessCameraProvider?> { continuation ->
                    val future =
                        androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
                    future.addListener(
                        {
                            val resolved = runCatching { future.get() }.getOrNull()
                            continuation.resume(resolved)
                        },
                        ContextCompat.getMainExecutor(context)
                    )
                }
            }
        if (provider == null) {
            Log.i(CAPTURER_TAG, "cameraProviderReady=false")
            return null
        }
        Log.i(CAPTURER_TAG, "cameraProviderReady=true")

        // Resolución acotada: alcanza de sobra para describir la escena y evita
        // payloads de varios MB (verificado: la full-res del Moto G15 hacía
        // fallar el upstream de visión con HTTP 400 por tamaño).
        val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
            .setResolutionStrategy(
                androidx.camera.core.resolutionselector.ResolutionStrategy(
                    android.util.Size(1280, 960),
                    androidx.camera.core.resolutionselector.ResolutionStrategy
                        .FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()
        val imageCapture = androidx.camera.core.ImageCapture.Builder()
            .setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolutionSelector)
            .setJpegQuality(80)
            .build()

        return try {
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    imageCapture
                )
            }.getOrElse { error ->
                Log.w(CAPTURER_TAG, "cameraBound=false err=${error.javaClass.simpleName}")
                return null
            }
            Log.i(CAPTURER_TAG, "cameraBound=true")

            withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine<ByteArray?> { continuation ->
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : androidx.camera.core.ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                val bytes = runCatching {
                                    val buffer = image.planes[0].buffer
                                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                                }.getOrNull()
                                image.close()
                                continuation.resume(bytes)
                            }

                            override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                continuation.resume(null)
                            }
                        }
                    )
                }
            }
        } finally {
            runCatching { provider.unbindAll() }
            Log.i(CAPTURER_TAG, "cameraUnbound=true")
        }
    }

    private companion object {
        const val CAPTURER_TAG = "EstelaOutdoor"
    }
}
