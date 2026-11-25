package dev.ryuya.accessibilitykeeper

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * system_server only. A force stop normally removes every accessibility service of the stopped app
 * from the enabled list and its shortcuts. Those apps are left out of that removal, so the
 * permission stays. The service either binds again right away or, by default, waits until the app
 * is opened.
 */
internal class KeeperHooks(
    private val framework: XposedInterface,
    loader: ClassLoader,
    private val settings: SharedPreferences,
    /** Components kept while waiting for their app to be opened again; survives hot updates. */
    val waiting: MutableSet<ComponentName>,
) {
    private val manager =
        loader.loadClass("com.android.server.accessibility.AccessibilityManagerService")
    private val userState =
        loader.loadClass("com.android.server.accessibility.AccessibilityUserState")
    private val connection =
        loader.loadClass("com.android.server.accessibility.AbstractAccessibilityServiceConnection")
    private val serviceConnection =
        loader.loadClass("com.android.server.accessibility.AccessibilityServiceConnection")
    private val forceStopped =
        manager
            .getDeclaredMethod("onPackagesForceStoppedLocked", Array<String>::class.java, userState)
            .accessible()
    private val binderDied = serviceConnection.getDeclaredMethod("binderDied").accessible()
    private val getLock = manager.getDeclaredMethod("getLock").accessible()
    private val currentUserState = manager.getDeclaredMethod("getCurrentUserState").accessible()
    private val userStateChanged =
        manager.getDeclaredMethod("onUserStateChangedLocked", userState).accessible()
    private val enabled = userState.field("mEnabledServices")
    private val binding = userState.field("mBindingServices")
    private val crashed = userState.field("mCrashedServices")
    private val userId = userState.field("mUserId")
    private val component = connection.field("mComponentName")
    private val system = connection.field("mSystemSupport")
    private val context = manager.field("mContext")
    private val handles = mutableListOf<XposedInterface.HookHandle>()
    private var owner = WeakReference<Any>(null)
    private var receiver: BroadcastReceiver? = null
    private var receiverContext: Context? = null
    private var thread: HandlerThread? = null

    private fun Class<*>.field(name: String): Field =
        getDeclaredField(name).apply { isAccessible = true }

    private fun Method.accessible(): Method = apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    private fun set(field: Field, state: Any) = field.get(state) as MutableSet<ComponentName>

    private val reconnect: Boolean
        get() = settings.getBoolean(KEY_RECONNECT, false)

    private val active: Boolean
        get() = settings.getBoolean(KEY_ENABLED, true)

    fun install() {
        handles +=
            framework.hook(forceStopped).intercept { chain ->
                val packages = chain.getArg(0) as Array<*>
                val state = chain.getArg(1)!!
                if (!active || packages.isEmpty()) return@intercept chain.proceed()
                // Runs with the accessibility lock held by the package monitor.
                val stopped = packages.filterIsInstance<String>().toHashSet()
                val excluded = settings.getStringSet(KEY_EXCLUDED, emptySet()).orEmpty()
                val kept =
                    set(enabled, state).filter {
                        it.packageName in stopped && it.packageName !in excluded
                    }
                if (kept.isEmpty()) return@intercept chain.proceed()
                val keptPackages = kept.mapTo(HashSet()) { it.packageName }
                val others = stopped.filter { it !in keptPackages }.toTypedArray()
                if (others.isNotEmpty()) chain.proceed(arrayOf(others, state))
                set(binding, state).removeAll(kept.toSet())
                listen(chain.thisObject)
                val pm = (context.get(chain.thisObject) as Context).packageManager
                // An app that already started again (for example its own component) does not wait.
                val (resume, wait) =
                    kept.partition {
                        reconnect ||
                            runCatching { !pm.isPackageStopped(it.packageName) }.getOrDefault(false)
                    }
                markRecent(resume)
                set(crashed, state).removeAll(resume.toSet())
                waiting.removeAll(resume.toSet())
                set(crashed, state).addAll(wait)
                waiting.addAll(wait)
                Log.i(
                    TAG,
                    "KEPT ${kept.joinToString { it.flattenToShortString() }} reconnect=$reconnect waiting=${wait.size}",
                )
                true
            }
        handles +=
            framework.hook(binderDied).intercept { chain ->
                val result = chain.proceed()
                // A resumed service that dies after the force stop is otherwise left marked as
                // crashed.
                if (active) {
                    val name = component.get(chain.thisObject) as? ComponentName
                    val service = system.get(chain.thisObject)
                    if (name != null && service != null && recentlyKept(name)) {
                        synchronized(getLock.invoke(service)!!) {
                            val state = currentUserState.invoke(service)!!
                            if (
                                set(enabled, state).contains(name) &&
                                    set(crashed, state).remove(name)
                            ) {
                                userStateChanged.invoke(service, state)
                            }
                        }
                    }
                }
                result
            }
    }

    private val recent = HashMap<ComponentName, Long>()

    private fun recentlyKept(name: ComponentName): Boolean =
        synchronized(recent) {
            (recent[name] ?: 0L) > android.os.SystemClock.uptimeMillis() - RECENT_MS
        }

    /** Lock held. Release waiting services once their app leaves the stopped state. */
    private fun listen(service: Any) {
        owner = WeakReference(service)
        if (receiver != null) return
        val context = context.get(service) as Context
        val worker = HandlerThread("AccessibilityKeeper").apply { start() }
        val listener =
            object : BroadcastReceiver() {
                override fun onReceive(ignored: Context, intent: Intent) {
                    val pkg =
                        intent.data?.schemeSpecificPart
                            ?: intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
                            ?: return
                    val manager = owner.get() ?: return
                    synchronized(getLock.invoke(manager)!!) {
                        val state = currentUserState.invoke(manager)!!
                        val released = waiting.filter { it.packageName == pkg }
                        if (released.isEmpty()) return
                        waiting.removeAll(released.toSet())
                        set(crashed, state).removeAll(released.toSet())
                        userStateChanged.invoke(manager, state)
                        Log.i(TAG, "RESUMED $pkg user=${userId.getInt(state)}")
                    }
                }
            }
        context.registerReceiver(
            listener,
            // Delivered both with and without package data depending on the sender.
            IntentFilter(Intent.ACTION_PACKAGE_UNSTOPPED),
            null,
            Handler(worker.looper),
            Context.RECEIVER_NOT_EXPORTED,
        )
        receiver = listener
        receiverContext = context
        thread = worker
    }

    fun markRecent(names: Collection<ComponentName>) {
        val now = android.os.SystemClock.uptimeMillis()
        synchronized(recent) { names.forEach { recent[it] = now } }
    }

    fun close() {
        handles.forEach { runCatching { it.unhook() } }
        handles.clear()
        receiver?.let { runCatching { receiverContext?.unregisterReceiver(it) } }
        receiver = null
        thread?.quitSafely()
        thread = null
    }

    /** A hot update keeps waiting services waiting; the new generation listens again. */
    fun resume(manager: Any?) {
        if (manager != null && waiting.isNotEmpty())
            synchronized(getLock.invoke(manager)!!) { listen(manager) }
    }

    val managerInstance: Any?
        get() = owner.get()

    companion object {
        const val TAG = "AccessibilityKeeper"
        const val GROUP = "settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_RECONNECT = "reconnect"

        /** Packages the user turned off; every other app keeps its permission. */
        const val KEY_EXCLUDED = "excluded"
        private const val RECENT_MS = 10_000L
    }
}
