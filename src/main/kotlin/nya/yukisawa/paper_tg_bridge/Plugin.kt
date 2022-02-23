package nya.yukisawa.paper_tg_bridge

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextReplacementConfig
import org.bukkit.command.CommandSender
import org.bukkit.event.HandlerList
import net.milkbowl.vault.chat.Chat as ch
import nya.yukisawa.paper_tg_bridge.Constants as C

class Plugin : AsyncJavaPlugin() {
    private var tgBot: TgBot? = null
    private var eventHandler: EventHandler? = null
    private var config: Configuration? = null
    var chat: ch? = null

    override suspend fun onEnableAsync() {
        try {
            launch {
                config = Configuration(this).also {
                    initializeWithConfig(it)
                }
            }
            if (!setupChat()) logger.warning("Vault is not installed!")
        } catch (e: Exception) {
            // Configuration file is missing or incomplete
            logger.warning(e.localizedMessage)
        }
    }

    private suspend fun initializeWithConfig(config: Configuration) {
        if (!config.isEnabled) return


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

    suspend fun reload(sender: CommandSender) {
        config = Configuration(this).also { config ->
            if (!config.isEnabled) return
            sender.sendMessage(C.INFO.reloading)
            eventHandler?.let { HandlerList.unregisterAll(it) }
            tgBot?.run { stop() }
            tgBot = TgBot(this, config).also { bot ->
                bot.startPolling()
                eventHandler = EventHandler(this, config, bot).also {
                    server.pluginManager.registerEvents(it, this)
                }
            }
            sender.sendMessage(C.INFO.reloadComplete)
        }
    }

    private fun setupChat(): Boolean {
        if (!config!!.useVault) return false
        val rsp = server.servicesManager.getRegistration(ch::class.java)
        rsp?.let { chat = it.provider }
        return chat != null
    }
}
