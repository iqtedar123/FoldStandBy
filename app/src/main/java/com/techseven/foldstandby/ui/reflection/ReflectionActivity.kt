package com.techseven.foldstandby.ui.reflection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.SurfaceTexture
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.techseven.foldstandby.R
import com.techseven.foldstandby.reflection.MediaRemote
import com.techseven.foldstandby.reflection.ReflectionCaptureService
import com.techseven.foldstandby.ui.adaptive.rememberTentFoldLayout
import com.techseven.foldstandby.ui.theme.FoldStandByTheme
import com.techseven.foldstandby.ui.theme.NightstandAccent
import com.techseven.foldstandby.ui.theme.NightstandBg
import com.techseven.foldstandby.ui.theme.NightstandInk
import com.techseven.foldstandby.ui.theme.NightstandMuted
import kotlinx.coroutines.delay

private val FrostTileFill = Color.White.copy(alpha = 0.14f)
private val FrostTileStroke = Color.White.copy(alpha = 0.28f)
private val FrostScrim = Color.Black.copy(alpha = 0.22f)
private val FrostTileShape = RoundedCornerShape(16.dp)
private const val AutoHideMs = 4_000L

class ReflectionActivity : ComponentActivity() {
    private var captureService: ReflectionCaptureService? = null
    private var bound = false
    private var outputSurface: Surface? = null
    private var pendingWidth = 0
    private var pendingHeight = 0
    private var pendingDpi = 0

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ReflectionCaptureService.LocalBinder
            captureService = binder.getService()
            bound = true
            flushPendingSurface()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            captureService = null
            bound = false
        }
    }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            ReflectionCaptureService.start(this, result.resultCode, data)
            bindCaptureService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        setContent {
            FoldStandByTheme {
                val fold by rememberTentFoldLayout()
                val capturing by ReflectionCaptureService.capturing.collectAsState()
                val contentVisible by ReflectionCaptureService.contentVisible.collectAsState()

                ReflectionScreen(
                    isTabletop = fold.isTabletop,
                    capturing = capturing,
                    contentVisible = contentVisible,
                    onStartCapture = { requestCapture() },
                    onStopCapture = { stopCapture() },
                    onClose = { finish() },
                    onSurfaceReady = { surfaceTexture, width, height, dpi ->
                        attachSurfaceTexture(surfaceTexture, width, height, dpi)
                    },
                    onSurfaceDestroyed = { releaseSurface() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindCaptureService()
    }

    override fun onStop() {
        if (bound) {
            unbindService(serviceConnection)
            bound = false
            captureService = null
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            ReflectionCaptureService.stop(this)
            releaseSurface()
        }
        super.onDestroy()
    }

    private fun requestCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java) ?: return
        captureLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun stopCapture() {
        ReflectionCaptureService.stop(this)
    }

    private fun bindCaptureService() {
        if (bound) return
        val intent = Intent(this, ReflectionCaptureService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun attachSurfaceTexture(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
        dpi: Int
    ) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        surfaceTexture.setDefaultBufferSize(w, h)
        if (outputSurface == null) {
            outputSurface = Surface(surfaceTexture)
        }
        pendingWidth = w
        pendingHeight = h
        pendingDpi = dpi
        flushPendingSurface()
    }

    private fun releaseSurface() {
        captureService?.attachSurface(null, 0, 0, 0)
        outputSurface?.release()
        outputSurface = null
        pendingWidth = 0
        pendingHeight = 0
    }

    private fun flushPendingSurface() {
        val surface = outputSurface ?: return
        captureService?.attachSurface(
            surface,
            pendingWidth,
            pendingHeight,
            pendingDpi
        )
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
fun ReflectionScreen(
    isTabletop: Boolean,
    capturing: Boolean,
    contentVisible: Boolean,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onClose: () -> Unit,
    onSurfaceReady: (SurfaceTexture, Int, Int, Int) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    val density = LocalDensity.current
    val floorTint = Color(0xFF0D0D0D)
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(true) }
    var infoExpanded by remember { mutableStateOf(false) }
    var pausedOptimistic by remember { mutableStateOf(false) }
    var hideNonce by remember { mutableStateOf(0) }

    fun bumpInteraction() {
        hideNonce++
        controlsVisible = true
    }

    // Keep controls up until capture starts; then auto-hide after a few seconds
    LaunchedEffect(capturing) {
        if (!capturing) {
            controlsVisible = true
            infoExpanded = false
        } else {
            controlsVisible = true
            hideNonce++
        }
    }

    LaunchedEffect(capturing, controlsVisible, hideNonce, infoExpanded) {
        if (!capturing || !controlsVisible || infoExpanded) return@LaunchedEffect
        delay(AutoHideMs)
        controlsVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightstandBg)
            .safeDrawingPadding()
            .then(
                if (!controlsVisible) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        controlsVisible = true
                        hideNonce++
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // Ambient reflection (visible when chrome is hidden)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(floorTint)
        ) {
            if (capturing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleY = -1f
                            rotationX = -25f
                            cameraDistance = 16f * density.density
                            alpha = 0.9f
                        }
                        // Extra blur under chrome reads as frosted glass behind tiles
                        .blur(if (controlsVisible) 28.dp else 16.dp)
                ) {
                    if (contentVisible) {
                        CaptureTextureView(
                            onSurfaceReady = onSurfaceReady,
                            onSurfaceDestroyed = onSurfaceDestroyed
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.25f),
                                        0.2f to Color.Transparent,
                                        0.55f to Color.Black.copy(alpha = 0.5f),
                                        1.0f to Color.Black.copy(alpha = 0.9f)
                                    )
                                )
                            )
                            drawRect(
                                Color.Black.copy(alpha = if (controlsVisible) 0.35f else 0.55f)
                            )
                        }
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            FlexControlsPanel(
                isTabletop = isTabletop,
                capturing = capturing,
                infoExpanded = infoExpanded,
                paused = pausedOptimistic,
                onToggleInfo = {
                    infoExpanded = !infoExpanded
                    bumpInteraction()
                },
                onClose = onClose,
                onStartCapture = {
                    bumpInteraction()
                    onStartCapture()
                },
                onStopCapture = {
                    bumpInteraction()
                    onStopCapture()
                },
                onPlayPause = {
                    bumpInteraction()
                    MediaRemote.playPause(context)
                    pausedOptimistic = !pausedOptimistic
                },
                onSkipBack = {
                    bumpInteraction()
                    MediaRemote.skipBack10(context)
                },
                onSkipForward = {
                    bumpInteraction()
                    MediaRemote.skipForward10(context)
                },
                onVolumeDown = {
                    bumpInteraction()
                    MediaRemote.volumeDown(context)
                },
                onVolumeUp = {
                    bumpInteraction()
                    MediaRemote.volumeUp(context)
                },
                onMute = {
                    bumpInteraction()
                    MediaRemote.toggleMute(context)
                },
                onUserInteract = { bumpInteraction() }
            )
        }

        // Corner affordance — show controls again when chrome is hidden
        if (!controlsVisible) {
            IconButton(
                onClick = {
                    controlsVisible = true
                    hideNonce++
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(NightstandInk.copy(alpha = 0.22f), CircleShape),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = NightstandInk
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = stringResource(R.string.reflection_show_controls)
                )
            }
        }
    }
}

