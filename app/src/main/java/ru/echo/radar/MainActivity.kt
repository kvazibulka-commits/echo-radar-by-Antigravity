package ru.echo.radar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EchoStateRepository.init(this)
        setContent {
            EchoRadarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0E14)
                ) {
                    EchoMainScreen()
                }
            }
        }
    }
}

@Composable
fun EchoRadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0B0E14),
            surface = Color(0xFF121722),
            primary = Color(0xFF00F0FF),
            secondary = Color(0xFFA855F7),
            tertiary = Color(0xFFF59E0B)
        ),
        content = content
    )
}

// -------------------------------------------------------------
// UI DATA WRAPPER FOR CAROUSEL & RADAR SYNCHRONIZATION
// -------------------------------------------------------------
sealed class RadarItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val angleDeg: Float
    abstract val ringFactor: Float
    abstract val colorHex: Long
    abstract val distanceMeters: Int

    data class SignalNode(val signal: NearbyEchoSignal) : RadarItem() {
        override val id: String get() = signal.id
        override val title: String get() = signal.title
        override val subtitle: String get() = signal.artist
        override val angleDeg: Float get() = signal.angleDeg
        override val ringFactor: Float get() = signal.ringFactor
        override val colorHex: Long get() = signal.colorHex
        override val distanceMeters: Int get() = signal.compatibility
    }

    data class CapsuleNode(val capsule: EchoCapsule) : RadarItem() {
        override val id: String get() = capsule.id
        override val title: String get() = capsule.title
        override val subtitle: String get() = capsule.artist
        override val angleDeg: Float get() = capsule.angleDeg
        override val ringFactor: Float get() = capsule.ringFactor
        override val colorHex: Long get() = 0xFFF59E0BL
        override val distanceMeters: Int get() = 40
    }
}

fun formatPoeticDistance(ringFactor: Float): String {
    return when {
        ringFactor < 0.38f -> "В одном дворе • ~25 м"
        ringFactor < 0.70f -> "Через улицу • ~70 м"
        else -> "В квартале • ~140 м"
    }
}

