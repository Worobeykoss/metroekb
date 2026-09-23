package com.worobeyko.metroekb.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Какой вагон удобнее к нужному выходу — отмечает сам пользователь. */
@Serializable
enum class BestCar(val title: String) {
    FIRST("первый"),
    MIDDLE("в середине"),
    LAST("последний"),
}

/** Заметка пользователя о станции: свободный текст и лучший вагон по направлениям. */
@Serializable
data class StationNote(
    val text: String = "",
    val bestCar: Map<String, BestCar> = emptyMap(),
) {
    val isEmpty: Boolean get() = text.isBlank() && bestCar.isEmpty()
}

/**
 * Личные данные пользователя, которые должны пережить перезапуск: избранные станции и
 * заметки. Хранятся в SharedPreferences; поля — Compose-состояние, UI обновляется сам.
 */
class UserStore private constructor(private val prefs: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true }
    private val notesSerializer = MapSerializer(String.serializer(), StationNote.serializer())

    var favorites by mutableStateOf(prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet())
        private set

    var notes by mutableStateOf(
        try {
            prefs.getString(KEY_NOTES, null)?.let { json.decodeFromString(notesSerializer, it) } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    )
        private set

    fun isFavorite(stationId: String) = stationId in favorites

    fun toggleFavorite(stationId: String) {
        favorites = if (stationId in favorites) favorites - stationId else favorites + stationId
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }

    fun note(stationId: String): StationNote = notes[stationId] ?: StationNote()

    fun setNote(stationId: String, note: StationNote) {
        notes = if (note.isEmpty) notes - stationId else notes + (stationId to note)
        prefs.edit().putString(KEY_NOTES, json.encodeToString(notesSerializer, notes)).apply()
    }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_NOTES = "notes"

        @Volatile
        private var instance: UserStore? = null

        fun get(context: Context): UserStore = instance ?: synchronized(this) {
            instance ?: UserStore(
                context.applicationContext.getSharedPreferences("metro_user", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
