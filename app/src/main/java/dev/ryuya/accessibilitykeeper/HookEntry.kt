package dev.ryuya.accessibilitykeeper

import android.content.ComponentName
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.util.Collections

/** Loaded only into system_server; the app process uses the module's own settings screen. */
class HookEntry : XposedModule() {
    private var hooks: KeeperHooks? = null
    private var loader: ClassLoader? = null

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        start(param.classLoader, Collections.synchronizedSet(HashSet()), null)
    }

    private fun start(classLoader: ClassLoader, waiting: MutableSet<ComponentName>, manager: Any?) {
        try {
            val installed =
                KeeperHooks(this, classLoader, getRemotePreferences(KeeperHooks.GROUP), waiting)
            installed.install()
            installed.resume(manager)
            hooks = installed
            loader = classLoader
            Log.i(KeeperHooks.TAG, "READY build=${BuildConfig.VERSION_CODE}")
        } catch (error: ReflectiveOperationException) {
            Log.e(KeeperHooks.TAG, "UNSUPPORTED", error)
        } catch (error: RuntimeException) {
            Log.e(KeeperHooks.TAG, "UNSUPPORTED", error)
        }
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        val current = hooks ?: return false
        val classLoader = loader ?: return false
        param.setSavedInstanceState(arrayOf(classLoader, current.waiting, current.managerInstance))
        current.close()
        hooks = null
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val state = param.savedInstanceState as Array<*>
        @Suppress("UNCHECKED_CAST")
        start(state[0] as ClassLoader, state[1] as MutableSet<ComponentName>, state[2])
        param.oldHookHandles.forEach { it.unhook() }
        Log.i(
            KeeperHooks.TAG,
            "ONEUI_MODS_HOT_RELOAD package=${BuildConfig.APPLICATION_ID} version=${BuildConfig.VERSION_CODE} pid=${android.os.Process.myPid()}",
        )
    }
}
