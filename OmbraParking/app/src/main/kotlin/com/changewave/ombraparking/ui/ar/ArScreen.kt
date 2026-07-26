package com.changewave.ombraparking.ui.ar

import android.hardware.GeomagneticField
import android.os.SystemClock
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.changewave.ombraparking.core.ar.CameraProjector
import com.changewave.ombraparking.core.geo.LocalPlane
import com.changewave.ombraparking.core.geo.Vec2
import com.changewave.ombraparking.core.shadow.ShadeInfo
import com.changewave.ombraparking.core.shadow.ShadowEngine
import com.changewave.ombraparking.core.sun.SolarPosition
import com.changewave.ombraparking.core.sun.SunPosition
import com.changewave.ombraparking.data.DeviceOrientation
import com.changewave.ombraparking.data.OrientationTracker
import com.changewave.ombraparking.ui.ShadowUiState
import com.changewave.ombraparking.ui.color
import com.changewave.ombraparking.ui.components.ParkedCarBar
import com.changewave.ombraparking.ui.components.QuickTimeChips
import com.changewave.ombraparking.ui.components.ShadeTimelineStrip
import com.changewave.ombraparking.ui.emoji
import com.changewave.ombraparking.ui.label
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Altezza tipica a cui si tiene il telefono guardando avanti. */
private const val EYE_HEIGHT_M = 1.5

/** Ogni quanto ricalcolare il verdetto sul punto inquadrato. */
private const val RETICLE_INTERVAL_MS = 250L

/**
 * Vista in realtà aumentata: la fotocamera inquadra la strada e l'app ci appoggia sopra
 * le ombre che ci saranno all'ora scelta, il sole e la sua traiettoria.
 */
