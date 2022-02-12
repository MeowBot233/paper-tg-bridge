package nya.yukisawa.paper_tg_bridge

import kotlinx.coroutines.*
import org.bukkit.plugin.java.JavaPlugin

open class AsyncJavaPlugin : JavaPlugin() {
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onEnable() {
        runBlocking { onEnableAsync() }
    }

    override fun onDisable() {
        runBlocking {
            onDisableAsync()
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    open suspend fun onEnableAsync() = Unit

    open suspend fun onDisableAsync() = Unit

    fun <T> launch(f: suspend () -> T) = scope.launch { f() }
}
