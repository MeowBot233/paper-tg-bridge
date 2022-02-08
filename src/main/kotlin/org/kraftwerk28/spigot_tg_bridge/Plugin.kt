package org.kraftwerk28.spigot_tg_bridge

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import org.bukkit.event.HandlerList
import java.lang.Exception
import net.milkbowl.vault.chat.Chat as ch
import org.kraftwerk28.spigot_tg_bridge.Constants as C

class Plugin : AsyncJavaPlugin() {
    private var tgBot: TgBot? = null
    private var eventHandler: EventHandler? = null
    private var config: Configuration? = null
    var ignAuth: IgnAuth? = null
    var chat: ch? = null

    override suspend fun onEnableAsync() {
        try {
            setupChat()
            launch {
                config = Configuration(this).also {
                    initializeWithConfig(it)
                }
            }
        } catch (e: Exception) {
            // Configuration file is missing or incomplete
            logger.warning(e.message)
        }
    }

    private suspend fun initializeWithConfig(config: Configuration) {
        if (!config.isEnabled) return

        if (config.enableIgnAuth) {
            val dbFilePath = dataFolder.resolve("spigot-tg-bridge.sqlite")
            ignAuth = IgnAuth(
                fileName = dbFilePath.absolutePath,
                plugin = this,
            )
        }

        tgBot?.run { stop() }
        tgBot = TgBot(this, config).also { bot ->
            bot.startPolling()
            EventHandler(this, config, bot).also {
                server.pluginManager.registerEvents(it, this)
            }
        }

        getCommand(C.COMMANDS.PLUGIN_RELOAD)?.run {
            setExecutor(CommandHandler(this@Plugin))
        }
        config.serverStartMessage?.let {
            tgBot?.sendMessageToTelegram(it)
        }
    }

    override suspend fun onDisableAsync() {
        config?.let fn@{ config ->
            if (!config.isEnabled)
                return@fn
            config.serverStopMessage?.let {
                tgBot?.sendMessageToTelegram(it)
            }
            eventHandler?.let { HandlerList.unregisterAll(it) }
            tgBot?.run { stop() }
            tgBot = null
            ignAuth?.close()
        }
    }

    fun sendMessageToMinecraft(
        text: Component,
        username: String,
        chatTitle: String,
    ) = config?.run {
        val format = Component.text(
            minecraftFormat
            .replace(C.USERNAME_PLACEHOLDER,username)
            .replace(C.CHAT_TITLE_PLACEHOLDER, chatTitle)
        ).replaceText(
            TextReplacementConfig.builder()
                .match(C.MESSAGE_TEXT_PLACEHOLDER)
                .replacement(text)
                .once()
                .build()
            )
        server.broadcast(format)

    }

    suspend fun reload() {
        config = Configuration(this).also { config ->
            if (!config.isEnabled) return
            logger.info(C.INFO.reloading)
            eventHandler?.let { HandlerList.unregisterAll(it) }
            tgBot?.run { stop() }
            tgBot = TgBot(this, config).also { bot ->
                bot.startPolling()
                eventHandler = EventHandler(this, config, bot).also {
                    server.pluginManager.registerEvents(it, this)
                }
            }
            logger.info(C.INFO.reloadComplete)
        }
    }

    private fun setupChat(): Boolean {
        val rsp = server.servicesManager.getRegistration(ch::class.java)
        rsp?.let { chat = it.provider }
        return chat != null
    }
}
