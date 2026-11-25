package dev.ryuya.accessibilitykeeper

import android.app.Application
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * Receives the framework's binder; settings are shared with system_server as remote preferences.
 */
class ModuleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(
            object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    current = service
                    listeners.forEach { it() }
                }

                override fun onServiceDied(service: XposedService) {
                    if (current === service) current = null
                    listeners.forEach { it() }
                }
            },
        )
    }

    companion object {
        @Volatile private var current: XposedService? = null
        val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

        val settings: SharedPreferences?
            get() = current?.let {
                runCatching { it.getRemotePreferences(KeeperHooks.GROUP) }.getOrNull()
            }
    }
}