@Composable
fun ArScreen(
    state: ShadowUiState,
    onMinuteSelected: (Int) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onNow: () -> Unit,
    onPark: () -> Unit,
    onClearParkedCar: () -> Unit,
    onBackToMyPosition: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val orientationTracker = remember { OrientationTracker(context) }

    var orientation by remember { mutableStateOf<DeviceOrientation?>(null) }
    var sensorFov by remember { mutableStateOf<FieldOfView?>(null) }
    var sourceAspect by remember { mutableStateOf(0.0) }
    var bufferRotation by remember { mutableStateOf(90) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var reticleShade by remember { mutableStateOf<ShadeInfo?>(null) }
    var reticleDistance by remember { mutableStateOf<Double?>(null) }

    val currentState by rememberUpdatedState(state)

    // Il sensore punta al nord magnetico, il sole si calcola sul nord geografico.
    val declination = remember(state.userLocation) {
        state.userLocation?.let { position ->
            GeomagneticField(
                position.latitude.toFloat(),
                position.longitude.toFloat(),
                0f,
                System.currentTimeMillis(),
            ).declination
        } ?: 0f
    }
    val currentDeclination by rememberUpdatedState(declination)

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(orientationTracker) {
        var lastReticleUpdate = 0L
        orientationTracker
            .orientation(
                displayRotation = { view.display?.rotation ?: Surface.ROTATION_0 },
                declinationDegrees = { currentDeclination },
            )
            .collect { update ->
                orientation = update

                val now = SystemClock.uptimeMillis()
                if (now - lastReticleUpdate < RETICLE_INTERVAL_MS) return@collect
                lastReticleUpdate = now

                val snapshot = currentState
                val projector = buildProjector(update, sensorFov, sourceAspect, bufferRotation, viewportSize)
                val plane = snapshot.plane
                val user = snapshot.userLocation
                val sun = snapshot.sun
                if (projector == null || plane == null || user == null || sun == null) return@collect

                val ground = projector.groundIntersection(EYE_HEIGHT_M, maxDistanceMeters = 80.0)
                if (ground == null) {
                    reticleShade = null
                    reticleDistance = null
                    return@collect
                }

                val aimed = plane.toLocal(user) + ground
                reticleShade = withContext(Dispatchers.Default) {
                    ShadowEngine.shadeAt(aimed, snapshot.obstacles, sun)
                }
                reticleDistance = ground.length
            }
    }

    val userLocal: Vec2? = remember(state.plane, state.userLocation) {
        val plane = state.plane ?: return@remember null
        val user = state.userLocation ?: return@remember null
        plane.toLocal(user)
    }

    // Se i dati caricati sono di un'altra zona qui non c'è niente di vero da sovrapporre:
    // meglio una schermata onesta che ombre inventate su edifici che non conosciamo.
    val shapes = remember(state.shadowShapes, userLocal, state.coversUserLocation) {
        if (userLocal == null || !state.coversUserLocation) {
            emptyList()
        } else {
            toUserCentredShapes(state.shadowShapes, userLocal, EYE_HEIGHT_M)
        }
    }

    // Posizione dell'auto rispetto a chi guarda: serve per piantarci il segnaposto.
    val parkedCarOffset: Vec2? = remember(state.parkedCar, state.userLocation) {
        val car = state.parkedCar ?: return@remember null
        val user = state.userLocation ?: return@remember null
        LocalPlane(user).toLocal(car.position)
    }

    val sunPath = remember(state.date, state.zone, state.userLocation) {
        val position = state.userLocation ?: return@remember emptyList<SunPosition>()
        val dayStart = state.date.atStartOfDay(state.zone).toInstant()
        (0 until 96).map { quarter ->
            SolarPosition.at(dayStart.plusSeconds(quarter * 15L * 60L), position)
        }
    }

    Box(modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onOptics = { fov, aspect, rotation ->
                sensorFov = fov
                sourceAspect = aspect
                bufferRotation = rotation
            },
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
        ) {
            val projector = buildProjector(orientation, sensorFov, sourceAspect, bufferRotation, viewportSize)
                ?: return@Canvas

            drawShadows(projector, shapes)
            drawHorizon(projector, textMeasurer)
            state.sun?.let { sun -> drawSun(projector, sun, sunPath) }
            parkedCarOffset?.let { offset ->
                drawParkedCar(projector, offset, EYE_HEIGHT_M, textMeasurer)
            }
            drawReticle(reticleShade?.quality?.color ?: Color.White)
        }

        if (state.isExploring && !state.coversUserLocation) {
            ExplorationNotice(
                name = state.explorationName,
                onBackToMyPosition = onBackToMyPosition,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp, start = 16.dp, end = 16.dp),
            )
        } else {
            ReticleLabel(
                shade = reticleShade,
                distanceMeters = reticleDistance,
                compassAccuracy = orientation?.magneticAccuracy,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp, start = 16.dp, end = 16.dp),
            )
        }

        MaterialSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ParkedCarBar(
                    parkedCar = state.parkedCar,
                    status = state.parkedCarStatus,
                    userLocation = state.userLocation,
                    zone = state.zone,
                    onPark = onPark,
                    onClear = onClearParkedCar,
                )
                ShadeTimelineStrip(
                    forecast = state.forecast,
                    dayStart = state.date.atStartOfDay(state.zone).toInstant(),
                    selectedMinute = state.minuteOfDay,
                    onMinuteSelected = onMinuteSelected,
                )
                QuickTimeChips(
                    date = state.date,
                    zone = state.zone,
                    selectedMinute = state.minuteOfDay,
                    onDateSelected = onDateSelected,
                    onMinuteSelected = onMinuteSelected,
                    onNow = onNow,
                )
            }
        }
    }
}

/**
 * Avviso che sostituisce il mirino quando si stanno guardando le ombre di un'altra zona:
 * la realtà aumentata può parlare solo di dove ci si trova davvero.
 */
