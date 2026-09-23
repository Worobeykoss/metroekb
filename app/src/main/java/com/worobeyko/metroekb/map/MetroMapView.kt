package com.worobeyko.metroekb.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.worobeyko.metroekb.data.Directions
import com.worobeyko.metroekb.data.Entrance
import com.worobeyko.metroekb.data.ScheduleRepository
import com.worobeyko.metroekb.domain.TrainRef
import kotlinx.coroutines.isActive
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineGradient
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.hypot

/** Что нажали на карте. */
sealed interface MapTap {
    data class StationTap(val stationId: String) : MapTap
    data class TrainTap(val ref: TrainRef) : MapTap
    data object GhostTap : MapTap
}

private const val ACCESSIBLE_BLUE = "#1E88E5"
private const val FAVORITE_FILL = "#FFB300"

/**
 * Все элементы - НАТИВНЫЕ слои MapLibre, поэтому они намертво приклеены к карте и не
 * «плавают» при быстром перетаскивании. Смена темы перезагружает базовый стиль и заново
 * строит наши слои; последние данные (пользователь, маршрут, избранное, видимость слоёв)
 * контроллер помнит и накладывает на новый стиль сам.
 */
class MetroMapController(
    private val map: MapLibreMap,
    private val repo: ScheduleRepository,
    private val entrances: List<Entrance>,
    private val density: Float,
    private val onTap: (MapTap) -> Unit,
    private val onUserGesture: () -> Unit,
) {
    private var style: Style? = null
    private var theme: MapTheme = MapTheme.DARK
    private var trainsSource: GeoJsonSource? = null
    private var effectsSource: GeoJsonSource? = null
    private var trailsSource: GeoJsonSource? = null
    private var stationsSource: GeoJsonSource? = null
    private var userSource: GeoJsonSource? = null
    private var walkSource: GeoJsonSource? = null

    /** Уже добавленные в текущий стиль картинки-плашки («~2½ мин» и т. п.). */
    private val labelImages = HashSet<String>()

    // Последнее известное состояние - чтобы восстановить его после смены стиля.
    private var lastUser: Pair<Double, Double>? = null
    private var lastWalk: List<Pair<Double, Double>>? = null
    private var favorites: Set<String> = emptySet()
    private var southColor = TrainPalette.south
    private var northColor = TrainPalette.north
    private val visible = HashMap<String, Boolean>()

    init {
        map.addOnMapClickListener { latLng -> handleTap(latLng) }
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onUserGesture()
        }
    }

    /** Построить наши слои поверх только что загруженного стиля. */
    fun onStyleLoaded(style: Style, theme: MapTheme) {
        this.style = style
        this.theme = theme
        labelImages.clear()
        addScrim(style)
        addGrid(style)
        addRoute(style)
        addWalkPath(style)
        addTrails(style)
        addEntrances(style)
        addStations(style)
        addUser(style)
        addEffects(style)
        addTrains(style)
        // Вернуть то, что было до перезагрузки стиля.
        updateUser(lastUser?.first, lastUser?.second)
        updateWalk(lastWalk)
        setFavorites(favorites)
        visible.forEach { (layer, v) -> setLayerVisible(layer, v) }
    }

    /** Сменить тему: другой базовый стиль - перезагрузка, иначе просто перестройка слоёв. */
    fun applyTheme(newTheme: MapTheme) {
        if (newTheme == theme && style != null) return
        // Источники старого стиля после перезагрузки недействительны - не трогаем их.
        style = null
        trainsSource = null
        effectsSource = null
        trailsSource = null
        stationsSource = null
        userSource = null
        walkSource = null
        map.setStyle(Style.Builder().fromUri(newTheme.styleUrl)) { s -> onStyleLoaded(s, newTheme) }
    }

    /** Ближайший к пальцу поезд или станция (в пикселях экрана). */
    private fun handleTap(latLng: LatLng): Boolean {
        val screen = map.projection.toScreenLocation(latLng)
        val pad = 22f * density // зона нажатия ±22 dp: по едущему поезду легко попасть
        val rect = RectF(screen.x - pad, screen.y - pad, screen.x + pad, screen.y + pad)
        val features = map.queryRenderedFeatures(rect, MapConfig.LYR_TRAINS, MapConfig.LYR_STATION_CIRCLES)
        val best = features.minByOrNull { f ->
            val p = f.geometry() as? Point ?: return@minByOrNull Float.MAX_VALUE
            val s: PointF = map.projection.toScreenLocation(LatLng(p.latitude(), p.longitude()))
            hypot(s.x - screen.x, s.y - screen.y)
        } ?: return false

        val tap: MapTap? = when {
            best.hasProperty(MapConfig.PROP_ID) -> MapTap.StationTap(best.getStringProperty(MapConfig.PROP_ID))
            best.getStringProperty(MapConfig.PROP_DIR) == MapFrame.KIND_GHOST -> MapTap.GhostTap
            best.hasProperty(MapConfig.PROP_REF_DIR) -> MapTap.TrainTap(
                TrainRef(
                    direction = best.getStringProperty(MapConfig.PROP_REF_DIR),
                    stationIndex = best.getNumberProperty(MapConfig.PROP_REF_STATION).toInt(),
                    minute = best.getNumberProperty(MapConfig.PROP_REF_MINUTE).toInt(),
                )
            )
            else -> null
        }
        tap?.let(onTap)
        return tap != null
    }

    fun updateFrame(frame: MapFrame) {
        val st = style ?: return
        trainsSource?.setGeoJson(FeatureCollection.fromFeatures(frame.trains.map { t ->
            Feature.fromGeometry(Point.fromLngLat(t.lon, t.lat)).apply {
                addStringProperty(MapConfig.PROP_DIR, t.kind)
                addNumberProperty(MapConfig.PROP_BEARING, t.bearing)
                addNumberProperty(MapConfig.PROP_OPACITY, t.opacity)
                addStringProperty(MapConfig.PROP_LABEL_IMG, labelImage(st, t))
                t.ref?.let { r ->
                    addStringProperty(MapConfig.PROP_REF_DIR, r.direction)
                    addNumberProperty(MapConfig.PROP_REF_STATION, r.stationIndex)
                    addNumberProperty(MapConfig.PROP_REF_MINUTE, r.minute)
                }
            }
        }))
        effectsSource?.setGeoJson(FeatureCollection.fromFeatures(frame.effects.map { e ->
            Feature.fromGeometry(Point.fromLngLat(e.lon, e.lat)).apply {
                addNumberProperty(MapConfig.PROP_R, e.radius)
                addNumberProperty(MapConfig.PROP_SW, e.strokeWidth)
                addStringProperty(MapConfig.PROP_SC, hex(e.strokeColor))
                addNumberProperty(MapConfig.PROP_SO, e.strokeOpacity)
                addStringProperty(MapConfig.PROP_FC, hex(e.fillColor))
                addNumberProperty(MapConfig.PROP_FO, e.fillOpacity)
            }
        }))
        trailsSource?.setGeoJson(FeatureCollection.fromFeatures(frame.trails.filter { it.points.size >= 2 }.map { tr ->
            Feature.fromGeometry(LineString.fromLngLats(tr.points.map { Point.fromLngLat(it.second, it.first) })).apply {
                addStringProperty(MapConfig.PROP_DIR, tr.direction)
            }
        }))
        frame.follow?.let { followTo(it.first, it.second) }
    }

    /** Плавно ведём камеру за поездом: каждый кадр чуть ближе к нему и к нужному масштабу. */
    private fun followTo(lat: Double, lon: Double) {
        val cam = map.cameraPosition
        val cur = cam.target ?: return
        val k = 0.18
        val target = LatLng(cur.latitude + (lat - cur.latitude) * k, cur.longitude + (lon - cur.longitude) * k)
        val zoom = cam.zoom + (MapConfig.FOLLOW_ZOOM - cam.zoom) * 0.06
        map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(cam).target(target).zoom(zoom).build()))
    }

    /** Показать точку (результат поиска и т. п.). */
    fun flyTo(lat: Double, lon: Double, zoom: Double = 15.0) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom))
    }

    /**
     * Картинка-плашка над поездом. Разных подписей немного («прибывает», «~1 мин», …),
     * поэтому каждую рисуем один раз и кэшируем в стиле (цвет - часть ключа).
     */
    private fun labelImage(style: Style, t: TrainMarker): String {
        val ring = directionColor(t.kind)
        val id = "lbl|${t.labelStyle}|${t.kind}|$ring|${t.label}"
        if (labelImages.add(id)) {
            val bmp = when (t.labelStyle) {
                LabelStyle.NORMAL -> makeLabelBitmap(density, t.label, Color.parseColor("#F7F3E7"), ring, Color.parseColor("#16181F"))
                LabelStyle.CATCH -> makeLabelBitmap(density, t.label, Color.parseColor("#E8FFF1"), Color.parseColor("#00E676"), Color.parseColor("#0B5D2A"))
                LabelStyle.GHOST -> makeLabelBitmap(density, t.label, Color.parseColor("#1B2A22"), Color.parseColor("#B9F6CA"), Color.parseColor("#B9F6CA"))
            }
            style.addImage(id, bmp)
        }
        return id
    }

    /** Свои цвета поездов: перерисовать иконки и градиенты следов. */
    fun setTrainColors(south: Int, north: Int) {
        if (south == southColor && north == northColor) return
        southColor = south
        northColor = north
        val st = style ?: return
        st.addImage(MapConfig.IMG_TRAIN_SOUTH, makeTrainBitmap(density, south, Color.WHITE))
        st.addImage(MapConfig.IMG_TRAIN_NORTH, makeTrainBitmap(density, north, Color.WHITE))
        st.getLayer(MapConfig.LYR_TRAILS_SOUTH)?.setProperties(lineGradient(trailGradient(south)))
        st.getLayer(MapConfig.LYR_TRAILS_NORTH)?.setProperties(lineGradient(trailGradient(north)))
    }

    /** Избранные станции - крупнее и янтарные. */
    fun setFavorites(ids: Set<String>) {
        favorites = ids
        stationsSource?.setGeoJson(FeatureCollection.fromFeatures(stationFeatures()))
    }

    fun updateUser(lat: Double?, lon: Double?) {
        lastUser = if (lat != null && lon != null) lat to lon else null
        val src = userSource ?: return
        val fc = if (lat == null || lon == null) {
            FeatureCollection.fromFeatures(emptyList<Feature>())
        } else {
            FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat))))
        }
        src.setGeoJson(fc)
    }

    /** points - список (lat, lon) вдоль пешего маршрута. */
    fun updateWalk(points: List<Pair<Double, Double>>?) {
        lastWalk = points
        val src = walkSource ?: return
        val fc = if (points == null || points.size < 2) {
            FeatureCollection.fromFeatures(emptyList<Feature>())
        } else {
            val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.second, it.first) })
            FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line)))
        }
        src.setGeoJson(fc)
    }

    fun setLayerVisible(layerId: String, v: Boolean) {
        visible[layerId] = v
        style?.getLayer(layerId)?.setProperties(visibility(if (v) Property.VISIBLE else Property.NONE))
    }

    fun setLabelsVisible(v: Boolean) = setLayerVisible(MapConfig.LYR_STATION_LABELS, v)

    fun setTrainTimersVisible(v: Boolean) = setLayerVisible(MapConfig.LYR_TRAIN_LABELS, v)

    fun setWalkVisible(v: Boolean) {
        setLayerVisible(MapConfig.LYR_WALK, v)
        setLayerVisible(MapConfig.LYR_USER_HALO, v)
        setLayerVisible(MapConfig.LYR_USER_DOT, v)
    }

    fun recenter(lat: Double, lon: Double) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 14.5))
    }

    fun resetNorth() {
        map.animateCamera(CameraUpdateFactory.bearingTo(0.0))
    }

    // ── слои ────────────────────────────────────────────────────────────────

    private fun c(hex: String) = Color.parseColor(hex)

    private fun addScrim(style: Style) {
        val world = Polygon.fromLngLats(
            listOf(
                listOf(
                    Point.fromLngLat(-180.0, -85.0),
                    Point.fromLngLat(180.0, -85.0),
                    Point.fromLngLat(180.0, 85.0),
                    Point.fromLngLat(-180.0, 85.0),
                    Point.fromLngLat(-180.0, -85.0),
                )
            )
        )
        style.addSource(GeoJsonSource(MapConfig.SRC_SCRIM, Feature.fromGeometry(world)))
        style.addLayer(
            FillLayer(MapConfig.LYR_SCRIM, MapConfig.SRC_SCRIM).withProperties(
                fillColor(c(theme.scrimColor)),
                fillOpacity(theme.scrimOpacity),
            )
        )
    }

    /** Сетка «чертежа» - линии через ~1 км над областью города. */
    private fun addGrid(style: Style) {
        val gridColor = theme.gridColor ?: return
        val lines = ArrayList<Feature>()
        var lat = MapConfig.BOUNDS_SOUTH
        while (lat <= MapConfig.BOUNDS_NORTH) {
            lines += Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(MapConfig.BOUNDS_WEST, lat), Point.fromLngLat(MapConfig.BOUNDS_EAST, lat))))
            lat += 0.009
        }
        var lon = MapConfig.BOUNDS_WEST
        while (lon <= MapConfig.BOUNDS_EAST) {
            lines += Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(lon, MapConfig.BOUNDS_SOUTH), Point.fromLngLat(lon, MapConfig.BOUNDS_NORTH))))
            lon += 0.0165
        }
        style.addSource(GeoJsonSource(MapConfig.SRC_GRID, FeatureCollection.fromFeatures(lines)))
        style.addLayer(LineLayer(MapConfig.LYR_GRID, MapConfig.SRC_GRID).withProperties(lineColor(c(gridColor)), lineWidth(0.8f)))
    }

    /** Линия по настоящей форме пути (из OpenStreetMap), не по прямым между станциями. */
    private fun addRoute(style: Style) {
        val points = repo.geometry.polyline().map { Point.fromLngLat(it.second, it.first) }
        style.addSource(GeoJsonSource(MapConfig.SRC_ROUTE, Feature.fromGeometry(LineString.fromLngLats(points))))
        style.addLayer(
            LineLayer(MapConfig.LYR_ROUTE_CASING, MapConfig.SRC_ROUTE).withProperties(
                lineColor(c(theme.casingColor)),
                lineWidth(theme.casingWidth),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        style.addLayer(
            LineLayer(MapConfig.LYR_ROUTE, MapConfig.SRC_ROUTE).withProperties(
                lineColor(c(theme.lineColor)),
                lineWidth(theme.lineWidth),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
    }

    private fun addWalkPath(style: Style) {
        val src = GeoJsonSource(MapConfig.SRC_WALK, FeatureCollection.fromFeatures(emptyList<Feature>()))
        style.addSource(src)
        walkSource = src
        style.addLayer(
            LineLayer(MapConfig.LYR_WALK, MapConfig.SRC_WALK).withProperties(
                lineColor(c("#00E676")),
                lineWidth(4.5f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineDasharray(arrayOf(1.6f, 1.2f)),
            )
        )
    }

    /** Градиент следа: от прозрачного хвоста к яркому поезду. */
    private fun trailGradient(color: Int): Expression = Expression.interpolate(
        Expression.linear(), Expression.lineProgress(),
        Expression.stop(0, Expression.rgba(Color.red(color), Color.green(color), Color.blue(color), 0f)),
        Expression.stop(1, Expression.rgba(Color.red(color), Color.green(color), Color.blue(color), 0.85f)),
    )

    private fun addTrails(style: Style) {
        val src = GeoJsonSource(
            MapConfig.SRC_TRAILS,
            FeatureCollection.fromFeatures(emptyList<Feature>()),
            GeoJsonOptions().withLineMetrics(true),
        )
        style.addSource(src)
        trailsSource = src
        for ((layerId, dir, color) in listOf(
            Triple(MapConfig.LYR_TRAILS_SOUTH, Directions.SOUTH, southColor),
            Triple(MapConfig.LYR_TRAILS_NORTH, Directions.NORTH, northColor),
        )) {
            style.addLayer(
                LineLayer(layerId, MapConfig.SRC_TRAILS).withProperties(
                    lineGradient(trailGradient(color)),
                    lineWidth(6f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                ).withFilter(Expression.eq(Expression.get(MapConfig.PROP_DIR), Expression.literal(dir)))
            )
        }
    }

    /** Входы в метро: кружки с номером вблизи (с 14-го масштаба); доступные - синие. */
    private fun addEntrances(style: Style) {
        val features = entrances.map { e ->
            Feature.fromGeometry(Point.fromLngLat(e.lon, e.lat)).apply {
                addStringProperty(MapConfig.PROP_NAME, e.ref)
                addStringProperty(MapConfig.PROP_WHEELCHAIR, e.wheelchair)
            }
        }
        style.addSource(GeoJsonSource(MapConfig.SRC_ENTRANCES, FeatureCollection.fromFeatures(features)))
        style.addLayer(
            CircleLayer(MapConfig.LYR_ENTRANCES, MapConfig.SRC_ENTRANCES).withProperties(
                circleRadius(4.5f),
                circleColor(
                    Expression.match(
                        Expression.get(MapConfig.PROP_WHEELCHAIR),
                        Expression.literal("yes"), Expression.color(c(ACCESSIBLE_BLUE)),
                        Expression.literal("limited"), Expression.color(c(ACCESSIBLE_BLUE)),
                        Expression.color(c(theme.stationStroke)),
                    )
                ),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(1.5f),
            ).apply { minZoom = 14f }
        )
        style.addLayer(
            SymbolLayer(MapConfig.LYR_ENTRANCE_LABELS, MapConfig.SRC_ENTRANCES).withProperties(
                textField(Expression.get(MapConfig.PROP_NAME)),
                textFont(arrayOf("Open Sans Regular")),
                textSize(10f),
                textColor(c(theme.labelColor)),
                textHaloColor(c(theme.labelHalo)),
                textHaloWidth(1.2f),
                textAnchor(Property.TEXT_ANCHOR_LEFT),
                textOffset(arrayOf(0.6f, 0.0f)),
                textOptional(true),
            ).apply { minZoom = 15.5f }
        )
    }

    private fun stationFeatures(): List<Feature> = repo.stations.map { s ->
        Feature.fromGeometry(Point.fromLngLat(s.lon, s.lat)).apply {
            addStringProperty(MapConfig.PROP_ID, s.id)
            addStringProperty(MapConfig.PROP_NAME, s.name)
            addBooleanProperty(MapConfig.PROP_FAV, s.id in favorites)
        }
    }

    private fun addStations(style: Style) {
        val src = GeoJsonSource(MapConfig.SRC_STATIONS, FeatureCollection.fromFeatures(stationFeatures()))
        style.addSource(src)
        stationsSource = src
        val isFav = Expression.toBool(Expression.get(MapConfig.PROP_FAV))
        style.addLayer(
            CircleLayer(MapConfig.LYR_STATION_CIRCLES, MapConfig.SRC_STATIONS).withProperties(
                circleRadius(Expression.switchCase(isFav, Expression.literal(7.5f), Expression.literal(5.5f))),
                circleColor(Expression.switchCase(isFav, Expression.color(c(FAVORITE_FILL)), Expression.color(c(theme.stationFill)))),
                circleStrokeColor(c(theme.stationStroke)),
                circleStrokeWidth(2.5f),
            )
        )
        style.addLayer(
            SymbolLayer(MapConfig.LYR_STATION_LABELS, MapConfig.SRC_STATIONS).withProperties(
                textField(Expression.get(MapConfig.PROP_NAME)),
                textFont(arrayOf("Open Sans Regular")),
                textSize(11f),
                textColor(c(theme.labelColor)),
                textHaloColor(c(theme.labelHalo)),
                textHaloWidth(1.4f),
                textAnchor(Property.TEXT_ANCHOR_LEFT),
                textOffset(arrayOf(0.8f, 0.0f)),
                textAllowOverlap(false),
                textOptional(true),
            )
        )
    }

    private fun addUser(style: Style) {
        val src = GeoJsonSource(MapConfig.SRC_USER, FeatureCollection.fromFeatures(emptyList<Feature>()))
        style.addSource(src)
        userSource = src
        style.addLayer(
            CircleLayer(MapConfig.LYR_USER_HALO, MapConfig.SRC_USER).withProperties(
                circleRadius(15f),
                circleColor(c("#3300E676")),
            )
        )
        style.addLayer(
            CircleLayer(MapConfig.LYR_USER_DOT, MapConfig.SRC_USER).withProperties(
                circleRadius(6f),
                circleColor(c("#00E676")),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(3f),
            )
        )
    }

    /** Круги-эффекты: всё оформление берётся из свойств каждой точки (см. [EffectCircle]). */
    private fun addEffects(style: Style) {
        val src = GeoJsonSource(MapConfig.SRC_EFFECTS, FeatureCollection.fromFeatures(emptyList<Feature>()))
        style.addSource(src)
        effectsSource = src
        style.addLayer(
            CircleLayer(MapConfig.LYR_EFFECTS, MapConfig.SRC_EFFECTS).withProperties(
                circleRadius(Expression.toNumber(Expression.get(MapConfig.PROP_R))),
                circleStrokeWidth(Expression.toNumber(Expression.get(MapConfig.PROP_SW))),
                circleStrokeColor(Expression.toColor(Expression.get(MapConfig.PROP_SC))),
                circleStrokeOpacity(Expression.toNumber(Expression.get(MapConfig.PROP_SO))),
                circleColor(Expression.toColor(Expression.get(MapConfig.PROP_FC))),
                circleOpacity(Expression.toNumber(Expression.get(MapConfig.PROP_FO))),
            )
        )
    }

    private fun addTrains(style: Style) {
        style.addImage(MapConfig.IMG_TRAIN_SOUTH, makeTrainBitmap(density, southColor, Color.WHITE))
        style.addImage(MapConfig.IMG_TRAIN_NORTH, makeTrainBitmap(density, northColor, Color.WHITE))
        style.addImage(MapConfig.IMG_TRAIN_GHOST, makeTrainBitmap(density, c("#B9F6CA"), c("#E8FFF0")))
        val src = GeoJsonSource(MapConfig.SRC_TRAINS, FeatureCollection.fromFeatures(emptyList<Feature>()))
        style.addSource(src)
        trainsSource = src
        style.addLayer(
            SymbolLayer(MapConfig.LYR_TRAINS, MapConfig.SRC_TRAINS).withProperties(
                iconImage(
                    Expression.match(
                        Expression.get(MapConfig.PROP_DIR),
                        Expression.literal(Directions.SOUTH), Expression.literal(MapConfig.IMG_TRAIN_SOUTH),
                        Expression.literal(Directions.NORTH), Expression.literal(MapConfig.IMG_TRAIN_NORTH),
                        Expression.literal(MapFrame.KIND_GHOST), Expression.literal(MapConfig.IMG_TRAIN_GHOST),
                        Expression.literal(MapConfig.IMG_TRAIN_SOUTH),
                    )
                ),
                iconRotate(Expression.get(MapConfig.PROP_BEARING)),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconOpacity(Expression.toNumber(Expression.get(MapConfig.PROP_OPACITY))),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                // Издалека поезда мельче, вблизи крупнее.
                iconSize(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.stop(10, 0.7f), Expression.stop(12, 0.9f), Expression.stop(15, 1.25f),
                    )
                ),
            )
        )
        // Плашка над поездом: не крутится вместе с картой, всегда «над» поездом на экране.
        style.addLayer(
            SymbolLayer(MapConfig.LYR_TRAIN_LABELS, MapConfig.SRC_TRAINS).withProperties(
                iconImage(Expression.get(MapConfig.PROP_LABEL_IMG)),
                iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                iconOffset(arrayOf(0f, -17f)),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                iconOpacity(Expression.toNumber(Expression.get(MapConfig.PROP_OPACITY))),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconSize(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.stop(10, 0.8f), Expression.stop(13, 1.0f),
                    )
                ),
            )
        )
    }
}

private fun hex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

/** Иконка поезда «носом» вверх: капсула цвета направления, обводка и шеврон по ходу. */
private fun makeTrainBitmap(density: Float, bodyColor: Int, strokeColor: Int): Bitmap {
    fun d(v: Float) = v * density
    val w = d(20f).toInt().coerceAtLeast(1)
    val h = d(36f).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.density = (density * 160f).toInt()
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Мягкий ореол цвета направления - поезд видно на тёмной карте.
    paint.style = Paint.Style.FILL
    paint.color = (bodyColor and 0x00FFFFFF) or (0x4D shl 24)
    canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), w / 2f, w / 2f, paint)

    val body = RectF(d(3f), d(3f), w - d(3f), h - d(3f))
    val radius = body.width() / 2f
    paint.color = bodyColor
    canvas.drawRoundRect(body, radius, radius, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = d(2f)
    paint.color = strokeColor
    canvas.drawRoundRect(body, radius, radius, paint)

    // Шеврон «куда едет» в передней половине.
    paint.strokeWidth = d(2.2f)
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeJoin = Paint.Join.ROUND
    val cx = w / 2f
    val tipY = body.top + d(8f)
    val chevron = Path().apply {
        moveTo(cx - d(4f), tipY + d(4.5f))
        lineTo(cx, tipY)
        lineTo(cx + d(4f), tipY + d(4.5f))
    }
    canvas.drawPath(chevron, paint)
    return bmp
}

/** Плашка над поездом: «пилюля» с обводкой. */
private fun makeLabelBitmap(density: Float, text: String, bgColor: Int, ringColor: Int, textColor: Int): Bitmap {
    fun d(v: Float) = v * density
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = d(12f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val stroke = d(2f)
    val h = d(20f)
    val w = textPaint.measureText(text) + d(16f)
    val bmp = Bitmap.createBitmap(
        (w + stroke).toInt().coerceAtLeast(1),
        (h + stroke).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    bmp.density = (density * 160f).toInt()
    val canvas = Canvas(bmp)
    val rect = RectF(stroke / 2f, stroke / 2f, stroke / 2f + w, stroke / 2f + h)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.FILL
    paint.color = bgColor
    canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = stroke
    paint.color = ringColor
    canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
    val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(text, rect.centerX(), baseline, textPaint)
    return bmp
}

/**
 * Compose-обёртка: жизненный цикл MapView, стиль по теме, слои. Поезда и эффекты обновляются
 * покадрово (withFrameNanos) через [frameProvider]; остальное - при изменении входных данных.
 */
@Composable
fun MetroMap(
    repo: ScheduleRepository,
    entrances: List<Entrance>,
    frameProvider: () -> MapFrame,
    userLat: Double?,
    userLon: Double?,
    walkPoints: List<Pair<Double, Double>>?,
    showLabels: Boolean,
    showTrainTimers: Boolean,
    showWalk: Boolean,
    theme: MapTheme,
    southColor: Int,
    northColor: Int,
    favorites: Set<String>,
    onTap: (MapTap) -> Unit,
    onUserGesture: () -> Unit,
    onControllerReady: (MetroMapController) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    var controller by remember { mutableStateOf<MetroMapController?>(null) }
    val provider by rememberUpdatedState(frameProvider)
    val tapHandler by rememberUpdatedState(onTap)
    val gestureHandler by rememberUpdatedState(onUserGesture)
    val initialTheme by rememberUpdatedState(theme)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var created = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> { mapView.onCreate(null); created = true }
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> if (created) { mapView.onDestroy(); created = false }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (created) { mapView.onDestroy(); created = false }
        }
    }

    // Плавная покадровая анимация поездов и эффектов (нативные слои -> приклеены к карте).
    LaunchedEffect(controller) {
        val ctrl = controller ?: return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            ctrl.updateFrame(provider())
        }
    }

    LaunchedEffect(controller, userLat, userLon) { controller?.updateUser(userLat, userLon) }
    LaunchedEffect(controller, walkPoints) { controller?.updateWalk(walkPoints) }
    LaunchedEffect(controller, showLabels) { controller?.setLabelsVisible(showLabels) }
    LaunchedEffect(controller, showTrainTimers) { controller?.setTrainTimersVisible(showTrainTimers) }
    LaunchedEffect(controller, showWalk) { controller?.setWalkVisible(showWalk) }
    LaunchedEffect(controller, theme) { controller?.applyTheme(theme) }
    LaunchedEffect(controller, southColor, northColor) { controller?.setTrainColors(southColor, northColor) }
    LaunchedEffect(controller, favorites) { controller?.setFavorites(favorites) }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    map.uiSettings.apply {
                        isRotateGesturesEnabled = true
                        isCompassEnabled = false // своя кнопка «на север»
                        isTiltGesturesEnabled = false
                        isAttributionEnabled = true
                        isLogoEnabled = true
                    }
                    val ctrl = MetroMapController(
                        map, repo, entrances, density,
                        onTap = { tapHandler(it) },
                        onUserGesture = { gestureHandler() },
                    )
                    val startTheme = initialTheme
                    map.setStyle(Style.Builder().fromUri(startTheme.styleUrl)) { style ->
                        val pos = CameraPosition.Builder()
                            .target(LatLng(MapConfig.CENTER_LAT, MapConfig.CENTER_LON))
                            .zoom(MapConfig.INITIAL_ZOOM)
                            .bearing(MapConfig.INITIAL_BEARING)
                            .tilt(MapConfig.INITIAL_TILT)
                            .build()
                        map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
                        ctrl.onStyleLoaded(style, startTheme)
                        controller = ctrl
                        onControllerReady(ctrl)
                    }
                }
            }
        },
    )
}