@Composable
private fun FlexControlsPanel(
    isTabletop: Boolean,
    capturing: Boolean,
    infoExpanded: Boolean,
    paused: Boolean,
    onToggleInfo: () -> Unit,
    onClose: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onMute: () -> Unit,
    onUserInteract: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mediaAccessEnabled by remember {
        mutableStateOf(MediaRemote.isNotificationAccessEnabled(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mediaAccessEnabled = MediaRemote.isNotificationAccessEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FrostScrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onUserInteract
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top row: close + info tile
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            FlexIconTile(
                onClick = onClose,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.reflection_close),
                    tint = NightstandInk,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .frostedGlass()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleInfo)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.reflection_info_title),
                            color = NightstandInk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                if (capturing) {
                                    R.string.reflection_info_capturing
                                } else {
                                    R.string.reflection_info_idle
                                }
                            ),
                            color = NightstandMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (infoExpanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = NightstandMuted
                    )
                }

                AnimatedVisibility(visible = infoExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isTabletop) {
                            Text(
                                text = stringResource(R.string.reflection_coach),
                                color = NightstandMuted,
                                fontSize = 13.sp
                            )
                        }
                        if (!capturing) {
                            Text(
                                text = stringResource(R.string.reflection_idle),
                                color = NightstandInk,
                                fontSize = 13.sp
                            )
                            Text(
                                text = stringResource(R.string.reflection_pick_app),
                                color = NightstandMuted,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = stringResource(R.string.reflection_drm_tip),
                            color = NightstandMuted,
                            fontSize = 12.sp
                        )
                        if (!mediaAccessEnabled) {
                            Text(
                                text = stringResource(R.string.reflection_media_access),
                                color = NightstandMuted,
                                fontSize = 12.sp
                            )
                            OutlinedButton(
                                onClick = { MediaRemote.openNotificationAccessSettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.reflection_media_access_action),
                                    color = NightstandInk
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (capturing) {
                            Button(
                                onClick = onStopCapture,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NightstandAccent.copy(alpha = 0.85f),
                                    contentColor = NightstandBg
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.reflection_stop))
                            }
                        } else {
                            Button(
                                onClick = onStartCapture,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NightstandAccent.copy(alpha = 0.85f),
                                    contentColor = NightstandBg
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.reflection_start))
                            }
                        }
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.reflection_close),
                                color = NightstandInk
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Transport: skip | play/pause (~2×) | skip — same height as Flex reference
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkipButton(
                forward = false,
                onClick = onSkipBack,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            FlexIconTile(
                onClick = onPlayPause,
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
            ) {
                Icon(
                    imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(R.string.reflection_play_pause),
                    tint = NightstandInk,
                    modifier = Modifier.size(26.dp)
                )
            }

            SkipButton(
                forward = true,
                onClick = onSkipForward,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // Volume row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FlexIconTile(
                onClick = onVolumeDown,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = stringResource(R.string.reflection_volume_down),
                    tint = NightstandInk
                )
            }
            FlexIconTile(
                onClick = onMute,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = stringResource(R.string.reflection_volume_mute),
                    tint = NightstandInk
                )
            }
            FlexIconTile(
                onClick = onVolumeUp,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.reflection_volume_up),
                    tint = NightstandInk
                )
            }
        }
    }
}

@Composable
private fun SkipButton(
    forward: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlexIconTile(
        onClick = onClick,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Replay,
                contentDescription = stringResource(
                    if (forward) R.string.reflection_skip_forward
                    else R.string.reflection_skip_back
                ),
                tint = NightstandInk,
                modifier = Modifier
                    .size(28.dp)
                    .then(if (forward) Modifier.graphicsLayer { scaleX = -1f } else Modifier)
            )
            Text(
                text = "10",
                color = NightstandInk,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FlexIconTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .frostedGlass()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun Modifier.frostedGlass(): Modifier =
    this
        .clip(FrostTileShape)
        .background(FrostTileFill)
        .border(width = 1.dp, color = FrostTileStroke, shape = FrostTileShape)

@Composable
private fun CaptureTextureView(
    onSurfaceReady: (SurfaceTexture, Int, Int, Int) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        val dpi = context.resources.displayMetrics.densityDpi
                        onSurfaceReady(surface, width.coerceAtLeast(1), height.coerceAtLeast(1), dpi)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        val dpi = context.resources.displayMetrics.densityDpi
                        onSurfaceReady(surface, width.coerceAtLeast(1), height.coerceAtLeast(1), dpi)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        onSurfaceDestroyed()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
