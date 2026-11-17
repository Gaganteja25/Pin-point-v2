package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CrowdEntity
import com.example.data.local.CrowdMemberEntity
import com.example.domain.GeofenceEngine
import com.example.domain.PlaceListing
import com.example.ui.theme.AmberStale
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

data class MapRecenterTrigger(
    val latitude: Double,
    val longitude: Double,
    val zoom: Int = 16,
    val nonce: Long = System.currentTimeMillis()
)

private object OsmTileCache {
    val memoryCache = LruCache<String, ImageBitmap>(96)
    val inFlight = mutableSetOf<String>()
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun loadTile(tileUrlTemplate: String, z: Int, x: Int, y: Int): ImageBitmap? {
        val url = tileUrlTemplate
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
            .replace("{s}", "a")

        memoryCache.get(url)?.let { return it }
        synchronized(inFlight) {
            if (!inFlight.add(url)) return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PinpointAndroid/1.0 (breakfreeentertainments0@gmail.com)")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bytes = response.body?.bytes() ?: return@withContext null
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                    val img = bmp.asImageBitmap()
                    memoryCache.put(url, img)
                    img
                }
            } catch (_: Exception) {
                null
            } finally {
                synchronized(inFlight) {
                    inFlight.remove(url)
                }
            }
        }
    }
}

