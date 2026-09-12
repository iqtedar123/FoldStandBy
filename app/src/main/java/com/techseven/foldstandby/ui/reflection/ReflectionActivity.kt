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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.techseven.foldstandby.R
import com.techseven.foldstandby.reflection.ReflectionCaptureService
import com.techseven.foldstandby.ui.adaptive.rememberTentFoldLayout
import com.techseven.foldstandby.ui.theme.FoldStandByTheme
import com.techseven.foldstandby.ui.theme.NightstandAccent
import com.techseven.foldstandby.ui.theme.NightstandBg
import com.techseven.foldstandby.ui.theme.NightstandInk
import com.techseven.foldstandby.ui.theme.NightstandMuted

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
    var controlsVisible by remember { mutableStateOf(!capturing) }

    // Immersive reflection: hide tips/buttons once capture is running
    LaunchedEffect(capturing) {
        controlsVisible = !capturing
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightstandBg)
            .safeDrawingPadding()
    ) {
        // Reflection visuals always stay on while capturing
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
                        .blur(16.dp)
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
                            drawRect(Color.Black.copy(alpha = 0.55f))
                        }
                )
            }
        }

        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isTabletop) {
                        Text(
                            text = stringResource(R.string.reflection_coach),
                            color = NightstandMuted,
                            fontSize = 14.sp
                        )
                    }
                    if (!capturing) {
                        Text(
                            text = stringResource(R.string.reflection_idle),
                            color = NightstandInk,
                            fontSize = 15.sp
                        )
                        Text(
                            text = stringResource(R.string.reflection_pick_app),
                            color = NightstandMuted,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = stringResource(R.string.reflection_drm_tip),
                        color = NightstandMuted,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (capturing) {
                        Button(
                            onClick = onStopCapture,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NightstandAccent,
                                contentColor = NightstandBg
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.reflection_stop))
                        }
                    } else {
                        Button(
                            onClick = onStartCapture,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NightstandAccent,
                                contentColor = NightstandBg
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.reflection_start))
                        }
                    }
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.reflection_close), color = NightstandInk)
                    }
                }
            }
        }

        IconButton(
            onClick = { controlsVisible = !controlsVisible },
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
                contentDescription = stringResource(
                    if (controlsVisible) {
                        R.string.reflection_hide_controls
                    } else {
                        R.string.reflection_show_controls
                    }
                )
            )
        }
    }
}

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