@Composable
private fun ExplorationNotice(
    name: String?,
    onBackToMyPosition: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialSurface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Stai esplorando ${name ?: "un'altra zona"}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Qui la realtà aumentata non ha edifici da mostrare: gli ombreggiamenti " +
                    "caricati sono di un altro posto.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(
                onClick = onBackToMyPosition,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Torna alla mia posizione")
            }
        }
    }
}

/** Etichetta sopra il mirino: cosa c'è nel punto inquadrato all'ora scelta. */
@Composable
private fun ReticleLabel(
    shade: ShadeInfo?,
    distanceMeters: Double?,
    compassAccuracy: Int?,
    modifier: Modifier = Modifier,
) {
    val text = when {
        shade == null && distanceMeters == null -> "Inquadra la strada davanti a te"
        shade == null -> "Calcolo…"
        else -> buildString {
            append(shade.quality.emoji)
            append(' ')
            append(shade.quality.label)
            distanceMeters?.let { append(" · a ${it.toInt()} m") }
            shade.obstacle?.name?.let { append(" · $it") }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MaterialSurface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (compassAccuracy != null && compassAccuracy < 2) {
            MaterialSurface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    text = "Bussola da calibrare: muovi il telefono a forma di otto",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onOptics: (FieldOfView?, Double, Int) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val provider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val currentOnOptics by rememberUpdatedState(onOptics)

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Senza questo avviso un guasto della fotocamera sarebbe solo uno schermo nero.
        cameraError?.let { message ->
            MaterialSurface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    LaunchedEffect(previewView) {
        try {
            val cameraProvider = context.awaitCameraProvider()
            provider.value = cameraProvider

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
            )
            cameraError = null

            val resolutionInfo = preview.resolutionInfo
            currentOnOptics(
                CameraOptics.sensorFieldOfView(camera.cameraInfo),
                CameraOptics.sourceAspectRatio(resolutionInfo),
                resolutionInfo?.rotationDegrees ?: 90,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            cameraError = "Fotocamera non disponibile: ${error.message ?: "errore sconosciuto"}"
        }
    }

    /*
     * Nessuna chiave, e non è una svista: con `DisposableEffect(provider)` il cambio di
     * stato da null a provider faceva scattare l'onDispose del passaggio precedente, che
     * legge il valore *corrente* e quindi sganciava la fotocamera appena agganciata.
     * Restava l'overlay disegnato su uno sfondo nero. Qui l'unbind avviene solo uscendo
     * davvero dalla schermata.
     */
    DisposableEffect(Unit) {
        onDispose { provider.value?.unbindAll() }
    }
}

/**
 * Costruisce il proiettore con il campo visivo effettivo dell'anteprima.
 * Restituisce null finché non si conoscono orientamento e dimensioni della vista.
 */
private fun buildProjector(
    orientation: DeviceOrientation?,
    sensorFov: FieldOfView?,
    sourceAspect: Double,
    bufferRotation: Int,
    viewportSize: IntSize,
): CameraProjector? {
    if (orientation == null || viewportSize.width == 0 || viewportSize.height == 0) return null

    val viewAspect = viewportSize.width.toDouble() / viewportSize.height.toDouble()
    val fov = CameraOptics.visibleFieldOfView(
        sensorFov = sensorFov ?: FieldOfView.FALLBACK,
        bufferRotationDegrees = if (sensorFov == null) 0 else bufferRotation,
        sourceAspectRatio = sourceAspect,
        viewAspectRatio = viewAspect,
    )

    return CameraProjector(
        viewportWidthPx = viewportSize.width.toFloat(),
        viewportHeightPx = viewportSize.height.toFloat(),
        horizontalFovDegrees = fov.horizontalDegrees,
        verticalFovDegrees = fov.verticalDegrees,
        deviceToWorld = orientation.deviceToWorld,
    )
}

private suspend fun android.content.Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (error: Exception) {
                    // L'errore va propagato, non inghiottito: chi chiama lo trasforma in un
                    // messaggio a schermo invece di lasciare l'anteprima nera e muta.
                    continuation.resumeWithException(error)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }
