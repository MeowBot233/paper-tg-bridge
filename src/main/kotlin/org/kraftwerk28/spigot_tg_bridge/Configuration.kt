package org.kraftwerk28.spigot_tg_bridge

import java.io.File
import org.kraftwerk28.spigot_tg_bridge.Constants as C

class Configuration(plugin: Plugin) {
    val isEnabled: Boolean
    val logFromMCtoTG: Boolean
    val telegramFormat: String
    val minecraftFormat: String
    val serverStartMessage: String?
    val serverStopMessage: String?
    val logJoinLeave: Boolean
    val joinString: String
    val leaveString: String
    val logDeath: Boolean
    val logPlayerAsleep: Boolean
    val logPlayerAdvancement: Boolean
    val advancementString: String
    val onlineString: String
    val nobodyOnlineString: String
    val asleepString: String
    val enableIgnAuth: Boolean

    // Telegram bot stuff
    val botToken: String
    val allowedChats: List<Long>
    val logFromTGtoMC: Boolean
    val allowWebhook: Boolean
    val webhookConfig: Map<String, Any>?
    val pollTimeout: Int

    val telegramAPI: String

    var commands: BotCommands

    init {
        val cfgFile = File(plugin.dataFolder, C.configFilename)
        if (!cfgFile.exists()) {
            cfgFile.parentFile.mkdirs()
            plugin.saveDefaultConfig()
            // plugin.saveResource(C.configFilename, false);
            throw Exception(C.WARN.noConfigWarning)
        }
        val pluginConfig = plugin.config
        pluginConfig.load(cfgFile)

        pluginConfig.getString("minecraftMessageFormat")?.let {
            plugin.logger.warning(
                """
                Config option "minecraftMessageFormat" is deprecated.
                Moved it to new key "telegramFormat"
                """.trimIndent().replace('\n', ' ')
            )
            pluginConfig.set("telegramFormat", it)
            pluginConfig.set("minecraftMessageFormat", null)
            plugin.saveConfig()
        }

        pluginConfig.getString("telegramMessageFormat")?.let {
            plugin.logger.warning(
                """
                Config option "telegramMessageFormat" is deprecated.
                Moved it to new key "minecraftFormat"
                """.trimIndent().replace('\n', ' ')
            )
            pluginConfig.set("minecraftFormat", it)
            pluginConfig.set("telegramMessageFormat", null)
            plugin.saveConfig()
        }

        pluginConfig.run {
            isEnabled = getBoolean("enable", true)
            serverStartMessage = getString("serverStartMessage")
            serverStopMessage = getString("serverStopMessage")
            logFromTGtoMC = getBoolean("logFromTGtoMC", true)
            logFromMCtoTG = getBoolean("logFromMCtoTG", true)
            telegramFormat = getString(
                "telegramFormat",
                "<i>%username%</i>: %message%",
            )!!
            minecraftFormat = getString(
                "minecraftFormat",
                "<%username%>: %message%",
            )!!
            // isEnabled = getBoolean("enable", true)
            allowedChats = getLongList("chats")
            enableIgnAuth = getBoolean("enableIgnAuth", false)

            botToken = getString("botToken") ?: throw Exception(C.WARN.noToken)
            allowWebhook = getBoolean("useWebhook", false)
            @Suppress("unchecked_cast")
            webhookConfig = get("webhookConfig") as Map<String, Any>?
            pollTimeout = getInt("pollTimeout", 30)

            logJoinLeave = getBoolean("logJoinLeave", false)
            onlineString = getString("strings.online", "Online")!!
            nobodyOnlineString = getString(
                "strings.nobodyOnline",
                "Nobody online"
            )!!
            asleepString = getString(
                "strings.asleep",
                "<b>%username%</b> 睡觉了"
            )!!
            joinString = getString(
                "strings.joined",
                "<b>%username%</b> 进入服务器"
            )!!
            leaveString = getString(
                "strings.left",
                "<b>%username%</b> 退出服务器"
            )!!
            logPlayerAdvancement = getBoolean("logPlayerAdvancement", false)
            advancementString = getString(
                "strings.advancement",
                "<b>%username%<b> 取得了进度 <b>%advancement%<b>"
            )!!
            logDeath = getBoolean("logPlayerDeath", false)
            logPlayerAsleep = getBoolean("logPlayerAsleep", false)
            telegramAPI = getString(
                "telegramAPI",
                "api.telegram.org"
            )!!

            commands = BotCommands(this)
        }
    }

    companion object {
    }
}
