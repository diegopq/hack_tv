package io.github.diegopq.hacktv.media

import android.content.Context
import android.graphics.Bitmap
import io.github.diegopq.hacktv.data.RecentItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * What the user played in other apps, as the TV's player controls reported it. It stays on the TV: a small
 * JSON file plus one small JPEG per entry, in the launcher's private storage.
 */
object PlayHistory {

    private const val FILE = "play_history.json"
    private const val ART_DIR = "history_art"
    private const val MAX_ENTRIES = 30
    private const val ART_WIDTH = 320

    private val _items = MutableStateFlow<List<RecentItem>>(emptyList())
    /** Newest first. */
    val items: StateFlow<List<RecentItem>> = _items.asStateFlow()

    /** Every read and write happens on this one thread, in order, so the file never sees two writers. */
    private val io = Executors.newSingleThreadExecutor()
    private var loaded = false

    /** Reads the saved history once; later calls do nothing. */
    fun load(context: Context) {
        val app = context.applicationContext
        io.execute { ensureLoaded(app) }
    }

    /** Adds or refreshes an entry, moving it to the front. [art] is saved once, the first time it is seen. */
    fun record(context: Context, item: RecentItem, art: Bitmap?) {
        val app = context.applicationContext
        io.execute {
            ensureLoaded(app)
            val previous = _items.value.firstOrNull { it.id == item.id }
            val imageUri = previous?.imageUri?.takeIf { it.startsWith("file:") }
                ?: art?.let { saveArt(app, item.id, it) }
                ?: item.imageUri
                ?: previous?.imageUri
            val entry = item.copy(imageUri = imageUri)
            val kept = (listOf(entry) + _items.value.filter { it.id != item.id })
            kept.drop(MAX_ENTRIES).forEach { deleteArt(it) }
            save(app, kept.take(MAX_ENTRIES))
        }
    }

    /** Updates how far along an entry is, without moving it. */
    fun updatePosition(context: Context, id: String, positionMs: Long, durationMs: Long) {
        val app = context.applicationContext
        io.execute {
            ensureLoaded(app)
            val list = _items.value
            val index = list.indexOfFirst { it.id == id }
            if (index < 0) return@execute
            val old = list[index]
            if (old.positionMs == positionMs && old.durationMs == durationMs) return@execute
            save(app, list.toMutableList().apply { set(index, old.copy(positionMs = positionMs, durationMs = durationMs)) })
        }
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val file = File(context.filesDir, FILE)
        _items.value = runCatching { decode(file.readText()) }.getOrDefault(emptyList())
    }

    private fun save(context: Context, list: List<RecentItem>) {
        _items.value = list
        runCatching {
            val file = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(encode(list))
            tmp.renameTo(file)
        }
    }

    private fun saveArt(context: Context, id: String, art: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, ART_DIR).apply { mkdirs() }
        val file = File(dir, "${id.hashCode().toUInt()}.jpg")
        val height = (ART_WIDTH * art.height / art.width.coerceAtLeast(1)).coerceAtLeast(1)
        val small = if (art.width > ART_WIDTH) Bitmap.createScaledBitmap(art, ART_WIDTH, height, true) else art
        file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (small !== art) small.recycle()
        "file://${file.absolutePath}"
    }.getOrNull()

    private fun deleteArt(item: RecentItem) {
        item.imageUri?.takeIf { it.startsWith("file://") }?.let { File(it.removePrefix("file://")).delete() }
    }

    private fun encode(list: List<RecentItem>): String = JSONArray().apply {
        list.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("pkg", item.packageName)
                put("title", item.title)
                item.subtitle?.let { put("subtitle", it) }
                item.imageUri?.let { put("image", it) }
                put("pos", item.positionMs)
                put("dur", item.durationMs)
                put("at", item.lastPlayed)
            })
        }
    }.toString()

    private fun decode(text: String): List<RecentItem> {
        val array = JSONArray(text)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            RecentItem(
                id = o.getString("id"),
                packageName = o.getString("pkg"),
                title = o.getString("title"),
                subtitle = o.optString("subtitle").takeIf { it.isNotEmpty() },
                imageUri = o.optString("image").takeIf { it.isNotEmpty() },
                positionMs = o.optLong("pos"),
                durationMs = o.optLong("dur"),
                lastPlayed = o.getLong("at"),
                fromApp = false,
            )
        }
    }
}