// -------------------------------------------------------------
// MAIN SCREEN: PURE NOCTURNE ATMOSPHERE
// -------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EchoMainScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val currentTrack by EchoStateRepository.currentTrack.collectAsState()
    val nearbySignals by EchoStateRepository.nearbySignals.collectAsState()
    val capsules by EchoStateRepository.capsules.collectAsState()
    val isPresenceActive by EchoStateRepository.presenceActive.collectAsState()
    val serverHost by EchoStateRepository.serverHost.collectAsState()
    val connectionStatusStr by EchoStateRepository.connectionStatus.collectAsState()
    val isConnected = !connectionStatusStr.contains("Не подключено") && !connectionStatusStr.contains("Сбой")

    // Dialog states
    var showMyTrackDialog by remember { mutableStateOf(false) }
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var showManualTrackDialog by remember { mutableStateOf(false) }
    var showDropCapsuleDialog by remember { mutableStateOf(false) }
    var selectedItemForSheet by remember { mutableStateOf<RadarItem?>(null) }

    // Combine signals & capsules into a unified item list for the bottom dock
    val radarItems = remember(nearbySignals, capsules) {
        val list = mutableListOf<RadarItem>()
        nearbySignals.forEach { list.add(RadarItem.SignalNode(it)) }
        capsules.forEach { list.add(RadarItem.CapsuleNode(it)) }
        list
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { if (radarItems.isEmpty()) 1 else radarItems.size }
    )

    // Current focused item on the radar (drives the neon laser beam)
    val focusedItem = remember(radarItems, pagerState.currentPage) {
        if (radarItems.isNotEmpty() && pagerState.currentPage in radarItems.indices) {
            radarItems[pagerState.currentPage]
        } else null
    }

    // Continuous smooth sweep angle (0 -> 360 deg)
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    // Breathing pulse for beacons
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BeaconPulse"
    )

    // Photon travel along the laser beam
    val photonProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PhotonProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 1. TOP BAR (56dp) — Minimalist & Clean, Zero Clutter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Typographic sparse logo
                Text(
                    text = "Э  Х  О",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )

                // Interactive Live Capsule
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMyTrackDialog = true
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showServerSettingsDialog = true
                            }
                        ),
                    color = Color(0xFF121722).copy(alpha = 0.9f),
                    border = BorderStroke(0.8.dp, if (isConnected) Color(0xFF1E2638) else Color(0xFFE11D48))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF00F0FF) else Color(0xFF64748B))
                        )
                        Text(
                            text = if (isConnected) "${radarItems.size} в эфире" else "Оффлайн",
                            color = if (isConnected) Color(0xFF00F0FF) else Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 2. CENTRAL SONAR SCENE (Taking remaining height up to Thumb Zone)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(radarItems) {
                            detectTapGestures { tapOffset ->
                                val center = Offset(size.width / 2f, size.height / 2f - 15.dp.toPx())
                                val maxR = minOf(size.width, size.height) * 0.44f

                                // Check tap on center dot (user's beacon)
                                val distFromCenter = sqrt(
                                    (tapOffset.x - center.x) * (tapOffset.x - center.x) +
                                    (tapOffset.y - center.y) * (tapOffset.y - center.y)
                                )
                                if (distFromCenter < 35.dp.toPx()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMyTrackDialog = true
                                    return@detectTapGestures
                                }

                                // Check tap on beacons
                                var clickedIndex: Int? = null
                                var minTapDist = 45.dp.toPx()

                                radarItems.forEachIndexed { idx, item ->
                                    // Add privacy angular jitter (±15 deg based on ID hash)
                                    val jitter = (item.id.hashCode() % 31 - 15) * 0.4f
                                    val rad = Math.toRadians((item.angleDeg + jitter).toDouble())
                                    val r = maxR * item.ringFactor.coerceIn(0.25f, 0.92f)
                                    val bx = center.x + (r.toDouble() * cos(rad)).toFloat()
                                    val by = center.y + (r.toDouble() * sin(rad)).toFloat()
                                    val d = sqrt((tapOffset.x - bx) * (tapOffset.x - bx) + (tapOffset.y - by) * (tapOffset.y - by))
                                    if (d < minTapDist) {
                                        minTapDist = d
                                        clickedIndex = idx
                                    }
                                }

                                if (clickedIndex != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(clickedIndex!!)
                                    }
                                    selectedItemForSheet = radarItems[clickedIndex!!]
                                }
                            }
                        }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f - 15.dp.toPx())
                    val maxR = minOf(size.width, size.height) * 0.44f

                    // A. Three concentric atmospheric range rings
                    listOf(0.35f, 0.65f, 0.95f).forEach { ratio ->
                        drawCircle(
                            color = Color(0xFF1E2638).copy(alpha = 0.55f),
                            radius = maxR * ratio,
                            center = center,
                            style = Stroke(width = 0.8.dp.toPx())
                        )
                    }

                    // Crosshair axis ticks
                    drawLine(
                        color = Color(0xFF1E2638).copy(alpha = 0.35f),
                        start = Offset(center.x - maxR, center.y),
                        end = Offset(center.x + maxR, center.y),
                        strokeWidth = 0.6.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF1E2638).copy(alpha = 0.35f),
                        start = Offset(center.x, center.y - maxR),
                        end = Offset(center.x, center.y + maxR),
                        strokeWidth = 0.6.dp.toPx()
                    )

                    // B. Soft Conical Sweep Gradient Beam (35° trail)
                    val trailAngle = 35f
                    val beamColor = if (isConnected) Color(0xFF00F0FF) else Color(0xFF64748B)

                    // Draw conical arc trail
                    for (step in 0..14) {
                        val frac = step / 14f
                        val angleStep = sweepAngle - (trailAngle * (1f - frac))
                        val radStep = Math.toRadians(angleStep.toDouble())
                        val xStep = center.x + (maxR * cos(radStep)).toFloat()
                        val yStep = center.y + (maxR * sin(radStep)).toFloat()
                        drawLine(
                            color = beamColor.copy(alpha = 0.015f + 0.16f * frac),
                            start = center,
                            end = Offset(xStep, yStep),
                            strokeWidth = (maxR * 0.05f).coerceAtLeast(2f)
                        )
                    }

                    // Bright leading ray edge
                    val leadingRad = Math.toRadians(sweepAngle.toDouble())
                    val leadEndX = center.x + (maxR * cos(leadingRad)).toFloat()
                    val leadEndY = center.y + (maxR * sin(leadingRad)).toFloat()
                    drawLine(
                        color = beamColor.copy(alpha = 0.75f),
                        start = center,
                        end = Offset(leadEndX, leadEndY),
                        strokeWidth = 1.3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // C. Selected Beacon Laser Resonance Beam + Photon Particle
                    if (focusedItem != null) {
                        val jitter = (focusedItem.id.hashCode() % 31 - 15) * 0.4f
                        val targetRad = Math.toRadians((focusedItem.angleDeg + jitter).toDouble())
                        val targetR = maxR * focusedItem.ringFactor.coerceIn(0.25f, 0.92f)
                        val targetX = center.x + (targetR.toDouble() * cos(targetRad)).toFloat()
                        val targetY = center.y + (targetR.toDouble() * sin(targetRad)).toFloat()

                        // Cyan glow background line
                        drawLine(
                            color = Color(0xFF00F0FF).copy(alpha = 0.25f),
                            start = center,
                            end = Offset(targetX, targetY),
                            strokeWidth = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Core bright laser
                        drawLine(
                            color = Color(0xFF00F0FF).copy(alpha = 0.85f),
                            start = center,
                            end = Offset(targetX, targetY),
                            strokeWidth = 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Traveling photon pulse
                        val photonX = center.x + (targetX - center.x) * photonProgress
                        val photonY = center.y + (targetY - center.y) * photonProgress
                        drawCircle(
                            color = Color.White,
                            radius = 2.8.dp.toPx(),
                            center = Offset(photonX, photonY)
                        )
                        drawCircle(
                            color = Color(0xFF00F0FF).copy(alpha = 0.6f),
                            radius = 6.dp.toPx(),
                            center = Offset(photonX, photonY)
                        )
                    }

                    // D. Render All Beacons (Clean glowing dots, ZERO text collision on canvas)
                    radarItems.forEach { item ->
                        val jitter = (item.id.hashCode() % 31 - 15) * 0.4f
                        val rad = Math.toRadians((item.angleDeg + jitter).toDouble())
                        val r = maxR * item.ringFactor.coerceIn(0.25f, 0.92f)
                        val bx = center.x + (r.toDouble() * cos(rad)).toFloat()
                        val by = center.y + (r.toDouble() * sin(rad)).toFloat()

                        val isFocused = (item.id == focusedItem?.id)
                        val beaconColor = Color(item.colorHex)

                        // Outer breathing aura
                        drawCircle(
                            color = beaconColor.copy(alpha = if (isFocused) 0.35f else 0.18f),
                            radius = (if (isFocused) 15.dp.toPx() else 9.dp.toPx()) * beaconPulse,
                            center = Offset(bx, by)
                        )
                        // Middle aura
                        drawCircle(
                            color = beaconColor.copy(alpha = 0.65f),
                            radius = if (isFocused) 6.5.dp.toPx() else 4.5.dp.toPx(),
                            center = Offset(bx, by)
                        )
                        // Bright core
                        drawCircle(
                            color = Color.White,
                            radius = if (isFocused) 3.dp.toPx() else 2.dp.toPx(),
                            center = Offset(bx, by)
                        )
                    }

                    // E. Central Node (User's presence)
                    drawCircle(
                        color = Color(0xFF00F0FF).copy(alpha = 0.22f),
                        radius = 16.dp.toPx() * beaconPulse,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF00F0FF),
                        radius = 6.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = center
                    )
                }
            }

            // 3. BOTTOM THUMB ZONE (20% height, max air & ergonomics)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // A. Horizontal Pager with Card Peek (82% width)
                    Box(modifier = Modifier.weight(1f)) {
                        if (radarItems.isEmpty()) {
                            // Empty State Card
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                color = Color(0xFF121722).copy(alpha = 0.85f),
                                border = BorderStroke(0.8.dp, Color(0xFF1E2638))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "🌑",
                                        fontSize = 20.sp
                                    )
                                    Column {
                                        Text(
                                            text = "Ночной эфир тих",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Включите музыку на телефоне или ПК",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                pageSpacing = 10.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                            ) { page ->
                                val item = radarItems[page]
                                val isSelected = (item.id == focusedItem?.id)

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedItemForSheet = item
                                        },
                                    color = Color(0xFF121722).copy(alpha = 0.90f),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.2.dp else 0.8.dp,
                                        color = if (isSelected) Color(0xFF00F0FF) else Color(0xFF1E2638)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Generative vinyl icon with 12dp protruding record affordance
                                        Box(
                                            modifier = Modifier.size(46.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            // Protruding dark vinyl record on the right
                                            Canvas(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .offset(x = 12.dp)
                                            ) {
                                                drawCircle(
                                                    color = Color(0xFF1E2638),
                                                    radius = size.minDimension / 2f
                                                )
                                                drawCircle(
                                                    color = Color(0xFF0B0E14),
                                                    radius = size.minDimension / 4f
                                                )
                                            }
                                            // Cover sleeve
                                            Surface(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                color = Color(item.colorHex).copy(alpha = 0.25f),
                                                border = BorderStroke(0.6.dp, Color(item.colorHex))
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "✦",
                                                        color = Color(item.colorHex),
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }

                                        // Track info with poetic distance ring
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = item.title,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.subtitle,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatPoeticDistance(item.ringFactor),
                                                color = Color(0xFF00F0FF),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        // Tap prompt arrow
                                        Text(
                                            text = "›",
                                            color = Color(0xFF64748B),
                                            fontSize = 20.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // B. Single Floating Capsule Action Button (48x48dp, Warm Amber)
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showDropCapsuleDialog = true
                            },
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "⏳",
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // MODAL BOTTOM SHEET: TASTE OVERLAP ("МОСТ СОЗВУЧИЯ")
    // -------------------------------------------------------------
    if (selectedItemForSheet != null) {
        val item = selectedItemForSheet!!
        ModalBottomSheet(
            onDismissRequest = { selectedItemForSheet = null },
            containerColor = Color(0xFF121722),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF334155))
                )
            }
        ) {
            TasteOverlapContent(
                item = item,
                onDismiss = { selectedItemForSheet = null }
            )
        }
    }

    // -------------------------------------------------------------
    // DIALOG: "ВАШ ЭФИР" (CURRENT TRACK & PLAYER STATUS)
    // -------------------------------------------------------------
    if (showMyTrackDialog) {
        AlertDialog(
            onDismissRequest = { showMyTrackDialog = false },
            containerColor = Color(0xFF121722),
            title = {
                Text("Ваш эфир", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (!currentTrack.title.isNullOrBlank())
                            "В наушниках: ${currentTrack.artist} — ${currentTrack.title}"
                        else
                            "Ожидание музыки... Включите плеер на телефоне или ПК",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.5.sp
                    )

                    HorizontalDivider(color = Color(0xFF1E2638))

                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                            showMyTrackDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🎧 Проверить слух эфира (Разрешения)", color = Color(0xFF00F0FF))
                    }

                    OutlinedButton(
                        onClick = {
                            showMyTrackDialog = false
                            showManualTrackDialog = true
                        },
                        border = BorderStroke(0.8.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✍ Ввести трек вручную", color = Color.White)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Вещание присутствия", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = isPresenceActive,
                            onCheckedChange = { EchoStateRepository.togglePresence() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00F0FF),
                                checkedTrackColor = Color(0xFF00F0FF).copy(alpha = 0.25f)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMyTrackDialog = false }) {
                    Text("Закрыть", color = Color(0xFF00F0FF))
                }
            }
        )
    }

    // -------------------------------------------------------------
    // SECRET DIALOG: SERVER SETTINGS (HIDDEN FROM MAIN UI)
    // -------------------------------------------------------------
    if (showServerSettingsDialog) {
        var tempHost by remember { mutableStateOf(serverHost) }
        AlertDialog(
            onDismissRequest = { showServerSettingsDialog = false },
            containerColor = Color(0xFF121722),
            title = {
                Text("Частота сервера (Секретно)", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Адрес узла ЭХО в локальной сети или Cloudflare:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = tempHost,
                        onValueChange = { tempHost = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00F0FF),
                            unfocusedBorderColor = Color(0xFF1E2638),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        EchoStateRepository.setServerHost(tempHost.trim())
                        showServerSettingsDialog = false
                    }
                ) {
                    Text("Сохранить", color = Color(0xFF00F0FF))
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerSettingsDialog = false }) {
                    Text("Отмена", color = Color(0xFF64748B))
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG: MANUAL TRACK INPUT (TEST FALLBACK)
    // -------------------------------------------------------------
    if (showManualTrackDialog) {
        var artist by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualTrackDialog = false },
            containerColor = Color(0xFF121722),
            title = { Text("Ручной ввод трека", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Исполнитель") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название трека") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            EchoStateRepository.updateDetectedMusic(
                                artist = artist.ifBlank { "Неизвестный автор" },
                                title = title,
                                packageName = "manual"
                            )
                        }
                        showManualTrackDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF))
                ) {
                    Text("В эфир", color = Color(0xFF0B0E14))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualTrackDialog = false }) {
                    Text("Отмена", color = Color(0xFF64748B))
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DIALOG: DROP CAPSULE (ТАЙНИК)
    // -------------------------------------------------------------
    if (showDropCapsuleDialog) {
        var message by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDropCapsuleDialog = false },
            containerColor = Color(0xFF121722),
            title = { Text("Оставить музыкальный тайник", color = Color(0xFFF59E0B)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Капсула с вашим треком и тайным посланием сохранится в этой точке города на 24 часа.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Шёпот тайника (записка)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        EchoStateRepository.dropCapsule(
                            artist = currentTrack.artist ?: "Ночной странник",
                            title = currentTrack.title ?: "Безмолвие",
                            secretMessage = message.ifBlank { "Тихий след в ночном эфире." }
                        )
                        showDropCapsuleDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) {
                    Text("Запечатать", color = Color(0xFF0B0E14), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDropCapsuleDialog = false }) {
                    Text("Отмена", color = Color(0xFF64748B))
                }
            }
        )
    }
}

