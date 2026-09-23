package com.worobeyko.metroekb.map

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Офлайн-карта Екатеринбурга: тайлы стиля текущей темы для масштабов 10-14 (дальше
 * векторные тайлы просто увеличиваются). MapLibre сам берёт их из базы, когда нет сети.
 */
object OfflineMaps {
    data class Status(val percent: Int, val bytes: Long, val done: Boolean, val error: String? = null)

    /** null - офлайн-карты нет. */
    val status = mutableStateOf<Status?>(null)

    private fun nameFor(theme: MapTheme) = "ekb-${theme.name}"

    private fun manager(context: Context): OfflineManager {
        MapLibre.getInstance(context)
        return OfflineManager.getInstance(context)
    }

    private fun regions(context: Context, onList: (List<OfflineRegion>) -> Unit) {
        manager(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) = onList(offlineRegions?.toList().orEmpty())
            override fun onError(error: String) = onList(emptyList())
        })
    }

    private fun OfflineRegion.name(): String = runCatching { String(metadata) }.getOrDefault("")

    /** Прочитать, скачана ли карта для темы. */
    fun refresh(context: Context, theme: MapTheme) {
        regions(context) { list ->
            val region = list.firstOrNull { it.name() == nameFor(theme) }
            if (region == null) {
                status.value = null
                return@regions
            }
            region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(s: OfflineRegionStatus?) {
                    s ?: return
                    status.value = Status(percentOf(s), s.completedResourceSize, s.isComplete)
                }

                override fun onError(error: String?) {
                    status.value = Status(0, 0, false, error)
                }
            })
        }
    }

    private fun percentOf(s: OfflineRegionStatus): Int =
        if (s.requiredResourceCount > 0) (s.completedResourceCount * 100 / s.requiredResourceCount).toInt() else 0

    fun download(context: Context, theme: MapTheme) {
        val density = context.resources.displayMetrics.density
        val bounds = LatLngBounds.from(MapConfig.BOUNDS_NORTH, MapConfig.BOUNDS_EAST, MapConfig.BOUNDS_SOUTH, MapConfig.BOUNDS_WEST)
        val definition = OfflineTilePyramidRegionDefinition(theme.styleUrl, bounds, 10.0, 14.0, density)
        status.value = Status(0, 0, false)
        manager(context).createOfflineRegion(definition, nameFor(theme).toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(s: OfflineRegionStatus) {
                        status.value = Status(percentOf(s), s.completedResourceSize, s.isComplete)
                        if (s.isComplete) offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    }

                    override fun onError(error: OfflineRegionError) {
                        status.value = status.value?.copy(error = error.message)
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        status.value = status.value?.copy(error = "слишком много тайлов ($limit)")
                    }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) {
                status.value = Status(0, 0, false, error)
            }
        })
    }

    fun delete(context: Context, theme: MapTheme) {
        regions(context) { list ->
            list.filter { it.name() == nameFor(theme) }.forEach { region ->
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        status.value = null
                    }

                    override fun onError(error: String) {
                        status.value = status.value?.copy(error = error)
                    }
                })
            }
        }
    }
}
