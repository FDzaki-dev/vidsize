package com.example.videoresizer

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One row of a device video as surfaced by [VideoPickerScreen] below.
 * Deliberately a separate small type from anything in VideoResizer.kt/
 * VideoHistoryStore.kt — this is purely a MediaStore listing row, it never
 * gets persisted anywhere.
 */
private data class DeviceVideo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val bucketId: String,
    val bucketName: String
)

private data class DeviceFolder(
    val bucketId: String,
    val bucketName: String,
    val itemCount: Int,
    val totalDurationMs: Long,
    val totalSizeBytes: Long,
    val coverVideo: DeviceVideo?
)

private enum class PickerSort(val label: String) {
    NEWEST("Terbaru"),
    OLDEST("Terlama"),
    NAME_ASC("Nama (A-Z)"),
    SIZE_DESC("Ukuran terbesar")
}

private enum class PickerTab { VIDEOS, FOLDERS }

private fun neededVideoPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_VIDEO
    else android.Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasVideoReadPermission(context: Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, neededVideoPermission()) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Queries every video MediaStore knows about (across all folders/apps —
 * Camera, Download, WhatsApp, etc., matching what the OS Photo Picker
 * itself would surface), newest-first. Read-only; no write permission
 * needed. Runs on IO — a cursor walk over a large library is real disk I/O.
 */
private suspend fun queryDeviceVideos(context: Context): List<DeviceVideo> = withContext(Dispatchers.IO) {
    val results = mutableListOf<DeviceVideo>()
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.BUCKET_ID,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
    )
    runCatching {
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                results.add(
                    DeviceVideo(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameCol) ?: "video_$id",
                        durationMs = cursor.getLong(durCol),
                        width = cursor.getInt(wCol),
                        height = cursor.getInt(hCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        dateAddedSec = cursor.getLong(dateCol),
                        bucketId = cursor.getString(bucketIdCol) ?: "",
                        bucketName = cursor.getString(bucketNameCol) ?: "Video"
                    )
                )
            }
        }
    }
    results
}

private fun formatPickerDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

private fun formatPickerSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format(Locale.US, "%.2f GB", mb / 1024.0)
    else String.format(Locale.US, "%.1f MB", mb)
}

// Indonesian day/month labels ("24 Juli, 2026"), matching the app's existing
// Indonesian-language UI strings rather than the device's raw locale.
private val pickerDateFormat = SimpleDateFormat("d MMMM, yyyy", Locale("in", "ID"))
private fun formatPickerDate(epochSec: Long): String =
    runCatching { pickerDateFormat.format(Date(epochSec * 1000)) }.getOrDefault("-")

private fun sortedVideos(videos: List<DeviceVideo>, sort: PickerSort): List<DeviceVideo> = when (sort) {
    PickerSort.NEWEST -> videos.sortedByDescending { it.dateAddedSec }
    PickerSort.OLDEST -> videos.sortedBy { it.dateAddedSec }
    PickerSort.NAME_ASC -> videos.sortedBy { it.displayName.lowercase(Locale.ROOT) }
    PickerSort.SIZE_DESC -> videos.sortedByDescending { it.sizeBytes }
}

private fun buildFolders(videos: List<DeviceVideo>): List<DeviceFolder> =
    videos.groupBy { it.bucketId to it.bucketName }
        .map { (key, items) ->
            DeviceFolder(
                bucketId = key.first,
                bucketName = key.second,
                itemCount = items.size,
                totalDurationMs = items.sumOf { it.durationMs },
                totalSizeBytes = items.sumOf { it.sizeBytes },
                coverVideo = items.maxByOrNull { it.dateAddedSec }
            )
        }
        .sortedBy { it.bucketName.lowercase(Locale.ROOT) }

/**
 * Thumbnail for one row — loaded lazily per row (only while it's actually
 * composed in the LazyColumn) via MediaStore's own thumbnail machinery
 * rather than decoding a full-resolution frame: `loadThumbnail` on API 29+,
 * the older `Thumbnails` table below that (minSdk is 24). Same "hand-rolled,
 * no image-loading library" convention as [AsyncThumbnail] elsewhere in
 * this app — a picker screen showing dozens of rows is exactly the kind of
 * place Coil/Glide would normally earn their keep, but one more dependency
 * for this alone isn't worth it while everything else in the app already
 * gets by without one.
 */
