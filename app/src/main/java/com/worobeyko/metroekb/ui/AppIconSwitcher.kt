package com.worobeyko.metroekb.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Смена иконки приложения: у каждой иконки свой activity-alias в манифесте, включён ровно
 * один. Сначала включаем новый, потом выключаем остальные - чтобы не остаться без ярлыка.
 */
object AppIconSwitcher {
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        fun component(a: AppIcon) = ComponentName(context.packageName, "${context.packageName}.${a.alias}")
        pm.setComponentEnabledSetting(component(icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
