package nya.yukisawa.paper_tg_bridge

import org.yaml.snakeyaml.Yaml
import java.io.File
import nya.yukisawa.paper_tg_bridge.Constants as C

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

    val death: Map<String, String>
    val advancements: Map<String, String>

    // Telegram bot stuff
    val botToken: String
    val allowedChats: List<Long>
    val logFromTGtoMC: Boolean
    val allowWebhook: Boolean
    val webhookConfig: Map<String, Any>?
    val pollTimeout: Int

    val telegramAPI: String

    var commands: BotCommands

    val debug: Boolean

    init {
        val cfgFile = File(plugin.dataFolder, C.configFilename)
        if (!cfgFile.exists()) {
            cfgFile.parentFile.mkdirs()
            plugin.saveDefaultConfig()
            // plugin.saveResource(C.configFilename, false);
            throw Exception(C.WARN.noConfigWarning)
        }
        val yaml = Yaml()
        death = yaml.load(this::class.java.getResourceAsStream("/death.yml"))
        advancements = yaml.load(this::class.java.getResourceAsStream("/advancements.yml"))
        val pluginConfig = plugin.config
        pluginConfig.load(cfgFile)

        pluginConfig.run {
            isEnabled = getBoolean("enable", true)
            serverStartMessage = getString("serverStartMessage")
            serverStopMessage = getString("serverStopMessage")
            logFromTGtoMC = getBoolean("logFromTGtoMC", true)
            logFromMCtoTG = getBoolean("logFromMCtoTG", true)
            telegramFormat = getString("telegramFormat")!!
            minecraftFormat = getString("minecraftFormat")!!
            allowedChats = getLongList("chats")

            botToken = getString("botToken") ?: throw Exception(C.WARN.noToken)
            allowWebhook = getBoolean("useWebhook", false)
            @Suppress("unchecked_cast")
            webhookConfig = get("webhookConfig") as Map<String, Any>?
            pollTimeout = getInt("pollTimeout", 30)

            logJoinLeave = getBoolean("logJoinLeave", false)
            onlineString = getString("strings.online")!!
            nobodyOnlineString = getString("strings.nobodyOnline")!!
            asleepString = getString("strings.asleep")!!
            joinString = getString("strings.joined")!!
            leaveString = getString("strings.left")!!
            logPlayerAdvancement = getBoolean("logPlayerAdvancement", false)
            advancementString = getString("strings.advancement")!!
            logDeath = getBoolean("logPlayerDeath", false)
            logPlayerAsleep = getBoolean("logPlayerAsleep", false)
            telegramAPI = getString(
                "telegramAPI",
                "api.telegram.org"
            )!!

            commands = BotCommands(this)

            debug = getBoolean("debug", false)
        }
    }

    companion object {
    }
}
