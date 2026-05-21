package com.maxcoder.lastmlkitscanner.presentation.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.maxcoder.lastmlkitscanner.presentation.destinations.ResultScreenDestination
//import com.maxcoder.lastmlkitscanner.destinations.ResultScreenDestination
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@RootNavGraph(start = true)
@Destination
@Composable
fun ScannerScreen(
    navigator: DestinationsNavigator,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val cameraControlRef = remember { mutableStateOf<CameraControl?>(null) }

    LaunchedEffect(state.isTorchOn) {
        cameraControlRef.value?.enableTorch(state.isTorchOn)
    }

    // Reset analyzer when returning from ResultScreen
    var pendingReset by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingReset) {
                viewModel.reset()
                pendingReset = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScannerContract.Effect.NavigateToResult -> {
                    pendingReset = true
                    navigator.navigate(ResultScreenDestination(card = effect.card))
                }
                ScannerContract.Effect.NavigateBack -> navigator.navigateUp()
            }
        }
    }

    val dm = LocalContext.current.resources.displayMetrics
    val screenW = dm.widthPixels
    val screenH = dm.heightPixels

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (hasCameraPermission) {
            val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setResolutionSelector(
                                    ResolutionSelector.Builder()
                                        .setResolutionStrategy(
                                            ResolutionStrategy(
                                                Size(1280, 720),
                                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                            )
                                        ).build()
                                )
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build().apply {
                                    setAnalyzer(
                                        ContextCompat.getMainExecutor(ctx),
                                        viewModel.imageAnalyzer()
                                    )
                                }
                            try {
                                provider.unbindAll()
                                val camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                                cameraControlRef.value = camera.cameraControl
                                viewModel.setFrameHint(screenW, screenH)
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        CardCutoutOverlay(
            cardFound = state.cardDetected || state.voteCount >= state.totalVotes,
            voteCount = state.voteCount,
            totalVotes = state.totalVotes,
            modifier = Modifier.fillMaxSize()
        )

        // Top bar: Close (left) + Torch (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.onEvent(ScannerContract.Event.Close) },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            IconButton(
                onClick = { viewModel.onEvent(ScannerContract.Event.ToggleTorch) },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = if (state.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    tint = if (state.isTorchOn) Color(0xFFFFD600) else Color.White
                )
            }
        }
    }
}

@Composable
private fun CardCutoutOverlay(
    cardFound: Boolean,
    voteCount: Int,
    totalVotes: Int,
    modifier: Modifier = Modifier
) {
    val cornerAlpha by animateFloatAsState(
        targetValue = if (cardFound) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "cornerAlpha"
    )

    BoxWithConstraints(modifier = modifier) {
        val cardWidth = maxWidth * 0.85f
        val cardHeight = cardWidth / 1.586f
        val topOffset = (maxHeight - cardHeight) / 2f

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val cw = cardWidth.toPx()
            val ch = cardHeight.toPx()
            val left = (size.width - cw) / 2f
            val top = (size.height - ch) / 2f
            val right = left + cw
            val bottom = top + ch
            val radius = CornerRadius(16.dp.toPx())
            val borderWidth = 2.dp.toPx()
            val cornerLen = minOf(cw, ch) * 0.12f
            val cornerStroke = 3.dp.toPx()

            // Dark overlay 70% opacity
            drawRect(Color.Black.copy(alpha = 0.70f))

            // Clear card cutout
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = GeometrySize(cw, ch),
                cornerRadius = radius,
                blendMode = BlendMode.Clear
            )

            // White 2dp border
            drawRoundRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = GeometrySize(cw, ch),
                cornerRadius = radius,
                style = Stroke(width = borderWidth)
            )

//            // Green L-corners, fade in when card found
//            if (cornerAlpha > 0f) {
//                val green = Color(0xFF4CAF50).copy(alpha = cornerAlpha)
//                // Top-left
//                drawLine(green, Offset(left, top + cornerLen), Offset(left, top), cornerStroke, StrokeCap.Square)
//                drawLine(green, Offset(left, top), Offset(left + cornerLen, top), cornerStroke, StrokeCap.Square)
//                // Top-right
//                drawLine(green, Offset(right - cornerLen, top), Offset(right, top), cornerStroke, StrokeCap.Square)
//                drawLine(green, Offset(right, top), Offset(right, top + cornerLen), cornerStroke, StrokeCap.Square)
//                // Bottom-left
//                drawLine(green, Offset(left, bottom - cornerLen), Offset(left, bottom), cornerStroke, StrokeCap.Square)
//                drawLine(green, Offset(left, bottom), Offset(left + cornerLen, bottom), cornerStroke, StrokeCap.Square)
//                // Bottom-right
//                drawLine(green, Offset(right - cornerLen, bottom), Offset(right, bottom), cornerStroke, StrokeCap.Square)
//                drawLine(green, Offset(right, bottom - cornerLen), Offset(right, bottom), cornerStroke, StrokeCap.Square)
//            }
        }

        // "Place card inside frame" hint above the card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topOffset - 40.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "Place card inside frame",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp
            )
        }

        // Vote progress below the card frame
        if (voteCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topOffset + cardHeight + 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "$voteCount / $totalVotes",
                    color = Color(0xFF4CAF50),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 4.sp
                )
            }
        }
    }
}