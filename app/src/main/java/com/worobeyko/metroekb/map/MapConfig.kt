package com.worobeyko.metroekb.map

/**
 * Настройки карты. Базовые стили — векторные CARTO (без ключа, требуется атрибуция),
 * см. [MapTheme]. Цвета поездов — [TrainPalette].
 */
object MapConfig {
    // Центр — середина линии (~Динамо/1905), старт: север сверху, без наклона.
    const val CENTER_LAT = 56.845
    const val CENTER_LON = 60.607
    const val INITIAL_ZOOM = 11.4
    const val INITIAL_BEARING = 0.0
    const val INITIAL_TILT = 0.0

    /** Область Екатеринбурга: офлайн-карта и сетка «чертежа». */
    const val BOUNDS_SOUTH = 56.72
    const val BOUNDS_NORTH = 56.95
    const val BOUNDS_WEST = 60.48
    const val BOUNDS_EAST = 60.76

    // Идентификаторы источников/слоёв.
    const val SRC_SCRIM = "scrim-src"
    const val LYR_SCRIM = "scrim"
    const val SRC_GRID = "grid-src"
    const val LYR_GRID = "grid"
    const val SRC_ROUTE = "route-src"
    const val LYR_ROUTE_CASING = "route-casing"
    const val LYR_ROUTE = "route-line"
    const val SRC_WALK = "walk-src"
    const val LYR_WALK = "walk-line"
    const val SRC_TRAILS = "trails-src"
    const val LYR_TRAILS_SOUTH = "trails-south"
    const val LYR_TRAILS_NORTH = "trails-north"
    const val SRC_STATIONS = "stations-src"
    const val LYR_STATION_CIRCLES = "station-circles"
    const val LYR_STATION_LABELS = "station-labels"
    const val SRC_USER = "user-src"
    const val LYR_USER_HALO = "user-halo"
    const val LYR_USER_DOT = "user-dot"
    const val SRC_TRAINS = "trains-src"
    const val LYR_TRAINS = "train-symbols"
    const val LYR_TRAIN_LABELS = "train-eta-labels"
    const val IMG_TRAIN_SOUTH = "train-south"
    const val IMG_TRAIN_NORTH = "train-north"
    const val IMG_TRAIN_GHOST = "train-ghost"
    const val SRC_EFFECTS = "effects-src"
    const val LYR_EFFECTS = "effects"
    const val SRC_ENTRANCES = "entrances-src"
    const val LYR_ENTRANCES = "entrances"
    const val LYR_ENTRANCE_LABELS = "entrance-labels"

    const val PROP_ID = "id"
    const val PROP_NAME = "name"
    const val PROP_FAV = "fav"
    const val PROP_WHEELCHAIR = "wc"
    const val PROP_DIR = "dir"
    const val PROP_BEARING = "bearing"
    const val PROP_LABEL_IMG = "label_img"
    const val PROP_OPACITY = "op"
    const val PROP_REF_DIR = "ref_dir"
    const val PROP_REF_STATION = "ref_station"
    const val PROP_REF_MINUTE = "ref_minute"

    // Свойства кругов-эффектов (вспышки, ореолы, хвост призрака).
    const val PROP_R = "r"
    const val PROP_SW = "sw"
    const val PROP_SC = "sc"
    const val PROP_SO = "so"
    const val PROP_FC = "fc"
    const val PROP_FO = "fo"

    /** Масштаб карты при слежении за поездом. */
    const val FOLLOW_ZOOM = 14.6
}
