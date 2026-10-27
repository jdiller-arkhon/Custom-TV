package dev.glasstv.launcher.remote

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

object NetworkInfo {
    fun lanAddress(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }

    fun remoteUrl(context: Context): String? = lanAddress(context)?.let { "http://$it:${RemoteServer.PORT}/" }
}