@Composable
fun LeafletMapView(
    crowd: CrowdEntity?,
    members: List<CrowdMemberEntity>,
    places: List<PlaceListing>,
    selfSessionId: String,
    tileUrl: String,
    tileAttribution: String,
    pickCenterMode: Boolean,
    recenterTrigger: MapRecenterTrigger?,
    onMemberTapped: (String) -> Unit,
    onPlaceTapped: (String, Boolean) -> Unit,
    onMapTappedForCenter: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var mapSize by remember { mutableStateOf(IntSize(1, 1)) }

    val selfMember = remember(members, selfSessionId) {
        members.find { it.sessionId == selfSessionId }
    }
    var cameraLat by remember {
        mutableDoubleStateOf(selfMember?.latitude ?: crowd?.geofenceLat ?: 20.5937)
    }
    var cameraLng by remember {
        mutableDoubleStateOf(selfMember?.longitude ?: crowd?.geofenceLng ?: 78.9629)
    }
    var zoomFloat by remember { mutableFloatStateOf(if (selfMember?.latitude != null) 15f else 5f) }
    var hasAutoCentered by remember { mutableStateOf(false) }
    var tileRenderTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(selfMember?.latitude, selfMember?.longitude) {
        if (!hasAutoCentered && selfMember?.latitude != null && selfMember.longitude != null) {
            cameraLat = selfMember.latitude
            cameraLng = selfMember.longitude
            zoomFloat = 15.5f
            hasAutoCentered = true
        }
    }

    LaunchedEffect(recenterTrigger?.nonce) {
        recenterTrigger?.let { trigger ->
            cameraLat = trigger.latitude
            cameraLng = trigger.longitude
            zoomFloat = trigger.zoom.toFloat().coerceIn(3f, 18f)
        }
    }

    val tileZoom = zoomFloat.roundToInt().coerceIn(3, 18)
    val scaleFactor = 2.0.pow((zoomFloat - tileZoom).toDouble()).toFloat()
    val tilePx = 256f * scaleFactor

    // Preload visible OpenStreetMap tiles
    LaunchedEffect(cameraLat, cameraLng, tileZoom, mapSize, tileUrl) {
        if (mapSize.width <= 1 || mapSize.height <= 1) return@LaunchedEffect
        val maxIndex = (1 shl tileZoom)
        val centerTileX = lngToTileX(cameraLng, tileZoom)
        val centerTileY = latToTileY(cameraLat, tileZoom)
        val tilesAcross = ((mapSize.width / tilePx) / 2f).toInt() + 2
        val tilesDown = ((mapSize.height / tilePx) / 2f).toInt() + 2

        val cx = floor(centerTileX).toInt()
        val cy = floor(centerTileY).toInt()
        for (dx in -tilesAcross..tilesAcross) {
            for (dy in -tilesDown..tilesDown) {
                val tx = ((cx + dx) % maxIndex + maxIndex) % maxIndex
                val ty = cy + dy
                if (ty in 0 until maxIndex) {
                    val key = tileUrl
                        .replace("{z}", tileZoom.toString())
                        .replace("{x}", tx.toString())
                        .replace("{y}", ty.toString())
                        .replace("{s}", "a")
                    if (OsmTileCache.memoryCache.get(key) == null) {
                        scope.launch {
                            val loaded = OsmTileCache.loadTile(tileUrl, tileZoom, tx, ty)
                            if (loaded != null) tileRenderTick++
                        }
                    }
                }
            }
        }
    }

    fun latLngToScreen(lat: Double, lng: Double): Offset {
        val centerTileX = lngToTileX(cameraLng, tileZoom)
        val centerTileY = latToTileY(cameraLat, tileZoom)
        val targetTileX = lngToTileX(lng, tileZoom)
        val targetTileY = latToTileY(lat, tileZoom)
        val x = mapSize.width / 2f + ((targetTileX - centerTileX) * tilePx).toFloat()
        val y = mapSize.height / 2f + ((targetTileY - centerTileY) * tilePx).toFloat()
        return Offset(x, y)
    }

    fun screenToLatLng(screenX: Float, screenY: Float): Pair<Double, Double> {
        val centerTileX = lngToTileX(cameraLng, tileZoom)
        val centerTileY = latToTileY(cameraLat, tileZoom)
        val targetTileX = centerTileX + (screenX - mapSize.width / 2f) / tilePx
        val targetTileY = centerTileY + (screenY - mapSize.height / 2f) / tilePx
        return tileYToLat(targetTileY, tileZoom) to tileXToLng(targetTileX, tileZoom)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(DarkNavy)
            .onSizeChanged { mapSize = it }
            .pointerInput(tileZoom, tilePx) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val newZoom = (zoomFloat + ln(zoomChange.toDouble()).toFloat() / ln(2f)).coerceIn(3f, 18f)
                    val curScale = 2.0.pow((zoomFloat - tileZoom).toDouble()).toFloat()
                    val curTilePx = 256f * curScale
                    val dTileX = -pan.x / curTilePx
                    val dTileY = -pan.y / curTilePx
                    val nextTileX = lngToTileX(cameraLng, tileZoom) + dTileX
                    val nextTileY = latToTileY(cameraLat, tileZoom) + dTileY
                    cameraLng = ((tileXToLng(nextTileX, tileZoom) + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                    cameraLat = tileYToLat(nextTileY, tileZoom).coerceIn(-85.0, 85.0)
                    zoomFloat = newZoom
                }
            }
            .pointerInput(pickCenterMode, cameraLat, cameraLng, tileZoom, tilePx) {
                detectTapGestures { tapOffset ->
                    if (pickCenterMode && mapSize.width > 1 && mapSize.height > 1) {
                        val (lat, lng) = screenToLatLng(tapOffset.x, tapOffset.y)
                        onMapTappedForCenter(lat, lng)
                    }
                }
            }
    ) {
        // 1. OpenStreetMap Tiles + Geofence Circle Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Reference tick so tile loads recompose canvas
            @Suppress("UNUSED_VARIABLE")
            val tick = tileRenderTick

            val gridStep = 48.dp.toPx()
            val gridColor = Color(0xFF1C294A)
            var gx = 0f
            while (gx < size.width) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                gx += gridStep
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                gy += gridStep
            }

            val maxIndex = (1 shl tileZoom)
            val centerTileX = lngToTileX(cameraLng, tileZoom)
            val centerTileY = latToTileY(cameraLat, tileZoom)
            val cx = floor(centerTileX).toInt()
            val cy = floor(centerTileY).toInt()
            val tilesAcross = ((size.width / tilePx) / 2f).toInt() + 2
            val tilesDown = ((size.height / tilePx) / 2f).toInt() + 2

            for (dx in -tilesAcross..tilesAcross) {
                for (dy in -tilesDown..tilesDown) {
                    val rawX = cx + dx
                    val rawY = cy + dy
                    val tx = ((rawX % maxIndex) + maxIndex) % maxIndex
                    if (rawY in 0 until maxIndex) {
                        val url = tileUrl
                            .replace("{z}", tileZoom.toString())
                            .replace("{x}", tx.toString())
                            .replace("{y}", rawY.toString())
                            .replace("{s}", "a")
                        val img = OsmTileCache.memoryCache.get(url)
                        if (img != null) {
                            val left = size.width / 2f + ((rawX - centerTileX) * tilePx).toFloat()
                            val top = size.height / 2f + ((rawY - centerTileY) * tilePx).toFloat()
                            drawImage(
                                image = img,
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(tilePx.roundToInt() + 1, tilePx.roundToInt() + 1)
                            )
                        }
                    }
                }
            }

            // Geofence boundary circle
            if (crowd != null) {
                val gfPos = latLngToScreen(crowd.geofenceLat, crowd.geofenceLng)
                val metersPerPixel =
                    (156543.03392 * cos(Math.toRadians(crowd.geofenceLat))) / 2.0.pow(zoomFloat.toDouble())
                val radiusPx = (crowd.geofenceRadiusMeters / metersPerPixel.coerceAtLeast(0.01)).toFloat()
                    .coerceIn(12f, 4000f)

                drawCircle(
                    color = ElectricBlue.copy(alpha = 0.14f),
                    radius = radiusPx,
                    center = gfPos
                )
                drawCircle(
                    color = ElectricBlue,
                    radius = radiusPx,
                    center = gfPos,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                    )
                )
                drawCircle(color = Color.White, radius = 7.dp.toPx(), center = gfPos)
                drawCircle(color = ElectricBlue, radius = 4.5.dp.toPx(), center = gfPos)
            }
        }

        // 2. Nearby & Sponsored Place Pins
        places.take(25).forEach { place ->
            val pos = latLngToScreen(place.latitude, place.longitude)
            if (pos.x in -60f..(mapSize.width + 60f) && pos.y in -60f..(mapSize.height + 60f)) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(pos.x.roundToInt() - 14, pos.y.roundToInt() - 28) }
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (place.isSponsored) AmberStale else EmeraldLive)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { onPlaceTapped(place.id, place.isSponsored) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (place.isSponsored) "★" else "•",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Crowd Member Avatar Pins
        val now = System.currentTimeMillis()
        members.forEach { member ->
            if (member.latitude != null && member.longitude != null) {
                val pos = latLngToScreen(member.latitude, member.longitude)
                val borderColor = when {
                    !member.isSharingLocation || member.locationStatus == "STOPPED" -> Color(0xFF64748B)
                    GeofenceEngine.isStaleLocation(member.lastUpdatedAt, now) -> AmberStale
                    else -> EmeraldLive
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset { IntOffset(pos.x.roundToInt() - 42, pos.y.roundToInt() - 56) }
                        .clickable { onMemberTapped(member.sessionId) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(parseColorSafe(member.avatarColorHex))
                            .border(3.dp, borderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(member.avatarEmoji, fontSize = 18.sp)
                    }
                    Spacer(Modifier.size(2.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = DarkNavy.copy(alpha = 0.88f)
                    ) {
                        Text(
                            text = member.displayName + if (member.sessionId == selfSessionId) " (You)" else "",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // 4. Zoom Controls (+ / -)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
        ) {
            Column {
                IconButton(onClick = { zoomFloat = (zoomFloat + 1f).coerceAtMost(18f) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom in")
                }
                IconButton(onClick = { zoomFloat = (zoomFloat - 1f).coerceAtLeast(3f) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                }
            }
        }

        // 5. OpenStreetMap Attribution Badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DarkNavy.copy(alpha = 0.78f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 78.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = tileAttribution.replace("&copy;", "©"),
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun lngToTileX(lng: Double, zoom: Int): Double {
    return (lng + 180.0) / 360.0 * (1 shl zoom)
}

private fun latToTileY(lat: Double, zoom: Int): Double {
    val clamped = lat.coerceIn(-85.05112878, 85.05112878)
    val rad = Math.toRadians(clamped)
    return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * (1 shl zoom)
}

private fun tileXToLng(tileX: Double, zoom: Int): Double {
    return tileX / (1 shl zoom) * 360.0 - 180.0
}

private fun tileYToLat(tileY: Double, zoom: Int): Double {
    val n = PI - 2.0 * PI * tileY / (1 shl zoom)
    return Math.toDegrees(atan(sinh(n)))
}