// -------------------------------------------------------------
// TASTE OVERLAP CONTENT (THE "МОСТ СОЗВУЧИЯ" MODAL BOTTOM SHEET)
// -------------------------------------------------------------
@Composable
fun TasteOverlapContent(
    item: RadarItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Spring animation for vinyl sleeve reveal
    var isVinylRevealed by remember { mutableStateOf(false) }
    val vinylOffset by animateDpAsState(
        targetValue = if (isVinylRevealed) 68.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "VinylSlide"
    )

    // Infinite rotation for spinning vinyl record
    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SpinAngle"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header with Vinyl Easter Egg Affordance
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Interactive Vinyl Sleeve Box
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isVinylRevealed = !isVinylRevealed
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Sliding and spinning vinyl record
                    Canvas(
                        modifier = Modifier
                            .size(54.dp)
                            .offset(x = vinylOffset)
                            .rotate(vinylRotation)
                    ) {
                        drawCircle(color = Color(0xFF1E2638))
                        drawCircle(color = Color(0xFF0F141C), radius = size.minDimension * 0.42f)
                        drawCircle(color = Color(item.colorHex), radius = size.minDimension * 0.18f)
                    }

                    // Card sleeve
                    Surface(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = Color(item.colorHex).copy(alpha = 0.25f),
                        border = BorderStroke(0.8.dp, Color(item.colorHex))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "✦", color = Color(item.colorHex), fontSize = 22.sp)
                        }
                    }
                }

                Column {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                    Text(
                        text = formatPoeticDistance(item.ringFactor),
                        color = Color(0xFF00F0FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            IconButton(onClick = onDismiss) {
                Text("✕", color = Color(0xFF64748B), fontSize = 16.sp)
            }
        }

        HorizontalDivider(color = Color(0xFF1E2638))

        // 2. Poetic Archetype & Taste Overlap Essay (0ms latency local fallback)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0B0E14).copy(alpha = 0.6f),
            border = BorderStroke(0.6.dp, Color(0xFF1E2638))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (item is RadarItem.SignalNode) item.signal.poeticName else "Тайник полуночи",
                        color = Color(0xFF00F0FF),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• 88% созвучие",
                        color = Color(0xFF64748B),
                        fontSize = 11.5.sp
                    )
                }
                Text(
                    text = if (item is RadarItem.SignalNode)
                        item.signal.resonanceStory.ifBlank { "Вы оба слушаете глубокую вечернюю музыку в этот тихий час." }
                    else
                        (item as RadarItem.CapsuleNode).capsule.secretMessage,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.5.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 18.sp
                )
            }
        }

        // 3. Five Custom Vector Wireframe Glyphs (NO colored system emojis!)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "ОТПРАВИТЬ СИМВОЛ РЕЗОНАНСА",
                color = Color(0xFF64748B),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    Pair("✦", "Волна"),
                    Pair("✨", "Искра"),
                    Pair("🌙", "Шёпот"),
                    Pair("🌲", "Лес"),
                    Pair("⚡", "Резонанс")
                ).forEach { (glyph, label) ->
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                EchoStateRepository.sendReaction(item.id, glyph)
                            },
                        color = Color(0xFF161D2C),
                        border = BorderStroke(0.8.dp, Color(0xFF1E2638))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = glyph,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = label,
                                color = Color(0xFF64748B),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. Hero Action: 1-Click Shazam-Style Stream Launch
        Button(
            onClick = {
                val query = URLEncoder.encode("${item.subtitle} ${item.title}", "UTF-8")
                val yandexMusicUri = Uri.parse("yandexmusic://search?text=$query")
                val intent = Intent(Intent.ACTION_VIEW, yandexMusicUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.yandex.ru/search?text=$query"))
                    context.startActivity(webIntent)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "▷  Слушать в Яндекс Музыке / Плеере",
                color = Color(0xFF0B0E14),
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
        }
    }
}