@Composable
private fun VideoRowThumbnail(uri: Uri, id: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(id) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(200, 200), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                    )
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                Icons.Filled.MovieFilter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun VideoRow(video: DeviceVideo, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoRowThumbnail(uri = video.uri, id = video.id, modifier = Modifier.size(64.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.displayName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatPickerDuration(video.durationMs)} • ${video.width}×${video.height}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${formatPickerDate(video.dateAddedSec)} • ${formatPickerSize(video.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun FolderRow(folder: DeviceFolder, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cover = folder.coverVideo
            if (cover != null) {
                VideoRowThumbnail(uri = cover.uri, id = cover.id, modifier = Modifier.size(64.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.bucketName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${folder.itemCount} item • ${formatPickerDuration(folder.totalDurationMs)} • ${formatPickerSize(folder.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun PermissionRequestPanel(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Izin akses video diperlukan", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "Vidsize perlu izin membaca video di perangkatmu supaya bisa menampilkan daftar video di sini.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("Izinkan akses") }
    }
}

@Composable
private fun PickerEmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * Full-screen in-app video picker — replaces the OS Photo Picker
 * (`ActivityResultContracts.PickVisualMedia`) for this app's own "pick a
 * source video" flows (ResizerScreen + GifScreen).
 *
 * Batch 13: added because the system Photo Picker's look (grid tiles,
 * "Video"/"Koleksi" tabs) is OEM-themed and outside this app's control —
 * the user wanted a list-style browser instead (thumbnail + filename +
 * duration + resolution + date + size per row, with a Videos/Folders
 * split), closer to a reference file-manager-style picker. Queries
 * MediaStore directly, so it needs READ_MEDIA_VIDEO/READ_EXTERNAL_STORAGE
 * — already declared in the manifest — rather than any new permission.
 *
 * Both an explicit "Batal" text action and the back arrow/system-back
 * cancel out of this screen without picking anything, per the user's
 * request for a clear way back out after a wrong tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPickerScreen(
    onVideoSelected: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(hasVideoReadPermission(context)) }
    var isLoading by remember { mutableStateOf(true) }
    var allVideos by remember { mutableStateOf<List<DeviceVideo>>(emptyList()) }
    var tab by remember { mutableStateOf(PickerTab.VIDEOS) }
    var sort by remember { mutableStateOf(PickerSort.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<DeviceFolder?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    suspend fun reload() {
        isLoading = true
        allVideos = queryDeviceVideos(context)
        isLoading = false
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) reload() else isLoading = false
    }

    // Back press steps out of an open folder first, only cancelling the
    // whole picker once already at the root — normal file-browser behavior
    // rather than always exiting outright on the first back press.
    BackHandler(enabled = true) {
        if (openFolder != null) openFolder = null else onCancel()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        openFolder?.bucketName ?: "Pilih Video",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (openFolder != null) openFolder = null else onCancel() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { reload() } }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Urutkan")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            PickerSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { sort = option; showSortMenu = false },
                                    leadingIcon = {
                                        if (sort == option) Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                    // Explicit, always-visible cancel action — separate from
                    // the back arrow so "I picked the wrong video / opened
                    // this by accident" always has one unambiguous, clearly
                    // labeled way out regardless of whether a folder is open.
                    TextButton(onClick = onCancel) { Text("Batal") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )

            if (openFolder == null) {
                TabRow(
                    selectedTabIndex = tab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = tab == PickerTab.VIDEOS,
                        onClick = { tab = PickerTab.VIDEOS },
                        text = { Text("Videos") },
                        icon = { Icon(Icons.Filled.PlayCircleOutline, contentDescription = null) }
                    )
                    Tab(
                        selected = tab == PickerTab.FOLDERS,
                        onClick = { tab = PickerTab.FOLDERS },
                        text = { Text("Folders") },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = null) }
                    )
                }
            }

            when {
                !hasPermission -> PermissionRequestPanel(onGrant = { permissionLauncher.launch(neededVideoPermission()) })
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    val folder = openFolder
                    if (folder != null || tab == PickerTab.VIDEOS) {
                        val list = if (folder != null) allVideos.filter { it.bucketId == folder.bucketId } else allVideos
                        val sortedList = sortedVideos(list, sort)
                        if (sortedList.isEmpty()) {
                            PickerEmptyState("Tidak ada video ditemukan.")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(sortedList, key = { it.id }) { video ->
                                    VideoRow(video = video, onClick = { onVideoSelected(video.uri) })
                                }
                            }
                        }
                    } else {
                        val folders = buildFolders(allVideos)
                        if (folders.isEmpty()) {
                            PickerEmptyState("Tidak ada folder video ditemukan.")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(folders, key = { it.bucketId }) { f ->
                                    FolderRow(folder = f, onClick = { openFolder = f })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
