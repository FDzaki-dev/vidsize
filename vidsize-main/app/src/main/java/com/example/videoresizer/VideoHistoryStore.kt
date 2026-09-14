package com.example.videoresizer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VideoHistoryEntry(
    val id: String,
    val createdAt: Long,
    val outputFilePath: String,
    val thumbnailPath: String,
    val sourceUri: String,
    val aspectRatioName: String,
    val resolutionName: String,
    val rotationName: String,
    val muteAudio: Boolean,
    val trimStartMs: Long,
    val trimEndMs: Long,
    /**
     * BUG FIX: this entry used to have no memory of Crop-vs-Stretch or a
     * custom W×H at all. "Edit ulang" would restore aspect/resolution/
     * rotation/mute/trim correctly but silently drop these two — a video
     * exported with a hand-picked custom resolution would reopen showing
     * "Custom" selected but with the actual numbers gone, quietly falling
     * back to the source's original size on the next export. Defaults here
     * keep old saved entries (from before this field existed) readable.
     */
    val resizeModeName: String = ResizeMode.CROP.name,
    val customWidth: Int? = null,
    val customHeight: Int? = null,
    /** Bitrate/quality preset used for this export. Defaults to ORIGINAL for entries saved before this field existed. */
    val qualityName: String = QualityOption.ORIGINAL.name,
    val customBitrateKbps: Int? = null,
    /**
     * Gallery integration: the content:// Uri of the public MediaStore copy
     * (Movies/VideoResizer), if [PublicMovieExporter.publish] succeeded.
     * [outputFilePath] points at this app's private cache, which Android is
     * free to wipe under storage pressure at any time — the MediaStore copy
     * is the durable one, so Studio prefers it for Share / "Buka di Galeri"
     * whenever it's present. Null for entries saved before this field
     * existed, or if publishing to the public gallery failed for this item.
     */
    val publicUri: String? = null,
    /** Watermark settings used for this export, if any. Null/defaults for entries saved before this field existed. */
    val watermarkUri: String? = null,
    val watermarkPositionName: String = WatermarkPosition.BOTTOM_RIGHT.name,
    val watermarkOpacityPercent: Int = 70,
    val watermarkScalePercent: Int = 18,
    /** Caption text used for this export, if any. Null/blank for entries saved before this field existed. */
    val captionText: String? = null,
    val captionPositionName: String = WatermarkPosition.BOTTOM_RIGHT.name,
    /** Flip/frame-rate settings used for this export. Defaults keep entries saved before Batch 9 readable. */
    val flipName: String = FlipOption.NONE.name,
    val frameRateName: String = FrameRateOption.ORIGINAL.name,
    /**
     * Distinguishes a GIF export (Batch 12: GifExporter, a completely
     * separate pipeline from VideoResizer/Transformer) from an ordinary
     * video export. "VIDEO" for every entry saved before this field
     * existed. Studio branches display/share/"Edit ulang" behavior on
     * this rather than trying to infer it from the file extension.
     */
    val kind: String = "VIDEO",
    /** GIF-only settings. 0 for VIDEO entries / entries saved before GIF history support existed. */
    val gifFps: Int = 0,
    val gifWidthPx: Int = 0
)

/**
 * Stores metadata about previously-resized videos (path, source video, and
 * the settings used) so the Studio screen can list them and let the user
 * reopen one to tweak and re-render.
 *
 * Backed by a single SharedPreferences JSON array, same lightweight
 * approach as GIF Maker's history store.
 */
object VideoHistoryStore {
    private const val PREFS_NAME = "video_history"
    private const val KEY_ENTRIES = "entries"

