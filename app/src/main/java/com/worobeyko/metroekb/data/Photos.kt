package com.worobeyko.metroekb.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL

/** Фото станции с Wikimedia Commons (см. tools/fetch_photos.py): превью и подпись. */
@Serializable
data class StationPhoto(
    val title: String = "",
    val thumb: String,
    val page: String = "",
    val author: String = "",
    val license: String = "",
)

@Serializable
private data class PhotosDoc(val source: String = "", val photos: Map<String, List<StationPhoto>> = emptyMap())

object PhotoRepository {
    @Volatile
    private var cached: Map<String, List<StationPhoto>>? = null

    fun get(context: Context): Map<String, List<StationPhoto>> = cached ?: synchronized(this) {
        cached ?: try {
            context.assets.open("photos.json").bufferedReader().use {
                ScheduleRepository.json.decodeFromString(PhotosDoc.serializer(), it.readText()).photos
            }
        } catch (_: Exception) {
            emptyMap()
        }.also { cached = it }
    }
}

/** Загрузка картинок по сети с кэшем в памяти (превью ~640 px - хватает маленького кэша). */
object RemoteImages {
    private val cache = LruCache<String, ImageBitmap>(16)

    fun cached(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "MetroEkb/1.0 (Android)")
                }
                conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()?.also { cache.put(url, it) }
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Composable
fun rememberRemoteImage(url: String): State<ImageBitmap?> {
    val state = remember(url) { mutableStateOf(RemoteImages.cached(url)) }
    LaunchedEffect(url) { if (state.value == null) state.value = RemoteImages.load(url) }
    return state
}
