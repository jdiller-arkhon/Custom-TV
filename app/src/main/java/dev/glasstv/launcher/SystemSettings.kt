package dev.glasstv.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Shortcuts into the TV's own settings. Each falls back to the main settings page if a TV hides the specific one. */
object SystemSettings {
    fun openWifi(context: Context) = open(context, Settings.ACTION_WIFI_SETTINGS)

    fun open(context: Context, action: String = Settings.ACTION_SETTINGS) {
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