    fun add(context: Context, entry: VideoHistoryEntry) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = readArray(prefs)
        array.put(toJson(entry))
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    fun getAll(context: Context): List<VideoHistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = readArray(prefs)
        val result = mutableListOf<VideoHistoryEntry>()
        for (i in 0 until array.length()) {
            fromJson(array.getJSONObject(i))?.let { result.add(it) }
        }
        return result.sortedByDescending { it.createdAt }
    }

    fun remove(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = readArray(prefs)
        val filtered = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optString("id") != id) filtered.put(obj)
        }
        prefs.edit().putString(KEY_ENTRIES, filtered.toString()).apply()
    }

    /** Deletes both the history record and its backing files (video + thumbnail copy). */
    fun deleteWithFiles(context: Context, entry: VideoHistoryEntry) {
        remove(context, entry.id)
        runCatching { File(entry.outputFilePath).delete() }
        runCatching { File(entry.thumbnailPath).delete() }
    }

    private fun readArray(prefs: android.content.SharedPreferences): JSONArray {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    private fun toJson(entry: VideoHistoryEntry): JSONObject = JSONObject().apply {
        put("id", entry.id)
        put("createdAt", entry.createdAt)
        put("outputFilePath", entry.outputFilePath)
        put("thumbnailPath", entry.thumbnailPath)
        put("sourceUri", entry.sourceUri)
        put("aspectRatioName", entry.aspectRatioName)
        put("resolutionName", entry.resolutionName)
        put("rotationName", entry.rotationName)
        put("muteAudio", entry.muteAudio)
        put("trimStartMs", entry.trimStartMs)
        put("trimEndMs", entry.trimEndMs)
        put("resizeModeName", entry.resizeModeName)
        put("customWidth", entry.customWidth)
        put("customHeight", entry.customHeight)
        put("qualityName", entry.qualityName)
        put("customBitrateKbps", entry.customBitrateKbps)
        put("publicUri", entry.publicUri)
        put("watermarkUri", entry.watermarkUri)
        put("watermarkPositionName", entry.watermarkPositionName)
        put("watermarkOpacityPercent", entry.watermarkOpacityPercent)
        put("watermarkScalePercent", entry.watermarkScalePercent)
        put("captionText", entry.captionText)
        put("captionPositionName", entry.captionPositionName)
        put("flipName", entry.flipName)
        put("frameRateName", entry.frameRateName)
        put("kind", entry.kind)
        put("gifFps", entry.gifFps)
        put("gifWidthPx", entry.gifWidthPx)
    }

    private fun fromJson(obj: JSONObject): VideoHistoryEntry? = runCatching {
        VideoHistoryEntry(
            id = obj.getString("id"),
            createdAt = obj.getLong("createdAt"),
            outputFilePath = obj.getString("outputFilePath"),
            thumbnailPath = obj.getString("thumbnailPath"),
            sourceUri = obj.getString("sourceUri"),
            aspectRatioName = obj.getString("aspectRatioName"),
            resolutionName = obj.getString("resolutionName"),
            rotationName = obj.getString("rotationName"),
            muteAudio = obj.getBoolean("muteAudio"),
            trimStartMs = obj.getLong("trimStartMs"),
            trimEndMs = obj.getLong("trimEndMs"),
            // optString/opt-int-or-null (not getString/getInt): entries
            // written before these fields existed won't have them in their
            // JSON at all — without these fallbacks, fromJson() would throw
            // and the whole history list would come back silently empty.
            resizeModeName = obj.optString("resizeModeName", "CROP"),
            customWidth = if (obj.has("customWidth") && !obj.isNull("customWidth")) obj.optInt("customWidth") else null,
            customHeight = if (obj.has("customHeight") && !obj.isNull("customHeight")) obj.optInt("customHeight") else null,
            qualityName = obj.optString("qualityName", QualityOption.ORIGINAL.name),
            customBitrateKbps = if (obj.has("customBitrateKbps") && !obj.isNull("customBitrateKbps")) obj.optInt("customBitrateKbps") else null,
            publicUri = obj.optString("publicUri", "").ifEmpty { null },
            watermarkUri = obj.optString("watermarkUri", "").ifEmpty { null },
            watermarkPositionName = obj.optString("watermarkPositionName", WatermarkPosition.BOTTOM_RIGHT.name),
            watermarkOpacityPercent = if (obj.has("watermarkOpacityPercent")) obj.optInt("watermarkOpacityPercent", 70) else 70,
            watermarkScalePercent = if (obj.has("watermarkScalePercent")) obj.optInt("watermarkScalePercent", 18) else 18,
            captionText = obj.optString("captionText", "").ifEmpty { null },
            captionPositionName = obj.optString("captionPositionName", WatermarkPosition.BOTTOM_RIGHT.name),
            flipName = obj.optString("flipName", FlipOption.NONE.name),
            frameRateName = obj.optString("frameRateName", FrameRateOption.ORIGINAL.name),
            kind = obj.optString("kind", "VIDEO"),
            gifFps = obj.optInt("gifFps", 0),
            gifWidthPx = obj.optInt("gifWidthPx", 0)
        )
    }.getOrNull()
}
